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

internal val VigiliaDarkColors = VigiliaColors(
    surfaceRaised = Color(0xFF2C2938),
    surfaceHero = Color(0xFF302A3D),
    outlineSubtle = Color(0xFF3B3749),
    onSurfaceMuted = Color(0xFFB5AFC3),
    active = Color(0xFFB8A2F8),
    onActive = Color(0xFF292738),
    success = Color(0xFFB0D9CB),
    onSuccess = Color(0xFF263F3B),
    warning = Color(0xFFE3C58F),
    onWarning = Color(0xFF45351B),
    info = Color(0xFFC2D3EC),
    onInfo = Color(0xFF2D3A50),
    vacation = Color(0xFFBFDEE0),
    onVacation = Color(0xFF23434B),
    cellNeutral = Color(0xFF2C2938),
    selection = Color(0xFF44375E),
    onSelection = Color(0xFFF2EFF8),
    dateContainer = Color(0xFFBFDEE0),
    onDateContainer = Color(0xFF23434B),
    completedContainer = Color(0xFF263F3B),
    onCompletedContainer = Color(0xFFB0D9CB),
    plannedContainer = Color(0xFF2D3A50),
    onPlannedContainer = Color(0xFFC2D3EC),
    heroEnd = Color(0xFF293A46),
    isDark = true,
)

internal val VigiliaLightColors = VigiliaColors(
    surfaceRaised = Color(0xFFEFEDF4),
    surfaceHero = Color(0xFFF0EBF6),
    outlineSubtle = Color(0xFFE5E1ED),
    // Slightly darker than the reference: >= 4.5:1 also on neutral cells.
    onSurfaceMuted = Color(0xFF665F74),
    active = Color(0xFF6951B4),
    onActive = Color.White,
    success = Color(0xFF2A6957),
    onSuccess = Color.White,
    warning = Color(0xFF76551D),
    onWarning = Color.White,
    info = Color(0xFF3E5B7C),
    onInfo = Color.White,
    vacation = Color(0xFF284B53),
    onVacation = Color.White,
    cellNeutral = Color(0xFFEFEDF4),
    selection = Color(0xFFEBE5F7),
    onSelection = Color(0xFF292738),
    dateContainer = Color(0xFFD8ECEB),
    onDateContainer = Color(0xFF284B53),
    completedContainer = Color(0xFFDEEEE7),
    onCompletedContainer = Color(0xFF2A6957),
    plannedContainer = Color(0xFFE4EDF5),
    onPlannedContainer = Color(0xFF3E5B7C),
    heroEnd = Color(0xFFE6F0F0),
    isDark = false,
)

internal val LocalVigiliaColors = staticCompositionLocalOf { VigiliaDarkColors }

val MaterialTheme.vigiliaColors: VigiliaColors
    @Composable
    @ReadOnlyComposable
    get() = LocalVigiliaColors.current
