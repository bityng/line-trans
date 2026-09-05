package com.linetrans.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.linetrans.app.model.AppSettings

object SettingsRepository {
    private const val PREFS = "linetrans_settings"
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    var settings by mutableStateOf(AppSettings())
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        settings = load()
    }

    private fun load(): AppSettings {
        val json = prefs.getString("settings", null) ?: return AppSettings()
        return runCatching { gson.fromJson(json, AppSettings::class.java) }.getOrNull() ?: AppSettings()
    }

    fun save() {
        if (!::prefs.isInitialized) return
        prefs.edit().putString("settings", gson.toJson(settings)).apply()
    }

    fun set(s: AppSettings) {
        settings = s
        save()
    }

    fun update(block: (AppSettings) -> AppSettings) {
        settings = block(settings)
        save()
    }
}
