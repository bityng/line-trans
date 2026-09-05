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
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 调用 AI 翻译服务，支持 OpenAI 兼容 / Anthropic / 自定义 API。
 */
class TranslationService(private val context: Context) {

    data class Result(val text: String, val promptTokens: Int, val completionTokens: Int)

    data class UsageState(
        var inputTokens: Int = 0,
        var outputTokens: Int = 0,
        var inputHit: Boolean = false,
        var totalCost: Double = 0.0
    )

    private val gson: Gson = GsonBuilder().create()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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

        val target = settings.targetLang
        val sourceLang = if (settings.detectLanguage) TextParser.detectLanguage(unit.source) else settings.sourceLang

        val system = buildString {
            append("你是专业翻译。请把用户提供的内容从 ")
            append(sourceLang)
            append(" 翻译成 ")
            append(target)
            append("。只输出译文，不要解释，不要添加多余内容，保持原有格式与换行。")
        }

        val previous = previousUnits.filter { it.translation.isNotBlank() }.takeLast(3)

        val user = buildString {
            append("文档：")
            append(docName)
            append("  |  当前模式：")
            append(if (mode == UnitMode.SENTENCE) "逐句" else "逐行")
            append("  |  当前进度：")
            append(index + 1)
            append("/")
            append(total)
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

        when (provider.type) {
            ProviderType.ANTHROPIC -> callAnthropic(provider, model, system, user)
            else -> callOpenAiCompatible(provider, model, system, user)
        }
    }

    private fun resolveUrl(base: String, path: String): String {
        val b = base.trimEnd('/')
        return if (b.endsWith("/v1")) b + path else b + "/v1" + path
    }

    private fun callOpenAiCompatible(
        provider: ProviderConfig,
        model: ModelConfig,
        system: String,
        user: String
    ): Result {
        val url = resolveUrl(provider.baseUrl, "/chat/completions")
        val body = mapOf(
            "model" to model.name,
            "temperature" to 0.2,
            "messages" to listOf(
                mapOf("role" to "system", "content" to system),
                mapOf("role" to "user", "content" to user)
            )
        )
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + provider.apiKey)
            .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IllegalStateException("API 错误 (" + resp.code + "): " + text.take(300))
            val obj = JsonParser.parseString(text).asJsonObject
            val message = obj.getAsJsonArray("choices")[0].asJsonObject.getAsJsonObject("message")
            val content = message.get("content").asString
            val usage = obj.getAsJsonObject("usage")
            val pt = usage.get("prompt_tokens")?.asInt ?: 0
            val ct = usage.get("completion_tokens")?.asInt ?: 0
            return Result(content.trim(), pt, ct)
        }
    }

    private fun callAnthropic(
        provider: ProviderConfig,
        model: ModelConfig,
        system: String,
        user: String
    ): Result {
        val url = resolveUrl(provider.baseUrl, "/messages")
        val body = mapOf(
            "model" to model.name,
            "system" to system,
            "max_tokens" to 4096,
            "messages" to listOf(mapOf("role" to "user", "content" to user))
        )
        val req = Request.Builder()
            .url(url)
            .header("x-api-key", provider.apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IllegalStateException("API 错误 (" + resp.code + "): " + text.take(300))
            val obj = JsonParser.parseString(text).asJsonObject
            val content = obj.getAsJsonArray("content")[0].asJsonObject.get("text").asString
            val usage = obj.getAsJsonObject("usage")
            val pt = usage.get("input_tokens")?.asInt ?: 0
            val ct = usage.get("output_tokens")?.asInt ?: 0
            return Result(content.trim(), pt, ct)
        }
    }
}
