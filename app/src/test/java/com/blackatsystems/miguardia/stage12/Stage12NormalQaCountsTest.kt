package com.blackatsystems.miguardia.stage12

import org.junit.Assert.*
import org.junit.Test

class Stage12NormalQaCountsTest {
    @Test fun exactStartPassSetClosesOnceAndRejectsLaterEvents() {
        val counts = Stage12NormalQaCounts(setOf("C#a", "C#b"))
        listOf("C#b", "C#a").forEach { counts.observe(it, 2, 1, false); counts.observe(it, 2, 0, false) }
        assertTrue(counts.finish(true).complete)
        assertThrows(IllegalStateException::class.java) { counts.observe("C#a", 2, 0, false) }
        assertThrows(IllegalStateException::class.java) { counts.finish(true) }
    }

    @Test fun contradictionsCannotBeRepairedByALaterPass() {
        val adversaries: List<(Stage12NormalQaCounts) -> Unit> = listOf(
            { it.observe("foreign#x", 1, 1, false) },
            { it.observe("C#a", 2, 1, false) },
            { it.observe("C#a", 1, 0, false) },
            { it.observe("C#a", 1, 1, false); it.observe("C#a", 1, 1, false) },
            { it.observe("C#a", 1, 1, false); it.observe("C#a", 1, 0, true) },
        )
        adversaries.forEach { adverse ->
            val counts = Stage12NormalQaCounts(setOf("C#a")); adverse(counts)
            counts.observe("C#a", 1, 1, false); counts.observe("C#a", 1, 0, false)
            assertFalse(counts.finish(true).complete)
        }
    }

    @Test fun failSkipAndMissingTerminalResultsNeverComplete() {
        listOf(-1, -2, -3, -4).forEach { code ->
            val counts = Stage12NormalQaCounts(setOf("C#a"))
            counts.observe("C#a", 1, 1, false); counts.observe("C#a", 1, code, false)
            val result = counts.finish(true)
            assertFalse(result.complete)
            assertEquals(if (code == -3 || code == -4) 1 else 0, result.skipped)
        }
        assertFalse(Stage12NormalQaCounts(setOf("C#a")).finish(true).complete)
    }

    @Test fun processFailureIsNotOverriddenByValidMethodStatuses() {
        val counts = Stage12NormalQaCounts(setOf("C#a"))
        counts.observe("C#a", 1, 1, false); counts.observe("C#a", 1, 0, false)
        assertFalse(counts.finish(false).complete)
    }
}
