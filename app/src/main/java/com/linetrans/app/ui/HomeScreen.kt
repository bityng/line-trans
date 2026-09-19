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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private data class PendingImport(
    val name: String,
    val text: String,
    val hasTranslated: Boolean
)

private enum class DocFilter(val label: String) {
    ALL("全部"),
    DOING("进行中"),
    DONE("已完成")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(onOpenDoc: (String, Int) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var expandedId by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    var showExportFor by remember { mutableStateOf<TranslationDoc?>(null) }
    var folderDialogFor by remember { mutableStateOf<TranslationDoc?>(null) }
    var renameFor by remember { mutableStateOf<TranslationDoc?>(null) }
    var deleteFor by remember { mutableStateOf<TranslationDoc?>(null) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(DocFilter.ALL) }
    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var addMenu by remember { mutableStateOf(false) }

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    val textPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val name = StorageManager.displayName(context, uri)
            val text = StorageManager.readText(context, uri)
            if (text.isBlank()) throw IllegalStateException("文件内容为空")
            val hasTranslated = text.lines().any { TextParser.splitSourceTranslation(it) != null }
            PendingImport(name, text, hasTranslated)
        }.onSuccess { pendingImport = it }
            .onFailure { notify("导入失败：" + (it.message ?: "无法读取文件")) }
    }

    fun openTextPicker() = textPicker.launch(arrayOf("text/*"))

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            SettingsRepository.update { it.copy(storageDirUri = uri.toString()) }
            notify("已设置数据文件夹")
            openTextPicker()
        }
    }

    fun startImport() {
        if (SettingsRepository.settings.storageDirUri.isBlank()) {
            folderPicker.launch(null)
        } else {
            openTextPicker()
        }
    }

    fun importFromClipboard() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = cm.primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context).toString() else ""
        if (text.isBlank()) {
            notify("剪贴板中没有文本")
            return
        }
        val hasTranslated = text.lines().any { TextParser.splitSourceTranslation(it) != null }
        pendingImport = PendingImport("剪贴板文本", text, hasTranslated)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Column(Modifier.padding(20.dp)) {
                            Text("逐行翻译", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            val goal = SettingsRepository.settings.dailyGoal
                            val done = SettingsRepository.dailyCount()
                            Text(
                                if (goal > 0) "今日进度 $done / $goal 句" else "今日已完成 $done 句",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        HorizontalDivider()
                    }
                    item {
                        Text(
                            "文件夹",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
                        )
                    }
                    item {
                        DrawerRow(
                            icon = Icons.Default.Folder,
                            title = "全部文档",
                            count = DocRepository.docs.size,
                            selected = selectedFolder == null,
                            onClick = {
                                selectedFolder = null
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                    items(DocRepository.folders(), key = { "folder-" + it }) { folder ->
                        DrawerRow(
                            icon = Icons.Default.Folder,
                            title = folder,
                            count = DocRepository.docs.count { it.folder == folder },
                            selected = selectedFolder == folder,
                            onClick = {
                                selectedFolder = folder
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        DrawerRow(
                            icon = Icons.Default.Settings,
                            title = "设置",
                            count = null,
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onOpenSettings()
                            }
                        )
                        DrawerRow(
                            icon = Icons.Default.Computer,
                            title = "Web 终端服务",
                            count = null,
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onOpenSettings()
                            }
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (searchActive) {
                    TopAppBar(
                        title = {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("搜索文档名称") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                searchActive = false
                                query = ""
                            }) { Icon(Icons.Default.Close, contentDescription = "关闭搜索") }
                        }
                    )
                } else {
                    TopAppBar(
                        title = {
                            Column {
                                Text("逐行翻译", style = MaterialTheme.typography.titleMedium)
                                val goal = SettingsRepository.settings.dailyGoal
                                val done = SettingsRepository.dailyCount()
                                Text(
                                    if (goal > 0) "今日 $done / $goal 句" else "今日已完成 $done 句",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "菜单")
                            }
                        },
                        actions = {
                            IconButton(onClick = { searchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "搜索")
                            }
                            Box {
                                IconButton(onClick = { addMenu = true }) {
                                    Icon(Icons.Default.Add, contentDescription = "添加文档")
                                }
                                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("导入 txt 文件") },
                                        onClick = { addMenu = false; startImport() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("从剪贴板新建") },
                                        onClick = { addMenu = false; importFromClipboard() }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        ) { padding ->
            val filtered = DocRepository.docs
                .filter { selectedFolder == null || it.folder == selectedFolder }
                .filter {
                    when (filter) {
                        DocFilter.ALL -> true
                        DocFilter.DOING -> !it.isFinished
                        DocFilter.DONE -> it.isFinished
                    }
                }
                .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                .sortedByDescending { it.updatedAt }

            Box(Modifier.padding(padding).fillMaxSize()) {
                if (DocRepository.docs.isEmpty()) {
                    EmptyState(onAdd = { startImport() })
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { TodayCard(onReset = { SettingsRepository.resetDaily() }) }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DocFilter.entries.forEach { f ->
                                    FilterChip(
                                        selected = filter == f,
                                        onClick = { filter = f },
                                        label = { Text(f.label) },
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                if (selectedFolder != null) {
                                    AssistChip(
                                        onClick = { selectedFolder = null },
                                        label = { Text(selectedFolder + " ×", maxLines = 1) }
                                    )
                                }
                            }
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("没有符合条件的文档", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        items(filtered, key = { it.id }) { doc ->
                            DocCard(
                                doc = doc,
                                expanded = expandedId == doc.id,
                                onToggle = { expandedId = if (expandedId == doc.id) null else doc.id },
                                onContinue = {
                                    onOpenDoc(doc.id, doc.nextUndoneIndex(0) ?: 0)
                                },
                                onView = { onOpenDoc(doc.id, 0) },
                                onExport = { showExportFor = doc },
                                onMoveFolder = { folderDialogFor = doc },
                                onRename = { renameFor = doc },
                                onDelete = { deleteFor = doc }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingImport?.let { pending ->
        ImportDialog(
            pending = pending,
            onDismiss = { pendingImport = null },
            onConfirm = { mode, skipTranslated ->
                val doc = createDocFromText(pending.name, pending.text, mode, skipTranslated)
                DocRepository.save(doc, immediate = true)
                pendingImport = null
                notify("已导入 " + doc.name + "，共 " + doc.totalCount + " " + unitLabel(mode))
            }
        )
    }

    showExportFor?.let { doc ->
        ExportDialog(
            doc = doc,
            onDismiss = { showExportFor = null },
            onExport = { mode ->
                runCatching { ExportManager.export(context, doc, mode) }
                    .onSuccess {
                        showExportFor = null
                        notify("已导出到数据文件夹")
                    }
                    .onFailure { notify("导出失败：" + (it.message ?: "未知错误")) }
            }
        )
    }

    folderDialogFor?.let { doc ->
        FolderDialog(
            doc = doc,
            folders = DocRepository.folders(),
            onDismiss = { folderDialogFor = null },
            onConfirm = { newFolder ->
                doc.folder = newFolder.ifBlank { TranslationDoc.DEFAULT_FOLDER }
                DocRepository.save(doc, immediate = true)
                folderDialogFor = null
                notify("已移动到文件夹：" + doc.folder)
            }
        )
    }

    renameFor?.let { doc ->
        RenameDialog(
            doc = doc,
            onDismiss = { renameFor = null },
            onConfirm = { newName ->
                doc.name = newName
                DocRepository.save(doc, immediate = true)
                renameFor = null
                notify("已重命名为 " + newName)
            }
        )
    }

    deleteFor?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("删除文档") },
            text = { Text("确定要删除「" + doc.name + "」吗？该操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    DocRepository.delete(doc.id)
                    deleteFor = null
                    notify("已删除 " + doc.name)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun DrawerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.width(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (count != null) {
            Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TodayCard(onReset: () -> Unit) {
    val docs = DocRepository.docs
    val goal = SettingsRepository.settings.dailyGoal
    val done = SettingsRepository.dailyCount()
    val totalTranslated = docs.sumOf { it.translatedCount }
    val totalUnits = docs.sumOf { it.totalCount }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("今日翻译", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (done > 0) {
                    TextButton(onClick = onReset) { Text("重置今日") }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (goal > 0) "已完成 $done / $goal 句" else "已完成 $done 句",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = {
                    if (goal <= 0) 0f else (done.toFloat() / goal).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                "全部文档 " + docs.size + " 篇 · 累计完成 " + totalTranslated + " / " + totalUnits + " 句",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.width(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text("还没有导入文本", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "支持 .txt 纯文本，导入后可按行或按句对照翻译",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("导入文本")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocCard(
    doc: TranslationDoc,
    expanded: Boolean,
    onToggle: () -> Unit,
    onContinue: () -> Unit,
    onView: () -> Unit,
    onExport: () -> Unit,
    onMoveFolder: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth().clickable { onToggle() }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        doc.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        doc.folder + " · " + formatTime(doc.updatedAt) +
                            if (doc.unitMode == UnitMode.SENTENCE) " · 逐句" else " · 逐行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    (doc.progress * 100).roundToInt().toString() + "%",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (doc.isFinished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onToggle) {
                    Icon(Icons.Default.ExpandMore, contentDescription = if (expanded) "收起" else "展开")
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { doc.progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                "已完成 " + doc.translatedCount + " / " + doc.totalCount + " " + unitLabel(doc.unitMode) +
                    if (doc.remainingCount > 0) " · 剩 " + doc.remainingCount + " " + unitLabel(doc.unitMode) else " · 全部完成",
                style = MaterialTheme.typography.bodySmall
            )
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onContinue) {
                        Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (doc.isFinished) "重新翻译" else if (doc.translatedCount > 0) "继续翻译" else "开始翻译")
                    }
                    OutlinedButton(onClick = onView) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("查看")
                    }
                    OutlinedButton(onClick = onExport) { Text("导出") }
                    OutlinedButton(onClick = onMoveFolder) {
                        Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("文件夹")
                    }
                    OutlinedButton(onClick = onRename) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重命名")
                    }
                    OutlinedButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.width(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportDialog(
    pending: PendingImport,
    onDismiss: () -> Unit,
    onConfirm: (UnitMode, Boolean) -> Unit
) {
    var mode by remember { mutableStateOf(UnitMode.LINE) }
    var skipTranslated by remember { mutableStateOf(pending.hasTranslated) }
    val count = remember(pending.text, mode) { TextParser.parse(pending.text, mode).size }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入 " + pending.name) },
        text = {
            Column {
                Text("切分方式", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row {
                    FilterChip(
                        selected = mode == UnitMode.LINE,
                        onClick = { mode = UnitMode.LINE },
                        label = { Text("逐行") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = mode == UnitMode.SENTENCE,
                        onClick = { mode = UnitMode.SENTENCE },
                        label = { Text("逐句") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "共 " + count + " " + unitLabel(mode) + "需要翻译",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (pending.hasTranslated) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("略过已有翻译", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "文本中检测到「原文 ⇥ 译文」格式",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = skipTranslated, onCheckedChange = { skipTranslated = it })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(mode, skipTranslated) }) { Text("导入") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderDialog(
    doc: TranslationDoc,
    folders: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(doc.folder) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到文件夹") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("文件夹名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (folders.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("已有文件夹", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        folders.forEach { f ->
                            AssistChip(
                                onClick = { name = f },
                                label = { Text(f, maxLines = 1) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "填写新名称即可新建文件夹。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun RenameDialog(doc: TranslationDoc, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(doc.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名文档") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("文档名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim()
                if (n.isNotEmpty()) onConfirm(n)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ExportDialog(
    doc: TranslationDoc,
    onDismiss: () -> Unit,
    onExport: (ExportManager.Mode) -> Unit
) {
    var selected by remember { mutableStateOf(ExportManager.Mode.TRANSLATED_ONLY) }
    val translatedOnly = ExportManager.buildExportText(doc, ExportManager.Mode.TRANSLATED_ONLY)
    val bilingual = ExportManager.buildExportText(doc, ExportManager.Mode.TRANSLATED_REPLACES_SOURCE)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出 " + doc.name) },
        text = {
            Column {
                ExportManager.Mode.entries.forEach { mode ->
                    val selectedMode = selected == mode
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = mode }
                            .padding(vertical = 8.dp)
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = selectedMode,
                            onClick = { selected = mode }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                            val preview = if (mode == ExportManager.Mode.TRANSLATED_ONLY) translatedOnly else bilingual
                            Text(
                                preview.lines().size.toString() + " 行 · " +
                                    (if (preview.isBlank()) "内容为空" else preview.lineSequence().first().take(28)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val dir = SettingsRepository.settings.storageDirUri
                if (dir.isBlank()) {
                    Text(
                        "尚未设置数据文件夹，导出前请先在设置中选择。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        "将保存到已设置的数据文件夹",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onExport(selected) }) { Text("导出") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> (diff / 60_000L).toString() + " 分钟前"
        diff < 86_400_000L -> (diff / 3_600_000L).toString() + " 小时前"
        diff < 604_800_000L -> (diff / 86_400_000L).toString() + " 天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun createDocFromText(
    name: String,
    text: String,
    mode: UnitMode,
    skipTranslated: Boolean
): TranslationDoc {
    val units = TextParser.parse(text, mode).map { line ->
        if (skipTranslated) {
            val pair = TextParser.splitSourceTranslation(line.source)
            if (pair != null) TranslationUnit(pair.first, pair.second) else line
        } else {
            line
        }
    }.toMutableList()
    return TranslationDoc(
        id = DocRepository.newId(),
        name = name.removeSuffix(".txt").ifBlank { "未命名文档" },
        units = units,
        unitMode = mode,
        sourceText = text
    )
}
