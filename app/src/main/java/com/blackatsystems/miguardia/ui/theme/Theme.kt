package com.blackatsystems.miguardia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.em
import com.blackatsystems.miguardia.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Vigilia layout with the Tokyo Night palette (decided by Joaquín on 8/11/2026).
 * Dark: Tokyo Night "Night" with the blue accent; violet marks today. Light: Tokyo Night Day
 * backgrounds with the darker Tokyo Night Light accents so every text pair stays >= 4.5:1.
 * Contrast table: docs/design/ETAPA_13_5_INVENTARIO.md.
 */
private val VigiliaDarkScheme = darkColorScheme(
    primary = Color(0xFF7AA2F7),
    onPrimary = Color(0xFF10111A),
    primaryContainer = Color(0xFF26304F),
    onPrimaryContainer = Color(0xFF89B4FA),
    secondary = Color(0xFF7DCFFF),
    onSecondary = Color(0xFF10111A),
    secondaryContainer = Color(0xFF26304F),
    onSecondaryContainer = Color(0xFFC0CAF5),
    tertiary = Color(0xFFA9DC76),
    onTertiary = Color(0xFF10111A),
    tertiaryContainer = Color(0xFF22362A),
    onTertiaryContainer = Color(0xFFA9DC76),
    background = Color(0xFF13141C),
    onBackground = Color(0xFFE6EAFF),
    surface = Color(0xFF1E2030),
    onSurface = Color(0xFFE6EAFF),
    surfaceVariant = Color(0xFF24283B),
    onSurfaceVariant = Color(0xFFA9B1D6),
    surfaceContainerLowest = Color(0xFF0F1016),
    surfaceContainerLow = Color(0xFF181A26),
    surfaceContainer = Color(0xFF1E2030),
    surfaceContainerHigh = Color(0xFF24283B),
    surfaceContainerHighest = Color(0xFF2A2E45),
    outline = Color(0xFF737AA2),
    outlineVariant = Color(0xFF2A2E45),
    error = Color(0xFFFF8FA3),
    onError = Color(0xFF10111A),
    errorContainer = Color(0xFF33202B),
    onErrorContainer = Color(0xFFFFC2CD),
    inverseSurface = Color(0xFFE6EAFF),
    inverseOnSurface = Color(0xFF1E2030),
    inversePrimary = Color(0xFF2F5DC6),
)

private val VigiliaLightScheme = lightColorScheme(
    primary = Color(0xFF2F5DC6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7EEFF),
    onPrimaryContainer = Color(0xFF2D5BD0),
    secondary = Color(0xFF0F4B6E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E9F2),
    onSecondaryContainer = Color(0xFF0F4B6E),
    tertiary = Color(0xFF3C6B20),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6F4DD),
    onTertiaryContainer = Color(0xFF3C6B20),
    background = Color(0xFFF4F5FA),
    onBackground = Color(0xFF1F2440),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2440),
    surfaceVariant = Color(0xFFF0F2F8),
    onSurfaceVariant = Color(0xFF5B6180),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFD),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F2F8),
    surfaceContainerHighest = Color(0xFFE8EBF4),
    outline = Color(0xFF6E7596),
    outlineVariant = Color(0xFFE4E7F2),
    error = Color(0xFFA8304B),
    onError = Color.White,
    errorContainer = Color(0xFFFCE8EC),
    onErrorContainer = Color(0xFF7A1F35),
    inverseSurface = Color(0xFF1F2440),
    inverseOnSurface = Color(0xFFF4F5FA),
    inversePrimary = Color(0xFF7AA2F7),
)

/*
 * Asymmetric corners (decided by Joaquín on 8/11/2026, option C): large top-start and
 * bottom-end, small top-end and bottom-start, at every size. Cards use `large`; fields,
 * badges, dialogs and sheets inherit the rest. Buttons keep Material's pill shape.
 */
internal fun asymmetricShape(large: Int, small: Int) = RoundedCornerShape(
    topStart = large.dp,
    topEnd = small.dp,
    bottomEnd = large.dp,
    bottomStart = small.dp,
)

private val AppShapes = Shapes(
    extraSmall = asymmetricShape(8, 3),
    small = asymmetricShape(12, 4),
    medium = asymmetricShape(16, 5),
    large = asymmetricShape(22, 6),
    extraLarge = asymmetricShape(28, 8),
)

/*
 * Plus Jakarta Sans (SIL OFL 1.1, bundled as one variable font; decided by Joaquín on 8/11/2026).
 * Each weight pins the variable axis so the shape matches the weight. Tabular figures keep times
 * aligned. Sizes are unchanged from Vigilia: Android's font scale still applies on top (ADR 0043).
 */
@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    resId = R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

internal val PlusJakartaSans = FontFamily(
    jakarta(400),
    jakarta(500),
    jakarta(600),
    jakarta(700),
    jakarta(800),
)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) = TextStyle(
    fontFamily = PlusJakartaSans,
    fontFeatureSettings = "tnum",
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
)

private val AppTypography = Typography(
    displayLarge = style(40, 48, FontWeight.ExtraBold, -0.02f),
    displayMedium = style(36, 42, FontWeight.ExtraBold, -0.02f),
    displaySmall = style(32, 38, FontWeight.ExtraBold, -0.02f),
    headlineLarge = style(28, 34, FontWeight.ExtraBold, -0.02f),
    headlineMedium = style(26, 32, FontWeight.ExtraBold, -0.02f),
    headlineSmall = style(24, 30, FontWeight.ExtraBold, -0.02f),
    titleLarge = style(22, 28, FontWeight.Bold, -0.01f),
    titleMedium = style(18, 24, FontWeight.Bold, -0.01f),
    titleSmall = style(15, 20, FontWeight.Bold),
    bodyLarge = style(16, 24),
    bodyMedium = style(14, 21),
    bodySmall = style(12, 18),
    labelLarge = style(14, 20, FontWeight.Bold, 0.01f),
    labelMedium = style(12, 16, FontWeight.SemiBold),
    labelSmall = style(11, 16, FontWeight.SemiBold),
)

@Composable
fun MiGuardiaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Inherit Android's density and SP converter, including its native font scaling.
    CompositionLocalProvider(
        LocalVigiliaColors provides if (darkTheme) VigiliaDarkColors else VigiliaLightColors,
    ) {
        MaterialTheme(
            colorScheme = vigiliaColorScheme(darkTheme),
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}

internal fun vigiliaColorScheme(darkTheme: Boolean) = if (darkTheme) VigiliaDarkScheme else VigiliaLightScheme
