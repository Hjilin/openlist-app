package com.pocketnas.pro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import com.pocketnas.pro.core.KeepAlive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import android.os.IBinder
import java.lang.Process
import android.util.Log
import com.pocketnas.pro.MainActivity
import com.pocketnas.pro.R
import com.pocketnas.pro.core.BinaryUtil
import com.pocketnas.pro.core.LogStore
import com.pocketnas.pro.core.NasState
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

/**
 * 前台服务：托管 OpenList 内核子进程。
 *
 * - 持有 Process 对象，可主动 destroy() 杀进程（根治"进程隔离看不到/杀不掉"问题）
 * - 读取内核 stdout/stderr 日志，转发到 [NasState] 供 UI 展示，同时落盘日志文件
 * - 进程意外退出自动重启（可配置）
 */
class OpenListService : Service() {

    companion object {
        private const val TAG = "OpenListService"
        private const val CHANNEL_ID = "pocketnas_service"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.pocketnas.pro.action.START"
        const val ACTION_STOP = "com.pocketnas.pro.action.STOP"
        const val ACTION_RESTART = "com.pocketnas.pro.action.RESTART"

        private const val PREF_NAME = "pocketnas_prefs"
        private const val KEY_AUTO_RESTART = "auto_restart"
        private const val KEY_BOOT_START = "boot_start"

        fun isAutoRestartEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .getBoolean(KEY_AUTO_RESTART, true)

        fun setAutoRestart(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .edit().putBoolean(KEY_AUTO_RESTART, enabled).apply()
        }

        fun isBootStartEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .getBoolean(KEY_BOOT_START, false)

        fun setBootStart(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .edit().putBoolean(KEY_BOOT_START, enabled).apply()
        }

        fun start(context: Context) {
            val i = Intent(context, OpenListService::class.java).setAction(ACTION_START)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, OpenListService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }

        /** 重启内核进程（配置变更后调用，如远程访问开关） */
        fun restart(context: Context) {
            val i = Intent(context, OpenListService::class.java).setAction(ACTION_RESTART)
            context.startService(i)
        }
    }

    private var process: Process? = null
    private var restartScheduled = false
    private var stopping = false
    private val keepAliveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var beatJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    /** 保活心跳：前台运行期间每 60s 刷新 master 心跳，并保证守护进程存活 */
    private fun startKeepAliveBeats() {
        if (beatJob?.isActive == true) return
        LogStore.log("SVC", "保活心跳已启动（60s）")
        beatJob = keepAliveScope.launch {
            while (true) {
                KeepAlive.beat(KeepAlive.masterFile(this@OpenListService))
                if (!KeepAlive.guardAlive(this@OpenListService)) {
                    try {
                        startService(Intent(this@OpenListService, GuardService::class.java))
                    } catch (_: Exception) {}
                }
                delay(60_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_STOP -> {
                LogStore.log("SVC", "收到停止命令，停止内核")
                stopOpenList()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                LogStore.log("SVC", "收到重启命令")
                stopOpenList()
                startOpenList()
            }
            else -> {
                LogStore.log("SVC", "服务启动（action=${action}）")
                startForeground(NOTIFICATION_ID, buildNotification())
                startOpenList()
                startKeepAliveBeats()
                GuardService.start(this)
            }
        }
        return START_STICKY
    }

    /** 启动内核子进程 */
    private fun startOpenList() {
        if (process?.isAlive == true) return
        stopping = false

        if (!BinaryUtil.ensureBinary(this)) {
            NasState.setError("内核二进制部署失败：请确认 jniLibs/arm64-v8a/libopenlist.so 存在")
            NasState.appendLog("[PocketNAS] 错误：二进制部署失败")
            LogStore.log("KERNEL", "二进制部署失败：${BinaryUtil.binFile(this)?.absolutePath}")
            return
        }

        // 清理残留 socket
        BinaryUtil.cleanSocket(this)

        val bin = BinaryUtil.binFile(this)
        val workDir = BinaryUtil.dataDir(this)
        val logFile = BinaryUtil.logFile(this)

        NasState.appendLog("[PocketNAS] 启动内核: ${bin.absolutePath}")
        NasState.appendLog("[PocketNAS] 工作目录: ${workDir.absolutePath}")

        try {
            val pb = ProcessBuilder(bin.absolutePath, "server")
            pb.directory(workDir)
            pb.redirectErrorStream(true)

            // 远程访问服务（FTP / SFTP）：按开关注入内核环境变量
            val remoteEnv = com.pocketnas.pro.core.RemoteAccess.env(this)
            if (remoteEnv.isNotEmpty()) {
                pb.environment().putAll(remoteEnv)
                NasState.appendLog("[PocketNAS] 远程访问: ${remoteEnv.keys.joinToString(", ")}")
            }

            process = pb.start()
            NasState.setRunning(true)
            NasState.setError(null)

            // 日志转发线程：stdout -> 内存缓冲 + 日志文件
            Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(process!!.inputStream, Charsets.UTF_8))
                    val fos = FileOutputStream(logFile, true)
                    reader.useLines { lines ->
                        for (line in lines) {
                            NasState.appendLog(line)
                            try {
                                fos.write((line + "\n").toByteArray(Charsets.UTF_8))
                                fos.flush()
                            } catch (_: Exception) {}
                        }
                    }
                    fos.close()
                } catch (_: Exception) {}
                // 进程退出处理
                handleProcessExit()
            }.apply {
                name = "openlist-log-reader"
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            NasState.setError("启动失败: ${e.message}")
            NasState.appendLog("[PocketNAS] 启动异常: ${e.message}")
            Log.e(TAG, "start failed", e)
        }
    }

    private fun handleProcessExit() {
        val exited = process?.waitFor() ?: return
        NasState.setRunning(false)
        NasState.appendLog("[PocketNAS] 内核进程已退出 (code=$exited)")
        LogStore.log("KERNEL", "内核进程退出 code=$exited")

        val shouldRestart = isAutoRestartEnabled(this) && !stopping && !restartScheduled
        if (shouldRestart) {
            restartScheduled = true
            NasState.appendLog("[PocketNAS] 3 秒后自动重启...")
            LogStore.log("KERNEL", "崩溃自动重启已调度（3s）")
            Thread {
                try {
                    Thread.sleep(3_000)
                } catch (_: InterruptedException) {}
                restartScheduled = false
                if (!stopping) {
                    // 前台服务仍在，执行重启
                    startOpenList()
                }
            }.apply {
                name = "openlist-restarter"
                isDaemon = true
                start()
            }
        }
    }

    /** 主动停止内核子进程 */
    private fun stopOpenList() {
        stopping = true
        NasState.appendLog("[PocketNAS] 停止内核...")
        LogStore.log("KERNEL", "停止内核")
        process?.let {
            try {
                it.destroy()
                if (it.isAlive) {
                    it.destroyForcibly()
                }
            } catch (e: Exception) {
                Log.e(TAG, "destroy failed", e)
            }
        }
        process = null
        NasState.setRunning(false)
    }

    override fun onDestroy() {
        beatJob?.cancel()
        keepAliveScope.cancel()
        stopOpenList()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PocketNAS 内核服务",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OpenListService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PocketNAS 运行中")
            .setContentText("OpenList 内核正在后台服务，点击管理")
            .setSmallIcon(R.drawable.ic_stat_nas)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, "停止", stopIntent)
            .build()
    }
}
