package com.blackatsystems.miguardia.ui.nextevent

import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventInput
import com.blackatsystems.miguardia.core.domain.repository.AvailabilityWindowRepository
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.IndependentExtraWorkRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftActualRepository
import com.blackatsystems.miguardia.core.domain.repository.V2ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

data class NextEventSourceData(
    val shifts: List<V2ShiftWrite>,
    val availabilityWindows: List<AvailabilityWindowRecord>,
    val actualsByShiftId: Map<UUID, ShiftActualAggregate>,
    val independentExtras: List<IndependentExtraWorkRecord>,
    val explicitDayStatuses: List<ExplicitDayStatus>,
    val vacations: List<Vacation>,
    val medicalLeaves: List<MedicalLeave>,
) {
    fun toInput(): NextEventInput = NextEventInput(
        shifts = shifts,
        availabilityWindows = availabilityWindows,
        actualsByShiftId = actualsByShiftId,
        independentExtras = independentExtras,
        explicitDayStatuses = explicitDayStatuses,
        vacations = vacations,
        medicalLeaves = medicalLeaves,
    )
}

private data class SectorEventSources(
    val sector: WorkSector,
    val shifts: List<V2ShiftWrite>,
    val availabilityWindows: List<AvailabilityWindowRecord>,
    val actualsByShiftId: Map<UUID, ShiftActualAggregate>,
    val independentExtras: List<IndependentExtraWorkRecord>,
)

/** One reactive read graph shared by the card and notification runtime. */
class V2WorkEventSourceObserver(
    private val shifts: V2ShiftRepository,
    private val availabilityWindows: AvailabilityWindowRepository,
    private val shiftActuals: ShiftActualRepository,
    private val independentExtras: IndependentExtraWorkRepository,
    private val explicitDayStatuses: ExplicitDayStatusRepository,
    private val vacations: VacationRepository,
    private val medicalLeaves: MedicalLeaveRepository,
    private val workConfiguration: WorkConfigurationRepository,
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observe(referenceDate: LocalDate): Flow<NextEventSourceData> =
        workConfiguration.observe().flatMapLatest { history ->
            observeSectorSources(history).flatMapLatest { sectorSources ->
                val allShifts = sectorSources.flatMap(SectorEventSources::shifts)
                val allAvailability = sectorSources.flatMap(SectorEventSources::availabilityWindows)
                val allExtras = sectorSources.flatMap(SectorEventSources::independentExtras)
                val actuals = buildMap {
                    sectorSources.forEach { source ->
                        source.actualsByShiftId.forEach { (id, aggregate) ->
                            val previous = put(id, aggregate)
                            require(previous == null || previous == aggregate) {
                                "El horario real $id aparece en dos sectores incompatibles"
                            }
                        }
                    }
                }
                val sourceDates = buildList {
                    allShifts.forEach { add(it.shift.localStartDate) }
                    allAvailability.forEach { add(it.ownerLocalDate) }
                    allExtras.forEach { add(it.ownerLocalDate) }
                }
                val firstDate = minOf(referenceDate.minusDays(1), sourceDates.minOrNull() ?: referenceDate)
                val lastDate = maxOf(referenceDate, sourceDates.maxOrNull() ?: referenceDate)
                combine(
                    explicitDayStatuses.observeFrom(referenceDate),
                    vacations.observeEndingOnOrAfter(firstDate),
                    medicalLeaves.observeIntersecting(firstDate, lastDate),
                ) { statuses, vacationValues, medicalValues ->
                    NextEventSourceData(
                        shifts = allShifts,
                        availabilityWindows = allAvailability,
                        actualsByShiftId = actuals,
                        independentExtras = allExtras,
                        explicitDayStatuses = statuses,
                        vacations = vacationValues,
                        medicalLeaves = medicalValues,
                    )
                }
            }
        }

    private fun observeSectorSources(
        history: WorkConfigurationHistory?,
    ): Flow<List<SectorEventSources>> {
        if (history == null) return flowOf(emptyList())
        val timelineId = history.timeline.id
        val sectors = history.timeline.revisions
            .map { revision -> revision.value.sector }
            .distinct()
        val sectorFlows = sectors.map { sector ->
            val shiftsAndActuals = combine(
                shifts.observeAll(timelineId, sector),
                shiftActuals.observeAllActuals(timelineId, sector),
            ) { shiftValues, actualValues -> shiftValues to actualValues }
            val availabilityAndExtras = combine(
                availabilityWindows.observeAll(timelineId, sector),
                independentExtras.observeAll(timelineId, sector),
            ) { availabilityValues, extraValues -> availabilityValues to extraValues }
            combine(shiftsAndActuals, availabilityAndExtras) { work, secondary ->
                SectorEventSources(
                    sector = sector,
                    shifts = work.first,
                    actualsByShiftId = work.second,
                    availabilityWindows = secondary.first,
                    independentExtras = secondary.second,
                )
            }
        }
        return if (sectorFlows.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(sectorFlows) { values -> values.toList() }
        }
    }
}
