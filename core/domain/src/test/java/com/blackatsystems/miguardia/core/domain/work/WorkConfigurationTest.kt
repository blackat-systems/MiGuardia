package com.blackatsystems.miguardia.core.domain.work

import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkConfigurationTest {
    @Test
    fun sectorCatalogAndSuggestedVocabularyAreExact() {
        assertEquals(
            listOf("Vigilancia privada", "Policía", "Enfermería", "Medicina"),
            WorkSector.entries.map { it.displayName },
        )
        assertEquals(
            listOf(
                "Objetivo" to "Guardia",
                "Dependencia o lugar de servicio" to "Guardia",
                "Institución o servicio" to "Turno",
                "Hospital, clínica, consultorio o servicio" to "Jornada",
            ),
            WorkSector.entries.map {
                it.suggestedVocabulary.placeLabel to it.suggestedVocabulary.shiftLabel
            },
        )
    }

    @Test
    fun dateBeforeEveryRevisionHasNoApplicableValue() {
        val timeline = timeline(
            revision(DATE, "vigente"),
        )

        assertNull(timeline.valueAt(DATE.minusDays(1)))
    }

    @Test
    fun exactBetweenAndAfterDatesResolveTheLatestApplicableRevision() {
        val firstDate = LocalDate.of(2026, 8, 10)
        val secondDate = LocalDate.of(2026, 8, 20)
        val timeline = timeline(
            revision(firstDate, "primera", REVISION_1_ID),
            revision(secondDate, "segunda", REVISION_2_ID),
        )

        assertEquals("primera", timeline.valueAt(firstDate))
        assertEquals("primera", timeline.valueAt(secondDate.minusDays(1)))
        assertEquals("segunda", timeline.valueAt(secondDate))
        assertEquals("segunda", timeline.valueAt(secondDate.plusYears(10)))
    }

    @Test
    fun unorderedInputIsDefensivelyCopiedAndResolvedDeterministically() {
        val source = mutableListOf(
            revision(LocalDate.of(2027, 1, 1), "nueva", REVISION_2_ID),
            revision(LocalDate.of(2026, 12, 31), "anterior", REVISION_1_ID),
        )
        val timeline = EffectiveDateTimeline(TIMELINE_ID, source)
        source.clear()

        assertEquals(
            listOf(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1)),
            timeline.revisions.map { it.effectiveFrom },
        )
        assertEquals("anterior", timeline.valueAt(LocalDate.of(2026, 12, 31)))
        assertEquals("nueva", timeline.valueAt(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun duplicateEffectiveDateIsRejectedEvenWithDifferentIds() {
        assertThrows(IllegalArgumentException::class.java) {
            timeline(
                revision(DATE, "primera", REVISION_1_ID),
                revision(DATE, "segunda", REVISION_2_ID),
            )
        }
    }

    @Test
    fun duplicateRevisionIdIsRejectedEvenOnDifferentDates() {
        assertThrows(IllegalArgumentException::class.java) {
            timeline(
                revision(DATE, "primera", REVISION_1_ID),
                revision(DATE.plusDays(1), "segunda", REVISION_1_ID),
            )
        }
    }

    @Test
    fun emptyTimelineIsAValidUnconfiguredState() {
        val timeline = EffectiveDateTimeline<String>(TIMELINE_ID, emptyList())

        assertEquals(TIMELINE_ID, timeline.id)
        assertEquals(emptyList<EffectiveRevision<String>>(), timeline.revisions)
        assertNull(timeline.valueAt(DATE))
    }

    @Test
    fun exposedRevisionsCannotMutateTheTimelineThroughACast() {
        val timeline = timeline(revision(DATE, "vigente"))

        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (timeline.revisions as MutableList<EffectiveRevision<String>>).clear()
        }
        assertEquals("vigente", timeline.valueAt(DATE))
        assertEquals(1, timeline.revisions.size)
    }

    private fun timeline(vararg revisions: EffectiveRevision<String>) =
        EffectiveDateTimeline(TIMELINE_ID, revisions.asList())

    private fun revision(
        date: LocalDate,
        value: String,
        id: UUID = REVISION_1_ID,
    ) = EffectiveRevision(id = id, effectiveFrom = date, value = value)

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 21)
        val TIMELINE_ID: UUID = UUID.fromString("71000000-0000-0000-0000-000000000001")
        val REVISION_1_ID: UUID = UUID.fromString("71000000-0000-0000-0000-000000000002")
        val REVISION_2_ID: UUID = UUID.fromString("71000000-0000-0000-0000-000000000003")
    }
}
