package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.repository.DuplicateObjectiveAbbreviationException
import com.blackatsystems.miguardia.core.domain.repository.DuplicateScheduleCombinationException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDataStoreInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: MiGuardiaDatabase
    private lateinit var store: LocalDataStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DATABASE)
        openStore()
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun abbreviationsAreNormalizedAndUnique() = runBlocking {
        store.objectives.create(objective(OBJECTIVE_ID, "  dep "))
        val saved = store.objectives.getById(OBJECTIVE_ID)

        assertEquals("DEP", saved?.abbreviation)
        assertSuspendThrows<DuplicateObjectiveAbbreviationException> {
            store.objectives.create(objective(SECOND_OBJECTIVE_ID, "dep"))
        }
        Unit
    }

    @Test
    fun hiddenObjectivesAreExcludedFromActiveButRemainObservable() = runBlocking {
        store.objectives.create(objective())

        store.objectives.hide(OBJECTIVE_ID, FIXED_INSTANT.plusSeconds(1))

        assertTrue(store.objectives.observeActive().first().isEmpty())
        val all = store.objectives.observeAll().first()
        assertEquals(1, all.size)
        assertTrue(!all.single().isActive)
    }

    @Test
    fun exactSchedulesAreUniqueAndDifferentSchedulesKeepTheirColors() = runBlocking {
        store.objectives.create(objective())
        val night = schedule(SCHEDULE_ID, LocalTime.of(19, 0), LocalTime.of(7, 0), 0xFF123456.toInt())
        val morning = schedule(SECOND_SCHEDULE_ID, LocalTime.of(7, 0), LocalTime.of(19, 0), 0xFFFEDCBA.toInt())
        store.scheduleCombinations.create(night)
        store.scheduleCombinations.create(morning)

        val schedules = store.scheduleCombinations.observeByObjective(OBJECTIVE_ID).first()
        assertEquals(listOf(0xFFFEDCBA.toInt(), 0xFF123456.toInt()), schedules.map { it.colorArgb })
        assertSuspendThrows<DuplicateScheduleCombinationException> {
            store.scheduleCombinations.create(night.copy(id = THIRD_SCHEDULE_ID))
        }
        Unit
    }

    @Test
    fun overnightMonthEndShiftUsesRealInstantsAndMonthOfStart() = runBlocking {
        val shift = shift(
            id = SHIFT_ID,
            startDate = LocalDate.of(2026, 8, 31),
            startTime = LocalTime.of(19, 0),
            endDate = LocalDate.of(2026, 9, 1),
            endTime = LocalTime.of(7, 0),
        )
        store.shifts.insert(shift)

        val august = store.shifts.observeStartingBetween(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
        ).first()
        val september = store.shifts.observeStartingBetween(
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 30),
        ).first()

        assertEquals(listOf(shift), august)
        assertTrue(september.isEmpty())
        assertEquals(LocalDate.of(2026, 8, 31), august.single().localStartDate)
        assertTrue(august.single().endAt.isAfter(august.single().startAt))
        assertEquals(0xFF123456.toInt(), august.single().colorArgbSnapshot)
    }

    @Test
    fun twoShiftsCanBeStoredOnTheSameDay() = runBlocking {
        val date = LocalDate.of(2026, 8, 18)
        store.shifts.insert(shift(SHIFT_ID, date, LocalTime.of(6, 0), date, LocalTime.of(12, 0)))
        store.shifts.insert(shift(SECOND_SHIFT_ID, date, LocalTime.of(14, 0), date, LocalTime.of(20, 0)))

        val shifts = store.shifts.observeStartingBetween(date, date).first()
        assertEquals(2, shifts.size)
    }

    @Test
    fun authorizedShiftStatusesRoundTripExactly() = runBlocking {
        val date = LocalDate.of(2026, 8, 19)
        val shifts = listOf(
            shift(SHIFT_ID, date, LocalTime.of(0, 0), date, LocalTime.of(1, 0)),
            shift(SECOND_SHIFT_ID, date, LocalTime.of(2, 0), date, LocalTime.of(3, 0))
                .copy(status = ShiftStatus.CANCELLED),
            shift(THIRD_SHIFT_ID, date, LocalTime.of(4, 0), date, LocalTime.of(5, 0))
                .copy(status = ShiftStatus.ABSENT),
        )
        shifts.forEach { store.shifts.insert(it) }

        assertEquals(
            listOf(ShiftStatus.PLANNED, ShiftStatus.CANCELLED, ShiftStatus.ABSENT),
            store.shifts.observeStartingBetween(date, date).first().map { it.status },
        )
    }

    @Test
    fun deletingTemplatesDoesNotChangeHistoricalSnapshot() = runBlocking {
        store.objectives.create(objective())
        store.scheduleCombinations.create(schedule())
        val historical = shift(
            id = SHIFT_ID,
            startDate = LocalDate.of(2026, 8, 20),
            startTime = LocalTime.of(19, 0),
            endDate = LocalDate.of(2026, 8, 21),
            endTime = LocalTime.of(7, 0),
        ).copy(
            sourceObjectiveId = OBJECTIVE_ID,
            sourceScheduleCombinationId = SCHEDULE_ID,
        )
        store.shifts.insert(historical)

        store.objectives.delete(OBJECTIVE_ID)

        assertNull(store.objectives.getById(OBJECTIVE_ID))
        assertNull(store.scheduleCombinations.getById(SCHEDULE_ID))
        assertEquals(historical, store.shifts.getById(SHIFT_ID))
    }

    @Test
    fun missingDayIsDifferentFromExplicitUndefinedAndOnlyRowsCountAsDayOff() = runBlocking {
        val undefinedDate = LocalDate.of(2026, 8, 10)
        val dayOffDate = LocalDate.of(2026, 8, 11)
        val missingDate = LocalDate.of(2026, 8, 12)
        store.explicitDayStatuses.set(undefinedDate, ExplicitDayStatusType.UNDEFINED)
        store.explicitDayStatuses.set(dayOffDate, ExplicitDayStatusType.DAY_OFF)

        val statuses = store.explicitDayStatuses.observeBetween(undefinedDate, missingDate).first()
        assertEquals(2, statuses.size)
        assertEquals(1, statuses.count { it.type == ExplicitDayStatusType.DAY_OFF })
        assertEquals(1, statuses.count { it.type == ExplicitDayStatusType.UNDEFINED })
        assertTrue(statuses.none { it.date == missingDate })

        store.explicitDayStatuses.clear(undefinedDate)
        assertTrue(store.explicitDayStatuses.observeBetween(undefinedDate, undefinedDate).first().isEmpty())
    }

    @Test
    fun medicalLeaveIntersectsBothMonthsAndInvalidRangeIsRejected() = runBlocking {
        val leave = MedicalLeave(
            id = MEDICAL_LEAVE_ID,
            startDate = LocalDate.of(2026, 8, 30),
            endDateInclusive = LocalDate.of(2026, 9, 2),
            privateNote = "Nota ficticia privada",
            createdAt = FIXED_INSTANT,
            updatedAt = FIXED_INSTANT,
        )
        store.medicalLeaves.create(leave)

        assertEquals(
            listOf(leave),
            store.medicalLeaves.observeIntersecting(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
            ).first(),
        )
        assertEquals(
            listOf(leave),
            store.medicalLeaves.observeIntersecting(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
            ).first(),
        )
        assertSuspendThrows<InvalidLocalDataException> {
            store.medicalLeaves.create(
                leave.copy(
                    id = SECOND_MEDICAL_LEAVE_ID,
                    startDate = LocalDate.of(2026, 9, 2),
                    endDateInclusive = LocalDate.of(2026, 9, 1),
                ),
            )
        }
        Unit
    }

    @Test
    fun closingAndReopeningDatabaseKeepsDataAndSchemaVersion() = runBlocking {
        store.objectives.create(objective())
        store.close()
        openStore()

        assertNotNull(store.objectives.getById(OBJECTIVE_ID))
        assertEquals(4, database.openHelper.readableDatabase.version)
    }

    @Test
    fun productionConfigurationRejectsMainThreadQueries() {
        var thrown: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                database.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM objectives"))
            } catch (error: Throwable) {
                thrown = error
            }
        }

        assertTrue("Room debía rechazar una consulta en el hilo principal", thrown is IllegalStateException)
    }

    @Test
    fun recentlyUsedUsesCreationTimeLimitAndExcludesHiddenTemplates() = runBlocking {
        store.objectives.create(objective())
        val first = schedule(SCHEDULE_ID, LocalTime.of(6, 0), LocalTime.of(10, 0), 0xFF111111.toInt())
        val second = schedule(SECOND_SCHEDULE_ID, LocalTime.of(10, 0), LocalTime.of(14, 0), 0xFF222222.toInt())
        val hidden = schedule(THIRD_SCHEDULE_ID, LocalTime.of(14, 0), LocalTime.of(18, 0), 0xFF333333.toInt())
        listOf(first, second, hidden).forEach { store.scheduleCombinations.create(it) }
        store.shifts.insert(
            shift(SHIFT_ID, LocalDate.of(2026, 9, 1), LocalTime.of(6, 0), LocalDate.of(2026, 9, 1), LocalTime.of(10, 0)).copy(
                sourceObjectiveId = OBJECTIVE_ID,
                sourceScheduleCombinationId = first.id,
                createdAt = FIXED_INSTANT.plusSeconds(1),
                updatedAt = FIXED_INSTANT.plusSeconds(1),
            ),
        )
        store.shifts.insert(
            shift(SECOND_SHIFT_ID, LocalDate.of(2026, 7, 1), LocalTime.of(10, 0), LocalDate.of(2026, 7, 1), LocalTime.of(14, 0)).copy(
                sourceObjectiveId = OBJECTIVE_ID,
                sourceScheduleCombinationId = second.id,
                createdAt = FIXED_INSTANT.plusSeconds(3),
                updatedAt = FIXED_INSTANT.plusSeconds(3),
            ),
        )
        store.shifts.insert(
            shift(THIRD_SHIFT_ID, LocalDate.of(2026, 8, 1), LocalTime.of(14, 0), LocalDate.of(2026, 8, 1), LocalTime.of(18, 0)).copy(
                sourceObjectiveId = OBJECTIVE_ID,
                sourceScheduleCombinationId = hidden.id,
                createdAt = FIXED_INSTANT.plusSeconds(5),
                updatedAt = FIXED_INSTANT.plusSeconds(5),
            ),
        )
        store.scheduleCombinations.hide(hidden.id, FIXED_INSTANT.plusSeconds(6))

        val recent = store.scheduleCombinations.observeRecentlyUsed(limit = 2).first()

        assertEquals(listOf(second.id, first.id), recent.map { it.combination.id })
        assertEquals(
            listOf(FIXED_INSTANT.plusSeconds(3), FIXED_INSTANT.plusSeconds(1)),
            recent.map { it.lastUsedAt },
        )
    }

    @Test
    fun batchInsertAndReplacementAreAtomicAndLimitedToChosenIds() = runBlocking {
        val firstDate = LocalDate.of(2026, 8, 20)
        val secondDate = firstDate.plusDays(1)
        val untouchedDate = firstDate.plusDays(2)
        val original = shift(SHIFT_ID, firstDate, LocalTime.of(8, 0), firstDate, LocalTime.of(16, 0))
        val untouched = shift(SECOND_SHIFT_ID, untouchedDate, LocalTime.of(8, 0), untouchedDate, LocalTime.of(16, 0))
        store.shifts.insert(original)
        store.shifts.insert(untouched)
        val replacements = listOf(
            shift(THIRD_SHIFT_ID, firstDate, LocalTime.of(19, 0), secondDate, LocalTime.of(7, 0)),
            shift(FOURTH_SHIFT_ID, secondDate, LocalTime.of(8, 0), secondDate, LocalTime.of(16, 0)),
        )

        store.shifts.applyBatch(
            ShiftBatchMutation(
                shiftIdsToDelete = setOf(original.id),
                shiftsToInsert = replacements,
            ),
        )

        val stored = store.shifts.observeStartingBetween(firstDate, untouchedDate).first()
        assertEquals(setOf(untouched.id, THIRD_SHIFT_ID, FOURTH_SHIFT_ID), stored.mapTo(mutableSetOf()) { it.id })
    }

    @Test
    fun batchFailureRollsBackPriorDeletion() = runBlocking {
        val date = LocalDate.of(2026, 8, 25)
        val toDelete = shift(SHIFT_ID, date, LocalTime.of(8, 0), date, LocalTime.of(16, 0))
        val existing = shift(SECOND_SHIFT_ID, date.plusDays(1), LocalTime.of(8, 0), date.plusDays(1), LocalTime.of(16, 0))
        store.shifts.insert(toDelete)
        store.shifts.insert(existing)

        assertSuspendThrows<InvalidLocalDataException> {
            store.shifts.applyBatch(
                ShiftBatchMutation(
                    shiftIdsToDelete = setOf(toDelete.id),
                    shiftsToInsert = listOf(
                        shift(
                            SECOND_SHIFT_ID,
                            date.plusDays(2),
                            LocalTime.of(8, 0),
                            date.plusDays(2),
                            LocalTime.of(16, 0),
                        ),
                    ),
                ),
            )
        }

        assertNotNull(store.shifts.getById(toDelete.id))
        assertNotNull(store.shifts.getById(existing.id))
    }

    @Test
    fun batchRejectsDuplicateAndConflictingIdsBeforeMutatingData() = runBlocking {
        val date = LocalDate.of(2026, 8, 28)
        val original = shift(SHIFT_ID, date, LocalTime.of(8, 0), date, LocalTime.of(16, 0))
        val duplicate = shift(SECOND_SHIFT_ID, date.plusDays(1), LocalTime.of(8, 0), date.plusDays(1), LocalTime.of(16, 0))
        store.shifts.insert(original)

        assertSuspendThrows<InvalidLocalDataException> {
            store.shifts.applyBatch(
                ShiftBatchMutation(shiftsToInsert = listOf(duplicate, duplicate)),
            )
        }
        assertNotNull(store.shifts.getById(original.id))
        assertEquals(null, store.shifts.getById(duplicate.id))

        assertSuspendThrows<InvalidLocalDataException> {
            store.shifts.applyBatch(
                ShiftBatchMutation(
                    shiftIdsToDelete = setOf(original.id),
                    shiftsToInsert = listOf(original.copy(updatedAt = FIXED_INSTANT.plusSeconds(1))),
                ),
            )
        }
        assertEquals(original, store.shifts.getById(original.id))
    }

    private fun openStore() {
        database = Room.databaseBuilder(context, MiGuardiaDatabase::class.java, TEST_DATABASE).build()
        store = LocalDataStore(database)
    }

    private fun objective(
        id: UUID = OBJECTIVE_ID,
        abbreviation: String = "DEP",
    ) = Objective(
        id = id,
        fullName = "Depósito Norte",
        abbreviation = abbreviation,
        address = "Avenida Ficticia 100",
        note = null,
        isActive = true,
        createdAt = FIXED_INSTANT,
        updatedAt = FIXED_INSTANT,
    )

    private fun schedule(
        id: UUID = SCHEDULE_ID,
        startTime: LocalTime = LocalTime.of(19, 0),
        endTime: LocalTime = LocalTime.of(7, 0),
        colorArgb: Int = 0xFF123456.toInt(),
    ) = ScheduleCombination(
        id = id,
        objectiveId = OBJECTIVE_ID,
        startTime = startTime,
        endTime = endTime,
        colorArgb = colorArgb,
        isActive = true,
        createdAt = FIXED_INSTANT,
        updatedAt = FIXED_INSTANT,
    )

    private fun shift(
        id: UUID,
        startDate: LocalDate,
        startTime: LocalTime,
        endDate: LocalDate,
        endTime: LocalTime,
    ): Shift {
        val start = startDate.atTime(startTime).atZone(ZONE).toInstant()
        val end = endDate.atTime(endTime).atZone(ZONE).toInstant()
        return Shift(
            id = id,
            startAt = start,
            endAt = end,
            zoneId = ZONE,
            localStartDate = startDate,
            objectiveNameSnapshot = "Depósito Norte",
            objectiveAbbreviationSnapshot = "DEP",
            objectiveAddressSnapshot = "Avenida Ficticia 100",
            startTimeSnapshot = startTime,
            endTimeSnapshot = endTime,
            colorArgbSnapshot = 0xFF123456.toInt(),
            position = "Acceso ficticio",
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = null,
            sourceScheduleCombinationId = null,
            createdAt = FIXED_INSTANT,
            updatedAt = FIXED_INSTANT,
        )
    }

    private suspend inline fun <reified T : Throwable> assertSuspendThrows(
        crossinline block: suspend () -> Unit,
    ): T = try {
        block()
        throw AssertionError("Se esperaba ${T::class.java.simpleName}")
    } catch (error: Throwable) {
        if (error !is T) throw error
        error
    }

    private companion object {
        const val TEST_DATABASE = "miguardia-local-data-test.db"
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val FIXED_INSTANT: Instant = Instant.parse("2026-08-13T12:00:00Z")
        val OBJECTIVE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val SECOND_OBJECTIVE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val SCHEDULE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000011")
        val SECOND_SCHEDULE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000012")
        val THIRD_SCHEDULE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000013")
        val SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000021")
        val SECOND_SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000022")
        val THIRD_SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000023")
        val FOURTH_SHIFT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000024")
        val MEDICAL_LEAVE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000031")
        val SECOND_MEDICAL_LEAVE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000032")
    }
}
