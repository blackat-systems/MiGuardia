package com.blackatsystems.miguardia.core.domain.work

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkConfigurationHistoryTest {
    @Test
    fun historyRequiresAtLeastOneV2Revision() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkConfigurationHistory(
                timeline = EffectiveDateTimeline(TIMELINE_ID, emptyList()),
                perPeriodHoursValues = PerPeriodHoursValues(emptyList()),
            )
        }
    }

    @Test
    fun pendingSetupIsAValidInitialReference() {
        val initial = revision(REVISION_1_ID, EFFECTIVE_DATE, HoursReference.PendingSetup)
        val history = history(listOf(initial))

        assertEquals(initial.value, history.timeline.valueAt(EFFECTIVE_DATE))
    }

    @Test
    fun referencedPerPeriodValuesAreAcceptedAcrossRevisions() {
        val monthly = HoursReference.PerPeriod(DEFINITION_1_ID, HoursPeriod.Monthly)
        val weekly = HoursReference.PerPeriod(DEFINITION_2_ID, HoursPeriod.Weekly(DayOfWeek.THURSDAY))
        val monthlyEntry = entry(ENTRY_1_ID, monthly, EFFECTIVE_DATE)
        val weeklyEntry = entry(ENTRY_2_ID, weekly, EFFECTIVE_DATE.plusMonths(1))
        val history = history(
            revisions = listOf(
                revision(REVISION_1_ID, EFFECTIVE_DATE, monthly),
                revision(REVISION_2_ID, EFFECTIVE_DATE.plusMonths(1), weekly),
            ),
            entries = listOf(monthlyEntry, weeklyEntry),
        )

        assertEquals(PerPeriodHoursLookup.Defined(monthlyEntry), history.perPeriodHoursValues.valueFor(monthlyEntry.key))
        assertEquals(PerPeriodHoursLookup.Defined(weeklyEntry), history.perPeriodHoursValues.valueFor(weeklyEntry.key))
    }

    @Test
    fun definitionCannotChangeItsPeriodPattern() {
        val monthly = HoursReference.PerPeriod(DEFINITION_1_ID, HoursPeriod.Monthly)
        val weekly = HoursReference.PerPeriod(DEFINITION_1_ID, HoursPeriod.Weekly(DayOfWeek.MONDAY))

        assertThrows(IllegalArgumentException::class.java) {
            history(
                listOf(
                    revision(REVISION_1_ID, EFFECTIVE_DATE, monthly),
                    revision(REVISION_2_ID, EFFECTIVE_DATE.plusDays(1), weekly),
                ),
            )
        }
    }

    @Test
    fun periodValueRequiresAReferencedDefinitionAndMatchingPattern() {
        val monthly = HoursReference.PerPeriod(DEFINITION_1_ID, HoursPeriod.Monthly)
        val weeklyWithSameId = HoursReference.PerPeriod(DEFINITION_1_ID, HoursPeriod.Weekly(DayOfWeek.MONDAY))

        assertThrows(IllegalArgumentException::class.java) {
            history(
                listOf(revision(REVISION_1_ID, EFFECTIVE_DATE, HoursReference.NotUsed)),
                listOf(entry(ENTRY_1_ID, monthly, EFFECTIVE_DATE)),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            history(
                listOf(revision(REVISION_1_ID, EFFECTIVE_DATE, monthly)),
                listOf(entry(ENTRY_1_ID, weeklyWithSameId, EFFECTIVE_DATE)),
            )
        }
    }

    private fun history(
        revisions: List<EffectiveRevision<WorkConfiguration>>,
        entries: List<PerPeriodHoursEntry> = emptyList(),
    ) = WorkConfigurationHistory(
        timeline = EffectiveDateTimeline(TIMELINE_ID, revisions),
        perPeriodHoursValues = PerPeriodHoursValues(entries),
    )

    private fun revision(id: UUID, date: LocalDate, hours: HoursReference) = EffectiveRevision(
        id = id,
        effectiveFrom = date,
        value = WorkConfiguration(
            WorkSector.PRIVATE_SECURITY,
            hours,
            availabilityLabel = null,
            hoursReferenceStartedOn = date.takeIf { hours.requiresStartedOnMarker },
        ),
    )

    private fun entry(id: UUID, reference: HoursReference.PerPeriod, date: LocalDate) = PerPeriodHoursEntry(
        id = id,
        key = reference.keyContaining(date),
        requiredMinutes = PositiveMinutes(9_600),
    )

    private companion object {
        val EFFECTIVE_DATE: LocalDate = LocalDate.of(2026, 8, 21)
        val TIMELINE_ID: UUID = UUID.fromString("73000000-0000-0000-0000-000000000001")
        val REVISION_1_ID: UUID = UUID.fromString("73000000-0000-0000-0000-000000000002")
        val REVISION_2_ID: UUID = UUID.fromString("73000000-0000-0000-0000-000000000003")
        val DEFINITION_1_ID: UUID = UUID.fromString("73000000-0000-0000-0000-000000000004")
        val DEFINITION_2_ID: UUID = UUID.fromString("73000000-0000-0000-0000-000000000005")
        val ENTRY_1_ID: UUID = UUID.fromString("73000000-0000-0000-0000-000000000006")
        val ENTRY_2_ID: UUID = UUID.fromString("73000000-0000-0000-0000-000000000007")
    }
}
