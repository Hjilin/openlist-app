package com.pocketnas.pro.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自启 / 应用更新后自启接收器。
 * 仅在用户开启"开机自启"开关时拉起前台服务。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val enabled = when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> OpenListService.isBootStartEnabled(context)
            else -> return
        }
        if (enabled) {
            OpenListService.start(context)
        }
    }
}
