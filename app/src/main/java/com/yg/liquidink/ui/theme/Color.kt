package com.yg.liquidink.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

data class LiquidPalette(
    val mint: Color,
    val sky: Color,
    val peach: Color,
    val lilac: Color,
    val gold: Color,
)

data class SyntaxPalette(
    val background: Color,
    val plain: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val function: Color,
    val tag: Color,
    val attribute: Color,
    val property: Color,
    val variable: Color,
    val selection: Color,
)

val LightColors = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF0B5D51),
    onPrimary = Color(0xFFF4FFFC),
    primaryContainer = Color(0xFFCDEFE6),
    onPrimaryContainer = Color(0xFF063D35),
    secondary = Color(0xFF3D6070),
    onSecondary = Color(0xFFF8FDFF),
    secondaryContainer = Color(0xFFD9EAF2),
    onSecondaryContainer = Color(0xFF173A48),
    tertiary = Color(0xFF984A2E),
    onTertiary = Color(0xFFFFF8F5),
    tertiaryContainer = Color(0xFFFFDBCF),
    onTertiaryContainer = Color(0xFF602A16),
    background = Color(0xFFF2F0EA),
    onBackground = Color(0xFF14201D),
    surface = Color(0xFFFBFAF7),
    onSurface = Color(0xFF14201D),
    surfaceVariant = Color(0xFFE4E9E4),
    onSurfaceVariant = Color(0xFF4C5B56),
    outline = Color(0xFF75847E),
    outlineVariant = Color(0xFFC4CDC8),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

val DarkColors = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF7CE2CC),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF1A5048),
    onPrimaryContainer = Color(0xFFB4F3E5),
    secondary = Color(0xFFA2CFDF),
    onSecondary = Color(0xFF0C3442),
    secondaryContainer = Color(0xFF2B4E5A),
    onSecondaryContainer = Color(0xFFD2EDF7),
    tertiary = Color(0xFFFFB59B),
    onTertiary = Color(0xFF55200D),
    tertiaryContainer = Color(0xFF6D3A26),
    onTertiaryContainer = Color(0xFFFFDBD0),
    background = Color(0xFF07110F),
    onBackground = Color(0xFFEAF3EF),
    surface = Color(0xFF0D1A17),
    onSurface = Color(0xFFEAF3EF),
    surfaceVariant = Color(0xFF1B2B27),
    onSurfaceVariant = Color(0xFFB8C8C2),
    outline = Color(0xFF879892),
    outlineVariant = Color(0xFF354640),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

val LightLiquidPalette = LiquidPalette(
    mint = Color(0xFF7FD9C4),
    sky = Color(0xFF8CB9FF),
    peach = Color(0xFFFFB49E),
    lilac = Color(0xFFCDB8FF),
    gold = Color(0xFFFFD58A),
)

val DarkLiquidPalette = LiquidPalette(
    mint = Color(0xFF2E9D89),
    sky = Color(0xFF315E9E),
    peach = Color(0xFF8A4838),
    lilac = Color(0xFF5A478A),
    gold = Color(0xFF8A6A2E),
)

val LightSyntaxPalette = SyntaxPalette(
    background = Color(0xFF0D1715),
    plain = Color(0xFFE5EFEA),
    keyword = Color(0xFF8ED8FF),
    string = Color(0xFFB9E8A5),
    comment = Color(0xFF80948C),
    number = Color(0xFFFFC58A),
    function = Color(0xFFFFE08A),
    tag = Color(0xFFFF9EBB),
    attribute = Color(0xFFB9C8FF),
    property = Color(0xFF9BE8DC),
    variable = Color(0xFFF7C7A1),
    selection = Color(0xFF2B5C52),
)

val DarkSyntaxPalette = SyntaxPalette(
    background = Color(0xFF07110F),
    plain = Color(0xFFE5EFEA),
    keyword = Color(0xFF8ED8FF),
    string = Color(0xFFB9E8A5),
    comment = Color(0xFF71857E),
    number = Color(0xFFFFC58A),
    function = Color(0xFFFFE08A),
    tag = Color(0xFFFF9EBB),
    attribute = Color(0xFFB9C8FF),
    property = Color(0xFF9BE8DC),
    variable = Color(0xFFF7C7A1),
    selection = Color(0xFF245047),
)

fun blend(base: Color, tint: Color, amount: Float): Color = lerp(base, tint, amount)
/**
 * A light, low-blue paper palette. It intentionally stays warm regardless of the
 * app's dark/light mode so readers can choose a restful page independently.
 */
val ParchmentReaderColors = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF6E4429),
    onPrimary = Color(0xFFFFF8F1),
    primaryContainer = Color(0xFFE8CDAE),
    onPrimaryContainer = Color(0xFF402211),
    secondary = Color(0xFF4D614F),
    onSecondary = Color(0xFFF8FFF7),
    secondaryContainer = Color(0xFFD8E5D6),
    onSecondaryContainer = Color(0xFF213525),
    tertiary = Color(0xFF855039),
    onTertiary = Color(0xFFFFF8F5),
    tertiaryContainer = Color(0xFFF0D4C2),
    onTertiaryContainer = Color(0xFF4D2819),
    background = Color(0xFFF4E8D3),
    onBackground = Color(0xFF33261A),
    surface = Color(0xFFF7ECDA),
    onSurface = Color(0xFF32261A),
    surfaceVariant = Color(0xFFE5D4B6),
    onSurfaceVariant = Color(0xFF5A4936),
    outline = Color(0xFF8B765B),
    outlineVariant = Color(0xFFD3BE9D),
    error = Color(0xFF9C3F34),
    onError = Color(0xFFFFF8F5),
)

val ParchmentPaperTop = Color(0xFFF8EEDB)
val ParchmentPaperBottom = Color(0xFFEDDFC4)
val ParchmentPaperEdge = Color(0xFFC9B28D)
