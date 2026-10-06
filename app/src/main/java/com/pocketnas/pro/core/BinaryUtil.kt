package com.pocketnas.pro.core

import android.content.Context
import java.io.File

object BinaryUtil {

    const val BIN_FILE_NAME = "libopenlist.so"

    fun dataDir(context: Context): File =
        File(context.filesDir, "openlist_data").apply { mkdirs() }

    fun binFile(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, BIN_FILE_NAME)

    fun logFile(context: Context): File = File(dataDir(context), "openlist.log")

    fun socketFile(context: Context): File = File(dataDir(context), "data/openlist.sock")

    fun ensureBinary(context: Context): Boolean {
        val bin = binFile(context)
        return bin.exists() && bin.canExecute()
    }

    fun cleanSocket(context: Context) {
        val sock = socketFile(context)
        if (sock.exists()) sock.delete()
    }

    fun clearData(context: Context) {
        dataDir(context).deleteRecursively()
        dataDir(context).mkdirs()
    }
}
