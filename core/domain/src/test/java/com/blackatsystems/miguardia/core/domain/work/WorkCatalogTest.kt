package com.blackatsystems.miguardia.core.domain.work

import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkCatalogTest {
    @Test
    fun equalStartAndEndRepresentsTwentyFourHours() {
        assertEquals(Duration.ofHours(24), template(start = time(8), end = time(8)).plannedDuration)
        assertEquals(24 * 60, template(start = time(8), end = time(8)).plannedDurationMinutes)
    }

    @Test
    fun twoTypesMayShareTheSamePlaceAndExactInterval() {
        val consultorio = type(TYPE_ID, "Consultorio")
        val guardia = type(OTHER_TYPE_ID, "Guardia habitual")
        val templates = listOf(
            template(id = TEMPLATE_ID, typeId = consultorio.id),
            template(id = OTHER_TEMPLATE_ID, typeId = guardia.id),
        )

        val catalog = catalog(types = listOf(consultorio, guardia), templates = templates)

        assertEquals(2, catalog.workTemplates.size)
    }

    @Test
    fun exactDuplicateTemplateIsRejectedEvenWithDifferentId() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog(
                types = listOf(type()),
                templates = listOf(template(), template(id = OTHER_TEMPLATE_ID)),
            )
        }
    }

    @Test
    fun capitalizationNameNeverChangesActiveWorkBehavior() {
        val type = type(name = "Capacitación")

        assertEquals(WorkTypeBehavior.ACTIVE_WORK, type.behavior)
        assertEquals("CAPACITACIÓN", type.normalizedNameKey)
    }

    @Test
    fun archivedParentsAndTheirActiveTemplateRemainQueryableAsHistory() {
        val archivedPlace = place().copy(isActive = false, updatedAt = NOW.plusSeconds(1))
        val historical = catalog(place = archivedPlace, templates = listOf(template()))

        assertEquals(listOf(archivedPlace), historical.workPlaces)
        assertTrue(historical.workTemplates.single().isActive)
    }

    @Test
    fun everyStoredPlaceRequiresAtLeastOneRuleRevision() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkCatalog(
                timelineId = TIMELINE_ID,
                sector = SECTOR,
                workPlaces = listOf(place()),
                workTypes = emptyList(),
                workTemplates = emptyList(),
                workplaceRuleRevisions = emptyList(),
            )
        }
    }

    @Test
    fun firstSetRequiresOneCoherentActiveAtomicBundle() {
        val bundle = FirstWorkSet(
            objective = objective(),
            workPlace = place(),
            firstRuleRevision = rule(),
            configurationContext = configurationContext(),
            workType = type(),
            workTemplate = template(),
        )

        assertTrue(bundle.workPlace.isActive)
        assertTrue(bundle.workTemplate.isActive)
        assertEquals(bundle.objective.id, bundle.workPlace.objectiveId)
    }

    @Test
    fun adoptedLegacyScheduleMustBelongToObjectiveAndKeepExpectedTimes() {
        val legacy = legacySchedule().copy(objectiveId = OTHER_OBJECTIVE_ID)

        assertThrows(IllegalArgumentException::class.java) {
            WorkPlaceAdoption(
                workPlaceCandidate = place(),
                firstRuleRevisionCandidate = rule(),
                configurationContext = configurationContext(),
                workTypeToCreate = type(),
                workTemplateToCreate = template(legacyId = legacy.id),
                expectedLegacyScheduleCombination = legacy,
            )
        }
    }

    @Test
    fun adoptionMayBuildTemplateWithAnExistingTypeResolvedByRepository() {
        val legacy = legacySchedule()

        val adoption = WorkPlaceAdoption(
            workPlaceCandidate = place(),
            firstRuleRevisionCandidate = rule(),
            configurationContext = configurationContext(),
            workTypeToCreate = null,
            workTemplateToCreate = template(legacyId = legacy.id),
            expectedLegacyScheduleCombination = legacy,
        )

        assertEquals(TYPE_ID, adoption.workTemplateToCreate?.workTypeId)
    }

    @Test
    fun repeatedAdoptionReusesExistingPlaceAndRebindsCandidateTemplateExplicitly() {
        val legacy = legacySchedule()
        val adoption = WorkPlaceAdoption(
            workPlaceCandidate = place(),
            firstRuleRevisionCandidate = rule(),
            configurationContext = configurationContext(),
            workTypeToCreate = type(),
            workTemplateToCreate = template(legacyId = legacy.id),
            expectedLegacyScheduleCombination = legacy,
        )
        val existing = place().copy(
            id = EXISTING_PLACE_ID,
            isActive = false,
            updatedAt = NOW.plusSeconds(1),
        )

        val resolved = adoption.resolve(existing)

        assertTrue(resolved.reusedExisting)
        assertEquals(existing, resolved.workPlace)
        assertNull(resolved.workPlaceToCreate)
        assertNull(resolved.firstRuleRevisionToCreate)
        assertEquals(EXISTING_PLACE_ID, resolved.workTemplateToCreate?.workPlaceId)
        assertFalse(resolved.workPlace.isActive)
    }

    @Test
    fun firstRuleMustStartWithTheResolvedConfigurationRevision() {
        assertThrows(IllegalArgumentException::class.java) {
            NewWorkPlace(
                objective = objective(),
                workPlace = place(),
                firstRuleRevision = rule(date = DATE.plusDays(1)),
                configurationContext = configurationContext(),
            )
        }
    }

    @Test
    fun updateCommandsPreserveCatalogIdentityAndUseSeparateArchiveActions() {
        val previousPlace = place()
        val previousObjective = objective()
        val timestamp = NOW.plusSeconds(1)
        val updatedObjective = previousObjective.copy(
            fullName = " Hospital Central ",
            updatedAt = timestamp,
        ).normalizedForV2Update(previousObjective, timestamp)
        val validPlaceUpdate = WorkPlaceUpdate(
            previousWorkPlace = previousPlace,
            updatedWorkPlace = previousPlace.copy(updatedAt = timestamp),
            previousObjective = previousObjective,
            updatedObjective = updatedObjective,
        )

        assertEquals("Hospital Central", validPlaceUpdate.updatedObjective.fullName)
        assertThrows(IllegalArgumentException::class.java) {
            validPlaceUpdate.copy(
                updatedWorkPlace = validPlaceUpdate.updatedWorkPlace.copy(objectiveId = OTHER_OBJECTIVE_ID),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkTypeUpdate(
                previous = type(),
                updated = type().copy(isActive = false, updatedAt = timestamp),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkTemplateUpdate(
                previous = template(),
                updated = template().copy(workTypeId = OTHER_TYPE_ID, updatedAt = timestamp),
            )
        }
    }

    @Test
    fun newV2BackfillRequiresRealEquivalentExtensionAndEquivalentRules() {
        val currentHistory = history()
        val earlierDate = DATE.minusDays(3)
        val sourceRule = rule(date = DATE)
        val earlierRule = rule(date = earlierDate, id = BACKFILL_RULE_ID)
        val extension = NewV2Backfill(
            currentHistory = currentHistory,
            configurationRevision = revision(
                id = BACKFILL_CONFIGURATION_REVISION_ID,
                date = earlierDate,
            ),
            workplaceRuleBackfills = listOf(WorkplaceRuleBackfill(sourceRule, earlierRule)),
        )

        assertEquals(earlierDate, extension.configurationRevision.effectiveFrom)
        assertEquals(listOf(earlierRule), extension.workplaceRuleRevisions)

        assertThrows(IllegalArgumentException::class.java) {
            extension.copy(
                currentHistory = history(origin = WorkConfigurationOrigin.MIGRATED_V1),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            extension.copy(
                configurationRevision = revision(
                    id = BACKFILL_CONFIGURATION_REVISION_ID,
                    date = earlierDate,
                    hoursReference = HoursReference.NotUsed,
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkplaceRuleBackfill(
                sourceRevision = sourceRule,
                earlierRevision = earlierRule.copy(
                    rules = WorkplaceRules(
                        nightHours = NightHoursRule.Disabled,
                        weekend = WeekendRule.None,
                        holiday = HolidayRule(true, false),
                    ),
                ),
            )
        }
    }

    @Test
    fun ruleResolutionUsesLatestRevisionAtOrBeforeDate() {
        val first = rule(date = DATE.minusMonths(1), id = RULE_ID)
        val second = rule(date = DATE, id = OTHER_RULE_ID)
        val catalog = catalog(rules = listOf(second, first))

        assertEquals(first, catalog.ruleRevisionAt(PLACE_ID, DATE.minusDays(1)))
        assertEquals(second, catalog.ruleRevisionAt(PLACE_ID, DATE))
    }

    private fun catalog(
        place: WorkPlace = place(),
        types: List<WorkType> = listOf(type()),
        templates: List<WorkTemplate> = listOf(template()),
        rules: List<WorkplaceRuleRevision> = listOf(rule()),
    ) = WorkCatalog(
        timelineId = TIMELINE_ID,
        sector = SECTOR,
        workPlaces = listOf(place),
        workTypes = types,
        workTemplates = templates,
        workplaceRuleRevisions = rules,
    )

    private fun place() = WorkPlace(
        id = PLACE_ID,
        timelineId = TIMELINE_ID,
        sector = SECTOR,
        objectiveId = OBJECTIVE_ID,
        isActive = true,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun type(
        id: UUID = TYPE_ID,
        name: String = "Guardia habitual",
    ) = WorkType.create(id, TIMELINE_ID, SECTOR, name, NOW)

    private fun template(
        id: UUID = TEMPLATE_ID,
        typeId: UUID = TYPE_ID,
        start: LocalTime = time(8),
        end: LocalTime = time(16),
        legacyId: UUID? = null,
    ) = WorkTemplate(
        id = id,
        timelineId = TIMELINE_ID,
        sector = SECTOR,
        workPlaceId = PLACE_ID,
        objectiveId = OBJECTIVE_ID,
        workTypeId = typeId,
        startTime = start,
        endTime = end,
        colorArgb = 0xFF336699.toInt(),
        isActive = true,
        legacyScheduleCombinationId = legacyId,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun rule(
        date: LocalDate = DATE,
        id: UUID = RULE_ID,
    ) = WorkplaceRuleRevision(
        id = id,
        timelineId = TIMELINE_ID,
        sector = SECTOR,
        workPlaceId = PLACE_ID,
        objectiveId = OBJECTIVE_ID,
        effectiveFrom = date,
        rules = WorkplaceRules(
            nightHours = NightHoursRule.Disabled,
            weekend = WeekendRule.None,
            holiday = HolidayRule(false, false),
        ),
        createdAt = NOW,
    )

    private fun objective() = Objective(
        id = OBJECTIVE_ID,
        fullName = "Hospital Norte",
        abbreviation = "HNO",
        address = null,
        note = null,
        isActive = true,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun legacySchedule() = ScheduleCombination(
        id = LEGACY_SCHEDULE_ID,
        objectiveId = OBJECTIVE_ID,
        startTime = time(8),
        endTime = time(16),
        colorArgb = 0xFF336699.toInt(),
        isActive = true,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun time(hour: Int) = LocalTime.of(hour, 0)

    private fun configurationContext() = ResolvedWorkConfigurationRevision.resolve(history(), DATE)

    private fun history(
        origin: WorkConfigurationOrigin = WorkConfigurationOrigin.NEW_V2,
    ) = WorkConfigurationHistory(
        origin = origin,
        timeline = EffectiveDateTimeline(TIMELINE_ID, listOf(revision())),
        perPeriodHoursValues = PerPeriodHoursValues(emptyList()),
    )

    private fun revision(
        id: UUID = CONFIGURATION_REVISION_ID,
        date: LocalDate = DATE,
        hoursReference: HoursReference = HoursReference.PendingSetup,
    ) = EffectiveRevision(
        id = id,
        effectiveFrom = date,
        value = WorkConfiguration(SECTOR, hoursReference, availabilityLabel = null),
    )

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 21)
        val NOW: Instant = Instant.parse("2026-08-21T12:00:00Z")
        val SECTOR: WorkSector = WorkSector.PRIVATE_SECURITY
        val TIMELINE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000001")
        val OBJECTIVE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000002")
        val OTHER_OBJECTIVE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000003")
        val PLACE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000004")
        val EXISTING_PLACE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000012")
        val TYPE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000005")
        val OTHER_TYPE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000006")
        val TEMPLATE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000007")
        val OTHER_TEMPLATE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000008")
        val RULE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000009")
        val OTHER_RULE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000010")
        val LEGACY_SCHEDULE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000011")
        val CONFIGURATION_REVISION_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000013")
        val BACKFILL_CONFIGURATION_REVISION_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000014")
        val BACKFILL_RULE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000015")
    }
}
