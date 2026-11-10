package com.blackatsystems.miguardia

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.calendar.CalendarDay
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.ui.components.PICKER_CANCEL_TAG
import com.blackatsystems.miguardia.ui.components.PICKER_CONFIRM_TAG
import com.blackatsystems.miguardia.ui.DayDetailSheet
import com.blackatsystems.miguardia.ui.Stage2DayActions
import com.blackatsystems.miguardia.ui.weather.WeatherUiState
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Controlled asynchronous result regression. No persistence or external picker claim. */
class MedicalLeaveDraftComposeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val date = LocalDate.of(2026, 10, 23)

    @Test
    fun deferredMedicalRejectionKeepsFieldsAndOnlySuccessDismisses() {
        val callbacks = mutableListOf<(Boolean) -> Unit>()
        val submissions = mutableListOf<List<Any?>>()
        compose.setContent {
            AndroidFontScaleTheme {
                DayDetailSheet(day = CalendarDay(date, emptyList(), null, false),
                    referenceInstant = Instant.parse("2026-11-01T12:00:00Z"), onEditDay = null,
                    onOpenNotes = {}, onOpenNotifications = {}, onOpenWeather = {}, weatherState = WeatherUiState(),
                    stage2Actions = Stage2DayActions(saveMedicalLeave = { previous, start, end, note, completed ->
                        submissions += listOf(previous, start, end, note)
                        callbacks += completed
                    }))
            }
        }
        openEditor()
        fill("medical-leave-start", "23/10/2026")
        fill("medical-leave-end", "25/10/2026")
        fill("medical-leave-note", "Borrador médico sintético")
        compose.onNodeWithTag("medical-leave-save").performClick()
        listOf("start", "end", "note", "save", "cancel").forEach {
            compose.onNodeWithTag("medical-leave-$it").assertIsNotEnabled()
        }
        assertEquals(1, callbacks.size)
        assertEquals(listOf(null, date, date.plusDays(2), "Borrador médico sintético"), submissions.single())
        compose.onNodeWithTag("medical-leave-error").assertDoesNotExist()
        compose.runOnIdle { callbacks[0](false) }
        compose.onNodeWithTag("medical-leave-dialog").assertIsDisplayed()
        compose.onNodeWithTag("medical-leave-start").assertTextContains("23/10/2026")
        compose.onNodeWithTag("medical-leave-end").assertTextContains("25/10/2026")
        compose.onNodeWithTag("medical-leave-note").assertTextContains("Borrador médico sintético")
        compose.onNodeWithTag("medical-leave-save").assertIsEnabled()
        compose.onNodeWithTag("medical-leave-error").assertIsDisplayed()
        compose.onNodeWithTag("medical-leave-cancel").performClick()
        compose.onNodeWithTag("day-add-medical-leave").performScrollTo().performClick()
        compose.onNodeWithTag("medical-leave-error").assertDoesNotExist()
        compose.runOnIdle { callbacks[0](false) }
        compose.onNodeWithTag("medical-leave-error").assertDoesNotExist()
        fill("medical-leave-end", "22/10/2026")
        compose.onNodeWithTag("medical-leave-save").assertIsNotEnabled()
        fill("medical-leave-end", "26/10/2026")
        compose.onNodeWithTag("medical-leave-save").performClick()
        assertEquals(2, callbacks.size)
        compose.onNodeWithTag("medical-leave-error").assertDoesNotExist()
        // A duplicate late success from the rejected request cannot complete the new one.
        compose.runOnIdle { callbacks[0](true) }
        compose.runOnIdle { callbacks[0](false) }
        compose.onNodeWithTag("medical-leave-error").assertDoesNotExist()
        compose.onNodeWithTag("medical-leave-dialog").assertIsDisplayed()
        compose.onNodeWithTag("medical-leave-save").assertIsNotEnabled()
        compose.runOnIdle { callbacks[1](true) }
        compose.onNodeWithTag("medical-leave-dialog").assertDoesNotExist()
    }

    @Test
    fun abandonedMedicalEditorIgnoresItsLateResultAfterAnotherOpening() {
        val callbacks = mutableListOf<(Boolean) -> Unit>()
        var mounted by mutableStateOf(true)
        compose.setContent {
            AndroidFontScaleTheme {
                if (mounted) DayDetailSheet(day = CalendarDay(date, emptyList(), null, false),
                    referenceInstant = Instant.parse("2026-11-01T12:00:00Z"), onEditDay = null,
                    onOpenNotes = {}, onOpenNotifications = {}, onOpenWeather = {}, weatherState = WeatherUiState(),
                    stage2Actions = Stage2DayActions(saveMedicalLeave = { _, _, _, _, completed -> callbacks += completed }))
            }
        }
        openEditor()
        compose.onNodeWithTag("medical-leave-save").performClick()
        compose.runOnIdle { mounted = false }
        compose.onNodeWithTag("medical-leave-dialog").assertDoesNotExist()
        compose.runOnIdle { mounted = true }
        openEditor()
        fill("medical-leave-note", "Otra apertura propia")
        compose.runOnIdle { callbacks[0](true) }
        compose.onNodeWithTag("medical-leave-dialog").assertIsDisplayed()
        compose.onNodeWithTag("medical-leave-note").assertTextContains("Otra apertura propia")
        compose.onNodeWithTag("medical-leave-save").assertIsEnabled()
        compose.onNodeWithTag("medical-leave-save").performClick()
        assertEquals(2, callbacks.size)
        compose.runOnIdle { callbacks[1](true) }
        compose.onNodeWithTag("medical-leave-dialog").assertDoesNotExist()
    }

    @Test
    fun medicalCalendarGivesTheSameTextAsTypingCancelChangesNothingAndNeverSaves() {
        val submissions = mutableListOf<List<Any?>>()
        compose.setContent {
            AndroidFontScaleTheme {
                MedicalSheet { previous, start, end, note -> submissions += listOf(previous, start, end, note) }
            }
        }
        openEditor()
        // Typed text and the calendar agree: confirming the typed date writes the same text.
        fill("medical-leave-start", "23/10/2026")
        compose.onNodeWithTag("medical-leave-start-picker").performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        compose.onNodeWithTag("medical-leave-start").assertTextContains("23/10/2026")
        fill("medical-leave-end", "25/10/2026")
        compose.onNodeWithTag("medical-leave-end-picker").performClick()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).performClick()
        compose.onNodeWithTag("medical-leave-end").assertTextContains("25/10/2026")
        compose.onNodeWithTag("medical-leave-end-picker").performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        compose.onNodeWithTag("medical-leave-end").assertTextContains("25/10/2026")
        // An unreadable end starts on the chosen start and writes DD/MM/AAAA.
        fill("medical-leave-end", "fin")
        compose.onNodeWithTag("medical-leave-save").assertIsNotEnabled()
        compose.onNodeWithTag("medical-leave-end-picker").performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        compose.onNodeWithTag("medical-leave-end").assertTextContains("23/10/2026")
        // The calendar never saves: the dialog stays open and nothing was submitted.
        compose.onNodeWithTag("medical-leave-dialog").assertIsDisplayed()
        compose.onNodeWithTag("medical-leave-save").assertIsEnabled()
        compose.runOnIdle { assertEquals(0, submissions.size) }
    }

    @Test
    fun medicalCalendarButtonsStayUsableWithAndroidTextAtTwoHundredPercent() {
        val submissions = mutableListOf<List<Any?>>()
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = false, fontScale = TestFontScale.EXTRA_LARGE) {
                Box(Modifier.requiredSize(width = 360.dp, height = 800.dp)) {
                    MedicalSheet { previous, start, end, note -> submissions += listOf(previous, start, end, note) }
                }
            }
        }
        openEditor()
        compose.onNodeWithTag("medical-leave-end-picker").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).assertIsDisplayed().performClick()
        compose.onNodeWithTag("medical-leave-start-picker").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).assertIsDisplayed().performClick()
        compose.onNodeWithTag("medical-leave-start").assertTextContains("23/10/2026")
        compose.onNodeWithTag("medical-leave-end").assertTextContains("23/10/2026")
        compose.runOnIdle { assertEquals(0, submissions.size) }
    }

    @Composable
    private fun MedicalSheet(onSave: (MedicalLeave?, LocalDate, LocalDate, String?) -> Unit) {
        DayDetailSheet(day = CalendarDay(date, emptyList(), null, false),
            referenceInstant = Instant.parse("2026-11-01T12:00:00Z"), onEditDay = null,
            onOpenNotes = {}, onOpenNotifications = {}, onOpenWeather = {}, weatherState = WeatherUiState(),
            stage2Actions = Stage2DayActions(saveMedicalLeave = { previous, start, end, note, _ ->
                onSave(previous, start, end, note)
            }))
    }

    private fun openEditor() {
        compose.onNodeWithTag("day-other-options").performScrollTo().performClick()
        compose.onNodeWithTag("day-add-medical-leave").performScrollTo().performClick()
        compose.onNodeWithTag("medical-leave-dialog").assertIsDisplayed()
    }
    private fun fill(tag: String, text: String) = compose.onNodeWithTag(tag).performTextReplacement(text)
}
