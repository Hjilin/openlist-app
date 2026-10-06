package com.pocketnas.pro.core

import android.content.Context
import android.content.Context.MODE_PRIVATE
import com.pocketnas.pro.core.LogStore
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 远程访问服务（内核原生 FTP / SFTP / WebDAV 开关）。
 *
 * OpenList 内核内置 FTP（:5221）与 SFTP（:5222）服务，属启动期配置，
 * 支持环境变量注入（FTP_ENABLE / FTP_LISTEN、SFTP_ENABLE / SFTP_LISTEN）。
 * WebDAV 挂在主 HTTP 路由 /dav 下，通过 OPENLIST_HTTP_PORT 打开 TCP 监听
 * （默认地址 0.0.0.0）；APP 已注释内核静态页面与 NoRoute，打开后无网页 UI，
 * 仅暴露 API 与 WebDAV。本类负责：持久化开关、构造启动环境变量、探测局域网 IPv4。
 */
object RemoteAccess {

    const val FTP_PORT = 5221
    const val SFTP_PORT = 5222
    const val WEBDAV_PORT = 5244

    private const val PREF_NAME = "pocketnas_remote"
    private const val KEY_FTP_ENABLE = "ftp_enable"
    private const val KEY_SFTP_ENABLE = "sftp_enable"
    private const val KEY_WEBDAV_ENABLE = "webdav_enable"

    fun isFtpEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .getBoolean(KEY_FTP_ENABLE, false)

    fun setFtpEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .edit().putBoolean(KEY_FTP_ENABLE, enabled).apply()
        LogStore.log("REMOTE", "FTP ${if (enabled) "开启" else "关闭"}")
    }

    fun isSftpEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .getBoolean(KEY_SFTP_ENABLE, false)

    fun setSftpEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .edit().putBoolean(KEY_SFTP_ENABLE, enabled).apply()
        LogStore.log("REMOTE", "SFTP ${if (enabled) "开启" else "关闭"}")
    }

    fun isWebdavEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .getBoolean(KEY_WEBDAV_ENABLE, false)

    fun setWebdavEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .edit().putBoolean(KEY_WEBDAV_ENABLE, enabled).apply()
        LogStore.log("REMOTE", "WebDAV ${if (enabled) "开启" else "关闭"}")
    }

    /**
     * 启动内核进程时注入的环境变量（仅注入已开启的服务）。
     */
    fun env(context: Context): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (isFtpEnabled(context)) {
            map["OPENLIST_FTP_ENABLE"] = "true"
            map["OPENLIST_FTP_LISTEN"] = ":$FTP_PORT"
        }
        if (isSftpEnabled(context)) {
            map["OPENLIST_SFTP_ENABLE"] = "true"
            map["OPENLIST_SFTP_LISTEN"] = ":$SFTP_PORT"
        }
        if (isWebdavEnabled(context)) {
            // WebDAV 挂在主 HTTP 路由 /dav；打开 TCP 监听（地址默认 0.0.0.0）
            map["OPENLIST_HTTP_PORT"] = WEBDAV_PORT.toString()
        }
        return map
    }

    /**
     * 探测局域网 IPv4 地址（排除回环与链路本地）。
     */
    fun lanIp(): String? {
        return try {
            val enums = NetworkInterface.getNetworkInterfaces()
            while (enums.hasMoreElements()) {
                val nif = enums.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
