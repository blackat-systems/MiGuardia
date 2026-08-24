package com.blackatsystems.miguardia

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.blackatsystems.miguardia.core.domain.calendar.CalendarDay
import com.blackatsystems.miguardia.core.domain.calendar.CalendarShift
import com.blackatsystems.miguardia.core.domain.calendar.ShiftTemporalStatus
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.management.V2DayEditEntry
import com.blackatsystems.miguardia.ui.management.V2ShiftDayInspectionState
import com.blackatsystems.miguardia.ui.management.V2ShiftEditActions
import com.blackatsystems.miguardia.ui.management.V2ShiftEditDayRow
import com.blackatsystems.miguardia.ui.management.V2ShiftEditStage
import com.blackatsystems.miguardia.ui.management.V2ShiftEditSurfaceHost
import com.blackatsystems.miguardia.ui.management.V2ShiftEditTemplateOption
import com.blackatsystems.miguardia.ui.management.V2ShiftEditUiState
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupUiState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class V2ShiftEditComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dayIdentificationShowsNeutralWaitErrorRetryAndConsciousEntry() {
        var state by mutableStateOf(
            V2ShiftEditUiState(
                timelineId = TIMELINE_ID,
                date = DATE,
                inspectionState = V2ShiftDayInspectionState.LOADING,
            ),
        )
        var retries = 0
        var starts = 0
        compose.setContent {
            MiGuardiaTheme {
                V2DayEditEntry(
                    state = state,
                    date = DATE,
                    onBegin = { starts++ },
                    onRetry = { retries++ },
                )
            }
        }

        compose.onNodeWithTag("v2-shift-identification-loading").assertIsDisplayed()
        compose.runOnIdle {
            state = state.copy(
                inspectionState = V2ShiftDayInspectionState.ERROR,
                errorMessage = "Fallo ficticio de identificación",
            )
        }
        compose.onNodeWithText("Fallo ficticio de identificación").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            state = state.copy(
                inspectionState = V2ShiftDayInspectionState.CONTENT,
                dayRows = listOf(row(write(uuid(10)), 1, 1)),
                errorMessage = null,
            )
        }
        compose.onNodeWithTag("v2-edit-day-action").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, starts) }
    }

    @Test
    fun calendarDetailUsesEditarEsteDiaWithoutEnteringBulkEditMode() {
        val original = write(uuid(11))
        var starts = 0
        compose.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = calendarState(original.shift),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    v2ShiftEditState = V2ShiftEditUiState(
                        timelineId = TIMELINE_ID,
                        date = DATE,
                        inspectionState = V2ShiftDayInspectionState.CONTENT,
                        dayRows = listOf(row(original, 1, 1)),
                    ),
                    v2ShiftEditActions = V2ShiftEditActions(beginDayEditing = { starts++ }),
                    workSetupState = readyWorkSetupState(),
                )
            }
        }

        compose.onNodeWithText("Editar este día").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, starts) }
        compose.onNodeWithText("Editando calendario").assertDoesNotExist()
    }

    @Test
    fun needsFirstWorkSetNeverMountsOrOffersTheV2Editor() {
        val original = write(uuid(12))
        val ready = readyWorkSetupState()
        val revision = (ready.rootState as WorkSetupState.V2Ready).configurationRevision
        val needsFirstSet = ready.copy(
            rootState = WorkSetupState.V2NeedsFirstSet(TIMELINE_ID, revision, emptySet()),
        )
        compose.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = calendarState(original.shift),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    v2ShiftEditState = editorState(original),
                    workSetupState = needsFirstSet,
                )
            }
        }

        compose.onNodeWithTag("v2-shift-edit-surface").assertDoesNotExist()
        compose.onNodeWithTag("v2-shift-identification-loading").assertDoesNotExist()
        compose.onNodeWithText("Editar este día").assertDoesNotExist()
        compose.onNodeWithText("Editar día").assertDoesNotExist()
    }

    @Test
    fun visuallyEqualV2RowsStayDistinctByOrdinalUuidAndAvailableActions() {
        val first = write(uuid(20))
        val second = write(uuid(21))
        val state = V2ShiftEditUiState(
            stage = V2ShiftEditStage.DAY_ACTIONS,
            timelineId = TIMELINE_ID,
            date = DATE,
            inspectionState = V2ShiftDayInspectionState.CONTENT,
            dayRows = listOf(
                row(first, 1, 2),
                row(second, 2, 2),
            ),
        )
        compose.setContent { MiGuardiaTheme { V2ShiftEditSurfaceHost(state, V2ShiftEditActions()) } }

        compose.onNodeWithContentDescription("Jornada 1 de 2", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Jornada 2 de 2", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("v2-edit-shift-${first.shift.id}").assertIsDisplayed()
        compose.onNodeWithTag("v2-delete-shift-${second.shift.id}").assertIsDisplayed()
    }

    @Test
    fun formKeepsDateReadOnlyAndEnablesReviewOnlyAfterARealChange() {
        val original = write(uuid(30), position = "Puesto A")
        var state by mutableStateOf(editorState(original))
        compose.setContent {
            MiGuardiaTheme {
                V2ShiftEditSurfaceHost(
                    state = state,
                    actions = V2ShiftEditActions(updatePosition = { state = state.copy(position = it) }),
                )
            }
        }

        compose.onNodeWithTag("v2-shift-fixed-date")
            .assertIsDisplayed()
            .assertHasNoClickAction()
        compose.onNodeWithText("lunes 10 de agosto de 2026").assertIsDisplayed()
        compose.onNodeWithTag("v2-shift-request-review").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("v2-shift-edit-position").performTextReplacement("Puesto B")
        compose.onNodeWithTag("v2-shift-request-review").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("v2-shift-original-summary").assertIsDisplayed()
    }

    @Test
    fun updatedActiveTemplateWithTheSameIdIsSeparateFromTheHistoricalChoice() {
        val original = write(uuid(35), position = "Puesto A")
        val baseOption = option()
        val current = baseOption.copy(
            template = baseOption.template.copy(
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(17, 0),
                updatedAt = NOW.plusSeconds(1),
            ),
            matchesHistoricalSelection = false,
        )
        var historicalSelections = 0
        var activeSelection: UUID? = null
        compose.setContent {
            MiGuardiaTheme {
                V2ShiftEditSurfaceHost(
                    state = editorState(original).copy(templateOptions = listOf(current)),
                    actions = V2ShiftEditActions(
                        chooseHistoricalTemplate = { historicalSelections++ },
                        chooseTemplate = { activeSelection = it },
                    ),
                )
            }
        }

        compose.onNodeWithTag("v2-shift-historical-template-$TEMPLATE_ID")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithTag("v2-shift-template-$TEMPLATE_ID")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        compose.runOnIdle {
            assertEquals(1, historicalSelections)
            assertEquals(TEMPLATE_ID, activeSelection)
        }
    }

    @Test
    fun warningsReviewDeleteAndDiscardExposeExactConsciousActions() {
        val original = write(uuid(40), position = "Puesto A")
        var state by mutableStateOf(
            editorState(original).copy(
                stage = V2ShiftEditStage.CONFIRM_WARNINGS,
                position = "Puesto B",
                warnings = listOf("Hay una segunda jornada.", "El descanso es menor a 12 horas."),
            ),
        )
        var confirmations = 0
        compose.setContent {
            MiGuardiaTheme {
                V2ShiftEditSurfaceHost(
                    state,
                    V2ShiftEditActions(
                        confirmWarnings = { confirmations++ },
                        confirmDelete = { confirmations++ },
                        confirmDiscard = { confirmations++ },
                    ),
                )
            }
        }

        compose.onNodeWithTag("v2-shift-warning-dialog").assertIsDisplayed()
        compose.onNodeWithText("segunda jornada", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("v2-shift-confirm-warnings").performClick()
        compose.runOnIdle {
            assertEquals(1, confirmations)
            state = state.copy(
                stage = V2ShiftEditStage.REVIEW,
                reviewFingerprint = "ficticio",
            )
        }
        compose.onNodeWithTag("v2-shift-final-summary").assertIsDisplayed()
        compose.onNodeWithTag("v2-shift-save").performScrollTo().assertIsEnabled()
        compose.runOnIdle {
            state = state.copy(
                stage = V2ShiftEditStage.CONFIRM_DELETE,
                confirmedPairFingerprint = "par-ficticio",
            )
        }
        compose.onNodeWithTag("v2-shift-delete-dialog").assertIsDisplayed()
        compose.onNodeWithText("Hospital ficticio", substring = true).assertIsDisplayed()
        compose.onNodeWithText("08:00–16:00", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("v2-shift-confirm-delete").performClick()
        compose.runOnIdle {
            assertEquals(2, confirmations)
            state = state.copy(stage = V2ShiftEditStage.CONFIRM_DISCARD)
        }
        compose.onNodeWithTag("v2-shift-discard-dialog").assertIsDisplayed()
        compose.onNodeWithTag("v2-shift-confirm-discard").performClick()
        compose.runOnIdle { assertEquals(3, confirmations) }
    }

    @Test
    fun primaryActionStaysReachableInBothThemesAndAllInternalZooms() {
        val original = write(uuid(50), position = "Puesto A")
        var dark by mutableStateOf(false)
        var zoom by mutableStateOf(AppZoom.STANDARD)
        compose.setContent {
            MiGuardiaTheme(darkTheme = dark, appZoom = zoom) {
                V2ShiftEditSurfaceHost(
                    editorState(original).copy(position = "Cambio alcanzable"),
                    V2ShiftEditActions(),
                )
            }
        }

        listOf(false, true).forEach { darkTheme ->
            AppZoom.entries.forEach { appZoom ->
                compose.runOnIdle {
                    dark = darkTheme
                    zoom = appZoom
                }
                compose.onNodeWithTag("v2-shift-request-review")
                    .performScrollTo()
                    .assertIsDisplayed()
                    .assertIsEnabled()
            }
        }
    }

    private fun editorState(original: V2ShiftWrite): V2ShiftEditUiState = V2ShiftEditUiState(
        stage = V2ShiftEditStage.EDIT_FORM,
        timelineId = TIMELINE_ID,
        date = DATE,
        targetShiftId = original.shift.id,
        originalWrite = original,
        templateOptions = listOf(option()),
        selectedTemplateId = original.snapshot.templateId,
        position = original.shift.position.orEmpty(),
    )

    private fun row(write: V2ShiftWrite, ordinal: Int, total: Int) =
        V2ShiftEditDayRow(write.shift, write.snapshot, ordinal, total)

    private fun write(id: UUID, position: String? = null): V2ShiftWrite {
        val shift = Shift(
            id = id,
            startAt = Instant.parse("2026-08-10T11:00:00Z"),
            endAt = Instant.parse("2026-08-10T19:00:00Z"),
            zoneId = ZONE,
            localStartDate = DATE,
            objectiveNameSnapshot = "Hospital ficticio",
            objectiveAbbreviationSnapshot = "HFI",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.of(8, 0),
            endTimeSnapshot = LocalTime.of(16, 0),
            colorArgbSnapshot = 0xFF336699.toInt(),
            position = position,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = OBJECTIVE_ID,
            createdAt = NOW,
            updatedAt = NOW,
        )
        return V2ShiftWrite(
            shift,
            ShiftWorkSnapshot(
                shift.id, TIMELINE_ID, WorkSector.NURSING, REVISION_ID,
                PLACE_ID, OBJECTIVE_ID, TEMPLATE_ID, TYPE_ID,
                "Turno asistencial", WorkTypeBehavior.ACTIVE_WORK,
            ),
        )
    }

    private fun option(): V2ShiftEditTemplateOption {
        val objective = Objective(OBJECTIVE_ID, "Hospital ficticio", "HFI", null, null, true, NOW, NOW)
        val place = WorkPlace(PLACE_ID, TIMELINE_ID, WorkSector.NURSING, OBJECTIVE_ID, true, NOW, NOW)
        val type = WorkType.create(TYPE_ID, TIMELINE_ID, WorkSector.NURSING, "Turno asistencial", NOW)
        val template = WorkTemplate(
            TEMPLATE_ID, TIMELINE_ID, WorkSector.NURSING, PLACE_ID, OBJECTIVE_ID, TYPE_ID,
            LocalTime.of(8, 0), LocalTime.of(16, 0), 0xFF336699.toInt(), true, NOW, NOW,
        )
        return V2ShiftEditTemplateOption(
            objective = objective,
            workPlace = place,
            workType = type,
            template = template,
            matchesHistoricalSelection = true,
        )
    }

    private fun calendarState(shift: Shift): CalendarUiState = CalendarUiState(
        visibleMonth = YearMonth.from(DATE),
        referenceInstant = NOW.minusSeconds(60),
        days = listOf(
            CalendarDay(
                date = DATE,
                shifts = listOf(CalendarShift(shift, ShiftTemporalStatus.UPCOMING)),
                explicitStatus = null,
                hasMedicalLeave = false,
            ),
        ),
        hasAnyShifts = true,
        hasAnyShiftsLoaded = true,
        loadState = CalendarLoadState.CONTENT,
        detailDate = DATE,
    )

    private fun readyWorkSetupState(): WorkSetupUiState {
        val revision = EffectiveRevision(
            REVISION_ID,
            DATE.minusDays(1),
            WorkConfiguration(WorkSector.NURSING, HoursReference.PendingSetup, null),
        )
        return WorkSetupUiState(
            rootState = WorkSetupState.V2Ready(TIMELINE_ID, revision),
            selectedSector = WorkSector.NURSING,
            catalog = WorkCatalog(TIMELINE_ID, WorkSector.NURSING, emptyList(), emptyList(), emptyList(), emptyList()),
        )
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 10)
        val NOW: Instant = Instant.parse("2026-08-10T10:00:00Z")
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val TIMELINE_ID: UUID = uuid(1)
        val REVISION_ID: UUID = uuid(2)
        val OBJECTIVE_ID: UUID = uuid(3)
        val PLACE_ID: UUID = uuid(4)
        val TYPE_ID: UUID = uuid(5)
        val TEMPLATE_ID: UUID = uuid(6)

        fun uuid(value: Long): UUID = UUID(0L, value)
    }
}
