package com.blackatsystems.miguardia.core.domain.remuneration

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.YearMonth

data class SuvicoSalaryScale(
    val month: YearMonth,
    val basicSalary: BigDecimal,
    val presentism: BigDecimal,
    val nonRemunerativeAmount: BigDecimal,
    val viatics: BigDecimal,
    val sourceFileName: String,
)

data class SuvicoRemunerationEstimate(
    val scale: SuvicoSalaryScale,
    val seniorityYears: Int,
    val seniorityPercentage: BigDecimal,
    val seniorityAmount: BigDecimal,
    val remunerativeBase: BigDecimal,
    val fixedGrossAmount: BigDecimal,
    val hourlyValue: BigDecimal,
    val projectedNightHours: Duration,
    val projectedHolidayHours: Duration,
    val projectedOvertimeHours: Duration,
    val nightAdditional: BigDecimal,
    val holidayAdditional: BigDecimal,
    val overtimeAtFiftyPercent: BigDecimal,
    val overtimeAtOneHundredPercent: BigDecimal,
    val estimatedGrossAtFiftyPercent: BigDecimal,
    val estimatedGrossAtOneHundredPercent: BigDecimal,
)

object SuvicoSalaryScales {
    private val scales = listOf(
        scale(7, "1001300", "180000", "20000", "505500", "WhatsApp Image 2026-08-13 at 10.07.56.jpeg"),
        scale(8, "1020300", "180000", "30000", "514500", "WhatsApp Image 2026-08-13 at 10.07.56 (1).jpeg"),
        scale(9, "1037600", "180000", "50000", "524000", "WhatsApp Image 2026-08-13 at 10.07.57.jpeg"),
        scale(10, "1053200", "180000", "60000", "534000", "WhatsApp Image 2026-08-13 at 10.07.57 (1).jpeg"),
        scale(11, "1069000", "180000", "70000", "545000", "WhatsApp Image 2026-08-13 at 10.07.57 (2).jpeg"),
        scale(12, "1085000", "180000", "120000", "545000", "WhatsApp Image 2026-08-13 at 10.07.57 (3).jpeg"),
    ).associateBy(SuvicoSalaryScale::month)

    fun forMonth(month: YearMonth): SuvicoSalaryScale? = scales[month]

    private fun scale(
        month: Int,
        basic: String,
        presentism: String,
        nonRemunerative: String,
        viatics: String,
        source: String,
    ) = SuvicoSalaryScale(
        month = YearMonth.of(2026, month),
        basicSalary = basic.toBigDecimal(),
        presentism = presentism.toBigDecimal(),
        nonRemunerativeAmount = nonRemunerative.toBigDecimal(),
        viatics = viatics.toBigDecimal(),
        sourceFileName = source,
    )
}

fun suvicoSeniorityPercentage(years: Int): BigDecimal {
    require(years in 0..60) { "La antigüedad debe estar entre 0 y 60 años" }
    if (years == 0) return BigDecimal.ZERO
    val published = listOf(
        "2.0", "4.0", "6.0", "8.0", "10.0", "11.5", "13.0", "14.5", "16.0", "17.5",
        "18.5", "19.5", "20.5", "21.5", "22.5", "23.5", "24.5", "25.5", "26.5", "27.5",
    )
    return if (years <= published.size) {
        published[years - 1].toBigDecimal()
    } else {
        published.last().toBigDecimal().plus(BigDecimal.valueOf((years - 20).toLong()))
    }
}

fun estimateSuvicoRemuneration(
    scale: SuvicoSalaryScale,
    seniorityYears: Int,
    projectedNightHours: Duration,
    projectedHolidayHours: Duration,
    projectedOvertimeHours: Duration,
): SuvicoRemunerationEstimate {
    require(!projectedNightHours.isNegative)
    require(!projectedHolidayHours.isNegative)
    require(!projectedOvertimeHours.isNegative)

    val seniorityPercentage = suvicoSeniorityPercentage(seniorityYears)
    val seniorityAmount = scale.basicSalary
        .multiply(seniorityPercentage)
        .divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP)
    val remunerativeBase = scale.basicSalary.plus(seniorityAmount).plus(scale.presentism)
    val fixedGross = remunerativeBase.plus(scale.nonRemunerativeAmount).plus(scale.viatics)
    val hourlyValue = remunerativeBase.divide(TWO_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP)
    val nightValue = scale.basicSalary.plus(seniorityAmount).multiply(NIGHT_RATE)
    val nightAdditional = durationHours(projectedNightHours).multiply(nightValue).money()
    val holidayAdditional = durationHours(projectedHolidayHours).multiply(hourlyValue).multiply(TWO).money()
    val overtimeAtFifty = durationHours(projectedOvertimeHours).multiply(hourlyValue).multiply(ONE_POINT_FIVE).money()
    val overtimeAtOneHundred = durationHours(projectedOvertimeHours).multiply(hourlyValue).multiply(TWO).money()
    val baseWithKnownVariables = fixedGross.plus(nightAdditional).plus(holidayAdditional)

    return SuvicoRemunerationEstimate(
        scale = scale,
        seniorityYears = seniorityYears,
        seniorityPercentage = seniorityPercentage,
        seniorityAmount = seniorityAmount.money(),
        remunerativeBase = remunerativeBase.money(),
        fixedGrossAmount = fixedGross.money(),
        hourlyValue = hourlyValue.money(),
        projectedNightHours = projectedNightHours,
        projectedHolidayHours = projectedHolidayHours,
        projectedOvertimeHours = projectedOvertimeHours,
        nightAdditional = nightAdditional,
        holidayAdditional = holidayAdditional,
        overtimeAtFiftyPercent = overtimeAtFifty,
        overtimeAtOneHundredPercent = overtimeAtOneHundred,
        estimatedGrossAtFiftyPercent = baseWithKnownVariables.plus(overtimeAtFifty).money(),
        estimatedGrossAtOneHundredPercent = baseWithKnownVariables.plus(overtimeAtOneHundred).money(),
    )
}

private fun durationHours(duration: Duration): BigDecimal =
    BigDecimal.valueOf(duration.seconds)
        .add(BigDecimal.valueOf(duration.nano.toLong(), 9))
        .divide(SECONDS_PER_HOUR, 8, RoundingMode.HALF_UP)

private fun BigDecimal.money(): BigDecimal = setScale(MONEY_SCALE, RoundingMode.HALF_UP)

private const val MONEY_SCALE = 2
private val HUNDRED = BigDecimal("100")
private val TWO_HUNDRED = BigDecimal("200")
private val SECONDS_PER_HOUR = BigDecimal("3600")
private val NIGHT_RATE = BigDecimal("0.001")
private val ONE_POINT_FIVE = BigDecimal("1.5")
private val TWO = BigDecimal("2")
