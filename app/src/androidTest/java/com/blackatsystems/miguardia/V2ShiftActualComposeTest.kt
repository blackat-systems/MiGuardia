package com.blackatsystems.miguardia

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
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
import com.blackatsystems.miguardia.ui.management.V2ActualEditorDraft
import com.blackatsystems.miguardia.ui.management.V2ExtraClassEditorState
import com.blackatsystems.miguardia.ui.management.V2ShiftActualActions
import com.blackatsystems.miguardia.ui.management.V2ShiftActualDetailCard
import com.blackatsystems.miguardia.ui.management.V2ShiftActualEditorState
import com.blackatsystems.miguardia.ui.management.V2ShiftActualRowState
import com.blackatsystems.miguardia.ui.management.V2ShiftActualStage
import com.blackatsystems.miguardia.ui.management.V2ShiftActualSurface
import com.blackatsystems.miguardia.ui.management.V2ShiftActualSurfaceHost
import com.blackatsystems.miguardia.ui.management.V2ShiftActualUiState
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
            MiGuardiaTheme {
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
    fun summaryShowsPlannedRealRegularTwoExtrasAndTotalWithDates() {
        val expected = expectationWithTwoExtras()
        compose.setContent {
            MiGuardiaTheme {
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

        compose.onNodeWithText("Planificado: 2026-08-25 08:00", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Real: 2026-08-25 07:30", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Trabajo habitual: 8 h 0 min").assertIsDisplayed()
        compose.onNodeWithText("Extra 1: Horas extras", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Extra 2: Horas extras", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Total real: 9 h 0 min").assertIsDisplayed()
        compose.onNodeWithTag("v2-actual-$SHIFT_ID-correct").assertIsDisplayed()
        compose.onNodeWithTag("v2-actual-$SHIFT_ID-return-planned").assertIsDisplayed()
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
            MiGuardiaTheme {
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
        compose.onNodeWithTag("v2-extra-class-helps-sí").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("v2-extra-class-dedicated-no").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("v2-extra-class-save").performScrollTo().assertIsEnabled()
    }

    @Test
    fun editorPrimaryActionRemainsReachableInBothThemesAndEveryInternalZoom() {
        val expected = expectation()
        var dark by mutableStateOf(false)
        var zoom by mutableStateOf(AppZoom.STANDARD)
        compose.setContent {
            MiGuardiaTheme(darkTheme = dark, appZoom = zoom) {
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
            AppZoom.entries.forEach { appZoom ->
                compose.runOnIdle {
                    dark = darkTheme
                    zoom = appZoom
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
            MiGuardiaTheme {
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
            MiGuardiaTheme {
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
            MiGuardiaTheme {
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
            MiGuardiaTheme {
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
