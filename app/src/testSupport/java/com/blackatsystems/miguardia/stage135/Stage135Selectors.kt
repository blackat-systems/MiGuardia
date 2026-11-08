package com.blackatsystems.miguardia.stage135

/**
 * Métodos de la etapa 13.5 (Estética y facilidad de uso) que el ejecutor QA normal admite juntos.
 * Son pruebas de pantalla con datos ficticios: no abren Room ni los datos QA.
 */
object Stage135Selectors {
    const val AVAILABILITY_CAPTURE: String =
        "com.blackatsystems.miguardia.stage135.Stage135AvailabilityCaptureTest#captureAvailabilitySurfaces"

    private const val AVAILABILITY = "com.blackatsystems.miguardia.AvailabilityComposeTest"

    /** Pruebas propias de la etapa. Sólo ellas activan la regla de la etapa en el ejecutor. */
    val own: Set<String> = setOf(AVAILABILITY_CAPTURE) + listOf(
        "windowPickersGiveTheSameTextAsTypingAndCancelChangesNothing",
        "emptyDateFieldPickerStartsOnTheOwnerDateAndOnlyWritesAfterConfirming",
        "configurationDateKeepsTypingAndAddsTheCalendar",
    ).map { "$AVAILABILITY#$it" }

    /**
     * Pruebas ya revisadas de las pantallas que esta etapa toca: corren como regresión sólo
     * junto con alguna propia. Solas siguen las reglas de su etapa de origen.
     */
    val screenRegression: Set<String> = listOf(
        "configurationShowsThreeExactNamesNoUseDateAndReviewBeforeSave",
        "configurationSelectionIsExplicitAndKeepsOneActionAcrossThemesAndFontScales",
        "dayUsesCalendarDateHidesCreateWhenDisabledAndShowsTextualAccessibleIndicatorWhenEnabled",
        "hoursShowsAvailabilitySeparatelyWithoutCallingItWorkedTime",
        "availabilityWindowKeepsBothCivilDatesAcrossYearBoundary",
        "monthlyGridAnnouncesAvailabilityWithTextIndependentFromColor",
        "loadingErrorAndRetryRemainDistinctFromEmptyContent",
        "editorRemainsReachableInLandscapeSizedViewportAtTwoHundredPercentFontScale",
        "discardAndObservationErrorKeepTheUserInControlWithoutHidingSavedWindows",
    ).map { "$AVAILABILITY#$it" }.toSet()

    val family: Set<String> = own + screenRegression
}
