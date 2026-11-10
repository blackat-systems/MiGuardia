package com.blackatsystems.miguardia.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeModeTest {
    @Test
    fun missingThemeStartsInVigiliaDarkRegardlessOfSystemTheme() {
        val initial = AppThemeMode.fromStorage(null)
        assertEquals(AppThemeMode.DARK, initial)
        assertTrue(initial.resolve(systemDarkTheme = false))
        assertTrue(initial.resolve(systemDarkTheme = true))
    }

    @Test
    fun explicitStoredChoicesRemainUnchanged() {
        AppThemeMode.entries.forEach { choice ->
            assertEquals(choice, AppThemeMode.fromStorage(choice.name))
        }
    }

    @Test
    fun invalidStoredThemeUsesTheSameDarkDefault() {
        assertEquals(AppThemeMode.DEFAULT, AppThemeMode.fromStorage("unknown"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromStorage(""))
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
        assertEquals(0xFF13141C.toInt(), dark.backgroundArgb)
        assertFalse(dark.useDarkIcons)

        val light = vigiliaSystemBarStyle(darkTheme = false)
        assertEquals(0xFFF4F5FA.toInt(), light.backgroundArgb)
        assertTrue(light.useDarkIcons)
    }
}
