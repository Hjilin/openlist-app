package com.pocketnas.pro.core

import android.content.Context
import android.os.Build
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 日志打包工具：把 App 日志、内核日志、崩溃日志和设备信息打成 zip，
 * 放到公共目录 Download/PocketNAS_logs/，任何文件管理器可直接取用，方便问题排查。
 */
object LogGrabber {

    /** 打包全部日志，返回生成的 zip 文件；失败返回 null */
    fun zip(context: Context): File? {
        return try {
            val dir = LogStore.publicLogDir()
                ?: File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val out = File(dir, "简云plas日志_$stamp.zip")

            ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zos ->
                // 1. App 日志（私有目录，含轮转）
                val logDir = LogStore.logDir(context)
                listOf(
                    "pocketnas.log",
                    "pocketnas.log.1",
                    "pocketnas.log.2",
                    "pocketnas.log.3",
                ).forEach { fn ->
                    val f = File(logDir, fn)
                    if (f.exists() && f.length() > 0) putZip(zos, fn, f.readBytes())
                }
                // 2. 崩溃日志（私有目录）
                val crash = File(context.filesDir, "crash.log")
                if (crash.exists() && crash.length() > 0) putZip(zos, "crash.log", crash.readBytes())
                // 3. 内核实时日志（内存缓冲，[NAS] 前缀，包含登录/驱动接口响应细节）
                val kernel = NasState.logs.value.joinToString("\n")
                if (kernel.isNotBlank()) putZip(zos, "nas_kernel.log", kernel.toByteArray(Charsets.UTF_8))
                // 4. 设备信息
                val ver = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
                } catch (_: Exception) { "?" }
                val dev = buildString {
                    appendLine("App 名称: 简云plas")
                    appendLine("App 版本: $ver")
                    appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Android: SDK ${Build.VERSION.SDK_INT}")
                    appendLine("内核运行: ${NasState.running.value}")
                    appendLine("内核错误: ${NasState.lastError.value ?: "无"}")
                    appendLine("通信: TCP 127.0.0.1:5244")
                    appendLine("生成时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                }
                putZip(zos, "device.txt", dev.toByteArray(Charsets.UTF_8))
            }
            out
        } catch (_: Exception) {
            null
        }
    }

    private fun putZip(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(bytes)
        zos.closeEntry()
    }
}
