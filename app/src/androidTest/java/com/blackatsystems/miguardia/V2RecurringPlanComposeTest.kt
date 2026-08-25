package com.blackatsystems.miguardia

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.model.MonthlyOrdinal
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.RecurringPattern
import com.blackatsystems.miguardia.core.domain.model.RecurringPlan
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanAggregate
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanMutation
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevision
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevisionKind
import com.blackatsystems.miguardia.core.domain.shift.RecurringConflictPolicy
import com.blackatsystems.miguardia.core.domain.shift.RecurringDateAction
import com.blackatsystems.miguardia.core.domain.shift.RecurringDateResult
import com.blackatsystems.miguardia.core.domain.shift.RecurringMutationPreview
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.ui.management.V2RecurringActions
import com.blackatsystems.miguardia.ui.management.V2RecurringMode
import com.blackatsystems.miguardia.ui.management.V2RecurringPatternKind
import com.blackatsystems.miguardia.ui.management.V2RecurringPlanSurfaceHost
import com.blackatsystems.miguardia.ui.management.V2RecurringStage
import com.blackatsystems.miguardia.ui.management.V2RecurringTemplateOption
import com.blackatsystems.miguardia.ui.management.V2RecurringUiState
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class V2RecurringPlanComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun formExposesOnlyTheFourFrozenPatternsAndEveryExactConflictChoice() {
        val selectedPatterns = mutableListOf<V2RecurringPatternKind>()
        val selectedPolicies = mutableListOf<RecurringConflictPolicy>()
        setSurface(
            state = formState(),
            actions = V2RecurringActions(
                selectPattern = selectedPatterns::add,
                selectConflictPolicy = selectedPolicies::add,
            ),
        )

        V2RecurringPatternKind.entries.forEach { pattern ->
            compose.onNodeWithTag("v2-recurring-pattern-${pattern.name}")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
        }
        RecurringConflictPolicy.entries.forEach { policy ->
            compose.onNodeWithTag("v2-recurring-policy-${policy.name}")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
        }
        compose.onNodeWithTag("v2-recurring-start-date").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("v2-recurring-review").performScrollTo().assertIsEnabled()
        compose.runOnIdle {
            assertEquals(V2RecurringPatternKind.entries, selectedPatterns)
            assertEquals(RecurringConflictPolicy.entries, selectedPolicies)
        }
    }

    @Test
    fun weekdaysExposeCheckboxSemanticsBecauseTheyAllowMultipleSelections() {
        setSurface(formState())

        compose.onNodeWithTag("v2-recurring-weekday-MONDAY")
            .performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        compose.onNodeWithTag("v2-recurring-weekday-WEDNESDAY")
            .performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
    }

    @Test
    fun changeKeepsCutDateImmutableAndNamesTemplateTimeColorPositionAndWarningsInText() {
        val state = formState().copy(
            mode = V2RecurringMode.CHANGE,
            cutDate = START,
            startDateText = START.toString(),
            position = "Puesto ficticio",
            conflictPolicy = RecurringConflictPolicy.KEEP_BOTH,
        )
        setSurface(state, darkTheme = false, zoom = AppZoom.LARGE)

        compose.onNodeWithTag("v2-recurring-cut-date").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("v2-recurring-start-date").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Lugar ficticio").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Trabajo habitual · 21:00–06:00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Color #FF336699").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "Mantener ambas puede dejar dos jornadas el mismo día. La vista previa mostrará cada advertencia.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun previewKeepsEveryExactDateScrollableAtTwoHundredPercentAndConfirmsOnlyOnce() {
        var saves = 0
        val dates = (0L..9L).map(START::plusDays)
        setSurface(
            state = formState().copy(
                stage = V2RecurringStage.PREVIEW,
                preview = preview(dates),
            ),
            actions = V2RecurringActions(save = { saves++ }),
            zoom = AppZoom.EXTRA_LARGE,
        )

        compose.onNodeWithText("Cantidad total de fechas: 10").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Carpeta médica: lunes 24 de agosto de 2026")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("martes 1 de septiembre de 2026 — se creará")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag("v2-recurring-save").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test
    fun emptyListIsExplicit() {
        setSurface(
            V2RecurringUiState(
                stage = V2RecurringStage.PLANS,
                timelineId = TIMELINE_ID,
                plansReadSuccessfully = true,
            ),
        )
        compose.onNodeWithText("Todavía no hay planes recurrentes.").assertIsDisplayed()
    }

    @Test
    fun listErrorOffersRetryWithoutPretendingThatTheListWasReadAsEmpty() {
        var retries = 0
        setSurface(
            state = V2RecurringUiState(
                stage = V2RecurringStage.PLANS,
                timelineId = TIMELINE_ID,
                errorMessage = "No pudimos leer los planes.",
                canRetry = true,
            ),
            actions = V2RecurringActions(retry = { retries++ }),
        )

        compose.onNodeWithText("Todavía no hay planes recurrentes.").assertDoesNotExist()
        compose.onNodeWithText("Reintentar").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun planDetailOffersFutureChangeAndFinalizationAndNamesEveryOccurrenceState() {
        val aggregate = aggregate()
        setSurface(
            V2RecurringUiState(
                stage = V2RecurringStage.PLAN_DETAIL,
                timelineId = TIMELINE_ID,
                referenceDate = START,
                plans = listOf(aggregate),
                selectedPlanId = PLAN_ID,
                selectedPlan = aggregate,
            ),
        )
        compose.onNodeWithText("Estado: activo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Automáticas 0 · personalizadas 0 · excluidas 0 · retiradas 0")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag("v2-recurring-change-plan").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("v2-recurring-finalize-plan").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun discardStatesExplicitlyThatNoJourneyWasWritten() {
        setSurface(formState().copy(stage = V2RecurringStage.CONFIRM_DISCARD))
        compose.onNodeWithTag("v2-recurring-discard-dialog").assertIsDisplayed()
        compose.onNodeWithText("No se escribió ninguna jornada.", substring = true).assertIsDisplayed()
    }

    @Test
    fun primaryReviewStaysReachableInBothThemesOrientationsAndEveryInternalZoom() {
        var dark by mutableStateOf(false)
        var landscape by mutableStateOf(false)
        var zoom by mutableStateOf(AppZoom.STANDARD)
        compose.setContent {
            MiGuardiaTheme(darkTheme = dark, appZoom = zoom) {
                Box(
                    Modifier.size(
                        width = if (landscape) 820.dp else 360.dp,
                        height = if (landscape) 460.dp else 760.dp,
                    ),
                ) {
                    V2RecurringPlanSurfaceHost(formState(), V2RecurringActions())
                }
            }
        }

        listOf(false, true).forEach { darkTheme ->
            listOf(false, true).forEach { useLandscape ->
                AppZoom.entries.forEach { appZoom ->
                    compose.runOnIdle {
                        dark = darkTheme
                        landscape = useLandscape
                        zoom = appZoom
                    }
                    compose.onNodeWithTag("v2-recurring-review")
                        .performScrollTo()
                        .assertIsDisplayed()
                        .assertIsEnabled()
                }
            }
        }
    }

    private fun setSurface(
        state: V2RecurringUiState,
        actions: V2RecurringActions = V2RecurringActions(),
        darkTheme: Boolean = true,
        zoom: AppZoom = AppZoom.STANDARD,
    ) {
        compose.setContent {
            MiGuardiaTheme(darkTheme = darkTheme, appZoom = zoom) {
                Box(Modifier.size(width = 820.dp, height = 460.dp)) {
                    V2RecurringPlanSurfaceHost(state, actions)
                }
            }
        }
    }

    private fun formState() = V2RecurringUiState(
        stage = V2RecurringStage.FORM,
        mode = V2RecurringMode.CREATE,
        timelineId = TIMELINE_ID,
        referenceDate = START,
        draftPlanId = PLAN_ID,
        templateOptions = listOf(option()),
        selectedTemplateId = TEMPLATE_ID,
        weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        monthlyOrdinal = MonthlyOrdinal.LAST,
        monthlyDayOfWeek = DayOfWeek.FRIDAY,
        startDateText = START.toString(),
        endDateText = START.plusMonths(1).toString(),
    )

    private fun preview(dates: List<LocalDate>) = RecurringMutationPreview(
        patternDescription = "Cada lunes y miércoles",
        dates = dates,
        results = dates.map { RecurringDateResult(it, RecurringDateAction.CREATE) },
        warnings = emptyList(),
        medicalLeaveDates = setOf(START.plusDays(1)),
        mutation = RecurringPlanMutation(
            planToInsert = plan(),
            revisionToInsert = revision(end = dates.last()),
        ),
    )

    private fun aggregate() = RecurringPlanAggregate(
        plan = plan(),
        revisions = listOf(revision()),
        occurrences = emptyList(),
    )

    private fun plan() = RecurringPlan(PLAN_ID, TIMELINE_ID, WorkSector.NURSING, NOW)

    private fun revision(end: LocalDate = START.plusMonths(1)) = RecurringPlanRevision(
        id = REVISION_ID,
        planId = PLAN_ID,
        revisionNumber = 1,
        effectiveFrom = START,
        kind = RecurringPlanRevisionKind.ACTIVE,
        endDateInclusive = end,
        pattern = RecurringPattern.Weekdays.of(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
        templateId = TEMPLATE_ID,
        workPlaceId = PLACE_ID,
        objectiveId = OBJECTIVE_ID,
        workTypeId = TYPE_ID,
        objectiveNameSnapshot = "Lugar ficticio",
        objectiveAbbreviationSnapshot = "FIC",
        objectiveAddressSnapshot = null,
        workTypeNameSnapshot = "Trabajo habitual",
        workTypeBehaviorSnapshot = option().workType.behavior,
        startTimeSnapshot = LocalTime.of(21, 0),
        endTimeSnapshot = LocalTime.of(6, 0),
        colorArgbSnapshot = 0xFF336699.toInt(),
        positionSnapshot = "Puesto ficticio",
        zoneId = ZONE,
        createdAt = NOW,
    )

    private fun option(): V2RecurringTemplateOption {
        val objective = Objective(
            id = OBJECTIVE_ID,
            fullName = "Lugar ficticio",
            abbreviation = "FIC",
            address = null,
            note = null,
            isActive = true,
            createdAt = NOW,
            updatedAt = NOW,
        )
        val place = WorkPlace(PLACE_ID, TIMELINE_ID, WorkSector.NURSING, objective.id, true, NOW, NOW)
        val type = WorkType.create(TYPE_ID, TIMELINE_ID, WorkSector.NURSING, "Trabajo habitual", NOW)
        val template = WorkTemplate(
            id = TEMPLATE_ID,
            timelineId = TIMELINE_ID,
            sector = WorkSector.NURSING,
            workPlaceId = place.id,
            objectiveId = objective.id,
            workTypeId = type.id,
            startTime = LocalTime.of(21, 0),
            endTime = LocalTime.of(6, 0),
            colorArgb = 0xFF336699.toInt(),
            isActive = true,
            createdAt = NOW,
            updatedAt = NOW,
        )
        return V2RecurringTemplateOption(objective, place, type, template)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-23T12:00:00Z")
        val START: LocalDate = LocalDate.of(2026, 8, 23)
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val TIMELINE_ID: UUID = UUID(0L, 1L)
        val OBJECTIVE_ID: UUID = UUID(0L, 2L)
        val PLACE_ID: UUID = UUID(0L, 3L)
        val TYPE_ID: UUID = UUID(0L, 4L)
        val TEMPLATE_ID: UUID = UUID(0L, 5L)
        val PLAN_ID: UUID = UUID(0L, 6L)
        val REVISION_ID: UUID = UUID(0L, 7L)
    }
}
