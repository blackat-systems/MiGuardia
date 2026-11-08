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
    onPrimary = Color(0xFF1A1B26),
    primaryContainer = Color(0xFF394B70),
    onPrimaryContainer = Color(0xFFC0CAF5),
    secondary = Color(0xFF7DCFFF),
    onSecondary = Color(0xFF1A1B26),
    secondaryContainer = Color(0xFF283457),
    onSecondaryContainer = Color(0xFFC0CAF5),
    tertiary = Color(0xFF9ECE6A),
    onTertiary = Color(0xFF1A1B26),
    tertiaryContainer = Color(0xFF2A3B2C),
    onTertiaryContainer = Color(0xFF9ECE6A),
    background = Color(0xFF1A1B26),
    onBackground = Color(0xFFC0CAF5),
    surface = Color(0xFF24283B),
    onSurface = Color(0xFFC0CAF5),
    surfaceVariant = Color(0xFF292E42),
    onSurfaceVariant = Color(0xFFA9B1D6),
    surfaceContainerLowest = Color(0xFF16161E),
    surfaceContainerLow = Color(0xFF1F2335),
    surfaceContainer = Color(0xFF24283B),
    surfaceContainerHigh = Color(0xFF292E42),
    surfaceContainerHighest = Color(0xFF2F3549),
    outline = Color(0xFF737AA2),
    outlineVariant = Color(0xFF3B4261),
    error = Color(0xFFF7768E),
    onError = Color(0xFF1A1B26),
    errorContainer = Color(0xFF3F2533),
    onErrorContainer = Color(0xFFF7C6D0),
    inverseSurface = Color(0xFFC0CAF5),
    inverseOnSurface = Color(0xFF24283B),
    inversePrimary = Color(0xFF34548A),
)

private val VigiliaLightScheme = lightColorScheme(
    primary = Color(0xFF34548A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5DEF2),
    onPrimaryContainer = Color(0xFF1F3360),
    secondary = Color(0xFF0F4B6E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E9F2),
    onSecondaryContainer = Color(0xFF0F4B6E),
    tertiary = Color(0xFF485E30),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE8CF),
    onTertiaryContainer = Color(0xFF3F5228),
    background = Color(0xFFE1E2E7),
    onBackground = Color(0xFF343B58),
    surface = Color(0xFFF5F6FA),
    onSurface = Color(0xFF343B58),
    surfaceVariant = Color(0xFFECEDF2),
    onSurfaceVariant = Color(0xFF565A72),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FAFC),
    surfaceContainer = Color(0xFFF5F6FA),
    surfaceContainerHigh = Color(0xFFECEDF2),
    surfaceContainerHighest = Color(0xFFE4E6EE),
    outline = Color(0xFF68709A),
    outlineVariant = Color(0xFFC4C8DA),
    error = Color(0xFF8C4351),
    onError = Color.White,
    errorContainer = Color(0xFFF6D9DF),
    onErrorContainer = Color(0xFF5C1F2C),
    inverseSurface = Color(0xFF343B58),
    inverseOnSurface = Color(0xFFE1E2E7),
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

private val AppTypography = Typography(
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum", fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum", fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum", fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum", fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum", fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold),
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum", fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum", fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum", fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum", fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum", fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum", fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum", fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum", fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum", fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontFeatureSettings = "tnum", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
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
