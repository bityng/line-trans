package com.linetrans.app.ai

import com.google.gson.JsonParser
import com.linetrans.app.model.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 划词释义。默认走牛津词典：
 *  1. [Source.OXFORD_WEB]  牛津学习者词典网页（免密钥）
 *  2. [Source.OXFORD_API]  牛津官方 API（需在设置里填 app_id / app_key）
 *  3. [Source.WIKTIONARY]  免费词典兜底（保证一定能出结果）
 *  4. [Source.AI]          用已配置的模型生成简明释义（可同时给中文解释）
 */
object DictionaryService {

    enum class Source(val id: String, val label: String) {
        OXFORD_WEB("oxford_web", "牛津词典（网页）"),
        OXFORD_API("oxford_api", "牛津词典（API）"),
        WIKTIONARY("wiktionary", "Wiktionary"),
        AI("ai", "AI 释义")
    }

    data class Sense(
        val partOfSpeech: String,
        val definition: String,
        val example: String? = null
    )

    data class Entry(
        val word: String,
        val phonetic: String? = null,
        val senses: List<Sense> = emptyList(),
        val translation: String? = null,
        val source: String = "",
        val url: String = "",
        val error: String? = null
    ) {
        val ok: Boolean get() = senses.isNotEmpty() || !translation.isNullOrBlank()
    }

    private const val OXFORD_WEB = "https://www.oxfordlearnersdictionaries.com/definition/english/"
    private const val OXFORD_API = "https://od-api.oxforddictionaries.com/api/v2/entries/en-gb/"
    private const val WIKTIONARY_API = "https://api.dictionaryapi.dev/api/v2/entries/en/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()

    /** 简单的 LRU 缓存，避免反复请求同一个词。 */
    private val cache = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > 200
    }

    fun cached(word: String): Entry? = synchronized(cache) { cache[word.lowercase()] }

    suspend fun lookup(
        settings: AppSettings,
        word: String,
        aiService: TranslationService? = null,
        withTranslation: Boolean = true
    ): Entry = withContext(Dispatchers.IO) {
        val key = word.lowercase()
        synchronized(cache) { cache[key] }?.let { return@withContext it }

        val order = buildOrder(settings)
        var lastError: String? = null
        for (source in order) {
            val entry = runCatching { fetch(source, settings, word, aiService) }.getOrElse { e ->
                lastError = e.message ?: "请求失败"
                null
            }
            if (entry != null && entry.ok) {
                val finalEntry = if (withTranslation && settings.dictionaryAiExplain &&
                    entry.translation.isNullOrBlank()
                ) {
                    entry.copy(translation = runCatching { aiExplain(settings, word, entry, aiService) }.getOrNull())
                } else entry
                synchronized(cache) { cache[key] = finalEntry }
                return@withContext finalEntry
            }
            if (entry?.error != null) lastError = entry.error
        }
        Entry(word = word, error = lastError ?: "没有查到这个词的释义")
    }

    private fun buildOrder(settings: AppSettings): List<Source> = when (settings.dictionarySource) {
        Source.OXFORD_API.id -> listOf(Source.OXFORD_API, Source.OXFORD_WEB, Source.AI, Source.WIKTIONARY)
        Source.OXFORD_WEB.id -> listOf(Source.OXFORD_WEB, Source.OXFORD_API, Source.AI, Source.WIKTIONARY)
        Source.WIKTIONARY.id -> listOf(Source.WIKTIONARY, Source.OXFORD_WEB, Source.AI)
        Source.AI.id -> listOf(Source.AI, Source.OXFORD_WEB, Source.WIKTIONARY)
        else -> listOf(Source.OXFORD_WEB, Source.OXFORD_API, Source.WIKTIONARY, Source.AI)
    }

    private fun fetch(source: Source, settings: AppSettings, word: String, aiService: TranslationService?): Entry = when (source) {
        Source.OXFORD_WEB -> oxfordWeb(word)
        Source.OXFORD_API -> oxfordApi(settings, word)
        Source.WIKTIONARY -> wiktionary(word)
        Source.AI -> aiEntry(settings, word, aiService)
    }

    // ---------- 牛津词典网页 ----------

    private fun oxfordWeb(word: String): Entry {
        val url = OXFORD_WEB + word.lowercase()
        val html = fetchText(url) ?: throw IllegalStateException("无法访问牛津词典网页")
        if (html.contains("No exact results found") || html.contains("we couldn't find")) {
            return Entry(word = word, source = Source.OXFORD_WEB.label, url = url, error = "牛津词典没有收录这个词")
        }
        val phonetic = Regex("""class="phon"[^>]*>(.*?)<""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)?.let { stripTags(it) }?.trim()
            ?.takeIf { it.isNotEmpty() && it.length < 40 }

        val partsOfSpeech = Regex("""class="pos"[^>]*>(.*?)<""")
            .findAll(html).map { stripTags(it.groupValues[1]).trim() }.filter { it.isNotEmpty() }.toList()

        val defs = Regex("""class="def"[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .map { stripTags(it.groupValues[1]).trim() }
            .filter { it.isNotEmpty() }
            .take(5)
            .toList()

        if (defs.isEmpty()) {
            return Entry(word = word, source = Source.OXFORD_WEB.label, url = url, error = "牛津网页没有解析到释义")
        }
        val senses = defs.mapIndexed { i, def ->
            Sense(partsOfSpeech.getOrElse(i) { partsOfSpeech.firstOrNull() ?: "" }, def)
        }
        return Entry(
            word = word,
            phonetic = phonetic,
            senses = senses,
            source = Source.OXFORD_WEB.label,
            url = url
        )
    }

    // ---------- 牛津官方 API ----------

    private fun oxfordApi(settings: AppSettings, word: String): Entry {
        if (settings.oxfordAppId.isBlank() || settings.oxfordAppKey.isBlank()) {
            throw IllegalStateException("未配置牛津 API 的 app_id / app_key")
        }
        val url = OXFORD_API + word.lowercase() + "?fields=definitions,examples,pronunciations"
        val text = fetchText(url, mapOf("app_id" to settings.oxfordAppId, "app_key" to settings.oxfordAppKey))
            ?: throw IllegalStateException("牛津 API 无响应")
        val root = JsonParser.parseString(text).asJsonObject
        val result = root.getAsJsonArray("results")?.firstOrNull()?.asJsonObject
            ?: return Entry(word = word, source = Source.OXFORD_API.label, url = url, error = "牛津 API 没有收录这个词")
        var phonetic: String? = null
        val senses = mutableListOf<Sense>()
        result.getAsJsonArray("lexicalEntries")?.forEach { le ->
            val lex = le.asJsonObject
            val pos = lex.get("lexicalCategory")?.asJsonObject?.get("text")?.asString ?: ""
            lex.getAsJsonArray("entries")?.forEach { e ->
                val entry = e.asJsonObject
                entry.getAsJsonArray("pronunciations")?.firstOrNull()?.asJsonObject
                    ?.get("phoneticSpelling")?.asString?.let { if (phonetic == null) phonetic = "/$it/" }
                entry.getAsJsonArray("senses")?.forEach { s ->
                    val sense = s.asJsonObject
                    val def = sense.getAsJsonArray("definitions")?.firstOrNull()?.asString
                    val example = sense.getAsJsonArray("examples")?.firstOrNull()?.asJsonObject?.get("text")?.asString
                    if (!def.isNullOrBlank() && senses.size < 6) {
                        senses.add(Sense(pos, def, example))
                    }
                }
            }
        }
        return Entry(
            word = word,
            phonetic = phonetic,
            senses = senses,
            source = Source.OXFORD_API.label,
            url = "https://www.oxfordlearnersdictionaries.com/definition/english/" + word.lowercase(),
            error = if (senses.isEmpty()) "牛津 API 没有返回释义" else null
        )
    }

    // ---------- Wiktionary（免密钥兜底） ----------

    private fun wiktionary(word: String): Entry {
        val url = WIKTIONARY_API + word.lowercase()
        val text = fetchText(url) ?: throw IllegalStateException("Wiktionary 无响应")
        if (!text.trimStart().startsWith("[")) {
            return Entry(word = word, source = Source.WIKTIONARY.label, url = url, error = "没有查到释义")
        }
        val arr = JsonParser.parseString(text).asJsonArray
        val first = arr.firstOrNull()?.asJsonObject ?: return Entry(word = word, error = "没有查到释义")
        val phonetic = first.get("phonetic")?.asString
            ?: first.getAsJsonArray("phonetics")?.firstOrNull { !it.asJsonObject.get("text").isJsonNull }
                ?.asJsonObject?.get("text")?.asString
        val senses = mutableListOf<Sense>()
        first.getAsJsonArray("meanings")?.forEach { m ->
            val meaning = m.asJsonObject
            val pos = meaning.get("partOfSpeech")?.asString ?: ""
            meaning.getAsJsonArray("definitions")?.take(2)?.forEach { d ->
                if (senses.size < 5) {
                    senses.add(Sense(pos, d.asJsonObject.get("definition")?.asString ?: ""))
                }
            }
        }
        return Entry(
            word = word,
            phonetic = phonetic,
            senses = senses.filter { it.definition.isNotBlank() },
            source = Source.WIKTIONARY.label,
            url = "https://en.wiktionary.org/wiki/" + word.lowercase()
        )
    }

    // ---------- AI 释义 ----------

    private fun aiEntry(settings: AppSettings, word: String, service: TranslationService?): Entry {
        if (settings.activeProvider == null) throw IllegalStateException("未配置 AI 模型")
        if (service == null) throw IllegalStateException("AI 服务不可用")
        val text = kotlinx.coroutines.runBlocking {
            service.rawChat(
                settings = settings,
                system = "你是英汉词典。只输出词条内容，不要寒暄。",
                user = "请给出单词 “$word” 的简明词典释义：" +
                    "第一行给出音标（没有就留空），随后每行一条 “词性: 中文释义（英文原释义）”，最多 3 条。"
            )
        }.trim()
        if (text.isBlank()) throw IllegalStateException("AI 没有返回内容")
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val phonetic = lines.firstOrNull()?.takeIf { it.startsWith("/") }
        val defs = lines.filter { it != phonetic }.take(4)
        return Entry(
            word = word,
            phonetic = phonetic,
            senses = defs.map { line ->
                val idx = line.indexOfFirst { it == ':' || it == '：' }
                if (idx > 0 && idx < 12) Sense(line.substring(0, idx).trim(), line.substring(idx + 1).trim())
                else Sense("", line)
            },
            source = Source.AI.label,
            url = "https://www.oxfordlearnersdictionaries.com/definition/english/" + word.lowercase()
        )
    }

    private fun aiExplain(settings: AppSettings, word: String, entry: Entry, service: TranslationService?): String? {
        if (settings.activeProvider == null) return null
        if (service == null) return null
        val defs = entry.senses.take(3).joinToString("; ") { it.definition }
        val text = kotlinx.coroutines.runBlocking {
            service.rawChat(
                settings = settings,
                system = "你是英汉词典助手，只输出一行简短中文释义，不要解释。",
                user = "单词：$word\n英文释义：$defs\n请给出不超过 20 字的中文释义。"
            )
        }.trim().lines().firstOrNull().orEmpty()
        return text.takeIf { it.isNotBlank() }
    }

    // ---------- 工具 ----------

    private fun fetchText(url: String, headers: Map<String, String> = emptyMap()): String? {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) LineTrans/1.4")
            .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.get().build()).execute().use { resp ->
            val body = resp.body?.string() ?: return null
            if (!resp.isSuccessful && resp.code != 404) return null
            return body
        }
    }

    private fun stripTags(html: String): String = html
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

}
