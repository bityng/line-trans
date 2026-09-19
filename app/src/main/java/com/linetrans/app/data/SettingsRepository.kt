package com.linetrans.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.linetrans.app.model.AppSettings
import java.time.LocalDate

object SettingsRepository {
    private const val PREFS = "linetrans_settings"
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    /**
     * 设置对象内部是可变字段（providers/models 直接增删），新旧值往往是同一个实例。
     * 默认的结构相等策略会认为“没有变化”，导致界面不刷新，这里固定为“永不相等”。
     */
    var settings by mutableStateOf(AppSettings(), neverEqualPolicy())
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        settings = load().sanitized()
    }

    private fun load(): AppSettings {
        val json = prefs.getString("settings", null) ?: return AppSettings()
        return runCatching { Gson().fromJson(json, AppSettings::class.java) }.getOrNull() ?: AppSettings()
    }

    /** 修正历史数据里可能缺失或越界的字段。 */
    private fun AppSettings.sanitized(): AppSettings {
        providers.forEach { p -> if (p.models == null) p.models = mutableListOf() }
        webServerPort = webServerPort.coerceIn(1024, 65535)
        contextUnits = contextUnits.coerceIn(0, 10)
        if (providers.none { it.id == activeProviderId }) activeProviderId = providers.firstOrNull()?.id ?: ""
        if (activeModel == null) activeModelId = activeProvider?.models?.firstOrNull()?.id ?: ""
        return this
    }

    fun save() {
        if (!::prefs.isInitialized) return
        runCatching { prefs.edit().putString("settings", gson.toJson(settings)).apply() }
    }

    fun set(s: AppSettings) {
        settings = s
        save()
    }

    fun update(block: (AppSettings) -> AppSettings) {
        settings = block(settings)
        save()
    }

    fun today(): String = LocalDate.now().toString()

    /** 今日已完成句数（跨天自动归零）。 */
    fun dailyCount(): Int = if (settings.dailyDate == today()) settings.dailyCount else 0

    /** 完成若干句后累加今日进度。 */
    fun bumpDaily(count: Int = 1) {
        if (count <= 0) return
        val today = today()
        update { s ->
            if (s.dailyDate != today) {
                s.dailyDate = today
                s.dailyCount = 0
            }
            s.dailyCount += count
            s
        }
    }

    /** 回退今日进度（例如取消“已完成”标记）。 */
    fun reduceDaily(count: Int = 1) {
        if (count <= 0) return
        val today = today()
        update { s ->
            if (s.dailyDate == today) s.dailyCount = (s.dailyCount - count).coerceAtLeast(0)
            s
        }
    }

    fun resetDaily() {
        update { s ->
            s.dailyDate = today()
            s.dailyCount = 0
            s
        }
    }
}
