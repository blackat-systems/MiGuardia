package com.blackatsystems.miguardia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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

private val CoreViewColors = darkColorScheme(
    primary = Color(0xFFC06CFF),
    onPrimary = Color(0xFF170022),
    primaryContainer = Color(0xFF55217A),
    onPrimaryContainer = Color(0xFFF5D9FF),
    secondary = Color(0xFFFF72D2),
    onSecondary = Color(0xFF330024),
    secondaryContainer = Color(0xFF61204D),
    onSecondaryContainer = Color(0xFFFFD8EF),
    tertiary = Color(0xFF8D7CFF),
    onTertiary = Color(0xFF10005B),
    background = Color(0xFF080611),
    onBackground = Color(0xFFF4EEFF),
    surface = Color(0xFF0D0A18),
    onSurface = Color(0xFFF4EEFF),
    surfaceVariant = Color(0xFF241C35),
    onSurfaceVariant = Color(0xFFD1C3DE),
    surfaceContainerLowest = Color(0xFF07050E),
    surfaceContainerLow = Color(0xFF0F0B1D),
    surfaceContainer = Color(0xFF151025),
    surfaceContainerHigh = Color(0xFF1B1430),
    surfaceContainerHighest = Color(0xFF251B3D),
    outline = Color(0xFF8D69A8),
    outlineVariant = Color(0xFF3B2A52),
    error = Color(0xFFFF6F91),
    errorContainer = Color(0xFF5D1630),
    onErrorContainer = Color(0xFFFFD9E1),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
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
    ) {
        MaterialTheme(
            colorScheme = CoreViewColors,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}

private const val DEFAULT_FONT_SCALE = 1f
private val DEFAULT_DISPLAY_DENSITY =
    DisplayMetrics.DENSITY_DEVICE_STABLE.toFloat() / DisplayMetrics.DENSITY_DEFAULT
