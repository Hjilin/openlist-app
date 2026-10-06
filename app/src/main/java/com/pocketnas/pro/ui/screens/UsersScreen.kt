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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pocketnas.pro.core.OpenListApi
import com.pocketnas.pro.ui.AppViewModel

/**
 * 用户管理：原生界面重置任意用户密码（含 admin）。
 * 这是根治"网页/命令行重置密码失效"问题的原生实现。
 */
@Composable
fun UsersScreen(vm: AppViewModel) {
    val users by vm.users.collectAsState()
    var target by remember { mutableStateOf<OpenListApi.UserInfo?>(null) }

    LaunchedEffect(Unit) { vm.refreshUsers() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("用户管理", style = MaterialTheme.typography.titleLarge)
            Text(
                "点击用户可重置密码",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        items(users) { u ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(u.username, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            buildString {
                                if (u.isAdmin) append("管理员 ")
                                if (u.isGuest) append("访客 ")
                                if (u.disabled) append("· 已禁用")
                            }.ifBlank { "普通用户" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { target = u }) {
                        Text("重置密码")
                    }
                }
            }
        }
    }

    target?.let { user ->
        var pwd by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        var mismatch by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text("重置 ${user.username} 的密码") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pwd,
                        onValueChange = {
                            pwd = it
                            mismatch = false
                        },
                        label = { Text("新密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = {
                            confirm = it
                            mismatch = false
                        },
                        label = { Text("确认新密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (mismatch) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "两次输入不一致",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pwd.isBlank() || pwd != confirm) {
                        mismatch = true
                        return@TextButton
                    }
                    vm.changePassword(user.id, pwd)
                    target = null
                }) {
                    Text("确认重置")
                }
            },
            dismissButton = {
                TextButton(onClick = { target = null }) {
                    Text("取消")
                }
            },
        )
    }
}
