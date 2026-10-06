package com.pocketnas.pro.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenList 内核业务 API 封装（基于 [LocalHttpClient]，全部走 Unix Domain Socket）。
 *
 * 响应统一结构：{"code":200,"message":"success","data":{...}}
 */
class OpenListApi(private val client: LocalHttpClient) {

    data class NasStatus(
        val version: String,
        val builtAt: String,
        val uptime: Long,
        val dataDir: String,
        val pid: Int,
        val listenMode: String,
        val httpPort: Int,
    )

    data class UserInfo(
        val id: Int,
        val username: String,
        val isAdmin: Boolean,
        val isGuest: Boolean,
        val disabled: Boolean,
    )

    data class StorageInfo(
        val id: Int,
        val mountPath: String,
        val driver: String,
        val remark: String,
        val enabled: Boolean,
    )

    /**
     * 安装向导：系统未初始化时创建管理员（POST /api/public/init/setup）。
     * 返回 true=初始化成功；false=已初始化或失败。
     */
    fun initSetup(username: String, password: String): Boolean {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()
        val resp = client.post("/api/public/init/setup", body)
        if (resp.code !in 200..299) return false
        val json = JSONObject(resp.body)
        return json.optInt("code") == 200
    }

    /**
     * 管理员登录，返回 token；失败返回 null。
     */
    fun login(username: String, password: String): String? {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()
        val resp = client.post("/api/auth/login", body)
        if (resp.code !in 200..299) return null
        val json = JSONObject(resp.body)
        if (json.optInt("code") != 200) return null
        return json.optJSONObject("data")?.optString("token")?.takeIf { it.isNotBlank() }
    }

    /**
     * 认证探测：校验 token 是否仍有效（GET /api/me）。
     * 返回 false = token 已失效/被吊销（如同账号在别处重新登录后旧 token 被内核吊销），
     * 此时需要重新登录换取新 token。
     */
    fun authCheck(token: String): Boolean {
        val resp = try {
            client.get("/api/me", token)
        } catch (_: Exception) {
            return false
        }
        if (resp.code !in 200..299) return false
        return try {
            JSONObject(resp.body).optInt("code") == 200
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 获取内核运行状态。
     */
    fun nasStatus(): NasStatus? {
        val resp = client.get("/api/nas/status")
        if (resp.code !in 200..299) return null
        val json = JSONObject(resp.body)
        if (json.optInt("code") != 200) return null
        val data = json.optJSONObject("data") ?: return null
        val scheme = data.optJSONObject("scheme")
        return NasStatus(
            version = data.optString("version"),
            builtAt = data.optString("built_at"),
            uptime = data.optLong("uptime"),
            dataDir = data.optString("data_dir"),
            pid = data.optInt("pid"),
            listenMode = data.optString("listen_mode"),
            httpPort = scheme?.optInt("http_port") ?: -1,
        )
    }

    /**
     * 获取用户列表（管理员）。
     */
    fun listUsers(token: String): List<UserInfo> {
        val resp = client.get("/api/admin/user/list?page=1&per_page=100", token)
        val list = mutableListOf<UserInfo>()
        if (resp.code !in 200..299) return list
        val json = try { JSONObject(resp.body) } catch (_: Exception) { return list }
        if (json.optInt("code") != 200) return list
        val content = json.optJSONObject("data")?.optJSONArray("content") ?: return list
        for (i in 0 until content.length()) {
            val u = content.optJSONObject(i) ?: continue
            list += UserInfo(
                id = u.optInt("id"),
                username = u.optString("username"),
                isAdmin = u.optBoolean("is_admin"),
                isGuest = u.optBoolean("is_guest"),
                disabled = u.optBoolean("disabled"),
            )
        }
        return list
    }

    /**
     * 修改用户密码（管理员接口，也用于重置 admin 密码 ——
     * 彻底解决命令行/网页重置密码失效问题）。
     */
    fun updateUserPassword(token: String, userId: Int, newPassword: String): Boolean {
        val body = JSONObject()
            .put("id", userId)
            .put("password", newPassword)
            .toString()
        val resp = client.post("/api/admin/user/update", body, token)
        if (resp.code !in 200..299) return false
        return try {
            JSONObject(resp.body).optInt("code") == 200
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 获取存储源列表（管理员）。
     */
    fun listStorages(token: String): List<StorageInfo> {
        val resp = client.get("/api/admin/storage/list?page=1&per_page=100", token)
        val list = mutableListOf<StorageInfo>()
        if (resp.code !in 200..299) return list
        val json = try { JSONObject(resp.body) } catch (_: Exception) { return list }
        if (json.optInt("code") != 200) return list
        val content = json.optJSONObject("data")?.optJSONArray("content") ?: return list
        for (i in 0 until content.length()) {
            val s = content.optJSONObject(i) ?: continue
            list += StorageInfo(
                id = s.optInt("id"),
                mountPath = s.optString("mount_path"),
                driver = s.optString("driver"),
                remark = s.optString("remark"),
                enabled = s.optBoolean("enabled"),
            )
        }
        return list
    }

    /**
     * 删除存储源（管理员）。
     */
    fun deleteStorage(token: String, storageId: Int): Boolean {
        val body = JSONObject().put("id", storageId).toString()
        val resp = client.post("/api/admin/storage/delete", body, token)
        if (resp.code !in 200..299) return false
        return try {
            JSONObject(resp.body).optInt("code") == 200
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 简单连通性测试。
     */
    fun ping(): Boolean {
        val resp = try {
            client.get("/ping")
        } catch (_: Exception) {
            return false
        }
        return resp.code == 200 && resp.body.contains("pong")
    }

    // ==================== 存储源添加（驱动表单） ====================

    /** 驱动字段模板 */
    data class DriverField(
        val name: String,
        val type: String,      // string / text / number / bool / select
        val default: String,
        val options: List<String>,
        val required: Boolean,
        val help: String,
    )

    /** 驱动完整信息 */
    data class DriverInfo(
        val name: String,
        val common: List<DriverField>,
        val additional: List<DriverField>,
        val noUpload: Boolean,
        val defaultRoot: String,
        val alert: String,
    )

    /**
     * 可用驱动列表（名称）。
     */
    fun listDriverNames(token: String): List<String> {
        val resp = client.get("/api/admin/driver/names", token)
        val list = mutableListOf<String>()
        if (resp.code !in 200..299) return list
        val json = try { JSONObject(resp.body) } catch (_: Exception) { return list }
        if (json.optInt("code") != 200) return list
        val arr = json.optJSONArray("data") ?: return list
        for (i in 0 until arr.length()) {
            arr.optString(i).takeIf { it.isNotBlank() }?.let { list += it }
        }
        return list
    }

    /**
     * 获取指定驱动的字段模板（用于动态表单渲染）。
     */
    fun getDriverInfo(token: String, driver: String): DriverInfo? {
        val resp = client.get("/api/admin/driver/info?driver=${urlEncode(driver)}", token)
        if (resp.code !in 200..299) return null
        val json = try { JSONObject(resp.body) } catch (_: Exception) { return null }
        if (json.optInt("code") != 200) return null
        val data = json.optJSONObject("data") ?: return null
        val config = data.optJSONObject("config")
        return DriverInfo(
            name = driver,
            common = parseFields(data.optJSONArray("common")),
            additional = parseFields(data.optJSONArray("additional")),
            noUpload = config?.optBoolean("no_upload") ?: false,
            defaultRoot = config?.optString("default_root") ?: "",
            alert = config?.optString("alert") ?: "",
        )
    }

    private fun parseFields(arr: JSONArray?): List<DriverField> {
        if (arr == null) return emptyList()
        val fields = mutableListOf<DriverField>()
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            fields += DriverField(
                name = f.optString("name"),
                type = f.optString("type", "string"),
                default = f.optString("default"),
                options = f.optString("options")
                    .split(',').map { it.trim() }.filter { it.isNotEmpty() },
                required = f.optBoolean("required"),
                help = f.optString("help"),
            )
        }
        return fields
    }

    /**
     * 创建存储源（完整表单：公共字段与驱动附加字段均由前端动态表单生成）。
     * [commonJson]：公共字段 JSON 对象字符串（mount_path/remark/order/cache_expiration/webdav_policy...）
     * [additionJson]：驱动附加字段 JSON 对象字符串。
     */
    fun createStorageFull(
        token: String,
        driver: String,
        commonJson: String?,
        additionJson: String?,
    ): Boolean {
        val body = JSONObject()
            .put("driver", driver)
        if (!commonJson.isNullOrBlank()) {
            try {
                val common = JSONObject(commonJson)
                common.keys().forEach { key -> body.put(key, common.get(key)) }
            } catch (_: Exception) {}
        }
        if (!additionJson.isNullOrBlank()) {
            body.put("addition", additionJson)
        }
        val resp = client.post("/api/admin/storage/create", body.toString(), token)
        if (resp.code !in 200..299) return false
        return try {
            JSONObject(resp.body).optInt("code") == 200
        } catch (_: Exception) {
            false
        }
    }

    // ==================== 文件浏览器 ====================

    /** 文件/目录对象 */
    data class FsObj(
        val name: String,
        val size: Long,
        val isDir: Boolean,
        val modified: String,
        val sign: String,
        val thumb: String,
        val type: Int,
    )

    /** 目录列表响应 */
    data class FsListResult(
        val content: List<FsObj>,
        val write: Boolean,
        val provider: String,
    )

    /**
     * 列目录。path 从挂载点开始，如 "/" 或 "/网盘/影视"。
     */
    fun fsList(token: String, path: String): FsListResult? {
        val body = JSONObject()
            .put("path", path)
            .put("password", "")
            .put("page", 1)
            .put("per_page", 200)
            .put("refresh", false)
            .toString()
        val resp = client.post("/api/fs/list", body, token)
        if (resp.code !in 200..299) return null
        val json = try { JSONObject(resp.body) } catch (_: Exception) { return null }
        if (json.optInt("code") != 200) return null
        val data = json.optJSONObject("data") ?: return null
        val arr = data.optJSONArray("content") ?: JSONArray()
        val content = mutableListOf<FsObj>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            content += FsObj(
                name = o.optString("name"),
                size = o.optLong("size"),
                isDir = o.optBoolean("is_dir"),
                modified = o.optString("modified"),
                sign = o.optString("sign"),
                thumb = o.optString("thumb"),
                type = o.optInt("type"),
            )
        }
        return FsListResult(
            content = content,
            write = data.optBoolean("write"),
            provider = data.optString("provider"),
        )
    }

    /** 新建文件夹（path 为完整新目录路径） */
    fun fsMkdir(token: String, path: String): Boolean {
        val body = JSONObject().put("path", path).toString()
        val resp = client.post("/api/fs/mkdir", body, token)
        if (resp.code !in 200..299) return false
        return try { JSONObject(resp.body).optInt("code") == 200 } catch (_: Exception) { false }
    }

    /** 重命名 */
    fun fsRename(token: String, path: String, newName: String): Boolean {
        val body = JSONObject()
            .put("path", path)
            .put("name", newName)
            .put("overwrite", false)
            .toString()
        val resp = client.post("/api/fs/rename", body, token)
        if (resp.code !in 200..299) return false
        return try { JSONObject(resp.body).optInt("code") == 200 } catch (_: Exception) { false }
    }

    /** 删除（dir 为父目录，names 为要删除的名字列表） */
    fun fsRemove(token: String, dir: String, names: List<String>): Boolean {
        val body = JSONObject()
            .put("dir", dir)
            .put("names", JSONArray(names))
            .toString()
        val resp = client.post("/api/fs/remove", body, token)
        if (resp.code !in 200..299) return false
        return try { JSONObject(resp.body).optInt("code") == 200 } catch (_: Exception) { false }
    }

    /** 保存单个系统设置（如 ftp_public_host），body 为 SettingItem 数组 */
    fun saveSetting(token: String, key: String, value: String): Boolean {
        val item = JSONObject()
            .put("key", key)
            .put("value", value)
            .put("type", 0)
            .put("group", 0)
            .put("flag", 0)
        val body = JSONArray().put(item).toString()
        val resp = client.post("/api/admin/setting/save", body, token)
        if (resp.code !in 200..299) return false
        return try { JSONObject(resp.body).optInt("code") == 200 } catch (_: Exception) { false }
    }

    /**
     * 上传文件（multipart/form-data 流式上传，走 /api/fs/form）。
     * [filePath] 为目标完整路径；[content] 为文件输入流（由调用方负责关闭）。
     */
    fun uploadFile(
        token: String,
        filePath: String,
        fileName: String,
        mimeType: String,
        content: java.io.InputStream,
    ): Boolean {
        val boundary = "----PocketNAS" + System.nanoTime()
        val encodedPath = urlEncode(filePath)
        val headers = mapOf(
            "Content-Type" to "multipart/form-data; boundary=$boundary",
            "File-Path" to encodedPath,
            "Overwrite" to "true",
            "As-Task" to "false",
        )
        val resp = client.requestStream("PUT", "/api/fs/form", headers, token) { out ->
            val head = "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n" +
                "Content-Type: $mimeType\r\n\r\n"
            out.write(head.toByteArray(Charsets.UTF_8))
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = content.read(buf)
                if (n == -1) break
                out.write(buf, 0, n)
            }
            out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
        }
        if (resp.code !in 200..299) return false
        return try { JSONObject(resp.body).optInt("code") == 200 } catch (_: Exception) { false }
    }

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    // ==================== 下载 / 分片上传（断点续传） ====================

    /** 下载链接信息：url 为 "/d/..."（走本地 socket）或 http(s) 直链 */
    data class LinkInfo(
        val url: String,
        val header: Map<String, String>,
        val contentLength: Long,
        val partSize: Int,
        val concurrency: Int,
    )

    /**
     * 获取下载链接。POST /api/fs/link {path}
     */
    fun fsLink(token: String, path: String): LinkInfo? {
        val body = JSONObject().put("path", path).toString()
        val resp = client.post("/api/fs/link", body, token)
        if (resp.code !in 200..299) return null
        val json = try { JSONObject(resp.body) } catch (_: Exception) { return null }
        if (json.optInt("code") != 200) return null
        val data = json.optJSONObject("data") ?: return null
        val header = LinkedHashMap<String, String>()
        data.optJSONObject("header")?.let { h ->
            h.keys().forEach { k -> header[k] = h.optString(k) }
        }
        return LinkInfo(
            url = data.optString("url"),
            header = header,
            contentLength = data.optLong("content_length"),
            partSize = data.optInt("part_size"),
            concurrency = data.optInt("concurrency"),
        )
    }

    /** 分片上传会话快照 */
    data class MultipartSession(
        val uploadId: String,
        val state: String,
        val path: String,
        val size: Long,
        val chunkSize: Long,
        val totalChunks: Int,
        val received: List<Pair<Int, Int>>,
        val receivedBytes: Long,
        val storageProgress: Double,
        val resumed: Boolean,
    )

    /**
     * 初始化（或恢复）分片上传会话。
     * headers：File-Path(URL 编码)、X-File-Size、X-Chunk-Size(可选)、Overwrite(可选)、Content-Type
     */
    fun multipartInit(
        token: String,
        filePath: String,
        size: Long,
        mimeType: String,
        chunkSize: Long,
        overwrite: Boolean = true,
    ): MultipartSession? {
        val headers = mapOf(
            "File-Path" to urlEncode(filePath),
            "X-File-Size" to size.toString(),
            "X-Chunk-Size" to chunkSize.toString(),
            "Overwrite" to if (overwrite) "true" else "false",
            "Content-Type" to mimeType,
        )
        val resp = client.request("POST", "/api/fs/multipart/init", null, token, headers)
        return parseSession(resp)
    }

    /**
     * 上传一个分片（幂等：重复上传同一 index 安全）。
     * 返回 null = 成功；否则返回错误提示（429 需退避重试）。
     */
    fun multipartChunk(
        token: String,
        uploadId: String,
        index: Int,
        data: ByteArray,
    ): String? {
        val headers = mapOf(
            "X-Upload-Id" to uploadId,
            "X-Chunk-Index" to index.toString(),
            "Content-Type" to "application/octet-stream",
        )
        val resp = client.request("PUT", "/api/fs/multipart/chunk", null, token, headers, rawBody = data)
        if (resp.code == 429) return "busy"
        if (resp.code == 409) return "inflight"
        if (resp.code !in 200..299) return "http_${resp.code}"
        return try {
            if (JSONObject(resp.body).optInt("code") == 200) null else "rejected"
        } catch (_: Exception) {
            "bad_resp"
        }
    }

    /** 完成分片上传，返回是否成功 */
    fun multipartComplete(token: String, uploadId: String): Boolean {
        val resp = client.request(
            "POST", "/api/fs/multipart/complete", null, token,
            mapOf("X-Upload-Id" to uploadId),
        )
        if (resp.code !in 200..299) return false
        return try { JSONObject(resp.body).optInt("code") == 200 } catch (_: Exception) { false }
    }

    /** 查询会话（按 upload_id 或 path+size，用于断点发现） */
    fun multipartStatus(token: String, uploadId: String? = null, path: String? = null, size: Long = 0): MultipartSession? {
        val q = when {
            !uploadId.isNullOrBlank() -> "upload_id=${urlEncode(uploadId)}"
            !path.isNullOrBlank() -> "path=${urlEncode(path)}&size=$size"
            else -> return null
        }
        val resp = client.get("/api/fs/multipart/status?$q", token)
        val snap = parseSession(resp)
        return snap?.copy(resumed = true)
    }

    /** 中止会话（取消上传） */
    fun multipartAbort(token: String, uploadId: String): Boolean {
        val resp = client.request(
            "POST", "/api/fs/multipart/abort", null, token,
            mapOf("X-Upload-Id" to uploadId),
        )
        if (resp.code !in 200..299) return false
        return try { JSONObject(resp.body).optInt("code") == 200 } catch (_: Exception) { false }
    }

    private fun parseSession(resp: LocalHttpClient.Response): MultipartSession? {
        if (resp.code !in 200..299) return null
        val json = try { JSONObject(resp.body) } catch (_: Exception) { return null }
        if (json.optInt("code") != 200) return null
        val d = json.optJSONObject("data") ?: return null
        val received = mutableListOf<Pair<Int, Int>>()
        d.optJSONArray("received")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.optJSONArray(i) ?: continue
                if (p.length() >= 2) received += (p.optInt(0) to p.optInt(1))
            }
        }
        return MultipartSession(
            uploadId = d.optString("upload_id"),
            state = d.optString("state"),
            path = d.optString("path"),
            size = d.optLong("size"),
            chunkSize = d.optLong("chunk_size"),
            totalChunks = d.optInt("total_chunks"),
            received = received,
            receivedBytes = d.optLong("received_bytes"),
            storageProgress = d.optDouble("storage_progress", 0.0),
            resumed = d.optBoolean("resumed"),
        )
    }

    // ==================== 文件元信息与预览 ====================

    data class FsGetInfo(
        val name: String,
        val size: Long,
        val isDir: Boolean,
        val modified: String,
        val rawUrl: String,
        val sign: String,
        val thumb: String,
        val type: Int,
    )

    /**
     * 文件/目录元信息。GET /api/fs/get?path=...
     * data.raw_url 为下载地址（本地内核为 "/d/..."，可走 socket 直读）。
     */
    fun fsGet(token: String, path: String): FsGetInfo? {
        val resp = client.get("/api/fs/get?path=${urlEncode(path)}", token)
        LogStore.log("API", "fsGet path=$path http=${resp.code} body=${resp.body.take(160)}")
        if (resp.code !in 200..299) return null
        val json = try { JSONObject(resp.body) } catch (_: Exception) { return null }
        if (json.optInt("code") != 200) return null
        val d = json.optJSONObject("data") ?: return null
        val o = d.optJSONObject("obj") ?: d
        return FsGetInfo(
            name = o.optString("name"),
            size = o.optLong("size"),
            isDir = o.optBoolean("is_dir"),
            modified = o.optString("modified"),
            rawUrl = d.optString("raw_url"),
            sign = o.optString("sign"),
            thumb = o.optString("thumb"),
            type = o.optInt("type"),
        )
    }

    /**
     * 读取文件开头 [maxBytes] 字节（文本预览用）。
     * raw_url 为 "/d/..." 时走本地 socket；为 http(s) 直链时走 HttpURLConnection。
     */
    fun fsGetText(token: String, path: String, maxBytes: Int = 64 * 1024): String? {
        return fsGetBytes(token, path, maxBytes)?.toString(Charsets.UTF_8)
    }

    /**
     * 读取文件开头 [maxBytes] 字节（二进制，docx/xlsx/pdf 文档渲染用）。
     */
    fun fsGetBytes(token: String, path: String, maxBytes: Int = 20 * 1024 * 1024): ByteArray? {
        val info = fsGet(token, path) ?: return null
        if (info.isDir) return null
        var url = info.rawUrl
        // unix socket 请求无端口，内核 raw_url 回填 localhost:80 → 修正为内核 TCP 端口
        url = url.replace("http://localhost:80/", "http://127.0.0.1:5244/")
            .replace("http://localhost/", "http://127.0.0.1:5244/")
            .replace("http://127.0.0.1:80/", "http://127.0.0.1:5244/")
            .replace("http://127.0.0.1/", "http://127.0.0.1:5244/")
        LogStore.log("API", "fsGetBytes rawUrl=$url")
        if (url.isBlank()) return null
        return try {
            if (url.startsWith("/")) {
                val stream = client.openStream("GET", url, emptyMap(), token)
                stream.use { s ->
                    if (s.code !in 200..299) return null
                    val buf = ByteArray(maxBytes)
                    val n = s.body.read(buf)
                    buf.copyOf(maxOf(n, 0))
                }
            } else {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                try {
                    if (conn.responseCode !in 200..299) return null
                    val buf = ByteArray(maxBytes)
                    val n = conn.inputStream.use { it.read(buf) }
                    buf.copyOf(maxOf(n, 0))
                } finally {
                    conn.disconnect()
                }
            }
        } catch (e: Exception) {
            LogStore.log("API", "fsGetBytes FAIL $url: $e")
            null
        }
    }
}
