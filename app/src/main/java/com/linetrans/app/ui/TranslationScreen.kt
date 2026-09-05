package com.linetrans.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.linetrans.app.ai.TranslationService
import com.linetrans.app.data.DocRepository
import com.linetrans.app.data.SettingsRepository
import com.linetrans.app.model.TranslationDoc
import com.linetrans.app.model.TranslationUnit
import com.linetrans.app.model.UnitMode
import com.linetrans.app.util.CostCalculator
import com.linetrans.app.util.TextParser
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationScreen(docId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { TranslationService(context) }

    val doc = DocRepository.get(docId) ?: run {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var currentIndex by remember(docId) { mutableStateOf(0) }
    var mode by remember(docId) { mutableStateOf(doc.unitMode) }
    var editableOriginal by remember(docId) { mutableStateOf(false) }
    var dividerLocked by remember(docId) { mutableStateOf(false) }
    var splitFraction by remember(docId) { mutableStateOf(0.5f) }
    var translatedText by remember(docId) { mutableStateOf("") }
    var aiLoading by remember(docId) { mutableStateOf(false) }
    var message by remember(docId) { mutableStateOf<String?>(null) }
    var moreMenu by remember(docId) { mutableStateOf(false) }
    var showDone by remember(docId) { mutableStateOf(false) }
    var usage by remember(docId) { mutableStateOf(TranslationService.UsageState()) }

    fun currentUnit(): TranslationUnit? = if (doc.units.isEmpty()) null else doc.units[currentIndex.coerceAtMost(doc.units.size - 1)]

    LaunchedEffect(currentIndex, doc.units.size) {
        translatedText = currentUnit()?.translation ?: ""
    }

    fun saveCurrent(translation: String) {
        val u = currentUnit() ?: return
        u.translation = translation
        DocRepository.save(doc)
    }

    fun switchMode(newMode: UnitMode) {
        if (newMode == doc.unitMode) return
        val source = if (doc.sourceText.isNotBlank()) doc.sourceText else doc.units.joinToString("\n") { it.source }
        val oldBySource = doc.units.associate { it.source to it.translation }
        val newUnits = TextParser.parse(source, newMode).map { u ->
            val found = oldBySource[u.source]
            if (found != null) TranslationUnit(u.source, found) else u
        }.toMutableList()
        doc.units.clear()
        doc.units.addAll(newUnits)
        doc.unitMode = newMode
        mode = newMode
        currentIndex = 0
        translatedText = doc.units.firstOrNull()?.translation ?: ""
        DocRepository.save(doc)
    }

    fun copyToClipboard(text: String, label: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }

    fun runAi() {
        val u = currentUnit() ?: return
        val settings = SettingsRepository.settings
        if (settings.activeProvider == null) {
            message = "请先在设置中添加并选择 API 提供商"
            return
        }
        if (u.source.isBlank()) return
        aiLoading = true
        scope.launch {
            try {
                val prev = doc.units.take(currentIndex).filter { it.isTranslated }
                val result = service.translate(
                    settings = settings,
                    docName = doc.name,
                    unit = u,
                    mode = mode,
                    index = currentIndex,
                    total = doc.units.size,
                    previousUnits = prev
                )
                translatedText = result.text
                u.translation = result.text
                val model = settings.activeModel
                val cost = if (model != null) CostCalculator.costFor(model, result.promptTokens, result.completionTokens) else 0.0
                usage = usage.copy(
                    inputTokens = usage.inputTokens + result.promptTokens,
                    outputTokens = usage.outputTokens + result.completionTokens,
                    totalCost = usage.totalCost + cost
                )
                DocRepository.save(doc)
            } catch (e: Exception) {
                message = e.message
            } finally {
                aiLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(doc.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "已翻译 " + doc.translatedCount + " / " + doc.totalCount + " 句" +
                                (if (mode == UnitMode.SENTENCE) " · 逐句" else " · 逐行"),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                actions = {
                    IconButton(onClick = {
                        if (currentIndex > 0) {
                            saveCurrent(translatedText)
                            currentIndex--
                        }
                    }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "上一句")
                    }
                    TextButton(onClick = {
                        if (currentIndex < doc.units.size - 1) {
                            saveCurrent(translatedText)
                            currentIndex++
                        } else {
                            saveCurrent(translatedText)
                            showDone = true
                        }
                    }) {
                        Icon(Icons.Default.SkipNext, null)
                        Spacer(Modifier.width(4.dp))
                        Text("下一句")
                    }
                }
            )
        }
    ) { padding ->
        if (doc.units.isEmpty()) {
            Column(Modifier.padding(padding).fillMaxSize().padding(24.dp)) {
                Text("该文档没有可翻译内容")
            }
            return@Scaffold
        }

        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            LinearProgressIndicator(
                progress = { doc.progress },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            Text(
                "已翻译 " + doc.translatedCount + " / " + doc.totalCount + " 句 · " +
                    (if (mode == UnitMode.SENTENCE) "逐句模式" else "逐行模式"),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = mode == UnitMode.SENTENCE,
                    onClick = { switchMode(UnitMode.SENTENCE) },
                    label = { Text("逐句") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = mode == UnitMode.LINE,
                    onClick = { switchMode(UnitMode.LINE) },
                    label = { Text("逐行") }
                )
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))

            // 上方原文区
            Column(Modifier.fillMaxWidth().weight(splitFraction)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("原文", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { copyToClipboard(currentUnit()?.source ?: "", "原文") }) {
                        Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("复制")
                    }
                    TextButton(onClick = { editableOriginal = !editableOriginal }) {
                        Icon(Icons.Default.Edit, null); Spacer(Modifier.width(4.dp));
                        Text(if (editableOriginal) "完成" else "修改")
                    }
                }
                OutlinedTextField(
                    value = currentUnit()?.source ?: "",
                    onValueChange = { if (editableOriginal) { val u = currentUnit(); if (u != null) u.source = it } },
                    readOnly = !editableOriginal,
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                )
            }

            // 中间可调分隔线
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                IconButton(onClick = { dividerLocked = !dividerLocked }) {
                    Icon(if (dividerLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "锁定分割线")
                }
                Slider(
                    value = splitFraction,
                    onValueChange = { if (!dividerLocked) splitFraction = it },
                    enabled = !dividerLocked,
                    modifier = Modifier.weight(1f)
                )
            }
            Divider()

            // 下方翻译区
            Column(Modifier.fillMaxWidth().weight(1f - splitFraction)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("译文", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { runAi() }, enabled = !aiLoading) {
                        Icon(Icons.Default.Send, null); Spacer(Modifier.width(4.dp));
                        Text(if (aiLoading) "翻译中..." else "AI翻译")
                    }
                    TextButton(onClick = { copyToClipboard(translatedText, "译文") }) {
                        Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("复制")
                    }
                    TextButton(onClick = { translatedText = currentUnit()?.source ?: "" }) {
                        Text("粘贴原文")
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "更多") }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(text = { Text("清空译文") }, onClick = {
                                translatedText = ""
                                moreMenu = false
                            })
                            DropdownMenuItem(text = { Text("复制原文") }, onClick = {
                                copyToClipboard(currentUnit()?.source ?: "", "原文")
                                moreMenu = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = translatedText,
                    onValueChange = {
                        translatedText = it
                        saveCurrent(it)
                    },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    placeholder = { Text("请输入翻译的内容") }
                )
            }

            Spacer(Modifier.height(8.dp))

            // 用量与费用
            Column {
                val settings = SettingsRepository.settings
                val modelName = settings.activeModel?.name ?: "未配置"
                Text("当前模型：" + modelName, style = MaterialTheme.typography.bodySmall)
                Text("输入（未命中）：" + usage.inputTokens, style = MaterialTheme.typography.bodySmall)
                Text("输入（命中）：" + usage.outputTokens, style = MaterialTheme.typography.bodySmall)
                Text("输出：" + usage.outputTokens, style = MaterialTheme.typography.bodySmall)
                Text("预计费用：" + String.format("%.6f", usage.totalCost) + " 元", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (message != null) {
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { TextButton(onClick = { message = null }) { Text("确定") } },
            title = { Text("提示") },
            text = { Text(message!!) }
        )
    }

    if (showDone) {
        AlertDialog(
            onDismissRequest = { showDone = false },
            confirmButton = { TextButton(onClick = { showDone = false; onBack() }) { Text("返回主页") } },
            title = { Text("翻译完毕") },
            text = { Text("已完成全部 " + doc.units.size + " 句的翻译。") }
        )
    }
}
