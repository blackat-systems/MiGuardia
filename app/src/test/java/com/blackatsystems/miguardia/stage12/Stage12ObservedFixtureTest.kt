package com.blackatsystems.miguardia.stage12

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class Stage12ObservedFixtureTest {
    @Test fun actualMemoryInputsHaveStableSemanticsButFreshOwnedIdentities() {
        val a = Stage12ObservedFixture.memory(Stage12W010Fixture(UUID.randomUUID()), 48)
        val b = Stage12ObservedFixture.memory(Stage12W010Fixture(UUID.randomUUID()), 48)
        assertEquals(a["semantic"], b["semantic"])
        assertNotEquals(a["owned_roles"], b["owned_roles"])
        assertEquals(emptyMap<String, String>(), a["received_references"])
        assertFalse(a.toString().contains("PRIVATE_"))
    }
    @Test fun normalizationExcludesOnlyDeclaredIdentityAndRetainsPayloadDifferences() {
        val a = UUID.randomUUID().toString();val b = UUID.randomUUID().toString()
        assertEquals(Stage12ObservedFixture.fingerprint("payload/$a", mapOf("shift" to a)),
            Stage12ObservedFixture.fingerprint("payload/$b", mapOf("shift" to b)))
        assertNotEquals(Stage12ObservedFixture.fingerprint("payload/$a", mapOf("shift" to a)),
            Stage12ObservedFixture.fingerprint("changed/$b", mapOf("shift" to b)))
        assertThrows(IllegalArgumentException::class.java) {
            Stage12ObservedFixture.fingerprint("payload", mapOf("first" to a, "second" to a))
        }
    }
}
