package com.linetrans.app.util

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 朗读助手。
 *
 * 之前直接在 `TextToSpeech(context) { }` 之后立刻 speak，引擎还没初始化完，
 * 所以经常没有声音。这里等待 OnInit 回调、按文本自动选语言，并把初始化期间
 * 的请求排队，初始化完成后自动补读。
 */
object SpeechService {

    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (engine != null) return
        val created = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                pending?.let { text ->
                    pending = null
                    speak(text)
                }
            }
        }
        engine = created
    }

    val isReady: Boolean get() = ready

    /** 朗读一段文本；引擎未就绪时会排队，就绪后自动朗读。 */
    fun speak(text: String): Result<Unit> {
        if (text.isBlank()) return Result.success(Unit)
        val tts = engine ?: run {
            appContext?.let { init(it) } ?: return Result.failure(
                IllegalStateException("语音引擎未初始化")
            )
            engine
        }
        if (tts == null || !ready) {
            pending = text
            return Result.success(Unit)
        }

        val locale = if (text.any { it.code in 0x4E00..0x9FFF }) Locale.SIMPLIFIED_CHINESE else Locale.US
        val availability = runCatching { tts.isLanguageAvailable(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        val applied = if (availability == TextToSpeech.LANG_MISSING_DATA ||
            availability == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            runCatching { tts.setLanguage(Locale.getDefault()) }.getOrDefault(TextToSpeech.ERROR)
        } else {
            runCatching { tts.setLanguage(locale) }.getOrDefault(TextToSpeech.ERROR)
        }
        if (applied == TextToSpeech.ERROR) {
            return Result.failure(
                IllegalStateException("系统语音引擎不支持该语言，请在系统设置里安装语音数据")
            )
        }
        runCatching { tts.setSpeechRate(0.95f) }
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "linetrans")
        }
        val result = runCatching { tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "linetrans") }
            .getOrDefault(TextToSpeech.ERROR)
        return if (result == TextToSpeech.SUCCESS) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("朗读失败：系统语音引擎不可用"))
        }
    }

    fun stop() {
        runCatching { engine?.stop() }
    }

    fun shutdown() {
        pending = null
        ready = false
        runCatching {
            engine?.stop()
            engine?.shutdown()
        }
        engine = null
    }
}
