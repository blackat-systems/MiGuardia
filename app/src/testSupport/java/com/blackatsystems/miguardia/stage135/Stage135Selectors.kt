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
    const val HERO_CONTENT_COLOR: String =
        "com.blackatsystems.miguardia.stage135.Stage135HeroContentColorTest#heroTextUsesTheThemeTextColorWithoutAParentSurface"

    val own: Set<String> = setOf(AVAILABILITY_CAPTURE, HERO_CONTENT_COLOR) + listOf(
        "windowPickersGiveTheSameTextAsTypingAndCancelChangesNothing",
        "emptyDateFieldPickerStartsOnTheOwnerDateAndOnlyWritesAfterConfirming",
        "configurationDateKeepsTypingAndAddsTheCalendar",
        "largeFontKeepsHeaderWordsWholeAndTheCalendarButtonUsable",
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
    ).map { "$AVAILABILITY#$it" }.toSet() + listOf(
        "darkThemeExposesApprovedVigiliaRolesAndContrast",
        "lightThemeExposesApprovedVigiliaRolesAndContrast",
    ).map { "com.blackatsystems.miguardia.VigiliaThemeComposeTest#$it" } +
        // Style v2 changes the font for the whole app: screens whose tests check fit, cut-off
        // text and typography run as regression (synthetic data, no Room).
        styleV2Regression

    private val styleV2Regression: Set<String> get() = emptySet<String>() + listOf(
        "standardFontScalePhysicalViewportShowsTheWholeMonthWithoutPermanentActions",
        "shortViewportKeepsTheWholeMonthWithoutAddingVerticalScroll",
        "anOldCollapsedPreferenceCannotHideDates",
        "fixedPhysicalViewportKeepsCalendarReachableAtEveryAndroidFontScale",
        "shortWindowManualSelectionKeepsSixFullRowsAtTwoHundredPercent",
        "wideShortWindowKeepsRailControlsAndEverySelectionOwnerAtNativeLargeText",
        "shiftDetailKeepsItsParticularNotificationSettingsReachable",
    ).map { "com.blackatsystems.miguardia.CalendarAdaptiveLayoutComposeTest#$it" } + listOf(
        "portraitFourFiveSixWeekMonthsAtEveryThemeAndAndroidFontScale",
        "landscapeFourFiveSixWeekMonthsAtEveryThemeAndAndroidFontScale",
    ).map { "com.blackatsystems.miguardia.Stage4CalendarMatrixTest#$it" } + listOf(
        "upcomingJourneyHasRequestedVisualOrderAndSubMinuteCountdown",
        "singleOngoingJourneyIsShownWithoutARedundantExpandableCopy",
        "singleJourneyDoesNotDuplicateWhenUpcomingBecomesOngoing",
        "expansionDisappearsWhenOnlyTheAlreadyShownPrimaryJourneyRemains",
        "completedTodayDoesNotHideTheNextJourneyOrItsCountdown",
        "upcomingJourneyIsReadableInBothThemesAndEveryAndroidFontScale",
        "loadingAndRecoverableErrorExposeRetryWithoutHidingLastValidResult",
        "activeOvernightStartedYesterdayIsTheClosedPrimaryWithoutPrivateContent",
        "activeAvailabilityKeepsItsHistoricalLabelAndDoesNotBecomeAShift",
        "twentyFiveHourAvailabilityKeepsItsCivilEndDayInTextAndAccessibility",
        "resumedAvailabilityShowsTheSegmentDateWithoutRewritingItsOwnerDate",
        "simultaneousUpcomingShiftAndAvailabilityRemainVisibleWithoutChangingShiftCount",
        "availabilityRemainsReadableInLightDarkAndEveryAndroidFontScale",
        "sameTimeUpcomingShiftsStayDifferentiatedAndExpandInStableOrder",
        "completedOnlySummaryExpandsAndCanCloseWithoutLosingItsData",
        "oneHistoricalTodayRecordStillOffersItsFullExpandableDetail",
        "sameDayExpansionSurvivesContentLoadingWithoutResultAndContent",
        "restoredSameDayExpansionSurvivesNullLoadingButDoesNotExpandAnotherCivilDate",
        "expansionResetsWhenTheCivilDateChanges",
        "restoredExpansionStaysBoundToTheCivilDateThatWasSaved",
        "expandedListIncludesCancelledAbsentAndProtectedTodayShifts",
        "expandedMixedListPreservesEveryTodayStateAndPlannedSnapshots",
        "actualTimeAndProtectionStayVisibleWithoutLeakingReasonsOrMedicalNotes",
        "futureShiftExplicitDayOffAndHonestEmptyStateAreDistinct",
        "protectedFutureProducesAnHonestEmptyStateWithoutAnnouncingWork",
        "exactTemporalTransitionsUpdateUpcomingToOngoingAndThenCompleted",
        "changingVisibleMonthDoesNotCreateASecondTopCardSourceOfTruth",
        "engineErrorDoesNotForceTheCardIntoAShortCalendarAcrossThemeAndAndroidFontScale",
        "todayCardRemainsReadableInLandscapeAndRestoresOrientation",
    ).map { "com.blackatsystems.miguardia.NextEventComposeTest#$it" } + listOf(
        "progressDistinguishesLoadingErrorAndKnownContentWithoutFalseZero",
        "referenceEditorExposesEveryExplicitChoiceAndNoSectorDefault",
        "exactReferenceReviewShowsPeriodAmountAndConfirmationsAfterReview",
        "independentExtraCardIsTextualAndKeepsCorrectionAndDeletionSeparate",
        "perPeriodMissingValueHasAnExplicitEditorAndCorrectionHasAConfirmation",
        "progressPrimaryActionStaysReachableInBothThemesAndEveryAndroidFontScale",
        "pendingHoursKeepTheWholeAmountAndUnitSeparatedFromTheCompleteLabel",
    ).map { "com.blackatsystems.miguardia.HoursAndExtrasComposeTest#$it" } + listOf(
        "loadingEmptyAndErrorAreDistinctAndRetryIsExplicit",
        "introAndThreeDotMenuOpenPersonalizationWithoutChangingProjection",
        "visibleSummaryOffersTheReportEntryAndCallsTheDedicatedAction",
        "monthControlsShareOneRowAndNumbersPrecedeIntroductionAndReport",
        "hiddenEmptyAndOrderedFamiliesFollowPreferences",
        "metricDetailUsesTheExactRowsAndNeverDisplaysPrivateFields",
        "personalizationHasAccessibleVisibilityAndBoundaryMoveControls",
        "restoredDetailAndPersonalizationWaitForRealDataAndExposeRetryOnFailure",
        "preferenceWriteFailureIsRecoverableWithoutClosingTheSurface",
        "eachHistoricallySimilarLabelOpensItsOwnMetricDetail",
        "returningFromDetailRestoresTheOverviewScrollPosition",
        "surfaceRendersAtEveryAndroidFontScaleInLightAndDarkThemes",
        "summaryAndContributionKeepLongLabelsAndAmountsWholeAtEveryThemeAndFontScale",
    ).map { "com.blackatsystems.miguardia.SummaryComposeTest#$it" }

    val family: Set<String> = own + screenRegression
}
