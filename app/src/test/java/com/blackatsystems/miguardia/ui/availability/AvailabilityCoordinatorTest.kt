package com.blackatsystems.miguardia.ui.availability

import com.blackatsystems.miguardia.core.domain.model.AvailabilityBreakdown
import com.blackatsystems.miguardia.core.domain.model.AvailabilityTemporalState
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.sumAvailabilityBreakdowns
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvailabilityCoordinatorTest {
    @Test
    fun creationActionFollowsExactRevisionAndDisablingKeepsHistoricalRowsVisible() {
        val state = state()
        assertTrue(state.canCreateOn(LocalDate.of(2026, 8, 26)))
        assertFalse(state.canCreateOn(LocalDate.of(2026, 9, 2)))
        assertEquals(1, state.windowsOn(LocalDate.of(2026, 8, 26)).size)
    }

    @Test
    fun dayRowsRemainSortedByExactStartAndOwnerDateDoesNotFollowEndDate() {
        val later = record(UUID.randomUUID(), "2026-08-26T22:00:00Z", "2026-08-28T02:00:00Z")
        val earlier = record(UUID.randomUUID(), "2026-08-26T08:00:00Z", "2026-08-26T12:00:00Z")
        val source = source(listOf(later, earlier))
        assertEquals(listOf(earlier, later), source.windowsOn(OWNER))
        assertEquals(OWNER, later.ownerLocalDate)
    }

    @Test
    fun everyEditorAndReviewStageBlocksCalendarWhileNoneDoesNot() {
        AvailabilitySurface.entries.filter { it != AvailabilitySurface.NONE }.forEach { surface ->
            assertTrue(AvailabilityUiState(surface = surface).isBlocking)
        }
        assertFalse(AvailabilityUiState(surface = AvailabilitySurface.NONE).isBlocking)
    }

    @Test
    fun draftKeepsCalendarOwnerFixedWhileAllowingMultidayEnd() {
        val draft = AvailabilityWindowDraftState(
            ownerDate = OWNER,
            startTime = "22:00",
            endDate = "2026-08-28",
            endTime = "02:00",
        )
        assertEquals(OWNER, draft.ownerDate)
        assertEquals("2026-08-28", draft.endDate)
    }

    private fun state(): AvailabilityUiState = AvailabilityUiState(
        loadState = AvailabilityLoadState.CONTENT,
        source = source(listOf(record(UUID.randomUUID(), "2026-08-26T08:00:00Z", "2026-08-26T12:00:00Z"))),
    )

    private fun source(windows: List<AvailabilityWindowRecord>): AvailabilitySource {
        val first = EffectiveRevision(
            UUID.randomUUID(),
            LocalDate.of(2026, 1, 1),
            WorkConfiguration(WorkSector.PRIVATE_SECURITY, HoursReference.PendingSetup, AvailabilityLabel.PASSIVE_GUARD),
        )
        val disabled = EffectiveRevision(
            UUID.randomUUID(),
            LocalDate.of(2026, 9, 1),
            first.value.copy(availabilityLabel = null),
        )
        val breakdowns = windows.associate { record ->
            record.id to AvailabilityBreakdown(
                AvailabilityTemporalState.COMPLETED,
                record.durationMinutes,
                record.durationMinutes,
                0,
                0,
                record.durationMinutes,
                0,
            )
        }
        return AvailabilitySource(
            history = WorkConfigurationHistory(
                EffectiveDateTimeline(TIMELINE, listOf(first, disabled)),
                PerPeriodHoursValues(emptyList()),
            ),
            windows = windows,
            breakdowns = breakdowns,
            totals = sumAvailabilityBreakdowns(breakdowns.values),
            protectedWindowIds = emptySet(),
            activeWork = emptyList(),
            protectedRanges = emptyList(),
            today = LocalDate.of(2026, 8, 27),
        )
    }

    private fun record(id: UUID, start: String, end: String) = AvailabilityWindowRecord(
        id = id,
        timelineId = TIMELINE,
        sector = WorkSector.PRIVATE_SECURITY,
        configurationRevisionId = UUID.randomUUID(),
        ownerLocalDate = OWNER,
        zoneId = ZoneOffset.UTC,
        start = Instant.parse(start),
        end = Instant.parse(end),
        labelSnapshot = "Guardia pasiva",
        createdAt = Instant.parse("2026-08-27T20:00:00Z"),
        updatedAt = Instant.parse("2026-08-27T20:00:00Z"),
    )

    private companion object {
        val OWNER: LocalDate = LocalDate.of(2026, 8, 26)
        val TIMELINE: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}
