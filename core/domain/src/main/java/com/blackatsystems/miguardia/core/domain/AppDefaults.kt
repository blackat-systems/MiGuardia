package com.blackatsystems.miguardia.core.domain

import java.time.ZoneId

object AppDefaults {
    const val COMPANY_NAME: String = "Inforce"
    const val MONTHLY_HOURS_THRESHOLD: Int = 204
    const val ZONE_ID: String = "America/Argentina/Cordoba"
    const val WEATHER_LOCATION_ID: String = "cordoba-capital"
    const val WEATHER_LOCATION_NAME: String = "Córdoba Capital, Argentina"
    const val WEATHER_LATITUDE: Double = -31.4201
    const val WEATHER_LONGITUDE: Double = -64.1888

    fun zoneId(): ZoneId = ZoneId.of(ZONE_ID)
}
