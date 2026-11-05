package com.blackatsystems.miguardia.stage12

import org.junit.Assert.*
import org.junit.Test

class Stage12TemporalInputTest {
    @Test fun negativeFromLastReadinessCallbackPreventsAnyInputAndRetainsOriginal() {
        val failure = Stage12TemporalFailure { }
        val original = IllegalStateException("synthetic readiness")
        var inputs = 0
        val caught = runCatching { failure.inputAfterReadiness({ failure.remember(original); 23 }, { inputs++; true }) }.exceptionOrNull()
        assertSame(original, caught); assertEquals(0, inputs)
        val second = IllegalStateException("synthetic recorder")
        failure.remember(second)
        assertSame(original, runCatching { failure.inputAfterReadiness({ error("readiness must not repeat") }, { inputs++ }) }.exceptionOrNull())
        assertArrayEquals(arrayOf(second), original.suppressed); assertEquals(0, inputs)
    }
    @Test fun rendererFailureRemainsPrimaryWhenCapabilityRetentionAlsoFails() {
        val failure = Stage12TemporalFailure { }
        val renderer = IllegalStateException("synthetic renderer")
        val retention = IllegalStateException("synthetic retention")
        try { throw renderer } catch (error: Throwable) { failure.remember(error) }
        try { throw retention } catch (error: Throwable) { failure.remember(error) }
        var inputs = 0
        assertSame(renderer, runCatching { failure.inputAfterReadiness({ 23 }, { inputs++ }) }.exceptionOrNull())
        assertArrayEquals(arrayOf(retention), renderer.suppressed); assertEquals(0, inputs)
    }
    @Test fun negativeDuringInputIsRetainedBeforeAnyDependentReadiness() {
        val failure = Stage12TemporalFailure { }
        val original = IllegalStateException("synthetic asynchronous input")
        var inputs = 0
        assertSame(original, runCatching { failure.inputAfterReadiness({ 23 }, { inputs++; failure.remember(original); true }) }.exceptionOrNull())
        assertEquals(1, inputs)
        assertSame(original, runCatching { failure.inputAfterReadiness({ error("dependent readiness") }, { inputs++ }) }.exceptionOrNull())
        assertEquals(1, inputs)
    }
}
