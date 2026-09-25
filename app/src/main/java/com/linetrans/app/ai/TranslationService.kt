package com.linetrans.app.ai

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.linetrans.app.data.SettingsRepository
import com.linetrans.app.model.AppSettings
import com.linetrans.app.model.ModelConfig
import com.linetrans.app.model.ProviderConfig
import com.linetrans.app.model.ProviderType
import com.linetrans.app.model.TranslationUnit
import com.linetrans.app.model.UnitMode
import com.linetrans.app.util.CostCalculator
import com.linetrans.app.util.TextParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * 调用 AI 翻译服务，支持 OpenAI 兼容 / Anthropic / 自定义 API。
 */
class TranslationService(private val context: Context) {

    data class Result(
        val text: String,
        val promptTokens: Int,
        val completionTokens: Int,
        /** 命中的缓存输入 token（部分服务商返回）。 */
        val cachedTokens: Int = 0
    )

    /** 单次会话内的用量累计。 */
    data class UsageState(
        var inputMissTokens: Int = 0,
        var inputHitTokens: Int = 0,
        var outputTokens: Int = 0,
        var calls: Int = 0,
        var totalCost: Double = 0.0
    ) {
        val inputTokens: Int get() = inputMissTokens + inputHitTokens
        val totalTokens: Int get() = inputTokens + outputTokens

        fun plus(result: Result, cost: Double): UsageState = UsageState(
            inputMissTokens = inputMissTokens + (result.promptTokens - result.cachedTokens).coerceAtLeast(0),
            inputHitTokens = inputHitTokens + result.cachedTokens.coerceAtLeast(0),
            outputTokens = outputTokens + result.completionTokens,
            calls = calls + 1,
            totalCost = totalCost + cost
        )
    }

    private val gson: Gson = GsonBuilder().create()

    /** 按“超时 + 代理”缓存客户端，配置变了自动重建。 */
    private var clientKey: String? = null
    private var client: OkHttpClient = buildClient(AppSettings())

    private fun buildClient(settings: AppSettings): OkHttpClient {
        val timeout = settings.requestTimeoutSec.coerceIn(10, 600).toLong()
        val builder = OkHttpClient.Builder()
            .connectTimeout(timeout.coerceAtMost(60), TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
        proxyOf(settings)?.let { builder.proxy(it) }
        return builder.build()
    }

    private fun proxyOf(settings: AppSettings): Proxy? {
        val raw = settings.proxyUrl.trim()
        if (raw.isEmpty()) return null
        return runCatching {
            val normalized = if (raw.contains("://")) raw else "http://$raw"
            val uri = java.net.URI(normalized)
            val port = if (uri.port > 0) uri.port else 8080
            Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, port))
        }.getOrNull()
    }

    private fun clientFor(settings: AppSettings): OkHttpClient {
        val key = settings.requestTimeoutSec.toString() + "|" + settings.proxyUrl
        if (key != clientKey) {
            client = buildClient(settings)
            clientKey = key
        }
        return client
    }

    suspend fun translate(
        settings: AppSettings,
        docName: String,
        unit: TranslationUnit,
        mode: UnitMode,
        index: Int,
        total: Int,
        previousUnits: List<TranslationUnit>,
        recordUsage: Boolean = true
    ): Result = withContext(Dispatchers.IO) {
        val provider = settings.activeProvider
            ?: throw IllegalStateException("请先在设置中添加并选择 API 提供商")
        val model = settings.activeModel
            ?: throw IllegalStateException("请先选择翻译模型")
        if (provider.baseUrl.isBlank()) throw IllegalStateException("请先在设置中填写 Base URL")
        if (unit.source.isBlank()) throw IllegalStateException("原文为空，无法翻译")

        val system = PromptBuilder.buildSystemPrompt(settings, unit.source, docName, mode)
        val user = PromptBuilder.buildUserPrompt(settings, docName, unit, mode, index, total, previousUnits)

        val result = when (provider.type) {
            ProviderType.ANTHROPIC -> callAnthropic(settings, provider, model, system, user)
            else -> callOpenAiCompatible(settings, provider, model, system, user)
        }
        if (recordUsage) {
            val cost = CostCalculator.costFor(model, result.promptTokens, result.completionTokens)
            SettingsRepository.recordUsage(
                modelId = model.id,
                modelName = model.name,
                inputTokens = (result.promptTokens - result.cachedTokens).coerceAtLeast(0),
                cachedTokens = result.cachedTokens,
                outputTokens = result.completionTokens,
                cost = cost
            )
        }
        result
    }

    /** 设置页“测试连接”：发一条极短的请求，返回耗时描述。 */
    /** 通用对话调用（供划词词典等复用），返回纯文本。 */
    suspend fun rawChat(settings: AppSettings, system: String, user: String): String = withContext(Dispatchers.IO) {
        val provider = settings.activeProvider
            ?: throw IllegalStateException("请先在设置中添加并选择 API 提供商")
        val model = settings.activeModel ?: throw IllegalStateException("请先选择模型")
        if (provider.baseUrl.isBlank()) throw IllegalStateException("请先填写 Base URL")
        val result = when (provider.type) {
            ProviderType.ANTHROPIC -> callAnthropic(settings, provider, model, system, user)
            else -> callOpenAiCompatible(settings, provider, model, system, user)
        }
        result.text
    }

    suspend fun testConnection(settings: AppSettings): Result2 = withContext(Dispatchers.IO) {
        val provider = settings.activeProvider ?: return@withContext Result2(false, "未选择 API 提供商", 0)
        val model = settings.activeModel ?: return@withContext Result2(false, "未选择模型", 0)
        if (provider.baseUrl.isBlank()) return@withContext Result2(false, "未填写 Base URL", 0)
        val started = System.currentTimeMillis()
        return@withContext try {
            val system = "You are a translation engine. Output only the translation."
            val user = "Translate into " + settings.targetLang + ": hello"
            val probeSettings = settings
            when (provider.type) {
                ProviderType.ANTHROPIC -> callAnthropic(probeSettings, provider, model, system, user)
                else -> callOpenAiCompatible(probeSettings, provider, model, system, user)
            }
            val ms = System.currentTimeMillis() - started
            Result2(true, "连接正常（" + model.name + "，用时 " + ms + " ms）", ms)
        } catch (e: Exception) {
            Result2(false, "连接失败：" + (e.message ?: "未知错误"), System.currentTimeMillis() - started)
        }
    }

    data class Result2(val ok: Boolean, val message: String, val elapsedMs: Long)

    private fun resolveUrl(base: String, path: String): String {
        val b = base.trim().trimEnd('/')
        return if (b.endsWith("/v1")) b + path else b + "/v1" + path
    }

    private fun headers(settings: AppSettings) = settings.userAgent.trim()

    private fun callOpenAiCompatible(
        settings: AppSettings,
        provider: ProviderConfig,
        model: ModelConfig,
        system: String,
        user: String
    ): Result {
        val url = resolveUrl(provider.baseUrl, "/chat/completions")
        val body = LinkedHashMap<String, Any?>()
        body["model"] = model.name
        body["messages"] = listOf(
            mapOf("role" to "system", "content" to system),
            mapOf("role" to "user", "content" to user)
        )
        // 推理类模型不支持 temperature / max_tokens，避免请求被拒
        if (!model.isReasoningModel()) {
            if (model.temperature > 0) body["temperature"] = model.temperature
            if (model.topP in 0.01..0.99) body["top_p"] = model.topP
            if (model.maxTokens > 0) body["max_tokens"] = model.maxTokens
        }

        val builder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + provider.apiKey)
            .header("Content-Type", "application/json")
        headers(settings).takeIf { it.isNotEmpty() }?.let { builder.header("User-Agent", it) }
        val req = builder.post(gson.toJson(body).toRequestBody(JSON)).build()

        val text = execute(settings, req)
        val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            ?: throw IllegalStateException("接口返回内容无法解析：" + text.take(300))
        val first = obj.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
            ?: throw IllegalStateException(errorMessage(obj) ?: "接口返回中没有 choices 字段")
        val content = first.getAsJsonObject("message")?.get("content")
        val translated = when {
            content == null || content.isJsonNull -> ""
            content.isJsonArray -> content.asJsonArray.joinToString("") { el ->
                runCatching { el.asJsonObject.get("text").asString }.getOrDefault("")
            }
            else -> content.asString
        }
        val usage = obj.get("usage")?.takeIf { it.isJsonObject }?.asJsonObject
        val pt = usage.intOrZero("prompt_tokens")
        val ct = usage.intOrZero("completion_tokens")
        val cached = usage?.get("prompt_tokens_details")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            .intOrZero("cached_tokens")
        val providerError = obj.get("error")
        if (translated.isBlank() && providerError != null) {
            throw IllegalStateException(errorMessage(obj) ?: "接口返回为空")
        }
        return Result(translated.trim(), pt, ct, cached)
    }

    private fun callAnthropic(
        settings: AppSettings,
        provider: ProviderConfig,
        model: ModelConfig,
        system: String,
        user: String
    ): Result {
        val url = resolveUrl(provider.baseUrl, "/messages")
        val body = LinkedHashMap<String, Any?>()
        body["model"] = model.name
        body["system"] = system
        body["max_tokens"] = if (model.maxTokens > 0) model.maxTokens else 4096
        if (model.temperature > 0) body["temperature"] = model.temperature
        if (model.topP in 0.01..0.99) body["top_p"] = model.topP
        body["messages"] = listOf(mapOf("role" to "user", "content" to user))

        val builder = Request.Builder()
            .url(url)
            .header("x-api-key", provider.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
        headers(settings).takeIf { it.isNotEmpty() }?.let { builder.header("User-Agent", it) }
        val req = builder.post(gson.toJson(body).toRequestBody(JSON)).build()

        val text = execute(settings, req)
        val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            ?: throw IllegalStateException("接口返回内容无法解析：" + text.take(300))
        val blocks = obj.getAsJsonArray("content")
            ?: throw IllegalStateException(errorMessage(obj) ?: "接口返回中没有 content 字段")
        val translated = blocks.mapNotNull { el ->
            runCatching {
                val o = el.asJsonObject
                if (o.get("type")?.asString == "text") o.get("text")?.asString else null
            }.getOrNull()
        }.joinToString("")
        val usage = obj.get("usage")?.takeIf { it.isJsonObject }?.asJsonObject
        val pt = usage.intOrZero("input_tokens")
        val ct = usage.intOrZero("output_tokens")
        val cached = usage.intOrZero("cache_read_input_tokens")
        return Result(translated.trim(), pt, ct, cached)
    }

    /** 发送请求，遇到网络错误或 429 / 5xx 时按设置重试。 */
    private fun execute(settings: AppSettings, req: Request): String {
        val retries = settings.maxRetries.coerceIn(0, 5)
        var lastError: Exception? = null
        for (attempt in 0..retries) {
            try {
                clientFor(settings).newCall(req).execute().use { resp ->
                    val text = resp.body?.string() ?: ""
                    if (resp.isSuccessful) return text
                    val retryable = resp.code == 429 || resp.code >= 500
                    val message = "API 错误 (" + resp.code + ")：" + extractError(text)
                    if (!retryable) throw ApiException(message)
                    lastError = ApiException(message)
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                lastError = e
            }
            if (attempt < retries) Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
        }
        throw lastError ?: IllegalStateException("请求失败")
    }

    private fun extractError(body: String): String {
        val parsed = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
        return errorMessage(parsed) ?: body.take(300)
    }

    private fun errorMessage(obj: JsonObject?): String? {
        val err: JsonElement = obj?.get("error") ?: return null
        return runCatching {
            if (err.isJsonObject) err.asJsonObject.get("message")?.asString else err.asString
        }.getOrNull()
    }

    /** 安全地读取一个整数统计字段，字段缺失或类型不符时返回 0。 */
    private fun JsonObject?.intOrZero(key: String): Int =
        runCatching { this?.get(key)?.takeIf { it.isJsonPrimitive }?.asInt ?: 0 }.getOrDefault(0)

    private fun ModelConfig.isReasoningModel(): Boolean {
        val n = name.lowercase()
        return n.startsWith("o1") || n.startsWith("o3") || n.startsWith("o4") ||
            n.contains("gpt-5") || n.contains("thinking")
    }

    private class ApiException(message: String) : IllegalStateException(message)

    companion object {
        private const val RETRY_DELAY_MS = 1500L
        private val JSON = "application/json".toMediaType()
    }
}

/** 系统提示词与用户提示词构建。 */
object PromptBuilder {

    fun buildSystemPrompt(settings: AppSettings, sourceText: String, docName: String, mode: UnitMode): String {
        val sourceLang = if (settings.detectLanguage) TextParser.detectLanguage(sourceText) else settings.sourceLang
        val template = settings.effectivePrompt
        val glossaryBlock = glossaryBlock(settings)

        var prompt = template
            .replace("{sourceLang}", if (sourceLang == "auto") "原语言" else sourceLang)
            .replace("{targetLang}", settings.targetLang.ifBlank { "zh-CN" })
            .replace("{docName}", docName)
            .replace("{mode}", if (mode == UnitMode.SENTENCE) "逐句" else "逐行")

        prompt = if (template.contains("{glossary}")) {
            prompt.replace("{glossary}", glossaryBlock)
        } else if (glossaryBlock.isNotEmpty()) {
            prompt + "\n" + glossaryBlock
        } else {
            prompt
        }

        if (mode == UnitMode.SENTENCE) {
            prompt += "\n当前按句翻译，请保证译文是完整通顺的句子。"
        }
        return prompt
    }

    fun buildUserPrompt(
        settings: AppSettings,
        docName: String,
        unit: TranslationUnit,
        mode: UnitMode,
        index: Int,
        total: Int,
        previousUnits: List<TranslationUnit>
    ): String = buildString {
        append("文档：")
        append(docName)
        append("  |  当前模式：")
        append(if (mode == UnitMode.SENTENCE) "逐句" else "逐行")
        append("  |  当前进度：")
        append(index + 1)
        append("/")
        append(total)
        val contextSize = settings.contextUnits.coerceIn(0, 10)
        val previous = if (contextSize == 0) emptyList()
        else previousUnits.filter { it.translation.isNotBlank() }.takeLast(contextSize)
        if (previous.isNotEmpty()) {
            append("  |  前文参考：")
            previous.forEach {
                append("【原文：")
                append(it.source)
                append(" → 译文：")
                append(it.translation)
                append("】")
            }
        }
        append("  |  待翻译原文：")
        append(unit.source)
    }

    private fun glossaryBlock(settings: AppSettings): String {
        val entries = settings.glossaryEntries
        if (entries.isEmpty()) return ""
        return buildString {
            append("术语表（必须严格使用以下译法）：")
            entries.forEach { (from, to) ->
                append("\n- ")
                append(from)
                append(" → ")
                append(to)
            }
        }
    }
}
