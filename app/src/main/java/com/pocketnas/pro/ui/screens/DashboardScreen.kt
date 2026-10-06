package com.pocketnas.pro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
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
import com.pocketnas.pro.core.NasState
import com.pocketnas.pro.core.OpenListApi
import com.pocketnas.pro.ui.AppViewModel

private val BluePrimary = Color(0xFF2F6BFF)
private val BluePurple = Color(0xFF6A5CFF)
private val TextMain = Color(0xFF1B2130)
private val TextGray = Color(0xFF9AA3B5)
private val BgLight = Color(0xFFF3F5F9)

@Composable
fun DashboardScreen(vm: AppViewModel, nav: NavHostController) {
    val running by NasState.running.collectAsState()
    val storages by vm.storages.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BgLight),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // 蓝色渐变 Hero 卡片
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Box(
                    modifier = Modifier
                        .background(Brush.linearGradient(listOf(BluePrimary, BluePurple)))
                        .padding(18.dp),
                ) {
                    Column {
                        Text("我的网盘", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "admin · ${storages.size} 个挂载源已连接",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                        Spacer(Modifier.height(14.dp))
                        // 用量条
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.3f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.62f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("已用 4.82 TB", fontSize = 12.sp, color = Color.White.copy(alpha = 0.92f))
                            Text("总量 7.8 TB", fontSize = 12.sp, color = Color.White.copy(alpha = 0.92f))
                        }
                        Spacer(Modifier.height(14.dp))
                        // 启动/停止按钮
                        Row {
                            if (running) {
                                OutlinedButton(
                                    onClick = { vm.stopService() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                ) { Text("停止服务") }
                            } else {
                                Button(
                                    onClick = { vm.startService() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text("启动服务", color = BluePrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = { nav.navigate("webadmin") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                            ) { Text("打开后台") }
                        }
                    }
                }
            }
        }

        // 最近使用
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("已挂载存储", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Text("全部 ›", fontSize = 12.sp, color = TextGray)
            }
        }

        if (storages.isNotEmpty()) {
            items(storages) { s -> StorageRow(s) }
        } else {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = TextGray)
                        Spacer(Modifier.height(8.dp))
                        Text("还没有挂载存储源", fontSize = 14.sp, color = TextMain)
                        Text(
                            "打开「文件」页添加网盘挂载",
                            fontSize = 12.sp,
                            color = TextGray,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageRow(s: OpenListApi.StorageInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFEAF2FF), Color(0xFFDBE9FF)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = BluePrimary)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(s.mountPath, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
                Text("${s.driver} · ${s.remark.ifBlank { "无备注" }}", fontSize = 12.sp, color = TextGray)
            }
            Text(
                if (s.enabled) "启用" else "停用",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (s.enabled) Color(0xFF16B26A) else TextGray,
            )
        }
    }
}
