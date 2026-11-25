package com.planaday.nativeapp.core.designtokens

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class PlanadayThemeMode(val displayName: String) {
    WARM_PAPER_LIGHT("Warm Paper (Light)"),
    CRISP_DAYLIGHT("Crisp Daylight"),
    DEEP_SLATE_DARK("Deep Slate (Dark)"),
    HIGH_CONTRAST_AAA("High Contrast (WCAG AAA)"),
}

enum class PlanadayDensity(val displayName: String, val itemPadding: Dp, val minControlHeight: Dp) {
    COMPACT("Compact", 6.dp, 36.dp),
    STANDARD("Standard", 10.dp, 44.dp),
    COMFORTABLE("Comfortable (Touch)", 14.dp, 48.dp),
}

data class PlanadaySpacing(
    val density: PlanadayDensity = PlanadayDensity.STANDARD,
    val cardCornerRadius: Dp = 12.dp,
    val paperRuleColor: Color = Color(0xFFE7DFD3),
)

val LocalPlanadaySpacing = staticCompositionLocalOf { PlanadaySpacing() }

private val WarmPaperColors = lightColorScheme(
    primary = Color(0xFF9A6525),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF6E3C6),
    onPrimaryContainer = Color(0xFF331D00),
    secondary = Color(0xFF0F766E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF042F2E),
    tertiary = Color(0xFF4F46E5),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF6F0),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFF1E8DB),
    onSurfaceVariant = Color(0xFF4E4539),
    outline = Color(0xFFD5C8B7),
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
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFFCBD5E1),
)

private val DeepSlateDarkColors = darkColorScheme(
    primary = Color(0xFFF5B971),
    onPrimary = Color(0xFF2E1800),
    primaryContainer = Color(0xFF5C3A0E),
    onPrimaryContainer = Color(0xFFFFE4C4),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF042F2E),
    background = Color(0xFF0F141B),
    onBackground = Color(0xFFECEFF4),
    surface = Color(0xFF171E28),
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = Color(0xFF232D3B),
    onSurfaceVariant = Color(0xFFB8C4D4),
    outline = Color(0xFF39475B),
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

@Composable
fun PlanadayNativeTheme(
    themeMode: PlanadayThemeMode = PlanadayThemeMode.WARM_PAPER_LIGHT,
    density: PlanadayDensity = PlanadayDensity.STANDARD,
    content: @Composable () -> Unit,
) {
    val scheme = when (themeMode) {
        PlanadayThemeMode.WARM_PAPER_LIGHT -> WarmPaperColors
        PlanadayThemeMode.CRISP_DAYLIGHT -> CrispDaylightColors
        PlanadayThemeMode.DEEP_SLATE_DARK -> DeepSlateDarkColors
        PlanadayThemeMode.HIGH_CONTRAST_AAA -> HighContrastAaaColors
    }
    val spacing = PlanadaySpacing(
        density = density,
        paperRuleColor = if (themeMode == PlanadayThemeMode.DEEP_SLATE_DARK) Color(0xFF2A3444) else Color(0xFFE7DFD3),
    )
    CompositionLocalProvider(LocalPlanadaySpacing provides spacing) {
        MaterialTheme(
            colorScheme = scheme,
            content = content,
        )
    }
}
