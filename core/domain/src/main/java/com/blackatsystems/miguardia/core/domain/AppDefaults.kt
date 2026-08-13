package com.blackatsystems.miguardia.core.domain

import java.time.ZoneId

object AppDefaults {
    const val COMPANY_NAME: String = "Inforce"
    const val MONTHLY_HOURS_THRESHOLD: Int = 204
    const val ZONE_ID: String = "America/Argentina/Cordoba"

    fun zoneId(): ZoneId = ZoneId.of(ZONE_ID)
}
