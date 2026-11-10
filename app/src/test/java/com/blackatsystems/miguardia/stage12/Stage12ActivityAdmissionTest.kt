package com.blackatsystems.miguardia.stage12

import org.junit.Assert.*
import org.junit.Test
import com.blackatsystems.miguardia.stage12.Stage12ActivityAdmission.*

class Stage12ActivityAdmissionTest {
    private fun good() = Snapshot(true, Foreground.OWN, true, false, Life.RESUMED, true, true, Startup.READY, true, true)
    private fun admission() = Stage12ActivityAdmission(true).apply { phase(Phase.READINESS) }

    @Test fun ownInitialTransitionWaitsPassivelyAndOnlyReadyAdmitsTheBody() {
        val a = admission()
        assertEquals(State.PREPARING, a.observe(good().copy(foreground = Foreground.NONE, attached = false,
            focused = false, startup = Startup.RECOVERING, accessAllowed = false), 0))
        assertThrows(IllegalStateException::class.java) { a.requireReady() }
        assertEquals(State.PREPARING, a.observe(good().copy(focused = false), 50))
        assertEquals(State.READY, a.observe(good(), 100)); a.requireReady()
    }

    @Test fun recoveringAccessIsNotConfusedWithReadyButBlockedAccess() {
        val a = admission()
        assertEquals(State.PREPARING, a.observe(good().copy(startup = Startup.RECOVERING, accessAllowed = false), 0))
        assertEquals(State.REJECTED, a.observe(good().copy(accessAllowed = false), 50))
        assertEquals(Reason.ACCESS_BLOCKED, a.reason)
    }

    @Test fun eachFatalPredicateRetainsItsExactReasonAndCannotAdmitActions() {
        val bad = listOf(good().copy(contextQa = false) to Reason.CONTEXT_NOT_QA,
            good().copy(screenOn = false) to Reason.SCREEN_OFF,
            good().copy(keyguardLocked = true) to Reason.KEYGUARD_LOCKED,
            good().copy(foreground = Foreground.OTHER) to Reason.FOREGROUND_OTHER,
            good().copy(life = Life.DESTROYED) to Reason.ACTIVITY_DESTROYED,
            good().copy(startup = Startup.FAILED) to Reason.STARTUP_FAILED,
            good().copy(accessPhase = Access.FAILED) to Reason.ACCESS_FAILED,
            good().copy(onMainThread = false) to Reason.WRONG_THREAD)
        bad.forEach { (snapshot, expected) ->
            val a = admission(); assertEquals(State.REJECTED, a.observe(snapshot, 0))
            assertEquals(expected, a.reason)
            assertThrows(IllegalStateException::class.java) { a.requireReady() }
        }
    }

    @Test fun unknownForegroundNeverBecomesReadyAndDeadlineIsNotResetByChanges() {
        val a = admission()
        a.observe(good().copy(foreground = Foreground.NONE), 0)
        assertEquals(State.PREPARING, a.observe(good().copy(focused = false), 29_999))
        assertEquals(State.REJECTED, a.observe(good(), 30_000))
        assertEquals(Reason.DEADLINE_EXCEEDED, a.reason)
        val b = admission(); assertEquals(State.REJECTED, b.observe(good(), 30_001))
    }

    @Test fun rejectionIsTerminalAndReadyLossDoesNotStartAnotherWait() {
        val a = admission(); a.observe(good().copy(foreground = Foreground.OTHER), 0)
        assertEquals(State.REJECTED, a.observe(good(), 50)); assertEquals(Reason.FOREGROUND_OTHER, a.reason)
        val b = admission(); b.observe(good(), 0)
        assertEquals(State.REJECTED, b.observe(good().copy(foreground = Foreground.NONE), 50))
        assertEquals(Reason.READY_LOST, b.reason)
    }

    @Test fun failedReadOrWrongThreadNeverFabricatesAFullyFalseSnapshot() {
        val a = admission(); a.readFailed(); assertEquals(Reason.READ_FAILED, a.reason)
        assertTrue(a.wire().contains("\"last\":null"))
        val b = admission(); b.readFailed(true); assertEquals(Reason.WRONG_THREAD, b.reason)
    }

    @Test fun accessLoadingAfterStartupReadyWaitsWithoutAdmittingContent() {
        val a = admission()
        assertEquals(State.PREPARING, a.observe(good().copy(accessPhase = Access.LOADING, accessAllowed = false), 0))
        assertThrows(IllegalStateException::class.java) { a.requireReady() }
        assertEquals(State.READY, a.observe(good(), 50))
        val b = admission()
        assertEquals(State.PREPARING, b.observe(good().copy(accessPhase = Access.WAITING, accessAllowed = true), 0))
        assertThrows(IllegalStateException::class.java) { b.requireReady() }
    }

    @Test fun technicalStatusAndTerminalDropEveryPrivateStringAndUnknownField() {
        val a = admission(); a.readFailed()
        val original = IllegalStateException("PRIVATE_MESSAGE", IllegalArgumentException("PRIVATE_CAUSE"))
        original.addSuppressed(java.io.IOException("PRIVATE_SUPPRESSED")); a.failed(original); a.finish(false)
        val unsafe = mapOf("numtests" to 1, "stack" to "PRIVATE_STACK", "stream" to "PRIVATE_STREAM",
            "shortMsg" to "PRIVATE_SHORT", "longMsg" to "PRIVATE_LONG", "arbitrary" to "PRIVATE_EXTRA")
        val selector = "owned.Admission#only"
        for (code in listOf(1, 0, -2)) {
            val safe = Stage12AdmissionProjection.status(selector, unsafe, code, a)
            assertFalse(safe.toString().contains("PRIVATE_"))
            assertTrue(safe.keys.all { it in setOf("class", "test", "id", "numtests", "stream", "stack") })
        }
        val terminal = Stage12AdmissionProjection.terminal(a)
        assertEquals(setOf("stream", "stage12ActivityAdmission"), terminal.keys)
        assertFalse(terminal.toString().contains("PRIVATE_"))
        assertEquals(1, original.suppressed.size) // Projection never mutates the engine Throwable.
    }

    @Test fun sanitationDoesNotTurnOriginalFailureOrContradictoryPassIntoSuccess() {
        val selector = "owned.Admission#only"
        for ((code, expectedFails) in listOf(-2 to 1, 0 to 0)) {
            val counts = Stage12NormalQaCounts(setOf(selector))
            counts.observe(selector, 1, 1, false); counts.observe(selector, 1, code, true)
            val a = admission(); a.readFailed()
            val projected = Stage12AdmissionProjection.status(selector, mapOf("numtests" to 1, "stack" to "PRIVATE"), code, a)
            val terminal = counts.finish(true)
            assertFalse(terminal.complete); assertTrue(terminal.malformed); assertEquals(expectedFails, terminal.failed)
            assertTrue((projected["stack"] as String).isNotBlank())
        }
    }

    @Test fun technicalSelectorIsSoleAndRealClockWhileFunctionalSelectorsRemainDistinct() {
        val functional = setOf("owned.Functional#one", "owned.Functional#two"); val technical = "owned.Admission#only"
        assertTrue(Stage12AdmissionProjection.requireScope(listOf(technical), functional, technical, "REAL"))
        assertFalse(Stage12AdmissionProjection.requireScope(functional.toList(), functional, technical, "STAGE12_20261023"))
        for (selectors in listOf(emptyList(), listOf(technical, functional.first()), listOf(technical, technical), listOf("foreign#one"))) {
            assertThrows(IllegalArgumentException::class.java) { Stage12AdmissionProjection.requireScope(selectors, functional, technical, "REAL") }
        }
        assertThrows(IllegalArgumentException::class.java) { Stage12AdmissionProjection.requireScope(listOf(technical), functional, technical, "STAGE12_20261023") }
    }

    @Test fun changeTraceIsBoundedInUtf8BytesAndDoesNotStoreArbitraryStrings() {
        val a = admission()
        repeat(100) { a.observe(good().copy(focused = false, attached = it % 2 == 0), it * 50L) }
        assertTrue(a.wire().contains("\"trace_truncated\":true"))
        assertTrue(a.wire().toByteArray(Charsets.UTF_8).size <= 8192)
        assertEquals(8, Regex("\"snapshot\":").findAll(a.wire()).count())
    }

    @Test fun emitsActualProducerGoldensForHostInteroperabilityIncludingLatePublicationFailure() {
        val ready = admission(); ready.observe(good(), 100); ready.finish(true)
        println("ADMISSION_GOLDEN_READY=" + ready.wire())
        val locked = admission(); locked.observe(good().copy(keyguardLocked = true), 10)
        locked.failed(Stage12ReadinessRejected(locked)); locked.finish(false)
        println("ADMISSION_GOLDEN_LOCKED=" + locked.wire())
        val late = admission(); late.observe(good(), 10); late.phase(Phase.PUBLISH)
        late.failed(java.io.IOException("PRIVATE_PUBLISH_MESSAGE")); late.finish(false)
        assertTrue(late.wire().contains("\"result_reason\":\"PUBLICATION_FAILED\""))
        println("ADMISSION_GOLDEN_LATE_PUBLISH=" + late.wire())
        val read = admission(); read.readFailed(); read.failed(IllegalStateException("PRIVATE_READ")); read.finish(false)
        println("ADMISSION_GOLDEN_READ_FAILED=" + read.wire())
    }

    @Test fun secondaryPublicationAndCloseFailuresRemainVisibleWithoutReplacingTheFirstCause() {
        val a = admission(); a.observe(good().copy(keyguardLocked = true), 0)
        a.failed(Stage12ReadinessRejected(a)); a.phase(Phase.CLOSE); a.failed(Failure.STATE)
        a.phase(Phase.PUBLISH); a.failed(Failure.IO); a.finish(false)
        val wire = a.wire()
        assertTrue(wire.contains("\"reason\":\"KEYGUARD_LOCKED\""))
        assertTrue(wire.contains("\"failure_phase\":\"READINESS\""))
        assertTrue(wire.contains("\"close_failed\":true"))
        assertTrue(wire.contains("\"publication_failed\":true"))
        assertTrue(wire.contains("\"result_reason\":\"PUBLICATION_FAILED\""))
        val b = admission(); b.observe(good(), 0); b.phase(Phase.POSTCONDITION); b.failed(Failure.IO); b.finish(false)
        assertTrue(b.wire().contains("\"close_failed\":false"))
    }

    @Test fun finalSanitationRetainsTheSafeFailureAfterTheCurrentReportWasReset() {
        val failed = admission(); failed.readFailed(); failed.failed(Failure.OTHER); failed.finish(false)
        val terminalReport = Stage12AdmissionProjection.terminalReport(false, true, null, failed)
        assertSame(failed, terminalReport)
        assertTrue(Stage12AdmissionProjection.terminal(requireNotNull(terminalReport)).toString().contains("READ_FAILED"))
        assertFalse(Stage12AdmissionProjection.terminal(terminalReport).toString().contains("PRIVATE_A"))
        val fallback = Stage12AdmissionProjection.terminalReport(false, true, null, null)
        assertNotNull(fallback)
        assertTrue(requireNotNull(fallback).wire().contains("\"state\":\"REJECTED\""))
    }

    @Test fun absentDecorRemainsUnmeasuredAndDestroyedScenarioHasAnExplicitReason() {
        val a = admission()
        assertEquals(State.PREPARING, a.observe(good().copy(decorPresent = false, attached = null, focused = null), 0))
        assertTrue(a.wire().contains("\"attached\":null"))
        assertThrows(IllegalStateException::class.java) { a.requireReady() }
        val b = admission(); b.activityDestroyed()
        assertEquals(Reason.ACTIVITY_DESTROYED, b.reason)
        assertTrue(b.wire().contains("\"last\":null"))
    }

    @Test fun x015StepSurvivesFilteredFailureBeforeAndAfterReadyWithoutPrivateThrowableText() {
        val selector = Stage12AdmissionProjection.X015_SELECTOR
        for (ready in listOf(false, true)) {
            val a = Stage12ActivityAdmission(false)
            if (ready) a.observe(good(), 0)
            a.phase(Phase.BODY); a.x015Step(X015Step.BEFORE_DAY_BACK)
            val first = NullPointerException("PRIVATE_X015_MESSAGE")
            first.addSuppressed(java.io.IOException("PRIVATE_X015_SECONDARY"))
            a.failed(first)
            a.x015Step(X015Step.PUBLISH); a.phase(Phase.PUBLISH); a.failed(Failure.IO); a.finish(false)
            assertTrue(Stage12AdmissionProjection.needsSafeStatus(false, selector, a))
            assertTrue(Stage12AdmissionProjection.needsSafeTerminal(false, true, setOf(selector)))
            val safe = Stage12AdmissionProjection.status(selector,
                mapOf("numtests" to 1, "stack" to first.stackTraceToString(), "stream" to "PRIVATE_STREAM", "unknown" to "PRIVATE_EXTRA"), -2, a)
            val terminal = Stage12AdmissionProjection.terminal(a)
            for (value in listOf(safe.toString(), terminal.toString())) {
                assertTrue(value.contains("\"x015_failure_step\":\"BEFORE_DAY_BACK\""))
                assertTrue(value.contains("\"x015_route_step\":\"PUBLISH\""))
                assertFalse(value.contains("PRIVATE_"))
            }
            assertEquals("PRIVATE_X015_MESSAGE", first.message)
            assertEquals("PRIVATE_X015_SECONDARY", first.suppressed.single().message)
            assertTrue(a.wire().contains("\"failure_kind\":\"OTHER\""))
            val counts = Stage12NormalQaCounts(setOf(selector))
            counts.observe(selector, 1, 1, false); counts.observe(selector, 1, -2, true)
            val result = counts.finish(true)
            assertEquals(1, result.failed); assertFalse(result.complete); assertTrue(result.malformed)
            val encoded = java.util.Base64.getEncoder().encodeToString((safe["stack"] as String).toByteArray(Charsets.UTF_8))
            println("X015_SAFE_FAILURE_${if (ready) "READY" else "PRE_READY"}=$encoded")
        }
    }

    @Test fun x015SafeUnknownStartDoesNotInventReadinessOrAnObservedRouteStep() {
        val selector = Stage12AdmissionProjection.X015_SELECTOR
        val unknown = Stage12ActivityAdmission(false).apply { x015Step(X015Step.UNKNOWN) }
        assertTrue(Stage12AdmissionProjection.needsSafeStatus(false, selector, null))
        assertTrue(Stage12AdmissionProjection.needsSafeTerminal(false, false, setOf(selector)))
        val safe = Stage12AdmissionProjection.status(selector,
            mapOf("numtests" to 1, "stream" to "PRIVATE_START", "stack" to "", "unknown" to "PRIVATE_EXTRA"), 1, unknown)
        assertFalse(safe.toString().contains("PRIVATE_"))
        assertFalse(safe.containsKey("stack"))
        assertTrue((safe["stream"] as String).contains("\"x015_route_step\":\"UNKNOWN\""))
        assertFalse(unknown.everReady)
        assertTrue(unknown.wire().contains("\"last\":null"))
        val ordinary = Stage12ActivityAdmission(false).apply { observe(good(), 0) }
        assertFalse(Stage12AdmissionProjection.needsSafeStatus(false, "owned.Ordinary#one", ordinary))
        assertFalse(Stage12AdmissionProjection.needsSafeTerminal(false, false, setOf("owned.Ordinary#one")))
        assertFalse(ordinary.wire().contains("x015_route_step"))
    }

    @Test fun x015FirstFailureStepCannotBeOverwrittenByCleanupOrPublication() {
        val a = Stage12ActivityAdmission(false).apply { observe(good(), 0); phase(Phase.BODY) }
        a.x015Step(X015Step.WAIT_DAY_DISMISSED); a.failed(Failure.STATE)
        a.x015Step(X015Step.CLOSE_ACTIVITY); a.phase(Phase.CLOSE); a.failed(Failure.OTHER)
        a.x015Step(X015Step.PUBLISH); a.phase(Phase.PUBLISH); a.failed(Failure.IO); a.finish(false)
        assertTrue(a.wire().contains("\"x015_failure_step\":\"WAIT_DAY_DISMISSED\""))
        assertTrue(a.wire().contains("\"failure_phase\":\"BODY\""))
        assertTrue(a.wire().contains("\"failure_kind\":\"STATE\""))
        assertTrue(a.wire().contains("\"close_failed\":true"))
        assertTrue(a.wire().contains("\"publication_failed\":true"))
    }

    @Test fun earlyUnadmittedFailureDoesNotNeedAnInitializedSelectorOrExposeItsBundle() {
        val selected = emptySet<String>()
        val selector = Stage12AdmissionProjection.statusSelector("PRIVATE_CLASS#PRIVATE_METHOD", selected)
        assertEquals(Stage12AdmissionProjection.UNKNOWN_SELECTOR, selector)
        assertTrue(Stage12AdmissionProjection.needsSafeStatus(false, selector, null, selected, admitted = false))
        assertTrue(Stage12AdmissionProjection.needsSafeTerminal(false, false, selected, admitted = false))
        val unknown = Stage12ActivityAdmission(false).apply { failed(Failure.OTHER) }
        val safe = Stage12AdmissionProjection.status(selector,
            mapOf("numtests" to -1, "stack" to "PRIVATE_ORIGINAL", "unknown" to "PRIVATE_EXTRA"), -2, unknown)
        assertFalse(safe.toString().contains("PRIVATE_"))
        assertEquals("stage12.Unknown", safe["class"])
        assertEquals("unattributed", safe["test"])
        assertTrue(unknown.wire().contains("\"last\":null"))
        assertFalse(unknown.wire().contains("x015_route_step"))
        // onCreate may have filled expected before validating scope or setting admitted=true.
        for (unvalidated in listOf("PRIVATE_CLASS#PRIVATE_METHOD", "PRIVATE_MALFORMED")) {
            val partial = Stage12AdmissionProjection.statusSelector(unvalidated, setOf(unvalidated), admitted = false)
            assertEquals(Stage12AdmissionProjection.UNKNOWN_SELECTOR, partial)
            val projected = Stage12AdmissionProjection.status(partial,
                mapOf("numtests" to -1, "stack" to "PRIVATE_ORIGINAL"), -2, unknown)
            assertFalse(projected.toString().contains("PRIVATE_"))
        }
    }

    @Test fun mixedX015SelectionFiltersUnknownIdentityWithoutAttributingItToAnotherMethod() {
        val ordinary = "owned.C090#one"
        val selected = setOf(ordinary, Stage12AdmissionProjection.X015_SELECTOR)
        val original = "PRIVATE_UNKNOWN#PRIVATE_UNKNOWN"
        val selector = Stage12AdmissionProjection.statusSelector(original, selected)
        assertEquals(Stage12AdmissionProjection.UNKNOWN_SELECTOR, selector)
        val ready = Stage12ActivityAdmission(false).apply { observe(good(), 0) }
        assertTrue(Stage12AdmissionProjection.needsSafeStatus(false, selector, ready, selected))
        assertTrue(Stage12AdmissionProjection.needsSafeStatus(false, ordinary, ready, selected))
        val safe = Stage12AdmissionProjection.status(selector,
            mapOf("numtests" to 2, "stack" to "PRIVATE_STACK", "stream" to "PRIVATE_STREAM"), -2, ready)
        assertEquals("stage12.Unknown", safe["class"])
        assertFalse(safe.toString().contains("PRIVATE_"))
        val counts = Stage12NormalQaCounts(selected)
        counts.observe(original, 2, -2, true) // Accounting sees the original identity before filtering.
        assertFalse(counts.finish(true).complete)
    }

    @Test fun x015BackReadsAndCallsEachOperationOnceInTheOriginalOrder() {
        val a = Stage12ActivityAdmission(false).apply { observe(good(), 0); phase(Phase.BODY) }
        val token = Any(); val calls = mutableListOf<String>()
        a.x015Back({ calls += "acquire"; token },
            { assertSame(token, it); calls += "foreground"; Foreground.OWN },
            { calls += "lifecycle"; Life.RESUMED },
            { assertSame(token, it); calls += "back"; true })
        assertEquals(listOf("acquire", "foreground", "lifecycle", "back"), calls)
        val wire = a.wire()
        assertTrue(wire.contains("\"x015_back_foreground\":\"OWN\""))
        assertTrue(wire.contains("\"x015_back_lifecycle\":\"RESUMED\""))
        assertTrue(wire.contains("\"x015_back_result\":true"))
        assertTrue(wire.contains("\"x015_route_step\":\"DAY_BACK_CHECK_RESULT\""))
    }

    @Test fun x015BackThrowsAndFailedGuardsKeepTheirExactFirstSubstepThroughSafeProjection() {
        val points = listOf(X015Step.DAY_BACK_ACQUIRE_DEVICE, X015Step.DAY_BACK_READ_FOREGROUND,
            X015Step.DAY_BACK_CHECK_FOREGROUND, X015Step.DAY_BACK_READ_LIFECYCLE,
            X015Step.DAY_BACK_CHECK_RESUMED, X015Step.DAY_BACK_CALL, X015Step.DAY_BACK_CHECK_RESULT)
        for (point in points) {
            val a = Stage12ActivityAdmission(false).apply { observe(good(), 0); phase(Phase.BODY) }
            val original = java.io.IOException("PRIVATE_OPERATION_$point")
            val calls = mutableListOf<String>()
            val thrown = assertThrows(Throwable::class.java) {
                a.x015Back(
                    { calls += "acquire"; if (point == X015Step.DAY_BACK_ACQUIRE_DEVICE) throw original; Unit },
                    { calls += "foreground"; if (point == X015Step.DAY_BACK_READ_FOREGROUND) throw original
                        if (point == X015Step.DAY_BACK_CHECK_FOREGROUND) Foreground.OTHER else Foreground.OWN },
                    { calls += "lifecycle"; if (point == X015Step.DAY_BACK_READ_LIFECYCLE) throw original
                        if (point == X015Step.DAY_BACK_CHECK_RESUMED) Life.STARTED else Life.RESUMED },
                    { calls += "back"; if (point == X015Step.DAY_BACK_CALL) throw original
                        point != X015Step.DAY_BACK_CHECK_RESULT },
                )
            }
            if (point in setOf(X015Step.DAY_BACK_ACQUIRE_DEVICE, X015Step.DAY_BACK_READ_FOREGROUND,
                    X015Step.DAY_BACK_READ_LIFECYCLE, X015Step.DAY_BACK_CALL)) assertSame(original, thrown)
            a.failed(thrown)
            val expectedCalls = when (point) {
                X015Step.DAY_BACK_ACQUIRE_DEVICE -> listOf("acquire")
                X015Step.DAY_BACK_READ_FOREGROUND, X015Step.DAY_BACK_CHECK_FOREGROUND -> listOf("acquire", "foreground")
                X015Step.DAY_BACK_READ_LIFECYCLE, X015Step.DAY_BACK_CHECK_RESUMED -> listOf("acquire", "foreground", "lifecycle")
                else -> listOf("acquire", "foreground", "lifecycle", "back")
            }
            assertEquals(expectedCalls, calls)
            a.x015Step(X015Step.PUBLISH); a.phase(Phase.PUBLISH)
            val secondary = IllegalStateException("PRIVATE_SECONDARY")
            assertSame(thrown, Stage12W010Oracle.retain(thrown, secondary)); a.failed(secondary); a.finish(false)
            val safe = Stage12AdmissionProjection.status(Stage12AdmissionProjection.X015_SELECTOR,
                mapOf("numtests" to 1, "stack" to thrown.stackTraceToString()), -2, a)
            assertTrue(safe.toString().contains("\"x015_failure_step\":\"${point.name}\""))
            assertFalse(safe.toString().contains("PRIVATE_"))
            if (point == X015Step.DAY_BACK_CHECK_RESULT) assertTrue(a.wire().contains("\"x015_back_result\":false"))
            else assertTrue(a.wire().contains("\"x015_back_result\":null"))
            val encoded = java.util.Base64.getEncoder().encodeToString((safe["stack"] as String).toByteArray(Charsets.UTF_8))
            println("X015_BACK_SAFE_${point.name}=$encoded")
        }
    }

    @Test fun x015BackNoneAndOtherForegroundShortCircuitBeforeLifecycleAndInput() {
        for (foreground in listOf(Foreground.NONE, Foreground.OTHER)) {
            val a = Stage12ActivityAdmission(false)
            assertThrows(IllegalStateException::class.java) {
                a.x015Back({ Unit }, { foreground },
                    { error("LIFECYCLE_MUST_NOT_BE_READ") }, { error("BACK_MUST_NOT_BE_CALLED") })
            }
            val wire = a.wire()
            assertTrue(wire.contains("\"x015_back_foreground\":\"${foreground.name}\""))
            assertTrue(wire.contains("\"x015_back_lifecycle\":null"))
            assertTrue(wire.contains("\"x015_back_result\":null"))
        }
    }

    @Test fun b1OwnedWritesKeepFailureAndTerminalMetadataPrivateEvenAfterReadiness() {
        for (selector in setOf(Stage12B1Selectors.OWNED_LOAD_EDIT, Stage12RuntimeTarget.C090)) {
        val selected = setOf(selector)
        val a = Stage12ActivityAdmission(false).apply { observe(good(), 0); phase(Phase.BODY) }
        a.failed(IllegalStateException("PRIVATE_B1_PATH_AND_DATA"))
        assertTrue(Stage12AdmissionProjection.needsSafeStatus(false, selector, a, selected))
        assertTrue(Stage12AdmissionProjection.needsSafeTerminal(false, false, selected))
        val status = Stage12AdmissionProjection.status(selector,
            mapOf("numtests" to 1, "stack" to "PRIVATE_B1_PATH_AND_DATA", "extra" to "PRIVATE_B1_PATH_AND_DATA"), -2, a)
        assertFalse(status.toString().contains("PRIVATE_B1_PATH_AND_DATA"))
        a.phase(Phase.PUBLISH)
        assertFalse(Stage12AdmissionProjection.terminal(a).toString().contains("PRIVATE_B1_PATH_AND_DATA"))
        for (phase in listOf(Phase.CLOSE, Phase.PUBLISH)) {
            val failed = Stage12ActivityAdmission(false).apply { observe(good(), 0); phase(phase); failed(IllegalStateException("PRIVATE_B1")) }
            assertTrue(Stage12AdmissionProjection.needsSafeTerminal(false, true, selected))
            assertFalse(Stage12AdmissionProjection.terminal(failed).toString().contains("PRIVATE_B1"))
        }
        }
    }

    @Test fun x015BackUnknownIsNotFalseAndAFalseReturnRemainsMeasuredAfterFailure() {
        val untouched = Stage12ActivityAdmission(false).apply { x015Step(X015Step.UNKNOWN) }
        assertTrue(untouched.wire().contains("\"x015_back_foreground\":null"))
        assertTrue(untouched.wire().contains("\"x015_back_lifecycle\":null"))
        assertTrue(untouched.wire().contains("\"x015_back_result\":null"))
        val a = Stage12ActivityAdmission(false).apply { observe(good(), 0); phase(Phase.BODY) }
        val thrown = assertThrows(IllegalStateException::class.java) {
            a.x015Back({ Unit }, { Foreground.OWN }, { Life.RESUMED }, { false })
        }
        a.failed(thrown); a.x015Step(X015Step.PUBLISH); a.phase(Phase.PUBLISH); a.finish(false)
        assertTrue(a.wire().contains("\"x015_failure_step\":\"DAY_BACK_CHECK_RESULT\""))
        assertTrue(a.wire().contains("\"x015_back_result\":false"))
    }
}
