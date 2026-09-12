package com.blackatsystems.miguardia.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeModeTest {
    @Test
    fun storedThemeModeFallsBackToSystem() {
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromStorage("DARK"))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStorage("unknown"))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStorage(null))
    }

    @Test
    fun themeModesResolveWithoutReadingVisualScaleSettings() {
        assertTrue(AppThemeMode.SYSTEM.resolve(systemDarkTheme = true))
        assertFalse(AppThemeMode.SYSTEM.resolve(systemDarkTheme = false))
        assertFalse(AppThemeMode.LIGHT.resolve(systemDarkTheme = true))
        assertTrue(AppThemeMode.DARK.resolve(systemDarkTheme = false))
    }

    @Test
    fun systemBarsKeepBackgroundAndIconsReadableInBothThemes() {
        val dark = vigiliaSystemBarStyle(darkTheme = true)
        assertEquals(0xFF090812.toInt(), dark.backgroundArgb)
        assertFalse(dark.useDarkIcons)

        val light = vigiliaSystemBarStyle(darkTheme = false)
        assertEquals(0xFFF7F4FA.toInt(), light.backgroundArgb)
        assertTrue(light.useDarkIcons)
    }
}
