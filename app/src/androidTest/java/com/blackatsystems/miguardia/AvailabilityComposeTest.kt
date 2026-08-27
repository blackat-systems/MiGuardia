package com.blackatsystems.miguardia

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.model.AvailabilityBreakdown
import com.blackatsystems.miguardia.core.domain.model.AvailabilityTemporalState
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.sumAvailabilityBreakdowns
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.ui.availability.AvailabilityActions
import com.blackatsystems.miguardia.ui.availability.AvailabilityConfigurationDraft
import com.blackatsystems.miguardia.ui.availability.AvailabilityDaySection
import com.blackatsystems.miguardia.ui.availability.AvailabilityHoursSection
import com.blackatsystems.miguardia.ui.availability.AvailabilityLoadState
import com.blackatsystems.miguardia.ui.availability.AvailabilitySource
import com.blackatsystems.miguardia.ui.availability.AvailabilitySurface
import com.blackatsystems.miguardia.ui.availability.AvailabilitySurfaceHost
import com.blackatsystems.miguardia.ui.availability.AvailabilityUiState
import com.blackatsystems.miguardia.ui.availability.AvailabilityWindowDraftState
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.YearMonth
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AvailabilityComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun configurationShowsThreeExactNamesNoUseDateAndReviewBeforeSave() {
        var selected: AvailabilityConfigurationDraft? = null
        val draft = AvailabilityConfigurationDraft(null, OWNER.minusDays(1).toString())
        var state by mutableStateOf(
            contentState().copy(
                surface = AvailabilitySurface.CONFIG_EDITOR,
                configurationDraft = draft,
            ),
        )
        compose.setContent {
            MiGuardiaTheme {
                AvailabilitySurfaceHost(
                    state,
                    AvailabilityActions(updateConfiguration = { selected = it }),
                )
            }
        }

        listOf("Guardia pasiva", "Disponible para llamado", "Retén", "No uso disponibilidad")
            .forEach { compose.onNodeWithText(it, substring = true).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithTag("availability-config-date").assertIsDisplayed()
        compose.onNodeWithTag("availability-config-review").assertIsDisplayed()
        compose.onNodeWithTag("availability-label-ON_CALL_RETAINER").performClick()
        compose.runOnIdle { assertEquals(AvailabilityLabel.ON_CALL_RETAINER, selected?.label) }
        compose.runOnIdle {
            state = state.copy(surface = AvailabilitySurface.CONFIG_REVIEW)
        }
        compose.onNodeWithText("Confirmá el cambio").assertIsDisplayed()
        compose.onNodeWithText("Vigente desde: ${draft.effectiveDate}").assertIsDisplayed()
        compose.onNodeWithText("ese tramo histórico", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("availability-config-save").assertIsDisplayed()
    }

    @Test
    fun dayUsesCalendarDateHidesCreateWhenDisabledAndShowsTextualAccessibleIndicatorWhenEnabled() {
        var created: LocalDate? = null
        var state by mutableStateOf(contentState())
        compose.setContent {
            MiGuardiaTheme(darkTheme = true, appZoom = AppZoom.EXTRA_LARGE) {
                AvailabilityDaySection(
                    date = OWNER,
                    state = state,
                    actions = AvailabilityActions(openCreate = { created = it }),
                )
            }
        }

        compose.onAllNodesWithText("Guardia pasiva")[0].assertIsDisplayed()
        compose.onNodeWithText("Efectiva 4 h 0 min · reemplazada 0 h 0 min").assertIsDisplayed()
        compose.onNodeWithTag("availability-add-$OWNER").performClick()
        compose.runOnIdle { assertEquals(OWNER, created) }

        compose.runOnIdle { state = contentState(disabled = true) }
        compose.onNodeWithTag("availability-add-$OWNER").assertDoesNotExist()
        compose.onNodeWithText("No estaba habilitada para esta fecha.", substring = true).assertIsDisplayed()
    }

    @Test
    fun hoursShowsAvailabilitySeparatelyWithoutCallingItWorkedTime() {
        compose.setContent {
            MiGuardiaTheme(appZoom = AppZoom.LARGE) {
                Column { AvailabilityHoursSection(contentState()) }
            }
        }
        compose.onNodeWithText("Disponibilidad").assertIsDisplayed()
        compose.onNodeWithText("Programada: 4 h 0 min").assertIsDisplayed()
        compose.onNodeWithText("Este desglose no altera el avance de horas trabajadas.").assertIsDisplayed()
    }

    @Test
    fun monthlyGridAnnouncesAvailabilityWithTextIndependentFromColor() {
        val calendar = CalendarUiState(
            visibleMonth = YearMonth.of(2026, 8),
            referenceInstant = Instant.parse("2026-08-27T13:00:00Z"),
            days = projectCalendarMonth(
                month = YearMonth.of(2026, 8),
                shifts = emptyList(),
                explicitDayStatuses = emptyList(),
                medicalLeaves = emptyList(),
                now = Instant.parse("2026-08-27T13:00:00Z"),
            ),
            hasAnyShifts = false,
            hasAnyShiftsLoaded = true,
            loadState = CalendarLoadState.CONTENT,
        )
        compose.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = calendar,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    availabilityState = contentState(),
                )
            }
        }
        compose.onNodeWithContentDescription("una ventana de disponibilidad", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun loadingErrorAndRetryRemainDistinctFromEmptyContent() {
        var retries = 0
        var state by mutableStateOf(
            AvailabilityUiState(surface = AvailabilitySurface.OVERVIEW),
        )
        compose.setContent {
            MiGuardiaTheme {
                AvailabilitySurfaceHost(state, AvailabilityActions(retry = { retries++ }))
            }
        }
        compose.onNodeWithText("Guardias pasivas y disponibilidad").assertIsDisplayed()
        compose.runOnIdle {
            state = state.copy(loadState = AvailabilityLoadState.ERROR, message = "Fallo ficticio")
        }
        compose.onNodeWithText("Fallo ficticio").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun editorRemainsReachableInLandscapeSizedViewportAtTwoHundredPercentZoom() {
        val draft = AvailabilityWindowDraftState(
            ownerDate = OWNER,
            startTime = "08:00",
            endDate = OWNER.plusDays(1).toString(),
            endTime = "12:00",
        )
        compose.setContent {
            MiGuardiaTheme(darkTheme = true, appZoom = AppZoom.EXTRA_LARGE) {
                Box(Modifier.requiredSize(width = 800.dp, height = 360.dp)) {
                    AvailabilitySurfaceHost(
                        contentState().copy(
                            surface = AvailabilitySurface.WINDOW_EDITOR,
                            windowDraft = draft,
                        ),
                        AvailabilityActions(),
                    )
                }
            }
        }

        listOf("availability-start", "availability-end-date", "availability-end", "availability-window-review")
            .forEach { tag -> compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed() }
    }

    @Test
    fun discardAndObservationErrorKeepTheUserInControlWithoutHidingSavedWindows() {
        var discarded = 0
        var state by mutableStateOf(
            contentState().copy(
                surface = AvailabilitySurface.WINDOW_EDITOR,
                windowDraft = AvailabilityWindowDraftState(
                    ownerDate = OWNER,
                    startTime = "08:00",
                    endDate = OWNER.toString(),
                    endTime = "12:00",
                ),
                showDiscardConfirmation = true,
            ),
        )
        compose.setContent {
            MiGuardiaTheme {
                AvailabilitySurfaceHost(
                    state,
                    AvailabilityActions(confirmDiscard = { discarded++ }),
                )
            }
        }
        compose.onNodeWithTag("availability-discard-confirm").performClick()
        compose.runOnIdle { assertEquals(1, discarded) }

        compose.runOnIdle {
            state = contentState().copy(
                surface = AvailabilitySurface.OVERVIEW,
                loadState = AvailabilityLoadState.ERROR,
                message = "Fallo recuperable",
            )
        }
        compose.onNodeWithText("Fallo recuperable").assertIsDisplayed()
        compose.onNodeWithText("Ventanas registradas: 1").assertIsDisplayed()
    }

    private fun contentState(disabled: Boolean = false): AvailabilityUiState = AvailabilityUiState(
        loadState = AvailabilityLoadState.CONTENT,
        source = source(disabled),
    )

    private fun source(disabled: Boolean): AvailabilitySource {
        val value = WorkConfiguration(
            WorkSector.PRIVATE_SECURITY,
            HoursReference.PendingSetup,
            if (disabled) null else AvailabilityLabel.PASSIVE_GUARD,
        )
        val history = WorkConfigurationHistory(
            EffectiveDateTimeline(TIMELINE, listOf(EffectiveRevision(REVISION, LocalDate.of(2026, 1, 1), value))),
            PerPeriodHoursValues(emptyList()),
        )
        val record = record()
        val breakdowns = mapOf(
            record.id to AvailabilityBreakdown(
                AvailabilityTemporalState.COMPLETED,
                240,
                240,
                0,
                0,
                240,
                0,
            ),
        )
        return AvailabilitySource(
            history = history,
            windows = listOf(record),
            breakdowns = breakdowns,
            totals = sumAvailabilityBreakdowns(breakdowns.values),
            protectedWindowIds = emptySet(),
            activeWork = emptyList(),
            protectedRanges = emptyList(),
            today = OWNER,
        )
    }

    private fun record() = AvailabilityWindowRecord(
        id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
        timelineId = TIMELINE,
        sector = WorkSector.PRIVATE_SECURITY,
        configurationRevisionId = REVISION,
        ownerLocalDate = OWNER,
        zoneId = ZoneOffset.UTC,
        start = Instant.parse("2026-08-27T08:00:00Z"),
        end = Instant.parse("2026-08-27T12:00:00Z"),
        labelSnapshot = "Guardia pasiva",
        createdAt = Instant.parse("2026-08-27T20:00:00Z"),
        updatedAt = Instant.parse("2026-08-27T20:00:00Z"),
    )

    private companion object {
        val OWNER: LocalDate = LocalDate.of(2026, 8, 27)
        val TIMELINE: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val REVISION: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    }
}
