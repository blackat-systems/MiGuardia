package com.blackatsystems.miguardia.stage12

import org.junit.Assert.*
import org.junit.Test

class Stage12C090OracleTest {
    @Test fun removalUsesTheNamedColumnAndPreservesEveryOtherFieldAndTable() {
        val before = mapOf(
            "shifts" to Stage12C090Oracle.Table(listOf("id", "payload"), listOf(listOf("first", "same"), listOf("second", "same"), listOf("other", "second"))),
            "notes" to Stage12C090Oracle.Table(listOf("id", "shiftId", "body"), listOf(listOf("n1", "first", "same"), listOf("n2", "second", "same"))),
            "received" to Stage12C090Oracle.Table(listOf("secret"), listOf(listOf("PRIVATE_SYNTHETIC"))),
        )
        val after = Stage12C090Oracle.afterRemoving(before, listOf(
            Stage12C090Oracle.Removal("shifts", "id", setOf("second")),
            Stage12C090Oracle.Removal("notes", "shiftId", setOf("second")),
        ))
        assertEquals(listOf(listOf("first", "same"), listOf("other", "second")), after.getValue("shifts").rows)
        assertEquals(listOf(listOf("n1", "first", "same")), after.getValue("notes").rows)
        assertEquals(before.getValue("received").rows, after.getValue("received").rows)
        assertEquals(3, before.getValue("shifts").rows.size)
    }

    @Test fun aMissingTableOrColumnCannotSilentlyWeakenTheOracle() {
        val before = mapOf("shifts" to Stage12C090Oracle.Table(listOf("id"), listOf(listOf("second"))))
        for (removal in listOf(Stage12C090Oracle.Removal("missing", "id", setOf("second")),
            Stage12C090Oracle.Removal("shifts", "missing", setOf("second")), Stage12C090Oracle.Removal("shifts", "id", emptySet()))) {
            assertThrows(IllegalStateException::class.java) { Stage12C090Oracle.afterRemoving(before, listOf(removal)) }
        }
    }

    @Test fun filesAllowOnlyExplicitOwnedRemovalAndNeverReplacementOrAddition() {
        val before = mapOf("first" to "hash1", "second" to "hash2", "received" to "hash3")
        assertTrue(Stage12C090Oracle.sameFilesExceptOwnedRemoval(before, before, setOf("second")))
        assertTrue(Stage12C090Oracle.sameFilesExceptOwnedRemoval(before, before - "second", setOf("second")))
        assertFalse(Stage12C090Oracle.sameFilesExceptOwnedRemoval(before, before - "first", setOf("second")))
        assertFalse(Stage12C090Oracle.sameFilesExceptOwnedRemoval(before, before + ("second" to "wrong"), setOf("second")))
        assertFalse(Stage12C090Oracle.sameFilesExceptOwnedRemoval(before, before + ("new" to "hash4"), setOf("second")))
    }

    @Test fun theSixLiteralV5ObligationsAndPrimaryFailureAreRetained() {
        assertEquals(6, Stage12C090Oracle.assertionKeys.size)
        assertEquals(6, Stage12C090Oracle.assertionKeys.toSet().size)
        val body = AssertionError("BODY"); val close = IllegalStateException("CLOSE")
        assertSame(body, Stage12C090Oracle.retain(body, close))
        Stage12C090Oracle.retain(body, body); Stage12C090Oracle.retain(body, close)
        assertEquals(listOf(close), body.suppressed.toList())
    }
}
