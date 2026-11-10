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
    /** Style v2: top of the card's soft vertical gradient (equals the card in light). */
    val cardTop: Color,
    /** Style v2: end of the primary action gradient (starts at primary). */
    val actionEnd: Color,
    /** Style v2: soft radial glow behind the top of each screen (two stops, translucent). */
    val glow: Color,
    val glowSecondary: Color,
    val isDark: Boolean,
)

// Tokyo Night roles (see Theme.kt): blue acts, violet marks today, green completed, cyan planned.
internal val VigiliaDarkColors = VigiliaColors(
    surfaceRaised = Color(0xFF24283B),
    surfaceHero = Color(0xFF283457),
    outlineSubtle = Color(0xFF2A2E45),
    onSurfaceMuted = Color(0xFF959ECA),
    active = Color(0xFFBB9AF7),
    onActive = Color(0xFF10111A),
    success = Color(0xFFA9DC76),
    onSuccess = Color(0xFF10111A),
    warning = Color(0xFFE0AF68),
    onWarning = Color(0xFF10111A),
    info = Color(0xFF89B4FA),
    onInfo = Color(0xFF10111A),
    vacation = Color(0xFF73DACA),
    onVacation = Color(0xFF10111A),
    cellNeutral = Color(0xFF24283B),
    selection = Color(0xFF253154),
    onSelection = Color(0xFFE6EAFF),
    dateContainer = Color(0xFFB4F9F8),
    onDateContainer = Color(0xFF10324A),
    completedContainer = Color(0xFF22362A),
    onCompletedContainer = Color(0xFFA9DC76),
    plannedContainer = Color(0xFF26304F),
    onPlannedContainer = Color(0xFF89B4FA),
    heroEnd = Color(0xFF2B2550),
    cardTop = Color(0xFF22253A),
    actionEnd = Color(0xFF9D8CF5),
    glow = Color(0x337AA2F7),
    glowSecondary = Color(0x1ABB9AF7),
    isDark = true,
)

internal val VigiliaLightColors = VigiliaColors(
    surfaceRaised = Color(0xFFF0F2F8),
    surfaceHero = Color(0xFFE3EBFF),
    outlineSubtle = Color(0xFFE4E7F2),
    // >= 4.5:1 on cards, neutral cells and the page background.
    onSurfaceMuted = Color(0xFF5B6180),
    active = Color(0xFF6D4FD6),
    onActive = Color.White,
    success = Color(0xFF3C6B20),
    onSuccess = Color.White,
    warning = Color(0xFF8F5E15),
    onWarning = Color.White,
    info = Color(0xFF2D5BD0),
    onInfo = Color.White,
    vacation = Color(0xFF33635C),
    onVacation = Color.White,
    cellNeutral = Color(0xFFF0F2F8),
    selection = Color(0xFFE7EEFF),
    onSelection = Color(0xFF1F2440),
    dateContainer = Color(0xFF1F2D5C),
    onDateContainer = Color(0xFFE7EEFF),
    completedContainer = Color(0xFFE6F4DD),
    onCompletedContainer = Color(0xFF3C6B20),
    plannedContainer = Color(0xFFE7EEFF),
    onPlannedContainer = Color(0xFF2D5BD0),
    heroEnd = Color(0xFFEEE7FF),
    cardTop = Color(0xFFFFFFFF),
    actionEnd = Color(0xFF6548CE),
    glow = Color(0x1F3565D2),
    glowSecondary = Color(0x146548CE),
    isDark = false,
)

internal val LocalVigiliaColors = staticCompositionLocalOf { VigiliaDarkColors }

val MaterialTheme.vigiliaColors: VigiliaColors
    @Composable
    @ReadOnlyComposable
    get() = LocalVigiliaColors.current
