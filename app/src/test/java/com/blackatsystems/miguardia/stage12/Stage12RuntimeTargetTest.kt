package com.blackatsystems.miguardia.stage12

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Stage12RuntimeTargetTest {
    private fun aosp(api: Int = 36, id: String = "BE2A.250530.026.D1",
                     fingerprint: String = Stage12RuntimeTarget.AOSP_FINGERPRINT, abi: String? = "x86_64") =
        Stage12RuntimeTarget.identify(api, "sdk_gphone64_x86_64", "unknown", id, fingerprint, abi)

    @Test fun qaBaselineBootstrapRequiresSamsungApi37ExactSingletonAndExplicitRealClock() {
        val selector = Stage12RuntimeTarget.QA_BASELINE_BOOTSTRAP
        val target = Stage12RuntimeTarget.Target.SAMSUNG_TEMPORAL_API37
        Stage12RuntimeTarget.requireSelection(target, listOf(selector), "REAL", false, true)
        for (other in listOf(aosp(), Stage12RuntimeTarget.Target.SAMSUNG)) {
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(other, listOf(selector), "REAL", false, true)
            }
        }
        for (selectors in listOf(listOf(selector, selector),
            listOf(selector, Stage12B1Selectors.TEMPORAL_RESUMPTION), listOf(selector.substringBefore('#')))) {
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(target, selectors, "REAL", false, true)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(target, listOf(selector), "FIXED", false, true)
        }
        assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(target, listOf(selector), "REAL", false, false)
        }
        assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(target, listOf(selector), "REAL", true, true)
        }
    }

    @Test fun exactSamsungContractRemainsIndependentOfEmulatorBuild() {
        for (manufacturer in listOf("samsung", "SAMSUNG")) {
            assertEquals(Stage12RuntimeTarget.Target.SAMSUNG,
                Stage12RuntimeTarget.identify(36, "SM-S938B", manufacturer, "received", "received", "arm64-v8a"))
        }
    }
    @Test fun exactAospBuildAcceptsOnlyTheReviewedSingleRealSelectors() {
        val target = aosp()
        assertEquals(Stage12RuntimeTarget.Target.AOSP_STAGE12, target)
        for (selector in listOf(Stage12RuntimeTarget.W010, Stage12RuntimeTarget.C090,
            Stage12RuntimeTarget.X015, Stage12RuntimeTarget.WORK_FIXTURE)) {
            Stage12RuntimeTarget.requireSelection(target, listOf(selector), "REAL", false, true)
        }
    }
    @Test fun changedApiBuildFingerprintAndAbiAreRejected() {
        for (api in listOf(35, 37)) assertThrows(IllegalStateException::class.java) { aosp(api = api) }
        assertThrows(IllegalStateException::class.java) { aosp(id = "different") }
        assertThrows(IllegalStateException::class.java) { aosp(fingerprint = Stage12RuntimeTarget.AOSP_FINGERPRINT + "x") }
        for (abi in listOf("arm64-v8a", "x86", null)) assertThrows(IllegalStateException::class.java) { aosp(abi = abi) }
    }
    @Test fun OtherSamsungModelsAndManufacturersAreRejected() {
        assertThrows(IllegalStateException::class.java) { Stage12RuntimeTarget.identify(36, "SM-S938X", "samsung", "other", "other", "arm64-v8a") }
        assertThrows(IllegalStateException::class.java) { Stage12RuntimeTarget.identify(36, "SM-S938B", "other", "other", "other", "arm64-v8a") }
    }
    @Test fun AospRejectsExtraDuplicateMissingAndDifferentSelectors() {
        for (selectors in listOf(emptyList(), listOf("X015"), listOf(Stage12RuntimeTarget.W010, "C090"),
            listOf(Stage12RuntimeTarget.W010, Stage12RuntimeTarget.W010),
            listOf(Stage12RuntimeTarget.C090, Stage12RuntimeTarget.X015),
            listOf(Stage12RuntimeTarget.WORK_FIXTURE, Stage12RuntimeTarget.X015))) {
            assertThrows(IllegalStateException::class.java) { Stage12RuntimeTarget.requireSelection(aosp(), selectors, "REAL", false, true) }
        }
    }
    @Test fun AospRejectsAlternateClockAndSummaryObservation() {
        assertThrows(IllegalStateException::class.java) { Stage12RuntimeTarget.requireSelection(aosp(), listOf(Stage12RuntimeTarget.W010), "FIXED", false, true) }
        assertThrows(IllegalStateException::class.java) { Stage12RuntimeTarget.requireSelection(aosp(), listOf(Stage12RuntimeTarget.W010), "REAL", true, true) }
    }
    @Test fun AospRejectsAnOmittedClockEvenWhenTheDefaultWouldBeReal() {
        assertThrows(IllegalStateException::class.java) { Stage12RuntimeTarget.requireSelection(aosp(), listOf(Stage12RuntimeTarget.W010), "REAL", false, false) }
    }
    @Test fun SamsungAcceptsOnlyTheInitialNavigationFamilyWithExplicitRealClock() {
        for (selectors in listOf(listOf(Stage12RuntimeTarget.MONTH_NAVIGATION), listOf(Stage12RuntimeTarget.TODAY_DETAIL),
            listOf(Stage12RuntimeTarget.MONTH_NAVIGATION, Stage12RuntimeTarget.TODAY_DETAIL))) {
            Stage12RuntimeTarget.requireSelection(Stage12RuntimeTarget.Target.SAMSUNG, selectors, "REAL", false, true)
        }
    }
    @Test fun SamsungRejectsAospAndUnreviewedFixedFixtureSelectors() {
        for (selector in listOf(Stage12RuntimeTarget.W010,
            Stage12RuntimeTarget.X015, Stage12RuntimeTarget.WORK_FIXTURE,
            "com.blackatsystems.miguardia.stage12.Stage12NormalQaActivityTest#fixedDateWeatherAndFutureDetailsKeepTheirOwnerAndReturn",
            "other#unknown")) {
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(Stage12RuntimeTarget.Target.SAMSUNG, listOf(selector), "REAL", false, true)
            }
        }
    }
    @Test fun SamsungRejectsMissingDuplicateMixedSelectorsAndIncompatibleClock() {
        val target = Stage12RuntimeTarget.Target.SAMSUNG
        val method = Stage12RuntimeTarget.MONTH_NAVIGATION
        for (selectors in listOf(emptyList(), listOf(method, method), listOf(method, Stage12RuntimeTarget.WORK_FIXTURE))) {
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(target, selectors, "REAL", false, true)
            }
        }
        for (clock in listOf("STAGE12_20261023", "FIXED", "", "real")) {
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(target, listOf(method), clock, false, true)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(target, listOf(method), "REAL", false, false)
        }
        assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(target, listOf(method), "REAL", true, true)
        }
    }
    @Test fun SamsungRejectsChangedApiAndAbi() {
        for (api in listOf(35, 38)) assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.identify(api, "SM-S938B", "samsung", "received", "received", "arm64-v8a")
        }
        for (abi in listOf("x86_64", "armeabi-v7a", null)) assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.identify(36, "SM-S938B", "samsung", "received", "received", abi)
        }
    }
    @Test fun AospDoesNotGainSamsungSelectors() {
        for (selector in listOf(Stage12RuntimeTarget.MONTH_NAVIGATION, Stage12RuntimeTarget.TODAY_DETAIL,
            Stage12B1Selectors.OWNED_LOAD_EDIT) + Stage12B1Selectors.compose + Stage12B1Selectors.ownedWorkFamily + Stage12B1Selectors.selectionBatchFamily) {
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(aosp(), listOf(selector), "REAL", false, true)
            }
        }
    }
    @Test fun SamsungB1AdmitsOnlyConcreteReviewedMethodsWithExplicitRealClock() {
        val target = Stage12RuntimeTarget.Target.SAMSUNG
        val reviewed = Stage12B1Selectors.compose + setOf(Stage12B1Selectors.OWNED_LOAD_EDIT, Stage12RuntimeTarget.C090) +
            Stage12B1Selectors.ownedWorkFamily
        assertEquals(130, reviewed.size)
        reviewed.forEach { selector ->
            Stage12RuntimeTarget.requireSelection(target, listOf(selector), "REAL", false, true)
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(target, listOf(selector), "REAL", false, false)
            }
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(target, listOf(selector), "STAGE12_20261023", false, true)
            }
        }
        Stage12RuntimeTarget.requireSelection(target, reviewed.toList(), "REAL", false, true)
        for (selector in listOf("com.blackatsystems.miguardia.V2ManualShiftLoadActivityTest#draftSurvivesRecreationAndShiftSnapshotSurviveReopen",
            "com.blackatsystems.miguardia.CalendarComposeTest", "com.blackatsystems.miguardia.CalendarComposeTest#notReviewed",
            "com.blackatsystems.miguardia.stage12.Stage12OwnedWorkFamilyTest",
            "com.blackatsystems.miguardia.stage12.Stage12OwnedWorkFamilyTest#clearReceivedStore")) {
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(target, listOf(selector), "REAL", false, true)
            }
        }
    }
    @Test fun SamsungRecurringLifecycleAddsOnlyThreeExactMethodsAndNoAospAuthority() {
        val previous = Stage12B1Selectors.compose + Stage12B1Selectors.ownedWorkFamily + Stage12B1Selectors.selectionBatchFamily +
            setOf(Stage12B1Selectors.OWNED_LOAD_EDIT, Stage12RuntimeTarget.C090)
        val added = Stage12B1Selectors.recurringLifecycleFamily
        assertEquals(134, previous.size); assertEquals(3, added.size); assertEquals(137, (previous + added).size)
        added.forEach { selector ->
            Stage12RuntimeTarget.requireSelection(Stage12RuntimeTarget.Target.SAMSUNG, listOf(selector), "REAL", false, true)
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(aosp(), listOf(selector), "REAL", false, true)
            }
            for ((clock, observation, explicit) in listOf(Triple("FIXED", false, true), Triple("REAL", true, true), Triple("REAL", false, false)))
                assertThrows(IllegalStateException::class.java) {
                    Stage12RuntimeTarget.requireSelection(Stage12RuntimeTarget.Target.SAMSUNG, listOf(selector), clock, observation, explicit)
                }
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(Stage12RuntimeTarget.Target.SAMSUNG, listOf(selector, selector), "REAL", false, true)
            }
        }
        for (selector in listOf("com.blackatsystems.miguardia.stage12.Stage12RecurringLifecycleTest",
            "com.blackatsystems.miguardia.stage12.Stage12RecurringLifecycleTest#unreviewed"))
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(Stage12RuntimeTarget.Target.SAMSUNG, listOf(selector), "REAL", false, true)
            }
    }
    @Test fun SamsungCalendarExceptionsAddOnlySixExactMethodsWithRealClockAndNoAospAuthority() {
        val old = Stage12B1Selectors.compose + Stage12B1Selectors.ownedWorkFamily + Stage12B1Selectors.selectionBatchFamily +
            Stage12B1Selectors.recurringLifecycleFamily + setOf(Stage12B1Selectors.OWNED_LOAD_EDIT, Stage12RuntimeTarget.C090)
        val added = Stage12B1Selectors.calendarExceptionsFamily + Stage12B1Selectors.medicalDraftRegression
        assertEquals(137, old.size); assertEquals(6, added.size); assertEquals(143, (old + added).size)
        for (selector in added) {
            Stage12RuntimeTarget.requireSelection(Stage12RuntimeTarget.Target.SAMSUNG, listOf(selector), "REAL", false, true)
            assertThrows(IllegalStateException::class.java) { Stage12RuntimeTarget.requireSelection(aosp(), listOf(selector), "REAL", false, true) }
            for ((clock, observation, explicit) in listOf(Triple("FIXED", false, true), Triple("REAL", true, true), Triple("REAL", false, false)))
                assertThrows(IllegalStateException::class.java) {
                    Stage12RuntimeTarget.requireSelection(Stage12RuntimeTarget.Target.SAMSUNG, listOf(selector), clock, observation, explicit)
                }
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(Stage12RuntimeTarget.Target.SAMSUNG, listOf(selector, selector), "REAL", false, true)
            }
        }
        for (selector in listOf("com.blackatsystems.miguardia.stage12.Stage12CalendarExceptionsTest",
            "com.blackatsystems.miguardia.stage12.Stage12CalendarExceptionsTest#unreviewed",
            "com.blackatsystems.miguardia.MedicalLeaveDraftComposeTest#unreviewed"))
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(Stage12RuntimeTarget.Target.SAMSUNG, listOf(selector), "REAL", false, true)
            }
    }
    @Test fun SamsungSelectionBatchAddsOnlyFourExactOwnedMethodsWithoutChangingThePrevious130() {
        val selectors = Stage12B1Selectors.selectionBatchFamily
        val previous = Stage12B1Selectors.compose + Stage12B1Selectors.ownedWorkFamily +
            setOf(Stage12B1Selectors.OWNED_LOAD_EDIT, Stage12RuntimeTarget.C090)
        assertEquals(130, previous.size)
        assertEquals(4, selectors.size)
        assertEquals(134, (previous + selectors).size)
        selectors.forEach { selector ->
            Stage12RuntimeTarget.requireSelection(Stage12RuntimeTarget.Target.SAMSUNG, listOf(selector), "REAL", false, true)
            for ((clock, observation, explicit) in listOf(Triple("FIXED", false, true), Triple("REAL", true, true), Triple("REAL", false, false))) {
                assertThrows(IllegalStateException::class.java) {
                    Stage12RuntimeTarget.requireSelection(Stage12RuntimeTarget.Target.SAMSUNG, listOf(selector), clock, observation, explicit)
                }
            }
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(Stage12RuntimeTarget.Target.SAMSUNG, listOf(selector, selector), "REAL", false, true)
            }
        }
        for (unreviewed in listOf("com.blackatsystems.miguardia.stage12.Stage12SelectionBatchTest",
            "com.blackatsystems.miguardia.stage12.Stage12SelectionBatchTest#clearReceivedStore")) {
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(Stage12RuntimeTarget.Target.SAMSUNG, listOf(unreviewed), "REAL", false, true)
            }
        }
    }
    @Test fun SamsungC026RepresentativeRequiresItsExactSingleSelectorAndRealClock() {
        val c026 = "com.blackatsystems.miguardia.stage12.Stage12TemporalLiteralAndroidTest#shiftEndpointsAndHistoricalDetailStayExact"
        val target = Stage12RuntimeTarget.Target.SAMSUNG
        Stage12RuntimeTarget.requireSelection(target, listOf(c026), "REAL", false, true)
        for (selectors in listOf(listOf(c026, c026), listOf(c026, Stage12RuntimeTarget.MONTH_NAVIGATION),
            listOf(Stage12RuntimeTarget.MONTH_NAVIGATION, c026))) {
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(target, selectors, "REAL", false, true)
            }
        }
        for ((clock, observation, explicit) in listOf(Triple("FIXED", false, true), Triple("REAL", true, true), Triple("REAL", false, false))) {
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(target, listOf(c026), clock, observation, explicit)
            }
        }
        for (selector in Stage12B1Selectors.temporalLiteralFamily) {
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(aosp(), listOf(selector), "REAL", false, true)
            }
            if (selector != c026) assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(target, listOf(selector), "REAL", false, true)
            }
        }
    }

    private val c026 = "com.blackatsystems.miguardia.stage12.Stage12TemporalLiteralAndroidTest#shiftEndpointsAndHistoricalDetailStayExact"
    private fun api37() = Stage12RuntimeTarget.identify(37, "SM-S938B", "samsung", "observed", "observed", "arm64-v8a")

    @Test fun api37SamsungHasItsExplicitTargetWithoutReplacingApi36() {
        assertEquals(Stage12RuntimeTarget.Target.SAMSUNG_TEMPORAL_API37, api37())
        assertEquals(Stage12RuntimeTarget.Target.SAMSUNG,
            Stage12RuntimeTarget.identify(36, "SM-S938B", "samsung", "observed", "observed", "arm64-v8a"))
        val a119Durations = "com.blackatsystems.miguardia.stage12.Stage12TemporalLiteralAndroidTest#availabilityDurationsKeepFullEndpointsAndReadback"
        val a119Resumption = "com.blackatsystems.miguardia.stage12.Stage12TemporalLiteralAndroidTest#availabilityResumptionKeepsOwnerAndUuidThroughRealWidgetTouch"
        val admitted = setOf(c026, a119Durations, a119Resumption)
        assertEquals(admitted, Stage12B1Selectors.temporalLiteralFamily)
        for (selector in admitted) {
            Stage12RuntimeTarget.requireSelection(api37(), listOf(selector), "REAL", false, true)
        }
        assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(api37(), listOf(a119Durations, a119Resumption), "REAL", false, true)
        }
    }
    @Test fun api37RejectsEveryOtherReviewedFamilyAndUnreviewedSelectors() {
        val previous = Stage12B1Selectors.compose + Stage12B1Selectors.ownedWorkFamily +
            Stage12B1Selectors.selectionBatchFamily + Stage12B1Selectors.recurringLifecycleFamily +
            Stage12B1Selectors.calendarExceptionsFamily + Stage12B1Selectors.medicalDraftRegression +
            Stage12B1Selectors.temporalLiteralFamily + setOf(Stage12B1Selectors.OWNED_LOAD_EDIT,
                Stage12RuntimeTarget.MONTH_NAVIGATION, Stage12RuntimeTarget.TODAY_DETAIL,
                Stage12RuntimeTarget.W010, Stage12RuntimeTarget.C090, Stage12RuntimeTarget.X015, Stage12RuntimeTarget.WORK_FIXTURE,
                "com.blackatsystems.miguardia.stage12.Stage12TemporalLiteralAndroidTest",
                "com.blackatsystems.miguardia.stage12.Stage12AvailabilityLifecycleTest", "unknown#method")
        val b1Closure = setOf(Stage12B1Selectors.OWNED_LOAD_EDIT,
            "com.blackatsystems.miguardia.stage12.Stage12RecurringLifecycleTest#mixedOccupancyPoliciesUseExactPreviewsAndAtomicRealWrites")
        for (selector in previous - Stage12B1Selectors.temporalLiteralFamily - b1Closure) assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(api37(), listOf(selector), "REAL", false, true)
        }
    }
    @Test fun stage14RuleAdmitsOnlyItsOwnFamilyOnSamsungWithARealClock() {
        val own = com.blackatsystems.miguardia.stage14.Stage14Selectors.family.toList()
        Stage12RuntimeTarget.requireSelection(api37(), own, "REAL", false, true)
        Stage12RuntimeTarget.requireSelection(api37(), own.take(1), "REAL", false, true)
        assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(api37(), own.take(1) + Stage12B1Selectors.OWNED_LOAD_EDIT, "REAL", false, true)
        }
        assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(api37(), own, "FIXED", false, true)
        }
        assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(api37(), own, "REAL", false, false)
        }
    }
    @Test fun stage135RuleNeedsAnOwnMethodAndAdmitsOnlyItsScreenRegression() {
        val own = com.blackatsystems.miguardia.stage135.Stage135Selectors.own.toList()
        val regression = com.blackatsystems.miguardia.stage135.Stage135Selectors.screenRegression.toList()
        Stage12RuntimeTarget.requireSelection(api37(), own, "REAL", false, true)
        Stage12RuntimeTarget.requireSelection(api37(), own.take(1) + regression, "REAL", false, true)
        // Alone, the earlier screen methods keep the rules of their own stage.
        regression.forEach { selector ->
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(api37(), listOf(selector), "REAL", false, true)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(api37(), own.take(1) + Stage12B1Selectors.OWNED_LOAD_EDIT, "REAL", false, true)
        }
        assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(api37(), own, "FIXED", false, true)
        }
        assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(api37(), own, "REAL", true, true)
        }
        assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(aosp(), own, "REAL", false, true)
        }
    }
    @Test fun api37B1ClosureAdmitsOnlyItsTwoExactRealClockSingletons() {
        val selected = listOf(Stage12B1Selectors.OWNED_LOAD_EDIT,
            "com.blackatsystems.miguardia.stage12.Stage12RecurringLifecycleTest#mixedOccupancyPoliciesUseExactPreviewsAndAtomicRealWrites")
        for (selector in selected) {
            Stage12RuntimeTarget.requireSelection(api37(), listOf(selector), "REAL", false, true)
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(api37(), listOf(selector), "FIXED", false, true)
            }
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(api37(), listOf(selector), "REAL", false, false)
            }
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(api37(), listOf(selector), "REAL", true, true)
            }
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(api37(), listOf(selector, c026), "REAL", false, true)
            }
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(api37(), listOf(selector, selector), "REAL", false, true)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(api37(), selected, "REAL", false, true)
        }
    }

    @Test fun api37RejectsMissingMixedDuplicateAndUnknownSelections() {
        for (selectors in listOf(emptyList(), listOf(c026, c026), listOf(c026, Stage12RuntimeTarget.MONTH_NAVIGATION),
            listOf(Stage12RuntimeTarget.MONTH_NAVIGATION, c026), Stage12B1Selectors.temporalLiteralFamily.toList(), listOf("C026"))) {
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(api37(), selectors, "REAL", false, true)
            }
        }
    }
    @Test fun api37RequiresExplicitRealClockAndNoSummaryObservation() {
        for (selector in Stage12B1Selectors.temporalLiteralFamily + Stage12B1Selectors.availabilityLifecycleFamily) {
            for (clock in listOf("FIXED", "STAGE12_20261023", "", "real")) assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(api37(), listOf(selector), clock, false, true)
            }
            assertThrows(IllegalStateException::class.java) { Stage12RuntimeTarget.requireSelection(api37(), listOf(selector), "REAL", false, false) }
            assertThrows(IllegalStateException::class.java) { Stage12RuntimeTarget.requireSelection(api37(), listOf(selector), "REAL", true, true) }
        }
    }
    @Test fun api37AvailabilityLifecycleRequiresOneExactSelectorAndRejectsApi36() {
        val owner = "com.blackatsystems.miguardia.stage12.Stage12AvailabilityLifecycleTest"
        val selectors = listOf(
            "$owner#configurationHistoryPreservesWindowsAndReferences",
            "$owner#protectedReviewsUseFreshSourcesAndExactBreakdowns",
            "$owner#conflictsErrorsAndConsciousDiscardPreserveTheWholeStore",
        )
        assertEquals(selectors.toSet(), Stage12B1Selectors.availabilityLifecycleFamily)
        val samsung36 = Stage12RuntimeTarget.identify(36, "SM-S938B", "samsung", "observed", "observed", "arm64-v8a")
        for (selector in selectors) {
            Stage12RuntimeTarget.requireSelection(api37(), listOf(selector), "REAL", false, true)
            for (target in listOf(samsung36, aosp())) assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(target, listOf(selector), "REAL", false, true)
            }
        }
        for (selection in listOf(selectors, listOf(selectors.first(), c026))) assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(api37(), selection, "REAL", false, true)
        }
    }
    @Test fun api37RejectsOtherModelsManufacturersAbisApisAndAospIdentity() {
        for (model in listOf("SM-S938X", "sdk_gphone64_x86_64", "")) assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.identify(37, model, "samsung", "observed", "observed", "arm64-v8a")
        }
        for (manufacturer in listOf("other", "unknown", "")) assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.identify(37, "SM-S938B", manufacturer, "observed", "observed", "arm64-v8a")
        }
        for (abi in listOf("x86_64", "x86", "armeabi-v7a", "", null)) assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.identify(37, "SM-S938B", "samsung", "observed", "observed", abi)
        }
        for (api in listOf(35, 38)) assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.identify(api, "SM-S938B", "samsung", "observed", "observed", "arm64-v8a")
        }
        assertThrows(IllegalStateException::class.java) { aosp(api = 37) }
    }
    @Test fun api36SamsungAndAospContractsKeepTheirPreviousSelectionsAndFingerprint() {
        val samsung = Stage12RuntimeTarget.identify(36, "SM-S938B", "samsung", "observed", "observed", "arm64-v8a")
        for (selector in listOf(c026, Stage12RuntimeTarget.MONTH_NAVIGATION, Stage12RuntimeTarget.TODAY_DETAIL,
            Stage12RuntimeTarget.C090, Stage12B1Selectors.OWNED_LOAD_EDIT) + Stage12B1Selectors.compose +
            Stage12B1Selectors.ownedWorkFamily + Stage12B1Selectors.selectionBatchFamily +
            Stage12B1Selectors.recurringLifecycleFamily + Stage12B1Selectors.calendarExceptionsFamily + Stage12B1Selectors.medicalDraftRegression) {
            Stage12RuntimeTarget.requireSelection(samsung, listOf(selector), "REAL", false, true)
        }
        for (selector in listOf(Stage12RuntimeTarget.W010, Stage12RuntimeTarget.C090, Stage12RuntimeTarget.X015, Stage12RuntimeTarget.WORK_FIXTURE))
            Stage12RuntimeTarget.requireSelection(aosp(), listOf(selector), "REAL", false, true)
        assertThrows(IllegalStateException::class.java) { Stage12RuntimeTarget.requireSelection(aosp(), listOf(c026), "REAL", false, true) }
        assertThrows(IllegalStateException::class.java) { aosp(fingerprint = Stage12RuntimeTarget.AOSP_FINGERPRINT + "x") }
    }

}
