package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.domain.work.WorkTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.TimeoutCancellationException

/** Only the contextual WorkTemplate round trip; never publishes input or stored values. */
internal class Stage12CatalogPersistenceCriterion {
    enum class Phase { NOT_REACHED, ID_CHECK, ROW_READ, ROW_COMPARE, COMPLETE }
    enum class ReadOutcome { NOT_REACHED, READING, RETURNED_NULL, RETURNED_VALUE, THREW, TIMED_OUT }
    enum class ComparisonOutcome { NOT_REACHED, STARTED, COMPLETE }

    private var phase = Phase.NOT_REACHED
    private var reservedIdMatches: Boolean? = null
    private var readOutcome = ReadOutcome.NOT_REACHED
    private var comparisonOutcome = ComparisonOutcome.NOT_REACHED
    private var comparison: Comparison? = null

    fun checkReserved(original: WorkTemplate, reservedId: UUID) {
        check(phase == Phase.NOT_REACHED)
        phase = Phase.ID_CHECK
        reservedIdMatches = original.id == reservedId
        check(reservedIdMatches == true) { "CATALOG_RESERVED_ID_MISMATCH" }
    }

    fun read(reader: () -> WorkTemplate?): WorkTemplate {
        check(phase == Phase.ID_CHECK && reservedIdMatches == true && readOutcome == ReadOutcome.NOT_REACHED)
        phase = Phase.ROW_READ
        readOutcome = ReadOutcome.READING
        val stored = try {
            reader()
        } catch (error: Throwable) {
            readOutcome = if (error is TimeoutException || error is TimeoutCancellationException) {
                ReadOutcome.TIMED_OUT
            } else {
                ReadOutcome.THREW
            }
            throw error
        }
        readOutcome = if (stored == null) ReadOutcome.RETURNED_NULL else ReadOutcome.RETURNED_VALUE
        return checkNotNull(stored) { "CATALOG_ROW_MISSING" }
    }

    fun compare(original: WorkTemplate, stored: WorkTemplate) {
        check(phase == Phase.ROW_READ && readOutcome == ReadOutcome.RETURNED_VALUE &&
            comparisonOutcome == ComparisonOutcome.NOT_REACHED)
        phase = Phase.ROW_COMPARE
        comparisonOutcome = ComparisonOutcome.STARTED
        // The expected object depends exclusively on the original argument and the storage contract.
        val expected = original.copy(
            createdAt = Instant.ofEpochMilli(original.createdAt.toEpochMilli()),
            updatedAt = Instant.ofEpochMilli(original.updatedAt.toEpochMilli()),
        )
        val fields = linkedMapOf(
            "id" to (stored.id == expected.id),
            "timelineId" to (stored.timelineId == expected.timelineId),
            "sector" to (stored.sector == expected.sector),
            "workPlaceId" to (stored.workPlaceId == expected.workPlaceId),
            "objectiveId" to (stored.objectiveId == expected.objectiveId),
            "workTypeId" to (stored.workTypeId == expected.workTypeId),
            "startTime" to (stored.startTime == expected.startTime),
            "endTime" to (stored.endTime == expected.endTime),
            "colorArgb" to (stored.colorArgb == expected.colorArgb),
            "isActive" to (stored.isActive == expected.isActive),
            "createdAt" to (stored.createdAt == expected.createdAt),
            "updatedAt" to (stored.updatedAt == expected.updatedAt),
        )
        comparison = Comparison(
            original == stored, expected == stored, fields,
            instantEquality(original.createdAt, stored.createdAt),
            instantEquality(original.updatedAt, stored.updatedAt),
        )
        comparisonOutcome = ComparisonOutcome.COMPLETE
        check(expected == stored) { "CATALOG_PERSISTED_VALUE_MISMATCH" }
        phase = Phase.COMPLETE
    }

    fun projection(): Map<String, Any> = linkedMapOf<String, Any>(
        "schema" to "stage12-selection-catalog-persistence-v1",
        "phase" to phase.name,
        "read_outcome" to readOutcome.name,
        "comparison_outcome" to comparisonOutcome.name,
    ).apply {
        reservedIdMatches?.let { put("reserved_id_matches", it) }
        comparison?.let { put("comparison", it.projection()) }
    }

    private data class InstantEquality(
        val exact: Boolean,
        val epochMillisEqual: Boolean,
        val originalMillisRepresentable: Boolean,
        val storedMillisRepresentable: Boolean,
    ) {
        fun projection(): Map<String, Any> = linkedMapOf(
            "exact" to exact,
            "epoch_millis_equal" to epochMillisEqual,
            "original_millis_representable" to originalMillisRepresentable,
            "stored_millis_representable" to storedMillisRepresentable,
        )
    }

    private data class Comparison(
        val originalEqual: Boolean,
        val persistibleEqual: Boolean,
        val fieldsEqual: Map<String, Boolean>,
        val createdAt: InstantEquality,
        val updatedAt: InstantEquality,
    ) {
        fun projection(): Map<String, Any> = linkedMapOf(
            "original_equal" to originalEqual,
            "persistible_equal" to persistibleEqual,
            "fields_equal" to fieldsEqual.toMap(),
            "instants" to linkedMapOf("createdAt" to createdAt.projection(), "updatedAt" to updatedAt.projection()),
        )
    }

    private fun instantEquality(original: Instant, stored: Instant) = InstantEquality(
        exact = original == stored,
        epochMillisEqual = original.toEpochMilli() == stored.toEpochMilli(),
        originalMillisRepresentable = Instant.ofEpochMilli(original.toEpochMilli()) == original,
        storedMillisRepresentable = Instant.ofEpochMilli(stored.toEpochMilli()) == stored,
    )
}
