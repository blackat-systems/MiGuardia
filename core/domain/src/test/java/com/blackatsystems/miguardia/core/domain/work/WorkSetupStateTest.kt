package com.blackatsystems.miguardia.core.domain.work

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
    fun absentHistoryMeansFreshInstallWithoutLookingAtShifts() {
        assertEquals(
            WorkSetupState.FreshInstall,
            projectLoadedWorkSetupState(history = null, catalog = null, referenceDate = TODAY),
        )
    }

    @Test
    fun migratedV1WithoutRevisionsRemainsLegacyEvenWithoutAnyCatalog() {
        assertEquals(
            WorkSetupState.LegacyV1(TIMELINE_ID),
            projectLoadedWorkSetupState(migratedHistory(emptyList()), null, TODAY),
        )
    }

    @Test
    fun futureV2ActivationDoesNotAdvanceMigratedUserMode() {
        val future = revision(REVISION_ID, TODAY.plusDays(10), WorkSector.POLICE)

        assertEquals(
            WorkSetupState.LegacyV1WithFutureActivation(TIMELINE_ID, future),
            projectLoadedWorkSetupState(migratedHistory(listOf(future)), null, TODAY),
        )
    }

    @Test
    fun applicableV2NeedsARealCatalogAndNeverTreatsLoadFailureAsEmpty() {
        val history = newHistory(listOf(revision()))

        assertEquals(WorkSetupState.LoadError, projectLoadedWorkSetupState(history, null, TODAY))
        val state = projectLoadedWorkSetupState(history, emptyCatalog(), TODAY)
        assertTrue(state is WorkSetupState.V2NeedsFirstSet)
        assertEquals(
            MissingWorkSetupRequirement.entries.toSet(),
            (state as WorkSetupState.V2NeedsFirstSet).missing,
        )
    }

    @Test
    fun readyRequiresOneCoherentActiveTemplateAndApplicablePlaceRule() {
        val configuration = revision()
        val state = projectLoadedWorkSetupState(
            history = newHistory(listOf(configuration)),
            catalog = readyCatalog(),
            referenceDate = TODAY,
        )

        assertEquals(WorkSetupState.V2Ready(TIMELINE_ID, configuration), state)
    }

    @Test
    fun readinessFiltersPlaceTypeAndTemplateActivityIndependently() {
        val history = newHistory(listOf(revision()))

        val archivedPlace = projectLoadedWorkSetupState(
            history,
            readyCatalog(placeActive = false),
            TODAY,
        ) as WorkSetupState.V2NeedsFirstSet
        assertTrue(MissingWorkSetupRequirement.ACTIVE_WORK_PLACE in archivedPlace.missing)
        assertTrue(MissingWorkSetupRequirement.ACTIVE_WORK_TEMPLATE in archivedPlace.missing)

        val archivedType = projectLoadedWorkSetupState(
            history,
            readyCatalog(typeActive = false),
            TODAY,
        ) as WorkSetupState.V2NeedsFirstSet
        assertTrue(MissingWorkSetupRequirement.ACTIVE_WORK_TYPE in archivedType.missing)
        assertTrue(MissingWorkSetupRequirement.ACTIVE_WORK_TEMPLATE in archivedType.missing)

        val archivedTemplate = projectLoadedWorkSetupState(
            history,
            readyCatalog(templateActive = false),
            TODAY,
        ) as WorkSetupState.V2NeedsFirstSet
        assertEquals(
            setOf(MissingWorkSetupRequirement.ACTIVE_WORK_TEMPLATE),
            archivedTemplate.missing,
        )
    }

    @Test
    fun migratedSelectionCannotMixLegacyAndV2Dates() {
        val history = migratedHistory(listOf(revision(date = TODAY)))

        assertThrows(InvalidV2SelectionException::class.java) {
            classifyWorkDateSelection(history, setOf(TODAY.minusDays(1), TODAY))
        }
    }

    @Test
    fun sameSectorMayUseDifferentConfigurationRevisionForEachDate() {
        val first = revision(REVISION_ID, TODAY)
        val second = revision(OTHER_REVISION_ID, TODAY.plusDays(10))
        val selection = classifyWorkDateSelection(
            newHistory(listOf(first, second)),
            setOf(TODAY, TODAY.plusDays(12)),
        ) as WorkDateSelection.V2

        assertEquals(first, selection.configurationRevisionsByDate[TODAY])
        assertEquals(second, selection.configurationRevisionsByDate[TODAY.plusDays(12)])
    }

    @Test
    fun differentSectorsMustBeLoadedSeparately() {
        val first = revision(REVISION_ID, TODAY, WorkSector.PRIVATE_SECURITY)
        val second = revision(OTHER_REVISION_ID, TODAY.plusDays(10), WorkSector.POLICE)

        assertThrows(InvalidV2SelectionException::class.java) {
            classifyWorkDateSelection(
                newHistory(listOf(first, second)),
                setOf(TODAY, TODAY.plusDays(12)),
            )
        }
    }

    @Test
    fun newV2DateBeforeFirstRevisionRequestsConsciousBackfillInsteadOfBecomingV1() {
        val selection = classifyWorkDateSelection(
            newHistory(listOf(revision(date = TODAY))),
            setOf(TODAY.minusDays(3), TODAY),
        ) as WorkDateSelection.NeedsNewV2Backfill

        assertEquals(TODAY.minusDays(3), selection.earliestDate)
        assertEquals(WorkSector.PRIVATE_SECURITY, selection.sector)
        assertEquals(setOf(TODAY.minusDays(3), TODAY), selection.dates)
    }

    private fun migratedHistory(revisions: List<EffectiveRevision<WorkConfiguration>>) = history(
        WorkConfigurationOrigin.MIGRATED_V1,
        revisions,
    )

    private fun newHistory(revisions: List<EffectiveRevision<WorkConfiguration>>) = history(
        WorkConfigurationOrigin.NEW_V2,
        revisions,
    )

    private fun history(
        origin: WorkConfigurationOrigin,
        revisions: List<EffectiveRevision<WorkConfiguration>>,
    ) = WorkConfigurationHistory(
        origin = origin,
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
    ): WorkCatalog {
        val place = WorkPlace(
            PLACE_ID,
            TIMELINE_ID,
            WorkSector.PRIVATE_SECURITY,
            OBJECTIVE_ID,
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
            PLACE_ID,
            OBJECTIVE_ID,
            TYPE_ID,
            LocalTime.of(8, 0),
            LocalTime.of(16, 0),
            0xFF336699.toInt(),
            templateActive,
            null,
            NOW,
            NOW,
        )
        val rule = WorkplaceRuleRevision(
            RULE_ID,
            TIMELINE_ID,
            WorkSector.PRIVATE_SECURITY,
            PLACE_ID,
            OBJECTIVE_ID,
            TODAY,
            WorkplaceRules(
                NightHoursRule.Disabled,
                WeekendRule.None,
                HolidayRule(false, false),
            ),
            NOW,
        )
        return WorkCatalog(
            TIMELINE_ID,
            WorkSector.PRIVATE_SECURITY,
            listOf(place),
            listOf(type),
            listOf(template),
            listOf(rule),
        )
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
