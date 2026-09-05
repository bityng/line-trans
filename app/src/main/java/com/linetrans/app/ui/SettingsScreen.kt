package com.linetrans.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.linetrans.app.data.SettingsRepository
import com.linetrans.app.data.StorageManager
import com.linetrans.app.model.BillingConfig
import com.linetrans.app.model.ModelConfig
import com.linetrans.app.model.ProviderConfig
import com.linetrans.app.model.ProviderType
import com.linetrans.app.server.WebTerminalService
import com.linetrans.app.util.NetworkUtils
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = SettingsRepository.settings

    var showAddProvider by remember { mutableStateOf(false) }
    var showAddModel by remember { mutableStateOf(false) }
    var showFolder by remember { mutableStateOf(false) }
    var portText by remember { mutableStateOf(settings.webServerPort.toString()) }
    var dailyGoalText by remember { mutableStateOf(settings.dailyGoal.toString()) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            enableWebTerminal(context)
        } else {
            Toast.makeText(context, "未授予通知权限，可能无法显示服务通知", Toast.LENGTH_LONG).show()
            enableWebTerminal(context)
        }
    }

    fun requestStartWebTerminal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enableWebTerminal(context)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            SettingsRepository.update { it.copy(storageDirUri = uri.toString()) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionTitle("API 提供商")
                settings.providers.forEach { provider ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = settings.activeProviderId == provider.id,
                                    onClick = { SettingsRepository.update { it.copy(activeProviderId = provider.id) } }
                                )
                                Spacer(Modifier.width(4.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(provider.name, style = MaterialTheme.typography.titleSmall)
                                    Text(provider.type.name, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = {
                                    SettingsRepository.update { s ->
                                        s.providers.removeAll { it.id == provider.id }
                                        if (s.activeProviderId == provider.id) s.activeProviderId = ""
                                        s
                                    }
                                }) { Text("删除") }
                            }
                            if (settings.activeProviderId == provider.id) {
                                Spacer(Modifier.height(8.dp))
                                Text("模型", style = MaterialTheme.typography.labelMedium)
                                provider.models.forEach { model ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = settings.activeModelId == model.id,
                                            onClick = { SettingsRepository.update { it.copy(activeModelId = model.id) } }
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(model.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        Text(
                                            "输入" + model.billing.inputPrice + "/M 输出" + model.billing.outputPrice + "/M",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        TextButton(onClick = { showAddModel = true }) { Text("编辑") }
                                    }
                                }
                                TextButton(onClick = { showAddModel = true }) { Text("添加模型") }
                            }
                        }
                    }
                }
                Button(onClick = { showAddProvider = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("添加 API 提供商")
                }
            }

            item {
                SectionTitle("语言检测")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, null)
                            Spacer(Modifier.width(8.dp))
                            Text("自动检测语言", Modifier.weight(1f))
                            Switch(
                                checked = settings.detectLanguage,
                                onCheckedChange = { on -> SettingsRepository.update { it.copy(detectLanguage = on) } }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("源语言", Modifier.width(72.dp))
                            OutlinedTextField(
                                value = settings.sourceLang,
                                onValueChange = { v -> SettingsRepository.update { it.copy(sourceLang = v) } },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("目标语言", Modifier.width(72.dp))
                            OutlinedTextField(
                                value = settings.targetLang,
                                onValueChange = { v -> SettingsRepository.update { it.copy(targetLang = v) } },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                SectionTitle("今日目标")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("设置每日翻译目标（句）", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = dailyGoalText,
                                onValueChange = { dailyGoalText = it.filter { c -> c.isDigit() }.take(6) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                SettingsRepository.update { it.copy(dailyGoal = dailyGoalText.toIntOrNull() ?: 0) }
                                Toast.makeText(context, "已保存今日目标", Toast.LENGTH_SHORT).show()
                            }) { Text("保存") }
                        }
                    }
                }
            }

            item {
                SectionTitle("存储文件夹")
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp).fillMaxWidth().clickable { folderPicker.launch(null) },
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("数据存放文件夹")
                            Text(
                                if (settings.storageDirUri.isBlank()) "未设置，点击选择" else "已设置",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedButton(onClick = { folderPicker.launch(null) }) { Text("选择") }
                    }
                }
            }

            item {
                SectionTitle("Web 终端服务")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Computer, null)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("局域网 Web 终端")
                                Text("供同一局域网内设备访问", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = settings.webServerEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) requestStartWebTerminal() else stopWebTerminal(context)
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("端口", Modifier.width(64.dp))
                            OutlinedTextField(
                                value = portText,
                                onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                val p = portText.toIntOrNull() ?: 8080
                                SettingsRepository.update { it.copy(webServerPort = p.coerceIn(1024, 65535)) }
                                if (WebTerminalService.isRunning) {
                                    WebTerminalService.stop(context)
                                    WebTerminalService.start(context)
                                }
                            }) { Text("应用") }
                        }
                        if (WebTerminalService.isRunning || settings.webServerEnabled) {
                            val ip = NetworkUtils.getLocalIpAddress() ?: "127.0.0.1"
                            Text(
                                "访问地址：http://" + ip + ":" + settings.webServerPort + "/terminal",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                SectionTitle("计费说明")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Icon(Icons.Default.Payment, null)
                        Spacer(Modifier.height(6.dp))
                        Text("在模型设置中可配置输入/输出价格（每百万 token），以及高峰倍率与时段。费用会自动根据用量估算。")
                        Text("如果 API 返回 token 用量，输入（未命中）/输入（命中）/输出 会分别累计。")
                    }
                }
            }

            item {
                SectionTitle("应用信息")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("逐行翻译 v1.0")
                        Text("一款便于逐行/逐句对照翻译的安卓原生应用。")
                        Text("支持局域网 Web 终端服务，UI 参考 RikkaHub。")
                    }
                }
            }
        }
    }

    if (showAddProvider) {
        AddProviderDialog(onDismiss = { showAddProvider = false }) { provider ->
            SettingsRepository.update { s ->
                s.providers.add(provider)
                if (s.activeProviderId.isBlank()) s.activeProviderId = provider.id
                s
            }
            showAddProvider = false
        }
    }

    if (showAddModel && settings.activeProvider != null) {
        val activeProvider = settings.activeProvider!!
        AddModelDialog(
            onDismiss = { showAddModel = false },
            provider = activeProvider
        ) { model ->
            SettingsRepository.update { s ->
                val p = s.providers.firstOrNull { it.id == activeProvider.id }
                p?.models?.add(model)
                if (s.activeModelId.isBlank()) s.activeModelId = model.id
                s
            }
            showAddModel = false
        }
    }
}

private fun enableWebTerminal(context: Context) {
    SettingsRepository.update { it.copy(webServerEnabled = true) }
    WebTerminalService.start(context)
    Toast.makeText(context, "Web 终端服务已开启", Toast.LENGTH_SHORT).show()
}

private fun stopWebTerminal(context: Context) {
    WebTerminalService.stop(context)
    SettingsRepository.update { it.copy(webServerEnabled = false) }
    Toast.makeText(context, "Web 终端服务已关闭", Toast.LENGTH_SHORT).show()
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun AddProviderDialog(onDismiss: () -> Unit, onAdd: (ProviderConfig) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ProviderType.OPENAI_COMPAT) }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 API 提供商") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row {
                    ProviderType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name) })
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL，如 https://api.example.com") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                val id = UUID.randomUUID().toString()
                onAdd(ProviderConfig(id = id, name = name, type = type, baseUrl = baseUrl, apiKey = apiKey))
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AddModelDialog(provider: ProviderConfig, onDismiss: () -> Unit, onAdd: (ModelConfig) -> Unit) {
    var name by remember { mutableStateOf("") }
    var inputPrice by remember { mutableStateOf("") }
    var outputPrice by remember { mutableStateOf("") }
    var peakMultiplier by remember { mutableStateOf("1.0") }
    var peakStart by remember { mutableStateOf("8") }
    var peakEnd by remember { mutableStateOf("22") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加模型 / 编辑计费") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("模型名称") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = inputPrice, onValueChange = { inputPrice = it }, label = { Text("输入价格（每百万token）") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = outputPrice, onValueChange = { outputPrice = it }, label = { Text("输出价格（每百万token）") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = peakMultiplier, onValueChange = { peakMultiplier = it }, label = { Text("高峰倍率") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(value = peakStart, onValueChange = { peakStart = it }, label = { Text("高峰起始") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value = peakEnd, onValueChange = { peakEnd = it }, label = { Text("高峰结束") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val mid = UUID.randomUUID().toString()
                val billing = BillingConfig(
                    inputPrice = inputPrice.toDoubleOrNull() ?: 0.0,
                    outputPrice = outputPrice.toDoubleOrNull() ?: 0.0,
                    peakMultiplier = peakMultiplier.toDoubleOrNull() ?: 1.0,
                    peakStartHour = peakStart.toIntOrNull() ?: 8,
                    peakEndHour = peakEnd.toIntOrNull() ?: 22
                )
                onAdd(ModelConfig(id = mid, name = name, providerId = provider.id, billing = billing))
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
