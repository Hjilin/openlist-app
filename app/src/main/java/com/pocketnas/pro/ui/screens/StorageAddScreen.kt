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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pocketnas.pro.core.OpenListApi
import com.pocketnas.pro.ui.AppViewModel
import org.json.JSONObject

/**
 * 添加存储源：选择驱动 → 根据驱动字段模板动态渲染表单 → 提交创建。
 * 全部字段来自内核 GET /api/admin/driver/info，与内核驱动保持同步。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAddScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val drivers by vm.drivers.collectAsState()
    val driverInfo by vm.driverInfo.collectAsState()
    val notice by vm.notice.collectAsState()

    var selectedDriver by remember { mutableStateOf<String?>(null) }
    var driverMenuExpanded by remember { mutableStateOf(false) }
    // 字段值：字段名 -> 字符串值
    val values = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(Unit) { vm.loadDrivers() }

    LaunchedEffect(selectedDriver) {
        if (selectedDriver != null) {
            vm.loadDriverInfo(selectedDriver!!)
        }
    }

    // 驱动信息加载完后自动填充默认值
    LaunchedEffect(driverInfo) {
        val info = driverInfo ?: return@LaunchedEffect
        val defaults = mapOf(
            "mount_path" to "/",
            "order" to "0",
            "thumbnail" to "true",
            "directory_size" to "false",
            "webdav_policy" to "302 redirect to upstream URL",
            "web_proxy" to "false",
            "down_proxy_url" to "",
            "disable_proxy_sign" to "false",
        )
        info.common.forEach { f ->
            if (values[f.name].isNullOrBlank()) {
                values[f.name] = defaults[f.name] ?: f.default
            }
        }
        info.additional.forEach { f ->
            if (values[f.name].isNullOrBlank()) {
                // select 字段默认选第一个选项
                values[f.name] = when {
                    f.type == "select" && f.options.isNotEmpty() && f.default.isBlank() -> f.options.first()
                    f.type == "bool" && f.default.isBlank() -> "false"
                    else -> f.default
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("添加存储源") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- 驱动选择 ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("驱动", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = driverMenuExpanded,
                        onExpandedChange = { driverMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedDriver ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("选择网盘驱动") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = driverMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = driverMenuExpanded,
                            onDismissRequest = { driverMenuExpanded = false },
                        ) {
                            drivers.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        selectedDriver = name
                                        driverMenuExpanded = false
                                        values.clear()
                                    },
                                )
                            }
                        }
                    }
                    if (drivers.isEmpty()) {
                        Text(
                            "驱动列表为空（内核未运行或接口异常）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // ---- 动态表单 ----
            val info = driverInfo
            if (selectedDriver != null && info == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            if (info != null) {
                if (info.alert.isNotBlank()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            info.alert,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("公共设置", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        info.common.forEach { field ->
                            FieldInput(
                                field = field,
                                value = values[field.name] ?: field.default,
                                onValueChange = { values[field.name] = it },
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }

                if (info.additional.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("驱动参数", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            info.additional.forEach { field ->
                                FieldInput(
                                    field = field,
                                    value = values[field.name] ?: field.default,
                                    onValueChange = { values[field.name] = it },
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        val common = JSONObject()
                        info.common.forEach { f ->
                            val v = values[f.name] ?: f.default
                            if (f.required && v.isBlank()) return@Button
                            putTyped(common, f.name, f.type, v)
                        }
                        val addition = JSONObject()
                        info.additional.forEach { f ->
                            val v = values[f.name] ?: f.default
                            putTyped(addition, f.name, f.type, v)
                        }
                        vm.createStorage(
                            driver = info.name,
                            commonJson = common.toString(),
                            additionJson = addition.toString(),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("保存存储源")
                }
                if (notice != null) {
                    Text(
                        notice!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (notice!!.contains("成功")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun putTyped(json: JSONObject, key: String, type: String, value: String) {
    if (value.isEmpty()) return
    when (type) {
        "number" -> json.put(key, value.toLongOrNull() ?: value.toDoubleOrNull() ?: 0)
        "bool" -> json.put(key, value == "true")
        else -> json.put(key, value)
    }
}


// ============ 字段名/帮助 汉化映射 ============
private val fieldNameZh = mapOf(
    "mount_path" to "挂载路径",
    "order" to "排序",
    "remark" to "备注",
    "webdav_strategy" to "WebDAV 策略",
    "native_proxy" to "原生代理",
    "down_proxy_url" to "下载代理地址",
    "disable_proxy_sign" to "禁用下载签名",
    "directory_size" to "目录大小统计",
    "thumbnail" to "缩略图",
    "pdf_thumbnail" to "PDF 缩略图",
    "thumb_cache_folder" to "缩略图缓存目录",
    "thumb_concurrency" to "缩略图并发数",
    "video_thumb_pos" to "视频缩略图位置",
    "webdav_policy" to "WebDAV 策略",
    "web_proxy" to "网络代理",
    "webdav" to "WebDAV",
    "username" to "用户名",
    "password" to "密码",
    "url" to "服务器地址",
    "root_folder_path" to "根目录路径",
    "share_dir" to "共享目录",
    "access_token" to "访问令牌",
    "refresh_token" to "刷新令牌",
    "drive_id" to "网盘 ID",
    "driver" to "驱动",
    "additional" to "驱动参数",
    "common" to "公共设置",
)

private val helpZh = mapOf(
    "The path you want to mount to, it is unique and cannot be repeated" to "要挂载到的路径，唯一且不能重复",
    "use to sort" to "用于排序",
    "Disable sign for Download proxy URL" to "下载代理地址禁用签名",
    "This might impact host performance" to "可能影响服务器性能",
    "enable thumbnail" to "开启缩略图",
    "Generate PDF first-page thumbnails with Quick Look on macOS" to "在 macOS 上用 Quick Look 生成 PDF 首页缩略图",
    "Number of concurrent thumbnail generation goroutines. This controls how many thumbnails can be generated in parallel." to "缩略图生成的并发数，控制并行生成缩略图的数量",
    "The position of the video thumbnail. If the value is a number (integer or floating point), it represents the time in seconds. If the value ends with '%'" to "视频缩略图位置。填数字表示秒数，填百分比表示占比",
)

private fun zhName(name: String): String = fieldNameZh[name] ?: name
private fun zhHelp(help: String): String = helpZh[help] ?: help

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldInput(
    field: OpenListApi.DriverField,
    value: String,
    onValueChange: (String) -> Unit,
) {
    when (field.type) {
        "bool" -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(zhName(field.name), style = MaterialTheme.typography.bodyLarge)
                    if (field.help.isNotBlank()) {
                        Text(
                            field.help,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = value == "true",
                    onCheckedChange = { onValueChange(it.toString()) },
                )
            }
        }
        "select" -> {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(zhName(field.name)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    field.options.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt) },
                            onClick = {
                                onValueChange(opt)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
        else -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = {
                    Text(if (field.required) "${zhName(field.name)} *" else zhName(field.name))
                },
                singleLine = true,
                supportingText = field.help.takeIf { it.isNotBlank() }?.let { { Text(zhHelp(it)) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (field.type == "number") KeyboardType.Number
                    else KeyboardType.Text,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
