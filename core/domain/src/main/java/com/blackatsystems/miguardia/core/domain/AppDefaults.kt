package com.blackatsystems.miguardia.core.domain

import java.time.ZoneId

object AppDefaults {
    const val ZONE_ID: String = "America/Argentina/Cordoba"

    fun zoneId(): ZoneId = ZoneId.of(ZONE_ID)
}
