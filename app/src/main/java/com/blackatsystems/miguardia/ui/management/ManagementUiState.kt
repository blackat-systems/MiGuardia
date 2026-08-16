package com.blackatsystems.miguardia.ui.management

import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.RecentScheduleCombination
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.shift.OccupiedDatePolicy
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class ManagementSurface {
    NONE,
    SETTINGS,
    OBJECTIVE_FORM,
    SCHEDULE_FORM,
    SHIFT_FORM,
    DAY_OFF_FORM,
}

enum class ShiftEntryMode {
    SINGLE,
    MULTIPLE,
}

data class ScheduleOption(
    val objective: Objective,
    val combination: ScheduleCombination,
)

data class ObjectiveDraft(
    val editingId: UUID? = null,
    val fullName: String = "",
    val abbreviation: String = "",
    val address: String = "",
    val note: String = "",
)

data class ScheduleDraft(
    val editingId: UUID? = null,
    val objectiveId: UUID? = null,
    val startTime: String = "19:00",
    val endTime: String = "07:00",
    val colorArgb: Int = 0xFF336699.toInt(),
)

data class ShiftDraft(
    val mode: ShiftEntryMode = ShiftEntryMode.SINGLE,
    val month: YearMonth,
    val selectedDates: Set<LocalDate>,
    val combinationId: UUID? = null,
    val position: String = "",
    val editingShift: Shift? = null,
    val duplicateSource: Shift? = null,
    val pendingPolicy: OccupiedDatePolicy? = null,
    val occupiedDates: Set<LocalDate> = emptySet(),
    val warnings: List<String> = emptyList(),
    val coexistenceWarnings: List<String> = emptyList(),
)

data class DayOffDraft(
    val month: YearMonth,
    val selectedDates: Set<LocalDate>,
)

data class ManagementUiState(
    val surface: ManagementSurface = ManagementSurface.NONE,
    val formReturnSurface: ManagementSurface = ManagementSurface.NONE,
    val objectives: List<Objective> = emptyList(),
    val scheduleOptions: List<ScheduleOption> = emptyList(),
    val recent: List<RecentScheduleCombination> = emptyList(),
    val showHidden: Boolean = false,
    val objectiveDraft: ObjectiveDraft = ObjectiveDraft(),
    val scheduleDraft: ScheduleDraft = ScheduleDraft(),
    val shiftDraft: ShiftDraft? = null,
    val dayOffDraft: DayOffDraft? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)
