package com.blackatsystems.miguardia.stage12

/** Test-only device contract. A matching build is not proof of AVD ownership. */
internal object Stage12RuntimeTarget {
    enum class Target { SAMSUNG, AOSP_STAGE12, SAMSUNG_TEMPORAL_API37 }
    const val AOSP_FINGERPRINT = "Android/sdk_phone64_x86_64/emu64x:16/BE2A.250530.026.D1/13818094:userdebug/test-keys"
    const val W010 = "com.blackatsystems.miguardia.stage12.Stage12W010NativeTest#hiddenRealRemoteViewsClearAdversarialAppliedFieldsInBothSizes"
    const val C090 = "com.blackatsystems.miguardia.stage12.Stage12C090NativeTest#identicalJourneysCancelThenRejectStaleCasAndDeleteOnlyTheSecond"
    const val X015 = "com.blackatsystems.miguardia.stage12.Stage12X015NativeTest#realCalendarSummaryCardAndAppliedWidgetPreserveCompleteStores"
    const val WORK_FIXTURE = "com.blackatsystems.miguardia.stage12.Stage12AospWorkFixtureTest#createsMinimalWorkThroughNormalUiAndExplicitBackfill"
    const val QA_BASELINE_BOOTSTRAP = "com.blackatsystems.miguardia.stage12.Stage12QaBaselineBootstrapTest#initializesSyntheticQaThroughNormalUi"
    const val MONTH_NAVIGATION = "com.blackatsystems.miguardia.stage12.Stage12NormalQaActivityTest#actualMonthNavigationCrossesYearsAndReturnsToToday"
    const val TODAY_DETAIL = "com.blackatsystems.miguardia.stage12.Stage12NormalQaActivityTest#todayDetailRemainsOpenAcrossCloseAndReopen"

    fun identify(api: Int, model: String, manufacturer: String, buildId: String,
                 fingerprint: String, primaryAbi: String?): Target {
        if (api == 37) {
            check(model == "SM-S938B" && manufacturer.equals("samsung", true) && primaryAbi == "arm64-v8a") {
                "Stage12 API37 requires the explicit Samsung temporal destination"
            }
            return Target.SAMSUNG_TEMPORAL_API37
        }
        check(api == 36) { "Stage12 requires its explicit API36 or API37 destination" }
        if (model == "SM-S938B" && manufacturer.equals("samsung", true)) {
            check(primaryAbi == "arm64-v8a") { "Samsung Stage12 requires arm64-v8a" }
            return Target.SAMSUNG
        }
        check(primaryAbi == "x86_64" && buildId == "BE2A.250530.026.D1" && fingerprint == AOSP_FINGERPRINT) {
            "Stage12 runtime identity is outside the two explicit targets"
        }
        return Target.AOSP_STAGE12
    }

    fun requireSelection(target: Target, selectors: List<String>, clock: String, hasSummaryObservation: Boolean,
                         clockExplicit: Boolean) {
        if (Stage12B1HumanContract.SELECTOR in selectors) {
            check(target == Target.SAMSUNG_TEMPORAL_API37 && selectors == listOf(Stage12B1HumanContract.SELECTOR) &&
                clock == "REAL" && clockExplicit && !hasSummaryObservation) {
                "Human session requires its exact Samsung API37 singleton and explicit real clock"
            }
            return
        }
        val b1Closure = setOf(Stage12B1Selectors.OWNED_LOAD_EDIT,
            "com.blackatsystems.miguardia.stage12.Stage12RecurringLifecycleTest#mixedOccupancyPoliciesUseExactPreviewsAndAtomicRealWrites")
        if (target == Target.SAMSUNG_TEMPORAL_API37 && selectors.any { it in b1Closure }) {
            check(selectors.singleOrNull() in b1Closure && clock == "REAL" && clockExplicit && !hasSummaryObservation) {
                "B1 closure requires one exact Samsung API37 method and an explicit real clock"
            }
            return
        }
        if (selectors.any { it in Stage12B1Selectors.calendarPrivacyComplements }) {
            check(target == Target.SAMSUNG_TEMPORAL_API37 && selectors.singleOrNull() in Stage12B1Selectors.calendarPrivacyComplements &&
                clock == "REAL" && clockExplicit && !hasSummaryObservation) {
                "Calendar privacy complements require one exact Samsung API37 method and an explicit real clock"
            }
            return
        }
        if (Stage12C097InstitutionContract.SELECTOR in selectors) {
            check(target == Target.SAMSUNG_TEMPORAL_API37 && selectors == listOf(Stage12C097InstitutionContract.SELECTOR) &&
                clock == "REAL" && clockExplicit && !hasSummaryObservation) {
                "C097 institution requires its exact Samsung API37 singleton and explicit real runner clock"
            }
            return
        }
        if (selectors.any { it in setOf(Stage12CalendarDetailContract.SELECTOR, Stage12CalendarDetailContract.ACTIVATION_SELECTOR) }) {
            check(target == Target.SAMSUNG_TEMPORAL_API37 && selectors.size == 1 &&
                clock == "REAL" && clockExplicit && !hasSummaryObservation) {
                "Calendar detail requires its exact Samsung API37 singleton and explicit real runner clock"
            }
            return
        }
        if (QA_BASELINE_BOOTSTRAP in selectors) {
            check(target == Target.SAMSUNG_TEMPORAL_API37 && selectors == listOf(QA_BASELINE_BOOTSTRAP) &&
                clock == "REAL" && clockExplicit && !hasSummaryObservation) {
                "QA baseline bootstrap requires its Samsung API37 singleton and explicit real clock"
            }
            return
        }
        if (target == Target.SAMSUNG_TEMPORAL_API37) {
            val reviewed = Stage12B1Selectors.temporalLiteralFamily + Stage12B1Selectors.availabilityLifecycleFamily
            check(selectors.singleOrNull() in reviewed &&
                clock == "REAL" && clockExplicit && !hasSummaryObservation) {
                "Samsung API37 requires one reviewed temporal or availability method and an explicit real clock"
            }
            return
        }
        if (target == Target.SAMSUNG) {
            val c026 = "com.blackatsystems.miguardia.stage12.Stage12TemporalLiteralAndroidTest#shiftEndpointsAndHistoricalDetailStayExact"
            if (c026 in selectors) {
                check(selectors == listOf(c026) && clock == "REAL" && clockExplicit && !hasSummaryObservation) {
                    "Samsung C026 requires its single reviewed method and an explicit real clock"
                }
                return
            }
            check(selectors.isNotEmpty() && selectors.size == selectors.toSet().size &&
                selectors.all { it in setOf(MONTH_NAVIGATION, TODAY_DETAIL, C090, Stage12B1Selectors.OWNED_LOAD_EDIT) ||
                    it in Stage12B1Selectors.compose || it in Stage12B1Selectors.ownedWorkFamily ||
                    it in Stage12B1Selectors.selectionBatchFamily || it in Stage12B1Selectors.recurringLifecycleFamily ||
                    it in Stage12B1Selectors.calendarExceptionsFamily || it in Stage12B1Selectors.medicalDraftRegression } &&
                clock == "REAL" && clockExplicit && !hasSummaryObservation) {
                "Samsung requires reviewed A/B1 methods and an explicit real clock"
            }
            return
        }
        check(selectors.size == 1 && selectors.single() in setOf(W010, C090, X015, WORK_FIXTURE) &&
            clock == "REAL" && clockExplicit && !hasSummaryObservation) {
            "AOSP Stage12 requires one exact reviewed selector and an explicit real clock"
        }
    }
}
