package com.blackatsystems.miguardia.stage12

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement
import org.junit.runners.model.FrameworkMethod
import org.junit.internal.runners.statements.RunAfters

class Stage12SummaryObservationTest {
    private fun ready() = Stage12SummaryObservation.Snapshot(true, true, true, true, true,
        Stage12SummaryObservation.Life.RESUMED, false, false, true, true, true,
        true, false)

    private fun completedBeforePublish() = Stage12SummaryObservation().apply {
        beginBody(); sample(ready()); requireOwnerSafe()
        contentRequested(); contentEntered(true)
        phase(Stage12SummaryObservation.Phase.AFTER_SET_CONTENT); sample(ready()); overview(1)
        phase(Stage12SummaryObservation.Phase.BEFORE_CLICK); sample(ready()); requireActionSafe()
        enteringClick(); callback(true, true); returnedClick(); detailAsserted(); completedBody()
        contentDisposed(true); ruleReturned(); phase(Stage12SummaryObservation.Phase.CLOSE_SAMPLE)
        sample(ready().copy(life = Stage12SummaryObservation.Life.DESTROYED, destroyed = true,
            decorPresent = null, attached = null, focused = null,
            interactive = null, keyguardLocked = null))
        closed(true); requireCompleteBeforePublish(); phase(Stage12SummaryObservation.Phase.PUBLISH)
    }

    @Test fun anEarlyFailureKeepsUnmeasuredFieldsUnknown() {
        val report = Stage12SummaryObservation()
        report.phase(Stage12SummaryObservation.Phase.FILES)
        report.failed(Stage12SummaryObservation.Failure.IO, Stage12SummaryObservation.Reason.FILES_FAILED)
        report.finish(false)
        val wire = report.wire()
        for (key in listOf("last", "content_enters", "overview_nodes", "click_calls", "callback_calls", "close_observed"))
            assertTrue(wire.contains("\"$key\":null"))
        assertTrue(wire.contains("\"failure_phase\":\"FILES\""))
        println("STAGE12_SUMMARY_GOLDEN_EARLY=$wire")
    }

    @Test fun unknownOwnershipIsRejectedWithoutInventingFalseValues() {
        val report = Stage12SummaryObservation()
        report.sample(Stage12SummaryObservation.Snapshot())
        assertThrows(Stage12SummaryObservation.Rejected::class.java) { report.requireOwnerSafe() }
        assertTrue(report.wire().contains("\"reason\":\"HOST_UNKNOWN\""))
        assertTrue(report.wire().contains("\"context_qa\":null"))
    }

    @Test fun eachUnsafeOwnerOrWindowConditionStopsTheAction() {
        val unsafe = listOf(ready().copy(contextQa = false), ready().copy(hostQa = false),
            ready().copy(uidQa = false), ready().copy(sameHost = false), ready().copy(onMain = false),
            ready().copy(interactive = false), ready().copy(keyguardLocked = true),
            ready().copy(finishing = true),
            ready().copy(destroyed = true), ready().copy(life = Stage12SummaryObservation.Life.STARTED),
            ready().copy(decorPresent = false, attached = null, focused = null),
            ready().copy(decorPresent = null, attached = null, focused = null),
            ready().copy(focused = false), ready().copy(attached = false), ready().copy(interactive = null))
        unsafe.forEach { snapshot ->
            val report = Stage12SummaryObservation(); report.sample(snapshot)
            assertThrows(Stage12SummaryObservation.Rejected::class.java) { report.requireActionSafe() }
        }
    }

    @Test fun theCompleteFunctionalPathStillNeedsPublicationBeforeDone() {
        val report = completedBeforePublish()
        println("STAGE12_SUMMARY_GOLDEN_PUBLISH=${report.wire()}")
        assertTrue(report.wire().contains("\"publication_complete\":false"))
        report.published(); report.finish(true)
        assertTrue(report.wire().contains("\"state\":\"DONE\""))
        assertTrue(report.wire().contains("\"click_calls\":1"))
        println("STAGE12_SUMMARY_GOLDEN_DONE=${report.wire()}")
        val incomplete = Stage12SummaryObservation(); incomplete.published(); incomplete.finish(true)
        assertTrue(incomplete.wire().contains("\"state\":\"FAILED\""))
    }

    @Test fun aMeasuredZeroRootAndAnUnreturnedClickKeepTheOriginalFailurePhase() {
        val report = Stage12SummaryObservation()
        report.beginBody(); report.contentRequested(); report.overview(0)
        report.phase(Stage12SummaryObservation.Phase.BEFORE_CLICK); report.sample(ready()); report.requireActionSafe()
        report.enteringClick()
        val error = IllegalStateException("PRIVATE_HIERARCHY_SENTINEL")
        report.bodyFailed(error); report.ruleFailed(error, false)
        report.phase(Stage12SummaryObservation.Phase.CLOSE_SAMPLE); report.closed(true)
        report.phase(Stage12SummaryObservation.Phase.PUBLISH); report.published(); report.finish(false)
        assertTrue(report.wire().contains("\"overview_nodes\":0"))
        assertTrue(report.wire().contains("\"click_returned\":false"))
        assertTrue(report.wire().contains("\"failure_phase\":\"BEFORE_CLICK\""))
        assertFalse(report.wire().contains("PRIVATE_HIERARCHY_SENTINEL"))
        println("STAGE12_SUMMARY_GOLDEN_ROOT_FAILURE=${report.wire()}")
    }

    @Test fun observingZeroDoesNotInventANewBusinessAssertionOrRetry() {
        val report = completedBeforePublish()
        report.overview(0)
        report.requireCompleteBeforePublish()
        report.published(); report.finish(true)
        assertTrue(report.wire().contains("\"overview_nodes\":0"))
        assertTrue(report.wire().contains("\"state\":\"DONE\""))
    }

    @Test fun secondaryCloseAndPublicationErrorsDoNotReplaceThePrimaryThrowable() {
        val primary = AssertionError("PRIVATE_PRIMARY_SENTINEL")
        val close = IllegalStateException("PRIVATE_CLOSE_SENTINEL")
        val publication = IOException("PRIVATE_PUBLICATION_SENTINEL")
        assertSame(primary, Stage12SummaryObservation.retain(primary, close))
        assertSame(primary, Stage12SummaryObservation.retain(primary, publication))
        assertSame(primary, Stage12SummaryObservation.retain(primary, primary))
        assertEquals(listOf(close, publication), primary.suppressed.toList())
        val report = Stage12SummaryObservation(); report.beginBody()
        report.phase(Stage12SummaryObservation.Phase.BEFORE_CLICK); report.bodyFailed(primary)
        report.phase(Stage12SummaryObservation.Phase.CLOSE_SAMPLE); report.closeFailed(close)
        report.phase(Stage12SummaryObservation.Phase.PUBLISH); report.publicationFailed(publication); report.finish(false)
        assertTrue(report.wire().contains("\"close_failed\":true"))
        assertTrue(report.wire().contains("\"publication_failed\":true"))
        assertTrue(report.wire().contains("\"failure_phase\":\"BEFORE_CLICK\""))
        assertFalse(report.wire().contains("PRIVATE_"))
        println("STAGE12_SUMMARY_GOLDEN_SECONDARY_FAILURE=${report.wire()}")
    }

    @Test fun bodyFailureRemainsPrimaryWhenTheRuleThrowsADifferentOrAggregateFailure() {
        val body = AssertionError("PRIVATE_BODY_SENTINEL")
        val teardown = IllegalStateException("PRIVATE_TEARDOWN_SENTINEL")
        val aggregate = MultipleFailureException(listOf(body, teardown))
        assertSame(body, Stage12SummaryObservation.retainRuleFailure(body, aggregate, aggregate.failures))
        assertEquals(listOf(teardown), body.suppressed.toList())
        assertFalse(body.suppressed.any { it === aggregate || it === body })
        assertSame(body, Stage12SummaryObservation.retainRuleFailure(body, teardown, emptyList()))
        assertEquals(1, body.suppressed.size)
        assertSame(teardown, Stage12SummaryObservation.retainRuleFailure(null, teardown, emptyList()))
        assertSame(body, Stage12SummaryObservation.retainRuleFailure(body, body, emptyList()))
    }

    @Test fun aDistinctTeardownErrorCannotDisplaceTheOriginalBodyError() {
        val body = AssertionError("PRIVATE_BODY_SENTINEL")
        val teardown = IllegalStateException("PRIVATE_TEARDOWN_SENTINEL")
        assertSame(body, Stage12SummaryObservation.retainRuleFailure(body, teardown, emptyList()))
        assertEquals(listOf(teardown), body.suppressed.toList())
    }

    class FailingTeardown(private val error: Throwable) {
        fun close() { throw error }
    }

    @Test fun actualStatementsCaptureBodyBeforeDistinctOrJUnitAggregateTeardown() {
        for (aggregate in listOf(false, true)) {
            val body = AssertionError("PRIVATE_EXECUTED_BODY_SENTINEL")
            val teardown = IllegalStateException("PRIVATE_EXECUTED_TEARDOWN_SENTINEL")
            val order = Stage12SummaryObservation.FailureOrder()
            val events = mutableListOf<String>()
            val base = object : Statement() {
                override fun evaluate() = order.observeBody { events += "body"; throw body }
            }
            val closing = if (aggregate) RunAfters(base,
                listOf(FrameworkMethod(FailingTeardown::class.java.getMethod("close"))), FailingTeardown(teardown))
            else object : Statement() {
                override fun evaluate() { try { base.evaluate() } finally { events += "close"; throw teardown } }
            }
            val outer = object : Statement() {
                override fun evaluate() = order.observeRule({ closing.evaluate() }, { error, additional ->
                    events += "rule"
                    assertTrue(additional)
                    if (aggregate) assertTrue(error is MultipleFailureException) else assertSame(teardown, error)
                }, { (it as? MultipleFailureException)?.failures.orEmpty() })
            }
            assertSame(body, assertThrows(AssertionError::class.java) { outer.evaluate() })
            assertEquals(listOf(teardown), body.suppressed.toList())
            assertFalse(body.suppressed.any { it === body || it is MultipleFailureException })
            assertEquals(if (aggregate) listOf("body", "rule") else listOf("body", "close", "rule"), events)
        }
    }

    @Test fun ownHostIsAcquiredOnceOnMainAndReusedAfterTeardownWithoutCallingTheProvider() {
        data class Host(var destroyed: Boolean = false)
        val expected = Host()
        val host = Stage12SummaryObservation.OwnHost<Host>()
        var onMain = false; var dispatches = 0; var acquisitions = 0
        val dispatch: (() -> Unit) -> Unit = { action ->
            dispatches++; onMain = true
            try { action() } finally { onMain = false }
        }
        val actual = host.acquire(dispatch) { assertTrue(onMain); acquisitions++; expected }
        assertSame(expected, actual)
        expected.destroyed = true // Synthetic teardown only; no Android execution.
        repeat(4) { assertSame(expected, host.existing()); assertTrue(host.existing()!!.destroyed) }
        assertThrows(IllegalStateException::class.java) { host.acquire(dispatch) { acquisitions++; Host() } }
        assertEquals(1, acquisitions); assertEquals(1, dispatches)
    }

    @Test fun aMainProviderFailureIsPreservedWithoutEscapingTheMainCallbackOrRetrying() {
        val host = Stage12SummaryObservation.OwnHost<Any>()
        val expected = IllegalStateException("PRIVATE_PROVIDER_SENTINEL")
        var escapedMain = false; var calls = 0
        val dispatch: (() -> Unit) -> Unit = { action ->
            calls++
            try { action() } catch (failure: Throwable) { escapedMain = true; throw failure }
        }
        assertSame(expected, assertThrows(IllegalStateException::class.java) { host.acquire(dispatch) { throw expected } })
        assertNull(host.existing()); assertFalse(escapedMain)
        assertThrows(IllegalStateException::class.java) { host.acquire(dispatch) { Any() } }
        assertEquals(1, calls)
    }

    @Test fun duplicateCallbackWrongIdAndWrongThreadCannotBeReportedAsComplete() {
        for (kind in 0..2) {
            val report = completedBeforePublish()
            report.callback(kind != 1, kind != 2)
            assertThrows(Stage12SummaryObservation.Rejected::class.java) { report.requireCompleteBeforePublish() }
            report.published(); report.finish(true)
            assertTrue(report.wire().contains("\"state\":\"FAILED\""))
        }
    }

    @Test fun eventHistoryIsBoundedAndMissingArmDoesNotFabricateZero() {
        val report = Stage12SummaryObservation()
        report.contentEntered(true); report.contentDisposed(true)
        assertTrue(report.wire().contains("\"content_enters\":null"))
        repeat(100) { report.sample(ready()) }
        assertTrue(report.wire().contains("\"trace_truncated\":true"))
        assertEquals(24, Regex("\"elapsed_ms\":").findAll(report.wire()).count())
        assertTrue(report.wire().toByteArray(Charsets.UTF_8).size < 16384)
    }

    @Test fun onlyTheExactOneMethodAndRealClockCanEnableObservation() {
        val selector = Stage12SummaryObservation.SELECTOR
        Stage12SummaryObservation.requireSelection(listOf(selector), "REAL", Stage12SummaryObservation.ADMISSION)
        for (selectors in listOf(emptyList(), listOf(selector, selector), listOf("other#method")))
            assertThrows(IllegalArgumentException::class.java) {
                Stage12SummaryObservation.requireSelection(selectors, "REAL", Stage12SummaryObservation.ADMISSION)
            }
        assertThrows(IllegalArgumentException::class.java) {
            Stage12SummaryObservation.requireSelection(listOf(selector), "STAGE12_20261023", Stage12SummaryObservation.ADMISSION)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Stage12SummaryObservation.requireSelection(listOf(selector), "REAL", "wrong")
        }
    }

    @Test fun safeStatusProjectionDoesNotChangeTheOriginalFailAccounting() {
        val report = Stage12SummaryObservation(); report.bodyFailed(AssertionError("PRIVATE_STATUS_SENTINEL")); report.finish(false)
        val original = Stage12NormalQaCounts(setOf(Stage12SummaryObservation.SELECTOR))
        original.observe(Stage12SummaryObservation.SELECTOR, 1, 1, false)
        original.observe(Stage12SummaryObservation.SELECTOR, 1, -2, true)
        val projected = report.statusFields(1, -2, true)
        assertEquals(setOf("class", "test", "id", "numtests", "stream", "stack"), projected.keys)
        assertFalse(projected.values.joinToString().contains("PRIVATE_STATUS_SENTINEL"))
        val counts = original.finish(true)
        assertEquals(1, counts.failed); assertEquals(0, counts.passed); assertTrue(counts.malformed); assertFalse(counts.complete)
    }
}
