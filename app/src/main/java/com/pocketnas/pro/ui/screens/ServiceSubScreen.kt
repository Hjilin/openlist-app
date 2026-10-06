package com.pocketnas.pro.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.pocketnas.pro.core.AppSettingStore
import com.pocketnas.pro.service.KeepAliveWorker
import com.pocketnas.pro.core.RootUtil
import com.pocketnas.pro.service.GuardService
import com.pocketnas.pro.service.OpenListService
import com.pocketnas.pro.ui.AppViewModel

/** 服务与自启子页：内核托管、开机自启、崩溃重启、系统授权、保活预留位 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ServiceSubScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var bootStart by remember { mutableStateOf(vm.isBootStart()) }
    var autoRestart by remember { mutableStateOf(vm.isAutoRestart()) }
    var keepAliveWorker by remember { mutableStateOf(AppSettingStore.isKeepAliveWorker(context)) }
    var hasRoot by remember { mutableStateOf(RootUtil.hasRoot()) }
    var rootMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("服务与自启") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SwitchRow(
                        title = "开机自启",
                        desc = "设备开机后自动启动内核服务",
                        checked = bootStart,
                        onCheckedChange = {
                            bootStart = it
                            vm.setBootStart(it)
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        title = "崩溃自动重启",
                        desc = "内核进程异常退出后 3 秒自动拉起",
                        checked = autoRestart,
                        onCheckedChange = {
                            autoRestart = it
                            vm.setAutoRestart(it)
                        },
                    )
                }
            }

            // ---- 系统授权（原设置页功能，保留不降级）----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("系统授权", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "为保持后台稳定运行，建议关闭电池优化，并允许通知与全部文件访问。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { requestIgnoreBatteryOptimizations(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("关闭电池优化")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { requestAllFilesAccess(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("授权全部文件访问")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { requestNotificationPermission(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("允许通知")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("后台保活", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "三层保活：前台服务（高优先级通知）→ 独立进程守护 → WorkManager 周期任务（系统级调度，15 分钟一次）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        title = "周期保活任务",
                        desc = "WorkManager 每 15 分钟检查内核存活并自动拉起",
                        checked = keepAliveWorker,
                        onCheckedChange = {
                            keepAliveWorker = it
                            AppSettingStore.setKeepAliveWorker(context, it)
                            if (it) KeepAliveWorker.enqueue(context) else KeepAliveWorker.cancel(context)
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            try {
                                context.startForegroundService(
                                    Intent(context, OpenListService::class.java)
                                        .setAction(OpenListService.ACTION_START)
                                )
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("立即拉起内核")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (RootUtil.hasRoot()) {
                                val ok = RootUtil.applyDozeWhitelist(context)
                                rootMsg = if (ok) "已加入系统 Doze 白名单" else "白名单命令执行失败"
                            } else {
                                rootMsg = "未检测到 Root 权限；非 Root 设备请手动关闭电池优化"
                            }
                            hasRoot = RootUtil.hasRoot()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (hasRoot) "Root：加入 Doze 白名单" else "Root 保活（未检测到权限）")
                    }
                    rootMsg?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "非 Root 建议同时开启：开机自启 + 关闭电池优化 + 允许通知（上方系统授权）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    val pm = context.getSystemService<PowerManager>() ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun requestAllFilesAccess(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = Uri.parse("package:${context.packageName}")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun requestNotificationPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            val intent = Intent(
                Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
