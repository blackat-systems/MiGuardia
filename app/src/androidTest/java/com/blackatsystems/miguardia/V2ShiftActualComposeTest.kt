package com.blackatsystems.miguardia

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualClassSelection
import com.blackatsystems.miguardia.core.domain.model.ShiftActualDifferenceChoice
import com.blackatsystems.miguardia.core.domain.model.ShiftActualDraft
import com.blackatsystems.miguardia.core.domain.model.ShiftActualExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftActualFragmentDraft
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.buildShiftActualSaveMutation
import com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.ui.components.PICKER_CANCEL_TAG
import com.blackatsystems.miguardia.ui.components.PICKER_CONFIRM_TAG
import com.blackatsystems.miguardia.ui.management.V2ActualEditorDraft
import com.blackatsystems.miguardia.ui.management.V2ActualFragmentInput
import com.blackatsystems.miguardia.ui.management.V2ExtraClassEditorState
import com.blackatsystems.miguardia.ui.management.V2ShiftActualActions
import com.blackatsystems.miguardia.ui.management.V2ShiftActualDetailCard
import com.blackatsystems.miguardia.ui.management.V2ShiftActualEditorState
import com.blackatsystems.miguardia.ui.management.V2ShiftActualRowState
import com.blackatsystems.miguardia.ui.management.V2ShiftActualStage
import com.blackatsystems.miguardia.ui.management.V2ShiftActualSurface
import com.blackatsystems.miguardia.ui.management.V2ShiftActualSurfaceHost
import com.blackatsystems.miguardia.ui.management.V2ShiftActualUiState
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class V2ShiftActualComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun detailShowsNeutralErrorRetryBoundaryActionAndStableIdentity() {
        val expected = expectation()
        var row: V2ShiftActualRowState by mutableStateOf(V2ShiftActualRowState.Loading)
        var retries = 0
        var begins = 0
        compose.setContent {
            AndroidFontScaleTheme {
                V2ShiftActualDetailCard(
                    shift = expected.planned.shift,
                    ordinal = 2,
                    count = 2,
                    ownerDate = DATE,
                    rowState = row,
                    actions = V2ShiftActualActions(
                        retryInspection = { retries++ },
                        begin = { _, ordinal, count, _ ->
                            assertEquals(2, ordinal)
                            assertEquals(2, count)
                            begins++
                            true
                        },
                    ),
                )
            }
        }

        compose.onNodeWithTag("v2-actual-$SHIFT_ID-loading").assertIsDisplayed()
        compose.runOnIdle { row = V2ShiftActualRowState.Error("Fallo ficticio") }
        compose.onNodeWithText("Fallo ficticio").assertIsDisplayed()
        compose.onNodeWithTag("v2-actual-$SHIFT_ID-retry").performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            row = V2ShiftActualRowState.Content(expected, false, "Todavía no llegó el final")
        }
        compose.onNodeWithText("Todavía no llegó el final").assertIsDisplayed()
        compose.onNodeWithTag("v2-actual-$SHIFT_ID-register").assertDoesNotExist()
        compose.runOnIdle { row = V2ShiftActualRowState.Content(expected, true, null) }
        compose.onNodeWithTag("v2-actual-$SHIFT_ID-register").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("jornada 2 de 2", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(SHIFT_ID.toString(), substring = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, begins) }
    }

    @Test
    fun summaryShowsHumanTimeRangesRegularTwoExtrasAndTotal() {
        val expected = expectationWithTwoExtras()
        compose.setContent {
            AndroidFontScaleTheme {
                V2ShiftActualDetailCard(
                    shift = expected.planned.shift,
                    ordinal = 1,
                    count = 1,
                    ownerDate = DATE,
                    rowState = V2ShiftActualRowState.Content(expected, true, null),
                    actions = V2ShiftActualActions(),
                )
            }
        }

        compose.onNodeWithText("Horario estipulado").assertIsDisplayed()
        compose.onNodeWithText("25/08/2026 08:00 – 25/08/2026 16:00").assertIsDisplayed()
        compose.onNodeWithText("Horario realmente cumplido").assertIsDisplayed()
        compose.onNodeWithText("25/08/2026 07:30 – 25/08/2026 16:30").assertIsDisplayed()
        compose.onNodeWithText("Trabajaste 1 hora más de lo planeado.").assertIsDisplayed()
        compose.onNodeWithText("Horas trabajadas sin extras: 8 h 0 min").assertIsDisplayed()
        compose.onNodeWithText("Extra 1: Horas extras", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Extra 2: Horas extras", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Total real: 9 h 0 min").assertIsDisplayed()
        compose.onNodeWithTag("v2-actual-$SHIFT_ID-correct").assertIsDisplayed()
        compose.onNodeWithTag("v2-actual-$SHIFT_ID-return-planned").assertIsDisplayed()
    }

    @Test
    fun overnightPlannedAndActualSummariesShowBothCivilEndpoints() {
        val base = expectation()
        val night = base.planned.shift.copy(
            startAt = DATE.atTime(22, 0).atZone(ZONE).toInstant(),
            endAt = DATE.plusDays(1).atTime(6, 0).atZone(ZONE).toInstant(),
            startTimeSnapshot = LocalTime.of(22, 0),
            endTimeSnapshot = LocalTime.of(6, 0),
        )
        val expected = base.copy(planned = base.planned.copy(shift = night))
        val actualEnd = DATE.plusDays(1).atTime(7, 30).atZone(ZONE).toInstant()
        val actual = requireNotNull(buildShiftActualSaveMutation(
            expectation = expected,
            draft = ShiftActualDraft(
                actualStart = DATE.atTime(23, 15).atZone(ZONE).toInstant(),
                actualEnd = actualEnd,
                differenceReason = "Horario nocturno declarado",
                explanation = null,
                differenceChoice = ShiftActualDifferenceChoice.ALL_REGULAR,
                classSelection = null,
                fragments = emptyList(),
            ),
            clock = Clock.fixed(actualEnd.plusSeconds(3600), ZONE),
            timestamp = actualEnd.plusSeconds(3600),
        )).replacement
        compose.setContent {
            AndroidFontScaleTheme {
                V2ShiftActualDetailCard(
                    shift = night,
                    ordinal = 1,
                    count = 1,
                    ownerDate = DATE,
                    rowState = V2ShiftActualRowState.Content(expected.copy(previousActual = actual), true, null),
                    actions = V2ShiftActualActions(),
                )
            }
        }

        compose.onNodeWithText("25/08/2026 22:00 – 26/08/2026 06:00").assertIsDisplayed()
        compose.onNodeWithText("25/08/2026 23:15 – 26/08/2026 07:30").assertIsDisplayed()
        compose.onNodeWithText("Total real: 8 h 15 min").assertIsDisplayed()
    }

    @Test
    fun catalogStartsBothAnswersEmptyAndSaveOnlyEnablesAfterExplicitYesNo() {
        var state by mutableStateOf(
            V2ShiftActualUiState(
                surface = V2ShiftActualSurface.CLASS_CATALOG,
                classEditor = V2ExtraClassEditorState(id = CLASS_ID),
            ),
        )
        compose.setContent {
            AndroidFontScaleTheme {
                V2ShiftActualSurfaceHost(
                    state,
                    V2ShiftActualActions(
                        updateClassEditor = { transform ->
                            state = state.copy(classEditor = state.classEditor?.let(transform))
                        },
                    ),
                )
            }
        }

        compose.onNodeWithTag("v2-extra-class-save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("v2-extra-class-name").performScrollTo().performTextReplacement("Servicio extra")
        compose.onNodeWithTag("v2-extra-class-save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("v2-extra-class-helps-sí").assertDoesNotExist()
        compose.onNodeWithTag("advanced-options-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("v2-extra-class-helps-sí").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("v2-extra-class-dedicated-no").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("v2-extra-class-save").performScrollTo().assertIsEnabled()
    }

    @Test
    fun editorPrimaryActionRemainsReachableInBothThemesAndEveryAndroidFontScale() {
        val expected = expectation()
        var dark by mutableStateOf(false)
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
                V2ShiftActualSurfaceHost(
                    V2ShiftActualUiState(
                        surface = V2ShiftActualSurface.EDITOR,
                        editor = V2ShiftActualEditorState(
                            expectation = expected,
                            ordinal = 1,
                            count = 1,
                            ownerDate = DATE,
                            stage = V2ShiftActualStage.ACTUAL_TIME,
                            draft = V2ActualEditorDraft(
                                startDate = DATE.toString(),
                                startTime = "08:00",
                                endDate = DATE.toString(),
                                endTime = "16:00",
                            ),
                        ),
                    ),
                    V2ShiftActualActions(),
                )
            }
        }

        listOf(false, true).forEach { darkTheme ->
            TestFontScale.entries.forEach { selectedScale ->
                compose.runOnIdle {
                    dark = darkTheme
                    fontScale = selectedScale
                }
                compose.onNodeWithTag("v2-actual-next-save")
                    .performScrollTo()
                    .assertIsDisplayed()
                    .assertIsEnabled()
            }
        }
    }

    @Test
    fun changingLocalTimeClearsHistoricalOffsetBeforeResolution() {
        val expected = expectation()
        var state by mutableStateOf(
            V2ShiftActualUiState(
                surface = V2ShiftActualSurface.EDITOR,
                editor = V2ShiftActualEditorState(
                    expectation = expected,
                    ordinal = 1,
                    count = 1,
                    ownerDate = DATE,
                    stage = V2ShiftActualStage.ACTUAL_TIME,
                    draft = V2ActualEditorDraft(
                        startDate = DATE.toString(),
                        startTime = "08:00",
                        startOffset = "-03:00",
                        endDate = DATE.toString(),
                        endTime = "16:00",
                        endOffset = "-03:00",
                    ),
                ),
            ),
        )
        compose.setContent {
            AndroidFontScaleTheme {
                V2ShiftActualSurfaceHost(
                    state,
                    V2ShiftActualActions(
                        updateDraft = { transform ->
                            state = state.copy(editor = state.editor?.let { it.copy(draft = transform(it.draft)) })
                        },
                    ),
                )
            }
        }

        compose.onNodeWithTag("v2-actual-start-time").performTextReplacement("08:05")
        compose.runOnIdle { assertEquals(null, state.editor?.draft?.startOffset) }
        compose.onNodeWithTag("v2-actual-end-date").performTextReplacement("2026-08-26")
        compose.runOnIdle { assertEquals(null, state.editor?.draft?.endOffset) }
    }

    @Test
    fun repeatedClockTimeOffersTwoExplicitChoicesOnlyWhenNeeded() {
        val repeatedDate = LocalDate.of(2026, 11, 1)
        val repeatedTime = LocalTime.of(1, 30)
        val repeatedZone = ZoneId.of("America/New_York")
        val secondOffset = repeatedZone.rules
            .getValidOffsets(LocalDateTime.of(repeatedDate, repeatedTime))[1]
            .id
        val base = expectation()
        val repeatedExpectation = base.copy(
            planned = base.planned.copy(
                shift = base.planned.shift.copy(zoneId = repeatedZone),
            ),
        )
        var state by mutableStateOf(
            V2ShiftActualUiState(
                surface = V2ShiftActualSurface.EDITOR,
                editor = V2ShiftActualEditorState(
                    expectation = repeatedExpectation,
                    ordinal = 1,
                    count = 1,
                    ownerDate = repeatedDate,
                    stage = V2ShiftActualStage.ACTUAL_TIME,
                    draft = V2ActualEditorDraft(
                        startDate = repeatedDate.toString(),
                        startTime = repeatedTime.toString(),
                        endDate = repeatedDate.toString(),
                        endTime = "03:00",
                    ),
                ),
            ),
        )
        compose.setContent {
            AndroidFontScaleTheme {
                V2ShiftActualSurfaceHost(
                    state,
                    V2ShiftActualActions(
                        updateDraft = { transform ->
                            state = state.copy(editor = state.editor?.let { it.copy(draft = transform(it.draft)) })
                        },
                    ),
                )
            }
        }

        compose.onNodeWithTag("v2-actual-start-offset-1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("v2-actual-start-offset-2").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(secondOffset, state.editor?.draft?.startOffset) }

        compose.runOnIdle {
            state = state.copy(
                editor = state.editor?.copy(
                    expectation = base,
                    ownerDate = DATE,
                    draft = V2ActualEditorDraft(
                        startDate = DATE.toString(),
                        startTime = "08:00",
                        endDate = DATE.toString(),
                        endTime = "16:00",
                    ),
                ),
            )
        }
        compose.onNodeWithTag("v2-actual-start-offset-1").assertDoesNotExist()
        compose.onNodeWithTag("v2-actual-start-offset-2").assertDoesNotExist()
    }

    @Test
    fun sourceConflictOffersRefreshAndDirtyDraftOffersConsciousDiscard() {
        val expected = expectation()
        var refreshes = 0
        var dismissals = 0
        var discards = 0
        var state by mutableStateOf(
            V2ShiftActualUiState(
                surface = V2ShiftActualSurface.EDITOR,
                editor = V2ShiftActualEditorState(
                    expectation = expected,
                    ordinal = 1,
                    count = 1,
                    ownerDate = DATE,
                    stage = V2ShiftActualStage.ACTUAL_TIME,
                    draft = V2ActualEditorDraft(reason = "Borrador ficticio"),
                    sourceConflict = true,
                    errorMessage = "La jornada cambió.",
                ),
            ),
        )
        compose.setContent {
            AndroidFontScaleTheme {
                V2ShiftActualSurfaceHost(
                    state,
                    V2ShiftActualActions(
                        refreshEditorSource = { refreshes++ },
                        dismissDiscardConfirmation = { dismissals++ },
                        confirmDiscard = { discards++ },
                    ),
                )
            }
        }

        compose.onNodeWithTag("v2-actual-refresh-source").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, refreshes)
            state = state.copy(editor = state.editor?.copy(showDiscardConfirmation = true))
        }
        compose.onNodeWithText("¿Descartar este borrador?").assertIsDisplayed()
        compose.onNodeWithText("Seguir editando").performClick()
        compose.runOnIdle { assertEquals(1, dismissals) }
        compose.onNodeWithTag("v2-actual-confirm-discard").performClick()
        compose.runOnIdle { assertEquals(1, discards) }
    }

    @Test
    fun unavailableRestoredDraftOffersOnlyRetryOrExplicitDiscard() {
        var retries = 0
        var discards = 0
        compose.setContent {
            AndroidFontScaleTheme {
                V2ShiftActualSurfaceHost(
                    V2ShiftActualUiState(
                        restoredDraftError = "No pudimos recuperar la jornada ficticia del borrador.",
                    ),
                    V2ShiftActualActions(
                        retryInspection = { retries++ },
                        discardUnavailableRestoredDraft = { discards++ },
                    ),
                )
            }
        }

        compose.onNodeWithText("No pudimos recuperar el borrador").assertIsDisplayed()
        compose.onNodeWithTag("v2-actual-restored-retry").performClick()
        compose.onNodeWithTag("v2-actual-restored-discard").performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(1, discards)
        }
    }

    @Test
    fun classLoadErrorAndRetryAreVisibleAndSelectionFreezesVersion() {
        val expected = expectation()
        val extraClass = ExtraWorkClass.create(
            CLASS_ID,
            TIMELINE_ID,
            WorkSector.NURSING,
            "Horas extras",
            helpsMeetHoursReference = false,
            showDedicatedSummary = true,
            timestamp = CREATED,
        )
        var retries = 0
        var state by mutableStateOf(
            V2ShiftActualUiState(
                surface = V2ShiftActualSurface.EDITOR,
                editor = V2ShiftActualEditorState(
                    expectation = expected,
                    ordinal = 1,
                    count = 1,
                    ownerDate = DATE,
                    stage = V2ShiftActualStage.CLASSIFICATION,
                    draft = V2ActualEditorDraft(choice = ShiftActualDifferenceChoice.EXTRA_CLASS),
                ),
                classes = listOf(extraClass),
                classesLoadError = "No pudimos leer las clases extra.",
            ),
        )
        compose.setContent {
            AndroidFontScaleTheme {
                V2ShiftActualSurfaceHost(
                    state,
                    V2ShiftActualActions(
                        retryClasses = { retries++ },
                        updateDraft = { transform ->
                            state = state.copy(editor = state.editor?.let { it.copy(draft = transform(it.draft)) })
                        },
                    ),
                )
            }
        }

        compose.onNodeWithTag("v2-actual-classes-retry").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("v2-actual-inline-class").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, retries)
            state = state.copy(classesLoadError = null)
        }
        compose.onNodeWithTag("v2-actual-class-$CLASS_ID").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(CLASS_ID, state.editor?.draft?.selectedClassId)
            assertEquals(extraClass.updatedAt.toString(), state.editor?.draft?.selectedClassUpdatedAt)
            state = state.copy(isLoadingClasses = true)
        }
        compose.onNodeWithTag("v2-actual-classes-loading").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun actualTimePickersGiveTheSameTextAsTypingAndCancelChangesNothing() {
        var state by mutableStateOf(
            editorState(
                V2ActualEditorDraft(startDate = "", startTime = "", endDate = DATE.toString(), endTime = ""),
            ),
        )
        var nexts = 0
        var saves = 0
        compose.setContent {
            AndroidFontScaleTheme {
                V2ShiftActualSurfaceHost(
                    state,
                    V2ShiftActualActions(
                        updateDraft = { transform ->
                            state = state.copy(editor = state.editor?.let { it.copy(draft = transform(it.draft)) })
                        },
                        next = { nexts++ },
                        save = { saves++ },
                    ),
                )
            }
        }
        fun draft() = requireNotNull(state.editor).draft

        // Empty date: cancelling keeps it empty; Aceptar writes the owner date as typed text.
        compose.onNodeWithTag("v2-actual-start-date-picker").performScrollTo().performClick()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).performClick()
        compose.runOnIdle { assertEquals("", draft().startDate) }
        compose.onNodeWithTag("v2-actual-start-date-picker").performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        compose.runOnIdle { assertEquals(DATE.toString(), draft().startDate) }

        // Lower limit typed, then confirmed in the clock: same text.
        compose.onNodeWithTag("v2-actual-start-time").performScrollTo().performTextInput("0000")
        compose.runOnIdle { assertEquals("00:00", draft().startTime) }
        compose.onNodeWithTag("v2-actual-start-time-picker").performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        compose.runOnIdle { assertEquals("00:00", draft().startTime) }

        // Upper limit typed; cancelling the clock keeps it, confirming writes the same text.
        compose.onNodeWithTag("v2-actual-end-time").performScrollTo().performTextInput("2359")
        compose.runOnIdle { assertEquals("23:59", draft().endTime) }
        compose.onNodeWithTag("v2-actual-end-time-picker").performClick()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).performClick()
        compose.runOnIdle { assertEquals("23:59", draft().endTime) }
        compose.onNodeWithTag("v2-actual-end-time-picker").performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        compose.runOnIdle { assertEquals("23:59", draft().endTime) }

        // Crossing midnight: the calendar opens on the typed next day and writes the same text.
        val nextDay = DATE.plusDays(1).toString()
        compose.onNodeWithTag("v2-actual-end-date").performScrollTo().performTextReplacement(nextDay)
        compose.onNodeWithTag("v2-actual-end-date-picker").performClick()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).performClick()
        compose.runOnIdle { assertEquals(nextDay, draft().endDate) }
        compose.onNodeWithTag("v2-actual-end-date-picker").performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        compose.runOnIdle {
            assertEquals(DATE.toString(), draft().startDate)
            assertEquals("00:00", draft().startTime)
            assertEquals(nextDay, draft().endDate)
            assertEquals("23:59", draft().endTime)
            // Pickers never continue, review or save on their own.
            assertEquals(0, nexts)
            assertEquals(0, saves)
            assertEquals(V2ShiftActualStage.ACTUAL_TIME, state.editor?.stage)
            assertEquals(V2ShiftActualSurface.EDITOR, state.surface)
        }
    }

    @Test
    fun fragmentPickersWriteOnlyTheirOwnDraftAndCrossMidnight() {
        val fragment = V2ActualFragmentInput(
            id = FRAGMENT_ONE,
            startDate = DATE.toString(),
            startTime = "",
            endDate = "",
            endTime = "",
        )
        var state by mutableStateOf(
            editorState(
                V2ActualEditorDraft(
                    startDate = DATE.toString(),
                    startTime = "08:00",
                    endDate = DATE.plusDays(1).toString(),
                    endTime = "00:30",
                    choice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    fragments = listOf(fragment),
                ),
                stage = V2ShiftActualStage.CLASSIFICATION,
            ),
        )
        var nexts = 0
        var saves = 0
        compose.setContent {
            AndroidFontScaleTheme {
                V2ShiftActualSurfaceHost(
                    state,
                    V2ShiftActualActions(
                        updateDraft = { transform ->
                            state = state.copy(editor = state.editor?.let { it.copy(draft = transform(it.draft)) })
                        },
                        updateFragment = { id, transform ->
                            state = state.copy(
                                editor = state.editor?.let { editor ->
                                    editor.copy(
                                        draft = editor.draft.copy(
                                            fragments = editor.draft.fragments.map { if (it.id == id) transform(it) else it },
                                        ),
                                    )
                                },
                            )
                        },
                        next = { nexts++ },
                        save = { saves++ },
                    ),
                )
            }
        }
        val tag = "v2-actual-fragment-$FRAGMENT_ONE"
        fun fragmentDraft() = requireNotNull(state.editor).draft.fragments.single()

        compose.onNodeWithTag("$tag-start-time").performScrollTo().performTextInput("2359")
        compose.onNodeWithTag("$tag-start-time-picker").performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        compose.runOnIdle { assertEquals("23:59", fragmentDraft().startTime) }

        // The empty end date opens on the fragment's start date; cancel writes nothing.
        compose.onNodeWithTag("$tag-end-date-picker").performScrollTo().performClick()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).performClick()
        compose.runOnIdle { assertEquals("", fragmentDraft().endDate) }
        compose.onNodeWithTag("$tag-end-date-picker").performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        compose.runOnIdle { assertEquals(DATE.toString(), fragmentDraft().endDate) }

        // Next day typed and kept by the calendar; 00:00 typed and kept by the clock.
        val nextDay = DATE.plusDays(1).toString()
        compose.onNodeWithTag("$tag-end-date").performScrollTo().performTextReplacement(nextDay)
        compose.onNodeWithTag("$tag-end-date-picker").performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        compose.onNodeWithTag("$tag-end-time").performScrollTo().performTextInput("0000")
        compose.onNodeWithTag("$tag-end-time-picker").performClick()
        compose.onNodeWithTag(PICKER_CANCEL_TAG).performClick()
        compose.runOnIdle { assertEquals("00:00", fragmentDraft().endTime) }
        compose.onNodeWithTag("$tag-end-time-picker").performClick()
        compose.onNodeWithTag(PICKER_CONFIRM_TAG).performClick()
        compose.runOnIdle {
            assertEquals(fragment.copy(startTime = "23:59", endDate = nextDay, endTime = "00:00"), fragmentDraft())
            // The actual interval of the journey is untouched by the fragment pickers.
            assertEquals("08:00", state.editor?.draft?.startTime)
            assertEquals("00:30", state.editor?.draft?.endTime)
            assertEquals(0, nexts)
            assertEquals(0, saves)
            assertEquals(V2ShiftActualStage.CLASSIFICATION, state.editor?.stage)
        }
    }

    @Test
    fun largeFontKeepsEveryActualTimePickerReachableAndOpen() {
        val initial = V2ActualEditorDraft(
            startDate = DATE.toString(),
            startTime = "22:00",
            endDate = DATE.plusDays(1).toString(),
            endTime = "06:00",
        )
        var state by mutableStateOf(editorState(initial))
        var nexts = 0
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = false, fontScale = TestFontScale.EXTRA_LARGE) {
                Box(Modifier.requiredSize(width = 360.dp, height = 800.dp)) {
                    V2ShiftActualSurfaceHost(
                        state,
                        V2ShiftActualActions(
                            updateDraft = { transform ->
                                state = state.copy(editor = state.editor?.let { it.copy(draft = transform(it.draft)) })
                            },
                            next = { nexts++ },
                        ),
                    )
                }
            }
        }

        listOf(
            "v2-actual-start-date-picker",
            "v2-actual-start-time-picker",
            "v2-actual-end-date-picker",
            "v2-actual-end-time-picker",
        ).forEach { picker ->
            compose.onNodeWithTag(picker).performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag(PICKER_CANCEL_TAG).assertIsDisplayed().performClick()
        }
        compose.runOnIdle {
            assertEquals(initial, state.editor?.draft)
            assertEquals(0, nexts)
        }
    }

    @Test
    fun internalIdsAreHiddenAndReturnToPlannedStaysAConsciousConfirmation() {
        // Decided by Joaquín on 9/11/2026: no "UUID: …" lines for the journey or its fragments,
        // and "Volver al planificado" (it removes the actual times) is a red, confirmed action.
        val fragment = V2ActualFragmentInput(
            id = FRAGMENT_ONE,
            startDate = DATE.toString(),
            startTime = "08:00",
            endDate = DATE.toString(),
            endTime = "09:00",
        )
        var state by mutableStateOf(
            editorState(
                V2ActualEditorDraft(
                    startDate = DATE.toString(),
                    startTime = "08:00",
                    endDate = DATE.toString(),
                    endTime = "17:00",
                    choice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    fragments = listOf(fragment),
                ),
                stage = V2ShiftActualStage.CLASSIFICATION,
            ),
        )
        var confirmed = 0
        compose.setContent {
            AndroidFontScaleTheme {
                V2ShiftActualSurfaceHost(state, V2ShiftActualActions(confirmReturnToPlanned = { confirmed++ }))
            }
        }
        compose.onNodeWithText("Fragmento 1").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("UUID", substring = true).assertCountEquals(0)
        compose.runOnIdle {
            state = state.copy(editor = state.editor?.copy(showReturnConfirmation = true))
        }
        compose.onNodeWithTag("v2-actual-confirm-return-planned").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, confirmed) }
    }

    private fun editorState(
        draft: V2ActualEditorDraft,
        stage: V2ShiftActualStage = V2ShiftActualStage.ACTUAL_TIME,
    ) = V2ShiftActualUiState(
        surface = V2ShiftActualSurface.EDITOR,
        editor = V2ShiftActualEditorState(
            expectation = expectation(),
            ordinal = 1,
            count = 1,
            ownerDate = DATE,
            stage = stage,
            draft = draft,
        ),
    )

    private fun expectation(): ShiftActualExpectation = ShiftActualExpectation(
        planned = write(),
        previousActual = null,
        observedClass = null,
        recurringOccurrence = null,
        protectionFingerprint = "fixture",
    )

    private fun expectationWithTwoExtras(): ShiftActualExpectation {
        val base = expectation()
        val extraClass = ExtraWorkClass.create(
            CLASS_ID,
            TIMELINE_ID,
            WorkSector.NURSING,
            "Horas extras",
            helpsMeetHoursReference = false,
            showDedicatedSummary = true,
            timestamp = CREATED,
        )
        val shift = base.planned.shift
        val mutation = requireNotNull(
            buildShiftActualSaveMutation(
                expectation = base,
                draft = ShiftActualDraft(
                    actualStart = shift.startAt.minus(Duration.ofMinutes(30)),
                    actualEnd = shift.endAt.plus(Duration.ofMinutes(30)),
                    differenceReason = "Entrada y salida extendidas",
                    explanation = null,
                    differenceChoice = ShiftActualDifferenceChoice.EXTRA_CLASS,
                    classSelection = ShiftActualClassSelection.Existing(extraClass),
                    fragments = listOf(
                        ShiftActualFragmentDraft(FRAGMENT_ONE, shift.startAt.minus(Duration.ofMinutes(30)), shift.startAt),
                        ShiftActualFragmentDraft(FRAGMENT_TWO, shift.endAt, shift.endAt.plus(Duration.ofMinutes(30))),
                    ),
                ),
                clock = Clock.fixed(shift.endAt.plus(Duration.ofHours(2)), ZoneOffset.UTC),
                timestamp = shift.endAt.plusSeconds(60),
            ),
        )
        return base.copy(previousActual = mutation.replacement, observedClass = extraClass)
    }

    private fun write(): V2ShiftWrite {
        val start = DATE.atTime(8, 0).atZone(ZONE).toInstant()
        val end = DATE.atTime(16, 0).atZone(ZONE).toInstant()
        return V2ShiftWrite(
            Shift(
                SHIFT_ID, start, end, ZONE, DATE, "Hospital ficticio", "HFI", null,
                LocalTime.of(8, 0), LocalTime.of(16, 0), 0xFF336699.toInt(), null,
                ShiftStatus.PLANNED, OBJECTIVE_ID, CREATED, CREATED,
            ),
            ShiftWorkSnapshot(
                SHIFT_ID, TIMELINE_ID, WorkSector.NURSING, REVISION_ID, PLACE_ID,
                OBJECTIVE_ID, TEMPLATE_ID, TYPE_ID, "Turno asistencial", WorkTypeBehavior.ACTIVE_WORK,
            ),
        )
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 25)
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val CREATED: Instant = Instant.parse("2026-01-01T00:00:00Z")
        val SHIFT_ID: UUID = UUID(0L, 101)
        val TIMELINE_ID: UUID = UUID(0L, 102)
        val REVISION_ID: UUID = UUID(0L, 103)
        val PLACE_ID: UUID = UUID(0L, 104)
        val OBJECTIVE_ID: UUID = UUID(0L, 105)
        val TEMPLATE_ID: UUID = UUID(0L, 106)
        val TYPE_ID: UUID = UUID(0L, 107)
        val CLASS_ID: UUID = UUID(0L, 108)
        val FRAGMENT_ONE: UUID = UUID(0L, 109)
        val FRAGMENT_TWO: UUID = UUID(0L, 110)
    }
}
