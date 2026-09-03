package com.blackatsystems.miguardia.core.domain.model

import java.time.Instant
import java.util.UUID

data class Objective(
    val id: UUID,
    val fullName: String,
    val abbreviation: String,
    val address: String?,
    val note: String?,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val weatherLatitude: Double? = null,
    val weatherLongitude: Double? = null,
) {
    init {
        require((weatherLatitude == null) == (weatherLongitude == null)) {
            "La ubicación del objetivo debe estar completa"
        }
        weatherLatitude?.let { require(it.isFinite() && it in -90.0..90.0) }
        weatherLongitude?.let { require(it.isFinite() && it in -180.0..180.0) }
    }

    val hasWeatherLocation: Boolean
        get() = weatherLatitude != null && weatherLongitude != null
}
