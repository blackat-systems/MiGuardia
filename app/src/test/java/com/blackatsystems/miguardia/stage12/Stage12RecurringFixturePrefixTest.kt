package com.blackatsystems.miguardia.stage12

import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.Assert.*
import org.junit.Test

class Stage12RecurringFixturePrefixTest {
    @Suppress("UNCHECKED_CAST")
    private fun events(prefix: Stage12RecurringFixturePrefix) = prefix.projection()["events"] as List<Map<String, Any>>
    private fun thrown(body: () -> Unit): Throwable = try { body(); error("EXPECTED_FAILURE") } catch (error: Throwable) { error }
    private fun finish(prefix: Stage12RecurringFixturePrefix) { prefix.expectedSteps().forEach { name -> prefix.call(name) { Unit } } }

    @Test fun profilesHaveClosedUniqueRolesAndBusinessCardinalities() {
        for (case in Stage12RecurringCase.entries) {
            val names = Stage12RecurringFixturePrefix.profile(case)
            val single = case == Stage12RecurringCase.SINGLE_OCCURRENCE
            val lifecycle = single || case == Stage12RecurringCase.LIFECYCLE
            assertEquals(names.size, names.distinct().size)
            assertTrue(names.all { it.matches(Regex("[A-Z_]+(?:/[A-Z_0-9]+)*")) })
            assertEquals(if (single) 14 else 13, names.count { it.startsWith("MAIN_BUILD/") })
            assertEquals(if (single) 15 else if (lifecycle) 14 else 17, names.count { it.startsWith("SHIFT_INSERT/") })
            assertEquals(15, names.count { it.startsWith("MAIN_OCCURRENCE/") })
            assertEquals(if (lifecycle) 2 else 3, names.count { it.startsWith("VALIDATE_PLAN/") && it.endsWith("/READ") })
            assertEquals(if (single) 15 else if (lifecycle) 14 else 15, names.count { it.startsWith("VALIDATE_LINK/") && it.endsWith("/READ") })
            assertEquals(if (single) 2 else 1, names.count { it.startsWith("NOTE_") && it.endsWith("/BUILD") })
            assertEquals(if (single) 3 else 2, names.count { it.startsWith("IMAGE_") && it.endsWith("/FILE") })
            assertTrue(names.indexOf("CREATE_RECEIVER") < names.indexOf("ARG_CONTEXT"))
            assertTrue(names.indexOf("HISTORY_RECEIVER") < names.indexOf("HISTORY_ARGS"))
            assertTrue(names.indexOf("ACTUAL_COMPARE") < names.indexOf("ACTUAL_SAVE"))
        }
    }
    @Test fun allSevenActualProfilesCompleteWithReturnedCallbacksOnly() {
        for (case in Stage12RecurringCase.entries) {
            val prefix = Stage12RecurringFixturePrefix(case) { fail("UNEXPECTED_RETAIN") }
            prefix.wrapper { finish(prefix) }
            assertEquals("COMPLETE", prefix.projection()["status"])
            assertEquals(prefix.expectedSteps(), events(prefix).map { it["step"] })
            assertTrue(events(prefix).all { it == mapOf("step" to it["step"], "state" to "RETURNED") })
            assertEquals(setOf("schema", "events", "status"), prefix.projection().keys)
        }
    }
    @Test fun everyOperationFrontierRetainsTheOriginalAndBlocksDependents() {
        for (case in Stage12RecurringCase.entries) for (frontier in Stage12RecurringFixturePrefix.profile(case).indices) {
            val primary = if (frontier % 2 == 0) IOException() else IllegalStateException()
            val retained = mutableListOf<Throwable>()
            val prefix = Stage12RecurringFixturePrefix(case) { retained += it }
            var callbacks = 0
            val names = prefix.expectedSteps()
            val error = thrown { prefix.wrapper { for ((index, name) in names.withIndex()) prefix.call(name) { callbacks++; if (index == frontier) throw primary } } }
            assertSame(primary, error); assertEquals(listOf(primary), retained)
            assertEquals(frontier + 1, callbacks)
            assertEquals(frontier + 1, events(prefix).size)
            assertEquals(mapOf("step" to names[frontier], "state" to "THREW", "error" to if (primary is IOException) "IO" else "OTHER"), events(prefix).last())
            assertEquals(names[frontier], prefix.projection()["first_negative_step"])
            assertFalse(prefix.projection().containsKey("external_failure"))
            assertSame(primary, thrown { prefix.call(names.last()) { fail("DEPENDENT_RAN") } })
            assertSame(primary, thrown { prefix.wrapper { fail("SECOND_WRAPPER_RAN") } })
        }
    }
    @Test fun everyBetweenStepGapIncludingBeforeEntryAndAfterCompletionIsHonest() {
        for (case in Stage12RecurringCase.entries) for (count in 0..Stage12RecurringFixturePrefix.profile(case).size) {
            val primary = IOException(); val prefix = Stage12RecurringFixturePrefix(case) { assertSame(primary, it) }
            assertSame(primary, thrown { prefix.wrapper { prefix.expectedSteps().take(count).forEach { prefix.call(it) { Unit } }; throw primary } })
            assertEquals(count, events(prefix).size); assertTrue(events(prefix).all { it["state"] == "RETURNED" })
            assertEquals("FAILED", prefix.projection()["status"])
            assertEquals(mapOf("kind" to "BETWEEN_STEPS", "error" to "IO"), prefix.projection()["external_failure"])
            assertFalse(prefix.projection().containsKey("first_negative_step"))
            assertSame(primary, thrown { prefix.call(prefix.expectedSteps().last()) { fail("GAP_DEPENDENT") } })
        }
    }
    @Test fun anArgumentFailureDoesNotInventCreateEntry() {
        val prefix = Stage12RecurringFixturePrefix(Stage12RecurringCase.KEEP_EXISTING) {}
        val names = prefix.expectedSteps(); val at = names.indexOf("ARG_STORE"); val primary = IllegalStateException()
        assertSame(primary, thrown { prefix.wrapper { names.take(at).forEach { prefix.call(it) { Unit } }; prefix.call("ARG_STORE") { throw primary } } })
        assertFalse(events(prefix).any { it["step"] == "CREATE_ENTRY" })
        assertEquals("ARG_STORE", prefix.projection()["first_negative_step"])
    }
    @Test fun suspendReturnRecordsEntryBeforeResumptionAndReturnAfterIt() = runBlocking {
        val prefix = Stage12RecurringFixturePrefix(Stage12RecurringCase.KEEP_EXISTING) {}
        val names = prefix.expectedSteps(); val at = names.indexOf("CONFIG_CREATE")
        names.take(at).forEach { prefix.call(it) { Unit } }
        lateinit var continuation: Continuation<Unit>
        var callbacks = 0
        val job = async(start = CoroutineStart.UNDISPATCHED) { prefix.callSuspend("CONFIG_CREATE") { callbacks++; suspendCoroutine<Unit> { continuation = it } } }
        assertEquals(1, callbacks); assertEquals("ENTERED", events(prefix).last()["state"])
        continuation.resume(Unit); job.await()
        assertEquals(1, callbacks); assertEquals("RETURNED", events(prefix).last()["state"])
    }
    @Test fun suspendThrowRetainsTheSameInstanceAndDoesNotRunLaterCallbacks() = runBlocking {
        supervisorScope {
            val primary = IOException(); val retained = mutableListOf<Throwable>()
            val prefix = Stage12RecurringFixturePrefix(Stage12RecurringCase.KEEP_EXISTING) { retained += it }
            val names = prefix.expectedSteps(); val at = names.indexOf("CONFIG_CREATE")
            names.take(at).forEach { prefix.call(it) { Unit } }
            lateinit var continuation: Continuation<Unit>
            var fromPrefix: Throwable? = null
            val job = async(start = CoroutineStart.UNDISPATCHED) {
                try { prefix.callSuspend("CONFIG_CREATE") { suspendCoroutine<Unit> { continuation = it } } }
                catch (error: Throwable) { fromPrefix = error }
            }
            continuation.resumeWithException(primary)
            job.await()
            assertSame(primary, retained.single()); assertSame(primary, fromPrefix)
            assertEquals("THREW", events(prefix).last()["state"])
            assertSame(primary, thrown { prefix.call(names[at + 1]) { fail("SUSPEND_DEPENDENT_RAN") } })
        }
    }
    @Test fun wrapperSecondaryCannotReplaceAnOperationPrimary() {
        val primary = IOException(); val secondary = IllegalStateException(); val prefix = Stage12RecurringFixturePrefix(Stage12RecurringCase.KEEP_EXISTING) {}
        assertSame(primary, thrown { prefix.wrapper { try { prefix.call("WRAPPER_BEFORE") { throw primary } } finally { throw secondary } } })
        assertEquals(1, primary.suppressed.size); assertSame(secondary, primary.suppressed.single())
        assertEquals("WRAPPER_BEFORE", prefix.projection()["first_negative_step"])
    }
    @Test fun aFailingRetainerStaysSecondaryInRam() {
        val primary = IOException(); val secondary = AssertionError()
        val prefix = Stage12RecurringFixturePrefix(Stage12RecurringCase.KEEP_EXISTING) { throw secondary }
        assertSame(primary, thrown { prefix.wrapper { throw primary } })
        assertSame(secondary, primary.suppressed.single())
        assertFalse(prefix.projection().toString().contains("AssertionError"))
    }
    @Test fun wrongOrderAndUnknownTokensNeverExecuteOrPublishPrivateInput() {
        for (name in listOf("CREATE_ENTRY", "SYNTHETIC_PRIVATE_PATH")) {
            val prefix = Stage12RecurringFixturePrefix(Stage12RecurringCase.KEEP_EXISTING) {}
            val primary = thrown { prefix.call(name) { fail("OUT_OF_ORDER_CALLBACK") } }
            assertTrue(events(prefix).isEmpty()); assertFalse(prefix.projection().containsKey("first_negative_step"))
            assertSame(primary, thrown { prefix.call("WRAPPER_BEFORE") { fail("AFTER_PROTOCOL_CALLBACK") } })
            assertFalse(prefix.projection().toString().contains("SYNTHETIC_PRIVATE_PATH"))
        }
    }
    @Test fun projectionsAndProfileCopiesCannotMutateTheJournal() {
        val prefix = Stage12RecurringFixturePrefix(Stage12RecurringCase.KEEP_EXISTING) {}
        val before = prefix.projection(); prefix.call("WRAPPER_BEFORE") { Unit }
        assertTrue((before["events"] as List<*>).isEmpty())
        @Suppress("UNCHECKED_CAST") val observed = events(prefix).single() as MutableMap<String, Any>
        observed["state"] = "SYNTHETIC_MUTATION"
        assertEquals("RETURNED", events(prefix).single()["state"])
        val names = prefix.expectedSteps().toMutableList(); names.clear(); assertTrue(prefix.expectedSteps().isNotEmpty())
    }
    @Test fun nullableReturnsAreNotRetriedOrConvertedToAnAbsenceClaim() {
        val prefix = Stage12RecurringFixturePrefix(Stage12RecurringCase.KEEP_EXISTING) {}; var calls = 0
        val value = prefix.call<String?>("WRAPPER_BEFORE") { calls++; null }
        assertNull(value); assertEquals(1, calls); assertEquals(mapOf("step" to "WRAPPER_BEFORE", "state" to "RETURNED"), events(prefix).single())
    }
}
