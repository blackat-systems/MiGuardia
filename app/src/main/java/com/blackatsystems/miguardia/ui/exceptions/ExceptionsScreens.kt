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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.HolidayConflictPolicy
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNovelty
import com.blackatsystems.miguardia.core.domain.model.ShiftNoveltyType
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

data class ExceptionsActions(
    val close: () -> Unit = {},
    val openHolidays: (YearMonth) -> Unit = {},
    val openShift: (Shift) -> Unit = {},
    val previousHolidayMonth: () -> Unit = {},
    val nextHolidayMonth: () -> Unit = {},
    val updateHolidayDraft: ((HolidayDraft) -> HolidayDraft) -> Unit = {},
    val editHoliday: (Holiday) -> Unit = {},
    val saveHolidays: (HolidayConflictPolicy?) -> Unit = {},
    val cancelHolidayConflict: () -> Unit = {},
    val deleteHoliday: (UUID) -> Unit = {},
    val updateNoteDraft: ((NoteDraft) -> NoteDraft) -> Unit = {},
    val editNote: (ShiftNote) -> Unit = {},
    val saveNote: () -> Unit = {},
    val deleteNote: (UUID) -> Unit = {},
    val updateNoveltyDraft: ((NoveltyDraft) -> NoveltyDraft) -> Unit = {},
    val editNovelty: (ShiftNovelty) -> Unit = {},
    val saveInformative: () -> Unit = {},
    val deleteInformative: (UUID) -> Unit = {},
    val changeStatus: (ShiftStatus, String) -> Unit = { _, _ -> },
    val applyFormalChange: (UUID, String) -> Unit = { _, _ -> },
    val restoreOriginal: () -> Unit = {},
    val createSecondShift: (UUID, String) -> Unit = { _, _ -> },
    val deleteSecondShift: (ShiftNovelty) -> Unit = {},
    val confirmPlanningWarnings: () -> Unit = {},
    val dismissPlanningWarnings: () -> Unit = {},
    val clearMessage: () -> Unit = {},
    val retry: () -> Unit = {},
) {
    companion object {
        fun from(vm: ExceptionsViewModel) = ExceptionsActions(
            close = vm::close,
            openHolidays = vm::openHolidays,
            openShift = vm::openShift,
            previousHolidayMonth = vm::previousHolidayMonth,
            nextHolidayMonth = vm::nextHolidayMonth,
            updateHolidayDraft = vm::updateHolidayDraft,
            editHoliday = vm::editHoliday,
            saveHolidays = vm::saveHolidays,
            cancelHolidayConflict = vm::cancelHolidayConflict,
            deleteHoliday = vm::deleteHoliday,
            updateNoteDraft = vm::updateNoteDraft,
            editNote = vm::editNote,
            saveNote = vm::saveNote,
            deleteNote = vm::deleteNote,
            updateNoveltyDraft = vm::updateNoveltyDraft,
            editNovelty = vm::editNovelty,
            saveInformative = vm::saveInformativeNovelty,
            deleteInformative = vm::deleteInformativeNovelty,
            changeStatus = vm::changeStatus,
            applyFormalChange = vm::applyFormalChange,
            restoreOriginal = vm::restoreOriginalPlan,
            createSecondShift = vm::createSecondShift,
            deleteSecondShift = vm::deleteSecondShift,
            confirmPlanningWarnings = vm::confirmPlanningWarnings,
            dismissPlanningWarnings = vm::dismissPlanningWarnings,
            clearMessage = vm::clearMessage,
            retry = vm::retry,
        )
    }
}

@Composable
fun ExceptionsSurfaceHost(state: ExceptionsUiState, actions: ExceptionsActions) {
    BackHandler(onBack = actions.close)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (state.surface == ExceptionsSurface.HOLIDAYS) "Feriados" else "Notas y novedades",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = actions.close) { Text("Cerrar") }
            }
            HorizontalDivider()
            state.errorMessage?.let { Message(it, true, actions.clearMessage) }
            if (state.errorMessage != null) {
                Button(onClick = actions.retry, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Text("Reintentar")
                }
            }
            state.infoMessage?.let { Message(it, false, actions.clearMessage) }
            when (state.surface) {
                ExceptionsSurface.NONE -> Unit
                ExceptionsSurface.HOLIDAYS -> HolidayScreen(state, actions)
                ExceptionsSurface.SHIFT -> ShiftExceptionsScreen(state, actions)
            }
        }
    }
}

@Composable
private fun Message(text: String, error: Boolean, dismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text, Modifier.weight(1f), color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = dismiss) { Text("Cerrar") }
        }
    }
}

@Composable
private fun HolidayScreen(state: ExceptionsUiState, actions: ExceptionsActions) {
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val hasUnsavedChanges = state.holidayDraft.editingId != null ||
        state.holidayDraft.datesText.isNotBlank() || state.holidayDraft.name.isNotBlank()
    BackHandler {
        if (hasUnsavedChanges) confirmDiscard = true else actions.close()
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = actions.previousHolidayMonth) { Text("‹") }
            Text(state.holidayMonth.label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = actions.nextHolidayMonth) { Text("›") }
        }
        Text("Carga manual. Para varias fechas usá AAAA-MM-DD separadas por coma.")
        OutlinedTextField(
            value = state.holidayDraft.datesText,
            onValueChange = { value -> actions.updateHolidayDraft { it.copy(datesText = value) } },
            label = { Text("Fecha o fechas") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.holidayDraft.name,
            onValueChange = { value -> actions.updateHolidayDraft { it.copy(name = value) } },
            label = { Text("Nombre opcional") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { actions.saveHolidays(null) }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
            if (state.isSaving) CircularProgressIndicator()
            Text(if (state.holidayDraft.editingId == null) "Guardar feriado(s)" else "Guardar cambios")
        }
        Text("Feriados del mes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (state.isLoading) CircularProgressIndicator()
        if (!state.isLoading && state.holidays.isEmpty()) Text("No hay feriados cargados en este mes.")
        state.holidays.forEach { holiday ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(holiday.date.toString(), fontWeight = FontWeight.Bold)
                        Text(holiday.name ?: "Feriado")
                    }
                    TextButton(onClick = { actions.editHoliday(holiday) }) { Text("Editar") }
                    TextButton(onClick = { pendingDelete = holiday.id.toString() }) { Text("Eliminar") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (state.holidayDraft.conflictDates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = actions.cancelHolidayConflict,
            title = { Text("Fechas con feriado") },
            text = { Text("Ya existen: ${state.holidayDraft.conflictDates.joinToString()}. Podés reemplazarlas o conservarlas y crear solo las nuevas.") },
            confirmButton = { TextButton(onClick = { actions.saveHolidays(HolidayConflictPolicy.REPLACE) }) { Text("Reemplazar") } },
            dismissButton = {
                Column {
                    TextButton(onClick = { actions.saveHolidays(HolidayConflictPolicy.KEEP_EXISTING) }) { Text("Conservar existentes") }
                    TextButton(onClick = actions.cancelHolidayConflict) { Text("Cancelar") }
                }
            },
        )
    }
    pendingDelete?.let { raw ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar feriado") },
            text = { Text("El calendario y el Resumen se recalcularán automáticamente.") },
            confirmButton = { TextButton(onClick = { actions.deleteHoliday(UUID.fromString(raw)); pendingDelete = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Descartar cambios") },
            text = { Text("Hay datos del feriado sin guardar.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; actions.close() }) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Seguir editando") } },
        )
    }
}

@Composable
private fun ShiftExceptionsScreen(state: ExceptionsUiState, actions: ExceptionsActions) {
    val shift = state.selectedShift ?: return
    var statusDescription by rememberSaveable { mutableStateOf("") }
    var selectedFormalId by rememberSaveable { mutableStateOf<String?>(null) }
    var formalDescription by rememberSaveable { mutableStateOf("") }
    var selectedSecondId by rememberSaveable { mutableStateOf<String?>(null) }
    var secondDescription by rememberSaveable { mutableStateOf("") }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteNote by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteInformative by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteSecond by rememberSaveable { mutableStateOf<String?>(null) }
    val informativeTypes = listOf(ShiftNoveltyType.ADDITIONAL_TIME, ShiftNoveltyType.EARLY_DEPARTURE, ShiftNoveltyType.OTHER)
    val hasUnsavedChanges = statusDescription.isNotBlank() || selectedFormalId != null ||
        formalDescription.isNotBlank() || selectedSecondId != null || secondDescription.isNotBlank() ||
        state.noteDraft.editingId != null || state.noteDraft.body.isNotBlank() ||
        state.noveltyDraft.editingId != null || state.noveltyDraft.description.isNotBlank()
    LaunchedEffect(state.infoMessage) {
        if (state.infoMessage != null) {
            statusDescription = ""
            selectedFormalId = null
            formalDescription = ""
            selectedSecondId = null
            secondDescription = ""
        }
    }
    BackHandler {
        if (hasUnsavedChanges) confirmDiscard = true else actions.close()
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("${shift.objectiveNameSnapshot} · ${shift.startTimeSnapshot}–${shift.endTimeSnapshot}", fontWeight = FontWeight.Bold)
        Text("Todo el contenido de notas y descripciones es privado y local.")

        Section("Estado explícito") {
            Text(
                "Estado actual: " + when (shift.status) {
                    ShiftStatus.PLANNED -> "Normal"
                    ShiftStatus.ABSENT -> "Ausencia"
                    ShiftStatus.CANCELLED -> "Cancelada"
                },
                fontWeight = FontWeight.Bold,
            )
            Text("Ausencia y cancelación llevan las horas trabajadas a cero. Volver a normal restaura el estado derivado del reloj.")
            OutlinedTextField(statusDescription, { statusDescription = it }, label = { Text("Descripción opcional") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { actions.changeStatus(ShiftStatus.ABSENT, statusDescription) }) { Text("Ausencia") }
                OutlinedButton(onClick = { actions.changeStatus(ShiftStatus.CANCELLED, statusDescription) }) { Text("Cancelar guardia") }
            }
            OutlinedButton(onClick = { actions.changeStatus(ShiftStatus.PLANNED, "") }, modifier = Modifier.fillMaxWidth()) { Text("Volver a normal") }
        }

        Section("Notas privadas") {
            OutlinedTextField(
                state.noteDraft.body,
                { value -> actions.updateNoteDraft { it.copy(body = value) } },
                label = { Text("Nota") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = actions.saveNote, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) { Text("Guardar nota") }
            if (state.notes.isEmpty()) Text("No hay notas.")
            state.notes.forEach { note ->
                PrivateRow(note.body, { actions.editNote(note) }, { pendingDeleteNote = note.id.toString() })
            }
        }

        Section("Novedad informativa") {
            Text("Tiempo adicional, salida anticipada y otra novedad no modifican las horas.")
            informativeTypes.forEach { type ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(state.noveltyDraft.type == type, onClick = { actions.updateNoveltyDraft { it.copy(type = type) } })
                    Text(type.label())
                }
            }
            OutlinedTextField(
                state.noveltyDraft.description,
                { value -> actions.updateNoveltyDraft { it.copy(description = value) } },
                label = { Text(if (state.noveltyDraft.type == ShiftNoveltyType.OTHER) "Descripción obligatoria" else "Descripción opcional") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = actions.saveInformative, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) { Text("Guardar novedad informativa") }
            state.novelties.filter { it.type in informativeTypes }.forEach { novelty ->
                PrivateRow(
                    "${novelty.type.label()}: ${novelty.description.orEmpty()}",
                    { actions.editNovelty(novelty) },
                    { pendingDeleteInformative = novelty.id.toString() },
                )
            }
        }

        Section("Cambio formal") {
            Text("Cambiar objetivo u horario sí modifica las horas. El plan original se conserva.")
            state.scheduleOptions.filter { it.objective.isActive && it.combination.isActive }.forEach { option ->
                val id = option.combination.id.toString()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selectedFormalId == id, onClick = { selectedFormalId = id })
                    Text("${option.objective.abbreviation} · ${option.combination.startTime}–${option.combination.endTime}")
                }
            }
            OutlinedTextField(formalDescription, { formalDescription = it }, label = { Text("Motivo opcional") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { selectedFormalId?.let { actions.applyFormalChange(UUID.fromString(it), formalDescription) } },
                enabled = selectedFormalId != null && !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Aplicar cambio formal") }
            state.formalChange?.let { formal ->
                Text("Plan original", fontWeight = FontWeight.Bold)
                Text("${formal.original.objectiveName} · ${formal.original.startTime}–${formal.original.endTime}")
                Text("Resultado final", fontWeight = FontWeight.Bold)
                Text("${formal.final.objectiveName} · ${formal.final.startTime}–${formal.final.endTime}")
                OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth()) { Text("Restaurar plan original") }
            }
        }

        Section("Segunda guardia") {
            Text("Se crea como otra guardia real y se computa una sola vez. Confirmá las posibles superposiciones o descansos cortos.")
            state.scheduleOptions.filter { it.objective.isActive && it.combination.isActive }.forEach { option ->
                val id = option.combination.id.toString()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selectedSecondId == id, onClick = { selectedSecondId = id })
                    Text("${option.objective.abbreviation} · ${option.combination.startTime}–${option.combination.endTime}")
                }
            }
            OutlinedTextField(secondDescription, { secondDescription = it }, label = { Text("Descripción opcional") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { selectedSecondId?.let { actions.createSecondShift(UUID.fromString(it), secondDescription) } },
                enabled = selectedSecondId != null && !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Agregar segunda guardia") }
            state.novelties.filter { it.type == ShiftNoveltyType.SECOND_SHIFT }.forEach { novelty ->
                PrivateRow(
                    "Segunda guardia vinculada",
                    {},
                    { pendingDeleteSecond = novelty.id.toString() },
                    showEdit = false,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restaurar plan original") },
            text = { Text("Se verificará que la guardia no haya cambiado por otro flujo antes de restaurarla.") },
            confirmButton = { TextButton(onClick = { confirmRestore = false; actions.restoreOriginal() }) { Text("Restaurar") } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancelar") } },
        )
    }
    if (state.planningWarnings.isNotEmpty() && state.pendingPlanning != null) {
        AlertDialog(
            onDismissRequest = actions.dismissPlanningWarnings,
            title = {
                Text(
                    if (state.pendingPlanning.operation == ExceptionPlanningOperation.SECOND_SHIFT) {
                        "Confirmar segunda guardia"
                    } else {
                        "Confirmar cambio formal"
                    },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.planningWarnings.forEach { Text("• $it") }
                }
            },
            confirmButton = { TextButton(onClick = actions.confirmPlanningWarnings) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = actions.dismissPlanningWarnings) { Text("Volver") } },
        )
    }
    pendingDeleteNote?.let { raw ->
        ConfirmDeleteDialog(
            title = "Eliminar nota",
            text = "La nota privada se eliminará definitivamente.",
            onConfirm = { actions.deleteNote(UUID.fromString(raw)); pendingDeleteNote = null },
            onDismiss = { pendingDeleteNote = null },
        )
    }
    pendingDeleteInformative?.let { raw ->
        ConfirmDeleteDialog(
            title = "Eliminar novedad",
            text = "Esta novedad informativa se eliminará.",
            onConfirm = { actions.deleteInformative(UUID.fromString(raw)); pendingDeleteInformative = null },
            onDismiss = { pendingDeleteInformative = null },
        )
    }
    pendingDeleteSecond?.let { raw ->
        val novelty = state.novelties.firstOrNull { it.id.toString() == raw }
        if (novelty != null) {
            ConfirmDeleteDialog(
                title = "Eliminar segunda guardia",
                text = "Se eliminarán la segunda guardia y su vínculo con la guardia original.",
                onConfirm = { actions.deleteSecondShift(novelty); pendingDeleteSecond = null },
                onDismiss = { pendingDeleteSecond = null },
            )
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Descartar cambios") },
            text = { Text("Hay datos sin guardar en notas o novedades.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; actions.close() }) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Seguir editando") } },
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

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun PrivateRow(text: String, edit: () -> Unit, delete: () -> Unit, showEdit: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = "Elemento privado de la guardia" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f))
        if (showEdit) TextButton(onClick = edit) { Text("Editar") }
        TextButton(onClick = delete) { Text("Eliminar") }
    }
}

private fun ShiftNoveltyType.label(): String = when (this) {
    ShiftNoveltyType.ADDITIONAL_TIME -> "Tiempo adicional"
    ShiftNoveltyType.EARLY_DEPARTURE -> "Salida anticipada"
    ShiftNoveltyType.ABSENCE -> "Ausencia"
    ShiftNoveltyType.CANCELLATION -> "Cancelación"
    ShiftNoveltyType.SECOND_SHIFT -> "Segunda guardia"
    ShiftNoveltyType.OTHER -> "Otra"
}

private fun YearMonth.label(): String {
    val locale = Locale.forLanguageTag("es-AR")
    val monthName = month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
    return "$monthName de $year"
}
