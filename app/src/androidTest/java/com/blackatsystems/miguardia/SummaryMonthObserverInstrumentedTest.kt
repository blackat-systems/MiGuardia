package com.blackatsystems.miguardia

import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.ui.summary.SummaryMonthObserver
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
import org.junit.Test

class SummaryMonthObserverInstrumentedTest {
    @Test
    fun combinesExistingRoomFlowsAndReactsToChanges() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "summary-observer-${UUID.randomUUID()}.db"
        val dataStore = LocalDataStore.create(context, databaseName)
        try {
            val observer = SummaryMonthObserver(
                dataStore.shifts,
                dataStore.explicitDayStatuses,
                dataStore.medicalLeaves,
                dataStore.holidays,
                dataStore.vacations,
            )
            val month = YearMonth.of(2026, 8)
            val update = async {
                withTimeout(5_000) {
                    observer.observe(month).first {
                        it.shifts.size == 1 && it.explicitStatuses.size == 1 &&
                            it.medicalLeaves.size == 1 && it.vacations.size == 1
                    }
                }
            }

            dataStore.shifts.insert(shift(month.atDay(31)))
            dataStore.explicitDayStatuses.set(month.atDay(2), ExplicitDayStatusType.DAY_OFF)
            dataStore.medicalLeaves.create(
                MedicalLeave(
                    id = UUID.fromString("30000000-0000-0000-0000-000000000002"),
                    startDate = month.atDay(30),
                    endDateInclusive = month.plusMonths(1).atDay(2),
                    privateNote = "Nota ficticia privada",
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                ),
            )
            dataStore.vacations.insert(
                Vacation(
                    id = UUID.fromString("30000000-0000-0000-0000-000000000003"),
                    startDate = month.atDay(10),
                    endDateInclusive = month.atDay(12),
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                ),
            )

            val data = update.await()
            assertEquals(month.atDay(31), data.shifts.single().localStartDate)
            assertEquals(month.atDay(2), data.explicitStatuses.single().date)
            assertEquals(1, data.medicalLeaves.size)
            assertEquals(month.atDay(10), data.vacations.single().startDate)
        } finally {
            dataStore.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun shift(date: LocalDate): Shift {
        val startTime = LocalTime.of(19, 0)
        val endTime = LocalTime.of(7, 0)
        val start = ZonedDateTime.of(date, startTime, AppDefaults.zoneId())
        val end = ZonedDateTime.of(date.plusDays(1), endTime, AppDefaults.zoneId())
        return Shift(
            id = UUID.fromString("30000000-0000-0000-0000-000000000001"),
            startAt = start.toInstant(),
            endAt = end.toInstant(),
            zoneId = AppDefaults.zoneId(),
            localStartDate = date,
            objectiveNameSnapshot = "Objetivo ficticio",
            objectiveAbbreviationSnapshot = "PRB",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = startTime,
            endTimeSnapshot = endTime,
            colorArgbSnapshot = 0xFF336699.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = null,
            sourceScheduleCombinationId = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}
