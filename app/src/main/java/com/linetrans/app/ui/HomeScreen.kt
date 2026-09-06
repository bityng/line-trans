package com.linetrans.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.linetrans.app.data.DocRepository
import com.linetrans.app.data.ExportManager
import com.linetrans.app.data.SettingsRepository
import com.linetrans.app.data.StorageManager
import com.linetrans.app.model.TranslationDoc
import com.linetrans.app.model.TranslationUnit
import com.linetrans.app.model.UnitMode
import com.linetrans.app.util.TextParser
import kotlinx.coroutines.launch

private data class PendingImport(val name: String, val text: String, val hasTranslated: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenDoc: (String) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var expandedId by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    var showExportFor by remember { mutableStateOf<TranslationDoc?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var editFolderFor by remember { mutableStateOf<TranslationDoc?>(null) }

    val textPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handlePickedText(context, uri) { p -> pendingImport = p }
    }

    fun openTextPicker() {
        textPicker.launch(arrayOf("text/plain"))
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            SettingsRepository.update { it.copy(storageDirUri = uri.toString()) }
            openTextPicker()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(16.dp)) {
                    Text("逐行翻译", style = MaterialTheme.typography.titleLarge)
                    val totalTranslated = DocRepository.docs.sumOf { it.translatedCount }
                    val totalUnits = DocRepository.docs.sumOf { it.totalCount }
                    Text("今日目标：已完成 " + totalTranslated + " / " + totalUnits + " 句", style = MaterialTheme.typography.bodySmall)
                }
                Divider()
                Text("文件夹", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { selectedFolder = null }
                        .background(if (selectedFolder == null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("全部文档 (" + DocRepository.docs.size + ")", style = MaterialTheme.typography.bodyMedium)
                }
                DocRepository.docs.map { it.folder }.distinct().forEach { folder ->
                    val count = DocRepository.docs.count { it.folder == folder }
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { selectedFolder = folder }
                            .background(if (selectedFolder == folder) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(folder + " (" + count + ")", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Divider()
                Row(
                    Modifier.padding(16.dp).fillMaxWidth().clickable { onOpenSettings() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("设置")
                }
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().clickable { onOpenSettings() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Computer, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Web 终端服务")
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("今日翻译") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "菜单")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (SettingsRepository.settings.storageDirUri.isBlank()) {
                                folderPicker.launch(null)
                            } else {
                                openTextPicker()
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "添加文本")
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                val filtered = DocRepository.docs.filter { selectedFolder == null || it.folder == selectedFolder }
                if (filtered.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { TotalStatsCard() }
                        items(filtered, key = { it.id }) { doc ->
                            DocCard(
                                doc = doc,
                                expanded = expandedId == doc.id,
                                onToggle = { expandedId = if (expandedId == doc.id) null else doc.id },
                                onTranslate = { onOpenDoc(doc.id) },
                                onView = { onOpenDoc(doc.id) },
                                onExport = { showExportFor = doc },
                                onEditFolder = { editFolderFor = doc },
                                onDelete = { DocRepository.delete(doc.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (pendingImport != null) {
        ImportDialog(pendingImport!!) { skip ->
            val p = pendingImport!!
            val doc = createDocFromText(p.name, p.text, skip)
            DocRepository.save(doc)
            pendingImport = null
            notice = "已导入：" + doc.name
        }
    }

    if (showExportFor != null) {
        ExportDialog(doc = showExportFor!!, onDismiss = { showExportFor = null }) { mode ->
            runCatching { ExportManager.export(context, showExportFor!!, mode) }
                .onSuccess { notice = "导出成功" }
                .onFailure { notice = it.message }
        }
    }

    if (editFolderFor != null) {
        FolderDialog(
            doc = editFolderFor!!,
            onDismiss = { editFolderFor = null }
        ) { newFolder ->
            val d = editFolderFor!!
            d.folder = newFolder.ifBlank { TranslationDoc.DEFAULT_FOLDER }
            DocRepository.save(d)
            editFolderFor = null
            notice = "已移动到文件夹：" + d.folder
        }
    }

    if (notice != null) {
        AlertDialog(
            onDismissRequest = { notice = null },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("确定") } },
            title = { Text("提示") },
            text = { Text(notice!!) }
        )
    }
}

@Composable
private fun TotalStatsCard() {
    val docs = DocRepository.docs
    val totalTranslated = docs.sumOf { it.translatedCount }
    val totalUnits = docs.sumOf { it.totalCount }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val goal = SettingsRepository.settings.dailyGoal
            Text("今日翻译" + if (goal > 0) " · 目标 " + goal + " 句" else "", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "文档 " + docs.size + " 篇 · 已完成 " + totalTranslated + " / " + totalUnits + " 句",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (totalUnits == 0) 0f else totalTranslated.toFloat() / totalUnits },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有文本，点击右上角 + 添加", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Text("支持导入 .txt 源文本", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DocCard(
    doc: TranslationDoc,
    expanded: Boolean,
    onToggle: () -> Unit,
    onTranslate: () -> Unit,
    onView: () -> Unit,
    onExport: () -> Unit,
    onEditFolder: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth().clickable { onToggle() }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(doc.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { doc.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("已完成 " + doc.translatedCount + " / " + doc.totalCount + " 句", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { onToggle() }) {
                    Icon(Icons.Default.ExpandMore, contentDescription = "展开",
                        modifier = Modifier.let { m -> if (expanded) m else m })
                }
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Division()
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onTranslate) { Text("翻译") }
                    OutlinedButton(onClick = onView) { Icon(Icons.Default.Visibility, null); Spacer(Modifier.width(6.dp)); Text("查看") }
                    OutlinedButton(onClick = onExport) { Text("导出") }
                    OutlinedButton(onClick = onEditFolder) { Text("文件夹") }
                    TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text("删除") }
                }
            }
        }
    }
}

@Composable
private fun Division() {
    Divider()
}

@Composable
private fun ImportDialog(pending: PendingImport, onConfirm: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onConfirm(true) },
        title = { Text("导入 " + pending.name) },
        text = {
            if (pending.hasTranslated) {
                Text("检测到该文本中已存在翻译内容，是否略过已翻译部分？")
            } else {
                Text("将导入该文本作为源文本，请为每一行填写翻译。")
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(true) }) { Text("继续") }
        },
        dismissButton = {
            if (pending.hasTranslated) {
                TextButton(onClick = { onConfirm(false) }) { Text("全部重新翻译") }
            }
        }
    )
}

@Composable
private fun FolderDialog(doc: TranslationDoc, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(doc.folder) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到文件夹") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("文件夹名称") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("可在此新建文件夹，或填写已有文件夹名称。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ExportDialog(doc: TranslationDoc, onDismiss: () -> Unit, onExport: (ExportManager.Mode) -> Unit) {
    var selected by remember { mutableStateOf(ExportManager.Mode.TRANSLATED_ONLY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出 " + doc.name) },
        text = {
            Column {
                ExportManager.Mode.entries.forEach { mode ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = mode }
                        .padding(vertical = 8.dp)) {
                        Text(if (selected == mode) "●  " else "○  " + mode.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onExport(selected) }) { Text("导出") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun handlePickedText(context: Context, uri: Uri, onResult: (PendingImport) -> Unit) {
    runCatching {
        val name = StorageManager.displayName(context, uri)
        val text = StorageManager.readText(context, uri)
        val hasTranslated = text.lines().any { TextParser.splitSourceTranslation(it) != null }
        PendingImport(name, text, hasTranslated)
    }.onSuccess { onResult(it) }
}

private fun createDocFromText(name: String, text: String, skipTranslated: Boolean): TranslationDoc {
    val id = DocRepository.newId()
    val lines = TextParser.parse(text, UnitMode.LINE)
    val units = lines.map { line ->
        if (skipTranslated) {
            val pair = TextParser.splitSourceTranslation(line.source)
            if (pair != null) TranslationUnit(pair.first, pair.second) else line
        } else {
            line
        }
    }.toMutableList()
    return TranslationDoc(
        id = id,
        name = name.removeSuffix(".txt"),
        units = units,
        unitMode = UnitMode.LINE,
        sourceText = text
    )
}
