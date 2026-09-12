package com.blackatsystems.miguardia.core.domain.work

import com.blackatsystems.miguardia.core.domain.model.Objective
import java.text.Normalizer
import java.time.Instant
import java.util.Locale

fun normalizeRequiredWorkText(rawValue: String, label: String): String =
    normalizeWorkText(rawValue).also { normalized ->
        require(normalized.isNotEmpty()) { "$label no puede estar vacio" }
    }

fun normalizeOptionalWorkText(rawValue: String?): String? = rawValue
    ?.let(::normalizeWorkText)
    ?.takeIf(String::isNotEmpty)

fun canonicalWorkTypeNameKey(rawName: String): String =
    normalizeRequiredWorkText(rawName, "El nombre del tipo de trabajo").uppercase(Locale.ROOT)

fun canonicalExtraWorkClassNameKey(rawName: String): String =
    normalizeRequiredWorkText(rawName, "El nombre de la clase extra").uppercase(Locale.ROOT)

fun normalizeNewWorkPlaceAbbreviation(rawAbbreviation: String): String =
    normalizeWorkPlaceAbbreviation(rawAbbreviation).also(::requireNewAbbreviationLength)

fun normalizeHistoricalWorkPlaceAbbreviation(rawAbbreviation: String): String =
    normalizeWorkPlaceAbbreviation(rawAbbreviation).also { abbreviation ->
        val characterCount = abbreviation.codePointCount(0, abbreviation.length)
        require(characterCount in 2..5) {
            "El nombre corto historico debe tener entre dos y cinco caracteres"
        }
    }

fun normalizeUpdatedWorkPlaceAbbreviation(
    rawAbbreviation: String,
    previousAbbreviation: String,
): String {
    val normalized = normalizeWorkPlaceAbbreviation(rawAbbreviation)
    val normalizedPrevious = normalizeWorkPlaceAbbreviation(previousAbbreviation)
    if (normalized != normalizedPrevious) requireNewAbbreviationLength(normalized)
    return normalized
}

fun Objective.normalizedForNewV2WorkPlace(): Objective {
    require(!updatedAt.isBefore(createdAt)) {
        "La actualizacion del objetivo no puede ser anterior a su creacion"
    }
    return copy(
        fullName = normalizeRequiredWorkText(fullName, "El nombre del lugar"),
        abbreviation = normalizeNewWorkPlaceAbbreviation(abbreviation),
        address = normalizeOptionalWorkText(address),
        note = normalizeOptionalWorkText(note),
    )
}

fun Objective.normalizedForV2Update(
    previous: Objective,
    timestamp: Instant = updatedAt,
): Objective {
    require(id == previous.id && createdAt == previous.createdAt) {
        "Editar un lugar no puede cambiar su identidad ni su fecha de creacion"
    }
    require(!timestamp.isBefore(previous.updatedAt)) {
        "La actualizacion del lugar no puede retroceder en el tiempo"
    }
    return copy(
        fullName = normalizeRequiredWorkText(fullName, "El nombre del lugar"),
        abbreviation = normalizeUpdatedWorkPlaceAbbreviation(abbreviation, previous.abbreviation),
        address = normalizeOptionalWorkText(address),
        note = normalizeOptionalWorkText(note),
        isActive = previous.isActive,
        createdAt = previous.createdAt,
        updatedAt = timestamp,
    )
}

fun WorkType.withUpdatedName(rawName: String, timestamp: Instant): WorkType {
    require(!timestamp.isBefore(updatedAt)) {
        "La actualizacion del tipo de trabajo no puede retroceder en el tiempo"
    }
    val normalizedName = normalizeRequiredWorkText(rawName, "El nombre del tipo de trabajo")
    return copy(
        name = normalizedName,
        normalizedNameKey = canonicalWorkTypeNameKey(normalizedName),
        updatedAt = timestamp,
    )
}

fun requireRecentWorkTemplateLimit(limit: Int) {
    require(limit in 1..MAX_RECENT_WORK_TEMPLATES) {
        "La cantidad de plantillas recientes debe estar entre 1 y $MAX_RECENT_WORK_TEMPLATES"
    }
}

const val MAX_RECENT_WORK_TEMPLATES: Int = 5

private fun normalizeWorkText(rawValue: String): String {
    val compatibilityNormalized = Normalizer.normalize(rawValue, Normalizer.Form.NFKC)
    return buildString(compatibilityNormalized.length) {
        var pendingSpace = false
        compatibilityNormalized.forEach { character ->
            if (character.isWorkSpace()) {
                pendingSpace = isNotEmpty()
            } else {
                if (pendingSpace) append(' ')
                append(character)
                pendingSpace = false
            }
        }
    }
}

private fun normalizeWorkPlaceAbbreviation(rawAbbreviation: String): String {
    val normalized = normalizeRequiredWorkText(rawAbbreviation, "El nombre corto del lugar")
        .uppercase(Locale.ROOT)
    require(normalized.none { character -> character.isWorkSpace() }) {
        "El nombre corto del lugar no puede contener espacios"
    }
    return normalized
}

private fun requireNewAbbreviationLength(abbreviation: String) {
    val characterCount = abbreviation.codePointCount(0, abbreviation.length)
    require(characterCount in 3..5) {
        "El nombre corto nuevo debe tener entre tres y cinco caracteres"
    }
}

private fun Char.isWorkSpace(): Boolean = isWhitespace() || Character.isSpaceChar(this)
