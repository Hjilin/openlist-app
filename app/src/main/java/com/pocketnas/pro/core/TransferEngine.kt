package com.pocketnas.pro.core

import android.content.Context
import com.pocketnas.pro.core.LogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * 传输引擎：上传（内核 multipart 分片协议，断点续传）与下载（/api/fs/link + Range，断点续传）。
 *
 * 加固策略：
 * - 上传：分片级幂等重试（429 退避 / 409 在途重试 / 网络错误重试），会话持久化，中断后 status 恢复；
 * - 下载：Range 断点续传，part 文件 + 完成大小校验，失败指数退避重试；
 * - 两者均支持暂停 / 恢复 / 取消，进度与会话状态写入 SharedPreferences，进程被杀后可恢复。
 */
class TransferEngine(
    private val context: Context,
    private val tokenProvider: () -> String?,
    private val onUpdate: (List<TransferEngine.Task>) -> Unit,
) {
    enum class Dir { Up, Down }
    enum class State { Pending, Running, Paused, Done, Error }

    data class Task(
        val id: String,
        val dir: Dir,
        val name: String,
        val path: String,        // 内核路径：上传目标 / 下载源
        val localFile: String,   // 本地文件（上传源 / 下载目标）
        val totalSize: Long,
        val doneBytes: Long,
        val state: State,
        val error: String? = null,
    ) {
        fun progress(): Float =
            if (totalSize <= 0) 0f else (doneBytes.toFloat() / totalSize).coerceIn(0f, 1f)
    }

    private val prefs = context.getSharedPreferences("pocketnas_transfers", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api: OpenListApi
        get() = OpenListApi(LocalHttpClient.tcp())

    private val tasks = LinkedHashMap<String, Task>()
    private val jobs = HashMap<String, Job>()
    private val uploadSessions = HashMap<String, UploadSession>() // id -> 会话（恢复用）

    private data class UploadSession(
        var uploadId: String = "",
        var chunkSize: Long = 0,
        var totalChunks: Int = 0,
    )

    private val taskDir: File = File(context.filesDir, "transfers").apply { mkdirs() }

    // ==================== 任务生命周期 ====================

    /** 恢复上次未完成任务（进程重启 / APP 重启后调用） */
    fun restore() {
        val arr = try { JSONArray(prefs.getString("tasks", "[]")) } catch (_: Exception) { JSONArray() }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isEmpty()) continue
            val dir = if (o.optString("dir") == "down") Dir.Down else Dir.Up
            val state = when (o.optString("state")) {
                "done" -> State.Done
                "error" -> State.Error
                else -> State.Paused
            }
            val t = Task(
                id = id,
                dir = dir,
                name = o.optString("name"),
                path = o.optString("path"),
                localFile = o.optString("local"),
                totalSize = o.optLong("total"),
                doneBytes = o.optLong("done"),
                state = state,
                error = o.optString("error").takeIf { it.isNotBlank() },
            )
            tasks[id] = t
            if (dir == Dir.Up && state == State.Paused) {
                uploadSessions[id] = UploadSession(
                    uploadId = o.optString("uploadId"),
                    chunkSize = o.optLong("chunkSize"),
                    totalChunks = o.optInt("totalChunks"),
                )
            }
        }
        dispatchUpdate()
    }

    fun list(): List<Task> = tasks.values.toList()

    /** 新建下载任务（断点续传） */
    fun startDownload(remotePath: String, name: String, size: Long) {
        LogStore.log("XFER", "开始下载: $name ($size B) <- $remotePath")
        val id = UUID.randomUUID().toString().take(8)
        val local = File(taskDir, sanitize(name)).absolutePath
        val t = Task(id, Dir.Down, name, remotePath, local, size, partSize(local), State.Running)
        tasks[id] = t
        persist()
        jobs[id] = scope.launch { runDownload(t) }
    }

    /** 新建上传任务：[localFile] 必须是可 seek 的本地文件（调用方先复制 Uri 到 transfers/） */
    fun startUpload(remotePath: String, name: String, localFile: String) {
        LogStore.log("XFER", "开始上传: $localFile -> $remotePath")
        val id = UUID.randomUUID().toString().take(8)
        val total = File(localFile).length()
        val t = Task(id, Dir.Up, name, remotePath, localFile, total, 0, State.Running)
        tasks[id] = t
        persist()
        jobs[id] = scope.launch { runUpload(t) }
    }

    fun pause(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        val t = tasks[id] ?: return
        tasks[id] = t.copy(state = State.Paused)
        persist()
        dispatchUpdate()
    }

    fun resume(id: String) {
        val t = tasks[id] ?: return
        if (jobs.containsKey(id)) return
        tasks[id] = t.copy(state = State.Running, error = null)
        persist()
        dispatchUpdate()
        jobs[id] = scope.launch {
            when (t.dir) {
                Dir.Down -> runDownload(tasks[id] ?: t)
                Dir.Up -> runUpload(tasks[id] ?: t)
            }
        }
    }

    fun cancel(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        val t = tasks.remove(id) ?: return
        // 中止上传会话
        if (t.dir == Dir.Up) {
            uploadSessions[id]?.uploadId?.takeIf { it.isNotBlank() }?.let { uid ->
                try { api.multipartAbort(token(), uid) } catch (_: Exception) {}
            }
        }
        runCatching { File(t.localFile).delete() }
        runCatching { File(partOf(t.localFile)).delete() }
        uploadSessions.remove(id)
        persist()
        dispatchUpdate()
    }

    // ==================== 下载实现 ====================

    private suspend fun runDownload(t: Task) {
        var task = t
        var attempt = 0
        while (true) {
            if (jobs[t.id]?.isActive != true) return
            try {
                val link = api.fsLink(token(), task.path)
                    ?: throw IllegalStateException("获取下载链接失败")
                val total = if (link.contentLength > 0) link.contentLength else task.totalSize
                task = task.copy(totalSize = total)
                tasks[t.id] = task
                persist()

                val done = partSize(task.localFile)
                if (total > 0 && done >= total) { finishDownload(task); return }

                val code = downloadInto(task, withRange = true)
                if (code == 416) { finishDownload(task); return } // 服务器认为已完成
                if (code == 200 && done > 0) {
                    // 服务器忽略 Range：从头重写
                    RandomAccessFile(partOf(task.localFile), "rw").use { it.setLength(0) }
                    tasks[t.id] = task.copy(doneBytes = 0)
                    task = tasks[t.id] ?: task
                    downloadInto(task, withRange = false)
                }
                val finalDone = partSize(task.localFile)
                if (total > 0 && finalDone < total) {
                    throw IllegalStateException("下载不完整 ${finalDone}/${total}")
                }
                finishDownload(task)
                return
            } catch (e: Exception) {
                attempt++
                if (attempt >= 4) {
                    fail(t.id, e.message ?: "下载失败")
                    return
                }
                delay(1000L * attempt) // 1s/2s/3s 退避
            }
        }
    }

    /** 执行一次 Range 请求并把响应体写入 part 文件；返回 HTTP 状态码 */
    private suspend fun downloadInto(t: Task, withRange: Boolean): Int {
        val link = api.fsLink(token(), t.path) ?: return -1
        return withContext(Dispatchers.IO) {
            val headers = LinkedHashMap<String, String>()
            if (withRange) headers["Range"] = "bytes=${partSize(t.localFile)}-"
            link.header.forEach { (k, v) -> headers[k] = v }
            if (link.url.startsWith("/")) {
                val stream = apiClient().openStream("GET", link.url, headers, token())
                stream.use { s ->
                    if (s.code !in 200..299) return@withContext s.code
                    writeBodyToPart(t, s.body, s.headers["content-length"]?.toLongOrNull())
                    s.code
                }
            } else {
                val conn = URL(link.url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                try {
                    val code = conn.responseCode
                    if (code in 200..299) {
                        writeBodyToPart(t, conn.inputStream, conn.contentLength.toLong())
                    }
                    code
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    private fun writeBodyToPart(t: Task, input: java.io.InputStream, contentLength: Long?) {
        val part = File(partOf(t.localFile))
        RandomAccessFile(part, "rw").use { raf ->
            raf.seek(raf.length())
            val buf = ByteArray(256 * 1024)
            var expected = contentLength ?: -1
            var wrote = 0L
            while (true) {
                val n = input.read(buf)
                if (n == -1) break
                raf.write(buf, 0, n)
                wrote += n
                val cur = tasks[t.id]
                if (cur != null) {
                    tasks[t.id] = cur.copy(doneBytes = cur.doneBytes + n)
                    if (wrote % (8L * 1024 * 1024) == 0L || (expected > 0 && wrote >= expected)) {
                        persist()
                        dispatchUpdate()
                    }
                }
                if (jobs[t.id]?.isActive != true) break
            }
        }
        persist()
        dispatchUpdate()
    }

    private fun finishDownload(t: Task) {
        val part = File(partOf(t.localFile))
        if (part.exists()) {
            part.renameTo(File(t.localFile))
        }
        tasks[t.id] = t.copy(state = State.Done, doneBytes = t.totalSize)
        jobs.remove(t.id)
        persist()
        dispatchUpdate()
    }

    // ==================== 上传实现 ====================

    private suspend fun runUpload(t: Task) {
        var task = t
        var attempt = 0
        while (true) {
            if (jobs[t.id]?.isActive != true) return
            try {
                val total = task.totalSize
                val file = File(task.localFile)
                val session = uploadSessions[task.id] ?: UploadSession().also { uploadSessions[task.id] = it }

                // 1. 恢复或初始化会话
                if (session.uploadId.isNotBlank()) {
                    val snap = api.multipartStatus(token(), uploadId = session.uploadId)
                    if (snap != null) {
                        session.chunkSize = snap.chunkSize
                        session.totalChunks = snap.totalChunks
                        task = task.copy(doneBytes = snap.receivedBytes)
                        tasks[task.id] = task
                    } else {
                        session.uploadId = "" // 会话失效，重新 init
                    }
                }
                if (session.uploadId.isBlank()) {
                    val init = api.multipartInit(
                        token(), task.path, total, mimeOf(task.name), CHUNK_SIZE
                    ) ?: throw IllegalStateException("初始化上传会话失败")
                    session.uploadId = init.uploadId
                    session.chunkSize = init.chunkSize
                    session.totalChunks = init.totalChunks
                    task = task.copy(doneBytes = init.receivedBytes)
                    tasks[task.id] = task
                    persist()
                }

                // 2. 上传缺失分片（chunk 幂等，可重试）
                val chunkSize = session.chunkSize
                val totalChunks = session.totalChunks
                val received = receivedSet(session)
                var idx = 0
                while (idx < totalChunks) {
                    if (jobs[t.id]?.isActive != true) return
                    if (idx in received) { idx++; continue }
                    val chunk = readChunk(file, idx, chunkSize)
                    if (chunk.isEmpty()) { idx++; continue }
                    val err = withContext(Dispatchers.IO) {
                        try {
                            api.multipartChunk(token(), session.uploadId, idx, chunk)
                        } catch (_: Exception) { "net" }
                    }
                    when (err) {
                        null -> {
                            received.add(idx)
                            val cur = tasks[t.id]
                            if (cur != null) {
                                tasks[t.id] = cur.copy(doneBytes = cur.doneBytes + chunk.size)
                            }
                            persist()
                            dispatchUpdate()
                            idx++
                        }
                        "busy" -> delay(500)
                        "inflight" -> { /* 在途，稍后重试 */ delay(300) }
                        else -> {
                            attempt++
                            if (attempt >= 5) { fail(t.id, "分片 $idx 上传失败: $err"); return }
                            delay(800L * attempt)
                        }
                    }
                }

                // 3. 完成
                val ok = withContext(Dispatchers.IO) { api.multipartComplete(token(), session.uploadId) }
                if (!ok) { fail(t.id, "完成上传失败"); return }
                uploadSessions.remove(t.id)
                runCatching { file.delete() }
                tasks[t.id] = task.copy(state = State.Done, doneBytes = total)
                jobs.remove(t.id)
                persist()
                dispatchUpdate()
                return
            } catch (e: Exception) {
                attempt++
                if (attempt >= 4) { fail(t.id, e.message ?: "上传失败"); return }
                delay(1000L * attempt)
            }
        }
    }

    private fun receivedSet(s: UploadSession): MutableSet<Int> {
        val set = HashSet<Int>()
        try {
            val snap = api.multipartStatus(token(), uploadId = s.uploadId) ?: return set
            s.chunkSize = snap.chunkSize
            s.totalChunks = snap.totalChunks
            snap.received.forEach { (a, b) -> for (i in a..b) set.add(i) }
        } catch (_: Exception) {}
        return set
    }

    private fun readChunk(file: File, idx: Int, chunkSize: Long): ByteArray {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(idx * chunkSize)
            val len = minOf(chunkSize, file.length() - raf.filePointer).toInt()
            if (len <= 0) return ByteArray(0)
            val buf = ByteArray(len)
            var off = 0
            while (off < len) {
                val n = raf.read(buf, off, len - off)
                if (n == -1) break
                off += n
            }
            return buf
        }
    }

    private fun partSize(localFile: String): Long {
        val part = File(partOf(localFile))
        return if (part.exists()) part.length() else 0L
    }

    private fun partOf(localFile: String): String = "$localFile.part"

    private fun fail(id: String, msg: String) {
        val t = tasks[id] ?: return
        tasks[id] = t.copy(state = State.Error, error = msg)
        jobs.remove(id)
        persist()
        dispatchUpdate()
        LogStore.log("XFER", "任务失败 id=$id: $msg")
    }

    // ==================== 持久化 ====================

    private fun persist() {
        val arr = JSONArray()
        tasks.values.forEach { t ->
            val o = JSONObject()
                .put("id", t.id)
                .put("dir", if (t.dir == Dir.Down) "down" else "up")
                .put("name", t.name)
                .put("path", t.path)
                .put("local", t.localFile)
                .put("total", t.totalSize)
                .put("done", t.doneBytes)
                .put("state", when (t.state) {
                    State.Done -> "done"; State.Error -> "error"
                    State.Paused -> "paused"; else -> "running"
                })
                .put("error", t.error ?: "")
            uploadSessions[t.id]?.let { s ->
                o.put("uploadId", s.uploadId)
                    .put("chunkSize", s.chunkSize)
                    .put("totalChunks", s.totalChunks)
            }
            arr.put(o)
        }
        prefs.edit().putString("tasks", arr.toString()).apply()
    }

    private fun dispatchUpdate() {
        onUpdate(tasks.values.toList())
    }

    private fun token(): String = tokenProvider() ?: throw IllegalStateException("未登录")

    private fun mimeOf(name: String): String = when {
        name.endsWith(".mp4") || name.endsWith(".mkv") -> "video/mp4"
        name.endsWith(".mp3") -> "audio/mpeg"
        name.endsWith(".png") -> "image/png"
        name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
        else -> "application/octet-stream"
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "file" }

    private fun apiClient(): LocalHttpClient = LocalHttpClient.tcp()

    companion object {
        const val CHUNK_SIZE = 4L * 1024 * 1024 // 4MB 分片
    }
}
