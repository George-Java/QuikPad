package com.yg.liquidink.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.yg.liquidink.core.GlassIntensity
import com.yg.liquidink.core.ThemeMode

@Immutable
data class LiquidGlassConfig(
    val animationsEnabled: Boolean,
    val enhancedShadows: Boolean,
    val specularHighlights: Boolean,
    val panelTopAlpha: Float,
    val panelBottomAlpha: Float,
    val borderAlpha: Float,
    val highContrast: Boolean,
    val glassEnabled: Boolean = true,
    val intensity: Float = GlassIntensity.MEDIUM.strength,
)

val LocalLiquidPalette = staticCompositionLocalOf { LightLiquidPalette }
val LocalSyntaxPalette = staticCompositionLocalOf { LightSyntaxPalette }
val LocalLiquidGlass = staticCompositionLocalOf {
    LiquidGlassConfig(
        animationsEnabled = true,
        enhancedShadows = true,
        specularHighlights = true,
        panelTopAlpha = 0.80f,
        panelBottomAlpha = 0.52f,
        borderAlpha = 0.62f,
        highContrast = false,
        glassEnabled = true,
        intensity = GlassIntensity.MEDIUM.strength,
    )
}

private val LiquidShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp),
)

/**
 * Pure token calculation so the visual contract can be unit tested.
 *
 * - LOW adds opacity, reduces highlights and removes heavy shadows.
 * - HIGH keeps the airy, more transparent panel and strongest specular edges.
 * - Disabling glass collapses every panel to an opaque, flat surface.
 */
fun resolveGlassConfig(
    darkTheme: Boolean,
    highContrast: Boolean,
    reducedEffects: Boolean,
    glassEffectsEnabled: Boolean,
    glassIntensity: GlassIntensity,
    animationsEnabled: Boolean,
): LiquidGlassConfig {
    val intensity = glassIntensity.strength.coerceIn(0f, 1f)
    val extraOpacity = (1f - intensity) * 0.14f
    var panelTop = (if (darkTheme) 0.62f else 0.80f) + extraOpacity
    var panelBottom = (if (darkTheme) 0.36f else 0.52f) + extraOpacity
    var border = (if (darkTheme) 0.28f else 0.62f) + extraOpacity * 0.75f

    if (highContrast) {
        panelTop = maxOf(panelTop, if (darkTheme) 0.94f else 0.97f)
        panelBottom = maxOf(panelBottom, if (darkTheme) 0.86f else 0.90f)
        border = maxOf(border, if (darkTheme) 0.82f else 0.92f)
    }

    if (!glassEffectsEnabled) {
        panelTop = 1f
        panelBottom = 1f
        border = if (highContrast) 0.62f else 0.26f
    }

    val useRichEffects = glassEffectsEnabled && !reducedEffects
    return LiquidGlassConfig(
        animationsEnabled = animationsEnabled && !reducedEffects,
        enhancedShadows = useRichEffects && glassIntensity != GlassIntensity.LOW,
        specularHighlights = useRichEffects && glassIntensity != GlassIntensity.LOW,
        panelTopAlpha = panelTop.coerceIn(0f, 1f),
        panelBottomAlpha = panelBottom.coerceIn(0f, 1f),
        borderAlpha = border.coerceIn(0f, 1f),
        highContrast = highContrast,
        glassEnabled = glassEffectsEnabled,
        intensity = intensity,
    )
}

@Composable
fun LiquidInkTheme(
    themeMode: ThemeMode,
    highContrast: Boolean,
    reducedEffects: Boolean,
    glassEffectsEnabled: Boolean = true,
    glassIntensity: GlassIntensity = GlassIntensity.MEDIUM,
    animationsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) darkColorScheme(
        primary = DarkColors.primary,
        onPrimary = DarkColors.onPrimary,
        primaryContainer = DarkColors.primaryContainer,
        onPrimaryContainer = DarkColors.onPrimaryContainer,
        secondary = DarkColors.secondary,
        onSecondary = DarkColors.onSecondary,
        tertiary = DarkColors.tertiary,
        background = DarkColors.background,
        onBackground = DarkColors.onBackground,
        surface = DarkColors.surface,
        onSurface = DarkColors.onSurface,
        surfaceVariant = DarkColors.surfaceVariant,
        onSurfaceVariant = DarkColors.onSurfaceVariant,
        outline = DarkColors.outline,
        outlineVariant = DarkColors.outlineVariant,
        error = DarkColors.error,
        onError = DarkColors.onError,
    ) else {
        lightColorScheme(
            primary = LightColors.primary,
            onPrimary = LightColors.onPrimary,
            primaryContainer = LightColors.primaryContainer,
            onPrimaryContainer = LightColors.onPrimaryContainer,
            secondary = LightColors.secondary,
            onSecondary = LightColors.onSecondary,
            tertiary = LightColors.tertiary,
            background = LightColors.background,
            onBackground = LightColors.onBackground,
            surface = LightColors.surface,
            onSurface = LightColors.onSurface,
            surfaceVariant = LightColors.surfaceVariant,
            onSurfaceVariant = LightColors.onSurfaceVariant,
            outline = LightColors.outline,
            outlineVariant = LightColors.outlineVariant,
            error = LightColors.error,
            onError = LightColors.onError,
        )
    }

    val liquidPalette = if (darkTheme) DarkLiquidPalette else LightLiquidPalette
    val syntaxPalette = if (darkTheme) DarkSyntaxPalette else LightSyntaxPalette
    val glassConfig = resolveGlassConfig(
        darkTheme = darkTheme,
        highContrast = highContrast,
        reducedEffects = reducedEffects,
        glassEffectsEnabled = glassEffectsEnabled,
        glassIntensity = glassIntensity,
        animationsEnabled = animationsEnabled,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalLiquidPalette provides liquidPalette,
        LocalSyntaxPalette provides syntaxPalette,
        LocalLiquidGlass provides glassConfig,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LiquidTypography,
            shapes = LiquidShapes,
            content = content,
        )
    }
}

@Composable
fun surfaceTint(amount: Float = 0.08f): Color =
    MaterialTheme.colorScheme.onSurface.copy(alpha = amount)
