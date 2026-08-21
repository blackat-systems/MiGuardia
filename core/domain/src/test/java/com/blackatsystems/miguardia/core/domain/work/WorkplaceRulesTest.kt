package com.blackatsystems.miguardia.core.domain.work

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkplaceRulesTest {
    @Test
    fun disabledNightRuleCarriesNoScheduleOrSummaryFlags() {
        assertSame(NightHoursRule.Disabled, NightHoursRule.Disabled)
    }

    @Test
    fun definedNightRuleAllowsCrossingMidnightAndKeepsInformativeFlags() {
        val rule = NightHoursRule.Defined(
            startInclusive = LocalTime.of(22, 0),
            endExclusive = LocalTime.of(5, 30),
            differentTreatment = true,
            showDedicatedSummary = true,
        )

        assertEquals(LocalTime.of(22, 0), rule.startInclusive)
        assertEquals(LocalTime.of(5, 30), rule.endExclusive)
        assertTrue(rule.differentTreatment)
        assertTrue(rule.showDedicatedSummary)
    }

    @Test
    fun definedNightRuleAlsoAllowsADaytimeWindowAndMidnightBoundary() {
        val daytime = NightHoursRule.Defined(
            startInclusive = LocalTime.of(10, 0),
            endExclusive = LocalTime.of(14, 0),
            differentTreatment = false,
            showDedicatedSummary = true,
        )
        val midnight = NightHoursRule.Defined(
            startInclusive = LocalTime.MIDNIGHT,
            endExclusive = LocalTime.of(6, 0),
            differentTreatment = true,
            showDedicatedSummary = false,
        )

        assertEquals(LocalTime.of(10, 0), daytime.startInclusive)
        assertEquals(LocalTime.of(14, 0), daytime.endExclusive)
        assertEquals(LocalTime.MIDNIGHT, midnight.startInclusive)
        assertEquals(LocalTime.of(6, 0), midnight.endExclusive)
    }

    @Test
    fun equalOrNonMinuteNightBoundariesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            NightHoursRule.Defined(
                LocalTime.of(21, 0),
                LocalTime.of(21, 0),
                differentTreatment = false,
                showDedicatedSummary = false,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NightHoursRule.Defined(
                LocalTime.of(21, 0, 1),
                LocalTime.of(6, 0),
                differentTreatment = false,
                showDedicatedSummary = false,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NightHoursRule.Defined(
                LocalTime.of(21, 0),
                LocalTime.of(6, 0, 0, 1),
                differentTreatment = false,
                showDedicatedSummary = false,
            )
        }
    }

    @Test
    fun weekendCanBeNoneSaturdaySundayOrBothWithoutImpossibleFlags() {
        assertSame(WeekendRule.None, WeekendRule.None)
        assertTrue(WeekendDays.SATURDAY.includes(DayOfWeek.SATURDAY))
        assertFalse(WeekendDays.SATURDAY.includes(DayOfWeek.SUNDAY))
        assertTrue(WeekendDays.SUNDAY.includes(DayOfWeek.SUNDAY))
        assertFalse(WeekendDays.SUNDAY.includes(DayOfWeek.SATURDAY))
        assertTrue(WeekendDays.SATURDAY_AND_SUNDAY.includes(DayOfWeek.SATURDAY))
        assertTrue(WeekendDays.SATURDAY_AND_SUNDAY.includes(DayOfWeek.SUNDAY))
        assertFalse(WeekendDays.SATURDAY_AND_SUNDAY.includes(DayOfWeek.MONDAY))

        WeekendDays.entries.forEach { days ->
            val rule = WeekendRule.Defined(
                days = days,
                differentTreatment = true,
                showDedicatedSummary = false,
            )
            assertEquals(days, rule.days)
            assertTrue(rule.differentTreatment)
            assertFalse(rule.showDedicatedSummary)
        }
    }

    @Test
    fun workplaceRulesCanChangeByDateWithoutCombiningRulesWithShiftIntervals() {
        val oldRules = workplaceRules(NightHoursRule.Disabled, WeekendRule.None)
        val newRules = workplaceRules(
            NightHoursRule.Defined(
                LocalTime.of(20, 0),
                LocalTime.of(5, 0),
                differentTreatment = true,
                showDedicatedSummary = true,
            ),
            WeekendRule.Defined(
                WeekendDays.SUNDAY,
                differentTreatment = true,
                showDedicatedSummary = true,
            ),
        )
        val timeline = EffectiveDateTimeline(
            id = TIMELINE_ID,
            revisions = listOf(
                EffectiveRevision(REVISION_2_ID, CHANGE_DATE, newRules),
                EffectiveRevision(REVISION_1_ID, CHANGE_DATE.minusMonths(1), oldRules),
            ),
        )

        assertEquals(oldRules, timeline.valueAt(CHANGE_DATE.minusDays(1)))
        assertEquals(newRules, timeline.valueAt(CHANGE_DATE))
    }

    @Test
    fun separateWorkplaceTimelinesMayChangeOnTheSameDate() {
        val firstRules = workplaceRules(NightHoursRule.Disabled, WeekendRule.None)
        val secondRules = workplaceRules(
            NightHoursRule.Disabled,
            WeekendRule.Defined(
                WeekendDays.SATURDAY,
                differentTreatment = true,
                showDedicatedSummary = true,
            ),
        )
        val firstTimeline = EffectiveDateTimeline(
            id = TIMELINE_ID,
            revisions = listOf(EffectiveRevision(REVISION_1_ID, CHANGE_DATE, firstRules)),
        )
        val secondTimeline = EffectiveDateTimeline(
            id = OTHER_TIMELINE_ID,
            revisions = listOf(EffectiveRevision(REVISION_2_ID, CHANGE_DATE, secondRules)),
        )

        assertEquals(firstRules, firstTimeline.valueAt(CHANGE_DATE))
        assertEquals(secondRules, secondTimeline.valueAt(CHANGE_DATE))
    }

    @Test
    fun holidayRuleStoresOnlyInformativeTreatmentAndSummaryChoices() {
        val rule = HolidayRule(differentTreatment = true, showDedicatedSummary = false)

        assertTrue(rule.differentTreatment)
        assertFalse(rule.showDedicatedSummary)
    }

    private fun workplaceRules(
        night: NightHoursRule,
        weekend: WeekendRule,
    ) = WorkplaceRules(
        nightHours = night,
        weekend = weekend,
        holiday = HolidayRule(differentTreatment = false, showDedicatedSummary = false),
    )

    private companion object {
        val CHANGE_DATE: LocalDate = LocalDate.of(2026, 9, 15)
        val TIMELINE_ID: UUID = UUID.fromString("74000000-0000-0000-0000-000000000001")
        val OTHER_TIMELINE_ID: UUID = UUID.fromString("74000000-0000-0000-0000-000000000004")
        val REVISION_1_ID: UUID = UUID.fromString("74000000-0000-0000-0000-000000000002")
        val REVISION_2_ID: UUID = UUID.fromString("74000000-0000-0000-0000-000000000003")
    }
}
