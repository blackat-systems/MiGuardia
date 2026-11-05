package com.blackatsystems.miguardia.stage12

import org.junit.Assert.assertThrows
import org.junit.Test

class Stage12CalendarWeatherAdmissionTest {
    @Test fun calendarPrivacyComplementsRejectMixedSelectionsAndOtherDevices() {
        val target = Stage12RuntimeTarget.Target.SAMSUNG_TEMPORAL_API37
        val methods = Stage12B1Selectors.calendarPrivacyComplements
        for (method in methods) {
            Stage12RuntimeTarget.requireSelection(target, listOf(method), "REAL", false, true)
            for (other in Stage12RuntimeTarget.Target.entries.filter { it != target }) assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(other, listOf(method), "REAL", false, true)
            }
            for ((clock, summary, explicit) in listOf(Triple("FIXED", false, true), Triple("REAL", true, true), Triple("REAL", false, false))) {
                assertThrows(IllegalStateException::class.java) {
                    Stage12RuntimeTarget.requireSelection(target, listOf(method), clock, summary, explicit)
                }
            }
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(target, listOf(method, Stage12CalendarDetailContract.SELECTOR), "REAL", false, true)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            Stage12RuntimeTarget.requireSelection(target, methods.toList(), "REAL", false, true)
        }
    }
    @Test fun weatherActivationAndOriginalDetailRequireIndependentExactSingletons() {
        val target = Stage12RuntimeTarget.Target.SAMSUNG_TEMPORAL_API37
        val methods = listOf(Stage12CalendarDetailContract.SELECTOR, Stage12CalendarDetailContract.ACTIVATION_SELECTOR)
        for (method in methods) {
            Stage12RuntimeTarget.requireSelection(target, listOf(method), "REAL", false, true)
            for (other in Stage12RuntimeTarget.Target.entries.filter { it != target }) {
                assertThrows(IllegalStateException::class.java) {
                    Stage12RuntimeTarget.requireSelection(other, listOf(method), "REAL", false, true)
                }
            }
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(target, listOf(method), "FIXED", false, true)
            }
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(target, listOf(method), "REAL", false, false)
            }
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(target, listOf(method), "REAL", true, true)
            }
        }
        for (selection in listOf(methods, methods.reversed(), listOf(methods.last(), methods.last()))) {
            assertThrows(IllegalStateException::class.java) {
                Stage12RuntimeTarget.requireSelection(target, selection, "REAL", false, true)
            }
        }
    }
}
