package com.pocketnas.pro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pocketnas.pro.core.AppSettingStore

@Composable
fun LoginScreen(
    onLogin: (String, String) -> Unit,
    error: String?,
) {
    val context = LocalContext.current
    val saved = remember { AppSettingStore.getSavedLogin(context) }
    var username by remember { mutableStateOf(saved.first) }
    var password by remember { mutableStateOf(saved.second) }
    var rememberLogin by remember { mutableStateOf(AppSettingStore.isLoginRemember(context)) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Openlist",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "登录以管理你的私有云",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberLogin,
                        onCheckedChange = {
                            rememberLogin = it
                            AppSettingStore.setLoginRemember(context, it)
                        },
                    )
                    Text(
                        "记住账号密码",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        if (rememberLogin) {
                            AppSettingStore.saveLogin(context, username.trim(), password)
                        } else {
                            AppSettingStore.clearSavedLogin(context)
                        }
                        onLogin(username, password)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("登录")
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "首次使用：账号 admin，密码 admin123456（登录后请在用户页修改）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "通信走本地安全通道，不经过网页",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}
