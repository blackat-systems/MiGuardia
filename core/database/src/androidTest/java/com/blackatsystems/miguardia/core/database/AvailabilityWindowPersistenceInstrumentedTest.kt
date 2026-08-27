package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowDraft
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowMutation
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowWriteResult
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.buildAvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationAvailabilityMutation
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationAvailabilityWriteResult
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AvailabilityWindowPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: MiGuardiaV2Database
    private lateinit var store: LocalDataStore
    private lateinit var fixture: SeededV2Catalog

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB)
        openStore()
        fixture = store.seedV2Catalog()
        changeLabel(AvailabilityLabel.PASSIVE_GUARD)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(DB)
    }

    @Test
    fun createCorrectDeleteAndReopenPreserveExactHistoricalLabel() = runBlocking {
        val initialExpectation = capture(null, START, END)
        val initial = build(ID, START, END, null, NOW)
        assertEquals(
            AvailabilityWindowWriteResult.Saved(initial),
            store.availabilityWindows.applyMutation(AvailabilityWindowMutation(initialExpectation, initial)),
        )
        assertEquals(
            listOf(initial),
            store.availabilityWindows.observeOn(
                V2TestIds.TIMELINE,
                WorkSector.PRIVATE_SECURITY,
                OWNER,
            ).first(),
        )

        store.close()
        openStore()
        assertEquals(initial, store.availabilityWindows.get(ID))

        changeLabel(AvailabilityLabel.ON_CALL_RETAINER)
        assertEquals("Guardia pasiva", requireNotNull(store.availabilityWindows.get(ID)).labelSnapshot)
        val correctionExpectation = capture(ID, START, END.plusSeconds(3_600))
        val corrected = build(ID, START.plusSeconds(1_800), END.plusSeconds(3_600), initial, NOW.plusMillis(2))
        assertEquals(
            AvailabilityWindowWriteResult.Saved(corrected),
            store.availabilityWindows.applyMutation(
                AvailabilityWindowMutation(correctionExpectation, corrected),
            ),
        )
        assertEquals("Guardia pasiva", corrected.labelSnapshot)
        assertEquals(initial.configurationRevisionId, corrected.configurationRevisionId)

        val deleteExpectation = capture(ID, corrected.start, corrected.end)
        assertEquals(
            AvailabilityWindowWriteResult.Deleted,
            store.availabilityWindows.applyMutation(AvailabilityWindowMutation(deleteExpectation, null)),
        )
        assertNull(store.availabilityWindows.get(ID))
    }

    @Test
    fun overlapIsRejectedContiguousWindowIsAcceptedAndDoubleTouchConflicts() = runBlocking {
        val firstExpectation = capture(null, START, END)
        val first = build(ID, START, END, null, NOW)
        val firstMutation = AvailabilityWindowMutation(firstExpectation, first)
        assertTrue(store.availabilityWindows.applyMutation(firstMutation) is AvailabilityWindowWriteResult.Saved)
        assertEquals(AvailabilityWindowWriteResult.Conflict, store.availabilityWindows.applyMutation(firstMutation))

        val overlappingId = V2TestIds.uuid(702)
        val overlappingStart = END.minusSeconds(1_800)
        val overlappingEnd = END.plusSeconds(1_800)
        val overlapExpectation = capture(null, overlappingStart, overlappingEnd)
        val overlapping = build(overlappingId, overlappingStart, overlappingEnd, null, NOW.plusMillis(2))
        assertThrows(IllegalArgumentException::class.java) {
            AvailabilityWindowMutation(overlapExpectation, overlapping)
        }
        assertNull(store.availabilityWindows.get(overlappingId))

        val contiguousId = V2TestIds.uuid(703)
        val contiguousExpectation = capture(null, END, END.plusSeconds(7_200))
        val contiguous = build(contiguousId, END, END.plusSeconds(7_200), null, NOW.plusMillis(3))
        assertEquals(
            AvailabilityWindowWriteResult.Saved(contiguous),
            store.availabilityWindows.applyMutation(AvailabilityWindowMutation(contiguousExpectation, contiguous)),
        )
        assertEquals(2, store.availabilityWindows.observeAll(
            V2TestIds.TIMELINE,
            WorkSector.PRIVATE_SECURITY,
        ).first().size)
    }

    @Test
    fun changingProtectionAfterReviewInvalidatesCompleteCasAndRollsBack() = runBlocking {
        val expectation = capture(null, START, END)
        store.medicalLeaves.create(
            MedicalLeave(
                id = V2TestIds.uuid(704),
                startDate = OWNER,
                endDateInclusive = OWNER,
                privateNote = null,
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        val record = build(ID, START, END, null, NOW.plusMillis(2))
        assertEquals(
            AvailabilityWindowWriteResult.Conflict,
            store.availabilityWindows.applyMutation(AvailabilityWindowMutation(expectation, record)),
        )
        assertNull(store.availabilityWindows.get(ID))
    }

    @Test
    fun changingConfigurationAfterReviewReturnsConflictInsteadOfAStorageError() = runBlocking {
        val expectation = capture(null, START, END)
        val record = build(ID, START, END, null, NOW)
        changeLabel(AvailabilityLabel.ON_CALL_RETAINER)

        assertEquals(
            AvailabilityWindowWriteResult.Conflict,
            store.availabilityWindows.applyMutation(AvailabilityWindowMutation(expectation, record)),
        )
        assertNull(store.availabilityWindows.get(ID))
    }

    @Test
    fun labelMutationPreservesHoursFieldsAndCanDisableFutureLoads() = runBlocking {
        val before = requireNotNull(store.workConfiguration.get()).timeline.valueAt(OWNER)!!
        changeLabel(null)
        val after = requireNotNull(store.workConfiguration.get()).timeline.valueAt(OWNER)!!
        assertEquals(before.hoursReference, after.hoursReference)
        assertEquals(before.hoursReferenceStartedOn, after.hoursReferenceStartedOn)
        assertEquals(null, after.availabilityLabel)
    }

    @Test
    fun staleCorrectionDeletionAndWrongOwnerContextAreRejectedConsciously(): Unit = runBlocking {
        val initial = build(ID, START, END, null, NOW)
        assertTrue(
            store.availabilityWindows.applyMutation(
                AvailabilityWindowMutation(capture(null, START, END), initial),
            ) is AvailabilityWindowWriteResult.Saved,
        )
        val staleDeleteExpectation = capture(ID, START, END)
        val movedStart = END.plusSeconds(3_600)
        val movedEnd = movedStart.plusSeconds(3_600)
        val correctionExpectation = capture(ID, START, movedEnd)
        val moved = build(ID, movedStart, movedEnd, initial, NOW.plusMillis(2))
        assertEquals(
            AvailabilityWindowWriteResult.Saved(moved),
            store.availabilityWindows.applyMutation(
                AvailabilityWindowMutation(correctionExpectation, moved),
            ),
        )
        assertEquals(
            AvailabilityWindowWriteResult.Conflict,
            store.availabilityWindows.applyMutation(
                AvailabilityWindowMutation(staleDeleteExpectation, null),
            ),
        )

        val deleteExpectation = capture(ID, moved.start, moved.end)
        val deleteMutation = AvailabilityWindowMutation(deleteExpectation, null)
        assertEquals(AvailabilityWindowWriteResult.Deleted, store.availabilityWindows.applyMutation(deleteMutation))
        assertEquals(AvailabilityWindowWriteResult.Conflict, store.availabilityWindows.applyMutation(deleteMutation))

        val second = build(V2TestIds.uuid(705), START, END, null, NOW.plusMillis(3))
        assertTrue(
            store.availabilityWindows.applyMutation(
                AvailabilityWindowMutation(capture(null, START, END), second),
            ) is AvailabilityWindowWriteResult.Saved,
        )
        val history = requireNotNull(store.workConfiguration.get())
        val wrongDateConfiguration = ResolvedWorkConfigurationRevision.resolve(history, OWNER.plusDays(1))
        val wrongContext = store.availabilityWindows.captureExpectation(
            second.id,
            wrongDateConfiguration,
            second.start,
            second.end,
        )
        assertThrows(IllegalArgumentException::class.java) {
            AvailabilityWindowMutation(wrongContext, null)
        }
    }

    @Test
    fun multidayReviewTracksProtectionThatChangesForAnOverlappingShiftOwnerDate() = runBlocking {
        val shiftDate = OWNER.plusDays(1)
        val shift = store.buildTestV2Write(fixture, V2TestIds.uuid(706), shiftDate)
        store.v2Shifts.insert(shift)
        val end = shift.shift.endAt.plusSeconds(3_600)
        val expectation = capture(null, START, end)

        store.medicalLeaves.create(
            MedicalLeave(
                id = V2TestIds.uuid(707),
                startDate = shiftDate,
                endDateInclusive = shiftDate,
                privateNote = null,
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        val record = build(V2TestIds.uuid(708), START, end, null, NOW.plusMillis(4))
        assertEquals(
            AvailabilityWindowWriteResult.Conflict,
            store.availabilityWindows.applyMutation(AvailabilityWindowMutation(expectation, record)),
        )
        assertNull(store.availabilityWindows.get(record.id))
    }

    private suspend fun changeLabel(label: AvailabilityLabel?) {
        val history = requireNotNull(store.workConfiguration.get())
        val sameDate = history.timeline.revisions.single { it.effectiveFrom == V2TestIds.CONFIGURATION_DATE }
        val result = store.workConfiguration.applyAvailabilityMutation(
            WorkConfigurationAvailabilityMutation(
                expectedHistory = history,
                revision = EffectiveRevision(
                    id = sameDate.id,
                    effectiveFrom = sameDate.effectiveFrom,
                    value = sameDate.value.copy(availabilityLabel = label),
                ),
            ),
        )
        assertTrue(result is WorkConfigurationAvailabilityWriteResult.Saved)
    }

    private suspend fun capture(id: UUID?, start: Instant, end: Instant) =
        store.availabilityWindows.captureExpectation(id, configuration(), start, end)

    private suspend fun build(
        id: UUID,
        start: Instant,
        end: Instant,
        previous: com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord?,
        timestamp: Instant,
    ) = buildAvailabilityWindowRecord(
        AvailabilityWindowDraft(id, OWNER, ZONE, start, end),
        configuration(),
        timestamp,
        previous,
    )

    private suspend fun configuration(): ResolvedWorkConfigurationRevision =
        ResolvedWorkConfigurationRevision.resolve(requireNotNull(store.workConfiguration.get()), OWNER)

    private fun openStore() {
        database = MiGuardiaV2Database.build(context, DB)
        store = LocalDataStore(database, recurringClock = CLOCK)
    }

    private companion object {
        const val DB = "availability-window-persistence-test.db"
        val OWNER: LocalDate = LocalDate.of(2026, 1, 3)
        val ZONE: ZoneOffset = ZoneOffset.UTC
        val START: Instant = Instant.parse("2026-01-03T08:00:00Z")
        val END: Instant = Instant.parse("2026-01-03T12:00:00Z")
        val NOW: Instant = Instant.parse("2026-01-04T12:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZONE)
        val ID: UUID = V2TestIds.uuid(701)
    }
}
