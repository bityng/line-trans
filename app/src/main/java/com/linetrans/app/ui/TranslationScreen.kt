package com.linetrans.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationScreen(docId: String, startIndex: Int = 0, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { TranslationService(context) }
    val snackbar = remember { SnackbarHostState() }

    val doc = DocRepository.get(docId) ?: run {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    if (doc.units.isEmpty()) {
        EmptyDocScreen(doc = doc, onBack = onBack)
        return
    }

    var revision by remember(docId) { mutableStateOf(0) }
    var currentIndex by remember(docId) {
        mutableStateOf(startIndex.coerceIn(0, (doc.units.size - 1).coerceAtLeast(0)))
    }
    var mode by remember(docId) { mutableStateOf(doc.unitMode) }
    var translatedText by remember(docId) { mutableStateOf(doc.units[currentIndex].translation) }
    var editableOriginal by remember(docId) { mutableStateOf(false) }
    var dividerLocked by remember(docId) { mutableStateOf(false) }
    var splitFraction by remember(docId) { mutableStateOf(0.5f) }
    var aiLoading by remember(docId) { mutableStateOf(false) }
    var topMenu by remember(docId) { mutableStateOf(false) }
    var unitMenu by remember(docId) { mutableStateOf(false) }
    var showDone by remember(docId) { mutableStateOf(false) }
    var showJump by remember(docId) { mutableStateOf(false) }
    var showBatchConfirm by remember(docId) { mutableStateOf(false) }
    var batchRunning by remember(docId) { mutableStateOf(false) }
    var batchDone by remember(docId) { mutableStateOf(0) }
    var batchTotal by remember(docId) { mutableStateOf(0) }
    var cancelBatch by remember(docId) { mutableStateOf(false) }
    var usage by remember(docId) { mutableStateOf(TranslationService.UsageState()) }

    // 读取 revision，保证文档内容变化后统计信息会刷新
    val stats = remember(revision) {
        Triple(doc.translatedCount, doc.totalCount, doc.progress)
    }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun currentUnit(): TranslationUnit? = doc.units.getOrNull(currentIndex)

    /** 统一的单元格更新入口：处理“今日进度”与文档保存。 */
    fun applyUnit(unit: TranslationUnit, translation: String? = null, done: Boolean? = null) {
        val wasDone = unit.isDone
        if (translation != null) unit.translation = translation
        if (done != null) unit.done = done
        if (!wasDone && unit.isDone) SettingsRepository.bumpDaily(1)
        DocRepository.save(doc)
        revision++
    }

    fun select(index: Int) {
        if (index !in doc.units.indices) return
        currentUnit()?.let { unit ->
            if (unit.translation != translatedText) applyUnit(unit, translation = translatedText)
        }
        currentIndex = index
        translatedText = doc.units[index].translation
        editableOriginal = false
    }

    fun goNext() {
        val unit = currentUnit() ?: return
        applyUnit(unit, translation = translatedText, done = true)
        if (currentIndex < doc.units.size - 1) {
            select(currentIndex + 1)
        } else {
            showDone = true
        }
    }

    fun goPrev() {
        if (currentIndex <= 0) return
        select(currentIndex - 1)
    }

    fun skipCurrent() {
        val unit = currentUnit() ?: return
        applyUnit(unit, translation = "", done = true)
        translatedText = ""
        if (currentIndex < doc.units.size - 1) {
            select(currentIndex + 1)
        } else {
            showDone = true
        }
    }

    fun switchMode(newMode: UnitMode) {
        if (newMode == doc.unitMode) return
        val source = if (doc.sourceText.isNotBlank()) doc.sourceText
        else doc.units.joinToString("\n") { it.source }
        val oldBySource = doc.units.associateBy { it.source }
        val newUnits = TextParser.parse(source, newMode).map { u ->
            val old = oldBySource[u.source]
            if (old != null) TranslationUnit(u.source, old.translation, old.done) else u
        }.toMutableList()
        doc.units.clear()
        doc.units.addAll(newUnits)
        doc.unitMode = newMode
        mode = newMode
        currentIndex = 0
        translatedText = doc.units.firstOrNull()?.translation ?: ""
        DocRepository.save(doc, immediate = true)
        revision++
        notify("已切换为" + if (newMode == UnitMode.SENTENCE) "逐句" else "逐行" + "模式")
    }

    fun copyToClipboard(text: String, label: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        toast("已复制")
    }

    fun countCost(result: TranslationService.Result) {
        val model = SettingsRepository.settings.activeModel
        val cost = if (model != null) {
            CostCalculator.costFor(model, result.promptTokens, result.completionTokens)
        } else 0.0
        usage = usage.plus(result, cost)
    }

    fun runAi(onFinished: (() -> Unit)? = null) {
        val unit = currentUnit() ?: return
        val settings = SettingsRepository.settings
        if (settings.activeProvider == null) {
            notify("请先在设置中添加并选择 API 提供商")
            return
        }
        if (unit.source.isBlank()) {
            notify("原文为空，无法翻译")
            return
        }
        aiLoading = true
        val index = currentIndex
        scope.launch {
            try {
                val result = service.translate(
                    settings = settings,
                    docName = doc.name,
                    unit = unit,
                    mode = mode,
                    index = index,
                    total = doc.units.size,
                    previousUnits = doc.units.take(index)
                )
                translatedText = result.text
                applyUnit(unit, translation = result.text, done = true)
                countCost(result)
                onFinished?.invoke()
            } catch (e: Exception) {
                notify("翻译失败：" + (e.message ?: "未知错误"))
            } finally {
                aiLoading = false
            }
        }
    }

    fun advanceAfterAi() {
        val next = doc.nextUndoneIndex(currentIndex + 1)
        when {
            next != null -> select(next)
            currentIndex < doc.units.size - 1 -> select(currentIndex + 1)
            else -> showDone = true
        }
    }

    fun runBatch() {
        val settings = SettingsRepository.settings
        if (settings.activeProvider == null) {
            notify("请先在设置中添加并选择 API 提供商")
            return
        }
        batchRunning = true
        cancelBatch = false
        batchDone = 0
        val total = doc.units.size
        batchTotal = doc.remainingCount
        scope.launch {
            var failed: String? = null
            for (i in doc.units.indices) {
                if (cancelBatch) break
                val unit = doc.units[i]
                if (unit.isDone) continue
                try {
                    val result = service.translate(
                        settings = SettingsRepository.settings,
                        docName = doc.name,
                        unit = unit,
                        mode = mode,
                        index = i,
                        total = total,
                        previousUnits = doc.units.take(i)
                    )
                    unit.translation = result.text
                    unit.done = true
                    countCost(result)
                    SettingsRepository.bumpDaily(1)
                    DocRepository.save(doc)
                    batchDone++
                    revision++
                    currentIndex = i
                    translatedText = result.text
                } catch (e: Exception) {
                    failed = e.message ?: "未知错误"
                    break
                }
            }
            batchRunning = false
            DocRepository.save(doc, immediate = true)
            when {
                failed != null -> notify("批量翻译中断：" + failed)
                cancelBatch -> notify("已停止批量翻译")
                else -> notify("批量翻译完成，共 " + batchDone + " 句")
            }
        }
    }

    fun exit() {
        currentUnit()?.let { unit ->
            if (unit.translation != translatedText) applyUnit(unit, translation = translatedText)
        }
        DocRepository.flushAll()
        onBack()
    }

    BackHandler { exit() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(doc.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "已完成 " + stats.first + " / " + stats.second + " " + unitLabel(mode) +
                                " · " + if (mode == UnitMode.SENTENCE) "逐句" else "逐行",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { exit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { goPrev() }, enabled = currentIndex > 0) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "上一句")
                    }
                    TextButton(onClick = { goNext() }, enabled = !aiLoading && !batchRunning) {
                        Icon(Icons.Default.SkipNext, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (currentIndex == doc.units.size - 1) "完成" else "下一句")
                    }
                    Box {
                        IconButton(onClick = { topMenu = true }, enabled = !batchRunning) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("跳转到…") },
                                onClick = { topMenu = false; showJump = true }
                            )
                            DropdownMenuItem(
                                text = { Text("批量翻译剩余 " + doc.remainingCount + " " + unitLabel(mode)) },
                                onClick = { topMenu = false; showBatchConfirm = true }
                            )
                            DropdownMenuItem(
                                text = { Text("跳到下一个未完成") },
                                onClick = {
                                    topMenu = false
                                    val next = doc.nextUndoneIndex(currentIndex)
                                    if (next == null) notify("已全部完成") else select(next)
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        ) {
            LinearProgressIndicator(
                progress = { stats.third },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "第 " + (currentIndex + 1) + " / " + doc.units.size + " " + unitLabel(mode) +
                        " · 完成 " + (stats.third * 100).roundToInt() + "%",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (doc.remainingCount > 0) "剩余 " + doc.remainingCount + " " + unitLabel(mode) else "已全部完成",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (doc.remainingCount > 0) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary
                )
            }
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
                TextButton(onClick = { showJump = true }) { Text("跳转") }
            }

            // 原文
            Column(Modifier.fillMaxWidth().weight(splitFraction)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("原文", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { copyToClipboard(currentUnit()?.source ?: "", "原文") }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("复制")
                    }
                    TextButton(onClick = { editableOriginal = !editableOriginal }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (editableOriginal) "完成" else "修改")
                    }
                }
                OutlinedTextField(
                    value = currentUnit()?.source ?: "",
                    onValueChange = { text ->
                        if (editableOriginal) {
                            currentUnit()?.let { unit ->
                                unit.source = text
                                DocRepository.save(doc)
                                revision++
                            }
                        }
                    },
                    readOnly = !editableOriginal,
                    placeholder = { Text("原文") },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }

            // 可调分隔线
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = {
                    dividerLocked = !dividerLocked
                    toast(if (dividerLocked) "已锁定分割线" else "已解锁分割线")
                }) {
                    Icon(
                        if (dividerLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (dividerLocked) "解锁分割线" else "锁定分割线",
                        modifier = Modifier.width(18.dp)
                    )
                }
                Slider(
                    value = splitFraction,
                    onValueChange = { if (!dividerLocked) splitFraction = it.coerceIn(0.2f, 0.8f) },
                    valueRange = 0.2f..0.8f,
                    enabled = !dividerLocked,
                    modifier = Modifier.weight(1f)
                )
            }

            // 译文
            Column(Modifier.fillMaxWidth().weight(1f - splitFraction)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("译文", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { skipCurrent() }, enabled = !aiLoading && !batchRunning) {
                        Text("跳过")
                    }
                    TextButton(onClick = { copyToClipboard(translatedText, "译文") }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("复制")
                    }
                    Box {
                        IconButton(onClick = { unitMenu = true }, enabled = !batchRunning) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("粘贴原文") },
                                onClick = {
                                    unitMenu = false
                                    val text = currentUnit()?.source ?: ""
                                    translatedText = text
                                    currentUnit()?.let { applyUnit(it, translation = text) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("清空译文") },
                                onClick = {
                                    unitMenu = false
                                    translatedText = ""
                                    currentUnit()?.let { applyUnit(it, translation = "", done = false) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("复制原文") },
                                onClick = {
                                    unitMenu = false
                                    copyToClipboard(currentUnit()?.source ?: "", "原文")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("重新翻译本句") },
                                onClick = { unitMenu = false; runAi() }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            runAi(onFinished = {
                                if (SettingsRepository.settings.autoAdvance) advanceAfterAi()
                            })
                        },
                        enabled = !aiLoading && !batchRunning,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (aiLoading) "翻译中…" else "AI 翻译")
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = translatedText,
                    onValueChange = { text ->
                        translatedText = text
                        currentUnit()?.let { applyUnit(it, translation = text) }
                    },
                    placeholder = { Text("在这里输入译文") },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }

            UsageBar(usage = usage, batchRunning = batchRunning, batchDone = batchDone, batchTotal = batchTotal)
        }
    }

    if (showJump) {
        JumpDialog(
            doc = doc,
            currentIndex = currentIndex,
            onDismiss = { showJump = false },
            onSelect = { showJump = false; select(it) }
        )
    }

    if (showBatchConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchConfirm = false },
            title = { Text("批量翻译") },
            text = {
                Text(
                    "将按顺序调用 AI 翻译剩余 " + doc.remainingCount + " " + unitLabel(mode) +
                        "，过程中可以随时停止。可能产生较多 API 费用。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showBatchConfirm = false; runBatch() }) { Text("开始") }
            },
            dismissButton = { TextButton(onClick = { showBatchConfirm = false }) { Text("取消") } }
        )
    }

    if (batchRunning) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("正在批量翻译") },
            text = {
                Column {
                    Text("已完成 " + batchDone + " / " + batchTotal + " " + unitLabel(mode))
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (batchTotal <= 0) 0f else (batchDone.toFloat() / batchTotal).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { cancelBatch = true }) { Text("停止") }
            }
        )
    }

    if (showDone) {
        AlertDialog(
            onDismissRequest = { showDone = false },
            title = { Text("全部完成") },
            text = { Text("已处理完全部 " + doc.units.size + " " + unitLabel(mode) + "。") },
            confirmButton = {
                TextButton(onClick = { showDone = false; exit() }) { Text("返回主页") }
            },
            dismissButton = { TextButton(onClick = { showDone = false }) { Text("继续查看") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmptyDocScreen(doc: TranslationDoc, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(doc.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("该文档没有可翻译内容", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "可以删除后重新导入，或检查源文本是否为空。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UsageBar(
    usage: TranslationService.UsageState,
    batchRunning: Boolean,
    batchDone: Int,
    batchTotal: Int
) {
    val modelName = SettingsRepository.settings.activeModel?.name ?: "未配置模型"
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (batchRunning) {
                Text(
                    "批量翻译中 " + batchDone + " / " + batchTotal,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("模型：" + modelName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("调用 " + usage.calls + " 次", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "输入 " + usage.inputMissTokens + "（命中 " + usage.inputHitTokens + "） · 输出 " + usage.outputTokens,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "约 " + String.format(Locale.CHINA, "%.4f", usage.totalCost) + " 元",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun JumpDialog(
    doc: TranslationDoc,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 3).coerceAtLeast(0))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳转 · 共 " + doc.units.size + " " + unitLabel(doc.unitMode)) },
        text = {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(doc.units) { index, unit ->
                    val isCurrent = index == currentIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelect(index) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .width(8.dp)
                                .height(8.dp)
                                .background(
                                    if (unit.isDone) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                (index + 1).toString() + ". " + unit.source,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (unit.translation.isNotBlank()) {
                                Text(
                                    unit.translation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
