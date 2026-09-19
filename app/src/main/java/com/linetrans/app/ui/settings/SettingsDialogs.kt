package com.linetrans.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.linetrans.app.model.BillingConfig
import com.linetrans.app.model.ModelConfig
import com.linetrans.app.model.ProviderConfig
import com.linetrans.app.model.ProviderType

val PROVIDER_PRESETS = listOf(
    "OpenAI" to "https://api.openai.com",
    "DeepSeek" to "https://api.deepseek.com",
    "Moonshot" to "https://api.moonshot.cn",
    "智谱 GLM" to "https://open.bigmodel.cn/api/paas",
    "Anthropic" to "https://api.anthropic.com",
    "OpenRouter" to "https://openrouter.ai/api",
    "硅基流动" to "https://api.siliconflow.cn"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderDialog(
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
fun ModelDialog(
    model: ModelConfig?,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double, Int, Double, Double, Double, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf(model?.name ?: "") }
    var inputPrice by remember { mutableStateOf(model?.billing?.inputPrice?.toString() ?: "") }
    var outputPrice by remember { mutableStateOf(model?.billing?.outputPrice?.toString() ?: "") }
    var maxTokens by remember { mutableStateOf((model?.maxTokens ?: 4096).toString()) }
    var temperature by remember { mutableStateOf((model?.temperature ?: 0.2).toString()) }
    var topP by remember { mutableStateOf((model?.topP ?: 1.0).toString()) }
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
                Row {
                    OutlinedTextField(
                        value = temperature,
                        onValueChange = { temperature = it },
                        label = { Text("Temperature") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = topP,
                        onValueChange = { topP = it },
                        label = { Text("Top-P") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
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
                    (temperature.toDoubleOrNull() ?: 0.2).coerceIn(0.0, 2.0),
                    (topP.toDoubleOrNull() ?: 1.0).coerceIn(0.0, 1.0),
                    peakMultiplier.toDoubleOrNull() ?: 1.0,
                    (peakStart.toIntOrNull() ?: 8).coerceIn(0, 23),
                    (peakEnd.toIntOrNull() ?: 22).coerceIn(0, 24)
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
