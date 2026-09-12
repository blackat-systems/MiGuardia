package com.blackatsystems.miguardia.core.domain.shift

import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrence
import com.blackatsystems.miguardia.core.domain.model.RecurringOccurrenceState
import com.blackatsystems.miguardia.core.domain.model.RecurringPattern
import com.blackatsystems.miguardia.core.domain.model.RecurringPlan
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanAggregate
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanMutation
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevision
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevisionKind
import com.blackatsystems.miguardia.core.domain.model.RecurringProtectionExpectation
import com.blackatsystems.miguardia.core.domain.model.RecurringShiftProtectionVersion
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.V2ShiftBatchMutation
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import java.time.Clock
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID

enum class RecurringConflictPolicy {
    KEEP_EXISTING,
    REPLACE_AUTOMATIC_INTACT,
    KEEP_BOTH,
    CANCEL,
}

const val MAX_RECURRING_CONCRETE_SHIFTS: Int = 2_000

enum class RecurringOccupantKind {
    MANUAL,
    AUTOMATIC_INTACT,
    CUSTOMIZED,
    PROTECTED,
}

data class RecurringOccupant(
    val shift: Shift,
    val occurrence: RecurringOccurrence?,
    val kind: RecurringOccupantKind,
)

enum class RecurringDateAction {
    CREATE,
    UPDATE_AUTOMATIC,
    KEEP_EXISTING_AS_EXCLUDED,
    REPLACE_AUTOMATIC,
    KEEP_BOTH,
    PRESERVE_PROTECTED,
    RETIRE_AUTOMATIC,
    KEEP_CUSTOMIZED,
    KEEP_EXCLUDED,
    KEEP_RETIRED,
    BLOCKED_BY_CANCEL,
}

data class RecurringDateResult(
    val date: LocalDate,
    val action: RecurringDateAction,
    val occupants: List<RecurringOccupant> = emptyList(),
    val affectedShiftIds: Set<UUID> = emptySet(),
)

data class RecurringMutationPreview(
    val patternDescription: String,
    val dates: List<LocalDate>,
    val results: List<RecurringDateResult>,
    val warnings: List<ShiftPlanningWarning>,
    val medicalLeaveDates: Set<LocalDate>,
    val mutation: RecurringPlanMutation?,
) {
    val freeDates: List<LocalDate>
        get() = results
            .filter { it.action == RecurringDateAction.CREATE }
            .map(RecurringDateResult::date)

    val occupiedDates: List<LocalDate>
        get() = results.filter { it.occupants.isNotEmpty() }.map(RecurringDateResult::date)

    val protectedDates: List<LocalDate>
        get() = results
            .filter { result ->
                result.occupants.any { occupant ->
                    occupant.kind == RecurringOccupantKind.CUSTOMIZED ||
                        occupant.kind == RecurringOccupantKind.PROTECTED ||
                        occupant.kind == RecurringOccupantKind.MANUAL
                }
            }
            .map(RecurringDateResult::date)

    val concreteShiftCount: Int
        get() = results.count { result ->
            result.action in setOf(
                RecurringDateAction.CREATE,
                RecurringDateAction.UPDATE_AUTOMATIC,
                RecurringDateAction.REPLACE_AUTOMATIC,
                RecurringDateAction.KEEP_BOTH,
            )
        }

    val canConfirm: Boolean
        get() = mutation != null && (mutation.planToInsert == null || concreteShiftCount > 0)
}

fun expandRecurringDates(
    pattern: RecurringPattern,
    startDateInclusive: LocalDate,
    endDateInclusive: LocalDate,
    clock: Clock,
    zoneId: ZoneId,
): List<LocalDate> {
    val today = LocalDate.now(clock.withZone(zoneId))
    if (startDateInclusive.isBefore(today)) {
        throw InvalidLocalDataException("La repetición debe comenzar hoy o en una fecha futura.")
    }
    if (endDateInclusive.isBefore(startDateInclusive)) {
        throw InvalidLocalDataException("La fecha final no puede ser anterior a la fecha inicial.")
    }
    val dates = try {
        expandRecurringDatesInRange(pattern, startDateInclusive, endDateInclusive)
    } catch (_: DateTimeException) {
        throw InvalidLocalDataException("El rango de repetición supera las fechas admitidas.")
    } catch (_: ArithmeticException) {
        throw InvalidLocalDataException("El rango de repetición es demasiado grande.")
    }
    if (dates.isEmpty()) {
        throw InvalidLocalDataException("El patrón no produce ninguna jornada dentro del rango elegido.")
    }
    return dates
}

fun describeRecurringPattern(pattern: RecurringPattern): String = when (pattern) {
    is RecurringPattern.Weekdays -> pattern.days
        .sortedBy(DayOfWeek::getValue)
        .joinToString(prefix = "Cada ", separator = ", ") { it.spanishName() }

    is RecurringPattern.EveryNDays -> if (pattern.intervalCount == 1) {
        "Todos los días"
    } else {
        "Cada ${pattern.intervalCount} días"
    }

    is RecurringPattern.EveryNWeeks -> if (pattern.intervalCount == 1) {
        "Cada semana"
    } else {
        "Cada ${pattern.intervalCount} semanas"
    }

    is RecurringPattern.Monthly ->
        "El ${pattern.ordinal.spanishName()} ${pattern.dayOfWeek.spanishName()} de cada mes"
}

fun planNewRecurringPlan(
    plan: RecurringPlan,
    revision: RecurringPlanRevision,
    dates: List<LocalDate>,
    candidates: List<V2ShiftWrite>,
    existingShifts: List<Shift>,
    linkedOccurrences: List<RecurringOccurrence>,
    protection: RecurringProtectionExpectation,
    conflictPolicy: RecurringConflictPolicy,
    medicalLeaveDates: Set<LocalDate>,
): RecurringMutationPreview {
    require(revision.planId == plan.id && revision.revisionNumber == 1) {
        "La primera revisión no pertenece al plan nuevo"
    }
    require(revision.kind == RecurringPlanRevisionKind.ACTIVE) {
        "Un plan nuevo debe comenzar activo"
    }
    val candidatesByDate = validateCandidates(plan, revision, dates, candidates)
    val occupantsByDate = classifyOccupants(existingShifts, linkedOccurrences, protection)
        .groupBy { it.shift.localStartDate }
    val occurrenceInserts = mutableListOf<RecurringOccurrence>()
    val occurrenceUpdates = mutableListOf<RecurringOccurrence>()
    val deletions = linkedSetOf<UUID>()
    val insertions = mutableListOf<V2ShiftWrite>()
    val results = dates.map { date ->
        val candidate = candidatesByDate.getValue(date)
        val occupants = occupantsByDate[date].orEmpty()
        val outcome = resolveNewDate(conflictPolicy, occupants)
        when (outcome.action) {
            RecurringDateAction.CREATE,
            RecurringDateAction.KEEP_BOTH,
            -> {
                insertions += candidate
                occurrenceInserts += automaticOccurrence(revision, candidate)
            }

            RecurringDateAction.REPLACE_AUTOMATIC -> {
                insertions += candidate
                occurrenceInserts += automaticOccurrence(revision, candidate)
                outcome.occupants.forEach { occupant ->
                    val occurrence = requireNotNull(occupant.occurrence)
                    deletions += occupant.shift.id
                    occurrenceUpdates += occurrence.retired(revision.createdAt)
                }
            }

            RecurringDateAction.KEEP_EXISTING_AS_EXCLUDED -> {
                occurrenceInserts += excludedOccurrence(revision, date)
            }

            RecurringDateAction.BLOCKED_BY_CANCEL -> Unit
            else -> error("Acción inesperada al crear un plan: ${outcome.action}")
        }
        outcome.copy(date = date)
    }
    val blockedByCancel = results.any { it.action == RecurringDateAction.BLOCKED_BY_CANCEL }
    val mutation = if (
        blockedByCancel ||
        insertions.isEmpty()
    ) {
        null
    } else {
        RecurringPlanMutation(
            planToInsert = plan,
            revisionToInsert = revision,
            occurrencesToInsert = occurrenceInserts,
            occurrencesToUpdate = occurrenceUpdates,
            shiftMutation = V2ShiftBatchMutation(
                shiftIdsToDelete = deletions,
                shiftsToInsert = insertions,
                explicitDayStatusDatesToClear = insertions
                    .mapTo(linkedSetOf()) { it.shift.localStartDate },
            ),
        )
    }
    return RecurringMutationPreview(
        patternDescription = describeRecurringPattern(revision.pattern),
        dates = dates,
        results = results,
        warnings = evaluateResultWarnings(
            existingShifts = existingShifts,
            candidates = insertions,
            deletedIds = deletions,
        ),
        medicalLeaveDates = medicalLeaveDates.intersect(dates.toSet()),
        mutation = mutation,
    )
}

fun planRecurringRevision(
    current: RecurringPlanAggregate,
    revision: RecurringPlanRevision,
    dates: List<LocalDate>,
    candidates: List<V2ShiftWrite>,
    existingShifts: List<Shift>,
    linkedOccurrences: List<RecurringOccurrence>,
    protection: RecurringProtectionExpectation,
    conflictPolicy: RecurringConflictPolicy,
    medicalLeaveDates: Set<LocalDate>,
): RecurringMutationPreview {
    validateNextRevision(current, revision, RecurringPlanRevisionKind.ACTIVE)
    val candidatesByDate = validateCandidates(current.plan, revision, dates, candidates)
    val allOccurrencesByShift = linkedOccurrences
        .mapNotNull { occurrence -> occurrence.shiftId?.let { it to occurrence } }
        .toMap()
    val currentByDate = current.occurrences.associateBy(RecurringOccurrence::localDate)
    val existingById = existingShifts.associateBy(Shift::id)
    val classified = classifyOccupants(existingShifts, linkedOccurrences, protection)
    val occurrenceInserts = mutableListOf<RecurringOccurrence>()
    val occurrenceUpdates = linkedMapOf<Pair<UUID, LocalDate>, RecurringOccurrence>()
    val deletions = linkedSetOf<UUID>()
    val insertions = mutableListOf<V2ShiftWrite>()
    val updates = mutableListOf<V2ShiftWrite>()
    val results = mutableListOf<RecurringDateResult>()
    fun occupant(occurrence: RecurringOccurrence, kind: RecurringOccupantKind): RecurringOccupant {
        val shiftId = requireNotNull(occurrence.shiftId)
        val shift = requireNotNull(existingById[shiftId]) {
            "La ocurrencia conservada no mantiene su jornada"
        }
        return RecurringOccupant(shift, occurrence, kind)
    }
    val targetDates = (dates + current.occurrences
        .filter { !it.localDate.isBefore(revision.effectiveFrom) }
        .map(RecurringOccurrence::localDate))
        .distinct()
        .sorted()

    targetDates.forEach { date ->
        val existingOccurrence = currentByDate[date]
        val candidate = candidatesByDate[date]
        when {
            existingOccurrence?.state == RecurringOccurrenceState.CUSTOMIZED -> {
                results += RecurringDateResult(
                    date,
                    RecurringDateAction.KEEP_CUSTOMIZED,
                    occupants = listOf(occupant(existingOccurrence, RecurringOccupantKind.CUSTOMIZED)),
                )
            }

            existingOccurrence?.state == RecurringOccurrenceState.EXCLUDED -> {
                results += RecurringDateResult(date, RecurringDateAction.KEEP_EXCLUDED)
            }

            existingOccurrence?.state == RecurringOccurrenceState.RETIRED && candidate == null -> {
                results += RecurringDateResult(date, RecurringDateAction.KEEP_RETIRED)
            }

            existingOccurrence?.state == RecurringOccurrenceState.AUTOMATIC &&
                !isAutomaticIntact(existingOccurrence, protection) -> {
                val shift = requireNotNull(existingById[existingOccurrence.shiftId]) {
                    "La ocurrencia protegida no conserva su jornada"
                }
                results += RecurringDateResult(
                    date = date,
                    action = RecurringDateAction.PRESERVE_PROTECTED,
                    occupants = listOf(
                        RecurringOccupant(
                            shift = shift,
                            occurrence = existingOccurrence,
                            kind = RecurringOccupantKind.PROTECTED,
                        ),
                    ),
                )
            }

            existingOccurrence?.state == RecurringOccurrenceState.AUTOMATIC && candidate == null -> {
                val shiftId = requireNotNull(existingOccurrence.shiftId)
                deletions += shiftId
                occurrenceUpdates[existingOccurrence.key()] = existingOccurrence.retired(revision.createdAt)
                results += RecurringDateResult(
                    date = date,
                    action = RecurringDateAction.RETIRE_AUTOMATIC,
                    occupants = listOf(occupant(existingOccurrence, RecurringOccupantKind.AUTOMATIC_INTACT)),
                    affectedShiftIds = setOf(shiftId),
                )
            }

            existingOccurrence?.state == RecurringOccurrenceState.AUTOMATIC && candidate != null -> {
                val oldShiftId = requireNotNull(existingOccurrence.shiftId)
                require(candidate.shift.id == oldShiftId) {
                    "Actualizar una ocurrencia automática debe conservar su UUID"
                }
                val ownOccupant = occupant(existingOccurrence, RecurringOccupantKind.AUTOMATIC_INTACT)
                val otherOccupants = classified.filter { classifiedOccupant ->
                    classifiedOccupant.shift.localStartDate == date &&
                        classifiedOccupant.shift.id != oldShiftId
                }
                if (otherOccupants.isEmpty()) {
                    updates += candidate
                    occurrenceUpdates[existingOccurrence.key()] = existingOccurrence.copy(
                        revisionId = revision.id,
                        updatedAt = revision.createdAt,
                    )
                    results += RecurringDateResult(
                        date = date,
                        action = RecurringDateAction.UPDATE_AUTOMATIC,
                        occupants = listOf(ownOccupant),
                        affectedShiftIds = setOf(oldShiftId),
                    )
                } else {
                    val outcome = resolveNewDate(conflictPolicy, otherOccupants).copy(date = date)
                    when (outcome.action) {
                        RecurringDateAction.KEEP_BOTH -> {
                            updates += candidate
                            occurrenceUpdates[existingOccurrence.key()] = existingOccurrence.copy(
                                revisionId = revision.id,
                                updatedAt = revision.createdAt,
                            )
                        }

                        RecurringDateAction.REPLACE_AUTOMATIC -> {
                            updates += candidate
                            occurrenceUpdates[existingOccurrence.key()] = existingOccurrence.copy(
                                revisionId = revision.id,
                                updatedAt = revision.createdAt,
                            )
                            outcome.occupants.forEach { otherOccupant ->
                                deletions += otherOccupant.shift.id
                                val otherOccurrence = requireNotNull(
                                    allOccurrencesByShift[otherOccupant.shift.id],
                                )
                                occurrenceUpdates[otherOccurrence.key()] =
                                    otherOccurrence.retired(revision.createdAt)
                            }
                        }

                        RecurringDateAction.KEEP_EXISTING_AS_EXCLUDED -> {
                            deletions += oldShiftId
                            occurrenceUpdates[existingOccurrence.key()] = existingOccurrence.copy(
                                revisionId = revision.id,
                                shiftId = null,
                                state = RecurringOccurrenceState.EXCLUDED,
                                updatedAt = revision.createdAt,
                            )
                        }

                        RecurringDateAction.BLOCKED_BY_CANCEL -> Unit
                        else -> error("Acción inesperada al resolver una jornada automática: ${outcome.action}")
                    }
                    results += outcome.copy(
                        occupants = listOf(ownOccupant) + outcome.occupants,
                        affectedShiftIds = outcome.affectedShiftIds + when (outcome.action) {
                            RecurringDateAction.KEEP_BOTH,
                            RecurringDateAction.REPLACE_AUTOMATIC,
                            RecurringDateAction.KEEP_EXISTING_AS_EXCLUDED,
                            -> setOf(oldShiftId)

                            else -> emptySet()
                        },
                    )
                }
            }

            candidate != null -> {
                val ownOldShiftId = existingOccurrence?.shiftId
                val otherOccupants = classified.filter { occupant ->
                    occupant.shift.localStartDate == date && occupant.shift.id != ownOldShiftId
                }
                val outcome = resolveNewDate(conflictPolicy, otherOccupants).copy(date = date)
                when (outcome.action) {
                    RecurringDateAction.CREATE,
                    RecurringDateAction.KEEP_BOTH,
                    -> {
                        insertions += candidate
                        val automatic = automaticOccurrence(revision, candidate)
                        if (existingOccurrence == null) {
                            occurrenceInserts += automatic
                        } else {
                            occurrenceUpdates[existingOccurrence.key()] = automatic.copy(
                                createdAt = existingOccurrence.createdAt,
                            )
                        }
                    }

                    RecurringDateAction.REPLACE_AUTOMATIC -> {
                        insertions += candidate
                        val automatic = automaticOccurrence(revision, candidate)
                        if (existingOccurrence == null) {
                            occurrenceInserts += automatic
                        } else {
                            occurrenceUpdates[existingOccurrence.key()] = automatic.copy(
                                createdAt = existingOccurrence.createdAt,
                            )
                        }
                        outcome.occupants.forEach { occupant ->
                            deletions += occupant.shift.id
                            val otherOccurrence = requireNotNull(allOccurrencesByShift[occupant.shift.id])
                            occurrenceUpdates[otherOccurrence.key()] = otherOccurrence.retired(revision.createdAt)
                        }
                    }

                    RecurringDateAction.KEEP_EXISTING_AS_EXCLUDED -> {
                        val excluded = excludedOccurrence(revision, date)
                        if (existingOccurrence == null) {
                            occurrenceInserts += excluded
                        } else {
                            occurrenceUpdates[existingOccurrence.key()] = excluded.copy(
                                createdAt = existingOccurrence.createdAt,
                            )
                        }
                    }

                    RecurringDateAction.BLOCKED_BY_CANCEL -> Unit
                    else -> error("Acción inesperada al versionar un plan: ${outcome.action}")
                }
                results += outcome
            }

            else -> Unit
        }
    }

    val mutation = if (results.any { it.action == RecurringDateAction.BLOCKED_BY_CANCEL }) {
        null
    } else {
        RecurringPlanMutation(
            revisionToInsert = revision,
            occurrencesToInsert = occurrenceInserts,
            occurrencesToUpdate = occurrenceUpdates.values.toList(),
            shiftMutation = V2ShiftBatchMutation(
                shiftIdsToDelete = deletions,
                shiftsToInsert = insertions,
                shiftsToUpdate = updates,
                explicitDayStatusDatesToClear = insertions
                    .mapTo(linkedSetOf()) { it.shift.localStartDate },
            ),
        )
    }
    return RecurringMutationPreview(
        patternDescription = describeRecurringPattern(revision.pattern),
        dates = targetDates,
        results = results.sortedBy(RecurringDateResult::date),
        warnings = evaluateResultWarnings(existingShifts, insertions + updates, deletions),
        medicalLeaveDates = medicalLeaveDates.intersect(targetDates.toSet()),
        mutation = mutation,
    )
}

fun planRecurringFinalization(
    current: RecurringPlanAggregate,
    finalRevision: RecurringPlanRevision,
    existingShifts: List<Shift>,
    protection: RecurringProtectionExpectation,
): RecurringMutationPreview {
    validateNextRevision(current, finalRevision, RecurringPlanRevisionKind.FINALIZED)
    val existingById = existingShifts.associateBy(Shift::id)
    val occurrenceUpdates = mutableListOf<RecurringOccurrence>()
    val deletions = linkedSetOf<UUID>()
    val results = current.occurrences
        .filter { !it.localDate.isBefore(finalRevision.effectiveFrom) }
        .sortedBy(RecurringOccurrence::localDate)
        .map { occurrence ->
            when (occurrence.state) {
                RecurringOccurrenceState.AUTOMATIC -> {
                    val shift = requireNotNull(existingById[occurrence.shiftId]) {
                        "La ocurrencia automática no conserva su jornada"
                    }
                    if (isAutomaticIntact(occurrence, protection)) {
                        deletions += shift.id
                        occurrenceUpdates += occurrence.retired(finalRevision.createdAt)
                        RecurringDateResult(
                            date = occurrence.localDate,
                            action = RecurringDateAction.RETIRE_AUTOMATIC,
                            occupants = listOf(
                                RecurringOccupant(
                                    shift = shift,
                                    occurrence = occurrence,
                                    kind = RecurringOccupantKind.AUTOMATIC_INTACT,
                                ),
                            ),
                            affectedShiftIds = setOf(shift.id),
                        )
                    } else {
                        RecurringDateResult(
                            date = occurrence.localDate,
                            action = RecurringDateAction.PRESERVE_PROTECTED,
                            occupants = listOf(
                                RecurringOccupant(
                                    shift = shift,
                                    occurrence = occurrence,
                                    kind = RecurringOccupantKind.PROTECTED,
                                ),
                            ),
                        )
                    }
                }

                RecurringOccurrenceState.CUSTOMIZED -> {
                    val shift = requireNotNull(existingById[occurrence.shiftId]) {
                        "La ocurrencia personalizada no conserva su jornada"
                    }
                    RecurringDateResult(
                        occurrence.localDate,
                        RecurringDateAction.KEEP_CUSTOMIZED,
                        occupants = listOf(
                            RecurringOccupant(
                                shift = shift,
                                occurrence = occurrence,
                                kind = RecurringOccupantKind.CUSTOMIZED,
                            ),
                        ),
                    )
                }

                RecurringOccurrenceState.EXCLUDED ->
                    RecurringDateResult(occurrence.localDate, RecurringDateAction.KEEP_EXCLUDED)

                RecurringOccurrenceState.RETIRED ->
                    RecurringDateResult(occurrence.localDate, RecurringDateAction.KEEP_RETIRED)
            }
        }
    return RecurringMutationPreview(
        patternDescription = "Finalizar desde ${finalRevision.effectiveFrom}",
        dates = results.map(RecurringDateResult::date),
        results = results,
        warnings = emptyList(),
        medicalLeaveDates = results
            .map(RecurringDateResult::date)
            .filterTo(linkedSetOf(), protection::hasApplicableSituation),
        mutation = RecurringPlanMutation(
            revisionToInsert = finalRevision,
            occurrencesToUpdate = occurrenceUpdates,
            shiftMutation = V2ShiftBatchMutation(shiftIdsToDelete = deletions),
        ),
    )
}

private fun expandRecurringDatesInRange(
    pattern: RecurringPattern,
    start: LocalDate,
    end: LocalDate,
): List<LocalDate> = when (pattern) {
    is RecurringPattern.Weekdays -> expandWeekdays(pattern.days, start, end)
    is RecurringPattern.EveryNDays -> expandFixedDays(pattern.intervalCount.toLong(), start, end)
    is RecurringPattern.EveryNWeeks -> expandFixedDays(
        Math.multiplyExact(pattern.intervalCount.toLong(), DAYS_PER_WEEK),
        start,
        end,
    )

    is RecurringPattern.Monthly -> expandMonthly(pattern, start, end)
}

private fun expandWeekdays(
    days: Set<DayOfWeek>,
    start: LocalDate,
    end: LocalDate,
): List<LocalDate> {
    val inclusiveDays = Math.addExact(ChronoUnit.DAYS.between(start, end), 1L)
    val fullWeeks = inclusiveDays / DAYS_PER_WEEK
    val remainder = (inclusiveDays % DAYS_PER_WEEK).toInt()
    var expectedCount = Math.multiplyExact(fullWeeks, days.size.toLong())
    repeat(remainder) { offset ->
        if (start.plusDays(offset.toLong()).dayOfWeek in days) expectedCount++
    }
    requireCollectionCapacity(expectedCount)
    return buildList(expectedCount.toInt()) {
        var cursor = start
        while (!cursor.isAfter(end)) {
            if (cursor.dayOfWeek in days) add(cursor)
            if (cursor == end) break
            cursor = cursor.plusDays(1)
        }
    }
}

private fun expandFixedDays(
    stepDays: Long,
    start: LocalDate,
    end: LocalDate,
): List<LocalDate> {
    val distance = ChronoUnit.DAYS.between(start, end)
    val expectedCount = Math.addExact(distance / stepDays, 1L)
    requireCollectionCapacity(expectedCount)
    return buildList(expectedCount.toInt()) {
        var cursor = start
        while (!cursor.isAfter(end)) {
            add(cursor)
            val remaining = ChronoUnit.DAYS.between(cursor, end)
            if (remaining < stepDays) break
            cursor = cursor.plusDays(stepDays)
        }
    }
}

private fun expandMonthly(
    pattern: RecurringPattern.Monthly,
    start: LocalDate,
    end: LocalDate,
): List<LocalDate> {
    val startMonth = YearMonth.from(start)
    val endMonth = YearMonth.from(end)
    val monthCount = Math.addExact(ChronoUnit.MONTHS.between(startMonth, endMonth), 1L)
    if (monthCount > MAX_RECURRING_CONCRETE_SHIFTS + MONTHLY_BOUNDARY_MONTHS) {
        requireCollectionCapacity(monthCount - MONTHLY_BOUNDARY_MONTHS)
    }
    var expectedCount = 0L
    var countMonth = startMonth
    while (!countMonth.isAfter(endMonth)) {
        if (monthlyCandidate(pattern, countMonth) in start..end) expectedCount++
        if (countMonth == endMonth) break
        countMonth = countMonth.plusMonths(1)
    }
    requireCollectionCapacity(expectedCount)
    return buildList(expectedCount.toInt()) {
        var month = startMonth
        while (!month.isAfter(endMonth)) {
            val candidate = monthlyCandidate(pattern, month)
            if (candidate in start..end) add(candidate)
            if (month == endMonth) break
            month = month.plusMonths(1)
        }
    }
}

private fun monthlyCandidate(
    pattern: RecurringPattern.Monthly,
    month: YearMonth,
): LocalDate = when (pattern.ordinal) {
    com.blackatsystems.miguardia.core.domain.model.MonthlyOrdinal.FIRST ->
        month.atDay(1).with(TemporalAdjusters.firstInMonth(pattern.dayOfWeek))

    com.blackatsystems.miguardia.core.domain.model.MonthlyOrdinal.SECOND ->
        month.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(2, pattern.dayOfWeek))

    com.blackatsystems.miguardia.core.domain.model.MonthlyOrdinal.THIRD ->
        month.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(3, pattern.dayOfWeek))

    com.blackatsystems.miguardia.core.domain.model.MonthlyOrdinal.FOURTH ->
        month.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(4, pattern.dayOfWeek))

    com.blackatsystems.miguardia.core.domain.model.MonthlyOrdinal.LAST ->
        month.atEndOfMonth().with(TemporalAdjusters.previousOrSame(pattern.dayOfWeek))
}

private fun validateCandidates(
    plan: RecurringPlan,
    revision: RecurringPlanRevision,
    dates: List<LocalDate>,
    candidates: List<V2ShiftWrite>,
): Map<LocalDate, V2ShiftWrite> {
    val expectedDates = expandRecurringDatesInRange(
        revision.pattern,
        revision.effectiveFrom,
        revision.endDateInclusive,
    )
    require(dates == expectedDates) {
        "Las fechas recurrentes deben ser la expansión exacta del patrón y su rango"
    }
    val candidatesByDate = candidates.associateBy { it.shift.localStartDate }
    require(candidatesByDate.size == candidates.size) { "Sólo puede prepararse una jornada por fecha del plan" }
    require(candidatesByDate.keys == dates.toSet()) {
        "Las jornadas preparadas no coinciden con todas las fechas del plan"
    }
    candidates.forEach { candidate ->
        validateCandidateAgainstRevision(plan, revision, candidate)
    }
    return candidatesByDate
}

private fun validateCandidateAgainstRevision(
    plan: RecurringPlan,
    revision: RecurringPlanRevision,
    candidate: V2ShiftWrite,
) {
    val shift = candidate.shift
    val snapshot = candidate.snapshot
    require(snapshot.timelineId == plan.timelineId && snapshot.sector == plan.sector) {
        "La jornada recurrente debe usar la línea temporal y el rubro del plan"
    }
    require(
        snapshot.templateId == revision.templateId &&
            snapshot.workPlaceId == revision.workPlaceId &&
            snapshot.objectiveId == revision.objectiveId &&
            snapshot.workTypeId == revision.workTypeId,
    ) { "La jornada recurrente no coincide con las fuentes de la revisión" }
    require(
        snapshot.workTypeNameSnapshot == revision.workTypeNameSnapshot &&
            snapshot.workTypeBehaviorSnapshot == revision.workTypeBehaviorSnapshot,
    ) { "La jornada recurrente no coincide con el tipo histórico de la revisión" }
    require(
        shift.sourceObjectiveId == revision.objectiveId &&
            shift.objectiveNameSnapshot == revision.objectiveNameSnapshot &&
            shift.objectiveAbbreviationSnapshot == revision.objectiveAbbreviationSnapshot &&
            shift.objectiveAddressSnapshot == revision.objectiveAddressSnapshot,
    ) { "La jornada recurrente no coincide con el lugar histórico de la revisión" }
    require(
        shift.startTimeSnapshot == revision.startTimeSnapshot &&
            shift.endTimeSnapshot == revision.endTimeSnapshot &&
            shift.colorArgbSnapshot == revision.colorArgbSnapshot &&
            shift.position == revision.positionSnapshot &&
            shift.zoneId == revision.zoneId,
    ) { "La jornada recurrente no coincide con la fotografía horaria de la revisión" }
    val expectedStart = shift.localStartDate
        .atTime(revision.startTimeSnapshot)
        .atZone(revision.zoneId)
        .toInstant()
    val expectedEndDate = if (revision.endTimeSnapshot > revision.startTimeSnapshot) {
        shift.localStartDate
    } else {
        shift.localStartDate.plusDays(1)
    }
    val expectedEnd = expectedEndDate
        .atTime(revision.endTimeSnapshot)
        .atZone(revision.zoneId)
        .toInstant()
    require(shift.startAt == expectedStart && shift.endAt == expectedEnd) {
        "Los instantes de la jornada recurrente no coinciden con su fecha, zona y horario"
    }
}

private fun classifyOccupants(
    shifts: List<Shift>,
    occurrences: List<RecurringOccurrence>,
    protection: RecurringProtectionExpectation,
): List<RecurringOccupant> {
    val occurrencesByShiftId = occurrences
        .mapNotNull { occurrence -> occurrence.shiftId?.let { it to occurrence } }
        .toMap()
    return shifts.map { shift ->
        val occurrence = occurrencesByShiftId[shift.id]
        val kind = when {
            occurrence == null -> RecurringOccupantKind.MANUAL
            occurrence.state == RecurringOccurrenceState.CUSTOMIZED -> RecurringOccupantKind.CUSTOMIZED
            occurrence.state != RecurringOccurrenceState.AUTOMATIC -> RecurringOccupantKind.PROTECTED
            isAutomaticIntact(occurrence, protection) -> RecurringOccupantKind.AUTOMATIC_INTACT
            else -> RecurringOccupantKind.PROTECTED
        }
        RecurringOccupant(shift, occurrence, kind)
    }
}

private fun isAutomaticIntact(
    occurrence: RecurringOccurrence,
    protection: RecurringProtectionExpectation,
): Boolean {
    if (occurrence.state != RecurringOccurrenceState.AUTOMATIC) return false
    val shiftId = requireNotNull(occurrence.shiftId)
    val version = requireNotNull(protection.versionsByShiftId[shiftId]) {
        "Falta la expectativa de protección para una jornada recurrente"
    }
    return !version.isProtected && !protection.hasApplicableSituation(occurrence.localDate)
}

private fun resolveNewDate(
    policy: RecurringConflictPolicy,
    occupants: List<RecurringOccupant>,
): RecurringDateResult {
    if (occupants.isEmpty()) {
        return RecurringDateResult(LocalDate.MIN, RecurringDateAction.CREATE)
    }
    return when (policy) {
        RecurringConflictPolicy.KEEP_EXISTING -> RecurringDateResult(
            LocalDate.MIN,
            RecurringDateAction.KEEP_EXISTING_AS_EXCLUDED,
            occupants,
        )

        RecurringConflictPolicy.REPLACE_AUTOMATIC_INTACT -> {
            if (occupants.all { it.kind == RecurringOccupantKind.AUTOMATIC_INTACT }) {
                RecurringDateResult(
                    LocalDate.MIN,
                    RecurringDateAction.REPLACE_AUTOMATIC,
                    occupants,
                    occupants.mapTo(linkedSetOf()) { it.shift.id },
                )
            } else {
                RecurringDateResult(
                    LocalDate.MIN,
                    RecurringDateAction.KEEP_EXISTING_AS_EXCLUDED,
                    occupants,
                )
            }
        }

        RecurringConflictPolicy.KEEP_BOTH -> RecurringDateResult(
            LocalDate.MIN,
            RecurringDateAction.KEEP_BOTH,
            occupants,
        )

        RecurringConflictPolicy.CANCEL -> RecurringDateResult(
            LocalDate.MIN,
            RecurringDateAction.BLOCKED_BY_CANCEL,
            occupants,
        )
    }
}

private fun automaticOccurrence(
    revision: RecurringPlanRevision,
    write: V2ShiftWrite,
): RecurringOccurrence = RecurringOccurrence(
    planId = revision.planId,
    localDate = write.shift.localStartDate,
    revisionId = revision.id,
    shiftId = write.shift.id,
    state = RecurringOccurrenceState.AUTOMATIC,
    createdAt = revision.createdAt,
    updatedAt = revision.createdAt,
)

private fun excludedOccurrence(
    revision: RecurringPlanRevision,
    date: LocalDate,
): RecurringOccurrence = RecurringOccurrence(
    planId = revision.planId,
    localDate = date,
    revisionId = revision.id,
    shiftId = null,
    state = RecurringOccurrenceState.EXCLUDED,
    createdAt = revision.createdAt,
    updatedAt = revision.createdAt,
)

private fun RecurringOccurrence.retired(timestamp: java.time.Instant): RecurringOccurrence = copy(
    shiftId = null,
    state = RecurringOccurrenceState.RETIRED,
    updatedAt = timestamp,
)

private fun RecurringOccurrence.key(): Pair<UUID, LocalDate> = planId to localDate

private fun validateNextRevision(
    current: RecurringPlanAggregate,
    revision: RecurringPlanRevision,
    expectedKind: RecurringPlanRevisionKind,
) {
    require(current.latestRevision.kind == RecurringPlanRevisionKind.ACTIVE) {
        "Un plan finalizado no admite revisiones posteriores"
    }
    require(revision.planId == current.plan.id) { "La revisión no pertenece al plan" }
    require(revision.revisionNumber == current.latestRevision.revisionNumber + 1) {
        "El número de revisión debe avanzar exactamente uno"
    }
    require(revision.kind == expectedKind) { "La revisión no tiene el tipo esperado" }
}

private fun evaluateResultWarnings(
    existingShifts: List<Shift>,
    candidates: List<V2ShiftWrite>,
    deletedIds: Set<UUID>,
): List<ShiftPlanningWarning> = evaluateShiftWarnings(
    existingShifts = existingShifts.filterNot { existing ->
        existing.id in deletedIds || candidates.any { it.shift.id == existing.id }
    },
    candidateShifts = candidates.map(V2ShiftWrite::shift),
)

private fun requireCollectionCapacity(count: Long) {
    if (count > MAX_RECURRING_CONCRETE_SHIFTS) {
        throw InvalidLocalDataException(
            "Un plan puede incluir como máximo 2.000 jornadas concretas.",
        )
    }
}

private fun DayOfWeek.spanishName(): String = when (this) {
    DayOfWeek.MONDAY -> "lunes"
    DayOfWeek.TUESDAY -> "martes"
    DayOfWeek.WEDNESDAY -> "miércoles"
    DayOfWeek.THURSDAY -> "jueves"
    DayOfWeek.FRIDAY -> "viernes"
    DayOfWeek.SATURDAY -> "sábado"
    DayOfWeek.SUNDAY -> "domingo"
}

private fun com.blackatsystems.miguardia.core.domain.model.MonthlyOrdinal.spanishName(): String =
    when (this) {
        com.blackatsystems.miguardia.core.domain.model.MonthlyOrdinal.FIRST -> "primer"
        com.blackatsystems.miguardia.core.domain.model.MonthlyOrdinal.SECOND -> "segundo"
        com.blackatsystems.miguardia.core.domain.model.MonthlyOrdinal.THIRD -> "tercer"
        com.blackatsystems.miguardia.core.domain.model.MonthlyOrdinal.FOURTH -> "cuarto"
        com.blackatsystems.miguardia.core.domain.model.MonthlyOrdinal.LAST -> "último"
    }

private const val DAYS_PER_WEEK = 7L
private const val MONTHLY_BOUNDARY_MONTHS = 2L
