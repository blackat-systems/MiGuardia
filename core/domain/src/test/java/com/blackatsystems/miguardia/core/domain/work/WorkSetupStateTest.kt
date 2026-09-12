package com.blackatsystems.miguardia.core.domain.work

import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.repository.InvalidV2SelectionException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkSetupStateTest {
    @Test
    fun absentHistoryMeansFreshInstall() {
        assertEquals(WorkSetupState.FreshInstall, projectLoadedWorkSetupState(null, null, TODAY))
    }

    @Test
    fun historyWithoutApplicableRevisionOrCatalogIsAControlledLoadError() {
        val futureHistory = history(listOf(revision(date = TODAY.plusDays(1))))
        assertEquals(WorkSetupState.LoadError, projectLoadedWorkSetupState(futureHistory, null, TODAY))
        assertEquals(WorkSetupState.LoadError, projectLoadedWorkSetupState(history(), null, TODAY))
    }

    @Test
    fun emptyCatalogReportsEveryMissingRequirement() {
        val state = projectLoadedWorkSetupState(history(), emptyCatalog(), TODAY)
        assertTrue(state is WorkSetupState.V2NeedsFirstSet)
        assertEquals(MissingWorkSetupRequirement.entries.toSet(), (state as WorkSetupState.V2NeedsFirstSet).missing)
    }

    @Test
    fun readyRequiresAnActiveCoherentTemplateAndApplicableRule() {
        val revision = revision()
        assertEquals(
            WorkSetupState.V2Ready(TIMELINE_ID, revision),
            projectLoadedWorkSetupState(history(listOf(revision)), readyCatalog(), TODAY),
        )
    }

    @Test
    fun placeTypeTemplateAndRuleAvailabilityAreEvaluatedIndependently() {
        val noActivePlace = projectLoadedWorkSetupState(
            history(),
            readyCatalog(placeActive = false),
            TODAY,
        ) as WorkSetupState.V2NeedsFirstSet
        assertEquals(
            setOf(
                MissingWorkSetupRequirement.ACTIVE_WORK_PLACE,
                MissingWorkSetupRequirement.APPLICABLE_WORKPLACE_RULE,
                MissingWorkSetupRequirement.ACTIVE_WORK_TEMPLATE,
            ),
            noActivePlace.missing,
        )

        val noActiveType = projectLoadedWorkSetupState(
            history(),
            readyCatalog(typeActive = false),
            TODAY,
        ) as WorkSetupState.V2NeedsFirstSet
        assertEquals(
            setOf(
                MissingWorkSetupRequirement.ACTIVE_WORK_TYPE,
                MissingWorkSetupRequirement.ACTIVE_WORK_TEMPLATE,
            ),
            noActiveType.missing,
        )

        val noActiveTemplate = projectLoadedWorkSetupState(
            history(),
            readyCatalog(templateActive = false),
            TODAY,
        ) as WorkSetupState.V2NeedsFirstSet
        assertEquals(
            setOf(MissingWorkSetupRequirement.ACTIVE_WORK_TEMPLATE),
            noActiveTemplate.missing,
        )

        val noApplicableRule = projectLoadedWorkSetupState(
            history(),
            readyCatalog(ruleDate = TODAY.plusDays(1)),
            TODAY,
        ) as WorkSetupState.V2NeedsFirstSet
        assertEquals(
            setOf(
                MissingWorkSetupRequirement.APPLICABLE_WORKPLACE_RULE,
                MissingWorkSetupRequirement.ACTIVE_WORK_TEMPLATE,
            ),
            noApplicableRule.missing,
        )
    }

    @Test
    fun selectionUsesTheApplicableRevisionForEveryDateAndRejectsMixedSectors() {
        val first = revision(REVISION_ID, TODAY, WorkSector.PRIVATE_SECURITY)
        val second = revision(OTHER_REVISION_ID, TODAY.plusDays(10), WorkSector.PRIVATE_SECURITY)
        val selection = classifyWorkDateSelection(
            history(listOf(first, second)),
            setOf(TODAY, TODAY.plusDays(12)),
        ) as WorkDateSelection.V2
        assertEquals(first, selection.configurationRevisionsByDate[TODAY])
        assertEquals(second, selection.configurationRevisionsByDate[TODAY.plusDays(12)])

        assertThrows(InvalidV2SelectionException::class.java) {
            classifyWorkDateSelection(
                history(listOf(first, second.copy(value = second.value.copy(sector = WorkSector.POLICE)))),
                setOf(TODAY, TODAY.plusDays(12)),
            )
        }
    }

    @Test
    fun dateBeforeFirstRevisionRequestsConsciousV2Backfill() {
        val selection = classifyWorkDateSelection(
            history(),
            setOf(TODAY.minusDays(3), TODAY),
        ) as WorkDateSelection.NeedsNewV2Backfill

        assertEquals(TODAY.minusDays(3), selection.earliestDate)
        assertEquals(WorkSector.PRIVATE_SECURITY, selection.sector)
    }

    private fun history(
        revisions: List<EffectiveRevision<WorkConfiguration>> = listOf(revision()),
    ) = WorkConfigurationHistory(
        timeline = EffectiveDateTimeline(TIMELINE_ID, revisions),
        perPeriodHoursValues = PerPeriodHoursValues(emptyList()),
    )

    private fun revision(
        id: UUID = REVISION_ID,
        date: LocalDate = TODAY,
        sector: WorkSector = WorkSector.PRIVATE_SECURITY,
    ) = EffectiveRevision(
        id = id,
        effectiveFrom = date,
        value = WorkConfiguration(sector, HoursReference.PendingSetup, availabilityLabel = null),
    )

    private fun emptyCatalog() = WorkCatalog(
        timelineId = TIMELINE_ID,
        sector = WorkSector.PRIVATE_SECURITY,
        workPlaces = emptyList(),
        workTypes = emptyList(),
        workTemplates = emptyList(),
        workplaceRuleRevisions = emptyList(),
    )

    private fun readyCatalog(
        placeActive: Boolean = true,
        typeActive: Boolean = true,
        templateActive: Boolean = true,
        ruleDate: LocalDate = TODAY,
    ): WorkCatalog {
        val objective = Objective(OBJECTIVE_ID, "Hospital", "HOS", null, null, true, NOW, NOW)
        val place = WorkPlace(
            PLACE_ID,
            TIMELINE_ID,
            WorkSector.PRIVATE_SECURITY,
            objective.id,
            placeActive,
            NOW,
            NOW,
        )
        val type = WorkType.create(
            TYPE_ID,
            TIMELINE_ID,
            WorkSector.PRIVATE_SECURITY,
            "Guardia",
            NOW,
        ).copy(isActive = typeActive)
        val template = WorkTemplate(
            TEMPLATE_ID,
            TIMELINE_ID,
            WorkSector.PRIVATE_SECURITY,
            place.id,
            objective.id,
            type.id,
            LocalTime.of(8, 0),
            LocalTime.of(16, 0),
            0xFF336699.toInt(),
            templateActive,
            NOW,
            NOW,
        )
        val rule = WorkplaceRuleRevision(
            RULE_ID,
            TIMELINE_ID,
            WorkSector.PRIVATE_SECURITY,
            place.id,
            objective.id,
            ruleDate,
            WorkplaceRules(NightHoursRule.Disabled, WeekendRule.None, HolidayRule(false, false)),
            NOW,
        )
        return WorkCatalog(TIMELINE_ID, WorkSector.PRIVATE_SECURITY, listOf(place), listOf(type), listOf(template), listOf(rule))
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 8, 21)
        val NOW: Instant = Instant.parse("2026-08-21T12:00:00Z")
        val TIMELINE_ID: UUID = UUID.fromString("83000000-0000-0000-0000-000000000001")
        val REVISION_ID: UUID = UUID.fromString("83000000-0000-0000-0000-000000000002")
        val OTHER_REVISION_ID: UUID = UUID.fromString("83000000-0000-0000-0000-000000000003")
        val OBJECTIVE_ID: UUID = UUID.fromString("83000000-0000-0000-0000-000000000004")
        val PLACE_ID: UUID = UUID.fromString("83000000-0000-0000-0000-000000000005")
        val TYPE_ID: UUID = UUID.fromString("83000000-0000-0000-0000-000000000006")
        val TEMPLATE_ID: UUID = UUID.fromString("83000000-0000-0000-0000-000000000007")
        val RULE_ID: UUID = UUID.fromString("83000000-0000-0000-0000-000000000008")
    }
}
