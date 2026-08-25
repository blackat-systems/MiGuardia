package com.blackatsystems.miguardia.core.database.mapping

import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursPeriod
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PositiveMinutes
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkConfigurationReferenceMarkerMappingTest {
    @Test
    fun fixedAndUnknownWithPeriodPersistTheirExplicitStart() {
        val fixed = revision(
            HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(9_600)),
            DATE,
        ).toEntity(TIMELINE_ID)
        val unknown = revision(
            HoursReference.Unknown(HoursPeriod.Weekly(java.time.DayOfWeek.THURSDAY)),
            DATE.plusDays(1),
        ).toEntity(TIMELINE_ID)

        assertEquals(DATE.toString(), fixed.revision.hoursReferenceStartedOn)
        assertEquals(DATE.plusDays(1).toString(), unknown.revision.hoursReferenceStartedOn)
    }

    @Test
    fun pendingAndNotUsedPersistNoFalseStart() {
        assertNull(revision(HoursReference.PendingSetup, null).toEntity(TIMELINE_ID).revision.hoursReferenceStartedOn)
        assertNull(revision(HoursReference.NotUsed, null).toEntity(TIMELINE_ID).revision.hoursReferenceStartedOn)
    }

    private fun revision(reference: HoursReference, startedOn: LocalDate?) = EffectiveRevision(
        id = UUID.randomUUID(),
        effectiveFrom = DATE,
        value = WorkConfiguration(
            WorkSector.POLICE,
            reference,
            null,
            hoursReferenceStartedOn = startedOn,
        ),
    )

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 25)
        val TIMELINE_ID: UUID = UUID.fromString("94000000-0000-0000-0000-000000000001")
    }
}
