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
    val cellNeutral: Color,
    val selection: Color,
    val onSelection: Color,
    val dateContainer: Color,
    val onDateContainer: Color,
    val completedContainer: Color,
    val onCompletedContainer: Color,
    val plannedContainer: Color,
    val onPlannedContainer: Color,
    val heroEnd: Color,
    val isDark: Boolean,
)

// Tokyo Night roles (see Theme.kt): blue acts, violet marks today, green completed, cyan planned.
internal val VigiliaDarkColors = VigiliaColors(
    surfaceRaised = Color(0xFF292E42),
    surfaceHero = Color(0xFF283457),
    outlineSubtle = Color(0xFF3B4261),
    onSurfaceMuted = Color(0xFFA9B1D6),
    active = Color(0xFFBB9AF7),
    onActive = Color(0xFF1A1B26),
    success = Color(0xFF9ECE6A),
    onSuccess = Color(0xFF1A1B26),
    warning = Color(0xFFE0AF68),
    onWarning = Color(0xFF1A1B26),
    info = Color(0xFF7DCFFF),
    onInfo = Color(0xFF1A1B26),
    vacation = Color(0xFF73DACA),
    onVacation = Color(0xFF1A1B26),
    cellNeutral = Color(0xFF292E42),
    selection = Color(0xFF253154),
    onSelection = Color(0xFFC0CAF5),
    dateContainer = Color(0xFF7DCFFF),
    onDateContainer = Color(0xFF1A1B26),
    completedContainer = Color(0xFF2A3B2C),
    onCompletedContainer = Color(0xFF9ECE6A),
    plannedContainer = Color(0xFF283457),
    onPlannedContainer = Color(0xFF7DCFFF),
    heroEnd = Color(0xFF1F2335),
    isDark = true,
)

internal val VigiliaLightColors = VigiliaColors(
    surfaceRaised = Color(0xFFECEDF2),
    surfaceHero = Color(0xFFD5DEF2),
    outlineSubtle = Color(0xFFC4C8DA),
    // >= 4.5:1 on cards, neutral cells and the page background.
    onSurfaceMuted = Color(0xFF565A72),
    active = Color(0xFF5A4A78),
    onActive = Color.White,
    success = Color(0xFF485E30),
    onSuccess = Color.White,
    warning = Color(0xFF8F5E15),
    onWarning = Color.White,
    info = Color(0xFF0F4B6E),
    onInfo = Color.White,
    vacation = Color(0xFF33635C),
    onVacation = Color.White,
    cellNeutral = Color(0xFFECEDF2),
    selection = Color(0xFFD5DEF2),
    onSelection = Color(0xFF343B58),
    dateContainer = Color(0xFFD3E9F2),
    onDateContainer = Color(0xFF0F4B6E),
    completedContainer = Color(0xFFDCE8CF),
    onCompletedContainer = Color(0xFF3F5228),
    plannedContainer = Color(0xFFD5DEF2),
    onPlannedContainer = Color(0xFF34548A),
    heroEnd = Color(0xFFD3E9F2),
    isDark = false,
)

internal val LocalVigiliaColors = staticCompositionLocalOf { VigiliaDarkColors }

val MaterialTheme.vigiliaColors: VigiliaColors
    @Composable
    @ReadOnlyComposable
    get() = LocalVigiliaColors.current
