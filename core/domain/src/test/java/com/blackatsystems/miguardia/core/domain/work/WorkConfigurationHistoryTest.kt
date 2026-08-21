package com.blackatsystems.miguardia.core.domain.work

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkConfigurationHistoryTest {
    @Test
    fun migratedV1MayStartWithoutRevisionsOrPeriodValues() {
        val history = WorkConfigurationHistory(
            origin = WorkConfigurationOrigin.MIGRATED_V1,
            timeline = EffectiveDateTimeline(TIMELINE_ID, emptyList()),
            perPeriodHoursValues = PerPeriodHoursValues(emptyList()),
        )

        assertEquals(WorkConfigurationOrigin.MIGRATED_V1, history.origin)
        assertTrue(history.timeline.revisions.isEmpty())
        assertTrue(history.perPeriodHoursValues.entries.isEmpty())
        assertNull(history.timeline.valueAt(EFFECTIVE_DATE))
    }

    @Test
    fun newV2RequiresAtLeastOneRevision() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkConfigurationHistory(
                origin = WorkConfigurationOrigin.NEW_V2,
                timeline = EffectiveDateTimeline(TIMELINE_ID, emptyList()),
                perPeriodHoursValues = PerPeriodHoursValues(emptyList()),
            )
        }
    }

    @Test
    fun newV2AcceptsPendingSetupAsItsInitialNeutralReference() {
        val initial = revision(
            id = REVISION_1_ID,
            date = EFFECTIVE_DATE,
            hoursReference = HoursReference.PendingSetup,
        )
        val history = history(
            origin = WorkConfigurationOrigin.NEW_V2,
            revisions = listOf(initial),
        )

        assertEquals(initial.value, history.timeline.valueAt(EFFECTIVE_DATE))
        assertEquals(HoursReference.PendingSetup, initial.value.hoursReference)
    }

    @Test
    fun migratedV1MayReceiveItsFirstV2RevisionLater() {
        val firstV2Revision = revision(
            id = REVISION_1_ID,
            date = EFFECTIVE_DATE,
            hoursReference = HoursReference.NotUsed,
        )
        val history = history(
            origin = WorkConfigurationOrigin.MIGRATED_V1,
            revisions = listOf(firstV2Revision),
        )

        assertNull(history.timeline.valueAt(EFFECTIVE_DATE.minusDays(1)))
        assertEquals(firstV2Revision.value, history.timeline.valueAt(EFFECTIVE_DATE))
    }

    @Test
    fun referencedPerPeriodValuesAreAcceptedAcrossHistoricalRevisions() {
        val firstDefinition = HoursReference.PerPeriod(
            definitionId = DEFINITION_1_ID,
            period = HoursPeriod.Monthly,
        )
        val secondDefinition = HoursReference.PerPeriod(
            definitionId = DEFINITION_2_ID,
            period = HoursPeriod.Weekly(DayOfWeek.THURSDAY),
        )
        val firstEntry = entry(
            id = ENTRY_1_ID,
            reference = firstDefinition,
            date = EFFECTIVE_DATE,
        )
        val secondEntry = entry(
            id = ENTRY_2_ID,
            reference = secondDefinition,
            date = EFFECTIVE_DATE.plusMonths(1),
        )
        val fixedReference = HoursReference.Fixed(
            period = HoursPeriod.Monthly,
            requiredMinutes = PositiveMinutes(9_600),
        )

        val history = history(
            origin = WorkConfigurationOrigin.NEW_V2,
            revisions = listOf(
                revision(REVISION_1_ID, EFFECTIVE_DATE, firstDefinition),
                revision(REVISION_2_ID, EFFECTIVE_DATE.plusMonths(1), fixedReference),
                revision(REVISION_3_ID, EFFECTIVE_DATE.plusMonths(2), secondDefinition),
            ),
            entries = listOf(firstEntry, secondEntry),
        )

        assertEquals(
            PerPeriodHoursLookup.Defined(firstEntry),
            history.perPeriodHoursValues.valueFor(firstEntry.key),
        )
        assertEquals(
            PerPeriodHoursLookup.Defined(secondEntry),
            history.perPeriodHoursValues.valueFor(secondEntry.key),
        )
    }

    @Test
    fun definitionIdCannotChangeItsPatternBetweenRevisionsEvenWithoutValues() {
        val monthly = HoursReference.PerPeriod(DEFINITION_1_ID, HoursPeriod.Monthly)
        val weekly = HoursReference.PerPeriod(
            DEFINITION_1_ID,
            HoursPeriod.Weekly(DayOfWeek.MONDAY),
        )

        assertThrows(IllegalArgumentException::class.java) {
            history(
                origin = WorkConfigurationOrigin.NEW_V2,
                revisions = listOf(
                    revision(REVISION_1_ID, EFFECTIVE_DATE, monthly),
                    revision(REVISION_2_ID, EFFECTIVE_DATE.plusDays(1), weekly),
                ),
            )
        }
    }

    @Test
    fun definitionIdMayBeReferencedAgainWhenItsPatternRemainsTheSame() {
        val definition = HoursReference.PerPeriod(DEFINITION_1_ID, HoursPeriod.Monthly)
        val entry = entry(ENTRY_1_ID, definition, EFFECTIVE_DATE)

        val history = history(
            origin = WorkConfigurationOrigin.NEW_V2,
            revisions = listOf(
                revision(REVISION_1_ID, EFFECTIVE_DATE, definition),
                revision(REVISION_2_ID, EFFECTIVE_DATE.plusDays(1), HoursReference.NotUsed),
                revision(REVISION_3_ID, EFFECTIVE_DATE.plusDays(2), definition),
            ),
            entries = listOf(entry),
        )

        assertEquals(
            PerPeriodHoursLookup.Defined(entry),
            history.perPeriodHoursValues.valueFor(entry.key),
        )
    }

    @Test
    fun periodValueWithoutAReferencedDefinitionIsRejected() {
        val orphanDefinition = HoursReference.PerPeriod(
            definitionId = DEFINITION_1_ID,
            period = HoursPeriod.Monthly,
        )

        assertThrows(IllegalArgumentException::class.java) {
            history(
                origin = WorkConfigurationOrigin.NEW_V2,
                revisions = listOf(
                    revision(REVISION_1_ID, EFFECTIVE_DATE, HoursReference.NotUsed),
                ),
                entries = listOf(entry(ENTRY_1_ID, orphanDefinition, EFFECTIVE_DATE)),
            )
        }
    }

    @Test
    fun periodValueMustUseThePatternReferencedByItsDefinition() {
        val monthly = HoursReference.PerPeriod(DEFINITION_1_ID, HoursPeriod.Monthly)
        val weeklyWithSameId = HoursReference.PerPeriod(
            DEFINITION_1_ID,
            HoursPeriod.Weekly(DayOfWeek.MONDAY),
        )

        assertThrows(IllegalArgumentException::class.java) {
            history(
                origin = WorkConfigurationOrigin.NEW_V2,
                revisions = listOf(revision(REVISION_1_ID, EFFECTIVE_DATE, monthly)),
                entries = listOf(entry(ENTRY_1_ID, weeklyWithSameId, EFFECTIVE_DATE)),
            )
        }
    }

    private fun history(
        origin: WorkConfigurationOrigin,
        revisions: List<EffectiveRevision<WorkConfiguration>>,
        entries: List<PerPeriodHoursEntry> = emptyList(),
    ) = WorkConfigurationHistory(
        origin = origin,
        timeline = EffectiveDateTimeline(TIMELINE_ID, revisions),
        perPeriodHoursValues = PerPeriodHoursValues(entries),
    )

    private fun revision(
        id: UUID,
        date: LocalDate,
        hoursReference: HoursReference,
    ) = EffectiveRevision(
        id = id,
        effectiveFrom = date,
        value = WorkConfiguration(
            sector = WorkSector.PRIVATE_SECURITY,
            hoursReference = hoursReference,
            availabilityLabel = null,
        ),
    )

    private fun entry(
        id: UUID,
        reference: HoursReference.PerPeriod,
        date: LocalDate,
    ) = PerPeriodHoursEntry(
        id = id,
        key = reference.keyContaining(date),
        requiredMinutes = PositiveMinutes(9_600),
    )

    private companion object {
        val EFFECTIVE_DATE: LocalDate = LocalDate.of(2026, 8, 21)
        val TIMELINE_ID: UUID = UUID.fromString("73000000-0000-0000-0000-000000000001")
        val REVISION_1_ID: UUID = UUID.fromString("73000000-0000-0000-0000-000000000002")
        val REVISION_2_ID: UUID = UUID.fromString("73000000-0000-0000-0000-000000000003")
        val REVISION_3_ID: UUID = UUID.fromString("73000000-0000-0000-0000-000000000004")
        val DEFINITION_1_ID: UUID = UUID.fromString("73000000-0000-0000-0000-000000000005")
        val DEFINITION_2_ID: UUID = UUID.fromString("73000000-0000-0000-0000-000000000006")
        val ENTRY_1_ID: UUID = UUID.fromString("73000000-0000-0000-0000-000000000007")
        val ENTRY_2_ID: UUID = UUID.fromString("73000000-0000-0000-0000-000000000008")
    }
}
