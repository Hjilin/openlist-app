package com.pocketnas.pro.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pocketnas.pro.core.AppSettingStore
import com.pocketnas.pro.core.RemoteAccess
import com.pocketnas.pro.ui.AppViewModel

/**
 * 网络与共享子页：FTP / SFTP / WebDAV（内核原生）+ N2N（Hin2n 适配，APP 层）。
 * SMB 服务端内核无原生支持，以 WebDAV 替代（Windows/主流文件管理器原生可挂载）。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NetworkShareSubScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val ftpEnabled by vm.ftpEnabled.collectAsState()
    val sftpEnabled by vm.sftpEnabled.collectAsState()
    val webdavEnabled by vm.webdavEnabled.collectAsState()

    var n2nConnected by remember { mutableStateOf(AppSettingStore.isN2nConnected(context)) }
    var supernode by remember { mutableStateOf(AppSettingStore.n2nSupernode(context)) }
    var community by remember { mutableStateOf(AppSettingStore.n2nCommunity(context)) }
    var secret by remember { mutableStateOf(AppSettingStore.n2nSecret(context)) }
    var virtualIp by remember { mutableStateOf(AppSettingStore.n2nVirtualIp(context)) }
    var copied by remember { mutableStateOf(false) }

    fun saveN2n() {
        AppSettingStore.setN2nSupernode(context, supernode)
        AppSettingStore.setN2nCommunity(context, community)
        AppSettingStore.setN2nSecret(context, secret)
        AppSettingStore.setN2nVirtualIp(context, virtualIp)
        AppSettingStore.setN2nConnected(context, n2nConnected)
    }

    fun saveN2nText() {
        val txt = "超级节点: $supernode\n社区名: $community\n密钥: $secret\n虚拟IP: $virtualIp"
        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clip?.setPrimaryClip(ClipData.newPlainText("n2n", txt))
        copied = true
    }

    val lan = vm.lanIp ?: "未连接"
    val baseIp = if (n2nConnected) virtualIp else lan

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("网络与共享") },
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
            // ---- FTP / SFTP ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("局域网服务", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        title = "FTP 服务",
                        desc = "端口 ${RemoteAccess.FTP_PORT}，账号为管理员账号",
                        checked = ftpEnabled,
                        onCheckedChange = { vm.setFtpEnabled(it) },
                    )
                    Spacer(Modifier.height(4.dp))
                    SwitchRow(
                        title = "SFTP 服务",
                        desc = "端口 ${RemoteAccess.SFTP_PORT}（SSH 文件传输）",
                        checked = sftpEnabled,
                        onCheckedChange = { vm.setSftpEnabled(it) },
                    )
                    Spacer(Modifier.height(4.dp))
                    SwitchRow(
                        title = "WebDAV 服务",
                        desc = "端口 ${RemoteAccess.WEBDAV_PORT}，路径 /dav，Windows/手机文件管理器可直接挂载",
                        checked = webdavEnabled,
                        onCheckedChange = { vm.setWebdavEnabled(it) },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "SMB 服务端内核无原生支持，可用 WebDAV 替代（Windows 映射网络驱动器、主流文件管理器均原生支持 WebDAV 挂载）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- N2N ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("N2N 虚拟组网（Hin2n）", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (n2nConnected) "已连接 · 远程地址使用虚拟 IP" else "未连接 · 隧道由 Hin2n 维护",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (n2nConnected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = n2nConnected,
                            onCheckedChange = { n2nConnected = it; saveN2n() },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = supernode,
                        onValueChange = { supernode = it },
                        label = { Text("超级节点") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = community,
                        onValueChange = { community = it },
                        label = { Text("社区名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it },
                        label = { Text("加密密钥") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = virtualIp,
                        onValueChange = { virtualIp = it },
                        label = { Text("本机虚拟 IP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { saveN2n(); saveN2nText() }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.height(16.dp))
                            Spacer(Modifier.padding(2.dp))
                            Text(if (copied) "已复制" else "复制配置")
                        }
                        TextButton(onClick = {
                            saveN2n()
                            // 尝试打开 Hin2n；未安装则跳转商店（演示）
                            val pm = context.packageManager
                            val intent = pm.getLaunchIntentForPackage("com.verge.android")
                            if (intent != null) {
                                context.startActivity(intent)
                            } else {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("market://search?q=Hin2n"),
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) {}
                            }
                        }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.height(16.dp))
                            Spacer(Modifier.padding(2.dp))
                            Text("打开 Hin2n")
                        }
                    }
                }
            }

            // ---- 当前访问地址 ----
            if (ftpEnabled || sftpEnabled || webdavEnabled) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            if (n2nConnected) "远程访问地址（N2N 虚拟网络）" else "远程访问地址（局域网）",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (ftpEnabled) {
                            AddressLine(
                                text = "ftp://$baseIp:${RemoteAccess.FTP_PORT}",
                                onCopy = {
                                    copyText(context, "ftp://$baseIp:${RemoteAccess.FTP_PORT}")
                                },
                            )
                        }
                        if (sftpEnabled) {
                            AddressLine(
                                text = "sftp://$baseIp:${RemoteAccess.SFTP_PORT}",
                                onCopy = {
                                    copyText(context, "sftp://$baseIp:${RemoteAccess.SFTP_PORT}")
                                },
                            )
                        }
                        if (webdavEnabled) {
                            AddressLine(
                                text = "http://$baseIp:${RemoteAccess.WEBDAV_PORT}/dav",
                                onCopy = {
                                    copyText(context, "http://$baseIp:${RemoteAccess.WEBDAV_PORT}/dav")
                                },
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "密码即管理员账号密码（「用户」页可修改）；公网访问需自行端口转发 / 内网穿透。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
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

@Composable
private fun AddressLine(text: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCopy) { Text("复制") }
    }
}

private fun copyText(context: Context, text: String) {
    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clip?.setPrimaryClip(ClipData.newPlainText("addr", text))
}
