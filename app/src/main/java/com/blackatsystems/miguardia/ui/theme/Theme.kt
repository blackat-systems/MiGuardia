package com.blackatsystems.miguardia.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import android.util.DisplayMetrics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val LightColors = lightColorScheme(
    primary = Color(0xFF315DA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001A41),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF16458F),
    onPrimaryContainer = Color(0xFFD9E2FF),
)

@Composable
fun MiGuardiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = DEFAULT_DISPLAY_DENSITY,
            fontScale = DEFAULT_FONT_SCALE,
        ),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}

private const val DEFAULT_FONT_SCALE = 1f
private val DEFAULT_DISPLAY_DENSITY =
    DisplayMetrics.DENSITY_DEVICE_STABLE.toFloat() / DisplayMetrics.DENSITY_DEFAULT
