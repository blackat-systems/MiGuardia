package com.blackatsystems.miguardia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import android.util.DisplayMetrics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val VigiliaDarkScheme = darkColorScheme(
    primary = Color(0xFF8B5CFF),
    onPrimary = Color(0xFF090812),
    primaryContainer = Color(0xFF30204B),
    onPrimaryContainer = Color(0xFFF7F2FA),
    secondary = Color(0xFFEC63F5),
    onSecondary = Color(0xFF090812),
    secondaryContainer = Color(0xFF452047),
    onSecondaryContainer = Color(0xFFFFD7FF),
    tertiary = Color(0xFF55C2FF),
    onTertiary = Color(0xFF090812),
    tertiaryContainer = Color(0xFF12354A),
    onTertiaryContainer = Color(0xFFD7F0FF),
    background = Color(0xFF090812),
    onBackground = Color(0xFFF7F2FA),
    surface = Color(0xFF151125),
    onSurface = Color(0xFFF7F2FA),
    surfaceVariant = Color(0xFF211732),
    onSurfaceVariant = Color(0xFFC9C2D6),
    surfaceContainerLowest = Color(0xFF090812),
    surfaceContainerLow = Color(0xFF100D1C),
    surfaceContainer = Color(0xFF151125),
    surfaceContainerHigh = Color(0xFF211732),
    surfaceContainerHighest = Color(0xFF2A1D3D),
    outline = Color(0xFF34254A),
    outlineVariant = Color(0xFF34254A),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF090812),
    errorContainer = Color(0xFF4B202B),
    onErrorContainer = Color(0xFFFFD9DE),
    inverseSurface = Color(0xFFF7F2FA),
    inverseOnSurface = Color(0xFF211732),
    inversePrimary = Color(0xFF6F3DE1),
)

private val VigiliaLightScheme = lightColorScheme(
    primary = Color(0xFF6F3DE1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF29105F),
    secondary = Color(0xFFB62AC8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7D8FA),
    onSecondaryContainer = Color(0xFF4C1054),
    tertiary = Color(0xFF00629A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7EFFF),
    onTertiaryContainer = Color(0xFF001D31),
    background = Color(0xFFF7F4FA),
    onBackground = Color(0xFF1B1524),
    surface = Color.White,
    onSurface = Color(0xFF1B1524),
    surfaceVariant = Color(0xFFF0EAF6),
    onSurfaceVariant = Color(0xFF665E70),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF7FC),
    surfaceContainer = Color(0xFFF7F1FA),
    surfaceContainerHigh = Color(0xFFF0EAF6),
    surfaceContainerHighest = Color(0xFFE8E0EE),
    outline = Color(0xFFDDD3E7),
    outlineVariant = Color(0xFFDDD3E7),
    error = Color(0xFFB3263E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD9),
    onErrorContainer = Color(0xFF410008),
    inverseSurface = Color(0xFF322D37),
    inverseOnSurface = Color(0xFFF7F1FA),
    inversePrimary = Color(0xFFCDBDFF),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun MiGuardiaTheme(
    darkTheme: Boolean = true,
    appZoom: AppZoom = AppZoom.STANDARD,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = DEFAULT_DISPLAY_DENSITY * appZoom.scale,
            fontScale = DEFAULT_FONT_SCALE,
        ),
        LocalVigiliaColors provides if (darkTheme) VigiliaDarkColors else VigiliaLightColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) VigiliaDarkScheme else VigiliaLightScheme,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}

private const val DEFAULT_FONT_SCALE = 1f
private val DEFAULT_DISPLAY_DENSITY =
    DisplayMetrics.DENSITY_DEVICE_STABLE.toFloat() / DisplayMetrics.DENSITY_DEFAULT
