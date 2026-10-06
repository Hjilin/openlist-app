@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.pocketnas.pro.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.pocketnas.pro.ui.components.ExoPlayerSurface
import com.pocketnas.pro.ui.components.rememberExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地磁盘读取：浏览手机本地存储（/sdcard），文本/图片/音频/视频直接预览。
 * 支持多选、复制/移动（剪贴板粘贴）、删除、编辑（文本）、属性、系统打开。
 */
@Composable
fun LocalDiskSection(
    onBackToRemote: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val rootPath = remember { android.os.Environment.getExternalStorageDirectory().absolutePath }
    var dirPath by rememberSaveable { mutableStateOf(rootPath) }
    var entries by remember { mutableStateOf(listOf<File>()) }
    var loading by remember { mutableStateOf(true) }
    var preview by remember { mutableStateOf<File?>(null) }
    var listMode by rememberSaveable { mutableStateOf(true) }

    // 多选
    var selMode by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    // 剪贴板（复制/移动）
    var clipActive by remember { mutableStateOf(false) }
    var clipMode by remember { mutableStateOf("copy") }
    var clipFiles by remember { mutableStateOf(listOf<File>()) }
    // 对话框
    var deleteTarget by remember { mutableStateOf<List<File>?>(null) }
    var editTarget by remember { mutableStateOf<File?>(null) }
    var propsTarget by remember { mutableStateOf<File?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            entries = withContext(Dispatchers.IO) {
                val f = File(dirPath)
                if (f.isDirectory) {
                    (f.listFiles() ?: emptyArray())
                        .filter { !it.name.startsWith(".") }
                        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                        .toList()
                } else emptyList()
            }
            loading = false
        }
    }

    LaunchedEffect(dirPath) { reload() }

    fun parent(): String? {
        val p = File(dirPath).parentFile?.absolutePath ?: return null
        return if (File(p).canRead()) p else null
    }

    fun goUp() {
        val p = parent() ?: return
        if (p == dirPath) return
        dirPath = p
    }

    fun selectedFiles(): List<File> = entries.filter { it.absolutePath in selected }

    fun toggleSelect(f: File) {
        selected = if (f.absolutePath in selected) selected - f.absolutePath else selected + f.absolutePath
    }

    fun startSelection(f: File) {
        selMode = true
        selected = setOf(f.absolutePath)
    }

    fun exitSelection() {
        selMode = false
        selected = emptySet()
    }

    /** 复制/移动 → 粘贴到当前目录 */
    fun doPaste() {
        if (clipFiles.isEmpty()) return
        val dst = File(dirPath)
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    for (src in clipFiles) {
                        val target = File(dst, src.name)
                        if (target.absolutePath == src.absolutePath) continue
                        if (clipMode == "move") {
                            if (!src.renameTo(target)) {
                                // 跨分区 rename 失败 → 复制后删除
                                if (src.isDirectory) src.copyRecursively(target, overwrite = true) else src.copyTo(target, overwrite = true)
                                src.deleteRecursively()
                            }
                        } else {
                            if (src.isDirectory) src.copyRecursively(target, overwrite = true) else src.copyTo(target, overwrite = true)
                        }
                    }
                    true
                } catch (_: Exception) { false }
            }
            toast = if (ok) {
                if (clipMode == "move") "已移动 ${clipFiles.size} 项到当前目录" else "已复制 ${clipFiles.size} 项到当前目录"
            } else "操作失败"
            clipActive = false
            clipFiles = emptyList()
            reload()
        }
    }

    /** 系统应用打开（FileProvider） */
    fun openWithSystem(f: File) {
        try {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, guessMime(f))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "打开方式"))
        } catch (_: Exception) {
            toast = "没有可打开该文件的应用"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("本地磁盘", style = MaterialTheme.typography.titleMedium)
                        Text(
                            dirPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selMode) exitSelection()
                        else if (dirPath != rootPath) goUp()
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (clipActive) {
                        IconButton(onClick = { doPaste() }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "粘贴")
                        }
                    }
                    if (selMode) {
                        TextButton(onClick = { selected = entries.map { it.absolutePath }.toSet() }) {
                            Text("全选", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (!selMode) {
                        IconButton(onClick = { listMode = !listMode }) {
                            Icon(
                                if (listMode) Icons.Default.GridView else Icons.Default.ViewList,
                                contentDescription = "切换视图",
                            )
                        }
                        TextButton(onClick = onBackToRemote) {
                            Text("内核存储", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (selMode) {
                SelectionBar(
                    count = selected.size,
                    single = selected.size == 1,
                    onOpen = { val f = selectedFiles().firstOrNull(); if (f != null) openWithSystem(f) },
                    onCopy = {
                        clipMode = "copy"; clipFiles = selectedFiles(); clipActive = true
                        toast = "已复制 ${clipFiles.size} 项，请到目标目录点粘贴"; exitSelection()
                    },
                    onMove = {
                        clipMode = "move"; clipFiles = selectedFiles(); clipActive = true
                        toast = "已剪切 ${clipFiles.size} 项，请到目标目录点粘贴"; exitSelection()
                    },
                    onDelete = { deleteTarget = selectedFiles() },
                    onProps = { propsTarget = selectedFiles().firstOrNull() },
                    onCancel = { exitSelection() },
                )
            }
        },
        content = { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    entries.isEmpty() -> Text(
                        "此目录为空",
                        Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listMode -> LocalList(
                        entries = entries,
                        selMode = selMode,
                        selected = selected,
                        onToggle = { toggleSelect(it) },
                        onOpen = { f ->
                            if (selMode) toggleSelect(f) else if (f.isDirectory) dirPath = f.absolutePath else preview = f
                        },
                        onLongPress = { startSelection(it) },
                    )
                    else -> LocalGrid(
                        entries = entries,
                        selMode = selMode,
                        selected = selected,
                        onToggle = { toggleSelect(it) },
                        onOpen = { f ->
                            if (selMode) toggleSelect(f) else if (f.isDirectory) dirPath = f.absolutePath else preview = f
                        },
                        onLongPress = { startSelection(it) },
                    )
                }
            }
        },
    )

    preview?.let { file -> LocalPreviewDialog(file, onDismiss = { preview = null }) }
    deleteTarget?.let { files ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除确认") },
            text = { Text("确定删除 ${files.size} 项？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val list = files
                    deleteTarget = null
                    exitSelection()
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            try { list.forEach { it.deleteRecursively() }; true } catch (_: Exception) { false }
                        }
                        toast = if (ok) "已删除" else "删除失败"
                        reload()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
    editTarget?.let { file ->
        EditTextDialog(file, onDismiss = { editTarget = null }, onSaved = {
            editTarget = null
            toast = "已保存"
            reload()
        })
    }
    propsTarget?.let { file -> PropsDialog(file, onDismiss = { propsTarget = null }) }
    toast?.let { msg ->
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = { TextButton(onClick = { toast = null }) { Text("知道了") } },
        ) { Text(msg) }
    }
}

private val textExt = setOf("txt", "md", "markdown", "json", "xml", "html", "css", "js", "kt", "java", "log", "csv", "ini", "conf", "sh", "py", "yaml", "yml", "toml", "properties")
private val imgExt = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "heic")
private val audioExt = setOf("mp3", "wav", "flac", "ogg", "m4a", "aac", "opus")
private val videoExt = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "ts", "m4v")

@Composable
private fun SelectionBar(
    count: Int,
    single: Boolean,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onProps: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column {
            HorizontalDivider()
            Text(
                "已选 $count 项",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ActionItem(Icons.Default.OpenInNew, "打开") { onOpen() }
                ActionItem(Icons.Default.ContentCopy, "复制") { onCopy() }
                ActionItem(Icons.Default.DriveFileMove, "移动") { onMove() }
                ActionItem(Icons.Default.Delete, "删除", error = true) { onDelete() }
                if (single) ActionItem(Icons.Default.Info, "属性") { onProps() }
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

/** 属性对话框 */
@Composable
private fun PropsDialog(file: File, onDismiss: () -> Unit) {
    val ext = file.extension.lowercase()
    val type = when {
        file.isDirectory -> "文件夹"
        ext in imgExt -> "图片"
        ext in audioExt -> "音频"
        ext in videoExt -> "视频"
        ext in textExt -> "文本"
        else -> "${ext.uppercase()} 文件"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("属性") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PropRow("名称", file.name)
                PropRow("类型", type)
                PropRow("大小", if (file.isDirectory) "—" else formatSize(file.length()))
                PropRow("位置", file.parent ?: "")
                PropRow(
                    "修改时间",
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified())),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun PropRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(v, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** 文本编辑对话框 */
@Composable
private fun EditTextDialog(file: File, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var content by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(file) {
        val t = withContext(Dispatchers.IO) {
            try {
                val all = file.readBytes()
                val bytes = if (all.size > 1024 * 1024) all.copyOfRange(0, 1024 * 1024) else all
                String(bytes, Charsets.UTF_8)
            } catch (_: Exception) { "" }
        }
        content = t
        draft = t
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑 ${file.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            val c = content
            if (c == null) {
                CircularProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            } else {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val text = draft
                onDismiss()
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    try { file.writeText(text, Charsets.UTF_8) } catch (_: Exception) {}
                }
                onSaved()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun LocalList(
    entries: List<File>,
    selMode: Boolean,
    selected: Set<String>,
    onToggle: (File) -> Unit,
    onOpen: (File) -> Unit,
    onLongPress: (File) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(entries, key = { it.absolutePath }) { f ->
            val isSel = f.absolutePath in selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
                    .combinedClickable(onClick = { onOpen(f) }, onLongClick = { onLongPress(f) })
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selMode) {
                    Checkbox(checked = isSel, onCheckedChange = { onToggle(f) })
                }
                LocalThumb(f, 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (f.isDirectory) "文件夹" else formatSize(f.length()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (f.isDirectory) Icons.Default.ChevronRight else Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun LocalGrid(
    entries: List<File>,
    selMode: Boolean,
    selected: Set<String>,
    onToggle: (File) -> Unit,
    onOpen: (File) -> Unit,
    onLongPress: (File) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(8.dp),
    ) {
        gridItems(entries, key = { it.absolutePath }) { f ->
            val isSel = f.absolutePath in selected
            Column(
                modifier = Modifier
                    .padding(4.dp)
                    .background(if (isSel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent, MaterialTheme.shapes.small)
                    .combinedClickable(onClick = { onOpen(f) }, onLongClick = { onLongPress(f) })
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (selMode) {
                    Checkbox(checked = isSel, onCheckedChange = { onToggle(f) }, modifier = Modifier.align(Alignment.End))
                }
                LocalThumb(f, 52.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    f.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** 本地文件缩略图：图片/视频帧/音频封面/PDF 首页，无则类型图标 */
@Composable
private fun LocalThumb(file: File, size: androidx.compose.ui.unit.Dp) {
    var bmp by remember(file.absolutePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file.absolutePath) {
        bmp = withContext(Dispatchers.IO) { loadLocalThumb(file) }
    }
    val b = bmp
    Box(
        Modifier.size(size).background(thumbBg(file), MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                if (file.isDirectory) Icons.Default.Folder else fileIcon(file),
                contentDescription = null,
                tint = iconTint(file),
            )
        }
    }
}

private val docxExt = setOf("doc", "docx", "odt", "rtf")
private val xlsxExt = setOf("xls", "xlsx", "ods", "csv")
private val pptExt = setOf("ppt", "pptx", "odp")
private val zipExt = setOf("zip", "rar", "7z", "tar", "gz")

/** 文件类型识别背景色（主流文件管理器风格） */
@Composable
private fun thumbBg(file: File): Color {
    val ext = file.extension.lowercase()
    return when {
        file.isDirectory -> MaterialTheme.colorScheme.surfaceVariant
        ext in imgExt || ext in videoExt || ext in audioExt -> MaterialTheme.colorScheme.surfaceVariant
        ext == "pdf" -> Color(0xFFFFEBEE)
        ext in docxExt -> Color(0xFFE3F2FD)
        ext in xlsxExt -> Color(0xFFE8F5E9)
        ext in pptExt -> Color(0xFFFFF3E0)
        ext in zipExt -> Color(0xFFF3E5F5)
        ext == "apk" -> Color(0xFFE0F7FA)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}

/** 类型图标颜色 */
@Composable
private fun iconTint(file: File): Color {
    val ext = file.extension.lowercase()
    return when {
        file.isDirectory -> MaterialTheme.colorScheme.primary
        ext == "pdf" -> Color(0xFFD32F2F)
        ext in docxExt -> Color(0xFF1976D2)
        ext in xlsxExt -> Color(0xFF388E3C)
        ext in pptExt -> Color(0xFFF57C00)
        ext in zipExt -> Color(0xFF7B1FA2)
        ext == "apk" -> Color(0xFF00838F)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun loadLocalThumb(file: File): android.graphics.Bitmap? {
    val ext = file.extension.lowercase()
    return try {
        when {
            ext in imgExt -> {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 3 }
                android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
            }
            ext in videoExt -> {
                val mmr = android.media.MediaMetadataRetriever()
                try {
                    mmr.setDataSource(file.absolutePath)
                    mmr.getFrameAtTime(500_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally { mmr.release() }
            }
            ext in audioExt -> {
                val mmr = android.media.MediaMetadataRetriever()
                try {
                    mmr.setDataSource(file.absolutePath)
                    val pic = mmr.embeddedPicture
                    if (pic == null) null else android.graphics.BitmapFactory.decodeByteArray(pic, 0, pic.size)
                } finally { mmr.release() }
            }
            ext == "pdf" -> {
                val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                try {
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val b = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
                        page.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        b
                    } else null
                } finally { renderer.close(); pfd.close() }
            }
            else -> null
        }
    } catch (_: Exception) { null }
}

private fun fileIcon(f: File): androidx.compose.ui.graphics.vector.ImageVector {
    val ext = f.extension.lowercase()
    return when {
        ext in imgExt -> Icons.Default.Image
        ext in audioExt -> Icons.Default.MusicNote
        ext in videoExt -> Icons.Default.PlayCircle
        ext == "pdf" -> Icons.Default.PictureAsPdf
        ext in docxExt -> Icons.Default.Article
        ext in xlsxExt -> Icons.Default.TableChart
        ext in zipExt -> Icons.Default.FolderZip
        ext == "apk" -> Icons.Default.Android
        ext in textExt -> Icons.Default.Article
        else -> Icons.Default.Description
    }
}

private fun formatSize(size: Long): String = when {
    size >= 1024L * 1024 * 1024 -> "%.1f GB".format(size / 1024.0 / 1024 / 1024)
    size >= 1024L * 1024 -> "%.1f MB".format(size / 1024.0 / 1024)
    size >= 1024L -> "%.1f KB".format(size / 1024.0)
    else -> "$size B"
}

private fun guessMime(f: File): String {
    val ext = f.extension.lowercase()
    return when {
        ext in imgExt -> "image/*"
        ext in videoExt -> "video/*"
        ext in audioExt -> "audio/*"
        ext == "pdf" -> "application/pdf"
        ext in docxExt -> "application/msword"
        ext in xlsxExt -> "application/vnd.ms-excel"
        ext in pptExt -> "application/vnd.ms-powerpoint"
        ext == "apk" -> "application/vnd.android.package-archive"
        ext in textExt -> "text/plain"
        else -> "*/*"
    }
}

/** 本地文件预览：文本 / 图片 / 音频 / 视频 */
@Composable
private fun LocalPreviewDialog(file: File, onDismiss: () -> Unit) {
    val ext = file.extension.lowercase()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (ext in imgExt || ext in videoExt) Color.Black else MaterialTheme.colorScheme.surface,
        ) {
            when {
                ext in imgExt -> LocalImagePreview(file, onDismiss)
                ext in audioExt || ext in videoExt -> LocalMediaPreview(file, onDismiss)
                ext in textExt -> LocalTextPreview(file, onDismiss)
                else -> {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("不支持预览该格式", color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(12.dp))
                        Text(file.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(20.dp))
                        TextButton(onClick = onDismiss) { Text("关闭") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalImagePreview(file: File, onDismiss: () -> Unit) {
    var bmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file) {
        bmp = withContext(Dispatchers.IO) {
            try {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
            } catch (_: Exception) { null }
        }
    }
    Box(Modifier.fillMaxSize().combinedClickable(onClick = onDismiss, onLongClick = null), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
        Text(
            file.name,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.5f)).padding(8.dp),
        )
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        ) { Text("关闭", color = Color.White) }
    }
}

@Composable
private fun LocalMediaPreview(file: File, onDismiss: () -> Unit) {
    val uri = remember(file) { Uri.fromFile(file) }
    val mime = when {
        file.extension.lowercase() in videoExt -> "video/mp4"
        else -> "audio/mpeg"
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(file.name, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onDismiss) { Text("关闭", color = Color.White) }
        }
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            val player = rememberExoPlayer(url = uri.toString(), onError = { }, mimeType = mime)
            ExoPlayerSurface(player = player)
        }
    }
}


@Composable
private fun LocalTextPreview(file: File, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var editMode by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(file) {
        val t = withContext(Dispatchers.IO) {
            try {
                val all = file.readBytes()
                val bytes = if (all.size > 500 * 1024) all.copyOfRange(0, 500 * 1024) else all
                String(bytes, Charsets.UTF_8)
            } catch (_: Exception) { "（无法读取文件）" }
        }
        text = t
        draft = t
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(file.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!editMode) {
                TextButton(onClick = { editMode = true; draft = text ?: "" }) { Text("编辑") }
            }
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        val t = text
        if (t == null) {
            CircularProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        } else if (editMode) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { editMode = false; draft = t }) { Text("取消") }
                TextButton(onClick = {
                    val content = draft
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try { file.writeText(content, Charsets.UTF_8) } catch (_: Exception) {}
                        }
                        editMode = false
                        text = content
                    }
                }) { Text("保存") }
            }
        } else {
            val scroll = rememberScrollState()
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .fillMaxWidth(),
            ) {
                Text(
                    t,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }
    }
}
