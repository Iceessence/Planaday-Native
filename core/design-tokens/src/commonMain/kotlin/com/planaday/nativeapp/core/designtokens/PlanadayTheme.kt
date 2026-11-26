package com.planaday.nativeapp.core.designtokens

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable

@Serializable
enum class PlanadayThemeMode(val displayName: String) {
    WARM_PAPER_LIGHT("Warm Paper"),
    CRISP_DAYLIGHT("Crisp Daylight"),
    DEEP_SLATE_DARK("Deep Slate"),
    HIGH_CONTRAST_AAA("High Contrast"),
}

@Serializable
enum class PlanadayDensity(val displayName: String, val itemPadding: Dp, val minControlHeight: Dp) {
    COMPACT("Compact", 6.dp, 36.dp),
    STANDARD("Standard", 10.dp, 44.dp),
    COMFORTABLE("Comfortable", 14.dp, 48.dp),
}

data class PlanadaySemanticColors(
    val success: Color,
    val warning: Color,
    val info: Color,
    val subtleBorder: Color,
    val elevatedSurface: Color,
    val paperRuleColor: Color,
)

data class PlanadaySpacing(
    val density: PlanadayDensity = PlanadayDensity.STANDARD,
    val cardCornerRadius: Dp = 12.dp,
    val paperRuleColor: Color = Color(0xFFE7DFD3),
    val fontScale: Float = 1.0f,
    val semantics: PlanadaySemanticColors = PlanadaySemanticColors(
        success = Color(0xFF15803D),
        warning = Color(0xFFB45309),
        info = Color(0xFF0369A1),
        subtleBorder = Color(0xFFE5DDD0),
        elevatedSurface = Color(0xFFFFFFFF),
        paperRuleColor = Color(0xFFE7DFD3),
    ),
)

val LocalPlanadaySpacing = staticCompositionLocalOf { PlanadaySpacing() }

val PlanadayAccentPresets: List<Pair<String, String>> = listOf(
    "Amber Ink" to "#9A6525",
    "Forest Sage" to "#0F766E",
    "Cobalt Studio" to "#1D4ED8",
    "Indigo Craft" to "#4F46E5",
    "Terracotta" to "#B45309",
    "Plum Editorial" to "#7E22CE",
    "Crimson Slate" to "#BE123C",
)

fun parseHexColorOrNull(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val clean = hex.trim().removePrefix("#")
    return runCatching {
        when (clean.length) {
            6 -> Color((0xFF000000L or clean.toLong(16)).toULong().toLong())
            8 -> Color(clean.toLong(16))
            else -> null
        }
    }.getOrNull()
}

private val WarmPaperColors = lightColorScheme(
    primary = Color(0xFF9A6525),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF6E3C6),
    onPrimaryContainer = Color(0xFF331D00),
    secondary = Color(0xFF0F766E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5F5EE),
    onSecondaryContainer = Color(0xFF042F2E),
    tertiary = Color(0xFF4F46E5),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F4ED),
    onBackground = Color(0xFF1E1A15),
    surface = Color(0xFFFFFDFA),
    onSurface = Color(0xFF1E1A15),
    surfaceVariant = Color(0xFFEFE7DA),
    onSurfaceVariant = Color(0xFF544B3F),
    outline = Color(0xFFD9CEBD),
    outlineVariant = Color(0xFFE8DFD1),
    error = Color(0xFFB91C1C),
    onError = Color(0xFFFFFFFF),
)

private val CrispDaylightColors = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF0F766E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF042F2E),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE9EEF5),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
)

private val DeepSlateDarkColors = darkColorScheme(
    primary = Color(0xFFF3B76B),
    onPrimary = Color(0xFF2B1600),
    primaryContainer = Color(0xFF4F3412),
    onPrimaryContainer = Color(0xFFFFE5C6),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFF0E131A),
    onBackground = Color(0xFFECEFF4),
    surface = Color(0xFF161D27),
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = Color(0xFF212B38),
    onSurfaceVariant = Color(0xFFB4C0D0),
    outline = Color(0xFF344254),
    outlineVariant = Color(0xFF263242),
)

private val HighContrastAaaColors = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E7EB),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF003B36),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF000000),
    outline = Color(0xFF000000),
    error = Color(0xFF991B1B),
    onError = Color(0xFFFFFFFF),
)

private fun Typography.scaled(scale: Float): Typography {
    if (scale in 0.99f..1.01f) return this
    fun TextStyle.s() = copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    return Typography(
        displayLarge = displayLarge.s(),
        displayMedium = displayMedium.s(),
        displaySmall = displaySmall.s(),
        headlineLarge = headlineLarge.s(),
        headlineMedium = headlineMedium.s(),
        headlineSmall = headlineSmall.s(),
        titleLarge = titleLarge.s(),
        titleMedium = titleMedium.s(),
        titleSmall = titleSmall.s(),
        bodyLarge = bodyLarge.s(),
        bodyMedium = bodyMedium.s(),
        bodySmall = bodySmall.s(),
        labelLarge = labelLarge.s(),
        labelMedium = labelMedium.s(),
        labelSmall = labelSmall.s(),
    )
}

@Composable
fun PlanadayNativeTheme(
    themeMode: PlanadayThemeMode = PlanadayThemeMode.WARM_PAPER_LIGHT,
    density: PlanadayDensity = PlanadayDensity.STANDARD,
    accentHex: String? = null,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    val baseScheme = when (themeMode) {
        PlanadayThemeMode.WARM_PAPER_LIGHT -> WarmPaperColors
        PlanadayThemeMode.CRISP_DAYLIGHT -> CrispDaylightColors
        PlanadayThemeMode.DEEP_SLATE_DARK -> DeepSlateDarkColors
        PlanadayThemeMode.HIGH_CONTRAST_AAA -> HighContrastAaaColors
    }
    val customAccent = if (themeMode != PlanadayThemeMode.HIGH_CONTRAST_AAA) parseHexColorOrNull(accentHex) else null
    val scheme = if (customAccent != null) {
        baseScheme.copy(
            primary = customAccent,
            primaryContainer = customAccent.copy(alpha = if (themeMode == PlanadayThemeMode.DEEP_SLATE_DARK) 0.28f else 0.14f),
            onPrimaryContainer = if (themeMode == PlanadayThemeMode.DEEP_SLATE_DARK) Color(0xFFF8FAFC) else customAccent,
        )
    } else baseScheme

    val isDark = themeMode == PlanadayThemeMode.DEEP_SLATE_DARK
    val spacing = PlanadaySpacing(
        density = density,
        paperRuleColor = if (isDark) Color(0xFF263140) else Color(0xFFE7DFD3),
        fontScale = fontScale.coerceIn(0.85f, 1.35f),
        semantics = PlanadaySemanticColors(
            success = if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D),
            warning = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309),
            info = if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1),
            subtleBorder = if (isDark) Color(0xFF283446) else Color(0xFFE5DDD0),
            elevatedSurface = if (isDark) Color(0xFF1B2431) else Color(0xFFFFFFFF),
            paperRuleColor = if (isDark) Color(0xFF263140) else Color(0xFFE7DFD3),
        ),
    )
    CompositionLocalProvider(LocalPlanadaySpacing provides spacing) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography().scaled(spacing.fontScale),
            content = content,
        )
    }
}
