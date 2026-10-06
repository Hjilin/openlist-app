package com.pocketnas.pro.core

import android.content.Context
import java.io.File

/**
 * 保活心跳与工具：跨进程状态通过 filesDir/keepalive/ 下的时间戳文件传递
 * （NasState 是内存对象，无法跨进程）。
 *
 * - master：OpenListService 前台运行时每 60s 刷新
 * - guard：GuardService（独立进程）每 30s 刷新
 * KeepAliveWorker 每 15 分钟检查两者，全部失活则尝试拉起。
 */
object KeepAlive {

    private fun dir(context: Context): File =
        File(context.filesDir, "keepalive").apply { mkdirs() }

    fun masterFile(context: Context) = File(dir(context), "master")

    fun guardFile(context: Context) = File(dir(context), "guard")

    /** 写入心跳（当前时间戳） */
    fun beat(file: File) {
        try {
            file.writeText(System.currentTimeMillis().toString())
        } catch (_: Exception) {}
    }

    /** 心跳距今秒数；文件缺失视为失活 */
    fun ageSeconds(file: File): Long {
        return try {
            val ts = file.readText().trim().toLongOrNull() ?: return Long.MAX_VALUE
            (System.currentTimeMillis() - ts) / 1000
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    /** 主服务失活判定（>180s 未心跳） */
    fun masterAlive(context: Context): Boolean = ageSeconds(masterFile(context)) < 180

    /** 守护进程失活判定（>180s 未心跳） */
    fun guardAlive(context: Context): Boolean = ageSeconds(guardFile(context)) < 180
}

/**
 * Root 保活工具：尝试通过 su 执行系统级保活（Doze 白名单）。
 * 无 Root 时所有方法安全返回 false，不阻塞 UI。
 */
object RootUtil {

    /** 检测是否有 root 权限 */
    fun hasRoot(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val ok = p.inputStream.readBytes().toString(Charsets.UTF_8).contains("uid=0")
            p.destroy()
            ok
        } catch (_: Exception) {
            false
        }
    }

    /** 执行一条 su 命令，返回是否成功 */
    private fun su(cmd: String): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val rc = p.waitFor()
            p.destroy()
            rc == 0
        } catch (_: Exception) {
            false
        }
    }

    /** 将本应用加入系统 Doze 白名单（免电池优化）；需要 root */
    fun applyDozeWhitelist(context: Context): Boolean {
        val pkg = context.packageName
        return su("dumpsys deviceidle whitelist +$pkg")
    }

    /** 将内核子进程提升为不可杀（cgroup 尽力而为）；需要 root */
    fun protectKernelProcess(pid: Int): Boolean {
        return su("echo -17 > /proc/$pid/oom_score_adj")
    }
}
