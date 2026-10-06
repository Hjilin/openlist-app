package com.pocketnas.pro.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全屏图库查看器（对标主流相册）：
 * - HorizontalPager 左右滑动切换
 * - 双指缩放 / 平移（detectTransformGestures）
 * - 顶部信息栏（序号/文件名/关闭）+ 底部操作（下载/分享）
 * 图片字节由 [loadImage] 按需加载（内核 /p/ 下载）。
 */
@Composable
fun ImageViewerDialog(
    items: List<Pair<String, String>>, // (path, name)
    initialIndex: Int,
    loadImage: suspend (String) -> android.graphics.Bitmap?,
    onClose: () -> Unit,
    onDownload: (String, String) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) {
        items.size
    }
    var current by remember { mutableIntStateOf(initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.ui.graphics.Color.Black,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (items.isEmpty()) {
                    Text("无图片", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.align(Alignment.Center))
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val item = items[page]
                        var bmp by remember(page) { mutableStateOf<android.graphics.Bitmap?>(null) }
                        LaunchedEffect(page) {
                            bmp = withContext(Dispatchers.IO) { loadImage(item.first) }
                        }
                        val b = bmp
                        if (b != null) {
                            ZoomableImage(bitmap = b)
                        } else {
                            CircularProgressIndicator(
                                color = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                }

                // 顶部信息栏
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onClose) {
                            Text("✕", color = androidx.compose.ui.graphics.Color.White, fontSize = 18.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                items.getOrNull(current)?.second ?: "",
                                color = androidx.compose.ui.graphics.Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${current + 1} / ${items.size}",
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                // 底部操作
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    val cur = items.getOrNull(current)
                    if (cur != null) {
                        TextButton(onClick = { onDownload(cur.first, cur.second) }) {
                            Text("下载", color = androidx.compose.ui.graphics.Color.White)
                        }
                        TextButton(onClick = {
                            scope.launch {
                                if (current > 0) { pagerState.animateScrollToPage(current - 1) }
                            }
                        }, enabled = current > 0) {
                            Text("上一张", color = androidx.compose.ui.graphics.Color.White)
                        }
                        TextButton(onClick = {
                            scope.launch {
                                if (current < items.size - 1) { pagerState.animateScrollToPage(current + 1) }
                            }
                        }, enabled = current < items.size - 1) {
                            Text("下一张", color = androidx.compose.ui.graphics.Color.White)
                        }
                    }
                }
            }
        }
    }

    // 页面切换同步 current（延迟 200ms 等动画稳定）
    LaunchedEffect(pagerState.currentPage) {
        kotlinx.coroutines.delay(200)
        current = pagerState.currentPage
    }
}

/** 双指缩放 + 平移的图片 */
@Composable
private fun ZoomableImage(bitmap: android.graphics.Bitmap) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 6f)
                    // 缩放中心粗略处理：平移按缩放比例累计
                    offsetX = (offsetX + pan.x).coerceIn(-2000f, 2000f)
                    offsetY = (offsetY + pan.y).coerceIn(-2000f, 2000f)
                    scale = newScale
                }
            }
            .pointerInput(Unit) {
                // 双击重置
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > 1f) 1f else 2.5f
                    offsetX = 0f; offsetY = 0f
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
        )
    }
}
