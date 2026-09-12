package com.blackatsystems.miguardia.core.domain

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDefaultsTest {
    @Test
    fun applicationZoneIsStable() {
        assertEquals(
            ZoneId.of("America/Argentina/Cordoba"),
            AppDefaults.zoneId(),
        )
    }
}
