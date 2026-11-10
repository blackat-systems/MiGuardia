package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.domain.model.AvailabilityActiveWorkInterval
import com.blackatsystems.miguardia.core.domain.model.AvailabilityActiveWorkKind
import com.blackatsystems.miguardia.core.domain.model.AvailabilityMinuteSegment
import com.blackatsystems.miguardia.core.domain.model.AvailabilityTemporalState
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowDraft
import com.blackatsystems.miguardia.core.domain.model.buildAvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.calculateAvailabilityIntervalBreakdown
import com.blackatsystems.miguardia.core.domain.model.resolveAvailabilityActiveWorkIntervals
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventInput
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.shift.buildV2ShiftWrite
import com.blackatsystems.miguardia.core.domain.work.WorkType
import com.blackatsystems.miguardia.ui.EventTimeRangeStyle
import com.blackatsystems.miguardia.ui.formatEventTimeRange
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage12TemporalLiteralTest {
    private val oracle = Stage12TemporalLiteralOracle
    private val recipe = Stage12TemporalRecipe(UUID.fromString("b119c026-0000-4000-8000-000000000001"),
        Instant.parse("2026-10-22T10:00:00.123Z"))

    @Test
    fun c026ProductionBuilderPreservesAllTwentySixFieldsAndFourLiteralFormats() {
        val cases = oracle.literals.filter { it.key.startsWith("C026-") }
        assertEquals(listOf("C026-mismo-dia-minutos", "C026-nocturna", "C026-24h", "C026-mes-ano", "C026-historicos"), cases.map { it.key })
        cases.forEach { literal ->
            oracle.requireConsistent(literal)
            val actual = recipe.buildShift(literal)
            assertEquals(literal.key, recipe.expectedShift(literal), actual)
            oracle.requireEndpoints(literal, actual.shift.startAt, actual.shift.endAt, actual.shift.zoneId, actual.shift.localStartDate)
            assertFormats(literal, actual.shift.startAt, actual.shift.endAt)
        }
    }

    @Test
    fun a119ProductionBuilderPreservesAllElevenFieldsIncludingTheSeparateOneHourComparator() {
        val cases = oracle.literals.filter { it.key.startsWith("A119-") }
        assertEquals(listOf("A119-25h", "A119-varios-dias", "A119-fin-mes", "A119-fin-ano", "A119-segmento-reanudado-otra-fecha"), cases.map { it.key })
        (cases + oracle.oneHourComparator).forEach { literal ->
            oracle.requireConsistent(literal)
            val actual = recipe.buildWindow(literal)
            assertEquals(literal.key, recipe.expectedWindow(literal), actual)
            assertEquals(literal.key, literal.minutes, actual.durationMinutes)
            oracle.requireEndpoints(literal, actual.start, actual.end, actual.zoneId, actual.ownerLocalDate)
            assertFormats(literal, actual.start, actual.end)
        }
        val long = recipe.buildWindow(oracle.literal("A119-25h"))
        val short = recipe.buildWindow(oracle.oneHourComparator)
        assertEquals(1500L, long.durationMinutes)
        assertEquals(60L, short.durationMinutes)
        assertNotEquals(long.end, short.end)
    }

    @Test
    fun storedShiftProjectionUsesHistoricalFieldsWhenCurrentCatalogInputsDiffer() {
        val literal = oracle.literal("C026-historicos")
        val stored = recipe.buildShift(literal)
        val changedObjective = recipe.objective.copy(fullName = "Institución modificada", abbreviation = "MOD",
            address = "Dirección modificada 999", updatedAt = recipe.timestamp.plusSeconds(60))
        val changedType = WorkType.create(recipe.typeId, recipe.timelineId, recipe.type.sector,
            "Tipo modificado", recipe.timestamp.plusSeconds(60)).copy(createdAt = recipe.type.createdAt)
        val changedTemplate = recipe.template(LocalTime.of(6, 0), LocalTime.of(10, 0)).copy(colorArgb = 0xFFAA2200.toInt(),
            updatedAt = recipe.timestamp.plusSeconds(60))
        val rebuiltForContrast = buildV2ShiftWrite(stored.shift.id, literal.ownerDate, changedObjective, recipe.place,
            changedType, changedTemplate, recipe.configuration(literal.ownerDate), "Puesto modificado", recipe.timestamp.plusSeconds(60), oracle.zone)
        assertNotEquals(stored, rebuiltForContrast)
        val projected = projectNextEvent(literal.start, oracle.zone, input(shifts = listOf(stored)))
        val item = projected.primaryEvents.single() as NextEventItem.Shift
        assertEquals(NextEventPrimary.ONGOING_SHIFT, projected.primaryEvent)
        assertEquals(literal.start, item.start)
        assertEquals(literal.end, item.end)
        assertEquals(literal.ownerDate, item.ownerLocalDate)
        assertEquals(oracle.zone, item.zoneId)
        assertEquals(recipe.type.sector, item.sector)
        assertEquals(recipe.type.behavior, item.workTypeBehaviorSnapshot)
        assertEquals(stored.shift.id, item.shiftId)
        assertEquals("Hospital Temporal", item.placeNameSnapshot)
        assertEquals("HTP", item.placeAbbreviationSnapshot)
        assertTrue(item.hasHistoricalAddress)
        assertEquals("Enfermería temporal", item.workTypeNameSnapshot)
        assertEquals(0xFF336699.toInt(), item.colorArgbSnapshot)
        assertEquals("Puesto temporal 7", item.positionSnapshot)
        assertEquals(LocalTime.of(8, 15), item.startTimeSnapshot)
        assertEquals(LocalTime.of(16, 45), item.endTimeSnapshot)
        assertEquals(recipe.expectedShift(literal), stored)
        // Component evidence only. Real catalogue mutation, Room and UI reopening are separate obligations.
    }

    @Test
    fun resumptionPartitionHasLiteralEndpointsKeysAndCompleteTotals() {
        val window = recipe.buildWindow(oracle.literal("A119-segmento-reanudado-otra-fecha"))
        val source = AvailabilityActiveWorkInterval("temporal-active-work", AvailabilityActiveWorkKind.SHIFT_PLANNED,
            Instant.parse("2026-10-23T23:00:00Z"), Instant.parse("2026-10-24T11:00:00Z"))
        val actual = calculateAvailabilityIntervalBreakdown(window, listOf(source), false,
            Clock.fixed(Instant.parse("2026-10-24T15:00:00Z"), oracle.zone))
        val partition = (actual.effectiveElapsed + actual.replacedElapsed).sortedBy { it.start }
        assertEquals(3, partition.size)
        partition.zip(oracle.resumptionPartition).forEach { (segment, expected) ->
            assertEquals(Instant.parse(expected.start), segment.start)
            assertEquals(Instant.parse(expected.end), segment.end)
            assertEquals(expected.minutes, segment.durationMinutes)
            assertEquals(if (expected.active) listOf(source.key) else emptyList<String>(), segment.activeWorkKeys)
        }
        assertEquals(AvailabilityTemporalState.COMPLETED, actual.totals.state)
        assertEquals(1680L, actual.totals.programmedMinutes)
        assertEquals(960L, actual.totals.effectiveElapsedMinutes)
        assertEquals(720L, actual.totals.replacedElapsedMinutes)
        assertEquals(0L, actual.totals.futurePendingMinutes)
        assertEquals(960L, actual.totals.effectiveProjectedAtEndMinutes)
        assertEquals(0L, actual.totals.futureOccupiedByPlannedWorkMinutes)
        assertEquals(listOf(AvailabilityMinuteSegment(window.start, window.end, emptyList())), actual.programmed)
        assertEquals(actual.effectiveElapsed, actual.effectiveProjectedAtEnd)
        assertTrue(actual.futurePending.isEmpty() && actual.futureOccupiedByPlannedWork.isEmpty())
    }

    @Test
    fun nextEventUsesHalfOpenBoundariesAndKeepsResumedWindowIdentityAndOriginalOwner() {
        val literal = oracle.literal("A119-segmento-reanudado-otra-fecha")
        val window = recipe.buildWindow(literal)
        val activeLiteral = oracle.resumedActiveWork
        oracle.requireConsistent(activeLiteral)
        val work = recipe.buildShift(activeLiteral)
        val inputs = input(shifts = listOf(work), windows = listOf(window))
        val resolved = resolveAvailabilityActiveWorkIntervals(listOf(work), emptyMap(), emptyList(), emptyList())
        assertEquals(listOf(AvailabilityActiveWorkInterval("shift:${work.shift.id}", AvailabilityActiveWorkKind.SHIFT_PLANNED,
            Instant.parse("2026-10-23T23:00:00Z"), Instant.parse("2026-10-24T11:00:00Z"))), resolved)
        val before = projectNextEvent(Instant.parse("2026-10-23T10:59:00Z"), oracle.zone, inputs)
        val effective = before.events.filterIsInstance<NextEventItem.Availability>()
        assertEquals(listOf(
            NextEventItem.Availability(window.id, Instant.parse("2026-10-23T11:00:00Z"), Instant.parse("2026-10-23T23:00:00Z"),
                oracle.zone, literal.ownerDate, "Guardia pasiva", false),
            NextEventItem.Availability(window.id, Instant.parse("2026-10-24T11:00:00Z"), Instant.parse("2026-10-24T15:00:00Z"),
                oracle.zone, literal.ownerDate, "Guardia pasiva", true),
        ), effective)
        assertTrue(before.activeEvents.isEmpty())
        assertEquals(NextEventPrimary.ONGOING_AVAILABILITY, projectNextEvent(window.start, oracle.zone, inputs).primaryEvent)
        val occupied = projectNextEvent(Instant.parse("2026-10-23T23:00:00Z"), oracle.zone, inputs)
        assertEquals(NextEventPrimary.ONGOING_SHIFT, occupied.primaryEvent)
        assertEquals(listOf(NextEventIdentity.Shift(work.shift.id)), occupied.activeEvents.map { it.identity })
        val resumed = projectNextEvent(Instant.parse("2026-10-24T11:00:00Z"), oracle.zone, inputs)
        assertEquals(NextEventPrimary.ONGOING_AVAILABILITY, resumed.primaryEvent)
        assertEquals(Duration.ofMinutes(240), resumed.remaining)
        val item = resumed.primaryEvents.single() as NextEventItem.Availability
        assertEquals(window.id, item.windowId)
        assertEquals(window.ownerLocalDate, item.ownerLocalDate)
        assertEquals("2026-10-23", item.ownerLocalDate.toString())
        assertEquals(oracle.zone, item.zoneId)
        assertEquals("Guardia pasiva", item.labelSnapshot)
        assertEquals(Instant.parse("2026-10-24T11:00:00Z"), item.start)
        assertEquals(Instant.parse("2026-10-24T15:00:00Z"), item.end)
        assertTrue(item.isResumption)
        assertEquals(NextEventIdentity.Availability(window.id, item.start, item.end), item.identity)
        assertEquals(item.identity, NextEventIdentity.parseTrackingKey(item.identity.trackingKey))
        assertEquals(oracle.resumptionDisplay, formatEventTimeRange(item.start, item.end, item.zoneId, EventTimeRangeStyle.COMPACT_DATED))
        assertEquals(oracle.resumptionDetail, formatEventTimeRange(item.start, item.end, item.zoneId, EventTimeRangeStyle.DETAIL))
        val finished = projectNextEvent(window.end, oracle.zone, inputs)
        assertEquals(NextEventPrimary.NONE, finished.primaryEvent)
        assertTrue(finished.activeEvents.isEmpty() && finished.upcomingEvents.isEmpty())
    }

    @Test
    fun invalidEndpointsAndAdversarialReadbacksCannotSatisfyTheLiteralOracle() {
        val literal = oracle.literal("A119-25h")
        val draft = AvailabilityWindowDraft(recipe.id(literal.key), literal.ownerDate, oracle.zone, literal.start, literal.end)
        listOf(draft.copy(end = draft.start), draft.copy(end = draft.start.minusSeconds(60)),
            draft.copy(start = draft.start.plusSeconds(1)), draft.copy(end = draft.end.plusSeconds(1)),
            draft.copy(ownerLocalDate = draft.ownerLocalDate.plusDays(1))).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                buildAvailabilityWindowRecord(invalid, recipe.configuration(literal.ownerDate), recipe.timestamp)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            oracle.requireEndpoints(literal, literal.start, oracle.oneHourComparator.end, oracle.zone, literal.ownerDate)
        }
        assertThrows(IllegalStateException::class.java) {
            oracle.requireEndpoints(literal, literal.start, literal.end, ZoneId.of("UTC"), literal.ownerDate)
        }
        val overnight = oracle.literal("C026-nocturna")
        assertNotEquals(overnight.civilDays, overnight.minutes / 1440L)
    }

    private fun assertFormats(literal: Stage12TemporalLiteralOracle.Literal, start: Instant, end: Instant) {
        listOf(EventTimeRangeStyle.DETAIL to literal.detail, EventTimeRangeStyle.COMPACT to literal.compact,
            EventTimeRangeStyle.COMPACT_DATED to literal.compactDated, EventTimeRangeStyle.CALENDAR to literal.calendar)
            .forEach { (style, expected) -> assertEquals("${literal.key}: $style", expected, formatEventTimeRange(start, end, oracle.zone, style)) }
    }

    private fun input(shifts: List<com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite> = emptyList(),
        windows: List<com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord> = emptyList()) =
        NextEventInput(shifts, windows, emptyMap(), emptyList(), emptyList(), emptyList(), emptyList())
}
