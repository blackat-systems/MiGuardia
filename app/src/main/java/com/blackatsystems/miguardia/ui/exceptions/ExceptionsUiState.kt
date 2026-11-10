package com.blackatsystems.miguardia.ui.exceptions

import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.HolidayConflictPolicy
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteAttachment
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteWithAttachments
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class ExceptionsSurface {
    NONE,
    HOLIDAYS,
    NOTES,
}

data class HolidayDraft(
    val editingId: UUID? = null,
    val datesText: String = "",
    val name: String = "",
    val conflictDates: Set<LocalDate> = emptySet(),
    val pendingPolicy: HolidayConflictPolicy? = null,
) {
    val selectedDates: Set<LocalDate>
        get() = datesText.split(',', ';', '\n')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
            .toCollection(linkedSetOf())
}

data class NoteDraft(
    val editingId: UUID? = null,
    val body: String = "",
    val attachments: List<ShiftNoteAttachment> = emptyList(),
    val originalAttachmentIds: Set<UUID> = emptySet(),
    val pendingId: UUID? = null,
)

data class ExceptionsUiState(
    val surface: ExceptionsSurface = ExceptionsSurface.NONE,
    val holidayMonth: YearMonth = YearMonth.now(),
    val holidaySelectionActive: Boolean = false,
    val holidays: List<Holiday> = emptyList(),
    val selectedShift: Shift? = null,
    val notes: List<ShiftNote> = emptyList(),
    val noteItems: List<ShiftNoteWithAttachments> = emptyList(),
    val holidayDraft: HolidayDraft = HolidayDraft(),
    val noteDraft: NoteDraft = NoteDraft(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)
