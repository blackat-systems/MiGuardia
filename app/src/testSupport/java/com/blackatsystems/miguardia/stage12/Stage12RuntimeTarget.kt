package com.blackatsystems.miguardia.stage12

/** Test-only device contract. A matching build is not proof of AVD ownership. */
internal object Stage12RuntimeTarget {
    enum class Target { SAMSUNG, AOSP_STAGE12 }
    const val AOSP_FINGERPRINT = "Android/sdk_phone64_x86_64/emu64x:16/BE2A.250530.026.D1/13818094:userdebug/test-keys"
    const val W010 = "com.blackatsystems.miguardia.stage12.Stage12W010NativeTest#hiddenRealRemoteViewsClearAdversarialAppliedFieldsInBothSizes"
    const val C090 = "com.blackatsystems.miguardia.stage12.Stage12C090NativeTest#identicalJourneysCancelThenRejectStaleCasAndDeleteOnlyTheSecond"
    const val X015 = "com.blackatsystems.miguardia.stage12.Stage12X015NativeTest#realCalendarSummaryCardAndAppliedWidgetPreserveCompleteStores"
    const val WORK_FIXTURE = "com.blackatsystems.miguardia.stage12.Stage12AospWorkFixtureTest#createsMinimalWorkThroughNormalUiAndExplicitBackfill"
    const val MONTH_NAVIGATION = "com.blackatsystems.miguardia.stage12.Stage12NormalQaActivityTest#actualMonthNavigationCrossesYearsAndReturnsToToday"
    const val TODAY_DETAIL = "com.blackatsystems.miguardia.stage12.Stage12NormalQaActivityTest#todayDetailRemainsOpenAcrossCloseAndReopen"

    fun identify(api: Int, model: String, manufacturer: String, buildId: String,
                 fingerprint: String, primaryAbi: String?): Target {
        check(api == 36) { "Stage12 requires API 36" }
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
        if (target == Target.SAMSUNG) {
            check(selectors.isNotEmpty() && selectors.size == selectors.toSet().size &&
                selectors.all { it in setOf(MONTH_NAVIGATION, TODAY_DETAIL, C090, Stage12B1Selectors.OWNED_LOAD_EDIT) ||
                    it in Stage12B1Selectors.compose } &&
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
