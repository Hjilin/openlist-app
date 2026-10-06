package com.pocketnas.pro.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pocketnas.pro.core.LogStore
import java.io.File
import kotlinx.coroutines.launch

/**
 * 日志查看：从持久化文件读取（含轮转文件），支持刷新 / 清空 / 导出分享。
 *
 * 存储位置（文件管理器可直接访问）：
 * /storage/emulated/0/Android/data/com.pocketnas.pro/files/logs/pocketnas.log(.1/.2/.3)
 */
@Composable
fun LogScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var pathMsg by remember { mutableStateOf("") }

    suspend fun reload() {
        loading = true
        logText = LogStore.readAll(context, maxLines = 1000)
        val f = LogStore.logFile(context)
        pathMsg = "${f.parentFile?.absolutePath}（${formatBytes(LogStore.totalBytes(context))}）"
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("日志记录", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { scope.launch { reload() } }) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
            }
            IconButton(
                onClick = {
                    shareLog(context)
                },
            ) {
                Icon(Icons.Default.Share, contentDescription = "导出/分享日志")
            }
            IconButton(
                onClick = {
                    LogStore.clear(context)
                    logText = ""
                    pathMsg = "日志已清空"
                },
            ) {
                Icon(Icons.Default.Delete, contentDescription = "清空日志")
            }
        }
        Text(
            pathMsg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101318))
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            when {
                loading -> Text(
                    "加载中…",
                    color = Color(0xFF888888),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                logText.isBlank() -> Text(
                    "暂无日志。启动内核服务并执行操作后，这里会显示详细记录。",
                    color = Color(0xFF888888),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                else -> Text(
                    text = logText,
                    color = Color(0xFFE0E0E0),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "最近 1000 行（含轮转文件），文件在 512KB 时自动轮转保留 4 份",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { scope.launch { reload() } }) { Text("刷新") }
        }
    }
}

private fun formatBytes(n: Long): String = when {
    n >= 1024 * 1024 -> "%.1f MB".format(n / 1024.0 / 1024.0)
    n >= 1024 -> "%.1f KB".format(n / 1024.0)
    else -> "$n B"
}

/** 导出分享：FileProvider 走系统分享（微信/网盘/邮件等） */
private fun shareLog(context: android.content.Context) {
    try {
        val dir = LogStore.logDir(context)
        // 导出到共享目录，确保 FileProvider 可访问
        val exportDir = File(context.getExternalFilesDir(null), "logs_export").apply { mkdirs() }
        val target = File(exportDir, "pocketnas.log")
        target.writeText(LogStore.readAll(context, maxLines = 5000))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PocketNAS Pro 日志")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "导出日志"))
    } catch (_: Exception) {}
}
