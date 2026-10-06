package com.pocketnas.pro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pocketnas.pro.core.AppSettingStore
import com.pocketnas.pro.ui.AppViewModel

/** 媒体库子页：缩略图缓存、排除目录、重新扫描 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MediaLibSubScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var cacheMb by remember { mutableStateOf(AppSettingStore.mediaThumbCacheMb(context).toString()) }
    var exclude by remember { mutableStateOf(AppSettingStore.mediaExcludeDirs(context)) }
    var saved by remember { mutableStateOf(false) }

    fun save() {
        val mb = cacheMb.toIntOrNull()?.coerceIn(8, 256) ?: 64
        AppSettingStore.setMediaThumbCacheMb(context, mb)
        AppSettingStore.setMediaExcludeDirs(context, exclude.trim())
        saved = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("媒体库") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        save()
                        vm.scanMedia()
                    }) { Text(if (saved) "已保存并扫描" else "保存并扫描") }
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
                    Text("缩略图缓存", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = cacheMb,
                        onValueChange = { cacheMb = it; saved = false },
                        label = { Text("缓存上限（MB，8–256）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("排除目录", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = exclude,
                        onValueChange = { exclude = it; saved = false },
                        label = { Text("目录名（逗号分隔，如: cache,tmp）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "媒体库会自动跳过：密码保护目录、隐藏目录、上级已排除目录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
