package com.blackatsystems.miguardia.stage12

/** Métodos de la etapa 12.2 (Cálculos y consulta) que el ejecutor QA normal admite juntos. */
object Stage12CalculationSelectors {
    private const val CLASS = "com.blackatsystems.miguardia.stage12.Stage12CalculationsDeviceTest"
    private const val EDITORS = "com.blackatsystems.miguardia.stage12.Stage12HoursEditorsDeviceTest"

    /** Sesión humana: sólo se corre sola y con Joaquín frente al teléfono. */
    const val HUMAN: String =
        "com.blackatsystems.miguardia.stage12.Stage12CalculationsHumanTest#saveWithTheRealPickerAndCancelTheRealShareSheet"
    val family: Set<String> = setOf(
        "$CLASS#hoursShowTheFixtureMinutesAndKeepAvailabilitySeparate",
        "$CLASS#summaryMatchesHoursAndNavigatesMonthsExactly",
        "$CLASS#calendarDetailSummaryAndBackKeepTheCalendarMonth",
        "$CLASS#reportsFromSummaryKeepMonthMatchTheRealXlsxAndReturn",
        "$CLASS#pdfIsGeneratedFromTheSameProjection",
        "$CLASS#reportPrivacySwitchesIncludeOnlyWhatWasChosen",
        "$CLASS#pdfPhotosShowTheWarningAndStopAtTwelve",
        HUMAN,
    ) + listOf(
        "weeklyReferenceFromTodayCutsThePreviousSegmentAndSaves",
        "cycleFromAPastDateRequiresBothConfirmationsBeforeSaving",
        "perPeriodValueIsReportedThenCorrectedOnlyWithConfirmation",
        "classCatalogCreatesEditsArchivesAndReactivatesWithoutRewritingHistory",
        "independentExtraAcrossMidnightNeedsOverlapConfirmationAndDeletesOnlyItself",
        "missingClassIsCreatedKeepingTheDraftAndProtectedDateNeedsConfirmation",
        "referenceSaveRevalidatesAfterAConcurrentConfigurationChange",
        "independentExtraSaveRevalidatesOccupancyAfterAConcurrentWrite",
    ).map { "$EDITORS#$it" }
}
