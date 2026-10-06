package com.pocketnas.pro.core

import android.content.Context
import java.io.File

/**
 * 内核二进制部署工具。
 *
 * OpenList 内核（arm64 静态 ELF）以 native library 形式打包：
 *   app/src/main/jniLibs/arm64-v8a/libopenlist.so
 * 配合 build.gradle 的 useLegacyPackaging=true，安装时系统解压到
 * nativeLibraryDir（权限 755），app 可直接 exec 启动。
 *
 * 为什么不用 assets 拷贝到 filesDir：MIUI/高版本 Android 的 SELinux 策略
 * 会拒绝 app 进程直接 exec 应用私有目录（app_data_file）下的 ELF
 * （实测 error=13 EACCES；run-as shell 域可执行）。native lib 路径
 * （app_lib_file）是系统允许的标准执行路径，绕开该限制。
 */
object BinaryUtil {

    /** native library 文件名（jniLibs/arm64-v8a/libopenlist.so） */
    const val BIN_FILE_NAME = "libopenlist.so"

    /** 数据/工作目录：filesDir/openlist_data */
    fun dataDir(context: Context): File =
        File(context.filesDir, "openlist_data").apply { mkdirs() }

    /** 内核二进制：nativeLibraryDir/libopenlist.so（系统解压，755 可执行） */
    fun binFile(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, BIN_FILE_NAME)

    /** 内核日志文件 */
    fun logFile(context: Context): File = File(dataDir(context), "openlist.log")

    /** Unix Socket 文件路径（与内核默认配置 data/openlist.sock 对应，相对工作目录） */
    fun socketFile(context: Context): File = File(dataDir(context), "data/openlist.sock")

    /**
     * 确保二进制已部署且可执行。
     * nativeLibraryDir 由系统在安装时解压，仅需校验存在且可执行。
     */
    fun ensureBinary(context: Context): Boolean {
        val bin = binFile(context)
        return bin.exists() && bin.canExecute()
    }

    /** 删除残留 socket 文件（进程异常退出后会残留，需在启动前清理） */
    fun cleanSocket(context: Context) {
        val sock = socketFile(context)
        if (sock.exists()) sock.delete()
    }
}
