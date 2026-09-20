package com.linetrans.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linetrans.app.data.DocRepository
import com.linetrans.app.data.SettingsRepository
import com.linetrans.app.ui.Motion
import com.linetrans.app.model.BillingConfig
import com.linetrans.app.model.ModelConfig
import com.linetrans.app.model.PromptTemplates
import com.linetrans.app.model.ProviderConfig
import com.linetrans.app.model.ProviderType
import kotlinx.coroutines.launch
import java.util.UUID

private enum class SettingsTab(val label: String) {
    AI("AI 翻译"),
    UI("界面"),
    DATA("数据"),
    ADVANCED("高级"),
    ABOUT("关于")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(SettingsTab.AI) }

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab.ordinal) {
                SettingsTab.entries.forEach { t ->
                    Tab(
                        selected = tab == t,
                        onClick = { tab = t },
                        text = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val offset = if (forward) 1 else -1
                    (slideInHorizontally(Motion.enter(Motion.MEDIUM)) { it / 6 * offset } +
                        fadeIn(animationSpec = Motion.enter(Motion.MEDIUM))) togetherWith
                        (slideOutHorizontally(Motion.exit(Motion.MEDIUM - 60)) { -it / 10 * offset } +
                            fadeOut(animationSpec = Motion.exit(Motion.MEDIUM - 60)))
                },
                label = "settings-tab"
            ) { current ->
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (current) {
                        SettingsTab.AI -> aiTab(::notify)
                        SettingsTab.UI -> uiTab()
                        SettingsTab.DATA -> dataTab(context, ::notify)
                        SettingsTab.ADVANCED -> advancedTab(context, ::notify)
                        SettingsTab.ABOUT -> aboutTab(context)
                    }
                }
            }
        }
    }
}

private typealias Notify = (String) -> Unit

// ———————————————————————— AI 翻译 ————————————————————————

private fun androidx.compose.foundation.lazy.LazyListScope.aiTab(notify: Notify) {
    val settings = SettingsRepository.settings

    item {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("当前模型", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    settings.activeModel?.name ?: "尚未选择模型",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    settings.activeProvider?.let { it.name + " · " + it.type.label } ?: "请先添加 API 提供商",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    item {
        SectionCard(
            title = "API 提供商",
            subtitle = "支持 OpenAI 兼容 / Anthropic / 自定义接口",
            icon = Icons.Default.Sync
        ) {
            if (settings.providers.isEmpty()) {
                Text(
                    "还没有配置服务商，添加后即可使用 AI 翻译。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
            settings.providers.forEach { provider ->
                ProviderRow(
                    provider = provider,
                    active = settings.activeProviderId == provider.id,
                    onSelect = { SettingsRepository.update { it.copy(activeProviderId = provider.id, activeModelId = provider.models.firstOrNull()?.id ?: "") } },
                    onDelete = { SettingsRepository.update { s ->
                        s.providers.removeAll { it.id == provider.id }
                        if (s.activeProviderId == provider.id) s.activeProviderId = s.providers.firstOrNull()?.id ?: ""
                        if (s.activeModel == null) s.activeModelId = s.activeProvider?.models?.firstOrNull()?.id ?: ""
                        s
                    } }
                )
            }
            Spacer(Modifier.height(8.dp))
            ProviderDialogHost(notify = notify)
        }
    }

    if (settings.activeProvider != null) {
        val provider = settings.activeProvider!!
        item {
            SectionCard(
                title = "模型 · " + provider.name,
                subtitle = "价格用于估算费用，可只填其一",
                icon = Icons.Default.Tune
            ) {
                provider.models.forEach { model ->
                    ModelRow(
                        model = model,
                        active = settings.activeModelId == model.id,
                        onSelect = { SettingsRepository.update { it.copy(activeModelId = model.id) } },
                        onDelete = { SettingsRepository.update { s ->
                            s.providers.firstOrNull { it.id == provider.id }?.models?.removeAll { it.id == model.id }
                            if (s.activeModelId == model.id) s.activeModelId = provider.models.firstOrNull()?.id ?: ""
                            s
                        } }
                    )
                }
                Spacer(Modifier.height(8.dp))
                ModelDialogHost(provider = provider, notify = notify)
            }
        }
    }

    item {
        SectionCard(
            title = "翻译参数",
            subtitle = "语言、上下文与行为",
            icon = Icons.Default.Translate
        ) {
            LanguageSettings()
        }
    }

    item {
        SystemPromptCard(notify = notify)
    }

    item {
        GlossaryCard(notify = notify)
    }
}

@Composable
private fun ProviderRow(
    provider: ProviderConfig,
    active: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = active, onClick = onSelect)
        Column(Modifier.weight(1f)) {
            Text(provider.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                provider.type.label + " · " + provider.baseUrl.ifBlank { "未填写地址" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (provider.apiKey.isBlank()) "未填写 API Key" else "Key: " + provider.apiKey.take(4) + "••••",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { showEdit = true }) {
            Icon(Icons.Default.Edit, contentDescription = "编辑")
        }
        IconButton(onClick = { confirmDelete = true }) {
            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除提供商") },
            text = { Text("确定删除「" + provider.name + "」及其模型配置吗？") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
    if (showEdit) {
        ProviderDialog(
            provider = provider,
            onDismiss = { showEdit = false },
            onConfirm = { name, type, baseUrl, apiKey ->
                provider.name = name
                provider.type = type
                provider.baseUrl = baseUrl
                provider.apiKey = apiKey
                SettingsRepository.save()
                showEdit = false
            }
        )
    }
}

@Composable
private fun ModelRow(
    model: ModelConfig,
    active: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var showEdit by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = active, onClick = onSelect)
        Column(Modifier.weight(1f)) {
            Text(model.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "输入 " + model.billing.inputPrice + " / 输出 " + model.billing.outputPrice +
                    " 元每百万 token · T=" + model.temperature,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { showEdit = true }) {
            Icon(Icons.Default.Edit, contentDescription = "编辑模型")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除模型", tint = MaterialTheme.colorScheme.error)
        }
    }
    if (showEdit) {
        ModelDialog(
            model = model,
            onDismiss = { showEdit = false },
            onConfirm = { name, input, output, maxTokens, temp, topP, peak, start, end ->
                model.name = name
                model.maxTokens = maxTokens
                model.temperature = temp
                model.topP = topP
                model.billing = BillingConfig(input, output, peak, start, end)
                SettingsRepository.save()
                showEdit = false
            }
        )
    }
}

@Composable
private fun ProviderDialogHost(notify: Notify) {
    var show by remember { mutableStateOf(false) }
    Button(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("添加 API 提供商")
    }
    if (show) {
        ProviderDialog(
            provider = null,
            onDismiss = { show = false },
            onConfirm = { name, type, baseUrl, apiKey ->
                val id = UUID.randomUUID().toString()
                SettingsRepository.update { s ->
                    s.providers.add(ProviderConfig(id, name, type, baseUrl, apiKey))
                    if (s.activeProviderId.isBlank()) s.activeProviderId = id
                    s
                }
                show = false
                notify("已添加提供商 $name")
            }
        )
    }
}

@Composable
private fun ModelDialogHost(provider: ProviderConfig, notify: Notify) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("添加模型")
    }
    if (show) {
        ModelDialog(
            model = null,
            onDismiss = { show = false },
            onConfirm = { name, input, output, maxTokens, temp, topP, peak, start, end ->
                val model = ModelConfig(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    providerId = provider.id,
                    billing = BillingConfig(input, output, peak, start, end),
                    maxTokens = maxTokens,
                    temperature = temp,
                    topP = topP
                )
                SettingsRepository.update { s ->
                    s.providers.firstOrNull { it.id == provider.id }?.models?.add(model)
                    if (s.activeModelId.isBlank()) s.activeModelId = model.id
                    s
                }
                show = false
                notify("已添加模型 $name")
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageSettings() {
    val settings = SettingsRepository.settings
    var targetText by remember { mutableStateOf(settings.targetLang) }
    var contextText by remember { mutableStateOf(settings.contextUnits.toString()) }

    SwitchRow(
        title = "自动检测源语言",
        subtitle = "关闭后使用下面选择的源语言",
        checked = settings.detectLanguage,
        onCheckedChange = { on -> SettingsRepository.update { it.copy(detectLanguage = on) } }
    )
    Spacer(Modifier.height(6.dp))
    Text("源语言", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    ChipsRow(
        options = listOf(
            "auto" to "自动", "en" to "English", "zh-CN" to "中文",
            "ja" to "日本語", "ko" to "한국어", "fr" to "Français", "de" to "Deutsch"
        ),
        selected = settings.sourceLang,
        onSelect = { code -> SettingsRepository.update { it.copy(sourceLang = code) } }
    )
    Spacer(Modifier.height(12.dp))
    Text("目标语言", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    ChipsRow(
        options = listOf(
            "zh-CN" to "中文", "en" to "English", "ja" to "日本語",
            "ko" to "한국어", "fr" to "Français", "de" to "Deutsch"
        ),
        selected = targetText,
        onSelect = { code ->
            targetText = code
            SettingsRepository.update { it.copy(targetLang = code) }
        }
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = targetText,
        onValueChange = {
            targetText = it
            SettingsRepository.update { s -> s.copy(targetLang = it) }
        },
        label = { Text("自定义目标语言") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = contextText,
        onValueChange = {
            contextText = it.filter { c -> c.isDigit() }.take(2)
            SettingsRepository.update { s -> s.copy(contextUnits = (contextText.toIntOrNull() ?: 0).coerceIn(0, 10)) }
        },
        label = { Text("前文参考条数（0-10）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(6.dp))
    SwitchRow(
        title = "自动跳转下一句",
        subtitle = "AI 翻译完成后自动进入下一句",
        checked = settings.autoAdvance,
        onCheckedChange = { on -> SettingsRepository.update { it.copy(autoAdvance = on) } }
    )
    SwitchRow(
        title = "翻译记忆",
        subtitle = "同一原文已有译文时直接复用，节省 token",
        checked = settings.translationMemory,
        onCheckedChange = { on -> SettingsRepository.update { it.copy(translationMemory = on) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SystemPromptCard(notify: Notify) {
    val settings = SettingsRepository.settings
    var prompt by remember(settings.systemPrompt) {
        mutableStateOf(settings.systemPrompt.ifBlank { settings.effectivePrompt })
    }
    var showPresets by remember { mutableStateOf(false) }

    SectionCard(
        title = "系统提示词",
        subtitle = "留空使用内置默认；支持占位符变量",
        icon = Icons.Default.AutoAwesome
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "当前模板：" + (PromptTemplates.byId(settings.promptTemplateId).name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { showPresets = !showPresets }) { Text("选择模板") }
        }
        if (showPresets) {
            ChipsRow(
                options = PromptTemplates.all.map { it.id to it.name },
                selected = settings.promptTemplateId,
                onSelect = { id ->
                    val template = PromptTemplates.byId(id)
                    prompt = template.prompt
                    SettingsRepository.update {
                        it.copy(promptTemplateId = id, systemPrompt = template.prompt)
                    }
                    notify("已应用模板：" + template.name)
                }
            )
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            minLines = 4,
            label = { Text("系统提示词") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "可用占位符：{sourceLang} 源语言 · {targetLang} 目标语言 · {docName} 文档名 · " +
                "{mode} 逐行/逐句 · {glossary} 术语表",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row {
            Button(
                onClick = {
                    SettingsRepository.update { it.copy(systemPrompt = prompt.trim()) }
                    notify("已保存系统提示词")
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("保存")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                prompt = PromptTemplates.DEFAULT
                SettingsRepository.update {
                    it.copy(systemPrompt = PromptTemplates.DEFAULT, promptTemplateId = "default")
                }
                notify("已恢复默认提示词")
            }) { Text("恢复默认") }
        }
    }
}

@Composable
private fun GlossaryCard(notify: Notify) {
    val settings = SettingsRepository.settings
    var text by remember(settings.glossary) { mutableStateOf(settings.glossary) }
    val count = remember(text) { countGlossary(text) }

    SectionCard(
        title = "术语表",
        subtitle = "每行一条「原文=译文」，翻译时强制使用",
        icon = Icons.Default.Check
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            minLines = 4,
            label = { Text("例如：\nApple=苹果\nmachine learning=机器学习") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "已识别 " + count + " 条术语（# 开头的行为注释）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row {
            Button(
                onClick = {
                    SettingsRepository.update { it.copy(glossary = text) }
                    notify("已保存术语表")
                },
                modifier = Modifier.weight(1f)
            ) { Text("保存术语表") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                text = ""
                SettingsRepository.update { it.copy(glossary = "") }
            }) { Text("清空") }
        }
    }
}

private fun countGlossary(text: String): Int = text.lines().count { line ->
    val t = line.trim()
    t.isNotEmpty() && !t.startsWith("#") &&
        t.indexOfFirst { it == '=' || it == '\t' || it == '：' || it == ':' } > 0
}

// ———————————————————————— 界面 ————————————————————————

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.uiTab() {
    item {
        SectionCard(title = "主题", subtitle = "外观与可读性", icon = Icons.Default.Palette) {
            val settings = SettingsRepository.settings
            Text("主题模式", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            ChipsRow(
                options = com.linetrans.app.model.ThemeMode.entries.map { it to it.label },
                selected = settings.themeMode,
                onSelect = { mode -> SettingsRepository.update { it.copy(themeMode = mode) } }
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "动态取色（Material You）",
                subtitle = "Android 12+ 跟随系统壁纸配色",
                checked = settings.dynamicColor,
                onCheckedChange = { on -> SettingsRepository.update { it.copy(dynamicColor = on) } }
            )
            Spacer(Modifier.height(4.dp))
            Text("界面文字大小", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            ChipsRow(
                options = listOf(0.85f to "小", 1.0f to "标准", 1.15f to "大", 1.3f to "特大"),
                selected = settings.uiScale,
                onSelect = { scale -> SettingsRepository.update { it.copy(uiScale = scale) } }
            )
            Spacer(Modifier.height(10.dp))
            SwitchRow(
                title = "界面动画",
                subtitle = "页面转场、展开收起等动效",
                checked = settings.animations,
                onCheckedChange = { on -> SettingsRepository.update { it.copy(animations = on) } }
            )
            SwitchRow(
                title = "主界面环形进度",
                subtitle = "在文档卡片上显示进度环",
                checked = settings.showProgressRing,
                onCheckedChange = { on -> SettingsRepository.update { it.copy(showProgressRing = on) } }
            )
        }
    }
    item {
        SectionCard(title = "预览", subtitle = "按当前字号设置渲染", icon = Icons.Default.Translate) {
            Text("原文示例：The quick brown fox jumps over the lazy dog.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text("译文示例：敏捷的棕色狐狸跃过懒狗。", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Text("小字说明文本 bodySmall", style = MaterialTheme.typography.bodySmall)
            Text("标签文本 labelMedium", style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ———————————————————————— 数据 ————————————————————————

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.dataTab(context: Context, notify: Notify) {
    val settings = SettingsRepository.settings

    item {
        SectionCard(title = "存储与导出", subtitle = "数据文件夹与默认导出格式", icon = Icons.Default.Storage) {
            DataFolderRow(context, notify)
            Spacer(Modifier.height(10.dp))
            Text("默认导出格式", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            ChipsRow(
                options = com.linetrans.app.model.ExportFormat.entries.map { it to it.label },
                selected = settings.defaultExportFormat,
                onSelect = { fmt -> SettingsRepository.update { it.copy(defaultExportFormat = fmt) } }
            )
        }
    }

    item {
        SectionCard(title = "每日目标", subtitle = "用于主界面进度环", icon = Icons.Default.Check) {
            var goalText by remember { mutableStateOf(if (settings.dailyGoal > 0) settings.dailyGoal.toString() else "") }
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
                    notify("已保存每日目标")
                }) { Text("保存") }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "今日已完成 " + SettingsRepository.dailyCount() + " 句",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    SettingsRepository.resetDaily()
                    notify("已重置今日进度")
                }) { Text("重置") }
            }
        }
    }

    item {
        UsageCard(notify)
    }

    item {
        BackupCard(context, notify)
    }
}

@Composable
private fun DataFolderRow(context: Context, notify: Notify) {
    val settings = SettingsRepository.settings
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            SettingsRepository.update { it.copy(storageDirUri = uri.toString()) }
            notify("已设置数据文件夹")
        }
    }
    Column {
        Text("数据文件夹", style = MaterialTheme.typography.labelLarge)
        Text(
            if (settings.storageDirUri.isBlank()) "未设置，导出的文件会保存到这里"
            else Uri.decode(settings.storageDirUri),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = { picker.launch(null) }, modifier = Modifier.weight(1f)) { Text("选择文件夹") }
            if (settings.storageDirUri.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { SettingsRepository.update { it.copy(storageDirUri = "") } }) {
                    Text("清除")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UsageCard(notify: Notify) {
    val settings = SettingsRepository.settings
    SectionCard(title = "用量统计", subtitle = "按模型累计的 token 与费用", icon = Icons.Default.AutoAwesome) {
        InfoRow("输入 tokens", SettingsRepository.totalInputTokens.toString())
        InfoRow("输出 tokens", SettingsRepository.totalOutputTokens.toString())
        InfoRow(
            "累计费用",
            "≈ " + String.format(java.util.Locale.CHINA, "%.4f", SettingsRepository.totalCost) + " 元"
        )
        Spacer(Modifier.height(10.dp))
        Text("近 7 天完成量", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        WeekChart()
        if (settings.usageByModel.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("按模型", style = MaterialTheme.typography.labelLarge)
            settings.usageByModel.forEach { record ->
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(record.modelName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            record.calls.toString() + " 次 · 输入 " + record.inputTokens + " · 输出 " + record.outputTokens,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "≈ " + String.format(java.util.Locale.CHINA, "%.4f", record.cost),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = {
            SettingsRepository.clearUsage()
            notify("已清空用量统计")
        }) { Text("清空统计") }
    }
}

@Composable
private fun WeekChart() {
    val stats = SettingsRepository.recentStats(7)
    val maxUnits = (stats.maxOfOrNull { it.units } ?: 0).coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(120.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        stats.forEach { stat ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(stat.units.toString(), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                Card(
                    Modifier
                        .fillMaxWidth()
                        .height((8 + 72 * stat.units / maxUnits).dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (stat.units > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {}
                Spacer(Modifier.height(4.dp))
                Text(stat.date.takeLast(5), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun BackupCard(context: Context, notify: Notify) {
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val json = com.linetrans.app.data.StorageManager.readText(context, uri)
            com.linetrans.app.data.BackupManager.restore(json).getOrThrow()
        }.onSuccess { result ->
            notify(
                "已恢复 " + result.docs + " 篇文档" +
                    if (result.settingsRestored) "（含设置）" else ""
            )
        }
            .onFailure { notify("恢复失败：" + (it.message ?: "文件格式不正确")) }
    }

    SectionCard(title = "备份与恢复", subtitle = "包含全部文档与设置", icon = Icons.Default.Save) {
        Row {
            Button(
                onClick = {
                    runCatching {
                        com.linetrans.app.data.BackupManager.exportToFolder(context)
                    }.onSuccess { name ->
                        notify("备份已保存到数据文件夹：$name")
                    }.onFailure { notify("备份失败：" + (it.message ?: "未知错误")) }
                },
                modifier = Modifier.weight(1f)
            ) { Text("导出备份") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { restorePicker.launch(arrayOf("application/json", "text/*")) }) {
                Text("恢复备份")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "当前共 " + DocRepository.docs.size + " 篇文档、" +
                DocRepository.docs.sumOf { it.totalCount } + " 个句子。恢复为合并模式，不会覆盖已有文档。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ———————————————————————— 高级 ————————————————————————

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.advancedTab(context: Context, notify: Notify) {
    item {
        SectionCard(title = "网络与请求", subtitle = "超时、重试与代理", icon = Icons.Default.Sync) {
            NetworkSettings(context, notify)
        }
    }

    item {
        SectionCard(title = "编辑与输入", subtitle = "自动保存与手势", icon = Icons.Default.Edit) {
            val settings = SettingsRepository.settings
            var saveText by remember { mutableStateOf(settings.autoSaveMs.toString()) }
            OutlinedTextField(
                value = saveText,
                onValueChange = {
                    saveText = it.filter { c -> c.isDigit() }.take(4)
                    SettingsRepository.update { s ->
                        s.copy(autoSaveMs = (saveText.toIntOrNull() ?: 700).coerceIn(200, 5000))
                    }
                },
                label = { Text("自动保存延迟（毫秒，200-5000）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                title = "左右滑动切换上下句",
                subtitle = "在翻译页顶部区域左右滑动",
                checked = settings.swipeToSwitch,
                onCheckedChange = { on -> SettingsRepository.update { it.copy(swipeToSwitch = on) } }
            )
            SwitchRow(
                title = "翻译时保持屏幕常亮",
                checked = settings.keepScreenOn,
                onCheckedChange = { on -> SettingsRepository.update { it.copy(keepScreenOn = on) } }
            )
        }
    }

    item {
        SectionCard(title = "Web 终端服务", subtitle = "局域网内浏览器访问手机 shell", icon = Icons.Default.Storage) {
            WebTerminalSettings(context, notify)
        }
    }
}

@Composable
private fun NetworkSettings(context: Context, notify: Notify) {
    val settings = SettingsRepository.settings
    var timeoutText by remember { mutableStateOf(settings.requestTimeoutSec.toString()) }
    var retryText by remember { mutableStateOf(settings.maxRetries.toString()) }
    var proxyText by remember { mutableStateOf(settings.proxyUrl) }
    var uaText by remember { mutableStateOf(settings.userAgent) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    OutlinedTextField(
        value = timeoutText,
        onValueChange = {
            timeoutText = it.filter { c -> c.isDigit() }.take(3)
            SettingsRepository.update { s -> s.copy(requestTimeoutSec = (timeoutText.toIntOrNull() ?: 120).coerceIn(10, 600)) }
        },
        label = { Text("请求超时（秒）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = retryText,
        onValueChange = {
            retryText = it.filter { c -> c.isDigit() }.take(1)
            SettingsRepository.update { s -> s.copy(maxRetries = (retryText.toIntOrNull() ?: 2).coerceIn(0, 5)) }
        },
        label = { Text("失败重试次数（0-5）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = proxyText,
        onValueChange = {
            proxyText = it
            SettingsRepository.update { s -> s.copy(proxyUrl = it.trim()) }
        },
        label = { Text("HTTP 代理（可留空）") },
        placeholder = { Text("http://127.0.0.1:7890") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = uaText,
        onValueChange = {
            uaText = it
            SettingsRepository.update { s -> s.copy(userAgent = it.trim()) }
        },
        label = { Text("自定义 User-Agent（可留空）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = {
            testing = true
            val current = SettingsRepository.settings
            scope.launch {
                val result = com.linetrans.app.ai.TranslationService(context).testConnection(current)
                testing = false
                notify(result.message)
            }
        },
        enabled = !testing,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.width(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (testing) "测试中…" else "测试当前模型连接")
    }
}

@Composable
private fun WebTerminalSettings(context: Context, notify: Notify) {
    val settings = SettingsRepository.settings
    var portText by remember { mutableStateOf(settings.webServerPort.toString()) }
    var tokenText by remember { mutableStateOf(settings.webServerToken) }
    val running = com.linetrans.app.server.WebTerminalService.isRunning

    SwitchRow(
        title = "启用 Web 终端服务",
        subtitle = if (running) "运行中" else "已停止",
        checked = settings.webServerEnabled,
        onCheckedChange = { checked ->
            if (checked) {
                SettingsRepository.update { it.copy(webServerEnabled = true) }
                com.linetrans.app.server.WebTerminalService.start(context)
                notify("Web 终端服务已开启")
            } else {
                com.linetrans.app.server.WebTerminalService.stop(context)
                SettingsRepository.update { it.copy(webServerEnabled = false) }
                notify("Web 终端服务已关闭")
            }
        }
    )
    SwitchRow(
        title = "应用启动后自动开启",
        checked = settings.webServerAutoStart,
        onCheckedChange = { on -> SettingsRepository.update { it.copy(webServerAutoStart = on) } }
    )
    Spacer(Modifier.height(8.dp))
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
            if (com.linetrans.app.server.WebTerminalService.isRunning) {
                com.linetrans.app.server.WebTerminalService.stop(context)
                com.linetrans.app.server.WebTerminalService.start(context)
            }
            notify("端口已设为 $p")
        }) { Text("应用") }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = tokenText,
        onValueChange = {
            tokenText = it
            SettingsRepository.update { s -> s.copy(webServerToken = it.trim()) }
        },
        label = { Text("访问令牌（可留空）") },
        placeholder = { Text("填写后访问需要携带 ?token=xxx") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    if (settings.webServerEnabled || running) {
        val ip = com.linetrans.app.util.NetworkUtils.getLocalIpAddress() ?: "127.0.0.1"
        val suffix = if (settings.webServerToken.isBlank()) "" else "?token=" + settings.webServerToken
        val url = "http://" + ip + ":" + settings.webServerPort + "/terminal" + suffix
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("url", url))
                notify("已复制访问地址")
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "复制地址")
            }
        }
    }
}

// ———————————————————————— 关于 ————————————————————————

private fun androidx.compose.foundation.lazy.LazyListScope.aboutTab(context: Context) {
    item {
        SectionCard(title = "逐行翻译", subtitle = "v" + com.linetrans.app.BuildConfig.VERSION_NAME, icon = Icons.Default.Translate) {
            Text(
                "一款用于逐行 / 逐句对照翻译的安卓原生应用，支持多家 AI 接口、翻译记忆、" +
                    "批量翻译与局域网 Web 终端。",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/bityng/line-trans"))
                    )
                }
            }) { Text("查看项目主页") }
        }
    }
    item {
        SectionCard(title = "最近更新", subtitle = "v1.3.1", icon = Icons.Default.AutoAwesome) {
            listOf(
                "键盘弹出时自动收起次要区域，译文输入框不再被挤压遮挡",
                "页面转场与展开动画统一曲线与时长，过渡更顺滑",
                "设置页重构为 AI / 界面 / 数据 / 高级 / 关于 五个分类",
                "系统提示词与术语表开放给用户自定义",
                "翻译记忆、撤销重做、查找替换、朗读、收藏",
                "导出支持 Markdown / CSV / JSON，支持分享",
                "用量统计与近 7 天进度图表",
                "文档置顶、排序、备份与恢复"
            ).forEach { line ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text("· ", style = MaterialTheme.typography.bodyMedium)
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    item {
        SectionCard(title = "开源许可", subtitle = "MIT License", icon = Icons.Default.Check) {
            Text(
                "本项目基于 MIT 许可开源，可自由使用与修改。",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
