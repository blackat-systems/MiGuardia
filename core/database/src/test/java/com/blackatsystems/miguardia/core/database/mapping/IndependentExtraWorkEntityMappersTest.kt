package com.blackatsystems.miguardia.core.database.mapping

import com.blackatsystems.miguardia.core.database.entity.IndependentExtraWorkRecordEntity
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSnapshot
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IndependentExtraWorkEntityMappersTest {
    @Test
    fun roundTripPreservesEveryIdentityIntervalAndHistoricalSnapshot() {
        val record = record()

        assertEquals(record, record.toEntity().toDomainIndependentExtra())
    }

    @Test
    fun invalidStoredIntervalProducesControlledLocalDataError() {
        val entity = record().toEntity().copy(endEpochMillis = START.toEpochMilli())

        assertThrows(InvalidLocalDataException::class.java) {
            entity.toDomainIndependentExtra()
        }
    }

    @Test
    fun unknownStoredSectorProducesControlledLocalDataError() {
        val entity: IndependentExtraWorkRecordEntity = record().toEntity().copy(sector = "HEALTH")

        assertThrows(InvalidLocalDataException::class.java) {
            entity.toDomainIndependentExtra()
        }
    }

    private fun record() = IndependentExtraWorkRecord(
        id = uuid(1),
        timelineId = uuid(2),
        sector = WorkSector.MEDICINE,
        configurationRevisionId = uuid(3),
        workPlaceId = uuid(4),
        objectiveId = uuid(5),
        workTypeId = uuid(6),
        templateId = null,
        extraWorkClassId = uuid(7),
        ownerLocalDate = LocalDate.of(2026, 8, 24),
        zoneId = ZoneOffset.UTC,
        start = START,
        end = END,
        snapshot = IndependentExtraWorkSnapshot(
            workPlaceName = "Hospital Central",
            workPlaceAbbreviation = "HCE",
            workPlaceAddress = "Calle 1",
            workTypeName = "Guardia extra",
            workTypeBehavior = WorkTypeBehavior.ACTIVE_WORK,
            colorArgb = 0xFF112233.toInt(),
            position = "Puesto 2",
            className = "Servicio extraordinario",
            helpsMeetHoursReference = false,
            showDedicatedSummary = true,
        ),
        createdAt = CREATED,
        updatedAt = CREATED.plusMillis(1),
    )

    private companion object {
        val START: Instant = Instant.parse("2026-08-24T20:00:00Z")
        val END: Instant = Instant.parse("2026-08-26T08:00:00Z")
        val CREATED: Instant = Instant.parse("2026-08-26T12:00:00Z")
        fun uuid(value: Int): UUID = UUID.fromString("93000000-0000-0000-0000-${value.toString().padStart(12, '0')}")
    }
}
