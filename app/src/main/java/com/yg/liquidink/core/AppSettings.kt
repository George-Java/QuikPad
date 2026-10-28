package com.yg.liquidink.core

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class PerformanceMode { AUTO, FULL, REDUCED }

enum class ReaderBackground { DEFAULT, PARCHMENT }

enum class GlassIntensity(val strength: Float) {
    LOW(0.36f),
    MEDIUM(0.68f),
    HIGH(1f),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoSaveEnabled: Boolean = true,
    val autoSaveIntervalSeconds: Int = 15,
    val performanceMode: PerformanceMode = PerformanceMode.AUTO,
    val highContrast: Boolean = false,
    val readerBackground: ReaderBackground = ReaderBackground.DEFAULT,
    val glassEffectsEnabled: Boolean = true,
    val glassIntensity: GlassIntensity = GlassIntensity.MEDIUM,
    val animationsEnabled: Boolean = true,
) {
    val autoSaveIntervalMillis: Long
        get() = autoSaveIntervalSeconds.coerceIn(5, 600) * 1000L
}

data class RecentDocument(
    val uri: String,
    val displayName: String,
    val lastOpened: Long,
    val sizeBytes: Long = 0L,
    val encodingLabel: String = "UTF-8",
)
