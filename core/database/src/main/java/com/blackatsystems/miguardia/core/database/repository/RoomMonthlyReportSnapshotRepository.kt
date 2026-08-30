package com.blackatsystems.miguardia.core.database.repository

import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaV2Database
import com.blackatsystems.miguardia.core.database.mapping.toDomain
import com.blackatsystems.miguardia.core.database.mapping.toDomainActualRecord
import com.blackatsystems.miguardia.core.database.mapping.toDomainAvailability
import com.blackatsystems.miguardia.core.database.mapping.toDomainExtraInterval
import com.blackatsystems.miguardia.core.database.mapping.toDomainIndependentExtra
import com.blackatsystems.miguardia.core.database.mapping.toDomainWorkSnapshot
import com.blackatsystems.miguardia.core.database.validation.readCatalog
import com.blackatsystems.miguardia.core.database.validation.requireValidV2LocalData
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.report.MonthlyReportSnapshotRepository
import com.blackatsystems.miguardia.core.domain.report.MonthlyReportSnapshotRequest
import com.blackatsystems.miguardia.core.domain.report.MonthlyReportSourceSnapshot
import com.blackatsystems.miguardia.core.domain.report.resolveReportCaptureRange
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryInput
import java.time.Instant
import java.time.YearMonth

internal class RoomMonthlyReportSnapshotRepository(
    private val database: MiGuardiaV2Database,
) : MonthlyReportSnapshotRepository {
    override suspend fun capture(request: MonthlyReportSnapshotRequest): MonthlyReportSourceSnapshot =
        database.withTransaction {
            val history = database.requireValidV2LocalData()
                ?: throw InvalidLocalDataException(
                    "Todavía no existe una configuración laboral para generar el informe.",
                )
            val range = resolveReportCaptureRange(request.month, history)
            val actualRows = database.shiftActualDao().getAllRecords()
            val intervalRows = database.shiftActualDao().getAllIntervals().groupBy { it.shiftId }
            val allActuals = actualRows.map { row ->
                ShiftActualAggregate(
                    record = row.toDomainActualRecord(),
                    extraIntervals = intervalRows[row.shiftId].orEmpty().map { it.toDomainExtraInterval() },
                )
            }
            val actualsByShiftId = allActuals.associateBy { it.record.shiftId }
            val allAvailability = database.availabilityWindowDao()
                .getAll()
                .map { it.toDomainAvailability() }
            val availability = allAvailability.filter { it.ownerLocalDate in range }
            val reportMonthAvailability = availability.filter {
                YearMonth.from(it.ownerLocalDate) == request.month
            }
            val reportMonthAvailabilityIntervals = reportMonthAvailability.map { it.start to it.end }
            val allShifts = database.v2ShiftDao().getAllShiftsWithSnapshots().map { row ->
                V2ShiftWrite(
                    shift = row.shift.toDomain(),
                    snapshot = row.snapshot.toDomainWorkSnapshot(),
                )
            }
            val shifts = allShifts.filter { write ->
                val actualOwnerDate = actualsByShiftId[write.shift.id]
                    ?.record
                    ?.actualStart
                    ?.atZone(write.shift.zoneId)
                    ?.toLocalDate()
                val actual = actualsByShiftId[write.shift.id]?.record
                write.shift.localStartDate in range ||
                    actualOwnerDate?.let { it in range } == true ||
                    overlapsAny(
                        start = actual?.actualStart ?: write.shift.startAt,
                        end = actual?.actualEnd ?: write.shift.endAt,
                        availability = reportMonthAvailabilityIntervals,
                    )
            }
            val includedShiftIds = shifts.mapTo(hashSetOf()) { it.shift.id }
            val actuals = allActuals.filter { it.record.shiftId in includedShiftIds }
            val extras = database.independentExtraWorkDao()
                .getAll()
                .map { it.toDomainIndependentExtra() }
                .filter { extra ->
                    extra.ownerLocalDate in range || overlapsAny(
                        start = extra.start,
                        end = extra.end,
                        availability = reportMonthAvailabilityIntervals,
                    )
                }
            val holidays = database.holidayDao().getAll().map { it.toDomain() }
            val medicalLeaves = database.medicalLeaveDao()
                .getAll()
                .map { it.toDomain() }
                .filter { it.startDate < range.endExclusive && !it.endDateInclusive.isBefore(range.startInclusive) }
                .map { leave ->
                    val intersectsReportMonth = leave.startDate < request.month.plusMonths(1).atDay(1) &&
                        !leave.endDateInclusive.isBefore(request.month.atDay(1))
                    if (request.includeMedicalNotes && intersectsReportMonth) {
                        leave
                    } else {
                        leave.copy(privateNote = null)
                    }
                }
            val vacations = database.vacationDao()
                .getAll()
                .map { it.toDomain() }
                .filter { it.startDate < range.endExclusive && !it.endDateInclusive.isBefore(range.startInclusive) }
            val explicitStatuses = database.explicitDayStatusDao()
                .getAll()
                .map { it.toDomain() }
                .filter { it.date in range }
            val catalogs = history.timeline.revisions
                .map { it.value.sector }
                .distinct()
                .map { sector -> database.readCatalog(history.timeline.id, sector) }
            val reportMonthShiftIds = shifts
                .filter { write ->
                    val ownerDate = actualsByShiftId[write.shift.id]
                        ?.record
                        ?.actualStart
                        ?.atZone(write.shift.zoneId)
                        ?.toLocalDate()
                        ?: write.shift.localStartDate
                    YearMonth.from(ownerDate) == request.month
                }
                .mapTo(hashSetOf()) { it.shift.id }
            val notes = if (request.includeShiftNotes) {
                database.shiftNoteDao().getAll()
                    .map { it.toDomain() }
                    .filter { it.shiftId in reportMonthShiftIds }
            } else {
                emptyList()
            }
            val monthPhotos = if (request.selectedPhotoIds.isEmpty()) {
                emptyList()
            } else {
                database.schedulePhotoDao().getForMonth(request.month.toString()).map { it.toDomain() }
            }
            val selectedPhotos = monthPhotos
                .filter { it.id in request.selectedPhotoIds }
                .sortedWith(compareBy({ it.createdAt }, { it.id }))
            if (selectedPhotos.map { it.id }.toSet() != request.selectedPhotoIds) {
                throw InvalidLocalDataException(
                    "Una foto elegida ya no está disponible. Actualizá la selección y reintentá.",
                )
            }
            MonthlyReportSourceSnapshot(
                request = request,
                captureRange = range,
                summaryInput = MonthlySummaryInput(
                    month = request.month,
                    configuration = history,
                    shifts = shifts,
                    actuals = actuals,
                    independentExtras = extras,
                    availabilityWindows = availability,
                    catalogs = catalogs,
                    holidays = holidays,
                    medicalLeaves = medicalLeaves,
                    vacations = vacations,
                    explicitDayStatuses = explicitStatuses,
                ),
                shiftNotes = notes,
                selectedPhotos = selectedPhotos,
            )
        }
}

private fun overlapsAny(
    start: Instant,
    end: Instant,
    availability: List<Pair<Instant, Instant>>,
): Boolean = availability.any { (availabilityStart, availabilityEnd) ->
    start < availabilityEnd && end > availabilityStart
}
