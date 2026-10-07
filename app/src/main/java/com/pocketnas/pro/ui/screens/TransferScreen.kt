package com.pocketnas.pro.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.pocketnas.pro.core.TransferEngine
import com.pocketnas.pro.ui.AppViewModel

private val TextMain = Color(0xFF1B2130)
private val TextGray = Color(0xFF9AA3B5)
private val BgLight = Color(0xFFF3F5F9)
private val BluePrimary = Color(0xFF3E6BFF)

private fun formatSize(size: Long): String = when {
    size >= 1024L * 1024 * 1024 -> "%.1f GB".format(size.toDouble() / (1024 * 1024 * 1024))
    size >= 1024L * 1024 -> "%.1f MB".format(size.toDouble() / (1024 * 1024))
    size >= 1024L -> "%.0f KB".format(size.toDouble() / 1024)
    else -> "$size B"
}

@Composable
fun TransferScreen(vm: AppViewModel, nav: NavHostController) {
    val transfers by vm.transfers.collectAsState()
    val running = transfers.filter {
        it.state == TransferEngine.State.Pending ||
            it.state == TransferEngine.State.Running ||
            it.state == TransferEngine.State.Paused
    }
    val done = transfers.filter {
        it.state == TransferEngine.State.Done || it.state == TransferEngine.State.Error
    }

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
            TextButton(onClick = { vm.pauseAllTransfers() }) {
                Text("全部暂停", fontSize = 12.sp, color = TextGray)
            }
        }

        if (running.isEmpty()) {
            Text(
                "暂无进行中的任务",
                fontSize = 13.sp,
                color = TextGray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        running.forEach { t ->
            TransferItem(
                name = t.name,
                status = when (t.state) {
                    TransferEngine.State.Running -> "进行中"
                    TransferEngine.State.Pending -> "排队中"
                    else -> "已暂停"
                },
                detail = "${formatSize(t.doneBytes)} / ${formatSize(t.totalSize)}",
                progress = t.progress(),
                done = false,
                onPause = { vm.pauseTransfer(t.id) },
                onResume = { vm.resumeTransfer(t.id) },
                onCancel = { vm.cancelTransfer(t.id) },
            )
        }

        // 已完成
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("已完成", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
            TextButton(onClick = { vm.clearDoneTransfers() }) {
                Text("清空", fontSize = 12.sp, color = TextGray)
            }
        }

        if (done.isEmpty()) {
            Text(
                "暂无已完成的任务",
                fontSize = 13.sp,
                color = TextGray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        done.forEach { t ->
            TransferItem(
                name = t.name,
                status = if (t.state == TransferEngine.State.Error) "失败" else "完成",
                detail = if (t.state == TransferEngine.State.Error) (t.error ?: "出错了") else formatSize(t.totalSize),
                progress = 1f,
                done = true,
            )
        }
    }
}

@Composable
private fun TransferItem(
    name: String,
    status: String,
    detail: String,
    progress: Float,
    done: Boolean,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(detail, fontSize = 11.sp, color = TextGray)
                Row {
                    if (!done) {
                        if (onResume != null && onPause != null) {
                            TextButton(onClick = onResume) { Text("继续", fontSize = 11.sp, color = BluePrimary) }
                        }
                        if (onPause != null) {
                            TextButton(onClick = onPause) { Text("暂停", fontSize = 11.sp, color = BluePrimary) }
                        }
                        if (onCancel != null) {
                            TextButton(onClick = onCancel) { Text("取消", fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    Text("${(progress * 100).toInt()}%", fontSize = 11.sp, color = TextGray)
                }
            }
        }
    }
}
