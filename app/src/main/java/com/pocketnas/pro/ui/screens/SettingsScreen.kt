package com.pocketnas.pro.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pocketnas.pro.R
import com.pocketnas.pro.core.AppSettingStore
import com.pocketnas.pro.ui.AppViewModel

/** 子页面路由 */
private enum class SubPage { Network, Service, Security, MediaLib }

/** 分组定义：后期新增功能只需在此追加条目，主页面主体零改动 */
private data class SettingGroup(
    val title: String,
    val items: List<SettingEntry>,
)

private data class SettingEntry(
    val title: String,
    val desc: String,
)

/**
 * 设置中心（数据驱动分组 + 子页隔离）。
 *
 * 约定（后期改动少）：
 * 1. 新增大块功能 → 新建 sub 子页面，主页面只加一行 [SettingEntry] 跳转；
 * 2. 所有配置读写走 [AppSettingStore]；
 * 3. 一个子页面对应一个职责，不把业务逻辑堆在主页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onOpenWebAdmin: () -> Unit = {}, onOpenStorages: () -> Unit = {}) {
    val context = LocalContext.current
    var sub by remember { mutableStateOf<SubPage?>(null) }
    var showLangDialog by remember { mutableStateOf(false) }
    val currentLang = com.pocketnas.pro.core.AppSettingStore.getLang(context)

    sub?.let { page ->
        when (page) {
            SubPage.Network -> NetworkShareSubScreen(vm, onBack = { sub = null })
            SubPage.Service -> ServiceSubScreen(vm, onBack = { sub = null })
            SubPage.Security -> SecuritySubScreen(onBack = { sub = null })
            SubPage.MediaLib -> MediaLibSubScreen(vm, onBack = { sub = null })
        }
        return
    }

    val groups = remember {
        listOf(
            SettingGroup(
                "网络与共享",
                listOf(
                    SettingEntry("FTP / SFTP 服务", "局域网访问，账号为管理员"),
                    SettingEntry("N2N 虚拟组网（Hin2n）", "公网穿透，远程访问"),
                ),
            ),
            SettingGroup(
                "服务与自启",
                listOf(
                    SettingEntry("内核服务与开机自启", "开机自启 / 崩溃自动重启"),
                ),
            ),
            SettingGroup(
                "安全与密码",
                listOf(
                    SettingEntry("文件密码保护 / 管理员密码", "APP 本地安全层"),
                ),
            ),
            SettingGroup(
                "媒体库",
                listOf(
                    SettingEntry("缩略图缓存与排除目录", "相册视图参数"),
                ),
            ),
            SettingGroup(
                "存储",
                listOf(
                    SettingEntry("存储源管理", "挂载网盘 / 本地目录"),
                ),
            ),
            SettingGroup(
                "通用",
                listOf(
                    SettingEntry("网页管理后台", "OpenList 原生 Web 管理界面"),
                    SettingEntry("语言 / Language", "中文 / English / 跟随系统"),
                ),
            ),
            SettingGroup(
                "数据管理",
                listOf(
                    SettingEntry("导出配置 / 导入配置", "备份与迁移全部设置"),
                ),
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.titleLarge)

        // ---- 设备信息卡片 ----
        DeviceInfoCard(vm)

        // ---- 数据驱动分组 ----
        groups.forEach { group ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    group.items.forEach { item ->
                        GroupEntryRow(
                            title = item.title,
                            desc = item.desc,
                            onClick = {
                                when (group.title) {
                                    "网络与共享" -> sub = SubPage.Network
                                    "服务与自启" -> sub = SubPage.Service
                                    "安全与密码" -> sub = SubPage.Security
                                    "媒体库" -> sub = SubPage.MediaLib
                                    "网页管理后台" -> onOpenWebAdmin()
                                    "存储源管理" -> onOpenStorages()
                                    "通用" -> showLangDialog = true
                                }
                            },
                        )
                    }
                }
            }
        }

        // ---- 语言选择对话框 ----
        if (showLangDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showLangDialog = false },
                title = { Text("选择语言 / Language") },
                text = {
                    Column {
                        listOf("system" to "跟随系统", "zh" to "中文", "en" to "English").forEach { (code, label) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    com.pocketnas.pro.core.AppSettingStore.setLang(context, code)
                                    showLangDialog = false
                                    // 重建 Activity 应用新语言
                                    (context as? androidx.activity.ComponentActivity)?.recreate()
                                }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                if (currentLang == code) {
                                    Text("✓", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
            )
        }

        // ---- 关于 ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("关于", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("PocketNAS Pro", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "版本 ${context.getString(R.string.app_version)} · 内核 OpenList 定制版",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "无 WebView · 无网页后台 · 本地 Unix Socket 通信",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 设备信息卡：版本 / 局域网 IP / N2N 状态 + 导入导出 */
@Composable
private fun DeviceInfoCard(vm: AppViewModel) {
    val context = LocalContext.current
    val n2nConnected = remember { AppSettingStore.isN2nConnected(context) }
    val n2nIp = remember { AppSettingStore.n2nVirtualIp(context) }
    var toast by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val json = AppSettingStore.exportJson(context)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            }
            toast = "配置已导出"
        } catch (_: Exception) {
            toast = "导出失败"
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val json = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return@rememberLauncherForActivityResult
            val n = AppSettingStore.importJson(context, json)
            toast = "已恢复 $n 项设置（部分需重启内核生效）"
        } catch (_: Exception) {
            toast = "导入失败：格式不正确"
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("设备", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            InfoLine("版本", context.getString(R.string.app_version))
            InfoLine("局域网 IP", vm.lanIp ?: "未连接")
            InfoLine("N2N 虚拟 IP", if (n2nConnected) n2nIp else "未连接")
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { exportLauncher.launch("pocketnas_settings.json") }) {
                    Text("导出配置")
                }
                TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) {
                    Text("导入配置")
                }
            }
            toast?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun GroupEntryRow(title: String, desc: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
