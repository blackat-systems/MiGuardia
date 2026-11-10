package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.model.CancellationActor
import com.blackatsystems.miguardia.core.domain.model.CancellationEvidence
import com.blackatsystems.miguardia.core.domain.model.ShiftSituation
import com.blackatsystems.miguardia.core.domain.model.ShiftSituationMutation
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShiftSituationPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: MiGuardiaV2Database
    private lateinit var store: LocalDataStore
    private val clock = Clock.fixed(Instant.parse("2026-09-14T18:00:00Z"), ZoneOffset.UTC)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB)
        database = MiGuardiaV2Database.build(context, DB)
        store = LocalDataStore(database, recurringClock = clock)
    }

    @After fun tearDown() {
        store.close()
        context.deleteDatabase(DB)
    }

    @Test fun chainedCorrectionsRefreshExpectationAndStaleDialogCannotOverwrite() = runBlocking {
        val write = seedShift(V2TestIds.uuid(801))
        val initial = requireNotNull(store.shiftSituations.inspect(write.shift.id))
        assertSuspendThrows<InvalidLocalDataException> {
            store.shiftSituations.apply(ShiftSituationMutation(initial, null, false))
        }
        assertEquals(initial, store.shiftSituations.inspect(write.shift.id))
        store.shiftSituations.apply(ShiftSituationMutation(initial, situation(initial.planned.shift.id, ShiftStatus.ABSENT), false))

        assertSuspendThrows<ConflictingLocalWriteException> {
            store.shiftSituations.apply(
                ShiftSituationMutation(initial, situation(initial.planned.shift.id, ShiftStatus.CANCELLED), false),
            )
        }

        val absent = requireNotNull(store.shiftSituations.inspect(write.shift.id))
        val cancelled = situation(write.shift.id, ShiftStatus.CANCELLED, absent.previous?.createdAt)
        store.shiftSituations.apply(ShiftSituationMutation(absent, cancelled, false))
        assertEquals(ShiftStatus.CANCELLED, store.shiftSituations.inspect(write.shift.id)?.planned?.shift?.status)

        val corrected = requireNotNull(store.shiftSituations.inspect(write.shift.id))
        store.shiftSituations.apply(ShiftSituationMutation(corrected, null, false))
        val planned = requireNotNull(store.shiftSituations.inspect(write.shift.id))
        assertEquals(ShiftStatus.PLANNED, planned.planned.shift.status)
        assertNull(planned.previous)
    }

    @Test fun casComparesTheExactExtraSetAndRemovalNeedsExplicitConsent() = runBlocking {
        val write = seedShift(V2TestIds.uuid(811))
        insertActual(write)
        val beforeExtra = requireNotNull(store.shiftSituations.inspect(write.shift.id))
        insertExtra(write, V2TestIds.uuid(812), V2TestIds.uuid(813))

        assertSuspendThrows<ConflictingLocalWriteException> {
            store.shiftSituations.apply(
                ShiftSituationMutation(beforeExtra, situation(write.shift.id, ShiftStatus.ABSENT), true),
            )
        }
        val fresh = requireNotNull(store.shiftSituations.inspect(write.shift.id))
        assertEquals(1, fresh.actual?.previousActual?.extraIntervals?.size)
        assertSuspendThrows<InvalidLocalDataException> {
            store.shiftSituations.apply(
                ShiftSituationMutation(fresh, situation(write.shift.id, ShiftStatus.ABSENT), false),
            )
        }
        store.shiftSituations.apply(
            ShiftSituationMutation(fresh, situation(write.shift.id, ShiftStatus.ABSENT), true),
        )
        assertNull(store.shiftActuals.getExpectation(write.shift.id)?.previousActual)
    }

    @Test fun legacyStatusWithoutMetadataRemainsCorrectibleAcrossAllowedTransitions() = runBlocking {
        val write = seedShift(V2TestIds.uuid(821))
        database.openHelper.writableDatabase.execSQL(
            "UPDATE shifts SET status = 'CANCELLED' WHERE id = ?",
            arrayOf(write.shift.id.toString()),
        )
        val legacy = requireNotNull(store.shiftSituations.inspect(write.shift.id))
        assertEquals(ShiftStatus.CANCELLED, legacy.planned.shift.status)
        assertNull(legacy.previous)
        store.shiftSituations.apply(
            ShiftSituationMutation(legacy, situation(write.shift.id, ShiftStatus.ABSENT), false),
        )
        val absent = requireNotNull(store.shiftSituations.inspect(write.shift.id))
        assertEquals(ShiftStatus.ABSENT, absent.planned.shift.status)
        store.shiftSituations.apply(ShiftSituationMutation(absent, null, false))
        assertEquals(ShiftStatus.PLANNED, store.shiftSituations.inspect(write.shift.id)?.planned?.shift?.status)
    }

    @Test fun twoCancellationsCannotShareTheSamePrivateEvidenceKey() = runBlocking {
        val fixture = store.seedV2Catalog()
        val first = store.buildTestV2Write(fixture, V2TestIds.uuid(831), V2TestIds.SHIFT_DATE)
        val second = store.buildTestV2Write(fixture, V2TestIds.uuid(832), V2TestIds.SHIFT_DATE.plusDays(1))
        store.v2Shifts.insert(first)
        store.v2Shifts.insert(second)
        val evidence = CancellationEvidence(
            storageKey = "cancel_${first.shift.id}_a1b2c3d4.png",
            mimeType = "image/png", byteSize = 1, pixelWidth = 1, pixelHeight = 1,
            sha256 = "a".repeat(64),
        )
        val firstExpected = requireNotNull(store.shiftSituations.inspect(first.shift.id))
        store.shiftSituations.apply(
            ShiftSituationMutation(
                firstExpected,
                situation(first.shift.id, ShiftStatus.CANCELLED).copy(evidence = evidence),
                false,
            ),
        )
        val secondExpected = requireNotNull(store.shiftSituations.inspect(second.shift.id))
        assertSuspendThrows<ConflictingLocalWriteException> {
            store.shiftSituations.apply(
                ShiftSituationMutation(
                    secondExpected,
                    situation(second.shift.id, ShiftStatus.CANCELLED).copy(evidence = evidence),
                    false,
                ),
            )
        }
        assertEquals(ShiftStatus.PLANNED, store.shiftSituations.inspect(second.shift.id)?.planned?.shift?.status)
    }

    private suspend fun seedShift(id: UUID) = store.buildTestV2Write(
        fixture = store.seedV2Catalog(), id = id, date = V2TestIds.SHIFT_DATE,
    ).also { store.v2Shifts.insert(it) }

    private fun situation(id: UUID, status: ShiftStatus, createdAt: Instant? = null): ShiftSituation {
        val created = createdAt ?: clock.instant()
        return ShiftSituation(
            shiftId = id,
            status = status,
            reason = if (status == ShiftStatus.CANCELLED) "Servicio retirado" else "Inasistencia",
            privateNote = null,
            cancellationActor = if (status == ShiftStatus.CANCELLED) CancellationActor.EMPLOYER else null,
            notifiedAt = if (status == ShiftStatus.CANCELLED) clock.instant() else null,
            evidence = null,
            createdAt = created,
            updatedAt = if (createdAt == null) created else created.plusMillis(1),
        )
    }

    private fun insertActual(write: V2ShiftWrite) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO shift_actual_records VALUES (?, ?, 'PRIVATE_SECURITY', ?, ?, 'Prueba', NULL, ?, ?)",
            arrayOf<Any>(
                write.shift.id.toString(),
                V2TestIds.TIMELINE.toString(),
                write.shift.startAt.minusSeconds(60 * 60).toEpochMilli(),
                write.shift.endAt.toEpochMilli(),
                clock.instant().toEpochMilli(),
                clock.instant().toEpochMilli(),
            ),
        )
    }

    private fun insertExtra(write: V2ShiftWrite, classId: UUID, intervalId: UUID) {
        val db = database.openHelper.writableDatabase
        db.execSQL(
            "INSERT INTO extra_work_classes VALUES (?, ?, 'PRIVATE_SECURITY', 'Extra', 'EXTRA', 0, 1, 1, 1, 1)",
            arrayOf(classId.toString(), V2TestIds.TIMELINE.toString()),
        )
        db.execSQL(
            "INSERT INTO shift_extra_intervals VALUES (?, ?, ?, 'PRIVATE_SECURITY', ?, ?, ?, 'Extra', 0, 1, ?, ?)",
            arrayOf<Any>(
                intervalId.toString(),
                write.shift.id.toString(),
                V2TestIds.TIMELINE.toString(),
                classId.toString(),
                write.shift.startAt.minusSeconds(60 * 60).toEpochMilli(),
                write.shift.startAt.toEpochMilli(),
                clock.instant().toEpochMilli(),
                clock.instant().toEpochMilli(),
            ),
        )
    }

    private suspend inline fun <reified T : Throwable> assertSuspendThrows(crossinline block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("Se esperaba ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            if (error !is T) throw error
        }
    }

    private companion object { const val DB = "shift-situation-stage2-test.db" }
}
