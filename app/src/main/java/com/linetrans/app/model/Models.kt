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
    val remainingCount: Int get() = units.count { !it.isDone }
    val progress: Float get() = if (totalCount == 0) 0f else translatedCount.toFloat() / totalCount
    val isFinished: Boolean get() = totalCount > 0 && translatedCount >= totalCount

    /** 从 [from] 开始按环形顺序找下一个未完成的单元，全部完成时返回 null。 */
    fun nextUndoneIndex(from: Int = 0): Int? {
        if (units.isEmpty()) return null
        for (offset in units.indices) {
            val i = ((from + offset) % units.size + units.size) % units.size
            if (!units[i].isDone) return i
        }
        return null
    }

    companion object {
        const val DEFAULT_FOLDER = "默认"
    }
}

enum class ProviderType(val label: String) {
    OPENAI_COMPAT("OpenAI 兼容"),
    ANTHROPIC("Anthropic"),
    CUSTOM("自定义")
}

data class BillingConfig(
    var inputPrice: Double = 0.0,
    var outputPrice: Double = 0.0,
    var peakMultiplier: Double = 1.0,
    var peakStartHour: Int = 8,
    var peakEndHour: Int = 22
) {
    val currentMultiplier: Double
        get() {
            if (peakMultiplier == 1.0) return 1.0
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val inPeak = if (peakStartHour <= peakEndHour) h in peakStartHour until peakEndHour
                else (h >= peakStartHour || h < peakEndHour)
            return if (inPeak) peakMultiplier else 1.0
        }

    /** 是否配置了价格（未配置时不做费用估算）。 */
    val hasPrice: Boolean get() = inputPrice > 0.0 || outputPrice > 0.0
}

data class ModelConfig(
    val id: String,
    var name: String,
    var providerId: String,
    var billing: BillingConfig = BillingConfig(),
    var maxTokens: Int = 4096,
    var temperature: Double = 0.2
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
    var webServerEnabled: Boolean = false,
    /** 附加提示词，拼接在系统提示之后，用于统一术语或风格。 */
    var customPrompt: String = "",
    /** AI 翻译时携带的前文参考句数。 */
    var contextUnits: Int = 3,
    /** AI 翻译完成后是否自动跳到下一句。 */
    var autoAdvance: Boolean = true,
    /** 每日进度统计的日期（yyyy-MM-dd）与计数。 */
    var dailyDate: String = "",
    var dailyCount: Int = 0
) {
    val activeProvider: ProviderConfig? get() = providers.firstOrNull { it.id == activeProviderId }
    val activeModel: ModelConfig? get() = activeProvider?.findModel(activeModelId)

    val allModels: List<ModelConfig> get() = providers.flatMap { it.models }
}
