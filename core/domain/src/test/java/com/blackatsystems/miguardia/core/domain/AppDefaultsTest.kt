package com.blackatsystems.miguardia.core.domain

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDefaultsTest {
    @Test
    fun approvedV1DefaultsAreStable() {
        assertEquals("Inforce", AppDefaults.COMPANY_NAME)
        assertEquals(204, AppDefaults.MONTHLY_HOURS_THRESHOLD)
        assertEquals(
            ZoneId.of("America/Argentina/Cordoba"),
            AppDefaults.zoneId(),
        )
    }
}
