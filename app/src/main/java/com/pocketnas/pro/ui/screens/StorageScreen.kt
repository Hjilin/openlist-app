package com.pocketnas.pro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocketnas.pro.core.OpenListApi
import com.pocketnas.pro.ui.AppViewModel

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun StorageScreen(
    vm: AppViewModel,
    onAdd: () -> Unit,
    onBack: () -> Unit = {},
) {
    val storages by vm.storages.collectAsState()
    var pendingDelete by remember { mutableStateOf<OpenListApi.StorageInfo?>(null) }

    LaunchedEffect(Unit) { vm.refreshStorages() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("存储源管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = "添加")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("存储源（${storages.size}）", style = MaterialTheme.typography.titleLarge)
                        Text("已挂载的网盘与本地目录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (storages.isEmpty()) {
                item {
                    Text("暂无存储源，点右上角 + 添加网盘或本地目录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(storages) { s ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cloud, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s.mountPath, style = MaterialTheme.typography.bodyLarge)
                            Text("${s.driver} · ${s.remark.ifBlank { "无备注" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (s.enabled) "启用" else "停用", color = if (s.enabled) Color(0xFF2E7D32) else Color(0xFFC62828), style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { pendingDelete = s }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除存储") },
            text = { Text("确认删除挂载 ${s.mountPath}（${s.driver}）？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteStorage(s.id); pendingDelete = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}
