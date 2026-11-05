package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.model.*
import com.blackatsystems.miguardia.core.domain.repository.*
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.*

/** Test-owned finite negative sources; all normal observations/writes delegate to the same Room. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class Stage12AvailabilityFaults(store: LocalDataStore) {
    enum class Mode { NONE, WINDOW_OBSERVATION, WORK_CALCULATION_SOURCE }
    private val mode = MutableStateFlow(Mode.NONE)
    private val windowFailures = AtomicInteger()
    private val calculationFailures = AtomicInteger()
    private val writes = AtomicInteger()
    fun set(value: Mode) { check(mode.value != value); mode.value = value }
    val current: Mode get() = mode.value
    val writeCalls: Int get() = writes.get()
    val windows: AvailabilityWindowRepository = object : AvailabilityWindowRepository by store.availabilityWindows {
        override fun observeAll(timelineId: UUID, sector: WorkSector): Flow<List<AvailabilityWindowRecord>> =
            mode.flatMapLatest { selected ->
                if (selected == Mode.WINDOW_OBSERVATION) flow {
                    windowFailures.incrementAndGet(); error("AVAILABILITY_CONTROLLED_WINDOW_OBSERVATION")
                } else store.availabilityWindows.observeAll(timelineId, sector)
            }
        override suspend fun applyMutation(mutation: AvailabilityWindowMutation): AvailabilityWindowWriteResult {
            writes.incrementAndGet()
            return store.availabilityWindows.applyMutation(mutation)
        }
    }
    val shifts: V2ShiftRepository = object : V2ShiftRepository by store.v2Shifts {
        override fun observeAll(timelineId: UUID, sector: WorkSector): Flow<List<V2ShiftWrite>> =
            mode.flatMapLatest { selected ->
                if (selected == Mode.WORK_CALCULATION_SOURCE) flow {
                    calculationFailures.incrementAndGet(); error("AVAILABILITY_CONTROLLED_WORK_CALCULATION_SOURCE")
                } else store.v2Shifts.observeAll(timelineId, sector)
            }
    }
    fun projection(): Map<String, Any> = mapOf("mode" to current.name, "window_observation_failures" to windowFailures.get(),
        "work_calculation_source_failures" to calculationFailures.get(), "write_calls" to writeCalls)
}
