package com.pocketnas.pro

import android.app.Application
import android.os.Build
import com.pocketnas.pro.core.BinaryUtil
import com.pocketnas.pro.core.LogStore
import com.pocketnas.pro.service.KeepAliveWorker
import com.pocketnas.pro.service.OpenListService
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用入口：部署内核二进制 + 初始化文件日志 + 自动拉起内核服务。
 * 内置全局崩溃捕获：任何未捕获异常（含主线程 composition 崩溃）都会把
 * 完整堆栈写到外部日志目录 crash.log，白屏/闪退时用文件管理器直接可取。
 */
class PocketNasApp : Application() {

    override fun onCreate() {
        super.onCreate()
        BinaryUtil.ensureBinary(this)
        LogStore.init(this)

        // 全局崩溃捕获：主线程/子线程未捕获异常都落盘，便于白屏/闪退定位
        installCrashCatcher()

        LogStore.log("APP", "Application.onCreate 完成，SDK=${Build.VERSION.SDK_INT} ${Build.MODEL}")

        // 注册 WorkManager 周期保活任务（15 分钟检查一次主服务/守护进程）
        try {
            KeepAliveWorker.enqueue(this)
        } catch (e: Exception) {
            LogStore.log("APP", "KeepAliveWorker.enqueue 失败: ${e.message}")
        }
        // 应用启动即拉起内核前台服务，避免登录时 socket 未就绪导致闪退
        try {
            OpenListService.start(this)
        } catch (e: Exception) {
            LogStore.log("APP", "OpenListService.start 失败: ${e.message}")
        }
    }

    private fun installCrashCatcher() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val sb = StringBuilder()
                sb.append("=== CRASH ${fmt()} thread=${thread.name} ===\n")
                sb.append(sw.toString())
                sb.append('\n')
                val dir = getExternalFilesDir(null) ?: filesDir
                val f = File(dir, "crash.log")
                f.appendText(sb.toString())
                // 同步镜像一份到 App 私有目录（外部存储被禁时备用）
                try {
                    File(filesDir, "crash.log").appendText(sb.toString())
                } catch (_: Exception) {}
            } catch (_: Exception) {}
            prev?.uncaughtException(thread, throwable)
        }
    }

    private fun fmt(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
