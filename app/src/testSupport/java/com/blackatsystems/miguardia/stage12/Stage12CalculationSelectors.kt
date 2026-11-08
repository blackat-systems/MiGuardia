package com.blackatsystems.miguardia.stage12

/** Métodos de la etapa 12.2 (Cálculos y consulta) que el ejecutor QA normal admite juntos. */
object Stage12CalculationSelectors {
    private const val CLASS = "com.blackatsystems.miguardia.stage12.Stage12CalculationsDeviceTest"
    val family: Set<String> = setOf(
        "$CLASS#hoursShowTheFixtureMinutesAndKeepAvailabilitySeparate",
        "$CLASS#summaryMatchesHoursAndNavigatesMonthsExactly",
        "$CLASS#calendarDetailSummaryAndBackKeepTheCalendarMonth",
        "$CLASS#reportsFromSummaryKeepMonthMatchTheRealXlsxAndReturn",
        "$CLASS#pdfIsGeneratedFromTheSameProjection",
    )
}
