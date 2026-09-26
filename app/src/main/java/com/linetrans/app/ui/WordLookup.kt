package com.linetrans.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.input.pointer.pointerInput
import com.linetrans.app.ai.DictionaryService
import com.linetrans.app.data.WordEntry
import kotlin.math.roundToInt

/**
 * 可点词文本：触摸某个单词时回调该词与它在窗口中的位置（用于在词上方弹出释义）。
 */
@Composable
fun WordLookupText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    availableWidthDp: Int = 0,
    onWordTap: (word: String, windowAnchor: IntOffset) -> Unit
) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    androidx.compose.material3.Text(
        text = text,
        style = style,
        modifier = modifier
            .onGloballyPositioned { coordinates = it }
            .pointerInput(text, style, availableWidthDp) {
                detectTapGestures { position ->
                    val result = layout ?: return@detectTapGestures
                    val coords = coordinates ?: return@detectTapGestures
                    val offset = result.getOffsetForPosition(position).coerceIn(0, (text.length - 1).coerceAtLeast(0))
                    val range = runCatching { result.getWordBoundary(offset) }.getOrNull()
                        ?: return@detectTapGestures
                    if (range.start >= range.end) return@detectTapGestures
                    val raw = text.substring(range.start, range.end)
                    val lead = raw.indexOfFirst { !it.isWhitespace() }
                    if (lead < 0) return@detectTapGestures
                    val wordStart = range.start + lead
                    val word = raw.trim().trimEnd('.', ',', ';', ':', '!', '?', '"', '\'', '。', '，', '、', '；', '：', '！', '？', '」', '』', '）', ')')
                    if (word.isBlank()) return@detectTapGestures
                    val box = runCatching { result.getBoundingBox(wordStart) }.getOrNull()
                        ?: return@detectTapGestures
                    val top = coords.localToWindow(Offset(box.left, box.top))
                    val bottom = coords.localToWindow(Offset(box.right, box.bottom))
                    onWordTap(
                        word,
                        IntOffset(((top.x + bottom.x) / 2).roundToInt(), top.y.roundToInt())
                    )
                }
            },
        onTextLayout = { layout = it }
    )
}

/** 让弹层落在单词上方、水平居中对齐。 */
private class AboveTextPositionProvider(
    private val anchor: IntOffset,
    private val marginPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = (anchor.x - popupContentSize.width / 2).coerceIn(marginPx, maxX)
        val above = anchor.y - popupContentSize.height - marginPx
        val y = if (above >= marginPx) above else anchor.y + marginPx * 4
        return IntOffset(x, y)
    }
}

/**
 * 单词释义浮层：显示音标、释义（牛津 / Wiktionary / AI）、中文解释与操作。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordDefinitionPopup(
    word: String,
    anchor: IntOffset,
    entry: DictionaryService.Entry?,
    localEntry: WordEntry?,
    loading: Boolean,
    definitionLanguage: String,
    onDismiss: () -> Unit,
    onSpeak: (String) -> Unit,
    onCopy: (String) -> Unit,
    onSaveToWordbook: (meaning: String) -> Unit,
    onRemoveFromWordbook: () -> Unit,
    onRetry: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val density = LocalDensity.current
    val margin = with(density) { 12.dp.roundToPx() }
    var showEnglish by remember(word, definitionLanguage) {
        mutableStateOf(definitionLanguage != "zh")
    }
    val fromLocalDict = entry?.source == DictionaryService.Source.LOCAL.label
    val zhSenses = if (fromLocalDict) entry?.senses.orEmpty() else emptyList()
    val enSenses = if (fromLocalDict) emptyList() else entry?.senses.orEmpty()

    Popup(
        popupPositionProvider = AboveTextPositionProvider(anchor, margin),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp,
            modifier = Modifier
                .widthIn(min = 240.dp, max = 340.dp)
                .padding(horizontal = 4.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val phonetic = entry?.phonetic
                    if (!phonetic.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            phonetic,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(max = 110.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onSpeak(word) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "朗读单词",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                val sourceLabel = entry?.source
                val badge = when {
                    localEntry != null && !sourceLabel.isNullOrBlank() -> "我的词库 · " + sourceLabel
                    localEntry != null -> "我的词库"
                    else -> sourceLabel
                }
                if (!badge.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                when {
                    loading && localEntry == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("正在查询…", style = MaterialTheme.typography.bodyMedium)
                    }

                    localEntry == null && (entry == null || !entry.ok) -> Column {
                        Text(
                            entry?.error ?: "没有查到释义",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(6.dp))
                        Row {
                            TextButton(onClick = onRetry) { Text("重试") }
                            TextButton(onClick = {
                                onOpenUrl("https://www.oxfordlearnersdictionaries.com/definition/english/" + word.lowercase())
                            }) { Text("在牛津网站查看") }
                        }
                    }

                    else -> Column {
                        // 我的词库优先显示
                        if (localEntry != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            localEntry.meaning.ifBlank { "（无释义）" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (localEntry.reading.isNotBlank()) {
                                            Text(
                                                localEntry.reading,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = onRemoveFromWordbook,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                    ) { Text("从词库移除", style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        val translation = entry?.translation
                        val showZhBox = !translation.isNullOrBlank() &&
                            (localEntry == null || localEntry.meaning.trim() != translation.trim())
                        if (showZhBox && !translation.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    translation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (loading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("正在查询词典…", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        // 中文释义（来自本地词库，最常用）
                        if (zhSenses.isNotEmpty()) {
                            Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                                zhSenses.forEachIndexed { index, sense -> SenseLine(sense, index > 0) }
                            }
                        }
                        // 英文释义：默认折叠，需要时可以展开
                        if (enSenses.isNotEmpty()) {
                            if (definitionLanguage == "zh") {
                                TextButton(
                                    onClick = { showEnglish = !showEnglish },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                ) {
                                    Text(
                                        if (showEnglish) "收起英文释义" else "显示英文释义（" + enSenses.size + "）",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                            if (showEnglish) {
                                if (zhSenses.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    HorizontalDivider()
                                    Spacer(Modifier.height(6.dp))
                                }
                                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                                    enSenses.forEachIndexed { index, sense -> SenseLine(sense, index > 0) }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                val text = buildString {
                                    append(word)
                                    if (!entry?.phonetic.isNullOrBlank()) append(" ").append(entry?.phonetic)
                                    localEntry?.meaning?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
                                    entry?.translation?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
                                    entry?.senses?.forEach { s ->
                                        append("\n")
                                        if (s.partOfSpeech.isNotBlank()) append(s.partOfSpeech).append(": ")
                                        append(s.definition)
                                    }
                                }
                                onCopy(text)
                            }) {
                                Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("复制")
                            }
                            val saveMeaning = localEntry?.meaning?.takeIf { it.isNotBlank() }
                                ?: entry?.translation?.takeIf { it.isNotBlank() }
                                ?: entry?.senses?.firstOrNull()?.definition
                            if (localEntry == null && !saveMeaning.isNullOrBlank()) {
                                TextButton(onClick = { onSaveToWordbook(saveMeaning) }) {
                                    Icon(Icons.Default.Star, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("加入词库")
                                }
                            }
                            if (!entry?.url.isNullOrBlank()) {
                                TextButton(onClick = { entry?.url?.let { onOpenUrl(it) } }) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("详情")
                                }
                            }
                            TextButton(onClick = onRetry) {
                                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("重查")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 释义卡片里用到的高亮底色（浅深色都可读）。 */
internal val LookupHighlight: Color @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

/** 一条释义：词性（可选）+ 正文 + 例句。 */
@Composable
private fun SenseLine(sense: DictionaryService.Sense, divider: Boolean) {
    if (divider) {
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
    }
    Row {
        if (sense.partOfSpeech.isNotBlank()) {
            Text(
                sense.partOfSpeech,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(52.dp)
            )
        }
        Text(
            sense.definition,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
    sense.example?.takeIf { it.isNotBlank() }?.let { example ->
        Text(
            "例：" + example,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, start = if (sense.partOfSpeech.isBlank()) 0.dp else 52.dp)
        )
    }
}
