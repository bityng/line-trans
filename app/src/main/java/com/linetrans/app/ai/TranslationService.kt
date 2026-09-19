package com.linetrans.app.ai

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.linetrans.app.model.AppSettings
import com.linetrans.app.model.ModelConfig
import com.linetrans.app.model.ProviderConfig
import com.linetrans.app.model.ProviderType
import com.linetrans.app.model.TranslationUnit
import com.linetrans.app.model.UnitMode
import com.linetrans.app.util.TextParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
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

    suspend fun translate(
        settings: AppSettings,
        docName: String,
        unit: TranslationUnit,
        mode: UnitMode,
        index: Int,
        total: Int,
        previousUnits: List<TranslationUnit>
    ): Result = withContext(Dispatchers.IO) {
        val provider = settings.activeProvider
            ?: throw IllegalStateException("请先在设置中添加并选择 API 提供商")
        val model = settings.activeModel
            ?: throw IllegalStateException("请先选择翻译模型")
        if (provider.baseUrl.isBlank()) throw IllegalStateException("请先在设置中填写 Base URL")
        if (unit.source.isBlank()) throw IllegalStateException("原文为空，无法翻译")

        val user = buildUserPrompt(settings, docName, unit, mode, index, total, previousUnits)
        val system = buildSystemPrompt(settings, unit, mode)

        when (provider.type) {
            ProviderType.ANTHROPIC -> callAnthropic(provider, model, system, user)
            else -> callOpenAiCompatible(provider, model, system, user)
        }
    }

    private fun buildSystemPrompt(settings: AppSettings, unit: TranslationUnit, mode: UnitMode): String {
        val target = settings.targetLang.ifBlank { "zh-CN" }
        val sourceLang = if (settings.detectLanguage) {
            TextParser.detectLanguage(unit.source)
        } else {
            settings.sourceLang
        }
        return buildString {
            append("你是专业翻译。请把用户提供的内容从 ")
            append(if (sourceLang == "auto") "原语言" else sourceLang)
            append(" 翻译成 ")
            append(target)
            append("。只输出译文，不要解释，不要添加多余内容，保持原有格式与换行。")
            if (mode == UnitMode.SENTENCE) append("当前是按句翻译，请保证译文为完整通顺的句子。")
            val extra = settings.customPrompt.trim()
            if (extra.isNotEmpty()) {
                append("\n附加要求：")
                append(extra)
            }
        }
    }

    private fun buildUserPrompt(
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

    private fun resolveUrl(base: String, path: String): String {
        val b = base.trim().trimEnd('/')
        return if (b.endsWith("/v1")) b + path else b + "/v1" + path
    }

    private fun callOpenAiCompatible(
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
            if (model.maxTokens > 0) body["max_tokens"] = model.maxTokens
        }

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + provider.apiKey)
            .header("Content-Type", "application/json")
            .post(gson.toJson(body).toRequestBody(JSON))
            .build()

        val text = execute(req)
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
        return Result(translated.trim(), pt, ct, cached)
    }

    private fun callAnthropic(
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
        body["messages"] = listOf(mapOf("role" to "user", "content" to user))

        val req = Request.Builder()
            .url(url)
            .header("x-api-key", provider.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(gson.toJson(body).toRequestBody(JSON))
            .build()

        val text = execute(req)
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

    /** 安全地读取一个整数统计字段，字段缺失或类型不符时返回 0。 */
    private fun com.google.gson.JsonObject?.intOrZero(key: String): Int =
        runCatching { this?.get(key)?.takeIf { it.isJsonPrimitive }?.asInt ?: 0 }.getOrDefault(0)

    /** 发送请求，遇到网络错误或 429 / 5xx 时自动重试。 */
    private fun execute(req: Request): String {
        var lastError: Exception? = null
        for (attempt in 0..MAX_RETRY) {
            try {
                client.newCall(req).execute().use { resp ->
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
            if (attempt < MAX_RETRY) Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
        }
        throw lastError ?: IllegalStateException("请求失败")
    }

    private fun extractError(body: String): String {
        val parsed = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
        return errorMessage(parsed) ?: body.take(300)
    }

    private fun errorMessage(obj: com.google.gson.JsonObject?): String? {
        val err = obj?.get("error") ?: return null
        return runCatching {
            if (err.isJsonObject) err.asJsonObject.get("message")?.asString else err.asString
        }.getOrNull()
    }

    private fun ModelConfig.isReasoningModel(): Boolean {
        val n = name.lowercase()
        return n.startsWith("o1") || n.startsWith("o3") || n.startsWith("o4") ||
            n.contains("gpt-5") || n.contains("thinking")
    }

    private class ApiException(message: String) : IllegalStateException(message)

    companion object {
        private const val MAX_RETRY = 2
        private const val RETRY_DELAY_MS = 1500L
        private val JSON = "application/json".toMediaType()

        /** 多个页面共用同一个客户端，复用连接。 */
        private val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
