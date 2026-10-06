package com.pocketnas.pro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.pocketnas.pro.ui.AppViewModel

private val TextMain = Color(0xFF1B2130)
private val TextGray = Color(0xFF9AA3B5)
private val BgLight = Color(0xFFF3F5F9)
private val BluePrimary = Color(0xFF2F6BFF)

@Composable
fun TransferScreen(vm: AppViewModel, nav: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgLight)
            .verticalScroll(rememberScrollState()),
    ) {
        // 进行中
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("进行中", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
            Text("全部暂停", fontSize = 12.sp, color = TextGray)
        }

        // 示例传输项
        TransferItem("OpenClaw-Termux 安装包.apk", "上传中", "5.1 MB/s · 剩余 1 分 12 秒", 0.72, false)
        TransferItem("红米K60 刷机资料", "离线下载", "2.4 MB/s · 剩余 12 分", 0.38, false)
        TransferItem("电影合集", "转存", "排队中", 0.12, false)

        // 已完成
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("已完成", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
            Text("清空", fontSize = 12.sp, color = TextGray)
        }

        TransferItem("备份_2026-10-01.zip", "完成", "1.2 GB · 用时 2 分 30 秒", 1f, true)
    }
}

@Composable
private fun TransferItem(name: String, status: String, detail: String, progress: Float, done: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextMain, modifier = Modifier.weight(1f))
                Text(
                    status,
                    fontSize = 12.sp,
                    color = if (done) Color(0xFF16B26A) else TextGray,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFEEF1F6)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (done) Color(0xFF16B26A) else BluePrimary),
                )
            }
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(detail, fontSize = 11.sp, color = TextGray)
                Text("${(progress * 100).toInt()}%", fontSize = 11.sp, color = TextGray)
            }
        }
    }
}
