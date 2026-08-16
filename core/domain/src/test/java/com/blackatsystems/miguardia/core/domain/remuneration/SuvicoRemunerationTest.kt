package com.blackatsystems.miguardia.core.domain.remuneration

import java.math.BigDecimal
import java.time.Duration
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuvicoRemunerationTest {
    @Test
    fun salaryScalesMatchTheSixSuvicoImages() {
        val expectedTotals = mapOf(
            7 to "1706800.00",
            8 to "1744800.00",
            9 to "1791600.00",
            10 to "1827200.00",
            11 to "1864000.00",
            12 to "1930000.00",
        )

        expectedTotals.forEach { (month, expected) ->
            val scale = requireNotNull(SuvicoSalaryScales.forMonth(YearMonth.of(2026, month)))
            val estimate = estimateSuvicoRemuneration(
                scale = scale,
                seniorityYears = 0,
                projectedNightHours = Duration.ZERO,
                projectedHolidayHours = Duration.ZERO,
                projectedOvertimeHours = Duration.ZERO,
            )
            assertMoney(expected, estimate.fixedGrossAmount)
        }
        assertNull(SuvicoSalaryScales.forMonth(YearMonth.of(2027, 1)))
    }

    @Test
    fun seniorityUsesThePublishedTableAndOnePointForLaterYears() {
        assertMoney("0.00", suvicoSeniorityPercentage(0))
        assertMoney("2.00", suvicoSeniorityPercentage(1))
        assertMoney("11.50", suvicoSeniorityPercentage(6))
        assertMoney("27.50", suvicoSeniorityPercentage(20))
        assertMoney("28.50", suvicoSeniorityPercentage(21))
        assertMoney("37.50", suvicoSeniorityPercentage(30))
    }

    @Test
    fun augustEstimateAddsSeniorityNightHolidayAndShowsBothExtraRates() {
        val estimate = estimateSuvicoRemuneration(
            scale = requireNotNull(SuvicoSalaryScales.forMonth(YearMonth.of(2026, 8))),
            seniorityYears = 5,
            projectedNightHours = Duration.ofHours(10),
            projectedHolidayHours = Duration.ofHours(8),
            projectedOvertimeHours = Duration.ofHours(4),
        )

        assertMoney("102030.00", estimate.seniorityAmount)
        assertMoney("6511.65", estimate.hourlyValue)
        assertMoney("11223.30", estimate.nightAdditional)
        assertMoney("104186.40", estimate.holidayAdditional)
        assertMoney("39069.90", estimate.overtimeAtFiftyPercent)
        assertMoney("52093.20", estimate.overtimeAtOneHundredPercent)
        assertMoney("2001309.60", estimate.estimatedGrossAtFiftyPercent)
        assertMoney("2014332.90", estimate.estimatedGrossAtOneHundredPercent)
    }

    private fun assertMoney(expected: String, actual: BigDecimal) {
        assertEquals(0, expected.toBigDecimal().compareTo(actual))
    }
}
