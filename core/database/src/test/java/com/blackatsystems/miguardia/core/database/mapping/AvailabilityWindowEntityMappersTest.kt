package com.blackatsystems.miguardia.core.database.mapping

import com.blackatsystems.miguardia.core.database.entity.AvailabilityWindowEntity
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AvailabilityWindowEntityMappersTest {
    @Test
    fun exactRecordRoundTripsWithoutChangingHistoryOrZone() {
        val record = record()
        assertEquals(record, record.toAvailabilityEntity().toDomainAvailability())
    }

    @Test
    fun invalidSectorLabelAndIntervalAreRejectedAsInvalidLocalData() {
        val entity = record().toAvailabilityEntity()
        listOf(
            entity.copy(sector = "HEALTH"),
            entity.copy(labelSnapshot = "Disponibilidad"),
            entity.copy(endEpochMillis = entity.startEpochMillis),
        ).forEach { invalid ->
            assertThrows(InvalidLocalDataException::class.java) { invalid.toDomainAvailability() }
        }
    }

    private fun record() = AvailabilityWindowRecord(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        timelineId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        sector = WorkSector.NURSING,
        configurationRevisionId = UUID.fromString("00000000-0000-0000-0000-000000000003"),
        ownerLocalDate = LocalDate.of(2028, 2, 29),
        zoneId = ZoneId.of("America/Argentina/Cordoba"),
        start = Instant.parse("2028-03-01T02:00:00Z"),
        end = Instant.parse("2028-03-02T14:00:00Z"),
        labelSnapshot = "Disponible para llamado",
        createdAt = Instant.parse("2026-08-27T12:00:00Z"),
        updatedAt = Instant.parse("2026-08-27T12:00:00Z"),
    )
}
