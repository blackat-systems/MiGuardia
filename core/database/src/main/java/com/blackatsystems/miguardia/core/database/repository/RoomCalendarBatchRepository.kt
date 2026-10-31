package com.blackatsystems.miguardia.core.database.repository

import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaV2Database
import com.blackatsystems.miguardia.core.database.mapping.toAvailabilityEntity
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toEntity
import com.blackatsystems.miguardia.core.database.mapping.encodeSector
import com.blackatsystems.miguardia.core.database.validation.requireValidV2LocalData
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowDraft
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.buildAvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAction
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAvailability
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchDateResult
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchDateState
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchPreview
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchRepository
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import com.blackatsystems.miguardia.core.domain.work.ResolvedWorkConfigurationRevision
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.security.MessageDigest

internal class RoomCalendarBatchRepository(
    private val database: MiGuardiaV2Database,
    private val clock: Clock = Clock.systemUTC(),
) : CalendarBatchRepository {
    override suspend fun preview(
        action: CalendarBatchAction,
        dates: Set<LocalDate>,
        availability: CalendarBatchAvailability?,
    ): CalendarBatchPreview = database.withTransaction {
        previewInternal(action, dates, availability)
    }

    override suspend fun apply(expected: CalendarBatchPreview) {
        database.withTransaction {
            val current = previewInternal(expected.action, expected.dates, expected.availability)
            if (current != expected) {
                throw ConflictingLocalWriteException(
                    "Los días cambiaron mientras confirmabas. Revisá el detalle antes de volver a guardar.",
                )
            }
            if (current.conflicts.isNotEmpty()) {
                throw ConflictingLocalWriteException(
                    "Hay ${current.conflicts.size} ${if (current.conflicts.size == 1) "día incompatible" else "días incompatibles"}. No se cambió ninguna fecha.",
                )
            }
            when (current.action) {
                CalendarBatchAction.CLEAR -> CalendarClearOperation(database, clock).apply(current.dates)
                CalendarBatchAction.DAY_OFF -> current.applicable.forEach { result ->
                    database.explicitDayStatusDao().set(
                        ExplicitDayStatus(result.date, ExplicitDayStatusType.DAY_OFF).toEntity(),
                    )
                }
                CalendarBatchAction.UNDEFINED -> current.applicable.forEach { result ->
                    database.explicitDayStatusDao().set(
                        ExplicitDayStatus(result.date, ExplicitDayStatusType.UNDEFINED).toEntity(),
                    )
                }
                CalendarBatchAction.AVAILABILITY -> {
                    val input = requireNotNull(current.availability)
                    val history = requireNotNull(database.requireValidV2LocalData()) {
                        "Todavía no existe una configuración laboral."
                    }
                    val zone = AppDefaults.zoneId()
                    val timestamp = clock.instant().truncatedTo(ChronoUnit.MILLIS)
                    current.applicable.forEach { result ->
                        val start = ZonedDateTime.of(result.date, input.start, zone).toInstant()
                        val endDate = if (input.end > input.start) result.date else result.date.plusDays(1)
                        val end = ZonedDateTime.of(endDate, input.end, zone).toInstant()
                        val configuration = ResolvedWorkConfigurationRevision.resolve(history, result.date)
                        val record = buildAvailabilityWindowRecord(
                            draft = AvailabilityWindowDraft(
                                id = UUID.randomUUID(),
                                ownerLocalDate = result.date,
                                zoneId = zone,
                                start = start,
                                end = end,
                                labelOverride = input.label,
                            ),
                            configuration = configuration,
                            timestamp = timestamp,
                        )
                        database.availabilityWindowDao().insert(record.toAvailabilityEntity())
                    }
                }
            }
            database.requireValidV2LocalData()
        }
    }

    private suspend fun previewInternal(
        action: CalendarBatchAction,
        dates: Set<LocalDate>,
        availability: CalendarBatchAvailability?,
    ): CalendarBatchPreview {
        val selected = dates.toSortedSet()
        require(selected.isNotEmpty() && selected.size <= 366) { "Seleccioná entre 1 y 366 días." }
        require((action == CalendarBatchAction.AVAILABILITY) == (availability != null))
        if (action == CalendarBatchAction.CLEAR) {
            return CalendarClearOperation(database, clock).preview(selected)
        }
        val history = database.requireValidV2LocalData()
        val statuses = database.explicitDayStatusDao().getAll()
            .associate { LocalDate.parse(it.localDate) to it.toDomain().type }
        val shifts = database.shiftDao().getStartingBetween(selected.first().toString(), selected.last().toString())
            .groupBy { LocalDate.parse(it.localStartDate) }
        val medicalLeaves = database.medicalLeaveDao().getAll()
        val vacations = database.vacationDao().getAll()
        val availabilityByDate = selected.associateWith { date ->
            database.availabilityWindowDao().getOn(date.toString())
        }
        val results = selected.map { date ->
            val current = statuses[date]
            when (action) {
                CalendarBatchAction.CLEAR -> error("Clear uses its own full-reference preview")
                CalendarBatchAction.DAY_OFF -> when {
                    current == ExplicitDayStatusType.DAY_OFF -> unchanged(date, "Ya estaba marcado como Franco.")
                    shifts[date].orEmpty().any { it.status == "PLANNED" } -> conflict(date, "Tiene una o más jornadas activas. No se borrarán para marcar Franco.")
                    else -> applicable(date, "Se marcará como Franco. Se conservan situaciones, carpetas médicas, vacaciones y disponibilidad existentes; no se eliminan registros.")
                }
                CalendarBatchAction.UNDEFINED -> when {
                    current == ExplicitDayStatusType.UNDEFINED -> unchanged(date, "Ya estaba marcado como Sin definir.")
                    current == ExplicitDayStatusType.DAY_OFF -> conflict(date, "Está marcado como Franco.")
                    shifts[date].orEmpty().isNotEmpty() -> conflict(date, "Tiene una o más jornadas.")
                    medicalLeaves.any { date.toString() >= it.startDate && date.toString() <= it.endDateInclusive } ->
                        conflict(date, "Está alcanzado por una carpeta médica.")
                    vacations.any { date.toString() >= it.startDate && date.toString() <= it.endDateInclusive } ->
                        conflict(date, "Está alcanzado por vacaciones.")
                    availabilityByDate.getValue(date).isNotEmpty() ->
                        conflict(date, "Tiene disponibilidad registrada.")
                    else -> applicable(date, "Se marcará como Sin definir.")
                }
                CalendarBatchAction.AVAILABILITY -> {
                    val input = requireNotNull(availability)
                    if (history == null) {
                        conflict(date, "No hay configuración laboral vigente.")
                    } else {
                        val configuration = runCatching { ResolvedWorkConfigurationRevision.resolve(history, date) }.getOrNull()
                        if (configuration == null) {
                            conflict(date, "No hay configuración laboral vigente para esta fecha.")
                        } else {
                            val zone = AppDefaults.zoneId()
                            val start = ZonedDateTime.of(date, input.start, zone).toInstant()
                            val endDate = if (input.end > input.start) date else date.plusDays(1)
                            val end = ZonedDateTime.of(endDate, input.end, zone).toInstant()
                            val overlapping = database.availabilityWindowDao().getOverlapping(
                                configuration.timelineId.toString(),
                                configuration.revision.value.sector.encodeSector(),
                                "",
                                start.toEpochMilli(),
                                end.toEpochMilli(),
                            )
                            val exact = overlapping.singleOrNull { stored ->
                                stored.ownerLocalDate == date.toString() &&
                                    stored.startEpochMillis == start.toEpochMilli() &&
                                    stored.endEpochMillis == end.toEpochMilli() &&
                                    stored.labelSnapshot == input.label.displayName
                            }
                            when {
                                exact != null && overlapping.size == 1 -> unchanged(date, "Ya existe la misma disponibilidad.")
                                overlapping.isNotEmpty() -> conflict(date, "Se superpone con otra disponibilidad.")
                                else -> applicable(date, "Se agregará ${input.label.displayName}.")
                            }
                        }
                    }
                }
            }
        }
        val tokenSource = buildString {
            append(calendarConfigurationToken(history))
            append('|').append(statuses.toSortedMap())
            append('|').append(shifts.toSortedMap())
            append('|').append(medicalLeaves.sortedBy { it.id })
            append('|').append(vacations.sortedBy { it.id })
            append('|').append(availabilityByDate.toSortedMap())
        }
        val expectationToken = MessageDigest.getInstance("SHA-256")
            .digest(tokenSource.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return CalendarBatchPreview(action, selected, availability, results, expectationToken)
    }

    private fun applicable(date: LocalDate, text: String) =
        CalendarBatchDateResult(date, CalendarBatchDateState.APPLICABLE, text)
    private fun unchanged(date: LocalDate, text: String) =
        CalendarBatchDateResult(date, CalendarBatchDateState.UNCHANGED, text)
    private fun conflict(date: LocalDate, text: String) =
        CalendarBatchDateResult(date, CalendarBatchDateState.CONFLICT, text)
}
