package com.blackatsystems.miguardia.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class VigiliaContrastTest {
    @Test fun textRolesPassOnEveryActualSurfaceAndHeroGradient() {
        for (dark in listOf(false, true)) {
            val scheme = vigiliaColorScheme(dark)
            val roles = if (dark) VigiliaDarkColors else VigiliaLightColors
            val surfaces = listOf(scheme.background, scheme.surface, scheme.surfaceContainerLowest,
                scheme.surfaceContainerLow, scheme.surfaceContainer, scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest, roles.cellNeutral, roles.selection) +
                (0..10).map { lerp(roles.surfaceHero, roles.heroEnd, it / 10f) }
            for (surface in surfaces) {
                assertContrast(scheme.onSurface, surface)
                assertContrast(roles.onSurfaceMuted, surface)
            }
            listOf(scheme.onPrimary to scheme.primary, scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onSecondary to scheme.secondary, scheme.onSecondaryContainer to scheme.secondaryContainer,
                scheme.onTertiary to scheme.tertiary, scheme.onTertiaryContainer to scheme.tertiaryContainer,
                scheme.onError to scheme.error, scheme.onErrorContainer to scheme.errorContainer,
                scheme.inverseOnSurface to scheme.inverseSurface, roles.onActive to roles.active,
                roles.onSuccess to roles.success, roles.onWarning to roles.warning, roles.onInfo to roles.info,
                roles.onVacation to roles.vacation, roles.onSelection to roles.selection,
                roles.onDateContainer to roles.dateContainer, roles.onCompletedContainer to roles.completedContainer,
                roles.onPlannedContainer to roles.plannedContainer).forEach { (text, surface) ->
                assertContrast(text, surface)
            }
        }
    }

    @Test fun enabledControlsKeepContrastAcrossFocusAndPressedLayers() {
        for (dark in listOf(false, true)) {
            val scheme = vigiliaColorScheme(dark)
            val roles = if (dark) VigiliaDarkColors else VigiliaLightColors
            for (surface in listOf(scheme.background, scheme.surface, roles.cellNeutral, roles.selection)) {
                assertContrast(scheme.outline, surface, 3f)
                assertContrast(scheme.primary, surface)
                for (alpha in listOf(.08f, .12f)) {
                    val stateSurface = scheme.primary.copy(alpha = alpha).compositeOver(surface)
                    assertContrast(scheme.onSurface, stateSurface)
                }
            }
            for (alpha in listOf(.08f, .12f)) {
                assertContrast(scheme.onPrimary,
                    scheme.onPrimary.copy(alpha = alpha).compositeOver(scheme.primary))
            }
        }
    }

    private fun assertContrast(foreground: Color, background: Color, minimum: Float = 4.5f) {
        val first = foreground.luminance(); val second = background.luminance()
        val contrast = (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
        assertTrue("$foreground / $background: $contrast < $minimum", contrast >= minimum)
    }
}
