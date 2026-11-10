package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.domain.widget.WidgetContentState
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetNavigation
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.core.domain.widget.WidgetProjection
import com.blackatsystems.miguardia.widget.WidgetRenderText

/** Independent literal/invariant oracle. Does not call a projection, renderer, formatter or palette resolver. */
internal object Stage12W010Oracle {
    const val DOMAIN_KEY = "W010-sin-fecha-hora-tipo-contador-simultaneos-color-clima.domain_jvm"
    const val REMOTE_KEY = "W010-sin-fecha-hora-tipo-contador-simultaneos-color-clima.remote_views_real"
    const val TITLE = "MIGUARDIA"
    const val PRIMARY = "Tenés información en MiGuardia."
    const val ROOT_DESCRIPTION = "MIGUARDIA. Tenés información en MiGuardia."
    val visibleText = setOf(TITLE, PRIMARY, "Configurar", ROOT_DESCRIPTION, "Reconfigurar este Widget de inicio")
    data class Palette(val background: Int, val primary: Int, val secondary: Int, val accent: Int, val action: Int)
    fun palette(dark: Boolean) = if (dark) Palette(0xFF1E2030.toInt(), 0xFFE6EAFF.toInt(),
        0xFFA9B1D6.toInt(), 0xFF7AA2F7.toInt(), 0xFF7AA2F7.toInt())
    else Palette(0xFFFFFFFF.toInt(), 0xFF1F2440.toInt(), 0xFF5B6180.toInt(), 0xFF2F5DC6.toInt(), 0xFF2F5DC6.toInt())

    fun requireProjection(actual: WidgetProjection, fixture: Stage12W010Fixture, boundary: Stage12W010Fixture.Boundary) {
        check(actual.privacy == WidgetPrivacy.HIDDEN && actual.events.isEmpty() && actual.totalSimultaneousEvents == 0 &&
            actual.countdown == null && actual.dayOff == null) { "W010_PROJECTION_PRIVATE_FIELD" }
        val expected = when (actual.mode) {
            WidgetMode.NEXT_DAY_OFF -> WidgetContentState.DAY_OFF
            WidgetMode.NEXT_SHIFT -> if (boundary == Stage12W010Fixture.Boundary.BEFORE) WidgetContentState.EVENTS else WidgetContentState.EMPTY
            WidgetMode.AUTOMATIC -> if (boundary == Stage12W010Fixture.Boundary.END_EXACT) WidgetContentState.DAY_OFF else WidgetContentState.EVENTS
        }
        check(actual.state == expected && actual.referenceInstant == fixture.now(boundary)) { "W010_PROJECTION_STATE" }
        when (expected) {
            WidgetContentState.EVENTS -> {
                val navigation = actual.navigation as? WidgetNavigation.Shift
                val first = fixture.shifts.minBy { it.shift.id.toString() }.shift.id
                check(navigation != null && navigation.shiftId == first &&
                    navigation.ownerLocalDate == fixture.ownerDate) { "W010_TYPED_NAVIGATION" }
            }
            WidgetContentState.DAY_OFF -> check(actual.navigation == WidgetNavigation.Date(fixture.dayOff)) { "W010_TYPED_DATE" }
            WidgetContentState.EMPTY -> check(actual.navigation == WidgetNavigation.Calendar) { "W010_EMPTY_NAVIGATION" }
            WidgetContentState.CONFIGURATION_INCOMPLETE -> error("W010_UNEXPECTED_CONFIG_STATE")
        }
    }
    fun requireRenderText(actual: WidgetRenderText) {
        check(actual.title == TITLE && actual.primary == PRIMARY && actual.schedule == null && actual.rows.isEmpty() &&
            actual.simultaneous == null && actual.countdownFormat == null) { "W010_RENDER_TEXT_PRIVATE_FIELD" }
    }
    fun requireNoSecretText(values: Iterable<String>, fixture: Stage12W010Fixture) {
        check(values.none { text -> text.any(Char::isDigit) ||
            fixture.forbiddenText.any { secret -> text.contains(secret, ignoreCase = true) } }) { "W010_APPLIED_SECRET_TEXT" }
    }
    fun requireNeutralColor(color: Int, fixture: Stage12W010Fixture) {
        check((color and 0x00FFFFFF) != (fixture.privateColor and 0x00FFFFFF)) { "W010_APPLIED_PRIVATE_COLOR" }
    }
    fun retain(primary: Throwable?, secondary: Throwable): Throwable {
        if (primary == null) return secondary
        if (primary !== secondary && primary.suppressed.none { it === secondary }) primary.addSuppressed(secondary)
        return primary
    }
}
