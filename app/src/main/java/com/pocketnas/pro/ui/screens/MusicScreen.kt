package com.pocketnas.pro.ui.screens
import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.pocketnas.pro.ui.AppViewModel
import com.pocketnas.pro.ui.components.ExoPlayerSurface
import com.pocketnas.pro.ui.components.rememberExoPlayer
import kotlinx.coroutines.launch

private val TextMain = Color(0xFF1B2130)
private val TextGray = Color(0xFF9AA3B5)
private val BgLight = Color(0xFFF3F5F9)

private fun formatSize(size: Long): String = when {
    size >= 1024L * 1024 * 1024 -> "%.1f GB".format(size.toDouble() / (1024 * 1024 * 1024))
    size >= 1024L * 1024 -> "%.1f MB".format(size.toDouble() / (1024 * 1024))
    size >= 1024L -> "%.0f KB".format(size.toDouble() / 1024)
    else -> "$size B"
}

@Composable
fun MusicScreen(vm: AppViewModel, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    var audioList by remember { mutableStateOf<List<AppViewModel.AudioEntry>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var current by remember { mutableStateOf<AppViewModel.AudioEntry?>(null) }
    var playUrl by remember { mutableStateOf<String?>(null) }

    // 扫描内核音频文件
    LaunchedEffect(Unit) {
        vm.scanAudio { list ->
            audioList = list
            scanning = false
        }
    }

    // 当前选中音频生成播放地址
    LaunchedEffect(current) {
        playUrl = null
        val c = current ?: return@LaunchedEffect
        playUrl = vm.mediaUrl(c.path)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgLight)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("正在播放", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
            Text("播放列表", fontSize = 12.sp, color = TextGray)
        }

        // 播放器卡片（当前选中音频，流式播放，走内核下载地址）
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Box(
                modifier = Modifier
                    .background(Brush.linearGradient(listOf(Color(0xFF26345C), Color(0xFF3A4F8F))))
                    .padding(18.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(66.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF7AA2FF), Color(0xFFB9A4FF)))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                current?.name ?: "未选择音频",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (current != null) "OpenList · ${formatSize(current!!.size)}" else "点击下方音频开始播放",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.75f),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    val url = playUrl
                    if (current != null && !url.isNullOrBlank()) {
                        val player = rememberExoPlayer(url, mimeType = "audio/mpeg")
                        ExoPlayerSurface(
                            player = player,
                            useController = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp),
                        )
                    } else if (current != null) {
                        Text(
                            "音频流加载中…",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("音频文件", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
            Text("共 ${audioList.size} 个", fontSize = 12.sp, color = TextGray)
        }

        when {
            scanning -> {
                CircularProgressIndicator(modifier = Modifier.padding(24.dp).align(Alignment.CenterHorizontally))
                Text(
                    "正在扫描内核音频…",
                    fontSize = 13.sp,
                    color = TextGray,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp),
                )
            }
            audioList.isEmpty() -> {
                Text(
                    "未找到音频文件\n（请在存储源中放入音乐后刷新）",
                    fontSize = 13.sp,
                    color = TextGray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            else -> {
                audioList.forEach { a ->
                    MusicItem(name = a.name, info = formatSize(a.size)) {
                        current = a
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicItem(name: String, info: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier.padding(14.dp).clickableItem(onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFFF0F4), Color(0xFFFFE3EC)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFFF6B9D))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextMain, maxLines = 1)
                Text(info, fontSize = 12.sp, color = TextGray)
            }
        }
    }
}

/** 点击修饰符封装（避免与滚动冲突） */
private fun Modifier.clickableItem(onClick: () -> Unit): Modifier =
    this.then(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    )
