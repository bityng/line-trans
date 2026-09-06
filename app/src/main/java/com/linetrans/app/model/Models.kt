package com.linetrans.app.model

import java.util.Calendar

enum class UnitMode { LINE, SENTENCE }

data class TranslationUnit(
    var source: String,
    var translation: String = "",
    var done: Boolean = false
) {
    /** 是否真的有译文（用于导出时决定“已翻译”）。 */
    val isTranslated: Boolean get() = translation.isNotBlank()

    /** 是否已处理（翻译或标记为“不需要翻译/跳过”），用于进度统计。 */
    val isDone: Boolean get() = done || translation.isNotBlank()
}

data class TranslationDoc(
    val id: String,
    var name: String,
    var folder: String = DEFAULT_FOLDER,
    val units: MutableList<TranslationUnit> = mutableListOf(),
    var unitMode: UnitMode = UnitMode.LINE,
    var sourceText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    val totalCount: Int get() = units.size
    val translatedCount: Int get() = units.count { it.isDone }
    val progress: Float get() = if (totalCount == 0) 0f else translatedCount.toFloat() / totalCount

    companion object {
        const val DEFAULT_FOLDER = "默认"
    }
}

enum class ProviderType { OPENAI_COMPAT, ANTHROPIC, CUSTOM }

data class BillingConfig(
    var inputPrice: Double = 0.0,
    var outputPrice: Double = 0.0,
    var peakMultiplier: Double = 1.0,
    var peakStartHour: Int = 8,
    var peakEndHour: Int = 22
) {
    val currentMultiplier: Double
        get() {
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val inPeak = if (peakStartHour <= peakEndHour) h in peakStartHour until peakEndHour
                else (h >= peakStartHour || h < peakEndHour)
            return if (inPeak) peakMultiplier else 1.0
        }
}

data class ModelConfig(
    val id: String,
    var name: String,
    var providerId: String,
    var billing: BillingConfig = BillingConfig()
)

data class ProviderConfig(
    val id: String,
    var name: String,
    var type: ProviderType = ProviderType.OPENAI_COMPAT,
    var baseUrl: String = "",
    var apiKey: String = "",
    var models: MutableList<ModelConfig> = mutableListOf()
) {
    fun findModel(id: String): ModelConfig? = models.firstOrNull { it.id == id }
}

data class AppSettings(
    var providers: MutableList<ProviderConfig> = mutableListOf(),
    var activeProviderId: String = "",
    var activeModelId: String = "",
    var detectLanguage: Boolean = true,
    var sourceLang: String = "auto",
    var targetLang: String = "zh-CN",
    var storageDirUri: String = "",
    var dailyGoal: Int = 0,
    var webServerPort: Int = 8080,
    var webServerEnabled: Boolean = false
) {
    val activeProvider: ProviderConfig? get() = providers.firstOrNull { it.id == activeProviderId }
    val activeModel: ModelConfig? get() = activeProvider?.findModel(activeModelId)
}
