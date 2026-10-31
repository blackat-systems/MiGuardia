package com.blackatsystems.miguardia.stage12

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Stage12RuntimeTargetTest {
    private fun aosp(api: Int = 36, id: String = "BE2A.250530.026.D1",
                     fingerprint: String = Stage12RuntimeTarget.AOSP_FINGERPRINT, abi: String? = "x86_64") =
        Stage12RuntimeTarget.identify(api, "sdk_gphone64_x86_64", "unknown", id, fingerprint, abi)

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
        for (api in listOf(35, 37)) assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.identify(api, "SM-S938B", "samsung", "received", "received", "arm64-v8a")
        }
        for (abi in listOf("x86_64", "armeabi-v7a", null)) assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.identify(36, "SM-S938B", "samsung", "received", "received", abi)
        }
    }
    @Test fun AospDoesNotGainSamsungSelectors() {
        for (selector in listOf(Stage12RuntimeTarget.MONTH_NAVIGATION, Stage12RuntimeTarget.TODAY_DETAIL,
            Stage12B1Selectors.OWNED_LOAD_EDIT) + Stage12B1Selectors.compose) {
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(aosp(), listOf(selector), "REAL", false, true)
            }
        }
    }
    @Test fun SamsungB1AdmitsOnlyConcreteReviewedMethodsWithExplicitRealClock() {
        val target = Stage12RuntimeTarget.Target.SAMSUNG
        val reviewed = Stage12B1Selectors.compose + setOf(Stage12B1Selectors.OWNED_LOAD_EDIT, Stage12RuntimeTarget.C090)
        assertEquals(126, reviewed.size)
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
            "com.blackatsystems.miguardia.CalendarComposeTest", "com.blackatsystems.miguardia.CalendarComposeTest#notReviewed")) {
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(target, listOf(selector), "REAL", false, true)
            }
        }
    }
}
