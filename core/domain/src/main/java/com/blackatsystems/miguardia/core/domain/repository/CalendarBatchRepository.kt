package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import java.time.LocalDate
import java.time.LocalTime

enum class CalendarBatchAction {
    DAY_OFF,
    UNDEFINED,
    /** Destructive, explicitly previewed clearing; UNDEFINED retains its non-destructive contract. */
    CLEAR,
    AVAILABILITY,
}

data class CalendarBatchAvailability(
    val start: LocalTime,
    val end: LocalTime,
    val label: AvailabilityLabel,
)

enum class CalendarBatchDateState {
    APPLICABLE,
    UNCHANGED,
    CONFLICT,
}

data class CalendarBatchDateResult(
    val date: LocalDate,
    val state: CalendarBatchDateState,
    val explanation: String,
)

data class CalendarBatchPreview(
    val action: CalendarBatchAction,
    val dates: Set<LocalDate>,
    val availability: CalendarBatchAvailability? = null,
    val results: List<CalendarBatchDateResult>,
    val expectationToken: String,
    /** Private files eligible for reference-checked cleanup only after apply succeeds. */
    val removedNoteAttachmentStorageKeys: Set<String> = emptySet(),
    val removedSituationEvidenceStorageKeys: Set<String> = emptySet(),
) {
    init {
        require(dates.isNotEmpty())
        require(results.map { it.date }.toSet() == dates)
        require((action == CalendarBatchAction.AVAILABILITY) == (availability != null))
        require(expectationToken.isNotBlank())
    }

    val applicable: List<CalendarBatchDateResult> get() = results.filter { it.state == CalendarBatchDateState.APPLICABLE }
    val unchanged: List<CalendarBatchDateResult> get() = results.filter { it.state == CalendarBatchDateState.UNCHANGED }
    val conflicts: List<CalendarBatchDateResult> get() = results.filter { it.state == CalendarBatchDateState.CONFLICT }
}

interface CalendarBatchRepository {
    suspend fun preview(
        action: CalendarBatchAction,
        dates: Set<LocalDate>,
        availability: CalendarBatchAvailability? = null,
    ): CalendarBatchPreview

    /** Revalidates the complete preview and writes every applicable date in one transaction. */
    suspend fun apply(expected: CalendarBatchPreview)
}
