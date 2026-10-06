package com.pocketnas.pro

import android.app.Application
import com.pocketnas.pro.core.BinaryUtil
import com.pocketnas.pro.core.LogStore
import com.pocketnas.pro.service.KeepAliveWorker
import com.pocketnas.pro.service.OpenListService

/**
 * 应用入口：部署内核二进制 + 初始化文件日志 + 自动拉起内核服务。
 */
class PocketNasApp : Application() {

    override fun onCreate() {
        super.onCreate()
        BinaryUtil.ensureBinary(this)
        LogStore.init(this)
        // 注册 WorkManager 周期保活任务（15 分钟检查一次主服务/守护进程）
        try {
            KeepAliveWorker.enqueue(this)
        } catch (_: Exception) {}
        // 应用启动即拉起内核前台服务，避免登录时 socket 未就绪导致闪退
        try {
            OpenListService.start(this)
        } catch (_: Exception) {}
    }
}
