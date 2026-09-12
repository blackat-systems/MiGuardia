package com.blackatsystems.miguardia.core.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.domain.model.ExtraWorkClassWriteResult
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkDraft
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkMutation
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSelection
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkWriteResult
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.buildIndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass
import com.blackatsystems.miguardia.core.domain.work.HoursPeriod
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PositiveMinutes
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationReferenceMutation
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationReferenceWriteResult
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IndependentExtraWorkPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: MiGuardiaV2Database
    private lateinit var store: LocalDataStore
    private lateinit var fixture: SeededV2Catalog
    private lateinit var extraClass: ExtraWorkClass

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB)
        openStore()
        fixture = store.seedV2Catalog()
        extraClass = ExtraWorkClass.create(
            V2TestIds.uuid(401),
            V2TestIds.TIMELINE,
            WorkSector.PRIVATE_SECURITY,
            "Servicio extra",
            true,
            true,
            NOW.plusMillis(1),
        )
        assertTrue(
            store.shiftActuals.saveExtraWorkClass(null, extraClass) is ExtraWorkClassWriteResult.Saved,
        )
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(DB)
    }

    @Test
    fun createCorrectDeleteAndReopenPreserveExactSnapshotsAndReactiveRows() = runBlocking {
        val start = NOW.minus(Duration.ofHours(3))
        val end = NOW.minus(Duration.ofHours(1))
        val selection = selection(start.atZone(ZONE).toLocalDate())
        val expectation = capture(RECORD_ID, selection, start, end)
        val initial = buildIndependentExtraWorkRecord(
            draft(RECORD_ID, start, end),
            selection,
            CLOCK,
            NOW.plusMillis(2),
        )

        val saved = store.independentExtraWork.applyMutation(
            IndependentExtraWorkMutation(expectation, initial, true, true),
        )
        assertEquals(IndependentExtraWorkWriteResult.Saved(initial), saved)
        assertEquals(listOf(initial), store.independentExtraWork.observeOn(
            V2TestIds.TIMELINE,
            WorkSector.PRIVATE_SECURITY,
            initial.ownerLocalDate,
        ).first())

        store.close()
        openStore()
        val reopened = requireNotNull(store.independentExtraWork.get(RECORD_ID))
        assertEquals(initial, reopened)

        val archivedClass = extraClass.updated(
            name = "Servicio archivado",
            helpsMeetHoursReference = false,
            isActive = false,
            timestamp = NOW.plusMillis(4),
        )
        assertTrue(
            store.shiftActuals.saveExtraWorkClass(extraClass, archivedClass) is ExtraWorkClassWriteResult.Saved,
        )
        extraClass = archivedClass
        val historicalSelection = selection(start.atZone(ZONE).toLocalDate())
        val correctionExpectation = capture(RECORD_ID, historicalSelection, start, end.plusSeconds(60))
        val corrected = buildIndependentExtraWorkRecord(
            draft(RECORD_ID, start, end.plusSeconds(60)),
            historicalSelection,
            CLOCK,
            NOW.plusMillis(5),
            previous = reopened,
            preserveHistoricalSnapshot = true,
        )
        assertEquals(
            IndependentExtraWorkWriteResult.Saved(corrected),
            store.independentExtraWork.applyMutation(
                IndependentExtraWorkMutation(correctionExpectation, corrected, true, true),
            ),
        )
        assertEquals("Servicio extra", corrected.snapshot.className)
        assertTrue(corrected.snapshot.helpsMeetHoursReference)
        val deleteExpectation = capture(RECORD_ID, historicalSelection, start, end.plusSeconds(60))
        assertEquals(
            IndependentExtraWorkWriteResult.Deleted,
            store.independentExtraWork.applyMutation(
                IndependentExtraWorkMutation(deleteExpectation, null, true, true),
            ),
        )
        assertNull(store.independentExtraWork.get(RECORD_ID))
    }

    @Test
    fun staleDoubleTouchReturnsConflictWithoutPartialRows() = runBlocking {
        val start = NOW.minus(Duration.ofHours(2))
        val end = NOW.minus(Duration.ofHours(1))
        val selection = selection(start.atZone(ZONE).toLocalDate())
        val expectation = capture(RECORD_ID, selection, start, end)
        val record = buildIndependentExtraWorkRecord(
            draft(RECORD_ID, start, end),
            selection,
            CLOCK,
            NOW.plusMillis(2),
        )
        val mutation = IndependentExtraWorkMutation(expectation, record, true, true)

        assertTrue(store.independentExtraWork.applyMutation(mutation) is IndependentExtraWorkWriteResult.Saved)
        assertEquals(
            IndependentExtraWorkWriteResult.Conflict,
            store.independentExtraWork.applyMutation(mutation),
        )
        assertEquals(1, store.independentExtraWork.observeAll(
            V2TestIds.TIMELINE,
            WorkSector.PRIVATE_SECURITY,
        ).first().size)
    }

    @Test
    fun overlapRequiresExplicitConfirmationAndRollbackLeavesSecondIdAbsent() = runBlocking {
        val firstStart = NOW.minus(Duration.ofHours(4))
        val firstEnd = NOW.minus(Duration.ofHours(2))
        save(RECORD_ID, firstStart, firstEnd)
        val secondId = V2TestIds.uuid(403)
        val secondStart = NOW.minus(Duration.ofHours(3))
        val secondEnd = NOW.minus(Duration.ofHours(1))
        val selection = selection(secondStart.atZone(ZONE).toLocalDate())
        val expectation = capture(secondId, selection, secondStart, secondEnd)
        assertEquals(1, expectation.observedExtras.size)
        val record = buildIndependentExtraWorkRecord(
            draft(secondId, secondStart, secondEnd),
            selection,
            CLOCK,
            NOW.plusMillis(3),
        )

        assertSuspendThrows<InvalidLocalDataException> {
            store.independentExtraWork.applyMutation(
                IndependentExtraWorkMutation(expectation, record, false, true),
            )
        }
        assertNull(store.independentExtraWork.get(secondId))
        assertTrue(
            store.independentExtraWork.applyMutation(
                IndependentExtraWorkMutation(expectation, record, true, true),
            ) is IndependentExtraWorkWriteResult.Saved,
        )
    }

    @Test
    fun forgedFutureIntervalIsRejectedByPersistenceBoundary() = runBlocking {
        val start = NOW.plus(Duration.ofHours(1))
        val end = NOW.plus(Duration.ofHours(2))
        val selection = selection(start.atZone(ZONE).toLocalDate())
        val expectation = capture(RECORD_ID, selection, start, end)
        val past = buildIndependentExtraWorkRecord(
            draft(RECORD_ID, NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1))),
            selection(NOW.atZone(ZONE).toLocalDate()),
            CLOCK,
            NOW.plusMillis(2),
        )
        val forged = past.copy(
            ownerLocalDate = start.atZone(ZONE).toLocalDate(),
            start = start,
            end = end,
            configurationRevisionId = selection.configuration.revision.id,
        )

        assertSuspendThrows<IllegalArgumentException> {
            store.independentExtraWork.applyMutation(
                IndependentExtraWorkMutation(expectation, forged, true, true),
            )
        }
        assertNull(store.independentExtraWork.get(RECORD_ID))
    }

    @Test
    fun multidayIntervalFinishedAtTheCurrentMinutePersistsExactly() = runBlocking {
        val start = NOW.minus(Duration.ofHours(30))
        val end = NOW
        val selection = selection(start.atZone(ZONE).toLocalDate())
        val record = buildIndependentExtraWorkRecord(
            draft(RECORD_ID, start, end),
            selection,
            CLOCK,
            NOW.plusMillis(2),
        )

        assertEquals(
            IndependentExtraWorkWriteResult.Saved(record),
            store.independentExtraWork.applyMutation(
                IndependentExtraWorkMutation(
                    capture(RECORD_ID, selection, start, end),
                    record,
                    true,
                    true,
                ),
            ),
        )
        assertEquals(30L * 60L, requireNotNull(store.independentExtraWork.get(RECORD_ID)).durationMinutes)
    }

    @Test
    fun multidayIntervalDetectsProtectionOnItsLastWorkedDate() = runBlocking {
        val start = NOW.minus(Duration.ofHours(30))
        val end = NOW
        val protectedDate = end.minusNanos(1).atZone(ZONE).toLocalDate()
        store.medicalLeaves.create(
            MedicalLeave(
                V2TestIds.uuid(409),
                protectedDate,
                protectedDate,
                "Nota ficticia",
                NOW.plusMillis(1),
                NOW.plusMillis(1),
            ),
        )
        val selection = selection(start.atZone(ZONE).toLocalDate())
        val expectation = capture(RECORD_ID, selection, start, end)
        val record = buildIndependentExtraWorkRecord(
            draft(RECORD_ID, start, end),
            selection,
            CLOCK,
            NOW.plusMillis(2),
        )

        assertTrue(expectation.hasProtectedDatesFor(record))
        assertSuspendThrows<InvalidLocalDataException> {
            store.independentExtraWork.applyMutation(
                IndependentExtraWorkMutation(expectation, record, true, false),
            )
        }
        assertNull(store.independentExtraWork.get(RECORD_ID))
    }

    @Test
    fun foreignKeysRejectCrossSectorRowsAndIntegrityRejectsInvalidIntervals() = runBlocking {
        val start = NOW.minus(Duration.ofHours(2))
        val end = NOW.minus(Duration.ofHours(1))
        val selection = selection(start.atZone(ZONE).toLocalDate())
        val record = buildIndependentExtraWorkRecord(
            draft(RECORD_ID, start, end),
            selection,
            CLOCK,
            NOW.plusMillis(2),
        )

        assertSuspendThrows<SQLiteConstraintException> {
            database.independentExtraWorkDao().insert(
                record.toEntity().copy(
                    id = V2TestIds.uuid(404).toString(),
                    sector = WorkSector.POLICE.name,
                ),
            )
        }
        database.independentExtraWorkDao().insert(
            record.toEntity().copy(
                id = V2TestIds.uuid(405).toString(),
                endEpochMillis = record.start.toEpochMilli(),
            ),
        )
        assertSuspendThrows<InvalidLocalDataException> {
            store.independentExtraWork.get(V2TestIds.uuid(405))
        }
    }

    @Test
    fun oneContinuousFlowEmitsCreateAndDeleteWithoutProjectingAnErrorAsEmpty() = runBlocking {
        val emissions = Channel<List<com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord>>(
            Channel.UNLIMITED,
        )
        val observer = launch {
            store.independentExtraWork.observeAll(
                V2TestIds.TIMELINE,
                WorkSector.PRIVATE_SECURITY,
            ).distinctUntilChanged().collect(emissions::send)
        }
        assertTrue(withTimeout(5_000) { emissions.receive() }.isEmpty())

        val start = NOW.minus(Duration.ofHours(2))
        val end = NOW.minus(Duration.ofHours(1))
        save(RECORD_ID, start, end)
        assertEquals(RECORD_ID, withTimeout(5_000) { emissions.receive() }.single().id)

        val selection = selection(start.atZone(ZONE).toLocalDate())
        val expectation = capture(RECORD_ID, selection, start, end)
        assertEquals(
            IndependentExtraWorkWriteResult.Deleted,
            store.independentExtraWork.applyMutation(
                IndependentExtraWorkMutation(expectation, null, true, true),
            ),
        )
        assertTrue(withTimeout(5_000) { emissions.receive() }.isEmpty())
        observer.cancelAndJoin()
        emissions.close()
        Unit
    }

    @Test
    fun retroactiveReferenceRestartDoesNotRewriteOrInvalidateStoredWorkSnapshots() = runBlocking {
        val start = NOW.minus(Duration.ofHours(2))
        val end = NOW.minus(Duration.ofHours(1))
        val ownerDate = start.atZone(ZONE).toLocalDate()
        val initialHistory = requireNotNull(store.workConfiguration.get())
        val futureRevision = EffectiveRevision(
            id = V2TestIds.uuid(408),
            effectiveFrom = ownerDate,
            value = requireNotNull(initialHistory.timeline.revisionAt(ownerDate)).value.copy(
                availabilityLabel = com.blackatsystems.miguardia.core.domain.work
                    .AvailabilityLabel.PASSIVE_GUARD,
            ),
        )
        store.workConfiguration.addRevision(V2TestIds.TIMELINE, futureRevision)
        save(RECORD_ID, start, end)
        val shift = store.buildTestV2Write(
            fixture = fixture,
            id = V2TestIds.uuid(406),
            date = ownerDate,
            timestamp = NOW.plusMillis(6),
        )
        store.v2Shifts.insert(shift)
        val history = requireNotNull(store.workConfiguration.get())
        val restartDate = ownerDate.minusDays(1)
        val referenceRevision = EffectiveRevision(
            id = V2TestIds.uuid(407),
            effectiveFrom = restartDate,
            value = WorkConfiguration(
                sector = WorkSector.PRIVATE_SECURITY,
                hoursReference = HoursReference.Fixed(
                    HoursPeriod.Monthly,
                    PositiveMinutes(6_000),
                ),
                availabilityLabel = history.timeline.revisionAt(restartDate)?.value?.availabilityLabel,
                hoursReferenceStartedOn = restartDate,
            ),
        )

        assertTrue(
            store.workConfiguration.applyReferenceMutation(
                WorkConfigurationReferenceMutation(history, referenceRevision),
            ) is WorkConfigurationReferenceWriteResult.Saved,
        )

        val storedBeforeCorrection = requireNotNull(store.independentExtraWork.get(RECORD_ID))
        val selectionAfterRestart = selection(storedBeforeCorrection.ownerLocalDate)
        val correctedEnd = storedBeforeCorrection.end.plusSeconds(60)
        val correction = buildIndependentExtraWorkRecord(
            draft(RECORD_ID, storedBeforeCorrection.start, correctedEnd),
            selectionAfterRestart,
            CLOCK,
            storedBeforeCorrection.updatedAt.plusMillis(1),
            previous = storedBeforeCorrection,
        )
        assertEquals(
            IndependentExtraWorkWriteResult.Saved(correction),
            store.independentExtraWork.applyMutation(
                IndependentExtraWorkMutation(
                    capture(
                        RECORD_ID,
                        selectionAfterRestart,
                        storedBeforeCorrection.start,
                        correctedEnd,
                    ),
                    correction,
                    true,
                    true,
                ),
            ),
        )

        assertEquals(
            futureRevision.id,
            requireNotNull(store.independentExtraWork.get(RECORD_ID)).configurationRevisionId,
        )
        assertEquals(
            futureRevision.id,
            requireNotNull(store.v2Shifts.getWorkSnapshot(shift.shift.id)).configurationRevisionId,
        )
        store.close()
        openStore()
        assertEquals(
            futureRevision.id,
            requireNotNull(store.independentExtraWork.get(RECORD_ID)).configurationRevisionId,
        )
    }

    private suspend fun save(id: UUID, start: Instant, end: Instant) {
        val selection = selection(start.atZone(ZONE).toLocalDate())
        val record = buildIndependentExtraWorkRecord(
            draft(id, start, end),
            selection,
            CLOCK,
            NOW.plusMillis(id.leastSignificantBits and 0xFF),
        )
        val result = store.independentExtraWork.applyMutation(
            IndependentExtraWorkMutation(capture(id, selection, start, end), record, true, true),
        )
        assertTrue(result is IndependentExtraWorkWriteResult.Saved)
    }

    private suspend fun selection(date: LocalDate): IndependentExtraWorkSelection =
        IndependentExtraWorkSelection(
            configuration = ResolvedWorkConfigurationRevision.resolve(
                requireNotNull(store.workConfiguration.get()),
                date,
            ),
            workPlace = fixture.place,
            objective = fixture.objective,
            workType = fixture.type,
            template = fixture.template,
            extraWorkClass = extraClass,
        )

    private suspend fun capture(
        id: UUID,
        selection: IndependentExtraWorkSelection,
        start: Instant,
        end: Instant,
    ) = store.independentExtraWork.captureExpectation(
        id = id,
        selection = selection,
        windowStart = start.minus(Duration.ofHours(1)),
        windowEnd = end.plus(Duration.ofHours(1)),
        windowStartDate = start.atZone(ZONE).toLocalDate(),
        windowEndDateInclusive = end.minusNanos(1).atZone(ZONE).toLocalDate(),
    )

    private fun draft(id: UUID, start: Instant, end: Instant) = IndependentExtraWorkDraft(
        id,
        start.atZone(ZONE).toLocalDate(),
        ZONE,
        start,
        end,
        fixture.template.colorArgb,
        "Cobertura",
    )

    private fun openStore() {
        database = MiGuardiaV2Database.build(context, DB)
        store = LocalDataStore(database, recurringClock = CLOCK)
    }

    private suspend inline fun <reified T : Throwable> assertSuspendThrows(
        crossinline block: suspend () -> Unit,
    ) {
        try {
            block()
            throw AssertionError("Se esperaba ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            if (error !is T) throw error
        }
    }

    private companion object {
        const val DB = "independent-extra-work-persistence-test.db"
        val NOW: Instant = V2TestIds.NOW.plus(Duration.ofDays(3))
        val ZONE: ZoneOffset = ZoneOffset.UTC
        val CLOCK: Clock = Clock.fixed(NOW, ZONE)
        val RECORD_ID: UUID = V2TestIds.uuid(402)
    }
}
