package com.pocketnas.pro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Media3 ExoPlayer 播放器（格式与能力远超系统 MediaPlayer）。
 *
 * 格式支持：
 * - 视频：MKV / H.265(HEVC，含 4K 硬解) / H.264(含 4K) / VP9 / AV1(设备支持时) / WebM / MP4 / TS / FLV
 * - 音频：MP3 / AAC / FLAC / OGG / Opus / WAV / M4A / WMA / APE(部分)
 * 播放地址走 KernelHttpProxy（http://127.0.0.1:<port>/d/...），支持 Range / seek。
 */

/** 创建并持有 ExoPlayer（调用方负责在退出时 release） */
@Composable
fun rememberExoPlayer(
    url: String,
    onError: (() -> Unit)? = null,
    mimeType: String? = null,
): ExoPlayer {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            val item = if (mimeType != null) MediaItem.Builder().setUri(url).setMimeType(mimeType).build() else MediaItem.fromUri(url)
            setMediaItem(item)
            playWhenReady = true
            prepare()
            if (onError != null) {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        com.pocketnas.pro.core.LogStore.log("PLAY", "onPlayerError code=${error.errorCode} name=${error.errorCodeName} msg=${error.message} url=$url")
                        onError()
                    }
                })
            }
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

/** 渲染 ExoPlayer 画面的 PlayerView（可复用同一 player 于预览/全屏切换） */
@Composable
fun ExoPlayerSurface(
    player: ExoPlayer?,
    modifier: Modifier = Modifier,
    useController: Boolean = true,
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.useController = useController
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
            }
        },
        update = { view ->
            view.player = player
        },
        modifier = modifier,
    )
}

/** 时间格式化 mm:ss / h:mm:ss */
fun formatPlayerTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
}

/**
 * 主流全屏播放器 UI（视频/音频统一）：
 * 顶部标题栏、底部进度条+控制（播放/暂停、倍速、音量、时间）、点击切换控制显隐、自动隐藏。
 */
@Composable
fun MainPlayerDialog(
    player: ExoPlayer,
    title: String,
    isAudio: Boolean,
    onClose: () -> Unit,
) {
    var showControls by remember { mutableStateOf(true) }
    var speedIdx by remember { mutableStateOf(2) }
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var volume by remember { mutableStateOf(1f) }

    // 进度轮询
    LaunchedEffect(player) {
        while (true) {
            val d = player.duration
            durationMs = if (d > 0) d else 0L
            positionMs = player.currentPosition.coerceIn(0, if (d > 0) d else Long.MAX_VALUE)
            isPlaying = player.isPlaying
            delay(500)
        }
    }

    // 控制栏自动隐藏
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // 画面层
            if (isAudio) {
                // 音频视觉：动态渐变 + 唱片
                AudioDiscVisual(playing = isPlaying)
            } else {
                ExoPlayerSurface(
                    player = player,
                    useController = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 点击层：单击切换控制栏
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showControls = !showControls },
            )

            // 顶部标题栏
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
                    }
                    Text(
                        title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!isAudio) {
                        Text(
                            "旋转横屏可获得最佳体验",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            // 底部控制栏
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    // 进度条
                    val dur = durationMs
                    Slider(
                        value = if (dur > 0) positionMs.toFloat().coerceIn(0f, dur.toFloat()) else 0f,
                        onValueChange = { v ->
                            positionMs = v.toLong()
                            player.seekTo(v.toLong())
                        },
                        valueRange = 0f..if (dur > 0) dur.toFloat() else 1f,
                        enabled = dur > 0,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFF4FC3F7),
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            formatPlayerTime(positionMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(56.dp),
                        )
                        Text(
                            formatPlayerTime(dur),
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(56.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        // 音量
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Slider(
                            value = volume,
                            onValueChange = { v ->
                                volume = v
                                player.volume = v
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.width(90.dp).height(28.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color(0xFF4FC3F7),
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            ),
                        )
                        // 倍速
                        TextButton(onClick = {
                            speedIdx = (speedIdx + 1) % speeds.size
                            player.setPlaybackSpeed(speeds[speedIdx])
                        }) {
                            Text(
                                "${speeds[speedIdx]}x",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        // 播放/暂停
                        FilledIconButton(
                            onClick = {
                                if (player.isPlaying) player.pause() else player.play()
                            },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFF4FC3F7),
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 音频播放视觉：深色渐变 + 旋转唱片 + 音符 */
@Composable
private fun AudioDiscVisual(playing: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF1A2733), Color(0xFF0B0F14)),
                    center = androidx.compose.ui.geometry.Offset(500f, 400f),
                    radius = 900f,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 唱片
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2C3E50), Color(0xFF111820))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A1A1A)),
                    contentAlignment = Alignment.Center,
                ) {
                    // 唱片纹理环
                    for (i in 0..5) {
                        Box(
                            modifier = Modifier
                                .size((150 - i * 24).dp)
                                .clip(CircleShape)
                                .border(0.8.dp, Color(0xFF2E2E2E), CircleShape),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4FC3F7)),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color(0xFF4FC3F7),
                modifier = Modifier.size(40.dp),
            )
            Text(
                if (playing) "正在播放" else "已暂停",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
