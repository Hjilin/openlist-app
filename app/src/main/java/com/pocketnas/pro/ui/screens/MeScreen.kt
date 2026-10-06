package com.pocketnas.pro.ui.screens
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.clickable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.pocketnas.pro.core.NasState
import com.pocketnas.pro.core.OpenListApi
import com.pocketnas.pro.ui.AppViewModel

private val TextMain = Color(0xFF1B2130)
private val TextGray = Color(0xFF9AA3B5)
private val BgLight = Color(0xFFF3F5F9)
private val BluePrimary = Color(0xFF2F6BFF)
private val BluePurple = Color(0xFF6A5CFF)

@Composable
fun MeScreen(vm: AppViewModel, nav: NavHostController) {
    val running by NasState.running.collectAsState()
    val storages by vm.storages.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgLight)
            .verticalScroll(rememberScrollState()),
    ) {
        // 用户信息卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(BluePrimary, BluePurple))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("A", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("admin", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextMain)
                    Spacer(Modifier.height(3.dp))
                    Text("自用网盘 · 已连接 ${storages.size} 个挂载源", fontSize = 12.sp, color = TextGray)
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFFFE9B0),
                ) {
                    Text(
                        "本地版",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB8860B),
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                    )
                }
            }
        }

        // 存储管理组
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column {
                MeItem(Icons.Default.Folder, "存储管理", "${storages.size} 个挂载 ›") {
                    nav.navigate("storages")
                }
                MeItem(Icons.Default.People, "用户与权限", "3 个账号 ›") {}
                MeItem(Icons.Default.Storage, "存储用量", "4.82 TB ›") {}
            }
        }

        Spacer(Modifier.height(12.dp))

        // 系统设置组
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column {
                MeItem(Icons.Default.Settings, "系统设置", "›") {
                    nav.navigate("settings")
                }
                MeItem(Icons.Default.Palette, "外观主题", "123 风格 ›") {}
                MeItem(Icons.Default.Security, "账号安全", "›") {}
                MeItem(Icons.Default.Lan, "WebDAV 服务", if (running) "已开启 ›" else "未开启 ›") {}
            }
        }

        Spacer(Modifier.height(12.dp))

        // 关于组
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column {
                MeItem(Icons.Default.Info, "关于简云plas", "v1.0.0 ›") {}
                MeItem(Icons.Default.Refresh, "检查更新", "›") {}
                MeItem(Icons.Default.ExitToApp, "打开网页后台", "›") {
                    nav.navigate("webadmin")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MeItem(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF1F5FD)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(13.dp))
        Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 12.5.sp, color = TextGray)
    }
}
