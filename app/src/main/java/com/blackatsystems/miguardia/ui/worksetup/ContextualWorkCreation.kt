package com.blackatsystems.miguardia.ui.worksetup

import androidx.lifecycle.SavedStateHandle
import java.util.UUID

enum class WorkCreationKind { PLACE_AND_TEMPLATE, TEMPLATE }

/** The request belongs to one load draft. Its reserved IDs survive a lost write response. */
data class WorkCreationRequest(
    val id: UUID,
    val timelineId: UUID,
    val kind: WorkCreationKind,
    val preferredPlaceId: UUID? = null,
    val preferredTypeId: UUID? = null,
) {
    val newPlaceId: UUID get() = reservedId("place")
    val newObjectiveId: UUID get() = reservedId("objective")
    val newRuleId: UUID get() = reservedId("rule")
    val newTemplateId: UUID get() = reservedId("template")

    private fun reservedId(part: String): UUID = UUID.nameUUIDFromBytes(
        "miguardia.contextual.$id.$part".toByteArray(Charsets.UTF_8),
    )
}

enum class WorkCreationOutcome { CREATED, CANCELLED }

data class WorkCreationResult(
    val requestId: UUID,
    val timelineId: UUID,
    val outcome: WorkCreationOutcome,
    val placeId: UUID? = null,
    val templateId: UUID? = null,
) {
    init {
        require(outcome != WorkCreationOutcome.CREATED || (placeId != null && templateId != null))
        require(outcome != WorkCreationOutcome.CANCELLED || templateId == null)
    }
}

enum class WorkCreationPendingWrite { PLACE, TEMPLATE }

// One SavedState value per message, using only Bundle-supported values. No callback is persisted.
internal fun SavedStateHandle.writeWorkCreationRequest(key: String, value: WorkCreationRequest?) {
    this[key] = value?.let {
        arrayListOf(it.id.toString(), it.timelineId.toString(), it.kind.name,
            it.preferredPlaceId?.toString().orEmpty(), it.preferredTypeId?.toString().orEmpty())
    }
}

internal fun SavedStateHandle.readWorkCreationRequest(key: String): WorkCreationRequest? =
    get<ArrayList<String>>(key)?.let { values ->
        runCatching {
            WorkCreationRequest(UUID.fromString(values[0]), UUID.fromString(values[1]),
                WorkCreationKind.valueOf(values[2]), values[3].uuidOrNull(), values[4].uuidOrNull())
        }.getOrNull()
    }

internal fun SavedStateHandle.writeWorkCreationResult(key: String, value: WorkCreationResult?) {
    this[key] = value?.let {
        arrayListOf(it.requestId.toString(), it.timelineId.toString(), it.outcome.name,
            it.placeId?.toString().orEmpty(), it.templateId?.toString().orEmpty())
    }
}

internal fun SavedStateHandle.readWorkCreationResult(key: String): WorkCreationResult? =
    get<ArrayList<String>>(key)?.let { values ->
        runCatching {
            WorkCreationResult(UUID.fromString(values[0]), UUID.fromString(values[1]),
                WorkCreationOutcome.valueOf(values[2]), values[3].uuidOrNull(), values[4].uuidOrNull())
        }.getOrNull()
    }

private fun String.uuidOrNull(): UUID? = takeIf(String::isNotEmpty)?.let(UUID::fromString)
