package com.pocketnas.pro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnas.pro.core.NasState
import com.pocketnas.pro.core.OpenListApi
import com.pocketnas.pro.ui.AppViewModel

private val Orange = Color(0xFFFB7A3D)
private val OrangeLight = Color(0xFFFF9A5A)
private val Green = Color(0xFF34C759)
private val Gray = Color(0xFF8E8E93)
private val GrayBg = Color(0xFFF2F2F7)
private val TextMain = Color(0xFF1C1C1E)

@Composable
fun DashboardScreen(
    vm: AppViewModel,
    onOpenLogs: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenWebAdmin: () -> Unit = {},
) {
    val status by vm.status.collectAsState()
    val running by NasState.running.collectAsState()
    val error by NasState.lastError.collectAsState()
    val storages by vm.storages.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshStatus()
            vm.refreshStorages()
            kotlinx.coroutines.delay(5000)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // 顶部橙色渐变标题栏
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Orange, OrangeLight)))
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Column {
                    Text("简云plas", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text("手机终端服务器 · 网盘 · 一键启动", fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
                }
            }
        }

        // 服务总览卡片
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        OverviewItem(
                            num = if (running) "1/1" else "0/1",
                            label = "运行服务",
                            color = if (running) Green else Gray,
                        )
                        OverviewItem(num = "5244", label = "网盘端口", color = TextMain)
                        OverviewItem(
                            num = vm.lanIp ?: "未连接",
                            label = "本机地址",
                            color = TextMain,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { vm.startService() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Orange),
                        ) { Text("⚡ 一键启动", fontWeight = FontWeight.Medium) }
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(
                            onClick = { vm.stopService() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Gray),
                        ) { Text("⏹ 一键停止", fontWeight = FontWeight.Medium) }
                    }
                }
            }
        }

        // OpenList 网盘大卡片
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (running) Green else Color(0xFFC7C7CC))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("☁️ OpenList 网盘", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = running,
                            onCheckedChange = { if (it) vm.startService() else vm.stopService() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Green),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "端口 5244 · ${vm.lanIp?.let { "http://$it:5244" } ?: "等待网络"}",
                        fontSize = 13.sp,
                        color = Gray,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onOpenWebAdmin,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Orange),
                        ) { Text("打开后台", fontSize = 13.sp) }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onOpenFiles,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("挂载管理", fontSize = 13.sp) }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onOpenLogs,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("日志", fontSize = 13.sp) }
                    }
                }
            }
        }

        // 快捷功能标题
        item {
            Text(
                "快捷功能",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMain,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 10.dp),
            )
        }

        // 快捷功能宫格
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                QuickItem("📁", "文件管理", Color(0xFFFFF3E8)) { onOpenFiles() }
                QuickItem("🌐", "Web 面板", Color(0xFFE8F0FF)) { onOpenWebAdmin() }
                QuickItem("📋", "运行日志", Color(0xFFE8F8ED)) { onOpenLogs() }
                QuickItem("⚙️", "系统设置", Color(0xFFF3E8FF)) { }
            }
        }

        // 已挂载存储
        if (storages.isNotEmpty()) {
            item {
                Text(
                    "已挂载存储（${storages.size}）",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMain,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 10.dp),
                )
            }
            items(storages.take(6)) { s -> StorageRow(s) }
        }

        error?.let {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE8E8)),
                ) {
                    Text(it, fontSize = 13.sp, color = Color(0xFFC62828), modifier = Modifier.padding(14.dp))
                }
            }
        }
    }
}

@Composable
private fun OverviewItem(num: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(num, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, color = Gray)
    }
}

@Composable
private fun QuickItem(icon: String, label: String, bg: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, fontSize = 24.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, color = TextMain)
    }
}

@Composable
private fun StorageRow(s: OpenListApi.StorageInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Storage, contentDescription = null, tint = Orange)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(s.mountPath, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextMain)
                Text("${s.driver} · ${s.remark.ifBlank { "无备注" }}", fontSize = 12.sp, color = Gray)
            }
            Text(
                if (s.enabled) "启用" else "停用",
                color = if (s.enabled) Green else Color(0xFFC7C7CC),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
