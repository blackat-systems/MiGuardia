package com.blackatsystems.miguardia.core.domain.model

import com.blackatsystems.miguardia.core.domain.work.normalizeOptionalWorkText
import com.blackatsystems.miguardia.core.domain.work.normalizeRequiredWorkText
import java.time.Instant
import java.util.UUID

enum class CancellationActor {
    NOT_REPORTED,
    EMPLOYER,
    ME,
    OTHER,
}

data class CancellationEvidence(
    val storageKey: String,
    val mimeType: String,
    val byteSize: Long,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val sha256: String,
) {
    init {
        require(STORAGE_KEY.matches(storageKey)) { "La clave de evidencia no es válida" }
        require(mimeType in SUPPORTED_TYPES) { "El tipo de evidencia no está permitido" }
        require(byteSize in 1..MAX_BYTES) { "La evidencia excede el tamaño permitido" }
        require(pixelWidth in 1..65_535 && pixelHeight in 1..65_535) { "Las dimensiones no son válidas" }
        require(SHA_256.matches(sha256)) { "La huella de evidencia no es válida" }
    }

    companion object {
        const val MAX_BYTES: Long = 12L * 1024L * 1024L
        val SUPPORTED_TYPES: Set<String> = setOf("image/jpeg", "image/png", "image/webp")
        private val STORAGE_KEY = Regex(
            "^cancel_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" +
                "(?:_[0-9a-f]{8})?\\.(jpg|png|webp)$",
        )
        private val SHA_256 = Regex("^[a-f0-9]{64}$")
    }
}

data class ShiftSituation(
    val shiftId: UUID,
    val status: ShiftStatus,
    val reason: String?,
    val privateNote: String?,
    val cancellationActor: CancellationActor?,
    val notifiedAt: Instant?,
    val evidence: CancellationEvidence?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(status == ShiftStatus.ABSENT || status == ShiftStatus.CANCELLED) {
            "Una situación sólo puede controlar ausencia o cancelación"
        }
        require(reason == normalizeOptionalWorkText(reason)) { "El motivo debe estar normalizado" }
        require(privateNote == normalizeOptionalWorkText(privateNote)) { "La nota privada debe estar normalizada" }
        require(!updatedAt.isBefore(createdAt)) { "La actualización no puede preceder a la creación" }
        when (status) {
            ShiftStatus.CANCELLED -> {
                normalizeRequiredWorkText(reason.orEmpty(), "El motivo de cancelación")
                requireNotNull(cancellationActor) { "Indicá quién canceló" }
                requireNotNull(notifiedAt) { "Indicá cuándo se avisó la cancelación" }
            }
            ShiftStatus.ABSENT -> require(cancellationActor == null && notifiedAt == null && evidence == null) {
                "Una ausencia no puede conservar metadatos de cancelación"
            }
            ShiftStatus.PLANNED -> error("PLANNED no persiste situación controladora")
        }
    }
}

data class ShiftSituationExpectation(
    val planned: V2ShiftWrite,
    val previous: ShiftSituation?,
    val actual: ShiftActualExpectation?,
    val recurringOccurrence: RecurringOccurrence?,
)

data class ShiftSituationMutation(
    val expected: ShiftSituationExpectation,
    val replacement: ShiftSituation?,
    val removeIncompatibleActual: Boolean,
)
