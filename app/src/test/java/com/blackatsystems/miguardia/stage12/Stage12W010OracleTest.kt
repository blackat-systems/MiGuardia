package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.core.domain.widget.WidgetSize
import com.blackatsystems.miguardia.widget.buildWidgetRenderText
import com.blackatsystems.miguardia.widget.resolveWidgetPalette
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class Stage12W010OracleTest {
    private val fixture = Stage12W010Fixture(UUID.fromString("649b72bc-4bbd-4961-8ee2-27de5ab92b58"))
    @Test fun allSecretsAndYearCrossingFeedTheRealHiddenProjectionAndLiteralOracle() {
        fixture.requireAllSecretsPresent()
        for (boundary in Stage12W010Fixture.Boundary.entries) for (mode in WidgetMode.entries) for (size in WidgetSize.entries) {
            val projection = fixture.projection(boundary, mode, size)
            check(projection.mode == mode && projection.size == size)
            Stage12W010Oracle.requireProjection(projection, fixture, boundary)
            Stage12W010Oracle.requireRenderText(buildWidgetRenderText(projection))
        }
    }
    @Test fun independentOracleRejectsCompleteProjectionAndLeakedCounts() {
        val b = Stage12W010Fixture.Boundary.BEFORE
        val hidden = fixture.projection(b, WidgetMode.AUTOMATIC, WidgetSize.EXPANDED)
        val complete = fixture.projection(b, WidgetMode.AUTOMATIC, WidgetSize.EXPANDED, WidgetPrivacy.COMPLETE)
        assertThrows(IllegalStateException::class.java) { Stage12W010Oracle.requireProjection(complete, fixture, b) }
        assertThrows(IllegalStateException::class.java) { Stage12W010Oracle.requireProjection(hidden.copy(totalSimultaneousEvents = 9), fixture, b) }
        assertThrows(IllegalStateException::class.java) { Stage12W010Oracle.requireProjection(hidden.copy(events = complete.events), fixture, b) }
    }
    @Test fun textAndColorAdversariesIncludeInvisibleCountersAndPrivateTint() {
        for (secret in fixture.forbiddenText + setOf("+8", "9", "01/01/2027", "22:17")) {
            assertThrows(IllegalStateException::class.java) { Stage12W010Oracle.requireNoSecretText(listOf(secret), fixture) }
        }
        Stage12W010Oracle.requireNoSecretText(Stage12W010Oracle.visibleText, fixture)
        assertThrows(IllegalStateException::class.java) { Stage12W010Oracle.requireNeutralColor(fixture.privateColor, fixture) }
    }
    @Test fun independentPaletteUsesLiteralNeutralValuesForBothThemes() {
        for (dark in listOf(false, true)) {
            val expected = Stage12W010Oracle.palette(dark)
            val actual = resolveWidgetPalette(if (dark) AppThemeMode.DARK else AppThemeMode.LIGHT, !dark)
            assertEquals(expected.background, actual.background)
            assertEquals(expected.primary, actual.primaryText)
            assertEquals(expected.secondary, actual.secondaryText)
            assertEquals(expected.accent, actual.defaultAccent)
            assertEquals(expected.action, actual.action)
        }
    }
}
