package com.blackatsystems.miguardia.core.database.repository

import com.blackatsystems.miguardia.core.domain.work.*
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CalendarConfigurationTokenTest {
    private val id = UUID(0, 1)
    private val revisionId = UUID(0, 2)
    private fun history(sector: WorkSector = WorkSector.NURSING) = WorkConfigurationHistory(
        EffectiveDateTimeline(id, listOf(EffectiveRevision(revisionId, LocalDate.of(2026, 9, 1),
            WorkConfiguration(sector, HoursReference.PendingSetup, null)))), PerPeriodHoursValues(emptyList()))

    @Test fun distinctContainersOfTheSameConfigurationProduceTheSameToken() {
        val first = history()
        val second = history()
        assertNotEquals(first.toString(), second.toString()) // Demonstrates the old identity-based cause.
        assertEquals(calendarConfigurationToken(first), calendarConfigurationToken(second))
    }

    @Test fun changedConfigurationIsStillDetected() {
        assertNotEquals(calendarConfigurationToken(history()), calendarConfigurationToken(history(WorkSector.MEDICINE)))
        assertNotEquals(calendarConfigurationToken(null), calendarConfigurationToken(history()))
    }

    @Test fun changedPerPeriodValueIsStillDetectedWithoutContainerIdentity() {
        val date = LocalDate.of(2026, 9, 1)
        val reference = HoursReference.PerPeriod(UUID(0, 3), HoursPeriod.Monthly)
        fun history(minutes: Long) = WorkConfigurationHistory(
            EffectiveDateTimeline(id, listOf(EffectiveRevision(revisionId, date,
                WorkConfiguration(WorkSector.NURSING, reference, null, date)))),
            PerPeriodHoursValues(listOf(PerPeriodHoursEntry(UUID(0, 4), reference.keyContaining(date), PositiveMinutes(minutes)))))
        assertEquals(calendarConfigurationToken(history(60)), calendarConfigurationToken(history(60)))
        assertNotEquals(calendarConfigurationToken(history(60)), calendarConfigurationToken(history(120)))
    }
}
