package com.pocketnas.pro.service

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pocketnas.pro.core.KeepAlive
import java.util.concurrent.TimeUnit

/**
 * 周期保活任务（WorkManager 调度，最小间隔 15 分钟）。
 *
 * 检查主服务心跳（master）与守护进程心跳（guard）：
 * - 全部失活 → 尝试拉起 OpenListService（Android 12+ 后台启动受限时静默失败，
 *   下个周期再试；用户打开 APP 时 MainActivity 也会自动拉起）
 * - 仅 guard 失活 → 尝试拉起 GuardService（独立进程双保险）
 */
class KeepAliveWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext
            if (!KeepAlive.masterAlive(ctx)) {
                try {
                    ctx.startForegroundService(
                        Intent(ctx, OpenListService::class.java).setAction(OpenListService.ACTION_START)
                    )
                } catch (_: Exception) {
                    // 后台启动前台服务受限（Android 12+），下个周期再试
                }
            }
            if (!KeepAlive.guardAlive(ctx)) {
                try {
                    ctx.startService(Intent(ctx, GuardService::class.java))
                } catch (_: Exception) {}
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "pocketnas_keepalive"
        private const val INTERVAL_MIN = 15L

        /** 注册周期任务（幂等；重复调用仅保留一个） */
        fun enqueue(context: Context) {
            val req = PeriodicWorkRequestBuilder<KeepAliveWorker>(INTERVAL_MIN, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
