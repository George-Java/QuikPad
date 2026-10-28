package com.yg.liquidink.ui

/**
 * Top-level destinations. Settings always takes precedence: opening it removes
 * Home/Editor from composition so no document text can shine through the glass
 * settings surface.
 */
enum class AppSurface {
    HOME,
    EDITOR,
    SETTINGS,
}

fun resolveAppSurface(settingsOpen: Boolean, hasEditor: Boolean): AppSurface = when {
    settingsOpen -> AppSurface.SETTINGS
    hasEditor -> AppSurface.EDITOR
    else -> AppSurface.HOME
}
