package com.blackatsystems.miguardia.core.domain.work

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HoursReferenceTest {
    @Test
    fun monthlyWindowIsHalfOpenAndHandlesLeapFebruary() {
        val window = HoursPeriod.Monthly.windowContaining(LocalDate.of(2028, 2, 29))

        assertEquals(LocalDate.of(2028, 2, 1), window.startInclusive)
        assertEquals(LocalDate.of(2028, 3, 1), window.endExclusive)
        assertTrue(LocalDate.of(2028, 2, 29) in window)
        assertFalse(LocalDate.of(2028, 3, 1) in window)
    }

    @Test
    fun weeklyWindowSupportsEveryFirstDayWithoutChangingTheUiSuggestion() {
        val queriedDate = LocalDate.of(2026, 8, 19)

        DayOfWeek.entries.forEach { firstDay ->
            val window = HoursPeriod.Weekly(firstDay).windowContaining(queriedDate)
            assertEquals(firstDay, window.startInclusive.dayOfWeek)
            assertEquals(7L, Duration.between(
                window.startInclusive.atStartOfDay(),
                window.endExclusive.atStartOfDay(),
            ).toDays())
            assertTrue(queriedDate in window)
            assertFalse(window.endExclusive in window)
        }
        assertEquals(DayOfWeek.MONDAY, HoursPeriod.Weekly.suggestedFirstDay)
    }

    @Test
    fun fourteenTwentyOneAndTwentyEightDayCyclesReturnTheirContainingWindow() {
        listOf(1, 14, 21, 28).forEach { length ->
            val period = HoursPeriod.Cycle(ANCHOR, length)
            val offsetInsideCycle = minOf(3, length - 1)
            val date = ANCHOR.plusDays((length * 2 + offsetInsideCycle).toLong())
            val window = period.windowContaining(date)

            assertEquals(ANCHOR.plusDays((length * 2).toLong()), window.startInclusive)
            assertEquals(window.startInclusive.plusDays(length.toLong()), window.endExclusive)
            assertTrue(date in window)
        }
    }

    @Test
    fun cycleUsesFloorDivisionForDatesBeforeTheAnchor() {
        val period = HoursPeriod.Cycle(anchorDate = ANCHOR, lengthDays = 14)

        assertEquals(
            DateWindow(ANCHOR.minusDays(14), ANCHOR),
            period.windowContaining(ANCHOR.minusDays(1)),
        )
        assertEquals(
            DateWindow(ANCHOR.minusDays(14), ANCHOR),
            period.windowContaining(ANCHOR.minusDays(14)),
        )
        assertEquals(
            DateWindow(ANCHOR.minusDays(28), ANCHOR.minusDays(14)),
            period.windowContaining(ANCHOR.minusDays(15)),
        )
        assertEquals(
            DateWindow(ANCHOR, ANCHOR.plusDays(14)),
            period.windowContaining(ANCHOR),
        )
    }

    @Test
    fun invalidCycleLengthAndInvalidWindowAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            HoursPeriod.Cycle(ANCHOR, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HoursPeriod.Cycle(ANCHOR, -14)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DateWindow(ANCHOR, ANCHOR)
        }
    }

    @Test
    fun notUsedAndUnknownReferencesDoNotInventAZeroValue() {
        assertSame(HoursReference.NotUsed, HoursReference.NotUsed)
        assertEquals(null, HoursReference.Unknown().period)
        assertEquals(
            HoursPeriod.Monthly,
            HoursReference.Unknown(period = HoursPeriod.Monthly).period,
        )
    }

    @Test
    fun fixedReferenceAcceptsOnlyPositiveWholeMinutes() {
        val reference = HoursReference.Fixed(
            period = HoursPeriod.Monthly,
            requiredMinutes = PositiveMinutes.from(Duration.ofHours(160)),
        )

        assertEquals(9_600L, reference.requiredMinutes.value)
        assertEquals(Duration.ofHours(160), reference.requiredMinutes.toDuration())
        assertThrows(IllegalArgumentException::class.java) { PositiveMinutes(0) }
        assertThrows(IllegalArgumentException::class.java) { PositiveMinutes(-1) }
        assertThrows(IllegalArgumentException::class.java) {
            PositiveMinutes.from(Duration.ofSeconds(90))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PositiveMinutes.from(Duration.ofMinutes(1).plusNanos(1))
        }
        val maximumRepresentable = Long.MAX_VALUE / 60L
        assertEquals(
            maximumRepresentable,
            PositiveMinutes(maximumRepresentable).toDuration().toMinutes(),
        )
        assertThrows(IllegalArgumentException::class.java) {
            PositiveMinutes(maximumRepresentable + 1L)
        }
    }

    @Test
    fun perPeriodDefinitionAndValuesRemainSeparateAndMissingIsExplicit() {
        val definition = HoursReference.PerPeriod(DEFINITION_1_ID, HoursPeriod.Weekly(DayOfWeek.WEDNESDAY))
        val firstWindow = definition.period.windowContaining(LocalDate.of(2026, 8, 19))
        val nextWindow = definition.period.windowContaining(firstWindow.endExclusive)
        val firstKey = definition.keyFor(firstWindow)
        val nextKey = definition.keyContaining(nextWindow.startInclusive)
        val entry = PerPeriodHoursEntry(ENTRY_1_ID, firstKey, PositiveMinutes(2_400))
        val values = PerPeriodHoursValues(listOf(entry))

        assertEquals(DEFINITION_1_ID, firstKey.definitionId)
        assertEquals(PerPeriodHoursLookup.Defined(entry), values.valueFor(firstKey))
        assertSame(PerPeriodHoursLookup.Missing, values.valueFor(nextKey))
    }

    @Test
    fun exposedPeriodEntriesCannotMutateValidatedValuesThroughACast() {
        val definition = HoursReference.PerPeriod(DEFINITION_1_ID, HoursPeriod.Monthly)
        val entry = PerPeriodHoursEntry(
            ENTRY_1_ID,
            definition.keyContaining(ANCHOR),
            PositiveMinutes(1_000),
        )
        val values = PerPeriodHoursValues(listOf(entry))

        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (values.entries as MutableList<PerPeriodHoursEntry>).clear()
        }
        assertEquals(PerPeriodHoursLookup.Defined(entry), values.valueFor(entry.key))
        assertEquals(1, values.entries.size)
    }

    @Test
    fun duplicateValueForTheSameWindowIsRejectedRegardlessOfId() {
        val definition = HoursReference.PerPeriod(DEFINITION_1_ID, HoursPeriod.Monthly)
        val key = definition.keyContaining(ANCHOR)

        assertThrows(IllegalArgumentException::class.java) {
            PerPeriodHoursValues(
                listOf(
                    PerPeriodHoursEntry(ENTRY_1_ID, key, PositiveMinutes(1)),
                    PerPeriodHoursEntry(ENTRY_2_ID, key, PositiveMinutes(2)),
                ),
            )
        }
    }

    @Test
    fun duplicatePeriodValueIdIsRejectedAcrossDifferentKeys() {
        val definition = HoursReference.PerPeriod(DEFINITION_1_ID, HoursPeriod.Monthly)
        val firstKey = definition.keyContaining(ANCHOR)
        val nextDate = firstKey.window.endExclusive
        val nextKey = definition.keyContaining(nextDate)

        assertThrows(IllegalArgumentException::class.java) {
            PerPeriodHoursValues(
                listOf(
                    PerPeriodHoursEntry(ENTRY_1_ID, firstKey, PositiveMinutes(1)),
                    PerPeriodHoursEntry(ENTRY_1_ID, nextKey, PositiveMinutes(2)),
                ),
            )
        }
    }

    @Test
    fun equalDateWindowsFromDifferentPeriodDefinitionsDoNotCollide() {
        val monthly = HoursReference.PerPeriod(DEFINITION_1_ID, HoursPeriod.Monthly)
        val cycle = HoursReference.PerPeriod(
            DEFINITION_2_ID,
            HoursPeriod.Cycle(anchorDate = LocalDate.of(2026, 8, 1), lengthDays = 31),
        )
        val sharedWindow = monthly.period.windowContaining(ANCHOR)
        val monthlyKey = monthly.keyFor(sharedWindow)
        val cycleKey = cycle.keyFor(sharedWindow)
        val monthlyEntry = PerPeriodHoursEntry(ENTRY_1_ID, monthlyKey, PositiveMinutes(1_000))
        val cycleEntry = PerPeriodHoursEntry(ENTRY_2_ID, cycleKey, PositiveMinutes(2_000))
        val values = PerPeriodHoursValues(listOf(monthlyEntry, cycleEntry))

        assertEquals(PerPeriodHoursLookup.Defined(monthlyEntry), values.valueFor(monthlyKey))
        assertEquals(PerPeriodHoursLookup.Defined(cycleEntry), values.valueFor(cycleKey))
    }

    @Test
    fun separateDefinitionIdsWithTheSamePatternAndWindowCanCoexist() {
        val firstDefinition = HoursReference.PerPeriod(DEFINITION_1_ID, HoursPeriod.Monthly)
        val secondDefinition = HoursReference.PerPeriod(DEFINITION_2_ID, HoursPeriod.Monthly)
        val firstKey = firstDefinition.keyContaining(ANCHOR)
        val secondKey = secondDefinition.keyContaining(ANCHOR)
        val firstEntry = PerPeriodHoursEntry(ENTRY_1_ID, firstKey, PositiveMinutes(1_000))
        val secondEntry = PerPeriodHoursEntry(ENTRY_2_ID, secondKey, PositiveMinutes(2_000))
        val values = PerPeriodHoursValues(listOf(firstEntry, secondEntry))

        assertEquals(firstKey.window, secondKey.window)
        assertEquals(firstKey.period, secondKey.period)
        assertEquals(PerPeriodHoursLookup.Defined(firstEntry), values.valueFor(firstKey))
        assertEquals(PerPeriodHoursLookup.Defined(secondEntry), values.valueFor(secondKey))
    }

    @Test
    fun periodValueRejectsAWindowThatDoesNotBelongToItsDefinition() {
        val monthly = HoursReference.PerPeriod(DEFINITION_1_ID, HoursPeriod.Monthly)
        val weeklyWindow = HoursPeriod.Weekly(DayOfWeek.MONDAY).windowContaining(ANCHOR)

        assertThrows(IllegalArgumentException::class.java) {
            monthly.keyFor(weeklyWindow)
        }
    }

    @Test
    fun oneDefinitionIdCannotChangeItsPeriodPatternAcrossValues() {
        val monthly = HoursReference.PerPeriod(DEFINITION_1_ID, HoursPeriod.Monthly)
        val cycle = HoursReference.PerPeriod(
            DEFINITION_1_ID,
            HoursPeriod.Cycle(anchorDate = LocalDate.of(2026, 9, 1), lengthDays = 30),
        )
        val monthlyWindow = monthly.period.windowContaining(ANCHOR)
        val cycleWindow = cycle.period.windowContaining(LocalDate.of(2026, 9, 10))

        assertThrows(IllegalArgumentException::class.java) {
            PerPeriodHoursValues(
                listOf(
                    PerPeriodHoursEntry(ENTRY_1_ID, monthly.keyFor(monthlyWindow), PositiveMinutes(1)),
                    PerPeriodHoursEntry(ENTRY_2_ID, cycle.keyFor(cycleWindow), PositiveMinutes(2)),
                ),
            )
        }
    }

    @Test
    fun lookupRejectsAKeyThatReusesDefinitionIdWithAnotherPattern() {
        val canonical = HoursReference.PerPeriod(DEFINITION_1_ID, HoursPeriod.Monthly)
        val storedEntry = PerPeriodHoursEntry(
            ENTRY_1_ID,
            canonical.keyContaining(ANCHOR),
            PositiveMinutes(1_000),
        )
        val values = PerPeriodHoursValues(listOf(storedEntry))
        val inconsistent = HoursReference.PerPeriod(
            DEFINITION_1_ID,
            HoursPeriod.Weekly(DayOfWeek.MONDAY),
        )

        assertThrows(IllegalArgumentException::class.java) {
            values.valueFor(inconsistent.keyContaining(ANCHOR))
        }
    }

    private companion object {
        val ANCHOR: LocalDate = LocalDate.of(2026, 8, 10)
        val ENTRY_1_ID: UUID = UUID.fromString("72000000-0000-0000-0000-000000000001")
        val ENTRY_2_ID: UUID = UUID.fromString("72000000-0000-0000-0000-000000000002")
        val DEFINITION_1_ID: UUID = UUID.fromString("72000000-0000-0000-0000-000000000003")
        val DEFINITION_2_ID: UUID = UUID.fromString("72000000-0000-0000-0000-000000000004")
    }
}
