package com.blackatsystems.miguardia.core.domain.work

import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkCategoriesTest {
    @Test
    fun regularWorkTypeKeepsStableIdAndNormalizesItsCustomName() {
        val type = RegularWorkType.create(REGULAR_ID, "  Consultorio  ")

        assertEquals(REGULAR_ID, type.id)
        assertEquals("Consultorio", type.name)
    }

    @Test
    fun namedCategoriesRejectBlankNamesAfterTrimming() {
        assertThrows(IllegalArgumentException::class.java) {
            RegularWorkType.create(REGULAR_ID, "   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExtraWorkClass.create(
                id = EXTRA_ID,
                timelineId = TIMELINE_ID,
                sector = WorkSector.PRIVATE_SECURITY,
                name = "\t",
                helpsMeetHoursReference = true,
                showDedicatedSummary = true,
                timestamp = NOW,
            )
        }
    }

    @Test
    fun eachExtraClassIndependentlyControlsFulfillmentAndSummaryBreakdown() {
        val combinations = listOf(
            false to false,
            false to true,
            true to false,
            true to true,
        )

        combinations.forEachIndexed { index, (helps, showDedicatedSummary) ->
            val id = UUID(0x7300000000000000L, index.toLong())
            val extra = ExtraWorkClass.create(
                id = id,
                timelineId = TIMELINE_ID,
                sector = WorkSector.NURSING,
                name = "Clase $index",
                helpsMeetHoursReference = helps,
                showDedicatedSummary = showDedicatedSummary,
                timestamp = NOW,
            )

            assertEquals(id, extra.id)
            assertEquals(TIMELINE_ID, extra.timelineId)
            assertEquals(WorkSector.NURSING, extra.sector)
            assertEquals(helps, extra.helpsMeetHoursReference)
            assertEquals(showDedicatedSummary, extra.showDedicatedSummary)
            assertEquals(true, extra.isActive)
        }
    }

    @Test
    fun extraClassNormalizesItsKeyAndAdvancesTimestampsWhenArchivedOrRenamed() {
        val original = ExtraWorkClass.create(
            id = EXTRA_ID,
            timelineId = TIMELINE_ID,
            sector = WorkSector.MEDICINE,
            name = "  Extensión\tde turno ",
            helpsMeetHoursReference = false,
            showDedicatedSummary = true,
            timestamp = NOW.plusNanos(456_789),
        )

        val archived = original.updated(
            name = " Servicio extra ",
            isActive = false,
            timestamp = NOW.plusSeconds(1),
        )

        assertEquals("Extensión de turno", original.name)
        assertEquals("EXTENSIÓN DE TURNO", original.normalizedNameKey)
        assertEquals(NOW, original.createdAt)
        assertEquals("Servicio extra", archived.name)
        assertEquals("SERVICIO EXTRA", archived.normalizedNameKey)
        assertEquals(false, archived.isActive)
        assertEquals(original.createdAt, archived.createdAt)
    }

    @Test
    fun availabilityIsOneConceptWithExactlyThreeVisibleLabels() {
        assertEquals(
            listOf("Guardia pasiva", "Disponible para llamado", "Retén"),
            AvailabilityLabel.entries.map { it.displayName },
        )
    }

    private companion object {
        val REGULAR_ID: UUID = UUID.fromString("73000000-0000-0000-0000-000000000001")
        val EXTRA_ID: UUID = UUID.fromString("73000000-0000-0000-0000-000000000002")
        val TIMELINE_ID: UUID = UUID.fromString("73000000-0000-0000-0000-000000000003")
        val NOW: Instant = Instant.parse("2026-08-25T12:00:00Z")
    }
}
