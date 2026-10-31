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

private val VigiliaDarkScheme = darkColorScheme(
    primary = Color(0xFFB8A2F8),
    onPrimary = Color(0xFF15151E),
    primaryContainer = Color(0xFF44375E),
    onPrimaryContainer = Color(0xFFF2EFF8),
    secondary = Color(0xFFBFDEE0),
    onSecondary = Color(0xFF15151E),
    secondaryContainer = Color(0xFF2D3A50),
    onSecondaryContainer = Color(0xFFC2D3EC),
    tertiary = Color(0xFFB0D9CB),
    onTertiary = Color(0xFF15151E),
    tertiaryContainer = Color(0xFF263F3B),
    onTertiaryContainer = Color(0xFFB0D9CB),
    background = Color(0xFF15151E),
    onBackground = Color(0xFFF2EFF8),
    surface = Color(0xFF23232F),
    onSurface = Color(0xFFF2EFF8),
    surfaceVariant = Color(0xFF2C2938),
    onSurfaceVariant = Color(0xFFB5AFC3),
    surfaceContainerLowest = Color(0xFF15151E),
    surfaceContainerLow = Color(0xFF1C1C26),
    surfaceContainer = Color(0xFF23232F),
    surfaceContainerHigh = Color(0xFF2C2938),
    surfaceContainerHighest = Color(0xFF353140),
    outline = Color(0xFF91899F),
    outlineVariant = Color(0xFF3B3749),
    error = Color(0xFFF4A4AE),
    onError = Color(0xFF15151E),
    errorContainer = Color(0xFF4B2833),
    onErrorContainer = Color(0xFFFFD9DE),
    inverseSurface = Color(0xFFF2EFF8),
    inverseOnSurface = Color(0xFF2C2938),
    inversePrimary = Color(0xFF6951B4),
)

private val VigiliaLightScheme = lightColorScheme(
    primary = Color(0xFF6951B4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBE5F7),
    onPrimaryContainer = Color(0xFF292738),
    secondary = Color(0xFF3E5B7C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EDF5),
    onSecondaryContainer = Color(0xFF3E5B7C),
    tertiary = Color(0xFF2A6957),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDEEEE7),
    onTertiaryContainer = Color(0xFF2A6957),
    background = Color(0xFFF5F4F8),
    onBackground = Color(0xFF292738),
    surface = Color.White,
    onSurface = Color(0xFF292738),
    surfaceVariant = Color(0xFFEFEDF4),
    onSurfaceVariant = Color(0xFF665F74),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF9FC),
    surfaceContainer = Color(0xFFF5F4F8),
    surfaceContainerHigh = Color(0xFFEFEDF4),
    surfaceContainerHighest = Color(0xFFE5E1ED),
    outline = Color(0xFF80778F),
    outlineVariant = Color(0xFFE5E1ED),
    error = Color(0xFFB3263E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD9),
    onErrorContainer = Color(0xFF410008),
    inverseSurface = Color(0xFF292738),
    inverseOnSurface = Color(0xFFF5F4F8),
    inversePrimary = Color(0xFFB8A2F8),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(24.dp),
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
