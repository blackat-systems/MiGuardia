package com.blackatsystems.miguardia.core.domain.work

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
    val name: String,
    val helpsMeetHoursReference: Boolean,
    val showDedicatedSummary: Boolean,
) {
    init {
        require(name.isNotEmpty() && name == name.trim()) {
            "El nombre de la clase extra no puede estar vacío"
        }
    }

    companion object {
        fun create(
            id: UUID,
            name: String,
            helpsMeetHoursReference: Boolean,
            showDedicatedSummary: Boolean,
        ): ExtraWorkClass = ExtraWorkClass(
            id = id,
            name = requireName(name, "El nombre de la clase extra no puede estar vacío"),
            helpsMeetHoursReference = helpsMeetHoursReference,
            showDedicatedSummary = showDedicatedSummary,
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
