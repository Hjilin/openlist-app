package com.pocketnas.pro.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import com.pocketnas.pro.core.DocRender
import com.pocketnas.pro.core.OpenListApi
import com.pocketnas.pro.core.PassStore
import com.pocketnas.pro.core.TransferEngine
import com.pocketnas.pro.ui.AppViewModel
import com.pocketnas.pro.ui.components.ExoPlayerSurface
import com.pocketnas.pro.ui.components.MainPlayerDialog
import com.pocketnas.pro.ui.components.rememberExoPlayer
import com.pocketnas.pro.ui.components.DocViewerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 视图模式 */
private enum class ViewMode { List, Grid }

/** 密码对话框状态：isVerify=true 为打开校验，false 为设置/修改/清除 */
private data class PwdDialogState(
    val key: String,
    val title: String,
    val isVerify: Boolean,
    val obj: OpenListApi.FsObj? = null,
)

/**
 * 原生文件浏览器：目录浏览 / 新建 / 重命名 / 删除 / 长按多选 /
 * 分片上传（断点续传）/ 下载（断点续传）/ 传输任务管理。
 * 数据全部来自内核 /api/fs/ 接口（走 LocalSocket），不加载任何网页。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileBrowserScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenStorages: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listResult by vm.fsList.collectAsState()
    val loading by vm.fsLoading.collectAsState()
    val transfers by vm.transfers.collectAsState()
    val storages by vm.storages.collectAsState()


    var currentPath by rememberSaveable { mutableStateOf("/") }

    // CN/EN name mapping: kernel uses English mount paths, UI shows Chinese remark names.
    val mountNames = remember(storages) {
        storages.associate { it.mountPath.trim('/') to (it.remark.ifBlank { it.mountPath.trim('/') }) }
    }
    fun displayName(name: String): String =
        if (currentPath == "/") mountNames[name] ?: name else name
    var viewMode by rememberSaveable { mutableStateOf(ViewMode.List) }
    var localMode by rememberSaveable { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<OpenListApi.FsObj?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    var mkdirDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<OpenListApi.FsObj?>(null) }
    var deleteTarget by remember { mutableStateOf<OpenListApi.FsObj?>(null) }
    var previewTarget by remember { mutableStateOf<OpenListApi.FsObj?>(null) }

    // 选择模式（长按进入，多选批量操作）
    var selMode by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var transfersSheet by remember { mutableStateOf(false) }

    // ---- 密码保护：本地门禁 + 离开目录自动上锁 ----
    val passStore = remember { PassStore(context) }
    var unlocked by remember { mutableStateOf(setOf<String>()) }
    var pwdDialog by remember { mutableStateOf<PwdDialogState?>(null) }

    fun lockKeyOf(obj: OpenListApi.FsObj): String = joinPath(currentPath, obj.name)

    fun isLocked(obj: OpenListApi.FsObj): Boolean {
        val key = lockKeyOf(obj)
        return passStore.has(key) && key !in unlocked
    }

    fun navigate(path: String) {
        currentPath = path
        selMode = false
        selected = emptySet()
        unlocked = emptySet() // 离开当前目录页面 → 全部自动上锁
    }

    fun doOpen(obj: OpenListApi.FsObj) {
        if (obj.isDir) navigate(joinPath(currentPath, obj.name))
        else previewTarget = obj
    }

    fun tryOpen(obj: OpenListApi.FsObj) {
        val key = lockKeyOf(obj)
        if (passStore.has(key) && key !in unlocked) {
            pwdDialog = PwdDialogState(key = key, title = "访问密码", isVerify = true, obj = obj)
        } else {
            doOpen(obj)
        }
    }

    LaunchedEffect(currentPath) { vm.loadFs(currentPath) }

    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val name = queryDisplayName(context, uri) ?: "upload_${System.currentTimeMillis()}.bin"
        val targetPath = joinPath(currentPath, name)
        scope.launch {
            val local = withContext(Dispatchers.IO) {
                copyUriToTransfers(context, uri, name)
            }
            if (local != null) {
                vm.startUpload(targetPath, name, local.absolutePath)
            } else {
                vm.showNotice("上传失败：无法读取所选文件")
            }
        }
    }

    val content = listResult?.content ?: emptyList()
    val runningCount = transfers.count { it.state == TransferEngine.State.Running }

    Scaffold(
        topBar = {
            if (localMode) return@Scaffold
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (selMode) "已选 ${selected.size} 项" else "文件浏览器",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            currentPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selMode) {
                            selMode = false
                            selected = emptySet()
                        } else {
                            val parent = parentPath(currentPath)
                            if (parent != null) navigate(parent) else onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (selMode) {
                        TextButton(onClick = { selected = content.map { it.name }.toSet() }) {
                            Text("全选", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (!selMode) {
                        IconButton(onClick = onOpenStorages) {
                            Icon(Icons.Default.Storage, contentDescription = "存储源")
                        }
                        IconButton(onClick = { localMode = !localMode }) {
                            Icon(
                                if (localMode) Icons.Default.Folder else Icons.Default.Storage,
                                contentDescription = "本地磁盘",
                            )
                        }
                        IconButton(onClick = { transfersSheet = true }) {
                            BadgedBox(
                                badge = {
                                    if (runningCount > 0) {
                                        Badge { Text("$runningCount") }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.SwapVert, contentDescription = "传输任务")
                            }
                        }
                        IconButton(onClick = {
                            viewMode = if (viewMode == ViewMode.List) ViewMode.Grid else ViewMode.List
                        }) {
                            Icon(
                                if (viewMode == ViewMode.List) Icons.Default.GridView
                                else Icons.Default.ViewList,
                                contentDescription = "切换视图",
                            )
                        }
                        IconButton(onClick = { vm.loadFs(currentPath) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (localMode) return@Scaffold
            if (selMode) {
                SelectionActionBar(
                    count = selected.size,
                    onOpen = {
                        val items = content.filter { it.name in selected }
                        if (items.size == 1) {
                            tryOpen(items[0])
                            selMode = false
                            selected = emptySet()
                        } else {
                            vm.showNotice("打开仅支持单选")
                        }
                    },
                    onDownload = {
                        val files = content.filter { it.name in selected && !it.isDir }
                        if (files.isEmpty()) {
                            vm.showNotice("文件夹不支持下载")
                        } else {
                            files.forEach { f ->
                                vm.startDownload(joinPath(currentPath, f.name), f.name, f.size)
                            }
                            vm.showNotice("已加入 ${files.size} 个下载任务")
                            selMode = false
                            selected = emptySet()
                        }
                    },
                    onDelete = {
                        val names = content.map { it.name }.filter { it in selected }
                        vm.remove(currentPath, names)
                        selMode = false
                        selected = emptySet()
                    },
                    onRename = {
                        val names = content.map { it.name }.filter { it in selected }
                        if (names.size == 1) {
                            renameTarget = content.first { it.name == names[0] }
                        } else {
                            vm.showNotice("重命名仅支持单选")
                        }
                        selMode = false
                        selected = emptySet()
                    },
                    onCancel = {
                        selMode = false
                        selected = emptySet()
                    },
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { mkdirDialog = true },
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("新建文件夹")
                    }
                    OutlinedButton(
                        onClick = { uploadLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("上传")
                    }
                }
            }
        },
    ) { innerPadding ->
        if (localMode) {
            LocalDiskSection(
                onBackToRemote = { localMode = false },
                onBack = onBack,
            )
        } else {
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (!selMode) {
                BreadcrumbBar(
                    path = currentPath,
                    mountNames = mountNames,
                    onNavigate = { navigate(it) },
                )
                HorizontalDivider()
            }

            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            }

            if (!loading && content.isEmpty()) {
                Text(
                    "此目录为空",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (viewMode) {
                ViewMode.List -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        listItems(content, key = { it.name }) { obj ->
                            FileRow(
                                obj = obj,
                                displayName = displayName(obj.name),
                                selected = selMode && obj.name in selected,
                                selMode = selMode,
                                locked = isLocked(obj),
                                onClick = {
                                    if (selMode) {
                                        selected = toggle(selected, obj.name)
                                        if (selected.isEmpty()) selMode = false
                                    } else {
                                        tryOpen(obj)
                                    }
                                },
                                onLongClick = {
                                    if (!selMode) {
                                        selMode = true
                                        selected = setOf(obj.name)
                                    }
                                },
                                onMenu = {
                                    if (!selMode) {
                                        menuTarget = obj
                                        menuExpanded = true
                                    }
                                },
                            )
                        }
                    }
                }
                ViewMode.Grid -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        gridItems(content, key = { it.name }) { obj ->
                            GridFileItem(
                                obj = obj,
                                displayName = displayName(obj.name),
                                selected = selMode && obj.name in selected,
                                selMode = selMode,
                                locked = isLocked(obj),
                                onClick = {
                                    if (selMode) {
                                        selected = toggle(selected, obj.name)
                                        if (selected.isEmpty()) selMode = false
                                    } else {
                                        tryOpen(obj)
                                    }
                                },
                                onLongClick = {
                                    if (!selMode) {
                                        selMode = true
                                        selected = setOf(obj.name)
                                    }
                                },
                                onMenu = {
                                    if (!selMode) {
                                        menuTarget = obj
                                        menuExpanded = true
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    }

    // ---- 行菜单 ----
    menuTarget?.let { target ->
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = {
                    menuExpanded = false
                    renameTarget = target
                },
            )
            DropdownMenuItem(
                text = { Text("下载") },
                onClick = {
                    menuExpanded = false
                    if (target.isDir) vm.showNotice("文件夹不支持下载")
                    else vm.startDownload(joinPath(currentPath, target.name), target.name, target.size)
                },
            )
            DropdownMenuItem(
                text = {
                    val k = joinPath(currentPath, target.name)
                    Text(if (passStore.has(k)) "修改密码" else "设置密码")
                },
                onClick = {
                    menuExpanded = false
                    val k = joinPath(currentPath, target.name)
                    pwdDialog = PwdDialogState(
                        key = k,
                        title = if (passStore.has(k)) "修改密码" else "设置密码",
                        isVerify = false,
                        obj = target,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = {
                    menuExpanded = false
                    deleteTarget = target
                },
            )
        }
    }

    // ---- 密码对话框（校验 / 设置）----
    pwdDialog?.let { dlg ->
        var pwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pwdDialog = null },
            title = { Text(dlg.title) },
            text = {
                Column {
                    Text(
                        if (dlg.isVerify) "「${dlg.obj?.name ?: ""}」已加密，请输入访问密码"
                        else if (passStore.has(dlg.key)) "输入新密码（留空确定 = 清除密码）"
                        else "为「${dlg.obj?.name ?: ""}」设置访问密码",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pwd,
                        onValueChange = { pwd = it },
                        label = { Text("密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dlg.isVerify) {
                        if (pwd == passStore.get(dlg.key)) {
                            unlocked = unlocked + dlg.key
                            pwdDialog = null
                            dlg.obj?.let { doOpen(it) }
                        } else {
                            vm.showNotice("密码错误")
                            pwd = ""
                        }
                    } else {
                        passStore.set(dlg.key, pwd.takeIf { it.isNotBlank() })
                        vm.showNotice(if (pwd.isBlank()) "已清除密码" else "已设置密码")
                        pwdDialog = null
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pwdDialog = null }) { Text("取消") }
            },
        )
    }

    // ---- 文件预览 ----
    previewTarget?.let { target ->
        FilePreviewDialog(
            vm = vm,
            path = joinPath(currentPath, target.name),
            obj = target,
            onDismiss = { previewTarget = null },
            onDownload = {
                vm.startDownload(joinPath(currentPath, target.name), target.name, target.size)
                previewTarget = null
            },
        )
    }

    // ---- 新建文件夹 ----
    if (mkdirDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { mkdirDialog = false },
            title = { Text("新建文件夹") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("文件夹名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        vm.mkdir(joinPath(currentPath, name.trim()))
                    }
                    mkdirDialog = false
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { mkdirDialog = false }) { Text("取消") }
            },
        )
    }

    // ---- 重命名 ----
    renameTarget?.let { target ->
        var newName by remember { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank() && newName != target.name) {
                        vm.rename(joinPath(currentPath, target.name), newName.trim())
                    }
                    renameTarget = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
        )
    }

    // ---- 删除确认 ----
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除") },
            text = { Text("确认删除 ${target.name}？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.remove(currentPath, listOf(target.name))
                    deleteTarget = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    // ---- 传输任务面板 ----
    if (transfersSheet) {
        TransfersSheet(
            transfers = transfers,
            onDismiss = { transfersSheet = false },
            onPause = vm::pauseTransfer,
            onResume = vm::resumeTransfer,
            onCancel = vm::cancelTransfer,
        )
    }
}

private fun toggle(set: Set<String>, name: String): Set<String> =
    if (name in set) set - name else set + name

/** 把系统选择器选中的 Uri 复制到 transfers/（引擎分片上传需要可 seek 的本地文件） */
private fun copyUriToTransfers(context: Context, uri: Uri, name: String): File? {
    val dir = File(context.filesDir, "transfers").apply { mkdirs() }
    val safeName = name.replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "upload.bin" }
    val target = File(dir, "up_${System.currentTimeMillis()}_$safeName")
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out -> input.copyTo(out, 256 * 1024) }
        }
        target
    } catch (_: Exception) {
        null
    }
}

/** 选择模式底部操作栏 */
@Composable
private fun SelectionActionBar(
    count: Int,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column {
            HorizontalDivider()
            Text(
                "已选  项",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ActionItem(Icons.Default.OpenInNew, "打开") { onOpen() }
                ActionItem(Icons.Default.Download, "下载") { onDownload() }
                ActionItem(Icons.Default.Delete, "删除", error = true) { onDelete() }
                ActionItem(Icons.Default.EditNote, "重命名") { onRename() }
                ActionItem(Icons.Default.Close, "取消") { onCancel() }
            }
        }
    }
}

/** 图标+小字操作项 */
@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    error: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .size(width = 60.dp, height = 52.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = tint)
    }
}

/** 传输任务底部面板 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransfersSheet(
    transfers: List<TransferEngine.Task>,
    onDismiss: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            "传输任务",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        if (transfers.isEmpty()) {
            Text(
                "暂无传输任务",
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                listItems(transfers, key = { it.id }) { t ->
                    TransferRow(t, onPause, onResume, onCancel)
                }
            }
        }
    }
}

@Composable
private fun TransferRow(
    t: TransferEngine.Task,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (t.dir == TransferEngine.Dir.Down) "⬇️" else "⬆️",
            fontSize = 18.sp,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                t.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { t.progress() },
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                when (t.state) {
                    TransferEngine.State.Running ->
                        "${formatSize(t.doneBytes)} / ${formatSize(t.totalSize)} · ${(t.progress() * 100).toInt()}%"
                    TransferEngine.State.Paused -> "已暂停 · ${(t.progress() * 100).toInt()}%（可恢复续传）"
                    TransferEngine.State.Done -> "已完成"
                    TransferEngine.State.Error -> "失败：${t.error ?: "未知错误"}"
                    TransferEngine.State.Pending -> "等待中"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (t.state == TransferEngine.State.Error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        when (t.state) {
            TransferEngine.State.Running ->
                TextButton(onClick = { onPause(t.id) }) { Text("暂停") }
            TransferEngine.State.Paused ->
                TextButton(onClick = { onResume(t.id) }) { Text("继续") }
            else -> {}
        }
        TextButton(onClick = { onCancel(t.id) }) { Text("取消") }
    }
}

/** 文件预览对话框：按类型真实渲染（文本读流 / 图片渲染 / 视频音频播放） */
@Composable
internal fun FilePreviewDialog(
    vm: AppViewModel,
    path: String,
    obj: OpenListApi.FsObj,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    val kind = fileKind(obj.name)
    var previewText by remember { mutableStateOf<String?>(null) }
    var textLoading by remember { mutableStateOf(kind == "text" || kind == "markdown" || kind == "csv" || kind == "code") }
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var imageLoading by remember { mutableStateOf(kind == "image") }
    var mediaUrl by remember { mutableStateOf<String?>(null) }
    var mediaLoading by remember { mutableStateOf(kind == "video" || kind == "audio") }
    var mediaError by remember { mutableStateOf(false) }
    var docBytes by remember { mutableStateOf<ByteArray?>(null) }
    var docLoading by remember { mutableStateOf(kind == "doc") }
    var docError by remember { mutableStateOf(false) }
    var docxBlocks by remember { mutableStateOf<List<com.pocketnas.pro.core.DocRender.DocxBlock>?>(null) }
    var xlsxRows by remember { mutableStateOf<List<List<String>>?>(null) }
    var showDocFull by remember { mutableStateOf(false) }
    var pdfFile by remember { mutableStateOf<java.io.File?>(null) }
    var pdfPage by remember { mutableStateOf(0) }
    var pdfPageCount by remember { mutableStateOf(0) }
    var pdfBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(path) {
        when (kind) {
            "text", "code" -> {
                previewText = vm.fsGetText(path)
                textLoading = false
            }
            "markdown", "csv" -> {
                previewText = vm.fsGetText(path, 1024 * 1024)
                textLoading = false
            }
            "image" -> {
                imageBytes = vm.loadImageBytes(path)
                imageLoading = false
            }
            "video", "audio" -> {
                val raw = vm.mediaUrl(path)
                // mediaUrl 已返回完整可访问 URL（修正 host/port 后），不再拼 mediaBaseUrl
                mediaUrl = raw
                mediaLoading = false
                if (raw.isNullOrBlank()) mediaError = true
            }
            "doc" -> {
                if (obj.name.lowercase().endsWith(".pdf")) {
                    pdfFile = vm.loadPdfFile(path)
                    val f = pdfFile
                    if (f != null) {
                        val renderer = DocRender.openPdf(f)
                        if (renderer != null) {
                            pdfPageCount = renderer.pageCount
                            pdfBitmap = DocRender.renderPdfPage(renderer, 0)
                            renderer.close()
                        } else docError = true
                    } else docError = true
                } else {
                    docBytes = vm.loadDocBytes(path)
                    val bytes = docBytes
                    val lower = obj.name.lowercase()
                    if (bytes != null) {
                        if (lower.endsWith(".docx")) {
                            docxBlocks = com.pocketnas.pro.core.DocRender.parseDocx(bytes)
                        } else if (lower.endsWith(".xlsx")) {
                            xlsxRows = com.pocketnas.pro.core.DocRender.extractXlsx(bytes)
                        }
                    }
                }
                docLoading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(obj.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (kind) {
                    "text", "code" -> {
                        if (textLoading) {
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        } else {
                            val text = previewText
                            if (text.isNullOrBlank()) {
                                Text(
                                    "无法读取内容",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    text,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp),
                                    maxLines = 12,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    "image" -> {
                        if (imageLoading) {
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        } else {
                            val bytes = imageBytes
                            val bitmap = bytes?.let { decodeSampled(it) }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = obj.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(280.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            } else {
                                Text(
                                    "图片加载失败",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    "video", "audio" -> {
                        val url = mediaUrl
                        if (mediaLoading) {
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        } else if (mediaError || url.isNullOrBlank()) {
                            Text(
                                "媒体流不可用（内核未返回下载地址）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            val player = rememberExoPlayer(url, onError = { mediaError = true }, mimeType = if (kind == "audio") "audio/mpeg" else null)
                            var fullscreen by remember { mutableStateOf(false) }
                            var speedIdx by remember { mutableStateOf(1) }
                            val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)

                            ExoPlayerSurface(
                                player = player,
                                useController = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (kind == "video") 240.dp else 96.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = {
                                    speedIdx = (speedIdx + 1) % speeds.size
                                    player.setPlaybackSpeed(speeds[speedIdx])
                                }) {
                                    Text("倍速 ${speeds[speedIdx]}x")
                                }
                                if (kind == "video") {
                                    TextButton(onClick = { fullscreen = true }) {
                                        Text("全屏")
                                    }
                                }
                            }
                            if (fullscreen) {
                                MainPlayerDialog(
                                    player = player,
                                    title = obj.name,
                                    isAudio = kind == "audio",
                                    onClose = { fullscreen = false },
                                )
                            }
                        }
                    }
                    "markdown" -> {
                        if (textLoading) {
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        } else {
                            val text = previewText
                            if (text.isNullOrBlank()) {
                                Text("无法读取内容", style = MaterialTheme.typography.bodySmall)
                            } else {
                                val blocks = DocRender.parseMarkdown(text)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .heightIn(max = 360.dp),
                                ) {
                                    blocks.forEach { b ->
                                        when (b) {
                                            is DocRender.MdBlock.Heading -> Text(
                                                b.text,
                                                style = when (b.level) {
                                                    1 -> MaterialTheme.typography.titleLarge
                                                    2 -> MaterialTheme.typography.titleMedium
                                                    else -> MaterialTheme.typography.titleSmall
                                                },
                                                modifier = Modifier.padding(vertical = 3.dp),
                                            )
                                            is DocRender.MdBlock.ListItem -> Row(
                                                modifier = Modifier.padding(vertical = 1.dp),
                                            ) {
                                                Text("• ", style = MaterialTheme.typography.bodyMedium)
                                                Text(b.text, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            is DocRender.MdBlock.Quote -> Text(
                                                b.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .padding(vertical = 2.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                            is DocRender.MdBlock.Code -> Text(
                                                b.text,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .padding(8.dp),
                                            )
                                            is DocRender.MdBlock.Para -> Text(
                                                b.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "csv" -> {
                        if (textLoading) {
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        } else {
                            val text = previewText
                            if (text.isNullOrBlank()) {
                                Text("无法读取内容", style = MaterialTheme.typography.bodySmall)
                            } else {
                                val rows = DocRender.parseCsv(text)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .heightIn(max = 360.dp),
                                ) {
                                    rows.forEachIndexed { i, cells ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                            Text(
                                                "$i",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.width(28.dp),
                                            )
                                            cells.forEach { cell ->
                                                Text(
                                                    cell,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "doc" -> {
                        if (docLoading) {
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        } else if (docError) {
                            Text(
                                "文档渲染失败（可能损坏或过大），请下载后查看",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (obj.name.lowercase().endsWith(".pdf")) {
                            val bmp = pdfBitmap
                            if (bmp == null) {
                                Text("PDF 渲染失败", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = obj.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                                    )
                                    if (pdfPageCount > 1) {
                                        Spacer(Modifier.height(6.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(
                                                enabled = pdfPage > 0,
                                                onClick = {
                                                    val f = pdfFile ?: return@TextButton
                                                    val r = DocRender.openPdf(f) ?: return@TextButton
                                                    pdfPage--
                                                    pdfBitmap = DocRender.renderPdfPage(r, pdfPage)
                                                    r.close()
                                                },
                                            ) { Text("上一页") }
                                            Text(
                                                "${pdfPage + 1} / $pdfPageCount",
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                            TextButton(
                                                enabled = pdfPage < pdfPageCount - 1,
                                                onClick = {
                                                    val f = pdfFile ?: return@TextButton
                                                    val r = DocRender.openPdf(f) ?: return@TextButton
                                                    pdfPage++
                                                    pdfBitmap = DocRender.renderPdfPage(r, pdfPage)
                                                    r.close()
                                                },
                                            ) { Text("下一页") }
                                        }
                                    }
                                }
                            }
                        } else {
                            val bytes = docBytes
                            if (bytes == null) {
                                Text("文档加载失败", style = MaterialTheme.typography.bodySmall)
                            } else {
                                val lower = obj.name.lowercase()
                                val blocks = docxBlocks
                                val rows = xlsxRows
                                val isDocx = lower.endsWith(".docx") && !blocks.isNullOrEmpty()
                                val isXlsx = lower.endsWith(".xlsx") && !rows.isNullOrEmpty()
                                if (isDocx) {
                                    Column {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .verticalScroll(rememberScrollState())
                                                .heightIn(max = 300.dp),
                                        ) {
                                            blocks.take(30).forEach { block ->
                                                when (block) {
                                                    is com.pocketnas.pro.core.DocRender.DocxBlock.Para -> {
                                                        val p = block.para
                                                        Text(
                                                            p.runs.joinToString("") { it.text },
                                                            style = when (p.style) {
                                                                "h1" -> MaterialTheme.typography.titleMedium
                                                                "h2" -> MaterialTheme.typography.titleSmall
                                                                "h3" -> MaterialTheme.typography.labelLarge
                                                                else -> MaterialTheme.typography.bodySmall
                                                            },
                                                            fontWeight = if (p.style != "normal") androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                                            modifier = Modifier.padding(vertical = 2.dp),
                                                        )
                                                    }
                                                    is com.pocketnas.pro.core.DocRender.DocxBlock.Table -> {
                                                        Text(
                                                            block.rows.joinToString("\n") { it.joinToString("  |  ") },
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontFamily = FontFamily.Monospace,
                                                            modifier = Modifier.padding(vertical = 4.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        TextButton(onClick = { showDocFull = true }) { Text("全屏查看") }
                                    }
                                } else if (isXlsx) {
                                    Column {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .verticalScroll(rememberScrollState())
                                                .heightIn(max = 300.dp),
                                        ) {
                                            rows.take(30).forEachIndexed { idx, row ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(
                                                            if (idx == 0) MaterialTheme.colorScheme.primaryContainer
                                                            else if (idx % 2 == 0) MaterialTheme.colorScheme.surface
                                                            else MaterialTheme.colorScheme.surfaceVariant
                                                        )
                                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                                ) {
                                                    row.forEach { cell ->
                                                        Text(
                                                            cell,
                                                            style = if (idx == 0) MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else MaterialTheme.typography.bodySmall,
                                                            modifier = Modifier.weight(1f),
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        TextButton(onClick = { showDocFull = true }) { Text("全屏查看") }
                                    }
                                } else {
                                    val text = bytes.toString(Charsets.UTF_8)
                                    if (text.isBlank()) {
                                        Text(
                                            "该文档类型暂不支持在线渲染，请下载后用专业软件查看",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        Text(
                                            text,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .verticalScroll(rememberScrollState())
                                                .heightIn(max = 360.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Text(iconFor(obj), fontSize = 56.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${formatSize(obj.size)} · ${formatTime(obj.modified)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!obj.isDir) {
                TextButton(onClick = onDownload) { Text("下载") }
            }
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
    if (showDocFull) {
        DocViewerDialog(
            name = obj.name,
            kind = kind,
            docxBlocks = docxBlocks,
            xlsxRows = xlsxRows,
            pdfFile = if (obj.name.lowercase().endsWith(".pdf")) pdfFile else null,
            onClose = { showDocFull = false },
            onDownload = onDownload,
        )
    }
}

/** 带采样解码，防止超大图 OOM */
private fun decodeSampled(bytes: ByteArray): android.graphics.Bitmap? {
    return try {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        val maxDim = 2048
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
            sample *= 2
        }
        val real = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, real)
    } catch (_: Exception) {
        null
    }
}

private fun fileKind(name: String): String {
    val n = name.lowercase()
    return when {
        n.endsWith(".md") -> "markdown"
        n.endsWith(".csv") -> "csv"
        n.endsWith(".txt") || n.endsWith(".m3u") || n.endsWith(".m3u8") ||
            n.endsWith(".json") || n.endsWith(".log") || n.endsWith(".srt") ||
            n.endsWith(".vtt") || n.endsWith(".ini") || n.endsWith(".conf") ||
            n.endsWith(".properties") || n.endsWith(".rtf") -> "text"
        // 代码文件（文本预览）
        n.endsWith(".kt") || n.endsWith(".kts") || n.endsWith(".java") ||
            n.endsWith(".py") || n.endsWith(".go") || n.endsWith(".rs") ||
            n.endsWith(".js") || n.endsWith(".ts") || n.endsWith(".jsx") ||
            n.endsWith(".tsx") || n.endsWith(".c") || n.endsWith(".cpp") ||
            n.endsWith(".h") || n.endsWith(".hpp") || n.endsWith(".cs") ||
            n.endsWith(".swift") || n.endsWith(".sh") || n.endsWith(".bash") ||
            n.endsWith(".sql") || n.endsWith(".xml") || n.endsWith(".html") ||
            n.endsWith(".htm") || n.endsWith(".css") || n.endsWith(".scss") ||
            n.endsWith(".yml") || n.endsWith(".yaml") || n.endsWith(".toml") ||
            n.endsWith(".gradle") || n.endsWith(".proto") || n.endsWith(".dockerfile") ||
            n.endsWith(".vue") || n.endsWith(".lua") || n.endsWith(".rb") ||
            n.endsWith(".php") || n.endsWith(".pl") || n.endsWith(".r") ||
            n.endsWith(".bat") || n.endsWith(".ps1") || n.endsWith(".cmake") ||
            n.endsWith(".mk") || n.endsWith(".makefile") -> "code"
        n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") ||
            n.endsWith(".gif") || n.endsWith(".webp") || n.endsWith(".bmp") ||
            n.endsWith(".heic") || n.endsWith(".heif") || n.endsWith(".svg") ||
            n.endsWith(".ico") || n.endsWith(".tif") || n.endsWith(".tiff") ||
            n.endsWith(".avif") -> "image"
        n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi") ||
            n.endsWith(".mov") || n.endsWith(".webm") || n.endsWith(".ts") ||
            n.endsWith(".m2ts") || n.endsWith(".flv") || n.endsWith(".wmv") ||
            n.endsWith(".3gp") || n.endsWith(".m4v") || n.endsWith(".rmvb") ||
            n.endsWith(".mpg") || n.endsWith(".mpeg") || n.endsWith(".vob") ->
            "video"
        n.endsWith(".mp3") || n.endsWith(".flac") || n.endsWith(".wav") ||
            n.endsWith(".aac") || n.endsWith(".m4a") || n.endsWith(".ogg") ||
            n.endsWith(".opus") || n.endsWith(".ape") || n.endsWith(".wma") ||
            n.endsWith(".mid") || n.endsWith(".midi") || n.endsWith(".amr") ->
            "audio"
        n.endsWith(".doc") || n.endsWith(".docx") || n.endsWith(".pdf") ||
            n.endsWith(".ppt") || n.endsWith(".pptx") || n.endsWith(".xls") ||
            n.endsWith(".xlsx") || n.endsWith(".odt") || n.endsWith(".ods") ||
            n.endsWith(".odp") || n.endsWith(".wps") -> "doc"
        n.endsWith(".zip") || n.endsWith(".rar") || n.endsWith(".7z") ||
            n.endsWith(".tar") || n.endsWith(".gz") || n.endsWith(".tgz") ||
            n.endsWith(".bz2") || n.endsWith(".xz") || n.endsWith(".zst") ||
            n.endsWith(".iso") || n.endsWith(".jar") || n.endsWith(".apk") ->
            "archive"
        n.endsWith(".epub") || n.endsWith(".mobi") || n.endsWith(".azw3") ||
            n.endsWith(".fb2") -> "ebook"
        n.endsWith(".ttf") || n.endsWith(".otf") || n.endsWith(".woff") ||
            n.endsWith(".woff2") -> "font"
        n.endsWith(".torrent") -> "torrent"
        else -> "file"
    }
}

@Composable
private fun BreadcrumbBar(
    path: String,
    mountNames: Map<String, String>,
    onNavigate: (String) -> Unit,
) {
    val segments = path.split('/').filter { it.isNotEmpty() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crumb(
            text = "/",
            active = segments.isEmpty(),
            onClick = { onNavigate("/") },
        )
        segments.forEachIndexed { index, seg ->
            Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Crumb(
                text = if (index == 0) mountNames[seg] ?: seg else seg,
                active = index == segments.lastIndex,
                onClick = {
                    val p = segments.take(index + 1).joinToString("/", prefix = "/")
                    onNavigate(p)
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Crumb(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .combinedClickable(enabled = !active, onClick = onClick)
            .padding(horizontal = 2.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 网格模式下的文件项（4 列，长按选择，加密项显示锁标） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridFileItem(
    obj: OpenListApi.FsObj,
    displayName: String,
    selected: Boolean,
    selMode: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(6.dp)
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = MaterialTheme.shapes.medium,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = iconFor(obj),
                fontSize = 34.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = Color.White, fontSize = 12.sp)
                }
            } else if (locked) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "已加密",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(14.dp),
                )
            }
            IconButton(
                onClick = onMenu,
                modifier = Modifier.align(Alignment.TopEnd).size(26.dp),
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "更多操作",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = if (obj.isDir) "文件夹" else formatSize(obj.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 列表行（长按选择，加密项显示锁标） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    obj: OpenListApi.FsObj,
    displayName: String,
    selected: Boolean,
    selMode: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Text("✓", color = Color.White, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(text = iconFor(obj), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (locked && !selMode) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "已加密",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                if (obj.isDir) "文件夹" else "${formatSize(obj.size)} · ${formatTime(obj.modified)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!selMode) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
            }
        }
    }
}

private fun iconFor(obj: OpenListApi.FsObj): String = when {
    obj.isDir -> "📁"
    else -> when (fileKind(obj.name)) {
        "video" -> "🎬"
        "audio" -> "🎵"
        "image" -> "🖼️"
        "code" -> "💻"
        "archive" -> "📦"
        "doc" -> if (obj.name.lowercase().endsWith(".pdf")) "📕" else "📄"
        "ebook" -> "📖"
        "font" -> "🔠"
        "torrent" -> "🧲"
        "markdown" -> "📝"
        "csv" -> "📊"
        else -> "📄"
    }
}

private fun formatSize(size: Long): String = when {
    size >= 1024L * 1024 * 1024 -> "%.2f GB".format(size / 1024.0 / 1024 / 1024)
    size >= 1024L * 1024 -> "%.1f MB".format(size / 1024.0 / 1024)
    size >= 1024L -> "%.1f KB".format(size / 1024.0)
    else -> "$size B"
}

private fun formatTime(iso: String): String = iso.take(10)



private fun joinPath(parent: String, child: String): String {
    val p = parent.trimEnd('/')
    return if (p.isEmpty()) "/$child" else "$p/$child"
}

private fun parentPath(path: String): String? {
    if (path == "/") return null
    val trimmed = path.trimEnd('/')
    val idx = trimmed.lastIndexOf('/')
    return if (idx <= 0) "/" else trimmed.substring(0, idx)
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (_: Exception) {
        null
    }
}
