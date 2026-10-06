package com.pocketnas.pro.core

import android.content.Context
import com.pocketnas.pro.service.OpenListService
import org.json.JSONObject

/**
 * 全局设置统一存储门面。
 *
 * 目标：所有功能读写配置只经过本类，禁止散落 getSharedPreferences。
 * - 已有服务类开关（开机自启/崩溃重启 → OpenListService，FTP/SFTP → RemoteAccess）
 *   委托原存储，保证历史数据不丢失；
 * - 新增设置（N2N / 媒体库 / 应用类）统一落在 prefs "pocketnas_settings"。
 *
 * 后期新增功能：只需在本类加一个伴生常量键 + get/set 方法，页面层零散改动。
 */
object AppSettingStore {

    private const val PREFS = "pocketnas_settings"

    // ---- 应用层（预留）----
    const val KEY_THEME = "theme"                 // light / dark / system
    const val KEY_LANGUAGE = "language"           // zh / en / system

    // ---- N2N（Hin2n 适配，APP 层配置）----
    const val KEY_N2N_SUPERNODE = "n2n_supernode"
    const val KEY_N2N_COMMUNITY = "n2n_community"
    const val KEY_N2N_SECRET = "n2n_secret"
    const val KEY_N2N_VIRTUAL_IP = "n2n_virtual_ip"
    const val KEY_N2N_CONNECTED = "n2n_connected"

    // ---- 登录记忆 ----
    const val KEY_LOGIN_REMEMBER = "login_remember"               // 记住账号密码（默认开）
    const val KEY_LOGIN_USERNAME = "login_username"
    const val KEY_LOGIN_PASSWORD = "login_password"

    fun isLoginRemember(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOGIN_REMEMBER, true)

    fun setLoginRemember(context: Context, v: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LOGIN_REMEMBER, v).apply()
    }

    fun getSavedLogin(context: Context): Pair<String, String> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val u = p.getString(KEY_LOGIN_USERNAME, "admin") ?: "admin"
        val pw = p.getString(KEY_LOGIN_PASSWORD, "admin123456") ?: "admin123456"
        return u to pw
    }

    fun saveLogin(context: Context, username: String, password: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOGIN_USERNAME, username)
            .putString(KEY_LOGIN_PASSWORD, password)
            .apply()
    }

    fun clearSavedLogin(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LOGIN_USERNAME)
            .remove(KEY_LOGIN_PASSWORD)
            .apply()
    }

    // ---- 后台保活 ----
    const val KEY_KEEPALIVE_WORKER = "keepalive_worker"           // WorkManager 周期保活（默认开）

    fun isKeepAliveWorker(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEEPALIVE_WORKER, true)

    fun setKeepAliveWorker(context: Context, v: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_KEEPALIVE_WORKER, v).apply()
    }

    // ---- 媒体库 ----
    const val KEY_MEDIA_THUMB_CACHE_MB = "media_thumb_cache_mb"   // 缩略图缓存上限（MB）
    const val KEY_MEDIA_EXCLUDE_DIRS = "media_exclude_dirs"       // 排除目录（逗号分隔）

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ==================== 服务开关（委托原存储） ====================

    fun isBootStart(context: Context) = OpenListService.isBootStartEnabled(context)
    fun setBootStart(context: Context, v: Boolean) = OpenListService.setBootStart(context, v)

    fun isAutoRestart(context: Context) = OpenListService.isAutoRestartEnabled(context)
    fun setAutoRestart(context: Context, v: Boolean) = OpenListService.setAutoRestart(context, v)

    fun isFtpEnabled(context: Context) = RemoteAccess.isFtpEnabled(context)
    fun setFtpEnabled(context: Context, v: Boolean) = RemoteAccess.setFtpEnabled(context, v)

    fun isSftpEnabled(context: Context) = RemoteAccess.isSftpEnabled(context)
    fun setSftpEnabled(context: Context, v: Boolean) = RemoteAccess.setSftpEnabled(context, v)

    fun isWebdavEnabled(context: Context) = RemoteAccess.isWebdavEnabled(context)
    fun setWebdavEnabled(context: Context, v: Boolean) = RemoteAccess.setWebdavEnabled(context, v)

    // ==================== N2N ====================

    fun n2nSupernode(context: Context) =
        prefs(context).getString(KEY_N2N_SUPERNODE, "wang.switchy.hin2n.stars:10086") ?: "wang.switchy.hin2n.stars:10086"

    fun setN2nSupernode(context: Context, v: String) =
        prefs(context).edit().putString(KEY_N2N_SUPERNODE, v).apply()

    fun n2nCommunity(context: Context) =
        prefs(context).getString(KEY_N2N_COMMUNITY, "nas-network") ?: "nas-network"

    fun setN2nCommunity(context: Context, v: String) =
        prefs(context).edit().putString(KEY_N2N_COMMUNITY, v).apply()

    fun n2nSecret(context: Context) =
        prefs(context).getString(KEY_N2N_SECRET, "nas123456") ?: "nas123456"

    fun setN2nSecret(context: Context, v: String) =
        prefs(context).edit().putString(KEY_N2N_SECRET, v).apply()

    fun n2nVirtualIp(context: Context) =
        prefs(context).getString(KEY_N2N_VIRTUAL_IP, "192.168.100.1") ?: "192.168.100.1"

    fun setN2nVirtualIp(context: Context, v: String) =
        prefs(context).edit().putString(KEY_N2N_VIRTUAL_IP, v).apply()

    fun isN2nConnected(context: Context) =
        prefs(context).getBoolean(KEY_N2N_CONNECTED, false)

    fun setN2nConnected(context: Context, v: Boolean) =
        prefs(context).edit().putBoolean(KEY_N2N_CONNECTED, v).apply()

    // ==================== 媒体库 ====================

    fun mediaThumbCacheMb(context: Context) =
        prefs(context).getInt(KEY_MEDIA_THUMB_CACHE_MB, 64)

    fun setMediaThumbCacheMb(context: Context, mb: Int) =
        prefs(context).edit().putInt(KEY_MEDIA_THUMB_CACHE_MB, mb).apply()

    fun mediaExcludeDirs(context: Context) =
        prefs(context).getString(KEY_MEDIA_EXCLUDE_DIRS, "") ?: ""

    fun setMediaExcludeDirs(context: Context, v: String) =
        prefs(context).edit().putString(KEY_MEDIA_EXCLUDE_DIRS, v).apply()


    // ==================== 语言 ====================
    const val KEY_LANG = "app_language"  // system / zh / en

    fun getLang(context: Context): String =
        prefs(context).getString(KEY_LANG, "system") ?: "system"

    fun setLang(context: Context, lang: String) {
        prefs(context).edit().putString(KEY_LANG, lang).apply()
    }

    // ==================== 配置导入 / 导出 ====================

    /** 导出全部设置为 JSON 字符串（不含 token 等敏感凭据） */
    fun exportJson(context: Context): String {
        val p = prefs(context)
        val o = JSONObject()
        o.put(KEY_THEME, p.getString(KEY_THEME, "system"))
        o.put(KEY_LANGUAGE, p.getString(KEY_LANGUAGE, "system"))
        o.put(KEY_N2N_SUPERNODE, n2nSupernode(context))
        o.put(KEY_N2N_COMMUNITY, n2nCommunity(context))
        o.put(KEY_N2N_SECRET, n2nSecret(context))
        o.put(KEY_N2N_VIRTUAL_IP, n2nVirtualIp(context))
        o.put(KEY_N2N_CONNECTED, isN2nConnected(context))
        o.put(KEY_MEDIA_THUMB_CACHE_MB, mediaThumbCacheMb(context))
        o.put(KEY_MEDIA_EXCLUDE_DIRS, mediaExcludeDirs(context))
        o.put("boot_start", isBootStart(context))
        o.put("auto_restart", isAutoRestart(context))
        o.put("ftp_enable", isFtpEnabled(context))
        o.put("sftp_enable", isSftpEnabled(context))
        return o.toString()
    }

    /** 从 JSON 恢复配置；返回恢复条数 */
    fun importJson(context: Context, json: String): Int {
        return try {
            val o = JSONObject(json)
            val e = prefs(context).edit()
            var n = 0
            fun putStr(k: String) { if (o.has(k)) { e.putString(k, o.getString(k)); n++ } }
            fun putBool(k: String) { if (o.has(k)) { e.putBoolean(k, o.getBoolean(k)); n++ } }
            fun putInt(k: String) { if (o.has(k)) { e.putInt(k, o.getInt(k)); n++ } }
            putStr(KEY_THEME); putStr(KEY_LANGUAGE)
            putStr(KEY_N2N_SUPERNODE); putStr(KEY_N2N_COMMUNITY); putStr(KEY_N2N_SECRET); putStr(KEY_N2N_VIRTUAL_IP)
            putBool(KEY_N2N_CONNECTED)
            putInt(KEY_MEDIA_THUMB_CACHE_MB); putStr(KEY_MEDIA_EXCLUDE_DIRS)
            e.apply()
            // 服务类开关：写回原存储
            if (o.has("boot_start")) setBootStart(context, o.getBoolean("boot_start"))
            if (o.has("auto_restart")) setAutoRestart(context, o.getBoolean("auto_restart"))
            if (o.has("ftp_enable")) setFtpEnabled(context, o.getBoolean("ftp_enable"))
            if (o.has("sftp_enable")) setSftpEnabled(context, o.getBoolean("sftp_enable"))
            n
        } catch (_: Exception) {
            0
        }
    }
}
