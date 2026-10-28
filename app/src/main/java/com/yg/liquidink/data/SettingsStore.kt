package com.yg.liquidink.data

import android.content.Context
import com.yg.liquidink.core.AppSettings
import com.yg.liquidink.core.GlassIntensity
import com.yg.liquidink.core.PerformanceMode
import com.yg.liquidink.core.ReaderBackground
import com.yg.liquidink.core.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        update { it.copy(themeMode = mode) }
    }

    fun setAutoSaveEnabled(enabled: Boolean) {
        update { it.copy(autoSaveEnabled = enabled) }
    }

    fun setAutoSaveIntervalSeconds(seconds: Int) {
        update { it.copy(autoSaveIntervalSeconds = seconds.coerceIn(5, 600)) }
    }

    fun setPerformanceMode(mode: PerformanceMode) {
        update { it.copy(performanceMode = mode) }
    }

    fun setHighContrast(enabled: Boolean) {
        update { it.copy(highContrast = enabled) }
    }

    fun setReaderBackground(background: ReaderBackground) {
        update { it.copy(readerBackground = background) }
    }

    fun setGlassEffectsEnabled(enabled: Boolean) {
        update { it.copy(glassEffectsEnabled = enabled) }
    }

    fun setGlassIntensity(intensity: GlassIntensity) {
        update { it.copy(glassIntensity = intensity) }
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        update { it.copy(animationsEnabled = enabled) }
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        preferences.edit()
            .putString(KEY_THEME, updated.themeMode.name)
            .putBoolean(KEY_AUTO_SAVE, updated.autoSaveEnabled)
            .putInt(KEY_AUTO_SAVE_INTERVAL, updated.autoSaveIntervalSeconds)
            .putString(KEY_PERFORMANCE, updated.performanceMode.name)
            .putBoolean(KEY_HIGH_CONTRAST, updated.highContrast)
            .putString(KEY_READER_BACKGROUND, updated.readerBackground.name)
            .putBoolean(KEY_GLASS_EFFECTS, updated.glassEffectsEnabled)
            .putString(KEY_GLASS_INTENSITY, updated.glassIntensity.name)
            .putBoolean(KEY_ANIMATIONS, updated.animationsEnabled)
            .apply()
        _settings.value = updated
    }

    private fun load(): AppSettings {
        val theme = enumOrDefault(
            preferences.getString(KEY_THEME, null),
            ThemeMode.SYSTEM,
        )
        val performance = enumOrDefault(
            preferences.getString(KEY_PERFORMANCE, null),
            PerformanceMode.AUTO,
        )
        val readerBackground = enumOrDefault(
            preferences.getString(KEY_READER_BACKGROUND, null),
            ReaderBackground.DEFAULT,
        )
        val glassIntensity = enumOrDefault(
            preferences.getString(KEY_GLASS_INTENSITY, null),
            GlassIntensity.MEDIUM,
        )
        return AppSettings(
            themeMode = theme,
            autoSaveEnabled = preferences.getBoolean(KEY_AUTO_SAVE, true),
            autoSaveIntervalSeconds = preferences.getInt(KEY_AUTO_SAVE_INTERVAL, 15),
            performanceMode = performance,
            highContrast = preferences.getBoolean(KEY_HIGH_CONTRAST, false),
            readerBackground = readerBackground,
            glassEffectsEnabled = preferences.getBoolean(KEY_GLASS_EFFECTS, true),
            glassIntensity = glassIntensity,
            animationsEnabled = preferences.getBoolean(KEY_ANIMATIONS, true),
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, fallback: T): T =
        runCatching { value?.let { enumValueOf<T>(it) } }.getOrNull() ?: fallback

    private companion object {
        const val PREFERENCES_NAME = "liquid_ink_settings"
        const val KEY_THEME = "theme"
        const val KEY_AUTO_SAVE = "auto_save"
        const val KEY_AUTO_SAVE_INTERVAL = "auto_save_interval"
        const val KEY_PERFORMANCE = "performance"
        const val KEY_HIGH_CONTRAST = "high_contrast"
        const val KEY_READER_BACKGROUND = "reader_background"
        const val KEY_GLASS_EFFECTS = "glass_effects"
        const val KEY_GLASS_INTENSITY = "glass_intensity"
        const val KEY_ANIMATIONS = "animations"
    }
}
