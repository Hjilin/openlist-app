package com.pocketnas.pro.core

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 文件日志中心：追加写入 + 大小轮转 + 外部存储可直达。
 *
 * 存储位置（用户文件管理器可直接访问，无需 Root）：
 *   /storage/emulated/0/Android/data/com.pocketnas.pro/files/logs/
 *   - pocketnas.log       最新日志
 *   - pocketnas.log.1     上一份（轮转）
 *   - pocketnas.log.2
 *   - pocketnas.log.3     最旧
 *
 * 轮转策略：单文件超过 MAX_BYTES（512KB）时整体顺移。
 * 线程安全：synchronized 串行写，内部独立单线程执行器，不阻塞调用方。
 */
object LogStore {

    private const val MAX_BYTES = 512 * 1024
    private const val KEEP_ROTATIONS = 3
    private const val LOG_DIR = "logs"
    private const val LOG_NAME = "pocketnas.log"

    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val executor = Executors.newSingleThreadExecutor()
    private var dir: File? = null
    private var appContext: Context? = null

    /** 初始化日志目录（应用启动时调用一次） */
    fun init(context: Context) {
        appContext = context.applicationContext
        if (dir != null) return
        dir = File(context.getExternalFilesDir(null), LOG_DIR).apply { mkdirs() }
    }

    /** 便捷写日志（使用已初始化的应用上下文） */
    fun log(tag: String, msg: String) {
        val c = appContext ?: return
        log(c, tag, msg)
    }

    fun logDir(context: Context): File =
        dir ?: File(context.getExternalFilesDir(null), LOG_DIR).apply { mkdirs() }

    fun logFile(context: Context): File = File(logDir(context), LOG_NAME)

    /** 追加一条日志：时间戳 [TAG] 消息 */
    fun log(context: Context, tag: String, msg: String) {
        val line = "${fmt.format(Date())} [$tag] $msg\n"
        executor.execute {
            synchronized(this) {
                try {
                    val f = logFile(context)
                    rotateIfNeeded(f)
                    f.appendText(line)
                } catch (_: Exception) {}
            }
        }
    }

    /** 单文件超过上限时轮转：log.2→log.3, log.1→log.2, log→log.1 */
    private fun rotateIfNeeded(f: File) {
        if (!f.exists() || f.length() < MAX_BYTES) return
        try {
            for (i in KEEP_ROTATIONS - 1 downTo 1) {
                val src = File(f.parentFile, "$LOG_NAME.$i")
                val dst = File(f.parentFile, "$LOG_NAME.${i + 1}")
                if (dst.exists()) dst.delete()
                if (src.exists()) src.renameTo(dst)
            }
            val b1 = File(f.parentFile, "$LOG_NAME.1")
            if (b1.exists()) b1.delete()
            f.renameTo(b1)
        } catch (_: Exception) {}
    }

    /** 读取全部日志文件（从最旧轮转合并，仅尾部，用于调取） */
    fun readAll(context: Context, maxLines: Int = 1000): String {
        return try {
            val sb = StringBuilder()
            for (i in KEEP_ROTATIONS downTo 1) {
                val f = File(logDir(context), "$LOG_NAME.$i")
                if (f.exists()) appendTail(f, sb, maxLines)
            }
            val main = logFile(context)
            if (main.exists()) appendTail(main, sb, maxLines)
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    /** 追加文件尾部，总行数不超过 maxLines */
    private fun appendTail(f: File, sb: StringBuilder, maxLines: Int) {
        val lines = f.readLines()
        val start = (lines.size - maxLines).coerceAtLeast(0)
        for (i in start until lines.size) {
            if (sb.length > 0) sb.append('\n')
            sb.append(lines[i])
        }
    }

    /** 日志总字节数（含轮转） */
    fun totalBytes(context: Context): Long {
        var n = 0L
        for (i in 1..KEEP_ROTATIONS) {
            val f = File(logDir(context), "$LOG_NAME.$i")
            if (f.exists()) n += f.length()
        }
        val main = logFile(context)
        if (main.exists()) n += main.length()
        return n
    }

    /** 清空全部日志文件 */
    fun clear(context: Context) {
        executor.execute {
            synchronized(this) {
                try {
                    for (i in 1..KEEP_ROTATIONS) {
                        File(logDir(context), "$LOG_NAME.$i").delete()
                    }
                    logFile(context).delete()
                } catch (_: Exception) {}
            }
        }
    }
}
