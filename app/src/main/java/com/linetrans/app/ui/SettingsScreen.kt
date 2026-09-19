package com.linetrans.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.linetrans.app.BuildConfig
import com.linetrans.app.data.SettingsRepository
import com.linetrans.app.model.BillingConfig
import com.linetrans.app.model.ModelConfig
import com.linetrans.app.model.ProviderConfig
import com.linetrans.app.model.ProviderType
import com.linetrans.app.server.WebTerminalService
import com.linetrans.app.util.NetworkUtils
import java.util.UUID

private const val GITHUB_URL = "https://github.com/bityng/line-trans"

private val TARGET_LANGUAGES = listOf("zh-CN" to "中文", "en" to "English", "ja" to "日本語", "ko" to "한국어")
private val SOURCE_LANGUAGES = listOf("auto" to "自动", "en" to "English", "zh-CN" to "中文", "ja" to "日本語")

private val PROVIDER_PRESETS = listOf(
    "OpenAI" to "https://api.openai.com",
    "DeepSeek" to "https://api.deepseek.com",
    "Moonshot" to "https://api.moonshot.cn",
    "智谱 GLM" to "https://open.bigmodel.cn/api/paas",
    "Anthropic" to "https://api.anthropic.com"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = SettingsRepository.settings

    var addProvider by remember { mutableStateOf(false) }
    var editProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var deleteProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var addModelFor by remember { mutableStateOf<ProviderConfig?>(null) }
    var editModel by remember { mutableStateOf<ModelConfig?>(null) }
    var portText by remember { mutableStateOf(settings.webServerPort.toString()) }
    var goalText by remember { mutableStateOf(if (settings.dailyGoal > 0) settings.dailyGoal.toString() else "") }
    var promptText by remember { mutableStateOf(settings.customPrompt) }
    var contextText by remember { mutableStateOf(settings.contextUnits.toString()) }
    var sourceText by remember { mutableStateOf(settings.sourceLang) }
    var targetText by remember { mutableStateOf(settings.targetLang) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(context, "未授予通知权限，服务通知可能不显示", Toast.LENGTH_LONG).show()
        }
        enableWebTerminal(context)
    }

    fun requestStartWebTerminal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enableWebTerminal(context)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            SettingsRepository.update { it.copy(storageDirUri = uri.toString()) }
            Toast.makeText(context, "已设置数据文件夹", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionTitle("API 提供商")
                Spacer(Modifier.height(8.dp))
                if (settings.providers.isEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            "还没有配置 AI 服务，添加后即可使用 AI 翻译。",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                settings.providers.forEach { provider ->
                    val active = settings.activeProviderId == provider.id
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = active,
                                    onClick = {
                                        SettingsRepository.update { it.copy(activeProviderId = provider.id) }
                                    }
                                )
                                Spacer(Modifier.width(4.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(provider.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        provider.type.label + " · " + provider.baseUrl.ifBlank { "未填写地址" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (provider.apiKey.isBlank()) "未填写 API Key"
                                        else "API Key 已填写（" + provider.apiKey.take(4) + "…）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { editProvider = provider }) {
                                    Icon(Icons.Default.Edit, contentDescription = "编辑")
                                }
                                IconButton(onClick = { deleteProvider = provider }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            if (active) {
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                Text("模型", style = MaterialTheme.typography.labelLarge)
                                if (provider.models.isEmpty()) {
                                    Text(
                                        "还没有模型，点击下方按钮添加。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                provider.models.forEach { model ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = settings.activeModelId == model.id,
                                            onClick = {
                                                SettingsRepository.update { it.copy(activeModelId = model.id) }
                                            }
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(model.name, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                "输入 " + model.billing.inputPrice + " / 输出 " + model.billing.outputPrice + " 元每百万 token",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { editModel = model }) {
                                            Icon(Icons.Default.Edit, contentDescription = "编辑模型")
                                        }
                                        IconButton(onClick = {
                                            provider.models.remove(model)
                                            if (settings.activeModelId == model.id) {
                                                SettingsRepository.update { it.copy(activeModelId = provider.models.firstOrNull()?.id ?: "") }
                                            } else {
                                                SettingsRepository.save()
                                            }
                                        }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "删除模型",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                                TextButton(onClick = { addModelFor = provider }) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.width(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("添加模型")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Button(onClick = { addProvider = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加 API 提供商")
                }
            }

            item {
                SectionTitle("语言")
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.width(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("自动检测源语言")
                                Text(
                                    "根据原文自动判断语言，关闭后使用下面指定的源语言",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.detectLanguage,
                                onCheckedChange = { on -> SettingsRepository.update { it.copy(detectLanguage = on) } }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("源语言", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SOURCE_LANGUAGES.forEach { (code, label) ->
                                FilterChip(
                                    selected = sourceText == code,
                                    onClick = {
                                        sourceText = code
                                        SettingsRepository.update { it.copy(sourceLang = code) }
                                    },
                                    label = { Text(label) },
                                    enabled = !settings.detectLanguage
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("目标语言", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TARGET_LANGUAGES.forEach { (code, label) ->
                                FilterChip(
                                    selected = targetText == code,
                                    onClick = {
                                        targetText = code
                                        SettingsRepository.update { it.copy(targetLang = code) }
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = targetText,
                            onValueChange = {
                                targetText = it
                                SettingsRepository.update { s -> s.copy(targetLang = it) }
                            },
                            label = { Text("目标语言代码或名称（可自定义）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                SectionTitle("AI 翻译行为")
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("翻译后自动跳转")
                                Text(
                                    "AI 翻译完成后自动移动到下一句",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.autoAdvance,
                                onCheckedChange = { on -> SettingsRepository.update { it.copy(autoAdvance = on) } }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = contextText,
                            onValueChange = {
                                contextText = it.filter { c -> c.isDigit() }.take(2)
                                SettingsRepository.update { s ->
                                    s.copy(contextUnits = (contextText.toIntOrNull() ?: 0).coerceIn(0, 10))
                                }
                            },
                            label = { Text("前文参考条数（0-10）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = promptText,
                            onValueChange = { promptText = it },
                            label = { Text("附加提示词（术语、风格要求）") },
                            placeholder = { Text("例如：人名保留原文；使用简体中文书面语") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Button(onClick = {
                                SettingsRepository.update { it.copy(customPrompt = promptText) }
                                Toast.makeText(context, "已保存提示词", Toast.LENGTH_SHORT).show()
                            }) { Text("保存提示词") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = {
                                promptText = ""
                                SettingsRepository.update { it.copy(customPrompt = "") }
                            }) { Text("清空") }
                        }
                    }
                }
            }

            item {
                SectionTitle("今日目标")
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = goalText,
                                onValueChange = { goalText = it.filter { c -> c.isDigit() }.take(6) },
                                label = { Text("每日目标（句）") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                SettingsRepository.update { it.copy(dailyGoal = goalText.toIntOrNull() ?: 0) }
                                Toast.makeText(context, "已保存今日目标", Toast.LENGTH_SHORT).show()
                            }) { Text("保存") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "今日已完成 " + SettingsRepository.dailyCount() + " 句",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = {
                            SettingsRepository.resetDaily()
                            Toast.makeText(context, "已重置今日进度", Toast.LENGTH_SHORT).show()
                        }) { Text("重置今日进度") }
                    }
                }
            }

            item {
                SectionTitle("数据文件夹")
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { folderPicker.launch(null) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.width(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("导入译文与导出的存放位置")
                                Text(
                                    if (settings.storageDirUri.isBlank()) "未设置，点击选择" else "已设置",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(onClick = { folderPicker.launch(null) }) { Text("选择") }
                        }
                        if (settings.storageDirUri.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                Uri.decode(settings.storageDirUri),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = {
                                SettingsRepository.update { it.copy(storageDirUri = "") }
                            }) { Text("清除设置") }
                        }
                    }
                }
            }

            item {
                SectionTitle("Web 终端服务")
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.width(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("局域网 Web 终端")
                                Text(
                                    if (WebTerminalService.isRunning) "运行中" else "已停止",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (WebTerminalService.isRunning) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.webServerEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) requestStartWebTerminal() else stopWebTerminal(context)
                                }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = portText,
                                onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
                                label = { Text("端口") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                val p = (portText.toIntOrNull() ?: 8080).coerceIn(1024, 65535)
                                portText = p.toString()
                                SettingsRepository.update { it.copy(webServerPort = p) }
                                if (WebTerminalService.isRunning) {
                                    WebTerminalService.stop(context)
                                    WebTerminalService.start(context)
                                }
                                Toast.makeText(context, "端口已设为 $p", Toast.LENGTH_SHORT).show()
                            }) { Text("应用") }
                        }
                        if (settings.webServerEnabled || WebTerminalService.isRunning) {
                            val ip = NetworkUtils.getLocalIpAddress() ?: "127.0.0.1"
                            val url = "http://" + ip + ":" + settings.webServerPort + "/terminal"
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    url,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("url", url))
                                    Toast.makeText(context, "已复制访问地址", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "复制地址")
                                }
                            }
                            Text(
                                "同一局域网内的设备用浏览器打开该地址即可使用终端。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                SectionTitle("关于")
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.width(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("逐行翻译 v" + BuildConfig.VERSION_NAME)
                                Text(
                                    "逐行 / 逐句对照翻译，支持多家 AI 接口与局域网 Web 终端。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.width(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "费用按输入/输出价格（每百万 token）估算，可配置高峰倍率与时段。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                            }.onFailure {
                                Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.width(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("查看项目主页")
                        }
                    }
                }
            }
        }
    }

    if (addProvider || editProvider != null) {
        ProviderDialog(
            provider = editProvider,
            onDismiss = {
                addProvider = false
                editProvider = null
            },
            onConfirm = { name, type, baseUrl, apiKey ->
                val target = editProvider
                if (target != null) {
                    target.name = name
                    target.type = type
                    target.baseUrl = baseUrl
                    target.apiKey = apiKey
                    SettingsRepository.save()
                } else {
                    val id = UUID.randomUUID().toString()
                    SettingsRepository.update { s ->
                        s.providers.add(ProviderConfig(id, name, type, baseUrl, apiKey))
                        if (s.activeProviderId.isBlank()) s.activeProviderId = id
                        s
                    }
                }
                addProvider = false
                editProvider = null
            }
        )
    }

    if (deleteProvider != null) {
        val provider = deleteProvider!!
        AlertDialog(
            onDismissRequest = { deleteProvider = null },
            title = { Text("删除提供商") },
            text = { Text("确定删除「" + provider.name + "」及其下的模型配置吗？") },
            confirmButton = {
                TextButton(onClick = {
                    SettingsRepository.update { s ->
                        s.providers.removeAll { it.id == provider.id }
                        if (s.activeProviderId == provider.id) s.activeProviderId = s.providers.firstOrNull()?.id ?: ""
                        if (s.activeModel == null) s.activeModelId = s.activeProvider?.models?.firstOrNull()?.id ?: ""
                        s
                    }
                    deleteProvider = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteProvider = null }) { Text("取消") } }
        )
    }

    if (addModelFor != null || editModel != null) {
        ModelDialog(
            model = editModel,
            onDismiss = {
                addModelFor = null
                editModel = null
            },
            onConfirm = { name, inputPrice, outputPrice, maxTokens, peakMultiplier, peakStart, peakEnd ->
                val target = editModel
                if (target != null) {
                    target.name = name
                    target.maxTokens = maxTokens
                    target.billing = BillingConfig(inputPrice, outputPrice, peakMultiplier, peakStart, peakEnd)
                    SettingsRepository.save()
                } else {
                    val provider = addModelFor
                    if (provider != null) {
                        val model = ModelConfig(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            providerId = provider.id,
                            billing = BillingConfig(inputPrice, outputPrice, peakMultiplier, peakStart, peakEnd),
                            maxTokens = maxTokens
                        )
                        SettingsRepository.update { s ->
                            s.providers.firstOrNull { it.id == provider.id }?.models?.add(model)
                            if (s.activeModelId.isBlank()) s.activeModelId = model.id
                            s
                        }
                    }
                }
                addModelFor = null
                editModel = null
            }
        )
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
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderDialog(
    provider: ProviderConfig?,
    onDismiss: () -> Unit,
    onConfirm: (String, ProviderType, String, String) -> Unit
) {
    var name by remember { mutableStateOf(provider?.name ?: "") }
    var type by remember { mutableStateOf(provider?.type ?: ProviderType.OPENAI_COMPAT) }
    var baseUrl by remember { mutableStateOf(provider?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(provider?.apiKey ?: "") }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (provider == null) "添加 API 提供商" else "编辑提供商") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("类型", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProviderType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text("常用地址", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PROVIDER_PRESETS.forEach { (label, url) ->
                        AssistChip(
                            onClick = {
                                baseUrl = url
                                if (name.isBlank()) name = label
                                if (label == "Anthropic") type = ProviderType.ANTHROPIC
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showKey) "隐藏" else "显示"
                            )
                        }
                    },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || baseUrl.isBlank()) return@TextButton
                onConfirm(name.trim(), type, baseUrl.trim(), apiKey.trim())
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ModelDialog(
    model: ModelConfig?,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double, Int, Double, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf(model?.name ?: "") }
    var inputPrice by remember { mutableStateOf(model?.billing?.inputPrice?.toString() ?: "") }
    var outputPrice by remember { mutableStateOf(model?.billing?.outputPrice?.toString() ?: "") }
    var maxTokens by remember { mutableStateOf((model?.maxTokens ?: 4096).toString()) }
    var peakMultiplier by remember { mutableStateOf(model?.billing?.peakMultiplier?.toString() ?: "1.0") }
    var peakStart by remember { mutableStateOf((model?.billing?.peakStartHour ?: 8).toString()) }
    var peakEnd by remember { mutableStateOf((model?.billing?.peakEndHour ?: 22).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (model == null) "添加模型" else "编辑模型") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("模型名称（接口中的 model 字段）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = inputPrice,
                        onValueChange = { inputPrice = it },
                        label = { Text("输入价格") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = outputPrice,
                        onValueChange = { outputPrice = it },
                        label = { Text("输出价格") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "价格为每百万 token（元），留空或 0 表示不估算费用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("最大输出 tokens") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = peakMultiplier,
                        onValueChange = { peakMultiplier = it },
                        label = { Text("高峰倍率") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = peakStart,
                        onValueChange = { peakStart = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("起") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = peakEnd,
                        onValueChange = { peakEnd = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("止") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "高峰时段按小时计（例如 8 到 22），该时段内按倍率估算费用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                onConfirm(
                    name.trim(),
                    inputPrice.toDoubleOrNull() ?: 0.0,
                    outputPrice.toDoubleOrNull() ?: 0.0,
                    maxTokens.toIntOrNull() ?: 4096,
                    peakMultiplier.toDoubleOrNull() ?: 1.0,
                    (peakStart.toIntOrNull() ?: 8).coerceIn(0, 23),
                    (peakEnd.toIntOrNull() ?: 22).coerceIn(0, 24)
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
