package com.blackatsystems.miguardia

import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.ui.calendar.CalendarMonthObserver
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMonthObserverInstrumentedTest {
    @Test
    fun combinesRoomFlowsAndSwitchesInclusiveMonthLimits() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "calendar-observer-${UUID.randomUUID()}.db"
        val dataStore = LocalDataStore.create(context, databaseName)
        try {
            val augustShift = shift(
                id = UUID.fromString("20000000-0000-0000-0000-000000000001"),
                date = LocalDate.of(2026, 8, 31),
            )
            val septemberShift = shift(
                id = UUID.fromString("20000000-0000-0000-0000-000000000002"),
                date = LocalDate.of(2026, 9, 1),
            )
            dataStore.v2Shifts.insert(
                V2AppTestFixture.writeFor(dataStore, augustShift, LocalDate.of(2026, 8, 1)),
            )
            dataStore.v2Shifts.insert(
                V2AppTestFixture.writeFor(dataStore, septemberShift, LocalDate.of(2026, 8, 1)),
            )
            dataStore.explicitDayStatuses.set(
                LocalDate.of(2026, 8, 15),
                ExplicitDayStatusType.DAY_OFF,
            )
            dataStore.medicalLeaves.create(
                MedicalLeave(
                    id = UUID.fromString("20000000-0000-0000-0000-000000000003"),
                    startDate = LocalDate.of(2026, 8, 30),
                    endDateInclusive = LocalDate.of(2026, 9, 2),
                    privateNote = "Nota médica ficticia",
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                ),
            )

            val observer = CalendarMonthObserver(
                shiftRepository = dataStore.shifts,
                explicitDayStatusRepository = dataStore.explicitDayStatuses,
                medicalLeaveRepository = dataStore.medicalLeaves,
                holidayRepository = dataStore.holidays,
                vacationRepository = dataStore.vacations,
            )
            val august = observer.observe(YearMonth.of(2026, 8)).first()
            val september = observer.observe(YearMonth.of(2026, 9)).first()

            assertEquals(listOf(augustShift.id), august.shifts.map { it.id })
            assertEquals(1, august.explicitStatuses.size)
            assertEquals(1, august.medicalLeaves.size)
            assertTrue(august.vacations.isEmpty())
            assertEquals(listOf(septemberShift.id), september.shifts.map { it.id })
            assertTrue(september.explicitStatuses.isEmpty())
            assertEquals(1, september.medicalLeaves.size)

            val update = async {
                withTimeout(5_000) {
                    observer.observe(YearMonth.of(2026, 8)).first {
                        it.shifts.size == 2 && it.vacations.size == 1
                    }
                }
            }
            val secondAugustShift = shift(
                id = UUID.fromString("20000000-0000-0000-0000-000000000004"),
                date = LocalDate.of(2026, 8, 10),
            )
            dataStore.vacations.insert(
                Vacation(
                    id = UUID.fromString("20000000-0000-0000-0000-000000000005"),
                    startDate = LocalDate.of(2026, 8, 10),
                    endDateInclusive = LocalDate.of(2026, 8, 12),
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                ),
            )
            dataStore.v2Shifts.insert(
                V2AppTestFixture.writeFor(dataStore, secondAugustShift, LocalDate.of(2026, 8, 1)),
            )
            val updated = update.await()
            assertEquals(2, updated.shifts.size)
            assertEquals(LocalDate.of(2026, 8, 10), updated.vacations.single().startDate)
        } finally {
            dataStore.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun shift(id: UUID, date: LocalDate): Shift {
        val startTime = LocalTime.of(19, 0)
        val endTime = LocalTime.of(7, 0)
        val start = ZonedDateTime.of(date, startTime, AppDefaults.zoneId())
        val end = ZonedDateTime.of(date.plusDays(1), endTime, AppDefaults.zoneId())
        return Shift(
            id = id,
            startAt = start.toInstant(),
            endAt = end.toInstant(),
            zoneId = AppDefaults.zoneId(),
            localStartDate = date,
            objectiveNameSnapshot = "Objetivo de prueba",
            objectiveAbbreviationSnapshot = "PRB",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = startTime,
            endTimeSnapshot = endTime,
            colorArgbSnapshot = 0xFF336699.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = V2AppTestFixture.PLACEHOLDER_OBJECTIVE_ID,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}
