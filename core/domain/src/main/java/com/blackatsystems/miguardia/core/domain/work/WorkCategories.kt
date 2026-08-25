package com.blackatsystems.miguardia.core.domain.work

import java.time.Instant
import java.util.UUID

data class RegularWorkType(
    val id: UUID,
    val name: String,
) {
    init {
        require(name.isNotEmpty() && name == name.trim()) {
            "El nombre del tipo de trabajo no puede estar vacío"
        }
    }

    companion object {
        fun create(id: UUID, name: String): RegularWorkType = RegularWorkType(
            id = id,
            name = requireName(name, "El nombre del tipo de trabajo no puede estar vacío"),
        )
    }
}

data class ExtraWorkClass(
    val id: UUID,
    val timelineId: UUID,
    val sector: WorkSector,
    val name: String,
    val normalizedNameKey: String,
    val helpsMeetHoursReference: Boolean,
    val showDedicatedSummary: Boolean,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(name == normalizeRequiredWorkText(name, "El nombre de la clase extra")) {
            "El nombre de la clase extra debe estar normalizado"
        }
        require(normalizedNameKey == canonicalExtraWorkClassNameKey(name)) {
            "La clave de la clase extra no coincide con su nombre"
        }
        require(isMillisecondNormalized(createdAt) && isMillisecondNormalized(updatedAt)) {
            "Las fechas de la clase extra deben expresarse en milisegundos"
        }
        require(!updatedAt.isBefore(createdAt)) {
            "La actualización de la clase extra no puede ser anterior a su creación"
        }
    }

    companion object {
        fun create(
            id: UUID,
            timelineId: UUID,
            sector: WorkSector,
            name: String,
            helpsMeetHoursReference: Boolean,
            showDedicatedSummary: Boolean,
            timestamp: Instant,
        ): ExtraWorkClass = ExtraWorkClass(
            id = id,
            timelineId = timelineId,
            sector = sector,
            name = normalizeRequiredWorkText(name, "El nombre de la clase extra"),
            normalizedNameKey = canonicalExtraWorkClassNameKey(name),
            helpsMeetHoursReference = helpsMeetHoursReference,
            showDedicatedSummary = showDedicatedSummary,
            isActive = true,
            createdAt = timestamp.normalizedToMilliseconds(),
            updatedAt = timestamp.normalizedToMilliseconds(),
        )
    }

    fun updated(
        name: String = this.name,
        helpsMeetHoursReference: Boolean = this.helpsMeetHoursReference,
        showDedicatedSummary: Boolean = this.showDedicatedSummary,
        isActive: Boolean = this.isActive,
        timestamp: Instant,
    ): ExtraWorkClass {
        val normalizedTimestamp = timestamp.normalizedToMilliseconds()
        require(normalizedTimestamp.isAfter(updatedAt)) {
            "La actualización de la clase extra debe avanzar en el tiempo"
        }
        val normalizedName = normalizeRequiredWorkText(name, "El nombre de la clase extra")
        return copy(
            name = normalizedName,
            normalizedNameKey = canonicalExtraWorkClassNameKey(normalizedName),
            helpsMeetHoursReference = helpsMeetHoursReference,
            showDedicatedSummary = showDedicatedSummary,
            isActive = isActive,
            updatedAt = normalizedTimestamp,
        )
    }
}

enum class AvailabilityLabel(
    val displayName: String,
) {
    PASSIVE_GUARD("Guardia pasiva"),
    AVAILABLE_FOR_CALL("Disponible para llamado"),
    ON_CALL_RETAINER("Retén"),
}

private fun requireName(rawName: String, message: String): String = rawName
    .trim()
    .also { normalized -> require(normalized.isNotEmpty()) { message } }

internal fun Instant.normalizedToMilliseconds(): Instant = Instant.ofEpochMilli(toEpochMilli())

internal fun isMillisecondNormalized(value: Instant): Boolean =
    value.nano % 1_000_000 == 0
