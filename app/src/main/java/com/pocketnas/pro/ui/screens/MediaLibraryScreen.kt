@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.pocketnas.pro.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnas.pro.core.OpenListApi
import com.pocketnas.pro.ui.AppViewModel
import com.pocketnas.pro.ui.components.ImageViewerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 媒体库视图模式 */
private enum class MediaViewMode { Wall, Timeline }

/**
 * 媒体库相册视图：缩略图墙（3 列）/ 时间线分组，筛选全部/图片/视频，
 * 长按多选批量下载/删除，点击进入媒体预览（复用 FilePreviewDialog）。
 * 加密目录与排除目录在扫描时已过滤（见 AppViewModel.scanMedia）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLibraryScreen(vm: AppViewModel) {
    val all by vm.mediaList.collectAsState()
    val loading by vm.mediaLoading.collectAsState()
    val filter by vm.mediaFilter.collectAsState()

    var viewMode by remember { mutableStateOf(MediaViewMode.Wall) }
    var selMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var preview by remember { mutableStateOf<AppViewModel.MediaItem?>(null) }
    var galleryItems by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var galleryIndex by remember { mutableIntStateOf(0) }

    val filtered = remember(all, filter) {
        when (filter) {
            AppViewModel.MediaFilter.All -> all
            AppViewModel.MediaFilter.Image -> all.filter { !it.isVideo && !it.isAudio }
            AppViewModel.MediaFilter.Video -> all.filter { it.isVideo }
            AppViewModel.MediaFilter.Audio -> all.filter { it.isAudio }
        }
    }

    LaunchedEffect(Unit) { vm.scanMedia() }

    fun exitSel() {
        selMode = false
        selected = emptySet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selMode) "已选 ${selected.size} 项" else "媒体库",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                actions = {
                    if (!selMode) {
                        IconButton(onClick = { vm.scanMedia() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // 筛选标签
            if (!selMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppViewModel.MediaFilter.values().forEach { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { vm.setMediaFilter(f) },
                            label = { Text(f.label) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    FilterChip(
                        selected = viewMode == MediaViewMode.Timeline,
                        onClick = {
                            viewMode =
                                if (viewMode == MediaViewMode.Timeline) MediaViewMode.Wall else MediaViewMode.Timeline
                        },
                        label = { Text(if (viewMode == MediaViewMode.Timeline) "时间线" else "网格") },
                    )
                }
            }

            if (loading && filtered.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            } else if (filtered.isEmpty()) {
                Text(
                    "媒体库为空（加密/排除目录已过滤）",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MediaGrid(
                    items = filtered,
                    vm = vm,
                    viewMode = viewMode,
                    selMode = selMode,
                    selected = selected,
                    onClick = { item ->
                        if (selMode) {
                            selected = toggleSet(selected, item.path)
                            if (selected.isEmpty()) selMode = false
                        } else {
                            if (item.isVideo || item.isAudio) {
                                preview = item
                            } else {
                                // 图片 → 全屏图库查看器（滑动/缩放）
                                val imgs = filtered.filter { !it.isVideo && !it.isAudio }
                                val idx = imgs.indexOfFirst { it.path == item.path }
                                if (idx >= 0) {
                                    galleryItems = imgs.map { it.path to it.name }
                                    galleryIndex = idx
                                }
                            }
                        }
                    },
                    onLongClick = { item ->
                        if (!selMode) {
                            selMode = true
                            selected = setOf(item.path)
                        }
                    },
                    thumb = { path ->
                        vm.loadThumb(path) ?: vm.loadImageBytes(path)?.let { decodeMediaImage(it) }
                    },
                )
            }
        }
    }

    // 多选底部操作栏
    if (selMode) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            val selItems = filtered.filter { it.path in selected }
            ActionBtn(Icons.Default.OpenInNew, "打开") {
                if (selItems.size == 1) {
                    preview = selItems[0]
                    exitSel()
                }
            }
            ActionBtn(Icons.Default.Download, "下载") {
                selItems.forEach { it ->
                    vm.startDownload(it.path, it.name, it.size)
                }
                vm.showNotice("已加入 ${selItems.size} 个下载任务")
                exitSel()
            }
            ActionBtn(Icons.Default.Delete, "删除") {
                selItems.forEach { it ->
                    vm.remove(parentOf(it.path), listOf(it.name))
                }
                vm.scanMedia()
                exitSel()
            }
            ActionBtn(Icons.Default.Close, "取消") { exitSel() }
        }
    }

    // 媒体预览（复用文件页预览器）
    preview?.let { item ->
        FilePreviewDialog(
            vm = vm,
            path = item.path,
            obj = OpenListApi.FsObj(
                name = item.name,
                size = item.size,
                isDir = false,
                modified = item.modified,
                sign = "",
                thumb = item.thumb,
                type = when { item.isVideo -> 2; item.isAudio -> 3; else -> 4 },
            ),
            onDismiss = { preview = null },
            onDownload = {
                vm.startDownload(item.path, item.name, item.size)
                preview = null
            },
        )
    }

    // 全屏图库查看器（图片滑动 / 缩放）
    galleryItems?.let { items ->
        ImageViewerDialog(
            items = items,
            initialIndex = galleryIndex,
            loadImage = { path ->
                vm.loadImageBytes(path)?.let { b -> decodeMediaImage(b) }
            },
            onClose = { galleryItems = null },
            onDownload = { path, name ->
                vm.startDownload(path, name, 0)
                vm.showNotice("已加入下载任务：$name")
            },
        )
    }
}

/** 图库解码（采样防 OOM） */
private fun decodeMediaImage(bytes: ByteArray): android.graphics.Bitmap? {
    return try {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        val maxDim = 2048
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) sample *= 2
        val real = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, real)
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun MediaGrid(
    items: List<AppViewModel.MediaItem>,
    vm: AppViewModel,
    viewMode: MediaViewMode,
    selMode: Boolean,
    selected: Set<String>,
    onClick: (AppViewModel.MediaItem) -> Unit,
    onLongClick: (AppViewModel.MediaItem) -> Unit,
    thumb: suspend (String) -> android.graphics.Bitmap?,
) {
    if (viewMode == MediaViewMode.Timeline) {
        TimelineGrid(items, vm, selMode, selected, onClick, onLongClick, thumb)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
        ) {
            items(items, key = { it.path }) { item ->
                ThumbCell(item, vm, selMode, selected, onClick, onLongClick, thumb)
            }
        }
    }
}

@Composable
private fun TimelineGrid(
    items: List<AppViewModel.MediaItem>,
    vm: AppViewModel,
    selMode: Boolean,
    selected: Set<String>,
    onClick: (AppViewModel.MediaItem) -> Unit,
    onLongClick: (AppViewModel.MediaItem) -> Unit,
    thumb: suspend (String) -> android.graphics.Bitmap?,
) {
    // 按日期分组：今天 / 最近7天 / 本月 / 更早
    val groups = remember(items) {
        val today = java.time.LocalDate.now()
        val map = linkedMapOf<String, MutableList<AppViewModel.MediaItem>>()
        for (it in items) {
            val d = it.modified.take(10).takeIf { s -> s.length == 10 }
            val label = when {
                d == null -> "未知日期"
                d == today.toString() -> "今天"
                d >= today.minusDays(7).toString() -> "最近 7 天"
                d.take(7) == today.toString().take(7) -> "本月"
                else -> "更早"
            }
            map.getOrPut(label) { mutableListOf() }.add(it)
        }
        map
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
    ) {
        groups.forEach { (label, list) ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(list, key = { it.path }) { item ->
                ThumbCell(item, vm, selMode, selected, onClick, onLongClick, thumb)
            }
        }
    }
}

@Composable
private fun ThumbCell(
    item: AppViewModel.MediaItem,
    vm: AppViewModel,
    selMode: Boolean,
    selected: Set<String>,
    onClick: (AppViewModel.MediaItem) -> Unit,
    onLongClick: (AppViewModel.MediaItem) -> Unit,
    thumb: suspend (String) -> android.graphics.Bitmap?,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var bmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val isSel = selected.contains(item.path)

    LaunchedEffect(item.thumb, item.path) {
        val viaThumb = if (item.thumb.isNotBlank()) withContext(Dispatchers.IO) { thumb(item.thumb) } else null
        bmp = viaThumb ?: withContext(Dispatchers.IO) { thumb(item.path) }
        if (item.isAudio && bmp == null) {
            bmp = withContext(Dispatchers.IO) { vm.loadAudioCover(item.path) }
        }
    }

    Box(
        modifier = Modifier
            .padding(3.dp)
            .aspectRatio(1f)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    if (isSel) listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primaryContainer)
                    else if (item.isAudio) listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceVariant)
                    else listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
                ),
                MaterialTheme.shapes.small,
            )
            .combinedClickable(
                onClick = { onClick(item) },
                onLongClick = { onLongClick(item) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            if (item.isAudio) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
            } else {
                Text(
                    if (item.isVideo) "▶" else "🖼",
                    fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                )
            }
        }
        // 视频标记
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("▶", color = Color.White, fontSize = 10.sp)
            }
        }
        // 多选角标
        if (selMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .background(
                        if (isSel) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (isSel) "✓" else "", color = Color.White, fontSize = 12.sp)
            }
        }
        // 文件名
        Text(
            item.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun ActionBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = null),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun toggleSet(set: Set<String>, v: String): Set<String> =
    if (v in set) set - v else set + v

private fun parentOf(path: String): String {
    val trimmed = path.trimEnd('/')
    val idx = trimmed.lastIndexOf('/')
    return if (idx <= 0) "/" else trimmed.substring(0, idx)
}

