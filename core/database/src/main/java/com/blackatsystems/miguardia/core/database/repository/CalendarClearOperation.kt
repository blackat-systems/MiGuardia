package com.blackatsystems.miguardia.core.database.repository

import com.blackatsystems.miguardia.core.database.MiGuardiaV2Database
import com.blackatsystems.miguardia.core.database.entity.ExplicitDayStatusEntity
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAction
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchDateResult
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchDateState
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchPreview
import com.blackatsystems.miguardia.core.database.validation.requireValidV2LocalData
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Called only inside the preview/apply transaction owned by RoomCalendarBatchRepository. */
internal class CalendarClearOperation(
    private val database: MiGuardiaV2Database,
    private val clock: Clock,
) {
    suspend fun preview(dates: Set<LocalDate>): CalendarBatchPreview {
        val selected = dates.toSortedSet()
        val history = database.requireValidV2LocalData()
        val shifts = database.v2ShiftDao().getAllShiftsWithSnapshots()
            .filter { LocalDate.parse(it.shift.localStartDate) in selected }
        val ids = shifts.map { it.shift.id }.toSet()
        val notes = database.shiftNoteDao().getAll().filter { it.shiftId in ids }
        val attachments = notes.map { it.id }.chunked(900)
            .flatMap { database.shiftNoteAttachmentDao().getForNotes(it) }
            .sortedWith(compareBy({ it.noteId }, { it.position }, { it.id }))
        val situations = database.shiftSituationDao().getAll().filter { it.shiftId in ids }
        val actuals = ids.toList().chunked(900)
            .flatMap { database.shiftActualDao().getRecords(it) }.sortedBy { it.shiftId }
        val extras = database.shiftActualDao().getAllIntervals().filter { it.shiftId in ids }
        val notifications = ids.toList().chunked(900)
            .flatMap { database.recurringPlanDao().getNotificationConfigs(it) }.sortedBy { it.shiftId }
        val reminders = ids.toList().chunked(900)
            .flatMap { database.recurringPlanDao().getNotificationReminders(it) }
            .sortedWith(compareBy({ it.shiftId }, { it.leadMinutes }))
        val occurrences = database.recurringPlanDao().getAllOccurrences().filter { it.shiftId in ids }
        val statuses = database.explicitDayStatusDao().getAll().filter { LocalDate.parse(it.localDate) in selected }
        val medical = database.medicalLeaveDao().getAll().filter { intersects(it.startDate, it.endDateInclusive, selected) }
        val vacations = database.vacationDao().getAll().filter { intersects(it.startDate, it.endDateInclusive, selected) }
        val availability = database.availabilityWindowDao().getAll().filter { LocalDate.parse(it.ownerLocalDate) in selected }
        val independent = database.independentExtraWorkDao().getAll().filter { LocalDate.parse(it.ownerLocalDate) in selected }
        val holidays = database.holidayDao().getAll().filter { LocalDate.parse(it.localDate) in selected }
        val token = listOf(calendarConfigurationToken(history), shifts, notes, attachments, situations, actuals, extras, notifications,
            reminders, occurrences, statuses, medical, vacations, availability, independent, holidays)
            .joinToString("|")
        val results = selected.map { date ->
            val owned = shifts.filter { it.shift.localStartDate == date.toString() }
            val ownedIds = owned.map { it.shift.id }.toSet()
            val ownedNotes = notes.filter { it.shiftId in ownedIds }.map { it.id }.toSet()
            val losses = buildList {
                owned.forEach { pair ->
                    val shift = pair.shift
                    val zone = ZoneId.of(shift.zoneId)
                    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    add("Jornada ${shift.objectiveNameSnapshot}: ${formatter.format(Instant.ofEpochMilli(shift.startEpochMillis).atZone(zone))} – ${formatter.format(Instant.ofEpochMilli(shift.endEpochMillis).atZone(zone))}")
                }
                if (ownedNotes.isNotEmpty()) add("${ownedNotes.size} nota(s) y ${attachments.count { it.noteId in ownedNotes }} imagen(es) privadas de esas jornadas")
                if (actuals.any { it.shiftId in ownedIds }) add("${actuals.count { it.shiftId in ownedIds }} horario(s) real(es) y ${extras.count { it.shiftId in ownedIds }} tramo(s) extra asociado(s)")
                if (situations.any { it.shiftId in ownedIds }) add("${situations.count { it.shiftId in ownedIds }} situación(es), sus motivos, notas y evidencia privada")
                if (notifications.any { it.shiftId in ownedIds }) add("Avisos particulares de las jornadas eliminadas")
                if (occurrences.any { it.shiftId in ownedIds }) add("Ocurrencias recurrentes: quedan excluidas para que no reaparezcan")
                availability.filter { it.ownerLocalDate == date.toString() }.forEach {
                    add("${it.labelSnapshot}: ${interval(it.startEpochMillis, it.endEpochMillis, it.zoneId)}")
                }
                independent.filter { it.ownerLocalDate == date.toString() }.forEach {
                    add("Trabajo extra ${it.classNameSnapshot}, ${it.workPlaceNameSnapshot}: ${interval(it.startEpochMillis, it.endEpochMillis, it.zoneId)}")
                }
                medical.filter { covers(it.startDate, it.endDateInclusive, date) }.forEach {
                    val survivors = remainingCalendarRanges(it.startDate, it.endDateInclusive, selected)
                    add("Carpeta médica ${it.startDate} a ${it.endDateInclusive}: " +
                        if (survivors.isEmpty()) "se elimina completa, incluida su nota privada" else
                            "se quita esta fecha; se conservan los tramos exteriores ${survivors.joinToString { range -> "${range.first} a ${range.second}" }} y su nota privada")
                }
                vacations.filter { covers(it.startDate, it.endDateInclusive, date) }.forEach {
                    add("Vacaciones ${it.startDate} a ${it.endDateInclusive}: se quita esta fecha; se conservan los tramos exteriores")
                }
                if (statuses.any { it.localDate == date.toString() && it.type != "UNDEFINED" }) add("Marca explícita de Franco")
            }
            val alreadyUndefined = statuses.any { it.localDate == date.toString() && it.type == "UNDEFINED" }
            CalendarBatchDateResult(date,
                if (losses.isEmpty() && alreadyUndefined) CalendarBatchDateState.UNCHANGED else CalendarBatchDateState.APPLICABLE,
                buildString {
                    append(if (losses.isEmpty()) "Sin registros laborales para quitar." else "Se quitará o ajustará:\n${losses.joinToString("\n")}")
                    append("\nEl día quedará Sin definir. Se conservan el catálogo y las jornadas iniciadas en otras fechas, aunque crucen medianoche.")
                    if (holidays.any { it.localDate == date.toString() }) append(" El feriado se conserva como información del calendario.")
                })
        }
        return CalendarBatchPreview(CalendarBatchAction.CLEAR, selected, results = results,
            expectationToken = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) },
            removedNoteAttachmentStorageKeys = attachments.map { it.storageKey }.toSet(),
            removedSituationEvidenceStorageKeys = situations.mapNotNull { it.evidenceStorageKey }.toSet())
    }

    suspend fun apply(dates: Set<LocalDate>) {
        val selected = dates.toSortedSet()
        val now = clock.millis()
        val shifts = database.v2ShiftDao().getAllShiftsWithSnapshots().filter { LocalDate.parse(it.shift.localStartDate) in selected }
        shifts.forEach { pair ->
            val id = pair.shift.id
            database.recurringPlanDao().getOccurrenceForShift(id)?.let { occurrence ->
                check(database.recurringPlanDao().updateOccurrence(occurrence.copy(shiftId = null, state = "EXCLUDED",
                    updatedAtEpochMillis = maxOf(now, occurrence.updatedAtEpochMillis + 1))) == 1)
            }
            database.shiftActualDao().deleteIntervals(id)
            database.shiftActualDao().deleteRecord(id)
            check(database.v2ShiftDao().deleteShiftAndOwnedSnapshot(id) == 1)
        }
        database.availabilityWindowDao().getAll().filter { LocalDate.parse(it.ownerLocalDate) in selected }
            .forEach { check(database.availabilityWindowDao().delete(it.id) == 1) }
        database.independentExtraWorkDao().getAll().filter { LocalDate.parse(it.ownerLocalDate) in selected }
            .forEach { check(database.independentExtraWorkDao().delete(it.id) == 1) }
        database.medicalLeaveDao().getAll().filter { intersects(it.startDate, it.endDateInclusive, selected) }.forEach { original ->
            val ranges = remainingCalendarRanges(original.startDate, original.endDateInclusive, selected)
            check(database.medicalLeaveDao().delete(original.id) == 1)
            ranges.forEachIndexed { index, range -> database.medicalLeaveDao().insert(original.copy(
                id = if (index == 0) original.id else splitId(original.id, range), startDate = range.first.toString(),
                endDateInclusive = range.second.toString(), updatedAtEpochMillis = maxOf(now, original.updatedAtEpochMillis + 1))) }
        }
        database.vacationDao().getAll().filter { intersects(it.startDate, it.endDateInclusive, selected) }.forEach { original ->
            val ranges = remainingCalendarRanges(original.startDate, original.endDateInclusive, selected)
            check(database.vacationDao().delete(original.id) == 1)
            ranges.forEachIndexed { index, range -> database.vacationDao().insert(original.copy(
                id = if (index == 0) original.id else splitId(original.id, range), startDate = range.first.toString(),
                endDateInclusive = range.second.toString(), updatedAtEpochMillis = maxOf(now, original.updatedAtEpochMillis + 1))) }
        }
        selected.forEach { database.explicitDayStatusDao().set(ExplicitDayStatusEntity(it.toString(), "UNDEFINED")) }
    }

    private fun splitId(id: String, range: Pair<LocalDate, LocalDate>) =
        UUID.nameUUIDFromBytes("calendar-clear:$id:${range.first}:${range.second}".toByteArray(Charsets.UTF_8)).toString()
    private fun covers(start: String, end: String, date: LocalDate) = date.toString() >= start && date.toString() <= end
    private fun intersects(start: String, end: String, dates: Set<LocalDate>) = dates.any { covers(start, end, it) }
    private fun interval(start: Long, end: Long, zoneId: String): String {
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        val zone = ZoneId.of(zoneId)
        return "${formatter.format(Instant.ofEpochMilli(start).atZone(zone))} – ${formatter.format(Instant.ofEpochMilli(end).atZone(zone))}"
    }
}

/** Subtract exact selected dates without iterating the potentially long unselected range. */
internal fun remainingCalendarRanges(start: String, end: String, removed: Set<LocalDate>): List<Pair<LocalDate, LocalDate>> {
    var cursor = LocalDate.parse(start)
    val last = LocalDate.parse(end)
    val result = mutableListOf<Pair<LocalDate, LocalDate>>()
    removed.filter { it >= cursor && it <= last }.sorted().forEach { date ->
        if (cursor < date) result += cursor to date.minusDays(1)
        cursor = date.plusDays(1)
    }
    if (cursor <= last) result += cursor to last
    return result
}
