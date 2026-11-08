package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.theme.VigiliaColors
import com.blackatsystems.miguardia.ui.theme.vigiliaColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VigiliaThemeComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun darkThemeExposesApprovedVigiliaRolesAndContrast() {
        var scheme = androidx.compose.material3.darkColorScheme()
        var roles: VigiliaColors? = null
        compose.setContent {
            MiGuardiaTheme(darkTheme = true) {
                scheme = MaterialTheme.colorScheme
                roles = MaterialTheme.vigiliaColors
            }
        }

        compose.runOnIdle {
            assertEquals(Color(0xFF13141C), scheme.background)
            assertEquals(Color(0xFF1E2030), scheme.surface)
            assertEquals(Color(0xFF7AA2F7), scheme.primary)
            assertEquals(Color(0xFF737AA2), scheme.outline)
            assertEquals(Color(0xFFBB9AF7), roles?.active)
            assertEquals(Color(0xFFA9DC76), roles?.success)
            assertEquals(Color(0xFF73DACA), roles?.vacation)
            assertThemeContrast(scheme, requireNotNull(roles))
        }
    }

    @Test
    fun lightThemeExposesApprovedVigiliaRolesAndContrast() {
        var scheme = androidx.compose.material3.lightColorScheme()
        var roles: VigiliaColors? = null
        compose.setContent {
            MiGuardiaTheme(darkTheme = false) {
                scheme = MaterialTheme.colorScheme
                roles = MaterialTheme.vigiliaColors
            }
        }

        compose.runOnIdle {
            assertEquals(Color(0xFFF4F5FA), scheme.background)
            assertEquals(Color(0xFFFFFFFF), scheme.surface)
            assertEquals(Color(0xFF2F5DC6), scheme.primary)
            assertEquals(Color(0xFF6E7596), scheme.outline)
            assertEquals(Color(0xFF6D4FD6), roles?.active)
            assertEquals(Color(0xFF3C6B20), roles?.success)
            assertEquals(Color(0xFF33635C), roles?.vacation)
            assertThemeContrast(scheme, requireNotNull(roles))
        }
    }

    private fun assertThemeContrast(
        scheme: androidx.compose.material3.ColorScheme,
        roles: VigiliaColors,
    ) {
        assertContrast(scheme.onSurface, scheme.surface)
        assertContrast(roles.onSurfaceMuted, scheme.surface)
        assertContrast(roles.onSurfaceMuted, scheme.surfaceContainerHighest)
        assertContrast(scheme.onSurface, roles.surfaceHero)
        assertContrast(scheme.onSurface, roles.heroEnd)
        assertContrast(roles.onSelection, roles.selection)
        assertContrast(roles.onDateContainer, roles.dateContainer)
        assertContrast(roles.onCompletedContainer, roles.completedContainer)
        assertContrast(roles.onPlannedContainer, roles.plannedContainer)
        assertContrast(scheme.onPrimary, scheme.primary)
        assertContrast(roles.onActive, roles.active)
        assertContrast(roles.onSuccess, roles.success)
        assertContrast(roles.onWarning, roles.warning)
        assertContrast(roles.onInfo, roles.info)
        assertContrast(roles.onVacation, roles.vacation)
    }

    private fun assertContrast(foreground: Color, background: Color) {
        val foregroundLuminance = foreground.luminance()
        val backgroundLuminance = background.luminance()
        val ratio = (maxOf(foregroundLuminance, backgroundLuminance) + 0.05f) /
            (minOf(foregroundLuminance, backgroundLuminance) + 0.05f)
        assertTrue("Contraste insuficiente: $ratio", ratio >= 4.5f)
    }
}
