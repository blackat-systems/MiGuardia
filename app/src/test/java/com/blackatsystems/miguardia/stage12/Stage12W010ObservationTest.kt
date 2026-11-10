package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetSize
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class Stage12W010ObservationTest {
    private val fixture = Stage12W010Fixture(UUID.fromString("1934050e-5603-492b-aef7-fc86647c8082"))
    private val variant = Stage12W010Observation.Variant(false, WidgetSize.COMPACT, WidgetMode.AUTOMATIC,
        Stage12W010Fixture.Boundary.BEFORE)

    @Test fun digitsOnlyAndLiteralCanariesRemainDistinctWithoutPublishingEither() {
        val numeric = diagnostic("876543 horas", Stage12W010Observation.Field.VIEW_DESCRIPTION)
        assertTrue(numeric.message!!.contains("has_digit=true;matches_forbidden_literal=false"))
        val private = diagnostic(fixture.institution, Stage12W010Observation.Field.NODE_TEXT)
        assertTrue(private.message!!.contains("has_digit=true;matches_forbidden_literal=true"))
        assertFalse(numeric.stackTraceToString().contains("876543"))
        assertFalse(private.stackTraceToString().contains(fixture.institution))
        assertNull(private.cause); assertTrue(private.suppressed.isEmpty())
    }

    @Test fun boundedLocationAndNeutralTimerStateIdentifyAFieldWithoutContent() {
        val error = diagnostic("876543 horas", Stage12W010Observation.Field.VIEW_DESCRIPTION)
        assertTrue(error.message!!.contains("theme=LIGHT;size=COMPACT;mode=AUTOMATIC;boundary=BEFORE"))
        assertTrue(error.message!!.contains("field=VIEW_DESCRIPTION;node=COUNTDOWN;kind=CHRONOMETER;visibility=GONE"))
        assertTrue(error.message!!.contains("effectively_visible=false;nodes_visited=7"))
        assertTrue(error.message!!.contains("timer_base_zero=true;timer_format_null=true;timer_count_down=false;timer_text_empty=true"))
        assertTrue(error.message!!.length < 700)
    }

    @Test fun metadataNeverMakesAnOracleRejectionPass() {
        for (value in fixture.forbiddenText + setOf("876543 horas", "+8", "9")) {
            val original = assertThrows(IllegalStateException::class.java) {
                Stage12W010Oracle.requireNoSecretText(listOf(value), fixture)
            }
            assertEquals("W010_APPLIED_SECRET_TEXT", original.message)
            val annotated = diagnostic(value, Stage12W010Observation.Field.TEXT)
            assertTrue(annotated.message!!.startsWith("W010_APPLIED_SECRET_TEXT;phase="))
            assertFalse(annotated.message!!.contains(value))
        }
        Stage12W010Oracle.requireNoSecretText(Stage12W010Oracle.visibleText, fixture)
    }

    private fun diagnostic(value: String, field: Stage12W010Observation.Field): IllegalStateException =
        Stage12W010Observation.failure(value, fixture.forbiddenText, variant, field,
            Stage12W010Observation.Node.COUNTDOWN, Stage12W010Observation.Kind.CHRONOMETER,
            Stage12W010Observation.Visibility.GONE, false, 7, Stage12W010Observation.Timer(true, true, false, true))
}
