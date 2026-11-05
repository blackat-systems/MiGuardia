package com.blackatsystems.miguardia.stage12

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class Stage12TemporalPrefixTest {
    private fun scopes() = Stage12TemporalVariant.entries.map { Stage12TemporalScope(it) } +
        Stage12TemporalScope(Stage12TemporalVariant.TWENTY_FIVE_HOURS, Stage12TemporalFixtureRole.ONE_HOUR_COMPARATOR)

    @Test fun shiftMatchAtEndOfUnitCallbackRecordsBooleanAndKeepsFirstNegative() {
        val scope = Stage12TemporalScope(Stage12TemporalVariant.SAME_DAY)
        val step = "VALIDATE_SHIFT_0/MATCH"
        fun beforeMatch(retain: (Throwable) -> Unit): Stage12TemporalFixturePrefix {
            val prefix = Stage12TemporalFixturePrefix(scope, retain)
            for (operation in Stage12TemporalFixturePrefix.operations(scope).takeWhile { it.name != step }) {
                prefix.call(operation.name) { operation.expected ?: Unit }
            }
            return prefix
        }

        val previous = beforeMatch { }
        val erased = runCatching {
            listOf(true).forEachIndexed { index, matches ->
                previous.call("VALIDATE_SHIFT_$index/MATCH") { matches }
            }
        }.exceptionOrNull()
        assertEquals("RECURRING_SETUP_BOOLEAN_REQUIRED", erased?.message)

        for (matches in listOf(true, false)) {
            val retained = mutableListOf<Throwable>()
            val prefix = beforeMatch(retained::add)
            val error = runCatching {
                listOf(matches).forEachIndexed { index, equal ->
                    prefix.guard("VALIDATE_SHIFT_$index/MATCH") { equal }
                }
            }.exceptionOrNull()
            val event = (prefix.projection()["events"] as List<*>).last() as Map<*, *>
            assertEquals(step, event["step"])
            assertEquals(matches, event["result"])
            if (matches) {
                assertNull(error)
                assertTrue(retained.isEmpty())
                assertEquals("RETURNED", event["state"])
            } else {
                assertEquals("RECURRING_SETUP_GUARD_REJECTED", error?.message)
                assertSame(error, retained.single())
                assertEquals("THREW", event["state"])
                assertEquals("GUARD_REJECTED", event["error"])
                assertEquals(step, prefix.projection()["first_negative_step"])
                var dependentInvoked = false
                assertSame(error, runCatching {
                    prefix.call("VALIDATE_WINDOW/READ") { dependentInvoked = true }
                }.exceptionOrNull())
                assertFalse(dependentInvoked)
            }
        }
    }

    @Test fun everyDeclaredProfileCompletesInOrder() {
        for (scope in scopes()) {
            val prefix = Stage12TemporalFixturePrefix(scope) { throw AssertionError("unexpected failure", it) }
            val profile = Stage12TemporalFixturePrefix.operations(scope)
            assertEquals(profile.size, profile.map { it.name }.distinct().size)
            for (operation in profile) prefix.call(operation.name) { operation.expected ?: Unit }
            assertEquals("COMPLETE", prefix.projection()["status"])
        }
    }
    @Test fun failureAtEveryDelegateRetainsTheOriginalAndBlocksAllDependents() {
        for (scope in scopes()) for ((index, injected) in Stage12TemporalFixturePrefix.operations(scope).withIndex()) {
            val original = IOException("injected")
            val secondary = IllegalArgumentException("retainer")
            val prefix = Stage12TemporalFixturePrefix(scope) { assertSame(original, it); throw secondary }
            val profile = Stage12TemporalFixturePrefix.operations(scope)
            for (operation in profile.take(index)) prefix.call(operation.name) { operation.expected ?: Unit }
            val caught = runCatching { prefix.call(injected.name) { throw original } }.exceptionOrNull()
            assertSame(original, caught)
            var dependentCalls = 0
            for (operation in profile.drop(index + 1)) {
                assertSame(original, runCatching { prefix.call(operation.name) { dependentCalls++; operation.expected ?: Unit } }.exceptionOrNull())
            }
            assertEquals(0, dependentCalls)
            assertEquals(listOf(secondary), original.suppressed.toList())
            assertEquals(injected.name, prefix.projection()["first_negative_step"])
            assertEquals("FAILED", prefix.projection()["status"])
        }
    }
    @Test fun falseGuardAndUnknownOperationNeverResumeThePrefix() {
        for (scope in scopes()) for ((index, injected) in Stage12TemporalFixturePrefix.operations(scope).withIndex()) {
            if (injected.expected == null) continue
            val retained = mutableListOf<Throwable>()
            val prefix = Stage12TemporalFixturePrefix(scope, retained::add)
            val profile = Stage12TemporalFixturePrefix.operations(scope)
            for (operation in profile.take(index)) prefix.call(operation.name) { operation.expected ?: Unit }
            val original = runCatching { prefix.call(injected.name) { !injected.expected } }.exceptionOrNull()
            assertSame(retained.single(), original)
            var invoked = false
            assertSame(original, runCatching { prefix.call("UNKNOWN") { invoked = true } }.exceptionOrNull())
            assertFalse(invoked)
        }
        var invoked = false
        val prefix = Stage12TemporalFixturePrefix(scopes().first()) { }
        assertNotNull(runCatching { prefix.call("UNKNOWN") { invoked = true } }.exceptionOrNull())
        assertFalse(invoked)
    }
    @Test fun recorderFailureCannotReplaceOrReopenTheFirstNegative() {
        val original = IOException("original")
        val recorder = IllegalStateException("recorder")
        val later = IllegalArgumentException("later")
        var calls = 0
        val failure = Stage12TemporalFailure { calls++; throw recorder }
        assertSame(original, runCatching { failure.reject(original) }.exceptionOrNull())
        assertSame(original, failure.remember(later))
        assertSame(original, runCatching { failure.requireActive() }.exceptionOrNull())
        assertSame(original, failure.remember(original))
        assertEquals(1, calls)
        assertEquals(listOf(recorder, later), original.suppressed.toList())
    }
}
