package com.blackatsystems.miguardia.stage12

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Stage12OwnedWorkFamilyOracleTest {
    private val march = LocalDate.of(2027, 3, 1)
    private val zone = ZoneId.of("America/Argentina/Cordoba")

    @Test fun weekdayAndDailyOraclesHaveLiteralInclusiveLists() {
        assertEquals(listOf(1, 4, 8, 11).map(march::withDayOfMonth),
            Stage12OwnedWorkFamilyOracle.dates(Stage12OwnedWorkFamilyOracle.Pattern.WEEKDAYS, march, march.plusDays(10)))
        assertEquals(listOf(1, 4, 7, 10).map(march::withDayOfMonth),
            Stage12OwnedWorkFamilyOracle.dates(Stage12OwnedWorkFamilyOracle.Pattern.EVERY_N_DAYS, march, march.plusDays(10), interval = 3))
    }

    @Test fun weeklyOracleUsesTheStartDateAndMonthlyUsesLastFriday() {
        assertEquals(listOf(1, 15, 29).map(march::withDayOfMonth),
            Stage12OwnedWorkFamilyOracle.dates(Stage12OwnedWorkFamilyOracle.Pattern.EVERY_N_WEEKS,
                march, march.plusDays(30), interval = 2))
        assertEquals(listOf(LocalDate.of(2027, 3, 26), LocalDate.of(2027, 4, 30), LocalDate.of(2027, 5, 28)),
            Stage12OwnedWorkFamilyOracle.dates(Stage12OwnedWorkFamilyOracle.Pattern.MONTHLY,
                march, LocalDate.of(2027, 5, 31), monthlyDay = DayOfWeek.FRIDAY))
    }

    @Test fun wholeMinuteAndCivilDateOraclesDoNotConfuseTwentyFiveHoursAndThreeDays() {
        val owner = LocalDate.of(2026, 10, 23)
        assertEquals(1500L, Stage12OwnedWorkFamilyOracle.minutes(owner, LocalTime.of(8, 0), owner.plusDays(1), LocalTime.of(9, 0), zone))
        assertEquals(4380L, Stage12OwnedWorkFamilyOracle.minutes(owner, LocalTime.of(8, 0), owner.plusDays(3), LocalTime.of(9, 0), zone))
        assertEquals(1L, Stage12OwnedWorkFamilyOracle.civilDays(owner, owner.plusDays(1)))
        assertEquals(3L, Stage12OwnedWorkFamilyOracle.civilDays(owner, owner.plusDays(3)))
        assertEquals(60L, Stage12OwnedWorkFamilyOracle.minutes(owner, LocalTime.of(8, 0), owner, LocalTime.of(9, 0), zone))
    }

    @Test fun weeklyIntervalRetainsAnyExplicitStartWeekday() {
        assertEquals(listOf(2, 16, 30).map(march::withDayOfMonth),
            Stage12OwnedWorkFamilyOracle.dates(Stage12OwnedWorkFamilyOracle.Pattern.EVERY_N_WEEKS,
                march.plusDays(1), march.plusDays(30), interval = 2))
    }

    @Test fun invalidBoundsIntervalsAndNonpositiveDurationsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Stage12OwnedWorkFamilyOracle.dates(Stage12OwnedWorkFamilyOracle.Pattern.EVERY_N_DAYS, march, march.plusDays(2), interval = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Stage12OwnedWorkFamilyOracle.dates(Stage12OwnedWorkFamilyOracle.Pattern.WEEKDAYS, march, march.minusDays(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Stage12OwnedWorkFamilyOracle.minutes(march, LocalTime.NOON, march, LocalTime.NOON, zone)
        }
    }
}
