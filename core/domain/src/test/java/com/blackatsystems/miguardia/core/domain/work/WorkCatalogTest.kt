package com.blackatsystems.miguardia.core.domain.work

import com.blackatsystems.miguardia.core.domain.model.Objective
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkCatalogTest {
    @Test
    fun equalStartAndEndRepresentsTwentyFourHours() {
        assertEquals(Duration.ofHours(24), template(start = time(8), end = time(8)).plannedDuration)
    }

    @Test
    fun exactDuplicateTemplateIsRejectedButDifferentTypesMayShareAnInterval() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog(templates = listOf(template(), template(id = OTHER_TEMPLATE_ID)))
        }

        val otherType = type(OTHER_TYPE_ID, "Consultorio")
        val valid = catalog(
            types = listOf(type(), otherType),
            templates = listOf(template(), template(id = OTHER_TEMPLATE_ID, typeId = otherType.id)),
        )
        assertEquals(2, valid.workTemplates.size)
    }

    @Test
    fun typeNamesAreNormalizedAndAlwaysKeepActiveWorkBehavior() {
        val workType = WorkType.create(TYPE_ID, TIMELINE_ID, SECTOR, " Capacitación ", NOW)

        assertEquals("Capacitación", workType.name)
        assertEquals("CAPACITACIÓN", workType.normalizedNameKey)
        assertEquals(WorkTypeBehavior.ACTIVE_WORK, workType.behavior)
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
    fun firstWorkSetRequiresOneCoherentActiveBundle() {
        val bundle = FirstWorkSet(
            objective = objective(),
            workPlace = place(),
            firstRuleRevision = rule(),
            configurationContext = configurationContext(),
            workType = type(),
            workTemplate = template(),
        )

        assertTrue(bundle.workPlace.isActive)
        assertEquals(bundle.objective.id, bundle.workPlace.objectiveId)
        assertEquals(bundle.workPlace.id, bundle.workTemplate.workPlaceId)
    }

    @Test
    fun firstRuleMustBeginWithTheApplicableConfigurationRevision() {
        assertThrows(IllegalArgumentException::class.java) {
            FirstWorkSet(
                objective = objective(),
                workPlace = place(),
                firstRuleRevision = rule(date = DATE.plusDays(1)),
                configurationContext = configurationContext(),
                workType = type(),
                workTemplate = template(),
            )
        }
    }

    @Test
    fun backfillMustExtendTheFirstV2RevisionWithoutChangingIt() {
        val earlierDate = DATE.minusDays(3)
        val sourceRule = rule(date = DATE)
        val earlierRule = rule(date = earlierDate, id = BACKFILL_RULE_ID)
        val extension = NewV2Backfill(
            currentHistory = history(),
            configurationRevision = revision(BACKFILL_REVISION_ID, earlierDate),
            workplaceRuleBackfills = listOf(WorkplaceRuleBackfill(sourceRule, earlierRule)),
        )

        assertEquals(earlierDate, extension.configurationRevision.effectiveFrom)
        assertEquals(listOf(earlierRule), extension.workplaceRuleRevisions)
        assertThrows(IllegalArgumentException::class.java) {
            extension.copy(configurationRevision = revision(BACKFILL_REVISION_ID, earlierDate, HoursReference.NotUsed))
        }
    }

    @Test
    fun backfillMustPreserveTheExactWorkplaceRules() {
        val source = rule(date = DATE)
        val changedRules = source.rules.copy(
            holiday = HolidayRule(differentTreatment = true, showDedicatedSummary = false),
        )

        assertThrows(IllegalArgumentException::class.java) {
            WorkplaceRuleBackfill(
                sourceRevision = source,
                earlierRevision = rule(date = DATE.minusDays(1), id = BACKFILL_RULE_ID)
                    .copy(rules = changedRules),
            )
        }
    }

    @Test
    fun ruleResolutionUsesLatestApplicableRevision() {
        val first = rule(date = DATE.minusMonths(1), id = RULE_ID)
        val second = rule(date = DATE, id = OTHER_RULE_ID)
        val catalog = catalog(rules = listOf(second, first))

        assertEquals(first, catalog.ruleRevisionAt(PLACE_ID, DATE.minusDays(1)))
        assertEquals(second, catalog.ruleRevisionAt(PLACE_ID, DATE))
    }

    @Test
    fun updateCommandsCannotChangeCatalogIdentity() {
        val timestamp = NOW.plusSeconds(1)
        assertThrows(IllegalArgumentException::class.java) {
            WorkTemplateUpdate(
                previous = template(),
                updated = template().copy(workTypeId = OTHER_TYPE_ID, updatedAt = timestamp),
            )
        }
    }

    @Test
    fun placeAndTypeUpdatesAcceptContentChangesButNotActivityChanges() {
        val timestamp = NOW.plusSeconds(1)
        val previousObjective = objective()
        val previousPlace = place()
        val updatedObjective = previousObjective.copy(
            fullName = "Hospital Central",
            address = "Calle 1",
            updatedAt = timestamp,
        )
        val placeUpdate = WorkPlaceUpdate(
            previousWorkPlace = previousPlace,
            updatedWorkPlace = previousPlace.copy(updatedAt = timestamp),
            previousObjective = previousObjective,
            updatedObjective = updatedObjective,
        )
        assertEquals("Hospital Central", placeUpdate.updatedObjective.fullName)

        assertThrows(IllegalArgumentException::class.java) {
            placeUpdate.copy(
                updatedWorkPlace = placeUpdate.updatedWorkPlace.copy(isActive = false),
            )
        }

        val previousType = type()
        val typeUpdate = WorkTypeUpdate(
            previous = previousType,
            updated = previousType.withUpdatedName("Turno nocturno", timestamp),
        )
        assertEquals("Turno nocturno", typeUpdate.updated.name)

        assertThrows(IllegalArgumentException::class.java) {
            typeUpdate.copy(updated = typeUpdate.updated.copy(isActive = false))
        }
    }

    @Test
    fun catalogPreservesIndependentActivityForHistoricalReferences() {
        val timestamp = NOW.plusSeconds(1)
        val archivedPlace = place().copy(isActive = false, updatedAt = timestamp)
        val archivedType = type().copy(isActive = false, updatedAt = timestamp)
        val activeTemplate = template()

        val stored = WorkCatalog(
            timelineId = TIMELINE_ID,
            sector = SECTOR,
            workPlaces = listOf(archivedPlace),
            workTypes = listOf(archivedType),
            workTemplates = listOf(activeTemplate),
            workplaceRuleRevisions = listOf(rule()),
        )

        assertFalse(stored.workPlaces.single().isActive)
        assertFalse(stored.workTypes.single().isActive)
        assertTrue(stored.workTemplates.single().isActive)
    }

    private fun catalog(
        types: List<WorkType> = listOf(type()),
        templates: List<WorkTemplate> = listOf(template()),
        rules: List<WorkplaceRuleRevision> = listOf(rule()),
    ) = WorkCatalog(TIMELINE_ID, SECTOR, listOf(place()), types, templates, rules)

    private fun objective() = Objective(
        OBJECTIVE_ID,
        "Hospital Norte",
        "HNO",
        null,
        null,
        true,
        NOW,
        NOW,
    )

    private fun place() = WorkPlace(PLACE_ID, TIMELINE_ID, SECTOR, OBJECTIVE_ID, true, NOW, NOW)

    private fun type(id: UUID = TYPE_ID, name: String = "Guardia habitual") =
        WorkType.create(id, TIMELINE_ID, SECTOR, name, NOW)

    private fun template(
        id: UUID = TEMPLATE_ID,
        typeId: UUID = TYPE_ID,
        start: LocalTime = time(8),
        end: LocalTime = time(16),
    ) = WorkTemplate(
        id,
        TIMELINE_ID,
        SECTOR,
        PLACE_ID,
        OBJECTIVE_ID,
        typeId,
        start,
        end,
        0xFF336699.toInt(),
        true,
        NOW,
        NOW,
    )

    private fun rule(date: LocalDate = DATE, id: UUID = RULE_ID) = WorkplaceRuleRevision(
        id,
        TIMELINE_ID,
        SECTOR,
        PLACE_ID,
        OBJECTIVE_ID,
        date,
        WorkplaceRules(NightHoursRule.Disabled, WeekendRule.None, HolidayRule(false, false)),
        NOW,
    )

    private fun history() = WorkConfigurationHistory(
        timeline = EffectiveDateTimeline(TIMELINE_ID, listOf(revision())),
        perPeriodHoursValues = PerPeriodHoursValues(emptyList()),
    )

    private fun configurationContext() = ResolvedWorkConfigurationRevision.resolve(history(), DATE)

    private fun revision(
        id: UUID = CONFIGURATION_REVISION_ID,
        date: LocalDate = DATE,
        hours: HoursReference = HoursReference.PendingSetup,
    ) = EffectiveRevision(id, date, WorkConfiguration(SECTOR, hours, availabilityLabel = null))

    private fun time(hour: Int) = LocalTime.of(hour, 0)

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 21)
        val NOW: Instant = Instant.parse("2026-08-21T12:00:00Z")
        val SECTOR: WorkSector = WorkSector.PRIVATE_SECURITY
        val TIMELINE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000001")
        val OBJECTIVE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000002")
        val PLACE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000003")
        val TYPE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000004")
        val OTHER_TYPE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000005")
        val TEMPLATE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000006")
        val OTHER_TEMPLATE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000007")
        val RULE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000008")
        val OTHER_RULE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000009")
        val CONFIGURATION_REVISION_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000010")
        val BACKFILL_REVISION_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000011")
        val BACKFILL_RULE_ID: UUID = UUID.fromString("82000000-0000-0000-0000-000000000012")
    }
}
