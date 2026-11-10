package com.blackatsystems.miguardia.stage135

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.calendar.CalendarDay
import com.blackatsystems.miguardia.core.domain.calendar.CalendarShift
import com.blackatsystems.miguardia.core.domain.calendar.ShiftTemporalStatus
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftSituationExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAvailability
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.ui.CalendarAvailabilityInputDialog
import com.blackatsystems.miguardia.ui.DayDetailSheet
import com.blackatsystems.miguardia.ui.NOTICE_FIELD_TAG
import com.blackatsystems.miguardia.ui.Stage2DayActions
import com.blackatsystems.miguardia.ui.components.PICKER_CANCEL_TAG
import com.blackatsystems.miguardia.ui.components.PICKER_CONFIRM_TAG
import com.blackatsystems.miguardia.ui.noticeWithDate
import com.blackatsystems.miguardia.ui.noticeWithTime
import com.blackatsystems.miguardia.ui.weather.WeatherUiState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Stage 13.5 pickers in the Calendar's "Agregar disponibilidad" dialog and in the day detail's
 * cancellation dialog ("Fecha y hora del aviso"). Synthetic data, no Room. A picker only writes
 * the same text a person would type; reviewing or confirming stays the person's step.
 */
class Stage135CalendarDialogPickersTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // ---- Calendar · varios días · «Agregar disponibilidad» (Desde / Hasta) ----

    @Test
    fun availabilityClockGivesTheSameTextAsTypingAndCancelChangesNothing() {
        val continued = mutableListOf<CalendarBatchAvailability>()
        compose.setContent {
            AndroidFontScaleTheme {
                CalendarAvailabilityInputDialog(onDismiss = {}, onContinue = { continued += it })
            }
        }
        // Upper limit typed, then confirmed in the clock: same text.
        compose.onNodeWithTag(START).performTextInput("2359")
        compose.onNodeWithTag(START).assertTextContains("23:59")
        compose.onNodeWithTag("$START-picker").performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        compose.onNodeWithTag(START).assertTextContains("23:59")

        // Lower limit typed; cancelling the clock keeps it, confirming writes the same text.
        compose.onNodeWithTag(END).performTextInput("0000")
        compose.onNodeWithTag(END).assertTextContains("00:00")
        compose.onNodeWithTag("$END-picker").performScrollTo().performClick()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).performClick()
        compose.onNodeWithTag(END).assertTextContains("00:00")
        compose.onNodeWithTag("$END-picker").performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        compose.onNodeWithTag(END).assertTextContains("00:00")

        // The typed "19:00" form that the batch flow uses keeps working.
        compose.onNodeWithTag(START).performTextReplacement("19:00")
        compose.onNodeWithTag(START).assertTextContains("19:00")

        // The pickers never review on their own.
        compose.runOnIdle { assertTrue(continued.isEmpty()) }
        compose.onNodeWithTag(REVIEW).assertIsEnabled()
    }

    @Test
    fun availabilityCrossingMidnightReachesReviewOnlyWhenThePersonTapsIt() {
        val continued = mutableListOf<CalendarBatchAvailability>()
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = false) {
                CalendarAvailabilityInputDialog(onDismiss = {}, onContinue = { continued += it })
            }
        }
        compose.onNodeWithTag(START).performTextInput("1900")
        compose.onNodeWithTag("$START-picker").performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        compose.onNodeWithTag(END).performTextInput("1900")
        // Same start and end stays blocked, as before.
        compose.onNodeWithTag(REVIEW).assertIsNotEnabled()
        compose.onNodeWithTag(END).performTextReplacement("07:00")
        compose.onNodeWithTag("$END-picker").performScrollTo().performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        compose.onNodeWithTag(END).assertTextContains("07:00")
        compose.runOnIdle { assertTrue(continued.isEmpty()) }

        compose.onNodeWithTag(REVIEW).assertIsEnabled().performClick()
        compose.runOnIdle {
            val window = continued.single()
            assertEquals(LocalTime.of(19, 0), window.start)
            assertEquals(LocalTime.of(7, 0), window.end)
        }
    }

    @Test
    fun availabilityClockStaysUsableWithAndroidTextAtTwoHundredPercent() {
        val continued = mutableListOf<CalendarBatchAvailability>()
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = true, fontScale = TestFontScale.EXTRA_LARGE) {
                Box(Modifier.requiredSize(width = 360.dp, height = 800.dp)) {
                    CalendarAvailabilityInputDialog(onDismiss = {}, onContinue = { continued += it })
                }
            }
        }
        compose.onNodeWithTag("$END-picker").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).assertIsDisplayed().performClick()
        compose.onNodeWithTag("$START-picker").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(continued.isEmpty()) }
    }

    // ---- Detalle del día · Cancelación · «Fecha y hora del aviso» ----

    @Test
    fun noticeCalendarAndClockReplaceOnlyTheirPartAndNeverConfirm() {
        var confirmations = 0
        setDayDetail(onChange = { confirmations++ })
        openCancellation()

        // Upper limit: the clock and the calendar keep the typed text.
        compose.onNodeWithTag(NOTICE_FIELD_TAG).performTextReplacement("23/10/2026 23:59")
        compose.onNodeWithTag(TIME_PICKER).performScrollTo().performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        assertNotice("23/10/2026 23:59")
        compose.onNodeWithTag(DATE_PICKER).performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        assertNotice("23/10/2026 23:59")

        // Lower limit; cancelling either picker changes nothing.
        compose.onNodeWithTag(NOTICE_FIELD_TAG).performTextReplacement("23/10/2026 00:00")
        compose.onNodeWithTag(TIME_PICKER).performClick()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).performClick()
        assertNotice("23/10/2026 00:00")
        compose.onNodeWithTag(DATE_PICKER).performClick()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).performClick()
        assertNotice("23/10/2026 00:00")
        compose.onNodeWithTag(TIME_PICKER).performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        assertNotice("23/10/2026 00:00")

        // An unreadable time: the clock replaces only the time and keeps the typed date.
        compose.onNodeWithTag(NOTICE_FIELD_TAG).performTextReplacement("23/10/2026 xx")
        compose.onNodeWithTag(TIME_PICKER).performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        val withTime = notice()
        assertTrue(withTime, withTime.startsWith("23/10/2026 ") && NOTICE_PATTERN.matches(withTime))

        // An unreadable date: the calendar replaces only the date and keeps the typed time.
        compose.onNodeWithTag(NOTICE_FIELD_TAG).performTextReplacement("aviso 07:30")
        compose.onNodeWithTag(DATE_PICKER).performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        val withDate = notice()
        assertTrue(withDate, withDate.endsWith(" 07:30") && NOTICE_PATTERN.matches(withDate))

        // Saved without review, as today: only "Confirmar" sends it, never a picker.
        compose.onNodeWithTag("shift-situation-dialog").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, confirmations) }
    }

    @Test
    fun noticePickersStayUsableWithAndroidTextAtTwoHundredPercent() {
        var confirmations = 0
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = false, fontScale = TestFontScale.EXTRA_LARGE) {
                Box(Modifier.requiredSize(width = 360.dp, height = 800.dp)) {
                    DayDetail(onChange = { confirmations++ })
                }
            }
        }
        openCancellation()
        compose.onNodeWithTag(NOTICE_FIELD_TAG).performTextReplacement("23/10/2026 07:30")
        compose.onNodeWithTag(TIME_PICKER).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).assertIsDisplayed().performClick()
        compose.onNodeWithTag(DATE_PICKER).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).assertIsDisplayed().performClick()
        assertNotice("23/10/2026 07:30")
        compose.runOnIdle { assertEquals(0, confirmations) }
    }

    @Test
    fun noticeTextHelpersKeepTheOtherPartAndTheTypedFormat() {
        val fallback = LocalDateTime.of(2026, 11, 9, 14, 5)
        assertEquals("01/02/2027 07:30", noticeWithDate("23/10/2026 07:30", LocalDate.of(2027, 2, 1), fallback))
        assertEquals("23/10/2026 00:00", noticeWithTime("23/10/2026 07:30", LocalTime.MIDNIGHT, fallback))
        assertEquals("23/10/2026 23:59", noticeWithTime("23/10/2026 07:30", LocalTime.of(23, 59), fallback))
        // Unreadable parts take the prefilled value; strict dates reject 31/02.
        assertEquals("09/11/2026 23:59", noticeWithTime("31/02/2026 07:30", LocalTime.of(23, 59), fallback))
        assertEquals("01/01/2027 14:05", noticeWithDate("", LocalDate.of(2027, 1, 1), fallback))
    }

    private fun setDayDetail(onChange: () -> Unit) {
        compose.setContent { AndroidFontScaleTheme { DayDetail(onChange) } }
    }

    @androidx.compose.runtime.Composable
    private fun DayDetail(onChange: () -> Unit) {
        DayDetailSheet(
            day = CalendarDay(DATE, listOf(CalendarShift(SHIFT, ShiftTemporalStatus.UPCOMING)), null, false),
            referenceInstant = Instant.parse("2026-10-01T12:00:00Z"),
            onEditDay = null,
            onOpenNotes = {},
            onOpenNotifications = {},
            onOpenWeather = {},
            weatherState = WeatherUiState(),
            stage2Actions = Stage2DayActions(changeSituation = { _, _, _, _, _, _, _, _ -> onChange() }),
            stage2Expectations = mapOf(SHIFT.id to expectation(SHIFT)),
        )
    }

    private fun openCancellation() {
        compose.onNodeWithTag("day-other-options").performScrollTo().performClick()
        compose.onNodeWithTag("shift-cancel-${SHIFT.id}").performScrollTo().performClick()
        compose.onNodeWithTag("shift-situation-dialog").assertIsDisplayed()
    }

    private fun notice(): String =
        compose.onNodeWithTag(NOTICE_FIELD_TAG).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    // Reading a node already waits for idle; it must not run inside runOnIdle (main thread).
    private fun assertNotice(expected: String) = assertEquals(expected, notice())

    private companion object {
        const val START = "calendar-availability-start"
        const val END = "calendar-availability-end"
        const val REVIEW = "calendar-availability-review"
        const val DATE_PICKER = "$NOTICE_FIELD_TAG-date-picker"
        const val TIME_PICKER = "$NOTICE_FIELD_TAG-time-picker"
        val NOTICE_PATTERN = Regex("""\d{2}/\d{2}/\d{4} \d{2}:\d{2}""")
        val DATE: LocalDate = LocalDate.of(2026, 10, 23)
        val SHIFT: Shift = shift()

        fun shift(): Shift {
            val start = ZonedDateTime.of(DATE, LocalTime.of(22, 0), AppDefaults.zoneId())
            val end = ZonedDateTime.of(DATE.plusDays(1), LocalTime.of(6, 0), AppDefaults.zoneId())
            return Shift(
                id = UUID.nameUUIDFromBytes("stage135-notice-shift".toByteArray()),
                startAt = start.toInstant(),
                endAt = end.toInstant(),
                zoneId = AppDefaults.zoneId(),
                localStartDate = DATE,
                objectiveNameSnapshot = "Objetivo ficticio",
                objectiveAbbreviationSnapshot = "FIC",
                objectiveAddressSnapshot = null,
                startTimeSnapshot = LocalTime.of(22, 0),
                endTimeSnapshot = LocalTime.of(6, 0),
                colorArgbSnapshot = 0xFF315DA8.toInt(),
                position = null,
                status = ShiftStatus.PLANNED,
                sourceObjectiveId = UUID.nameUUIDFromBytes("stage135-objective".toByteArray()),
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
        }

        fun expectation(shift: Shift) = ShiftSituationExpectation(
            planned = V2ShiftWrite(
                shift,
                ShiftWorkSnapshot(
                    shiftId = shift.id,
                    timelineId = UUID.nameUUIDFromBytes("stage135-timeline".toByteArray()),
                    sector = WorkSector.PRIVATE_SECURITY,
                    configurationRevisionId = UUID.nameUUIDFromBytes("stage135-revision".toByteArray()),
                    workPlaceId = UUID.nameUUIDFromBytes("stage135-place".toByteArray()),
                    objectiveId = shift.sourceObjectiveId,
                    templateId = UUID.nameUUIDFromBytes("stage135-template".toByteArray()),
                    workTypeId = UUID.nameUUIDFromBytes("stage135-type".toByteArray()),
                    workTypeNameSnapshot = "Jornada habitual",
                    workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
                ),
            ),
            previous = null,
            actual = null,
            recurringOccurrence = null,
        )
    }
}
