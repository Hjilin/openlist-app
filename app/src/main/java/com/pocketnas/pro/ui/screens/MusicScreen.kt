package com.pocketnas.pro.ui.screens
import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

private val TextMain = Color(0xFF1B2130)
private val TextGray = Color(0xFF9AA3B5)
private val BgLight = Color(0xFFF3F5F9)

@Composable
fun MusicScreen(vm: AppViewModel, nav: NavHostController) {
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

        // 播放器卡片
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
                            Text("背景音乐 - 夜间专注", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(4.dp))
                            Text("OpenList · 本地缓存", fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⏮", fontSize = 22.sp, color = Color.White)
                        Spacer(Modifier.width(26.dp))
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("▶", fontSize = 20.sp, color = Color(0xFF26345C))
                        }
                        Spacer(Modifier.width(26.dp))
                        Text("⏭", fontSize = 22.sp, color = Color.White)
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
            Text("全部 ›", fontSize = 12.sp, color = TextGray)
        }

        // 示例音频列表
        MusicItem("夜间专注.mp3", "04:12 · 9.6MB")
        MusicItem("起风了.flac", "05:11 · 32MB")
        MusicItem("纯音乐 - 雨声.wav", "60:00 · 605MB")
        MusicItem("BGM 合集.m4a", "12 首 · 128MB")
    }
}

@Composable
private fun MusicItem(name: String, info: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
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
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
                Text(info, fontSize = 12.sp, color = TextGray)
            }
        }
    }
}
