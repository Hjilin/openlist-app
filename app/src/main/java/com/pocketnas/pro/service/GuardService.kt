package com.pocketnas.pro.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.pocketnas.pro.core.KeepAlive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 守护服务（独立进程 :guard，双保险）。
 *
 * 周期（30s）检查主服务心跳：主服务失活则尝试拉起 OpenListService；
 * 自身持续写 guard 心跳，供 KeepAliveWorker 判断本进程是否被系统回收。
 *
 * 说明：Android 12+ 后台启动前台服务受系统限制，本服务为"尽力而为"，
 * 主保活链路是前台服务 + WorkManager 周期任务（系统级调度，最可靠）。
 */
class GuardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        start()
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        start()
    }

    private fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (true) {
                KeepAlive.beat(KeepAlive.guardFile(this@GuardService))
                if (!KeepAlive.masterAlive(this@GuardService)) {
                    try {
                        startService(
                            Intent(this@GuardService, OpenListService::class.java)
                                .setAction(OpenListService.ACTION_START)
                        )
                    } catch (_: Exception) {}
                }
                delay(30_000)
            }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            try {
                context.startService(Intent(context, GuardService::class.java))
            } catch (_: Exception) {}
        }
    }
}
