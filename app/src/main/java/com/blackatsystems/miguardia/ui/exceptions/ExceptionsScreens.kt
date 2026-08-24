package com.blackatsystems.miguardia.ui.exceptions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.HolidayConflictPolicy
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.ui.components.DestructiveAction
import com.blackatsystems.miguardia.ui.components.EmptyState
import com.blackatsystems.miguardia.ui.components.MonthNavigator
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.PrimaryAction
import com.blackatsystems.miguardia.ui.components.ScreenHeading
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.components.SelectableMonthCalendar
import com.blackatsystems.miguardia.ui.components.SurfaceHeader
import com.blackatsystems.miguardia.ui.components.TransientConfirmation
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

data class ExceptionsActions(
    val openHolidays: (YearMonth) -> Unit = {},
    val openNotes: (Shift) -> Unit = {},
    val close: () -> Unit = {},
    val previousHolidayMonth: () -> Unit = {},
    val nextHolidayMonth: () -> Unit = {},
    val updateHolidayDraft: ((HolidayDraft) -> HolidayDraft) -> Unit = {},
    val editHoliday: (Holiday) -> Unit = {},
    val cancelHolidayEdit: () -> Unit = {},
    val saveHolidays: (HolidayConflictPolicy?) -> Unit = {},
    val cancelHolidayConflict: () -> Unit = {},
    val deleteHoliday: (UUID) -> Unit = {},
    val updateNoteDraft: ((NoteDraft) -> NoteDraft) -> Unit = {},
    val editNote: (ShiftNote) -> Unit = {},
    val cancelNoteEdit: () -> Unit = {},
    val saveNote: () -> Unit = {},
    val deleteNote: (UUID) -> Unit = {},
    val retry: () -> Unit = {},
    val clearMessage: () -> Unit = {},
) {
    companion object {
        fun from(viewModel: ExceptionsViewModel) = ExceptionsActions(
            openHolidays = viewModel::openHolidays,
            openNotes = viewModel::openNotes,
            close = viewModel::close,
            previousHolidayMonth = viewModel::showPreviousHolidayMonth,
            nextHolidayMonth = viewModel::showNextHolidayMonth,
            updateHolidayDraft = viewModel::updateHolidayDraft,
            editHoliday = viewModel::editHoliday,
            cancelHolidayEdit = viewModel::cancelHolidayEdit,
            saveHolidays = viewModel::saveHolidays,
            cancelHolidayConflict = viewModel::cancelHolidayConflict,
            deleteHoliday = viewModel::deleteHoliday,
            updateNoteDraft = viewModel::updateNoteDraft,
            editNote = viewModel::editNote,
            cancelNoteEdit = viewModel::cancelNoteEdit,
            saveNote = viewModel::saveNote,
            deleteNote = viewModel::deleteNote,
            retry = viewModel::retry,
            clearMessage = viewModel::clearMessage,
        )
    }
}

@Composable
fun ExceptionsSurfaceHost(
    state: ExceptionsUiState,
    actions: ExceptionsActions,
) {
    if (state.surface == ExceptionsSurface.NONE) return
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val hasUnsavedChanges = when (state.surface) {
        ExceptionsSurface.HOLIDAYS -> state.holidayDraft.editingId != null ||
            state.holidayDraft.datesText.isNotBlank() ||
            state.holidayDraft.name.isNotBlank()
        ExceptionsSurface.NOTES -> state.noteDraft.editingId != null || state.noteDraft.body.isNotBlank()
        ExceptionsSurface.NONE -> false
    }
    val requestClose = {
        if (!state.isSaving) {
            if (hasUnsavedChanges) confirmDiscard = true else actions.close()
        }
    }
    BackHandler(onBack = requestClose)
    TransientConfirmation(state.infoMessage, actions.clearMessage) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                SurfaceHeader(
                    title = if (state.surface == ExceptionsSurface.HOLIDAYS) "Feriados" else "Notas",
                    navigationLabel = "Cerrar",
                    onNavigation = requestClose,
                )
                HorizontalDivider()
                state.errorMessage?.let {
                    PersistentMessage(
                        message = it,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        onDismiss = actions.clearMessage,
                        onRetry = actions.retry,
                    )
                }
                when (state.surface) {
                    ExceptionsSurface.HOLIDAYS -> HolidayContent(state, actions)
                    ExceptionsSurface.NOTES -> NotesContent(state, actions)
                    ExceptionsSurface.NONE -> Unit
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Descartar cambios") },
            text = {
                Text(
                    if (state.surface == ExceptionsSurface.HOLIDAYS) {
                        "Hay datos del feriado sin guardar."
                    } else {
                        "Hay una nota sin guardar."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        actions.close()
                    },
                ) { Text("Descartar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Seguir editando") }
            },
        )
    }
}

@Composable
private fun HolidayContent(state: ExceptionsUiState, actions: ExceptionsActions) {
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDates = state.holidayDraft.datesText.toSelectedDates()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthNavigator(
            monthLabel = state.holidayMonth.label(),
            previousDescription = "Mes anterior de feriados",
            nextDescription = "Mes siguiente de feriados",
            onPrevious = actions.previousHolidayMonth,
            onNext = actions.nextHolidayMonth,
        )
        SectionCard(
            title = if (state.holidayDraft.editingId == null) "Elegí los feriados" else "Cambiá la fecha",
            supportingText = if (state.holidayDraft.editingId == null) {
                "Tocá una o varias fechas del calendario."
            } else {
                "Al editar, solo puede quedar una fecha seleccionada."
            },
        ) {
            SelectableMonthCalendar(
                month = state.holidayMonth,
                selectedDates = selectedDates,
                onToggleDate = { date ->
                    val updatedDates = if (state.holidayDraft.editingId != null) {
                        if (date in selectedDates) emptySet() else setOf(date)
                    } else if (date in selectedDates) {
                        selectedDates - date
                    } else {
                        selectedDates + date
                    }
                    actions.updateHolidayDraft {
                        it.copy(datesText = updatedDates.sorted().joinToString(","))
                    }
                },
                monthLabel = state.holidayMonth.label(),
                testTag = "holiday-date-selector",
            )
            Text(
                text = when (selectedDates.size) {
                    0 -> "Todavía no elegiste ninguna fecha."
                    1 -> "1 fecha seleccionada."
                    else -> "${selectedDates.size} fechas seleccionadas."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = state.holidayDraft.name,
            onValueChange = { value -> actions.updateHolidayDraft { it.copy(name = value) } },
            label = { Text("Nombre opcional") },
            supportingText = { Text("Por ejemplo: Día de la Bandera") },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryAction(
            label = if (state.holidayDraft.editingId == null) "Guardar feriado(s)" else "Guardar cambios",
            onClick = { actions.saveHolidays(null) },
            enabled = !state.isSaving,
            working = state.isSaving,
        )
        if (state.holidayDraft.editingId != null) {
            OutlinedButton(
                onClick = actions.cancelHolidayEdit,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cancelar edición") }
        }
        Text("Feriados del mes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (state.holidays.isEmpty()) {
            EmptyState(
                title = "Sin feriados",
                message = "No hay feriados manuales cargados en este mes.",
            )
        } else {
            state.holidays.forEach { holiday ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(holiday.date.holidayDisplayName(), fontWeight = FontWeight.Bold)
                            Text(holiday.name ?: "Feriado")
                        }
                        TextButton(
                            onClick = { actions.editHoliday(holiday) },
                            enabled = !state.isSaving,
                        ) { Text("Editar") }
                        DestructiveAction(
                            label = "Eliminar",
                            onClick = { pendingDelete = holiday.id.toString() },
                            enabled = !state.isSaving,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (state.holidayDraft.conflictDates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = actions.cancelHolidayConflict,
            title = { Text("Fechas con feriado") },
            text = {
                Text(
                    "Ya existen: ${state.holidayDraft.conflictDates.sorted().joinToString { it.numericDisplayName() }}. " +
                        "Podés reemplazarlas o conservarlas y crear solo las nuevas.",
                )
            },
            confirmButton = {
                TextButton(onClick = { actions.saveHolidays(HolidayConflictPolicy.REPLACE) }) {
                    Text("Reemplazar")
                }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { actions.saveHolidays(HolidayConflictPolicy.KEEP_EXISTING) }) {
                        Text("Conservar existentes")
                    }
                    TextButton(onClick = actions.cancelHolidayConflict) { Text("Cancelar") }
                }
            },
        )
    }
    pendingDelete?.let { raw ->
        ConfirmDeleteDialog(
            title = "Eliminar feriado",
            text = "El calendario se actualizará automáticamente.",
            onConfirm = {
                actions.deleteHoliday(UUID.fromString(raw))
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun NotesContent(state: ExceptionsUiState, actions: ExceptionsActions) {
    val shift = state.selectedShift ?: return
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeading(
            title = "Notas privadas",
            supportingText = "${shift.objectiveNameSnapshot} · ${shift.startTimeSnapshot}–${shift.endTimeSnapshot}",
        )
        SectionCard(
            title = if (state.noteDraft.editingId == null) "Agregar nota" else "Editar nota",
            supportingText = "La nota queda guardada solamente en este dispositivo.",
        ) {
            OutlinedTextField(
                value = state.noteDraft.body,
                onValueChange = { value -> actions.updateNoteDraft { it.copy(body = value) } },
                label = { Text("Nota") },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = actions.saveNote,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar nota") }
            if (state.noteDraft.editingId != null) {
                OutlinedButton(
                    onClick = actions.cancelNoteEdit,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cancelar edición") }
            }
        }
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (state.notes.isEmpty()) {
            Text("Todavía no hay notas para esta jornada.")
        } else {
            state.notes.forEach { note ->
                SectionCard(title = "Nota", supportingText = note.body) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { actions.editNote(note) },
                            enabled = !state.isSaving,
                            modifier = Modifier.weight(1f),
                        ) { Text("Editar") }
                        DestructiveAction(
                            label = "Eliminar",
                            onClick = { pendingDelete = note.id.toString() },
                            enabled = !state.isSaving,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
    pendingDelete?.let { raw ->
        ConfirmDeleteDialog(
            title = "Eliminar nota",
            text = "La nota privada se eliminará.",
            onConfirm = {
                actions.deleteNote(UUID.fromString(raw))
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun YearMonth.label(): String {
    val locale = Locale.forLanguageTag("es-AR")
    val monthName = month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
    return "$monthName de $year"
}

private fun String.toSelectedDates(): Set<LocalDate> =
    split(',', ';', '\n')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .toCollection(linkedSetOf())

private fun LocalDate.holidayDisplayName(): String {
    val locale = Locale.forLanguageTag("es-AR")
    return format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale))
        .replaceFirstChar { it.titlecase(locale) }
}

private fun LocalDate.numericDisplayName(): String =
    format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))
