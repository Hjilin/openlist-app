package com.pocketnas.pro.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * APP 本地密码门禁存储（PocketNAS 安全层）。
 *
 * 密码表按「内核完整路径」维护：key = parentPath + "/" + name，
 * 例如 "/影视/剧集"。OpenList 内核不感知该密码，由本层在打开时校验；
 * 解锁状态仅存活于当前目录页面（导航离开即清空，见 FileBrowserScreen）。
 */
class PassStore(context: Context) {

    private val prefs = context.getSharedPreferences("pocketnas_pass", Context.MODE_PRIVATE)

    /** 全量密码表：路径 -> 密码 */
    fun all(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        try {
            val arr = JSONArray(prefs.getString("passwords", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val path = o.optString("path")
                val pwd = o.optString("pwd")
                if (path.isNotEmpty() && pwd.isNotEmpty()) out[path] = pwd
            }
        } catch (_: Exception) {}
        return out
    }

    fun get(path: String): String? = all()[path]

    fun has(path: String): Boolean = get(path) != null

    /** 设置/修改密码；[pwd] 为空时清除该路径密码 */
    fun set(path: String, pwd: String?) {
        val map = all().toMutableMap()
        if (pwd.isNullOrEmpty()) map.remove(path) else map[path] = pwd
        save(map)
    }

    private fun save(map: Map<String, String>) {
        val arr = JSONArray()
        map.forEach { (path, pwd) ->
            arr.put(JSONObject().put("path", path).put("pwd", pwd))
        }
        prefs.edit().putString("passwords", arr.toString()).apply()
    }
}
