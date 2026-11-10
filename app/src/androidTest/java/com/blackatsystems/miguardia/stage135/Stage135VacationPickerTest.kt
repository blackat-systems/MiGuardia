package com.blackatsystems.miguardia.stage135

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.ui.components.PICKER_CANCEL_TAG
import com.blackatsystems.miguardia.ui.components.PICKER_CONFIRM_TAG
import com.blackatsystems.miguardia.ui.vacation.VacationActions
import com.blackatsystems.miguardia.ui.vacation.VacationDraft
import com.blackatsystems.miguardia.ui.vacation.VacationSurface
import com.blackatsystems.miguardia.ui.vacation.VacationSurfaceHost
import com.blackatsystems.miguardia.ui.vacation.VacationUiState
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Etapa 13.5: the existing Vacations date buttons now open the shared calendar picker
 * (Argentine Spanish, Monday first, exact UTC date). Synthetic data only, no Room.
 */
class Stage135VacationPickerTest {
    @get:Rule val compose = createComposeRule()

    private var state by mutableStateOf(editorState(START, END))
    private val startPicks = mutableListOf<LocalDate>()
    private val endPicks = mutableListOf<LocalDate>()
    private var saves = 0

    private fun setEditor(fontScale: TestFontScale? = null, phoneBox: Boolean = false) {
        val actions = VacationActions(
            updateStartDate = { date ->
                startPicks += date
                state = state.copy(draft = state.draft.copy(startDate = date, isDirty = true))
            },
            updateEndDate = { date ->
                endPicks += date
                state = state.copy(draft = state.draft.copy(endDateInclusive = date, isDirty = true))
            },
            save = { saves++ },
        )
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = true, fontScale = fontScale) {
                if (phoneBox) {
                    Box(Modifier.requiredSize(width = 360.dp, height = 800.dp)) {
                        VacationSurfaceHost(state, actions)
                    }
                } else {
                    VacationSurfaceHost(state, actions)
                }
            }
        }
    }

    private fun dateButton(text: String) = compose.onNode(hasText(text) and hasClickAction())

    @Test
    fun pickerConfirmsTheShownDateExactlyAndNeverSaves() {
        setEditor()

        dateButton("Fecha inicial: 30 de agosto de 2026").performScrollTo().performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        // Year boundary: the UTC conversion keeps 31/12 instead of shifting a day.
        dateButton("Fecha final inclusiva: 31 de diciembre de 2026").performScrollTo().performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()

        compose.runOnIdle {
            assertEquals(listOf(START), startPicks)
            assertEquals(listOf(END), endPicks)
            assertEquals(0, saves)
            assertEquals(VacationSurface.EDITOR, state.surface)
        }
        dateButton("Fecha inicial: 30 de agosto de 2026").assertExists()
        dateButton("Fecha final inclusiva: 31 de diciembre de 2026").assertExists()
    }

    @Test
    fun cancelChangesNothing() {
        setEditor()

        dateButton("Fecha inicial: 30 de agosto de 2026").performScrollTo().performClick()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).performClick()
        dateButton("Fecha final inclusiva: 31 de diciembre de 2026").performScrollTo().performClick()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).performClick()

        compose.onNodeWithTag(PICKER_CONFIRM_TAG).assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(startPicks.isEmpty())
            assertTrue(endPicks.isEmpty())
            assertEquals(0, saves)
            assertEquals(START, state.draft.startDate)
            assertEquals(END, state.draft.endDateInclusive)
        }
    }

    @Test
    fun emptyDateOpensWithNoDayChosenAndAcceptStaysDisabled() {
        // Same rule as before stage 13.5 (found by the independent review): an empty date must
        // not become "today" just by tapping Aceptar; the person has to pick a day.
        state = editorState(null, END)
        setEditor()

        dateButton("Fecha inicial: Elegir").performScrollTo().performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).performClick()
        compose.runOnIdle {
            assertTrue(startPicks.isEmpty())
            assertEquals(0, saves)
        }
    }

    @Test
    fun pickerWeekStartsOnMondayLikeTheCalendar() {
        setEditor()

        dateButton("Fecha inicial: 30 de agosto de 2026").performScrollTo().performClick()
        // Weekday headers expose the full es-AR name; Monday must come before Sunday.
        val monday = compose.onAllNodesWithContentDescription("lunes", ignoreCase = true)
            .fetchSemanticsNodes().first().boundsInRoot
        val sunday = compose.onAllNodesWithContentDescription("domingo", ignoreCase = true)
            .fetchSemanticsNodes().first().boundsInRoot
        assertTrue("El selector debe empezar la semana en lunes", monday.left < sunday.left)
        compose.onNodeWithTag(PICKER_CANCEL_TAG).performClick()
    }

    @Test
    fun extraLargeFontKeepsBothDateButtonsUsableInAPhoneSizedBox() {
        setEditor(fontScale = TestFontScale.EXTRA_LARGE, phoneBox = true)

        dateButton("Fecha inicial: 30 de agosto de 2026").performScrollTo().performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).assertIsDisplayed()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).assertIsDisplayed().performClick()
        dateButton("Fecha final inclusiva: 31 de diciembre de 2026").performScrollTo().performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).assertIsDisplayed()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).assertIsDisplayed().performClick()

        compose.runOnIdle {
            assertTrue(startPicks.isEmpty())
            assertTrue(endPicks.isEmpty())
            assertEquals(0, saves)
        }
    }

    private companion object {
        val START: LocalDate = LocalDate.of(2026, 8, 30)
        val END: LocalDate = LocalDate.of(2026, 12, 31)

        fun editorState(start: LocalDate?, end: LocalDate?) = VacationUiState(
            surface = VacationSurface.EDITOR,
            visibleMonth = YearMonth.of(2026, 8),
            draft = VacationDraft(startDate = start, endDateInclusive = end),
        )
    }
}
