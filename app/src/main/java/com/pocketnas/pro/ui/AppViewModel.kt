package com.pocketnas.pro.ui

import android.app.Application
import android.content.Context
import android.os.PowerManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pocketnas.pro.core.AppSettingStore
import com.pocketnas.pro.core.BinaryUtil
import com.pocketnas.pro.core.LogStore
import com.pocketnas.pro.core.KernelHttpProxy
import com.pocketnas.pro.core.LocalHttpClient
import com.pocketnas.pro.core.NasState
import com.pocketnas.pro.core.OpenListApi
import com.pocketnas.pro.core.PassStore
import com.pocketnas.pro.core.RemoteAccess
import com.pocketnas.pro.core.TransferEngine
import com.pocketnas.pro.service.OpenListService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主 ViewModel：持有登录态、封装全部内核交互。
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("pocketnas_prefs", Context.MODE_PRIVATE)

    private val _token = MutableStateFlow(prefs.getString("token", null))
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _loggedIn = MutableStateFlow(!_token.value.isNullOrBlank())
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _users = MutableStateFlow<List<OpenListApi.UserInfo>>(emptyList())
    val users: StateFlow<List<OpenListApi.UserInfo>> = _users.asStateFlow()

    private val _storages = MutableStateFlow<List<OpenListApi.StorageInfo>>(emptyList())
    val storages: StateFlow<List<OpenListApi.StorageInfo>> = _storages.asStateFlow()

    // ---- 存储源添加 ----
    private val _drivers = MutableStateFlow<List<String>>(emptyList())
    val drivers: StateFlow<List<String>> = _drivers.asStateFlow()

    /** 可用驱动模板（/api/admin/driver/list）：内核未挂载任何源时也能列出，用于「添加存储源」 */
    private val _driverTemplates = MutableStateFlow<List<OpenListApi.DriverTemplate>>(emptyList())
    val driverTemplates: StateFlow<List<OpenListApi.DriverTemplate>> = _driverTemplates.asStateFlow()

    private val _driverInfo = MutableStateFlow<OpenListApi.DriverInfo?>(null)
    val driverInfo: StateFlow<OpenListApi.DriverInfo?> = _driverInfo.asStateFlow()

    // ---- 文件浏览 ----
    private val _fsList = MutableStateFlow<OpenListApi.FsListResult?>(null)
    val fsList: StateFlow<OpenListApi.FsListResult?> = _fsList.asStateFlow()

    private val _fsLoading = MutableStateFlow(false)
    val fsLoading: StateFlow<Boolean> = _fsLoading.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    // ---- 传输任务（上传/下载，断点续传）----
    private val transferEngine: TransferEngine by lazy {
        TransferEngine(
            context = getApplication(),
            tokenProvider = { _token.value },
            onUpdate = { list -> _transfers.value = list },
        )
    }
    private val _transfers = MutableStateFlow<List<TransferEngine.Task>>(emptyList())
    val transfers: StateFlow<List<TransferEngine.Task>> = _transfers.asStateFlow()

    // ---- 远程访问（FTP / SFTP）----
    private val _ftpEnabled = MutableStateFlow(RemoteAccess.isFtpEnabled(getApplication()))
    val ftpEnabled: StateFlow<Boolean> = _ftpEnabled.asStateFlow()

    private val _sftpEnabled = MutableStateFlow(RemoteAccess.isSftpEnabled(getApplication()))
    val sftpEnabled: StateFlow<Boolean> = _sftpEnabled.asStateFlow()

    private val _webdavEnabled = MutableStateFlow(RemoteAccess.isWebdavEnabled(getApplication()))
    val webdavEnabled: StateFlow<Boolean> = _webdavEnabled.asStateFlow()

    init {
        transferEngine.restore()
        // 启动即校验 token：被内核吊销（外部登录/重启签发新 secret）时自动用保存的账号重登
        // 必须跑在 IO 线程：waitTcpReady 探测不能阻塞主线程，否则 Compose 首帧卡 20 秒白屏
        viewModelScope.launch(Dispatchers.IO) {
            autoRevalidateToken()
        }
        // 周期自动刷新（30s）：内核重启 / token 被吊销后自动重登并恢复数据，无需手动重启 App
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                autoRefreshTick()
            }
        }
    }

    /** 周期刷新：token 失效自动重登；重登成功则全量刷新数据，否则轻量刷新状态 */
    private suspend fun autoRefreshTick() {
        val before = _token.value
        val t = ensureToken() ?: return
        if (before != null && before != t) {
            refreshAll()
            LogStore.log("SVC", "自动刷新：token 已更新，存储/文件/媒体数据已恢复")
        }
    }

    /**
     * 启动时自动登录/校验 token：若失效则自动重新登录（用内核日志最新随机密码），
     * 无 token（首次启动）也自动登录，使存储/文件/驱动列表/网页后台全程免手动登录。
     */
    private suspend fun autoRevalidateToken() {
        val ctx = getApplication<Application>()
        OpenListService.start(ctx)
        if (!waitTcpReady()) {
            LogStore.log("AUTH", "内核 TCP 未就绪，启动校验跳过")
            return
        }
        val current = _token.value
        if (current != null) {
            val valid = safeApi { api().authCheck(current) } == true
            if (valid) return
            LogStore.log("AUTH", "token 已失效（可能被外部登录吊销），自动重新登录")
        }
        // 无 token 或 token 失效：一律用内核日志最新随机密码自动登录
        val saved = AppSettingStore.getSavedLogin(ctx)
        val user = saved.first.ifBlank { "admin" }
        val pass = resolveAdminPassword()
        val tok = loginNow(user, pass)
        if (tok != null) {
            _token.value = tok
            prefs.edit().putString("token", tok).apply()
            _loggedIn.value = true
            LogStore.log("AUTH", "启动自动登录成功: $user")
            refreshAll()
        } else {
            LogStore.log("AUTH", "启动自动登录未成功（内核未就绪或被限流，用户可手动登录）")
        }
    }

    /** 同步登录（挂起直到拿到 token 或失败），供 ensureToken 复用 */
    private suspend fun loginNow(username: String, password: String): String? =
        withContext(Dispatchers.IO) {
            try {
                OpenListService.start(getApplication())
                // 等待 TCP 5244 真能连上（官方版内核只监听 TCP）
                if (!waitTcpReady()) {
                    LogStore.log("AUTH", "TCP 5244 等待超时")
                    return@withContext null
                }
                val pass = password.ifBlank { resolveAdminPassword() }
                LogStore.log("AUTH", "尝试自动登录 user=$username pass=****${pass.takeLast(2)}")
                // 内核刚启动的初始化窗口期可能返回 HTML 错误页，重试最多 3 次
                repeat(3) { attempt ->
                    val r = api().login(username.trim(), pass)
                    if (r.token != null) {
                        LogStore.log("AUTH", "登录成功（第 ${attempt + 1} 次尝试）")
                        return@withContext r.token
                    }
                    if (r.code == 429) {
                        // 限流是持久的（正确密码也会被拒），必须停止重试，重启内核/应用才能清除
                        LogStore.log("AUTH", "登录被限流(429)，停止重试；重启应用可清除限流")
                        return@withContext null
                    }
                    if (r.code == 401) {
                        api().initSetup("admin", "admin123456")
                        val r2 = api().login(username.trim(), pass)
                        if (r2.token != null) {
                            LogStore.log("AUTH", "登录成功（initSetup 后重试）")
                            return@withContext r2.token
                        }
                        if (r2.code == 429) {
                            LogStore.log("AUTH", "登录被限流(429)，停止重试；重启应用可清除限流")
                            return@withContext null
                        }
                    }
                    LogStore.log("AUTH", "登录第 ${attempt + 1} 次失败，等待后重试")
                    delay(3000)
                }
                null
            } catch (e: Exception) {
                // 记录响应体片段：非 JSON 响应（如 HTML 错误页）直接暴露，便于定位
                val body = (e.message ?: "")
                LogStore.log("AUTH", "loginNow 异常: ${body.take(400)}")
                null
            }
        }

    /** 内核最新随机密码提示（供登录界面展示，用户不知道密码时可复制） */
    val adminPasswordHint: String?
        get() = resolveAdminPasswordFromLogs()

    /** 确保有效 token：空 / 失效时自动用日志最新密码重登（内核重启后恢复数据链路的关键） */
    suspend fun ensureToken(): String? {
        _token.value?.let {
            val valid = safeApi { api().authCheck(it) } == true
            if (valid) return it
            LogStore.log("AUTH", "token 失效，ensureToken 触发重登")
        }
        val saved = AppSettingStore.getSavedLogin(getApplication())
        val user = saved.first.ifBlank { "admin" }
        // 密码一律取内核日志最新随机密码：内核每次重建 admin 都会换密码，
        // 保存的旧密码必然 401，连续失败还会触发持久 429 限流（重启内核才清除）
        val pass = resolveAdminPassword()
        val tok = loginNow(user, pass)
        if (tok != null) {
            _token.value = tok
            prefs.edit().putString("token", tok).apply()
            _loggedIn.value = true
            LogStore.log("AUTH", "ensureToken 重登成功: $user")
        } else {
            LogStore.log("AUTH", "ensureToken 重登失败（内核可能未就绪或被限流）")
        }
        return _token.value
    }

    private fun api(): OpenListApi {
        return OpenListApi(LocalHttpClient.tcp())
    }

    /** 等待内核 TCP 5244 就绪（官方版内核只监听 TCP，不创建 unix socket） */
    private suspend fun waitTcpReady(timeoutMs: Long = 20_000): Boolean {
        var waited = 0L
        while (waited < timeoutMs) {
            try {
                java.net.Socket("127.0.0.1", 5244).use { return true }
            } catch (_: Exception) {
                // 用 delay 挂起而非 Thread.sleep：绝不阻塞调用线程（主线程）
                delay(500); waited += 500
            }
        }
        return false
    }

    /**
     * 解析内核自动生成的随机管理员密码。
     * 官方 OpenList 内核首次启动会自动创建 admin 并打印随机密码：
     *   Successfully created the admin user and the initial password is: xxxxxxxx
     * 取【最新】一条（内存缓冲逆序 + 磁盘日志兜底，解决启动早期缓冲未同步时回退旧密码导致的 401→429）。
     */
    private fun resolveAdminPasswordFromLogs(): String? {
        val lines = NasState.logs.value
        if (lines != null) {
            for (i in lines.size - 1 downTo 0) {
                val m = Regex("initial password is: (\\S+)", RegexOption.IGNORE_CASE).find(lines[i])
                if (m != null) return m.groupValues[1]
            }
        }
        // 磁盘兜底：LogStore 落盘文件（内核启动行一定在），跨进程/缓冲延迟时可用
        try {
            val ctx = getApplication<Application>()
            val f = java.io.File(ctx.getExternalFilesDir(null), "logs/pocketnas.log")
            if (f.exists()) {
                val tail = f.readLines().takeLast(300)
                for (i in tail.size - 1 downTo 0) {
                    val m = Regex("initial password is: (\\S+)", RegexOption.IGNORE_CASE).find(tail[i])
                    if (m != null) return m.groupValues[1]
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /** 获取可用的管理员密码：优先从内核日志解析最新随机密码（内核每次重建 admin 都会打印），保存的密码仅作兜底 */
    private fun resolveAdminPassword(): String {
        resolveAdminPasswordFromLogs()?.let { return it }
        val saved = AppSettingStore.getSavedLogin(getApplication()).second
        if (saved.isNotBlank() && saved != "admin123456") return saved
        return "admin123456"
    }

    /**
     * 安全调用内核 API：IO 线程 + 全量 catch。
     * 内核未就绪/重启/被杀时返回 null 而不是抛异常崩溃（修复冷启动首次打开闪退）。
     */
    private suspend fun <T> safeApi(block: () -> T): T? =
        try {
            withContext(Dispatchers.IO) { block() }
        } catch (e: Exception) {
            LogStore.log("API", "内核调用失败: ${e.message}")
            null
        }

    /**
     * 登录（走内核 TCP 5244，绝不经过网页）；首次未初始化时自动用 admin + 随机密码完成安装向导。
     * 密码优先级：用户传入 > 已保存 > 内核日志解析的随机密码 > 默认 admin123456。
     */
    fun login(username: String, password: String) {
        viewModelScope.launch {
            _loginError.value = null
            var limited = false
            val result = withContext(Dispatchers.IO) {
                try {
                    // 确保内核前台服务在运行（登录前拉起，防止内核未就绪崩溃）
                    OpenListService.start(getApplication())
                    // 等待内核 TCP 5244 就绪（最长 20s）
                    if (!waitTcpReady()) {
                        LogStore.log("AUTH", "内核 TCP 未就绪（超时 20s），登录终止")
                        return@withContext null
                    }
                    val pass = password.ifBlank { resolveAdminPassword() }
                    LogStore.log("AUTH", "手动登录 user=$username pass=****${pass.takeLast(2)}")
                    var r = api().login(username.trim(), pass)
                    if (r.token == null && r.code == 401) {
                        // 系统可能未初始化——调用安装向导创建管理员
                        val initialized = api().initSetup("admin", "admin123456")
                        LogStore.log(
                            "AUTH",
                            if (initialized) "检测到未初始化，已创建默认管理员 admin" else "系统已初始化或向导不可用",
                        )
                        if (initialized) r = api().login(username.trim(), pass)
                    }
                    if (r.token == null && r.code == 401 && pass != "admin123456") {
                        // 随机密码可能已轮换，回退默认密码再试一次
                        r = api().login(username.trim(), "admin123456")
                    }
                    if (r.code == 429) {
                        limited = true
                        LogStore.log("AUTH", "手动登录被限流(429)，重启应用可清除")
                    }
                    r.token
                } catch (e: Exception) {
                    LogStore.log("AUTH", "登录异常: ${e.message}")
                    null
                }
            }
            if (result != null) {
                _token.value = result
                prefs.edit().putString("token", result).apply()
                _loggedIn.value = true
                LogStore.log("AUTH", "登录成功: $username")
                refreshAll()
            } else {
                _loginError.value = if (limited) {
                    "登录失败：尝试次数过多被内核限流（429）。请重启应用清除限流后重试，密码可点登录框下方的提示复制"
                } else {
                    "登录失败：账号密码错误，或尝试过多被内核限流（429）。重启应用后可重试，密码可点登录框下方的提示复制"
                }
                LogStore.log("AUTH", "登录失败: $username")
            }
        }
    }

    fun logout() {
        _token.value = null
        prefs.edit().remove("token").apply()
        _loggedIn.value = false
        LogStore.log("AUTH", "已退出登录")
    }

    /** 刷新用户 / 存储 */
    fun refreshAll() {
        viewModelScope.launch {
            val t = ensureToken() ?: return@launch
            refreshUsers()
            refreshStorages()
        }
    }

    fun refreshUsers() {
        viewModelScope.launch {
            val t = ensureToken() ?: return@launch
            _users.value = safeApi { api().listUsers(t) } ?: emptyList()
        }
    }

    fun refreshStorages() {
        viewModelScope.launch {
            val t = ensureToken() ?: return@launch
            _storages.value = safeApi { api().listStorages(t) } ?: emptyList()
        }
    }

    /** 修改指定用户密码（核心能力：原生界面重置密码） */
    fun changePassword(userId: Int, newPassword: String) {
        viewModelScope.launch {
            val t = ensureToken() ?: return@launch
            val ok = safeApi { api().updateUserPassword(t, userId, newPassword) } ?: false
            _notice.value = if (ok) "密码修改成功" else "密码修改失败"
        }
    }

    fun deleteStorage(storageId: Int) {
        viewModelScope.launch {
            val t = ensureToken() ?: return@launch
            val ok = safeApi { api().deleteStorage(t, storageId) } ?: false
            _notice.value = if (ok) "存储已删除" else "删除失败"
            refreshStorages()
        }
    }

    // ==================== 存储源添加 ====================

    fun loadDrivers() {
        viewModelScope.launch {
            val t = ensureToken() ?: return@launch
            _drivers.value = safeApi { api().listDriverNames(t) } ?: emptyList()
        }
    }

    /** 加载可用驱动模板；失败（内核未启动等）时清空，供页面显示启动引导。内核刚冷启动时可能未就绪，自动重试一次 */
    fun loadDriverTemplates() {
        viewModelScope.launch {
            repeat(2) { attempt ->
                val t = ensureToken()
                if (t == null) {
                    if (attempt == 0) { delay(2000); return@repeat }
                    _driverTemplates.value = emptyList()
                    return@launch
                }
                val list = safeApi { api().listDriverTemplates(t) } ?: emptyList()
                if (list.isNotEmpty() || attempt == 1) {
                    _driverTemplates.value = list
                    return@launch
                }
                delay(2000)
            }
        }
    }

    fun loadDriverInfo(driver: String) {
        viewModelScope.launch {
            val t = ensureToken() ?: return@launch
            _driverInfo.value = safeApi { api().getDriverInfo(t, driver) }
        }
    }

    fun createStorage(
        driver: String,
        commonJson: String?,
        additionJson: String?,
    ) {
        viewModelScope.launch {
            val t = ensureToken() ?: return@launch
            val err = safeApi {
                api().createStorageFull(t, driver, commonJson, additionJson)
            }
            _notice.value = if (err == null) "存储源创建成功" else "创建失败：${err ?: "未知错误"}"
            refreshStorages()
        }
    }

    // ==================== 文件浏览器 ====================

    fun loadFs(path: String) {
        viewModelScope.launch {
            val t = ensureToken() ?: return@launch
            _fsLoading.value = true
            _fsList.value = safeApi { api().fsList(t, path) }
            _fsLoading.value = false
        }
    }

    fun mkdir(path: String) {
        viewModelScope.launch {
            val t = ensureToken() ?: return@launch
            val ok = safeApi { api().fsMkdir(t, path) } ?: false
            _notice.value = if (ok) "文件夹已创建" else "创建失败"
        }
    }

    fun rename(path: String, newName: String) {
        viewModelScope.launch {
            val t = ensureToken() ?: return@launch
            val ok = safeApi { api().fsRename(t, path, newName) } ?: false
            _notice.value = if (ok) "重命名成功" else "重命名失败"
        }
    }

    fun remove(dir: String, names: List<String>) {
        viewModelScope.launch {
            val t = ensureToken() ?: return@launch
            val ok = safeApi { api().fsRemove(t, dir, names) } ?: false
            _notice.value = if (ok) "已删除" else "删除失败"
        }
    }

    /** 移动文件到目标目录 */
    fun move(srcDir: String, dstDir: String, names: List<String>) {
        viewModelScope.launch {
            val t = ensureToken() ?: return@launch
            val ok = safeApi { api().fsMove(t, srcDir, dstDir, names) } ?: false
            _notice.value = if (ok) "移动成功" else "移动失败"
        }
    }

    /** 复制文件到目标目录 */
    fun copy(srcDir: String, dstDir: String, names: List<String>) {
        viewModelScope.launch {
            val t = ensureToken() ?: return@launch
            val ok = safeApi { api().fsCopy(t, srcDir, dstDir, names) } ?: false
            _notice.value = if (ok) "复制成功" else "复制失败"
        }
    }

    /** 搜索文件，结果通过 [onResult] 回调 */
    fun search(parent: String, keywords: String, onResult: (List<OpenListApi.SearchResult>) -> Unit) {
        viewModelScope.launch {
            val t = ensureToken() ?: return@launch
            val r = safeApi { api().fsSearch(t, parent, keywords) } ?: emptyList()
            onResult(r)
        }
    }

    fun upload(filePath: String, fileName: String, mime: String, stream: java.io.InputStream) {
        viewModelScope.launch {
            val t = ensureToken() ?: return@launch
            val ok = withContext(Dispatchers.IO) {
                try {
                    api().uploadFile(t, filePath, fileName, mime, stream)
                } catch (e: Exception) {
                    false
                } finally {
                    try { stream.close() } catch (_: Exception) {}
                }
            }
            _notice.value = if (ok) "上传成功" else "上传失败"
        }
    }

    fun startService() = OpenListService.start(getApplication())
    fun stopService() = OpenListService.stop(getApplication())

    fun isBootStart(): Boolean = OpenListService.isBootStartEnabled(getApplication())
    fun setBootStart(v: Boolean) = OpenListService.setBootStart(getApplication(), v)

    fun isAutoRestart(): Boolean = OpenListService.isAutoRestartEnabled(getApplication())
    fun setAutoRestart(v: Boolean) = OpenListService.setAutoRestart(getApplication(), v)

    // ==================== 远程访问 ====================

    val lanIp: String?
        get() = RemoteAccess.lanIp()

    /** 切换 FTP 服务：持久化 →（开启时）同步 PASV 通告 IP → 重启内核进程生效 */
    fun setFtpEnabled(enabled: Boolean) {
        RemoteAccess.setFtpEnabled(getApplication(), enabled)
        _ftpEnabled.value = enabled
        if (enabled) {
            val ip = RemoteAccess.lanIp()
            if (ip != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    val t = ensureToken() ?: return@launch
                    val ok = try { api().saveSetting(t, "ftp_public_host", ip) } catch (_: Exception) { false }
                    if (ok) {
                        NasState.appendLog("[PocketNAS] FTP 局域网通告 IP 已设为 $ip")
                    }
                }
            }
        }
        restartKernelForRemote()
    }

    /** 切换 SFTP 服务 */
    fun setSftpEnabled(enabled: Boolean) {
        RemoteAccess.setSftpEnabled(getApplication(), enabled)
        _sftpEnabled.value = enabled
        restartKernelForRemote()
    }

    /** 切换 WebDAV 服务（打开主 HTTP 端口 /dav） */
    fun setWebdavEnabled(enabled: Boolean) {
        RemoteAccess.setWebdavEnabled(getApplication(), enabled)
        _webdavEnabled.value = enabled
        restartKernelForRemote()
    }

    private fun restartKernelForRemote() {
        NasState.appendLog("[PocketNAS] 远程访问配置已变更，重启内核生效...")
        OpenListService.restart(getApplication())
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
        } else true
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun showNotice(msg: String) {
        _notice.value = msg
    }

    // ==================== 传输任务（断点续传） ====================

    /** 启动下载（远程路径 → 本地 transfers/，支持断点续传） */
    fun startDownload(remotePath: String, name: String, size: Long) {
        transferEngine.startDownload(remotePath, name, size)
    }

    /** 启动分片上传（[localFile] 为已复制到 transfers/ 的可 seek 本地文件） */
    fun startUpload(remotePath: String, name: String, localFile: String) {
        transferEngine.startUpload(remotePath, name, localFile)
    }

    fun pauseTransfer(id: String) = transferEngine.pause(id)
    fun resumeTransfer(id: String) = transferEngine.resume(id)
    fun cancelTransfer(id: String) = transferEngine.cancel(id)
    fun clearDoneTransfers() = transferEngine.clearDone()
    fun pauseAllTransfers() {
        transferEngine.list().filter { it.state == TransferEngine.State.Running }.forEach { pauseTransfer(it.id) }
    }

    /** 音乐扫描条目 */
    data class AudioEntry(val path: String, val name: String, val size: Long)

    /** 扫描内核音频文件（递归深度 2：根目录 → 挂载点 → 子目录），结果回调 */
    fun scanAudio(onResult: (List<AudioEntry>) -> Unit) {
        viewModelScope.launch {
            val t = ensureToken() ?: return@launch
            val out = mutableListOf<AudioEntry>()
            val audioExt = listOf(".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".opus", ".m4b", ".wma")
            suspend fun scan(dir: String, depth: Int) {
                if (depth > 2) return
                val r = safeApi { api().fsList(t, dir) } ?: return
                for (o in r.content) {
                    if (o.isDir) {
                        scan(joinFsPath(dir, o.name), depth + 1)
                    } else if (audioExt.any { o.name.lowercase().endsWith(it) }) {
                        out += AudioEntry(joinFsPath(dir, o.name), o.name, o.size)
                    }
                }
            }
            scan("/", 0)
            onResult(out)
        }
    }

    private fun joinFsPath(parent: String, child: String): String =
        if (parent.endsWith("/")) parent + child else parent + "/" + child

    /** 读取文件开头内容（文本预览），在 IO 线程执行 */
    suspend fun fsGetText(path: String, maxBytes: Int = 64 * 1024): String? =
        withContext(Dispatchers.IO) {
            val t = ensureToken() ?: return@withContext null
            com.pocketnas.pro.core.LogStore.log("VM", "fsGetText path=$path token=${t.take(8)}...")
            try {
                api().fsGetText(t, path, maxBytes)
            } catch (e: Exception) {
                com.pocketnas.pro.core.LogStore.log("VM", "fsGetText FAIL: $e")
                null
            }
        }

    // ==================== 预览渲染（图片 / 媒体流） ====================

    private val mediaProxy by lazy {
        KernelHttpProxy(BinaryUtil.socketFile(getApplication()).absolutePath)
    }

    /** 本地媒体代理基地址（http://127.0.0.1:<port>），供 MediaPlayer/VideoView 播放 */
    fun mediaBaseUrl(): String {
        val port = mediaProxy.start()
        return "http://127.0.0.1:$port"
    }

    /** 获取文件 raw_url 路径（如 "/d/..."），配合 mediaBaseUrl 拼播放地址 */
    suspend fun mediaUrl(path: String): String? =
        withContext(Dispatchers.IO) {
            val t = ensureToken() ?: return@withContext null
            try {
                val raw = api().fsGet(t, path)?.rawUrl?.takeIf { it.isNotBlank() } ?: return@withContext null
                com.pocketnas.pro.core.LogStore.log("VM", "mediaUrl raw=$raw")
                // unix socket 无端口 → 内核回填 localhost:80，先修正 host 再提取路径
                val fixed = raw
                    .replace("http://localhost:80/", "http://127.0.0.1:5244/")
                    .replace("http://localhost/", "http://127.0.0.1:5244/")
                    .replace("http://127.0.0.1:80/", "http://127.0.0.1:5244/")
                    .replace("http://127.0.0.1/", "http://127.0.0.1:5244/")
                // 播放走本地 KernelHttpProxy（App 内 TCP → unix socket → 内核），避开直接连内核 TCP 的兼容问题
                val pathAndQuery = fixed.substringAfter("5244", fixed)
                val proxyUrl = "http://127.0.0.1:${mediaProxy.start()}$pathAndQuery"
                com.pocketnas.pro.core.LogStore.log("VM", "mediaUrl proxy=$proxyUrl")
                proxyUrl
            } catch (e: Exception) {
                com.pocketnas.pro.core.LogStore.log("VM", "mediaUrl FAIL: $e")
                null
            }
        }

    /** 读取完整图片字节（上限 8MB），用于 Bitmap 渲染 */
    suspend fun loadImageBytes(path: String, maxBytes: Int = 8 * 1024 * 1024): ByteArray? =
        withContext(Dispatchers.IO) {
            val t = ensureToken() ?: return@withContext null
            try {
                val b = api().fsGetBytes(t, path, maxBytes)
                com.pocketnas.pro.core.LogStore.log("VM", "loadImageBytes path=$path bytes=${b?.size ?: "null"}")
                b
            } catch (e: Exception) {
                com.pocketnas.pro.core.LogStore.log("VM", "loadImageBytes FAIL: $e")
                null
            }
        }

    /** 提取音频内嵌封面（ID3 APIC），无封面返回 null */
    suspend fun loadAudioCover(path: String): android.graphics.Bitmap? =
        withContext(Dispatchers.IO) {
            val t = ensureToken() ?: return@withContext null
            try {
                val bytes = api().fsGetBytes(t, path, 6 * 1024 * 1024) ?: return@withContext null
                val tmp = java.io.File(getApplication<Application>().cacheDir, "cover_.bin")
                tmp.writeBytes(bytes)
                val mmr = android.media.MediaMetadataRetriever()
                try {
                    mmr.setDataSource(tmp.absolutePath)
                    val pic = mmr.embeddedPicture
                    if (pic == null) null else android.graphics.BitmapFactory.decodeByteArray(pic, 0, pic.size)
                } finally {
                    mmr.release(); tmp.delete()
                }
            } catch (_: Exception) { null }
        }

    /** 读取文档完整字节（docx/xlsx/pdf 渲染用，上限 20MB） */
    suspend fun loadDocBytes(path: String, maxBytes: Int = 20 * 1024 * 1024): ByteArray? =
        withContext(Dispatchers.IO) {
            val t = ensureToken() ?: return@withContext null
            try {
                api().fsGetBytes(t, path, maxBytes)
            } catch (_: Exception) {
                null
            }
        }

    /** 下载 PDF 到缓存文件（PdfRenderer 需要文件描述符），失败返回 null */
    suspend fun loadPdfFile(path: String): java.io.File? {
        val bytes = loadDocBytes(path) ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val f = java.io.File(
                    getApplication<Application>().cacheDir,
                    "preview_${path.hashCode()}.pdf",
                )
                f.writeBytes(bytes)
                f
            } catch (_: Exception) {
                null
            }
        }
    }

    // ==================== 媒体库（相册视图） ====================

    data class MediaItem(
        val path: String,
        val name: String,
        val size: Long,
        val modified: String,
        val isVideo: Boolean,
        val thumb: String,
        val isAudio: Boolean = false,
    )

    enum class MediaFilter(val label: String) { All("全部"), Image("图片"), Video("视频"), Audio("音乐") }

    private val _mediaList = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaList: StateFlow<List<MediaItem>> = _mediaList.asStateFlow()

    private val _mediaLoading = MutableStateFlow(false)
    val mediaLoading: StateFlow<Boolean> = _mediaLoading.asStateFlow()

    private val _mediaFilter = MutableStateFlow(MediaFilter.All)
    val mediaFilter: StateFlow<MediaFilter> = _mediaFilter.asStateFlow()

    fun setMediaFilter(f: MediaFilter) { _mediaFilter.value = f }

    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
    private val videoExt = setOf("mp4", "mkv", "mov", "avi", "webm", "ts")
    private val audioExt = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "ape", "wma", "mid", "flv")

    private val thumbCache = android.util.LruCache<String, android.graphics.Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(4 * 1024 * 1024)
    )

    /** 递归扫描媒体文件（跳过加密目录），IO 线程执行 */
    fun scanMedia() {
        viewModelScope.launch(Dispatchers.IO) {
            _mediaLoading.value = true
            val t = _token.value
            val result = mutableListOf<MediaItem>()
            try {
                if (t != null) walkMedia(t, "/", 0, result, mutableSetOf())
            } catch (_: Exception) {}
            _mediaList.value = result.sortedByDescending { it.modified }
            _mediaLoading.value = false
        }
    }

    private fun walkMedia(
        token: String, path: String, depth: Int,
        out: MutableList<MediaItem>, visited: MutableSet<String>,
    ) {
        if (depth > 4 || out.size > 800 || visited.contains(path)) return
        visited += path
        if (path != "/" && PassStore(getApplication()).has(path)) return
        val list = try { api().fsList(token, path) } catch (_: Exception) { null } ?: return
        for (o in list.content) {
            val full = if (path == "/") "/${o.name}" else "$path/${o.name}"
            if (o.isDir) {
                if (o.name.startsWith(".") || out.size > 800) continue
                walkMedia(token, full, depth + 1, out, visited)
            } else {
                val ext = o.name.substringAfterLast('.', "").lowercase()
                val isVideo = ext in videoExt
                val isImage = ext in imageExt
                val isAudio = ext in audioExt
                if (isVideo || isImage || isAudio) {
                    out += MediaItem(full, o.name, o.size, o.modified.take(10), isVideo, o.thumb, isAudio)
                }
            }
        }
    }

    /** 加载缩略图字节 → Bitmap（LruCache 缓存）。thumbPath 可为 "/d/..."（socket）或 http(s) 直链 */
    suspend fun loadThumb(thumbPath: String): android.graphics.Bitmap? {
        if (thumbPath.isBlank()) return null
        thumbCache.get(thumbPath)?.let { return it }
        return withContext(Dispatchers.IO) {
            val t = ensureToken() ?: return@withContext null
            try {
                val bytes = when {
                    thumbPath.startsWith("/") -> {
                        val client = LocalHttpClient.tcp()
                        client.openStream("GET", thumbPath, emptyMap(), t).use { s ->
                            if (s.code !in 200..299) null else readUpTo(s.body, 512 * 1024)
                        }
                    }
                    thumbPath.startsWith("http") -> {
                        // unix socket 请求无端口 → 内核 raw_url 回填 localhost:80，修正为内核 TCP 5244
                        val fixed = thumbPath
                            .replace("http://localhost:80/", "http://127.0.0.1:5244/")
                            .replace("http://localhost/", "http://127.0.0.1:5244/")
                            .replace("http://127.0.0.1:80/", "http://127.0.0.1:5244/")
                            .replace("http://127.0.0.1/", "http://127.0.0.1:5244/")
                        val conn = java.net.URL(fixed).openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 10000
                        conn.readTimeout = 20000
                        try {
                            if (conn.responseCode !in 200..299) null else readUpTo(conn.inputStream, 512 * 1024)
                        } finally {
                            conn.disconnect()
                        }
                    }
                    else -> null
                } ?: return@withContext null
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                if (bmp != null) thumbCache.put(thumbPath, bmp)
                bmp
            } catch (_: Exception) { null }
        }
    }

    private fun readUpTo(input: java.io.InputStream, max: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n == -1) break
            total += n
            if (total > max) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
