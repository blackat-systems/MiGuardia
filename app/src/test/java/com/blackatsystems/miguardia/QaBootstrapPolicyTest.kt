package com.blackatsystems.miguardia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QaBootstrapPolicyTest {
    @Test fun normalQaKeepsOriginalRecoveryCallbacksWhileQuietDeniesBeforeEffects() {
        listOf(null, "normal-qa", "neutral", "quiet").forEach { mode ->
            var applyCalls = 0
            var retryCalls = 0
            val apply: () -> Unit = if (QaBootstrapPolicy.isIsolationMode(mode)) {
                { throw SecurityException("isolated apply") }
            } else { { applyCalls++ } }
            val retry: () -> Unit = if (QaBootstrapPolicy.isIsolationMode(mode)) {
                { throw SecurityException("isolated recovery") }
            } else { { retryCalls++ } }
            if (mode == "neutral" || mode == "quiet") {
                assertThrows(SecurityException::class.java) { apply() }
                assertThrows(SecurityException::class.java) { retry() }
                assertEquals(0, applyCalls + retryCalls)
            } else {
                apply(); retry()
                assertEquals(1, applyCalls); assertEquals(1, retryCalls)
            }
        }
    }

    @Test fun modesRequireTheirExactReviewedRunnerRole() {
        val legacy = "com.blackatsystems.miguardia.stage12.Stage12Runner"
        val normal = "com.blackatsystems.miguardia.stage12.Stage12NormalQaRunner"
        listOf("neutral", "quiet").forEach {
            QaBootstrapPolicy.requireRunnerMode(it, legacy)
            assertThrows(IllegalStateException::class.java) { QaBootstrapPolicy.requireRunnerMode(it, normal) }
        }
        QaBootstrapPolicy.requireRunnerMode("normal-qa", normal)
        assertThrows(IllegalStateException::class.java) { QaBootstrapPolicy.requireRunnerMode("normal-qa", legacy) }
    }

    @Test fun ordinaryRunnerOrUnknownModeCannotAcquireTheScope() {
        listOf("quiet", "neutral", "normal-qa").forEach {
            assertThrows(IllegalStateException::class.java) {
                QaBootstrapPolicy.requireRunnerMode(it, "androidx.test.runner.AndroidJUnitRunner")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            QaBootstrapPolicy.requireRunnerMode("normal", "com.blackatsystems.miguardia.stage12.Stage12NormalQaRunner")
        }
    }

    @Test fun unboundHarnessCannotReachAnyStartupEffect() {
        val effects = mutableListOf<String>()
        assertThrows(SecurityException::class.java) {
            QaBootstrapPolicy.requireAdmission(harnessBuild = true, bound = false)
            effects += "recovery"
        }
        assertEquals(emptyList<String>(), effects)
    }

    @Test fun deathDoesNotInheritAnEarlierAdmission() {
        QaBootstrapPolicy.requireAdmission(harnessBuild = true, bound = true)
        assertThrows(SecurityException::class.java) {
            QaBootstrapPolicy.requireAdmission(harnessBuild = true, bound = false)
        }
    }

    @Test fun explicitReadmissionAllowsTheExistingRunnerBoundaryOnly() {
        QaBootstrapPolicy.requireAdmission(harnessBuild = true, bound = true)
    }

    @Test fun ordinaryBuildDoesNotAcquireAHarnessRequirement() {
        QaBootstrapPolicy.requireAdmission(harnessBuild = false, bound = false)
        QaBootstrapPolicy.requireAdmission(harnessBuild = false, bound = true)
    }
}
