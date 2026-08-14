package com.blackatsystems.miguardia.ui.exceptions

import com.blackatsystems.miguardia.core.domain.model.FormalShiftChange
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftNovelty
import com.blackatsystems.miguardia.core.domain.model.ShiftNoveltyType
import com.blackatsystems.miguardia.core.domain.model.HolidayConflictPolicy
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class ExceptionsSurface { NONE, HOLIDAYS, SHIFT }

enum class ExceptionPlanningOperation { FORMAL_CHANGE, SECOND_SHIFT }

data class PendingExceptionPlanning(
    val operation: ExceptionPlanningOperation,
    val combinationId: UUID,
    val description: String,
)

data class ExceptionScheduleOption(
    val objective: Objective,
    val combination: ScheduleCombination,
)

data class HolidayDraft(
    val editingId: UUID? = null,
    val datesText: String = "",
    val name: String = "",
    val conflictDates: Set<LocalDate> = emptySet(),
    val pendingPolicy: HolidayConflictPolicy? = null,
)

data class NoteDraft(val editingId: UUID? = null, val body: String = "")

data class NoveltyDraft(
    val editingId: UUID? = null,
    val type: ShiftNoveltyType = ShiftNoveltyType.ADDITIONAL_TIME,
    val description: String = "",
)

data class ExceptionsUiState(
    val surface: ExceptionsSurface = ExceptionsSurface.NONE,
    val holidayMonth: YearMonth,
    val holidays: List<Holiday> = emptyList(),
    val holidayDraft: HolidayDraft = HolidayDraft(),
    val selectedShift: Shift? = null,
    val notes: List<ShiftNote> = emptyList(),
    val novelties: List<ShiftNovelty> = emptyList(),
    val formalChange: FormalShiftChange? = null,
    val scheduleOptions: List<ExceptionScheduleOption> = emptyList(),
    val noteDraft: NoteDraft = NoteDraft(),
    val noveltyDraft: NoveltyDraft = NoveltyDraft(),
    val planningWarnings: List<String> = emptyList(),
    val pendingPlanning: PendingExceptionPlanning? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)
