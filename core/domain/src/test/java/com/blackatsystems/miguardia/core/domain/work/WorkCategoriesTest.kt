package com.blackatsystems.miguardia.core.domain.work

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
            ExtraWorkClass.create(EXTRA_ID, "\t", helpsMeetHoursReference = true, showDedicatedSummary = true)
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
                name = "Clase $index",
                helpsMeetHoursReference = helps,
                showDedicatedSummary = showDedicatedSummary,
            )

            assertEquals(id, extra.id)
            assertEquals(helps, extra.helpsMeetHoursReference)
            assertEquals(showDedicatedSummary, extra.showDedicatedSummary)
        }
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
    }
}
