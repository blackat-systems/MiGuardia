package com.blackatsystems.miguardia.core.domain.shift

import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ShiftOccupancyExpectation
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationOrigin
import com.blackatsystems.miguardia.core.domain.work.WorkPlace
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import com.blackatsystems.miguardia.core.domain.work.WorkType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class V2ShiftPlanningTest {
    @Test
    fun buildCopiesExistingShiftSnapshotsAndV2WorkIdentity() {
        val write = build()

        assertEquals(OBJECTIVE_ID, write.shift.sourceObjectiveId)
        assertNull(write.shift.sourceScheduleCombinationId)
        assertEquals("Hospital Norte", write.shift.objectiveNameSnapshot)
        assertEquals("HN", write.shift.objectiveAbbreviationSnapshot)
        assertEquals(PLACE_ID, write.snapshot.workPlaceId)
        assertEquals(TYPE_ID, write.snapshot.workTypeId)
        assertEquals(TEMPLATE_ID, write.snapshot.templateId)
        assertEquals(REVISION_ID, write.snapshot.configurationRevisionId)
        assertEquals("Consultorio", write.snapshot.workTypeNameSnapshot)
        assertEquals(type().behavior, write.snapshot.workTypeBehaviorSnapshot)
    }

    @Test
    fun equalTemplateTimesBuildAnExactTwentyFourHourShift() {
        val template = template(start = LocalTime.of(8, 0), end = LocalTime.of(8, 0))

        val write = build(template = template)

        assertEquals(Duration.ofHours(24), Duration.between(write.shift.startAt, write.shift.endAt))
        assertEquals(DATE, write.shift.localStartDate)
    }

    @Test
    fun editingPreservesShiftIdentityCreationAndStatusButRefreshesWorkSnapshot() {
        val original = build().copy(shift = build().shift.copy(status = ShiftStatus.ABSENT))
        val newType = type(id = OTHER_TYPE_ID, name = "Guardia")
        val newTemplate = template(id = OTHER_TEMPLATE_ID, typeId = OTHER_TYPE_ID)

        val edited = editV2ShiftWrite(
            original = original,
            date = DATE.plusDays(1),
            objective = objective(),
            workPlace = place(),
            workType = newType,
            template = newTemplate,
            configurationContext = configurationContext(DATE.plusDays(1)),
            position = " Consultorio 2 ",
            updatedAt = NOW.plusSeconds(60),
        )

        assertEquals(original.shift.id, edited.shift.id)
        assertEquals(original.shift.createdAt, edited.shift.createdAt)
        assertEquals(ShiftStatus.ABSENT, edited.shift.status)
        assertEquals(OTHER_TYPE_ID, edited.snapshot.workTypeId)
        assertEquals("Guardia", edited.snapshot.workTypeNameSnapshot)
        assertEquals("Consultorio 2", edited.shift.position)
    }

    @Test
    fun inactiveCatalogItemCannotCreateANewV2Shift() {
        assertThrows(InvalidLocalDataException::class.java) {
            build(workPlace = place().copy(isActive = false, updatedAt = NOW.plusSeconds(1)))
        }
    }

    @Test
    fun sameDayOverlappingJobsRemainTwoRecordsAndBothDurationsCount() {
        val first = build(
            id = SHIFT_ID,
            template = template(start = LocalTime.of(8, 0), end = LocalTime.of(16, 0)),
        )
        val secondType = type(OTHER_TYPE_ID, "Guardia")
        val second = build(
            id = OTHER_SHIFT_ID,
            workType = secondType,
            template = template(
                id = OTHER_TEMPLATE_ID,
                typeId = OTHER_TYPE_ID,
                start = LocalTime.of(12, 0),
                end = LocalTime.of(20, 0),
            ),
        )

        val plan = planV2ShiftBatch(
            selectedDates = setOf(DATE),
            existingShifts = emptyList(),
            candidates = listOf(first, second),
            policy = OccupiedDatePolicy.ADD_SECOND_SHIFT,
        )

        assertEquals(2, plan.mutation.shiftsToInsert.size)
        assertEquals(
            Duration.ofHours(16),
            plan.mutation.shiftsToInsert
                .map { Duration.between(it.shift.startAt, it.shift.endAt) }
                .reduce(Duration::plus),
        )
        assertTrue(plan.warnings.any { it is ShiftPlanningWarning.Overlap })
    }

    @Test
    fun aWriteCannotPairAShiftWithAnotherSnapshot() {
        val valid = build()
        assertThrows(IllegalArgumentException::class.java) {
            V2ShiftWrite(
                shift = valid.shift,
                snapshot = valid.snapshot.copy(shiftId = OTHER_SHIFT_ID),
            )
        }
    }

    @Test
    fun batchRejectsDuplicateIdsAndMixedSectorsBeforeRepositoryWrite() {
        val first = build()
        assertThrows(IllegalArgumentException::class.java) {
            V2ShiftBatchMutation(shiftsToInsert = listOf(first, first))
        }

        val police = V2ShiftWrite(
            shift = first.shift.copy(id = OTHER_SHIFT_ID),
            snapshot = first.snapshot.copy(
                shiftId = OTHER_SHIFT_ID,
                sector = WorkSector.POLICE,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            V2ShiftBatchMutation(shiftsToInsert = listOf(first, police))
        }

        val anotherTimeline = V2ShiftWrite(
            shift = first.shift.copy(id = OTHER_SHIFT_ID),
            snapshot = first.snapshot.copy(
                shiftId = OTHER_SHIFT_ID,
                timelineId = OTHER_TIMELINE_ID,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            V2ShiftBatchMutation(shiftsToInsert = listOf(first, anotherTimeline))
        }
    }

    @Test
    fun occupancyExpectationCapturesTheExactReviewedShiftVersion() {
        val shift = build().shift
        val reviewed = ShiftOccupancyExpectation.capture(
            startDateInclusive = DATE.minusDays(2),
            endDateInclusive = DATE.plusDays(2),
            shifts = listOf(shift),
        )
        val changed = ShiftOccupancyExpectation.capture(
            startDateInclusive = DATE.minusDays(2),
            endDateInclusive = DATE.plusDays(2),
            shifts = listOf(shift.copy(updatedAt = shift.updatedAt.plusSeconds(1))),
        )

        assertEquals(setOf(shift.id), reviewed.observedShifts.map { it.shiftId }.toSet())
        assertNotEquals(reviewed, changed)
    }

    @Test
    fun occupancyExpectationRejectsRowsOutsideItsWindowOrRepeatedIds() {
        val shift = build().shift
        assertThrows(IllegalArgumentException::class.java) {
            ShiftOccupancyExpectation.capture(
                startDateInclusive = DATE.plusDays(1),
                endDateInclusive = DATE.plusDays(2),
                shifts = listOf(shift),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShiftOccupancyExpectation.capture(
                startDateInclusive = DATE.minusDays(2),
                endDateInclusive = DATE.plusDays(2),
                shifts = listOf(shift, shift),
            )
        }
    }

    @Test
    fun editingBatchProducesAnUpdateAndNeverAnInsert() {
        val original = build()
        val edited = editV2ShiftWrite(
            original = original,
            date = DATE.plusDays(1),
            objective = objective(),
            workPlace = place(),
            workType = type(),
            template = template(),
            configurationContext = configurationContext(DATE.plusDays(1)),
            position = null,
            updatedAt = NOW.plusSeconds(1),
        )

        val plan = planV2ShiftBatch(
            selectedDates = setOf(DATE.plusDays(1)),
            existingShifts = listOf(original.shift),
            candidates = listOf(edited),
            policy = OccupiedDatePolicy.ADD_SECOND_SHIFT,
            editingShiftId = original.shift.id,
        )

        assertTrue(plan.mutation.shiftsToInsert.isEmpty())
        assertEquals(listOf(edited), plan.mutation.shiftsToUpdate)
    }

    @Test
    fun exactConfigurationRevisionIsResolvedForDateAndForeignTimelineIsRejected() {
        val newerRevision = revision(
            id = OTHER_REVISION_ID,
            effectiveFrom = DATE.plusDays(1),
        )
        val targetDate = DATE.plusDays(2)
        val exactContext = configurationContext(
            date = targetDate,
            revisions = listOf(revision(), newerRevision),
        )
        val write = buildV2ShiftWrite(
            id = SHIFT_ID,
            date = targetDate,
            objective = objective(),
            workPlace = place(),
            workType = type(),
            template = template(),
            configurationContext = exactContext,
            position = null,
            timestamp = NOW,
            zoneId = ZONE,
        )

        assertEquals(OTHER_REVISION_ID, write.snapshot.configurationRevisionId)

        val contextResolvedForAnotherDate = configurationContext(
            date = DATE,
            revisions = listOf(revision(), newerRevision),
        )
        assertThrows(InvalidLocalDataException::class.java) {
            buildV2ShiftWrite(
                id = OTHER_SHIFT_ID,
                date = targetDate,
                objective = objective(),
                workPlace = place(),
                workType = type(),
                template = template(),
                configurationContext = contextResolvedForAnotherDate,
                position = null,
                timestamp = NOW,
                zoneId = ZONE,
            )
        }

        val foreignContext = configurationContext(
            date = targetDate,
            timelineId = OTHER_TIMELINE_ID,
            revisions = listOf(revision()),
        )
        assertThrows(InvalidLocalDataException::class.java) {
            buildV2ShiftWrite(
                id = OTHER_SHIFT_ID,
                date = targetDate,
                objective = objective(),
                workPlace = place(),
                workType = type(),
                template = template(),
                configurationContext = foreignContext,
                position = null,
                timestamp = NOW,
                zoneId = ZONE,
            )
        }
    }

    private fun build(
        id: UUID = SHIFT_ID,
        workPlace: WorkPlace = place(),
        workType: WorkType = type(),
        template: WorkTemplate = template(),
    ) = buildV2ShiftWrite(
        id = id,
        date = DATE,
        objective = objective(),
        workPlace = workPlace,
        workType = workType,
        template = template,
        configurationContext = configurationContext(DATE),
        position = null,
        timestamp = NOW,
        zoneId = ZONE,
    )

    private fun objective() = Objective(
        id = OBJECTIVE_ID,
        fullName = "Hospital Norte",
        abbreviation = "HN",
        address = "Calle 1",
        note = null,
        isActive = false,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun place() = WorkPlace(
        PLACE_ID,
        TIMELINE_ID,
        WorkSector.MEDICINE,
        OBJECTIVE_ID,
        true,
        NOW,
        NOW,
    )

    private fun type(
        id: UUID = TYPE_ID,
        name: String = "Consultorio",
    ) = WorkType.create(id, TIMELINE_ID, WorkSector.MEDICINE, name, NOW)

    private fun template(
        id: UUID = TEMPLATE_ID,
        typeId: UUID = TYPE_ID,
        start: LocalTime = LocalTime.of(8, 0),
        end: LocalTime = LocalTime.of(16, 0),
    ) = WorkTemplate(
        id,
        TIMELINE_ID,
        WorkSector.MEDICINE,
        PLACE_ID,
        OBJECTIVE_ID,
        typeId,
        start,
        end,
        0xFF336699.toInt(),
        true,
        null,
        NOW,
        NOW,
    )

    private fun revision(
        id: UUID = REVISION_ID,
        effectiveFrom: LocalDate = DATE,
    ) = EffectiveRevision(
        id = id,
        effectiveFrom = effectiveFrom,
        value = WorkConfiguration(
            WorkSector.MEDICINE,
            HoursReference.PendingSetup,
            availabilityLabel = null,
        ),
    )

    private fun configurationContext(
        date: LocalDate,
        timelineId: UUID = TIMELINE_ID,
        revisions: List<EffectiveRevision<WorkConfiguration>> = listOf(revision()),
    ): ResolvedWorkConfigurationRevision = ResolvedWorkConfigurationRevision.resolve(
        history = WorkConfigurationHistory(
            origin = WorkConfigurationOrigin.NEW_V2,
            timeline = EffectiveDateTimeline(timelineId, revisions),
            perPeriodHoursValues = PerPeriodHoursValues(emptyList()),
        ),
        date = date,
    )

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 31)
        val NOW: Instant = Instant.parse("2026-08-31T10:00:00Z")
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val TIMELINE_ID: UUID = UUID.fromString("84000000-0000-0000-0000-000000000001")
        val OTHER_TIMELINE_ID: UUID = UUID.fromString("84000000-0000-0000-0000-000000000011")
        val OBJECTIVE_ID: UUID = UUID.fromString("84000000-0000-0000-0000-000000000002")
        val PLACE_ID: UUID = UUID.fromString("84000000-0000-0000-0000-000000000003")
        val TYPE_ID: UUID = UUID.fromString("84000000-0000-0000-0000-000000000004")
        val OTHER_TYPE_ID: UUID = UUID.fromString("84000000-0000-0000-0000-000000000005")
        val TEMPLATE_ID: UUID = UUID.fromString("84000000-0000-0000-0000-000000000006")
        val OTHER_TEMPLATE_ID: UUID = UUID.fromString("84000000-0000-0000-0000-000000000007")
        val REVISION_ID: UUID = UUID.fromString("84000000-0000-0000-0000-000000000008")
        val OTHER_REVISION_ID: UUID = UUID.fromString("84000000-0000-0000-0000-000000000012")
        val SHIFT_ID: UUID = UUID.fromString("84000000-0000-0000-0000-000000000009")
        val OTHER_SHIFT_ID: UUID = UUID.fromString("84000000-0000-0000-0000-000000000010")
    }
}
