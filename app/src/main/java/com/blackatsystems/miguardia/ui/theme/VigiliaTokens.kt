package com.blackatsystems.miguardia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class VigiliaColors(
    val surfaceRaised: Color,
    val surfaceHero: Color,
    val outlineSubtle: Color,
    val onSurfaceMuted: Color,
    val active: Color,
    val onActive: Color,
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val info: Color,
    val onInfo: Color,
    val vacation: Color,
    val onVacation: Color,
    val isDark: Boolean,
)

internal val VigiliaDarkColors = VigiliaColors(
    surfaceRaised = Color(0xFF211732),
    surfaceHero = Color(0xFF2A1B3D),
    outlineSubtle = Color(0xFF34254A),
    onSurfaceMuted = Color(0xFFC9C2D6),
    active = Color(0xFFEC63F5),
    onActive = Color(0xFF090812),
    success = Color(0xFF42D392),
    onSuccess = Color(0xFF090812),
    warning = Color(0xFFFFCC66),
    onWarning = Color(0xFF090812),
    info = Color(0xFF55C2FF),
    onInfo = Color(0xFF090812),
    vacation = Color(0xFF71D8D1),
    onVacation = Color(0xFF090812),
    isDark = true,
)

internal val VigiliaLightColors = VigiliaColors(
    surfaceRaised = Color(0xFFF0EAF6),
    surfaceHero = Color(0xFFE8DDF1),
    outlineSubtle = Color(0xFFDDD3E7),
    onSurfaceMuted = Color(0xFF665E70),
    active = Color(0xFFB62AC8),
    onActive = Color.White,
    success = Color(0xFF167A56),
    onSuccess = Color.White,
    warning = Color(0xFF8A5A00),
    onWarning = Color.White,
    info = Color(0xFF00629A),
    onInfo = Color.White,
    vacation = Color(0xFF006A65),
    onVacation = Color.White,
    isDark = false,
)

internal val LocalVigiliaColors = staticCompositionLocalOf { VigiliaDarkColors }

val MaterialTheme.vigiliaColors: VigiliaColors
    @Composable
    @ReadOnlyComposable
    get() = LocalVigiliaColors.current
