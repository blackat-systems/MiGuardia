package com.blackatsystems.miguardia

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectionsConsentTest {
    @Test fun onlyLocalConfirmationSendsExactlyOnce() {
        val sent = mutableListOf<String>()
        val consent = DirectionsConsent("Calle sintética 123")
        assertEquals(emptyList<String>(), sent)
        repeat(2) { consent.confirm(true, sent::add) }
        assertEquals(listOf("Calle sintética 123"), sent)
    }
    @Test fun cancellationOrClosedGateCannotBeReplayedAfterUnlock() {
        for (cancel in listOf(true, false)) {
            val sent = mutableListOf<String>()
            val consent = DirectionsConsent("Calle sintética 123")
            if (cancel) consent.cancel() else consent.confirm(false, sent::add)
            consent.confirm(true, sent::add)
            assertEquals(emptyList<String>(), sent)
        }
    }
}
