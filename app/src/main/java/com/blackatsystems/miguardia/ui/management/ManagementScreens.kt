package com.blackatsystems.miguardia.ui.management

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.shift.OccupiedDatePolicy
import com.blackatsystems.miguardia.core.domain.shift.areColorsTooSimilar
import com.blackatsystems.miguardia.ui.components.TransientConfirmation
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

data class ManagementActions(
    val close: () -> Unit = {},
    val discardForm: () -> Unit = {},
    val openSettings: () -> Unit = {},
    val showHidden: (Boolean) -> Unit = {},
    val openObjective: (Objective?) -> Unit = {},
    val updateObjective: ((ObjectiveDraft) -> ObjectiveDraft) -> Unit = {},
    val saveObjective: () -> Unit = {},
    val hideObjective: (UUID) -> Unit = {},
    val deleteObjective: (UUID) -> Unit = {},
    val openSchedule: (UUID, ScheduleCombination?) -> Unit = { _, _ -> },
    val updateSchedule: ((ScheduleDraft) -> ScheduleDraft) -> Unit = {},
    val saveSchedule: () -> Unit = {},
    val hideSchedule: (UUID) -> Unit = {},
    val deleteSchedule: (UUID) -> Unit = {},
    val openAddShift: (YearMonth, LocalDate?) -> Unit = { _, _ -> },
    val openEditShift: (Shift) -> Unit = {},
    val openDuplicateShift: (Shift) -> Unit = {},
    val updateShiftMode: (ShiftEntryMode) -> Unit = {},
    val toggleShiftDate: (LocalDate) -> Unit = {},
    val chooseCombination: (UUID) -> Unit = {},
    val updatePosition: (String) -> Unit = {},
    val saveShift: (OccupiedDatePolicy?, Boolean) -> Unit = { _, _ -> },
    val confirmWarnings: () -> Unit = {},
    val dismissWarnings: () -> Unit = {},
    val deleteShift: (UUID) -> Unit = {},
    val clearMessage: () -> Unit = {},
) {
    companion object {
        fun from(viewModel: ManagementViewModel) = ManagementActions(
            close = viewModel::closeSurface,
            discardForm = viewModel::discardCurrentForm,
            openSettings = viewModel::openSettings,
            showHidden = viewModel::showHidden,
            openObjective = viewModel::openObjectiveForm,
            updateObjective = viewModel::updateObjectiveDraft,
            saveObjective = viewModel::saveObjective,
            hideObjective = viewModel::hideObjective,
            deleteObjective = viewModel::deleteObjective,
            openSchedule = viewModel::openScheduleForm,
            updateSchedule = viewModel::updateScheduleDraft,
            saveSchedule = viewModel::saveSchedule,
            hideSchedule = viewModel::hideSchedule,
            deleteSchedule = viewModel::deleteSchedule,
            openAddShift = viewModel::openAddShift,
            openEditShift = viewModel::openEditShift,
            openDuplicateShift = viewModel::openDuplicateShift,
            updateShiftMode = viewModel::updateShiftMode,
            toggleShiftDate = viewModel::toggleShiftDate,
            chooseCombination = viewModel::chooseShiftCombination,
            updatePosition = viewModel::updateShiftPosition,
            saveShift = viewModel::requestSaveShift,
            confirmWarnings = viewModel::confirmShiftWarnings,
            dismissWarnings = viewModel::dismissShiftWarnings,
            deleteShift = viewModel::deleteShift,
            clearMessage = viewModel::clearMessage,
        )
    }
}

@Composable
fun ManagementSurfaceHost(
    state: ManagementUiState,
    actions: ManagementActions,
) {
    var confirmClose by rememberSaveable { mutableStateOf(false) }
    val formOpen = state.surface in setOf(
        ManagementSurface.OBJECTIVE_FORM,
        ManagementSurface.SCHEDULE_FORM,
        ManagementSurface.SHIFT_FORM,
    )
    val requestClose = {
        if (formOpen) confirmClose = true else actions.close()
    }
    BackHandler(onBack = requestClose)

    TransientConfirmation(state.infoMessage, actions.clearMessage) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(surfaceTitle(state.surface), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = requestClose) { Text(if (formOpen) "Volver" else "Cerrar") }
            }
            HorizontalDivider()
            state.errorMessage?.let { MessageCard(it, isError = true, actions.clearMessage) }
            when (state.surface) {
                ManagementSurface.NONE -> Unit
                ManagementSurface.SETTINGS -> SettingsManagementContent(state, actions)
                ManagementSurface.OBJECTIVE_FORM -> ObjectiveForm(state, actions)
                ManagementSurface.SCHEDULE_FORM -> ScheduleForm(state, actions)
                ManagementSurface.SHIFT_FORM -> ShiftForm(state, actions)
                }
            }
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Descartar cambios") },
            text = { Text("Hay datos del formulario sin guardar. ¿Querés salir igualmente?") },
            confirmButton = {
                TextButton(onClick = { confirmClose = false; actions.discardForm() }) { Text("Descartar") }
            },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Seguir editando") } },
        )
    }
}

@Composable
private fun MessageCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    }
}

@Composable
private fun SettingsManagementContent(state: ManagementUiState, actions: ManagementActions) {
    var pendingAction by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Administrá las plantillas para futuras guardias. El historial no cambia.")
        Button(onClick = { actions.openObjective(null) }, modifier = Modifier.fillMaxWidth()) {
            Text("Crear objetivo")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.showHidden, onCheckedChange = actions.showHidden)
            Text("Mostrar ocultos", modifier = Modifier.padding(start = 8.dp))
        }
        val visible = state.objectives.filter { it.isActive || state.showHidden }
        if (visible.isEmpty()) Text("Todavía no hay objetivos.")
        visible.forEach { objective ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${objective.fullName} (${objective.abbreviation})", fontWeight = FontWeight.Bold)
                    if (!objective.isActive) Text("Oculto", color = MaterialTheme.colorScheme.secondary)
                    objective.address?.takeIf(String::isNotBlank)?.let { Text(it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { actions.openObjective(objective) }) { Text("Editar") }
                        if (objective.isActive) {
                            TextButton(onClick = { pendingAction = "hide-objective:${objective.id}" }) { Text("Ocultar") }
                        }
                        TextButton(onClick = { pendingAction = "delete-objective:${objective.id}" }) { Text("Eliminar") }
                    }
                    val schedules = state.scheduleOptions.filter {
                        it.objective.id == objective.id && (it.combination.isActive || state.showHidden)
                    }
                    schedules.forEach { option ->
                        ScheduleRow(
                            option.combination,
                            onEdit = { actions.openSchedule(objective.id, option.combination) },
                            onHide = { pendingAction = "hide-schedule:${option.combination.id}" },
                            onDelete = { pendingAction = "delete-schedule:${option.combination.id}" },
                        )
                    }
                    if (objective.isActive) {
                        OutlinedButton(onClick = { actions.openSchedule(objective.id, null) }) { Text("Crear horario") }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    pendingAction?.let { encoded ->
        val (kind, rawId) = encoded.split(":", limit = 2)
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(if (kind.startsWith("delete")) "Confirmar eliminación" else "Confirmar ocultamiento") },
            text = {
                Text(
                    when (kind) {
                        "delete-objective" -> "También se eliminarán sus horarios. Las guardias históricas se conservarán."
                        "delete-schedule" -> "Las guardias históricas que usaron este horario se conservarán."
                        else -> "La plantilla dejará de aparecer en nuevas cargas y en recientes; el historial se conservará."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = UUID.fromString(rawId)
                        when (kind) {
                            "hide-objective" -> actions.hideObjective(id)
                            "delete-objective" -> actions.deleteObjective(id)
                            "hide-schedule" -> actions.hideSchedule(id)
                            "delete-schedule" -> actions.deleteSchedule(id)
                        }
                        pendingAction = null
                    },
                    enabled = !state.isSaving,
                ) { Text(if (kind.startsWith("delete")) "Eliminar" else "Ocultar") }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ScheduleRow(
    schedule: ScheduleCombination,
    onEdit: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).background(Color(schedule.colorArgb), CircleShape))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text("${schedule.startTime}–${schedule.endTime}", fontWeight = FontWeight.SemiBold)
            if (schedule.endTime <= schedule.startTime) Text("Termina al día siguiente", style = MaterialTheme.typography.bodySmall)
            if (!schedule.isActive) Text("Oculto", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onEdit) { Text("Editar") }
        if (schedule.isActive) TextButton(onClick = onHide) { Text("Ocultar") }
        TextButton(onClick = onDelete) { Text("Eliminar") }
    }
}

@Composable
private fun ObjectiveForm(state: ManagementUiState, actions: ManagementActions) {
    val draft = state.objectiveDraft
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = draft.fullName,
            onValueChange = { value -> actions.updateObjective { it.copy(fullName = value) } },
            label = { Text("Nombre completo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.abbreviation,
            onValueChange = { value ->
                actions.updateObjective { it.copy(abbreviation = value.uppercase(Locale.ROOT).take(5)) }
            },
            label = { Text("Abreviatura (2 a 5 caracteres)") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.address,
            onValueChange = { value -> actions.updateObjective { it.copy(address = value) } },
            label = { Text("Dirección opcional") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.note,
            onValueChange = { value -> actions.updateObjective { it.copy(note = value) } },
            label = { Text("Nota opcional") },
            modifier = Modifier.fillMaxWidth(),
        )
        SaveButton(state.isSaving, "Guardar objetivo", actions.saveObjective)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleForm(state: ManagementUiState, actions: ManagementActions) {
    val draft = state.scheduleDraft
    var confirmSimilarColor by rememberSaveable { mutableStateOf(false) }
    var selectingStartTime by rememberSaveable { mutableStateOf(false) }
    var selectingEndTime by rememberSaveable { mutableStateOf(false) }
    var choosingColor by rememberSaveable { mutableStateOf(false) }
    val similar = state.scheduleOptions.any {
        it.objective.id == draft.objectiveId && it.combination.id != draft.editingId &&
            areColorsTooSimilar(it.combination.colorArgb, draft.colorArgb)
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Elegí las horas en formato de 24 horas.")
        OutlinedButton(
            onClick = { selectingStartTime = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Inicio: ${draft.startTime}") }
        OutlinedButton(
            onClick = { selectingEndTime = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Fin: ${draft.endTime}") }
        Text("Si la hora final es anterior o igual, termina al día siguiente. Horas iguales representan 24 horas.")
        Text("Color del horario", fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(Color(draft.colorArgb), CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
            )
            OutlinedButton(onClick = { choosingColor = true }, modifier = Modifier.weight(1f)) {
                Text("Elegir color")
            }
        }
        SaveButton(state.isSaving, "Guardar horario") {
            if (similar) confirmSimilarColor = true else actions.saveSchedule()
        }
    }
    if (confirmSimilarColor) {
        AlertDialog(
            onDismissRequest = { confirmSimilarColor = false },
            title = { Text("Colores parecidos") },
            text = { Text("Este color es muy parecido al de otro horario visible. El texto seguirá identificando cada horario. ¿Querés usarlo?") },
            confirmButton = {
                TextButton(onClick = { confirmSimilarColor = false; actions.saveSchedule() }) { Text("Usar color") }
            },
            dismissButton = { TextButton(onClick = { confirmSimilarColor = false }) { Text("Elegir otro") } },
        )
    }
    if (choosingColor) {
        RgbColorPickerDialog(
            initialColor = draft.colorArgb,
            onDismiss = { choosingColor = false },
            onConfirm = { selected ->
                actions.updateSchedule { it.copy(colorArgb = selected) }
                choosingColor = false
            },
        )
    }
    if (selectingStartTime) {
        TimeSelectionDialog(
            title = "Seleccionar hora de inicio",
            initialValue = draft.startTime,
            onDismiss = { selectingStartTime = false },
            onConfirm = { value ->
                actions.updateSchedule { it.copy(startTime = value) }
                selectingStartTime = false
            },
        )
    }
    if (selectingEndTime) {
        TimeSelectionDialog(
            title = "Seleccionar hora de finalización",
            initialValue = draft.endTime,
            onDismiss = { selectingEndTime = false },
            onConfirm = { value ->
                actions.updateSchedule { it.copy(endTime = value) }
                selectingEndTime = false
            },
        )
    }
}

@Composable
private fun RgbColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var selectedColor by rememberSaveable(initialColor) { mutableIntStateOf(initialColor) }
    val red = selectedColor ushr 16 and 0xFF
    val green = selectedColor ushr 8 and 0xFF
    val blue = selectedColor and 0xFF

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir color") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Color(selectedColor), MaterialTheme.shapes.medium)
                        .semantics { contentDescription = "Vista previa del color" },
                )
                Text(
                    "RGB ($red, $green, $blue) · #${selectedColor.toUInt().toString(16).takeLast(6).uppercase()}",
                    fontWeight = FontWeight.SemiBold,
                )
                RgbSlider("Rojo", red) { value -> selectedColor = rgb(value, green, blue) }
                RgbSlider("Verde", green) { value -> selectedColor = rgb(red, value, blue) }
                RgbSlider("Azul", blue) { value -> selectedColor = rgb(red, green, value) }
                Text("Colores comunes", fontWeight = FontWeight.Bold)
                ManagementViewModel.COLOR_PALETTE.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        row.forEach { argb ->
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .semantics {
                                        contentDescription = "Color común ${argb.toUInt().toString(16).takeLast(6).uppercase()}"
                                    }
                                    .background(Color(argb), CircleShape)
                                    .then(
                                        if (argb == selectedColor) {
                                            Modifier.border(4.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable { selectedColor = argb },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedColor) }) { Text("Usar color") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun RgbSlider(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Column {
        Text("$label: $value")
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            steps = 254,
            modifier = Modifier.semantics { contentDescription = "$label RGB" },
        )
    }
}

private fun rgb(red: Int, green: Int, blue: Int): Int =
    0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSelectionDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initialTime = runCatching { LocalTime.parse(initialValue) }.getOrDefault(LocalTime.MIDNIGHT)
    val pickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimeInput(state = pickerState) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm("%02d:%02d".format(Locale.ROOT, pickerState.hour, pickerState.minute))
                },
            ) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ShiftForm(state: ManagementUiState, actions: ManagementActions) {
    val draft = state.shiftDraft ?: return
    var finalConfirmation by rememberSaveable { mutableStateOf(false) }
    val activeOptions = state.scheduleOptions.filter { it.objective.isActive && it.combination.isActive }
    val activeObjectives = state.objectives.filter(Objective::isActive)
    val selectedObjectiveId = activeOptions
        .firstOrNull { it.combination.id == draft.combinationId }
        ?.objective
        ?.id
    var expandedObjectiveId by rememberSaveable(draft.month, draft.editingShift?.id) {
        mutableStateOf(selectedObjectiveId?.toString())
    }
    LaunchedEffect(selectedObjectiveId, activeObjectives) {
        when {
            selectedObjectiveId != null -> expandedObjectiveId = selectedObjectiveId.toString()
            expandedObjectiveId == null && activeObjectives.size == 1 -> {
                expandedObjectiveId = activeObjectives.single().id.toString()
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (draft.editingShift == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton("Una fecha", draft.mode == ShiftEntryMode.SINGLE) { actions.updateShiftMode(ShiftEntryMode.SINGLE) }
                ModeButton("Varias fechas", draft.mode == ShiftEntryMode.MULTIPLE) { actions.updateShiftMode(ShiftEntryMode.MULTIPLE) }
            }
        }
        Text(monthLabel(draft.month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        MonthDateSelector(draft.month, draft.selectedDates, actions.toggleShiftDate)

        if (draft.duplicateSource == null) {
            Text("Usados recientemente", fontWeight = FontWeight.Bold)
            if (state.recent.isEmpty()) Text("Todavía no hay horarios recientes.")
            state.recent.forEach { recent ->
                CombinationChoice(
                    label = "${recent.objective.abbreviation} · ${recent.combination.startTime}–${recent.combination.endTime}",
                    selected = draft.combinationId == recent.combination.id,
                    color = recent.combination.colorArgb,
                    onClick = { actions.chooseCombination(recent.combination.id) },
                )
            }
            Text("Explorar objetivos y horarios", fontWeight = FontWeight.Bold)
            if (activeObjectives.isEmpty()) {
                Text("Todavía no hay objetivos. Creá el primero para agregar sus horarios.")
            }
            activeObjectives.forEach { objective ->
                ObjectiveScheduleFolder(
                    objective = objective,
                    schedules = activeOptions.filter { it.objective.id == objective.id },
                    selectedCombinationId = draft.combinationId,
                    expanded = expandedObjectiveId == objective.id.toString(),
                    onToggle = {
                        expandedObjectiveId = if (expandedObjectiveId == objective.id.toString()) {
                            null
                        } else {
                            objective.id.toString()
                        }
                    },
                    onChoose = actions.chooseCombination,
                    onAddSchedule = { actions.openSchedule(objective.id, null) },
                )
            }
            OutlinedButton(
                onClick = { actions.openObjective(null) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Crear objetivo") }
        } else {
            Text("Se copiará ${draft.duplicateSource.objectiveAbbreviationSnapshot} · ${draft.duplicateSource.startTimeSnapshot}–${draft.duplicateSource.endTimeSnapshot}.")
        }
        OutlinedTextField(
            value = draft.position,
            onValueChange = actions.updatePosition,
            label = { Text("Puesto opcional") },
            modifier = Modifier.fillMaxWidth(),
        )
        ShiftPreview(state)
        SaveButton(state.isSaving, "Revisar y guardar") { finalConfirmation = true }
        Spacer(Modifier.height(24.dp))
    }

    if (finalConfirmation) {
        AlertDialog(
            onDismissRequest = { finalConfirmation = false },
            title = { Text("Confirmar guardias") },
            text = { Text("Se guardarán ${draft.selectedDates.size} guardia(s) en ${monthLabel(draft.month)}. Las fechas pasadas se verán completadas automáticamente.") },
            confirmButton = {
                TextButton(
                    enabled = !state.isSaving,
                    onClick = { finalConfirmation = false; actions.saveShift(null, false) },
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { finalConfirmation = false }) { Text("Volver") } },
        )
    }
    if (draft.occupiedDates.isNotEmpty()) OccupiedDatesDialog(draft, state.isSaving, actions)
    if (draft.warnings.isNotEmpty()) {
        WarningDialog(
            warnings = draft.warnings,
            isSaving = state.isSaving,
            onConfirm = actions.confirmWarnings,
            onDismiss = actions.dismissWarnings,
        )
    }
}

@Composable
private fun ObjectiveScheduleFolder(
    objective: Objective,
    schedules: List<ScheduleOption>,
    selectedCombinationId: UUID?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChoose: (UUID) -> Unit,
    onAddSchedule: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .semantics {
                        contentDescription = if (expanded) {
                            "Cerrar horarios de ${objective.abbreviation}"
                        } else {
                            "Abrir horarios de ${objective.abbreviation}"
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(if (expanded) "▾" else "▸", fontWeight = FontWeight.Bold)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${objective.fullName} (${objective.abbreviation})",
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (schedules.size == 1) "1 horario" else "${schedules.size} horarios",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (expanded) {
                HorizontalDivider()
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (schedules.isEmpty()) {
                        Text(
                            "Todavía no hay horarios para este objetivo.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    schedules.forEach { option ->
                        CombinationChoice(
                            label = "${option.combination.startTime}–${option.combination.endTime}",
                            selected = selectedCombinationId == option.combination.id,
                            color = option.combination.colorArgb,
                            onClick = { onChoose(option.combination.id) },
                        )
                    }
                    TextButton(
                        onClick = onAddSchedule,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("+ Agregar horario")
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthDateSelector(month: YearMonth, selected: Set<LocalDate>, onToggle: (LocalDate) -> Unit) {
    val leading = month.atDay(1).dayOfWeek.value - 1
    val cells = List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    Text("L  M  X  J  V  S  D", style = MaterialTheme.typography.labelLarge)
    cells.chunked(7).forEach { week ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            week.forEach { date ->
                if (date == null) {
                    Spacer(Modifier.size(42.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                if (date in selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                CircleShape,
                            )
                            .clickable { onToggle(date) }
                            .semantics { contentDescription = "${date.dayOfMonth} ${monthLabel(month)}, ${if (date in selected) "seleccionado" else "sin seleccionar"}" },
                        contentAlignment = Alignment.Center,
                    ) { Text(date.dayOfMonth.toString()) }
                }
            }
            repeat(7 - week.size) { Spacer(Modifier.size(42.dp)) }
        }
    }
}

@Composable
private fun CombinationChoice(label: String, selected: Boolean, color: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Box(Modifier.size(16.dp).background(Color(color), CircleShape))
        Text(label, modifier = Modifier.padding(start = 8.dp).weight(1f))
    }
}

@Composable
private fun ShiftPreview(state: ManagementUiState) {
    val draft = state.shiftDraft ?: return
    val option = state.scheduleOptions.firstOrNull { it.combination.id == draft.combinationId }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Vista previa", fontWeight = FontWeight.Bold)
            Text("${draft.selectedDates.size} fecha(s): ${draft.selectedDates.sorted().joinToString { it.dayOfMonth.toString() }}")
            if (draft.duplicateSource != null) {
                Text("${draft.duplicateSource.objectiveNameSnapshot} · ${draft.duplicateSource.startTimeSnapshot}–${draft.duplicateSource.endTimeSnapshot}")
            } else if (option != null) {
                Text("${option.objective.fullName} · ${option.combination.startTime}–${option.combination.endTime}")
                if (option.combination.endTime <= option.combination.startTime) Text("Termina al día siguiente")
            } else {
                Text("Elegí un objetivo y horario.")
            }
            draft.position.takeIf(String::isNotBlank)?.let { Text("Puesto: $it") }
        }
    }
}

@Composable
private fun OccupiedDatesDialog(draft: ShiftDraft, isSaving: Boolean, actions: ManagementActions) {
    var secondShift by rememberSaveable { mutableStateOf(false) }
    if (draft.editingShift != null) {
        AlertDialog(
            onDismissRequest = { actions.saveShift(OccupiedDatePolicy.CANCEL, false) },
            title = { Text("Ya hay otra guardia") },
            text = { Text("La fecha elegida ya tiene otra guardia. Podés conservarla y confirmar esta segunda guardia.") },
            confirmButton = {
                TextButton(enabled = !isSaving, onClick = { actions.saveShift(OccupiedDatePolicy.ADD_SECOND_SHIFT, false) }) { Text("Guardar como segunda") }
            },
            dismissButton = {
                TextButton(onClick = { actions.saveShift(OccupiedDatePolicy.CANCEL, false) }) { Text("Cancelar") }
            },
        )
        return
    }
    if (secondShift) {
        AlertDialog(
            onDismissRequest = { secondShift = false },
            title = { Text("Agregar segunda guardia") },
            text = { Text("Las fechas ${draft.occupiedDates.sorted().joinToString { it.dayOfMonth.toString() }} ya tienen guardias. Se conservarán y se agregará otra.") },
            confirmButton = {
                TextButton(enabled = !isSaving, onClick = { secondShift = false; actions.saveShift(OccupiedDatePolicy.ADD_SECOND_SHIFT, false) }) { Text("Agregar segunda") }
            },
            dismissButton = { TextButton(onClick = { secondShift = false }) { Text("Volver") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = { actions.saveShift(OccupiedDatePolicy.CANCEL, false) },
        title = { Text("Fechas ocupadas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ya tienen guardias: ${draft.occupiedDates.sorted().joinToString { it.dayOfMonth.toString() }}.")
                Button(enabled = !isSaving, onClick = { actions.saveShift(OccupiedDatePolicy.REPLACE, false) }, modifier = Modifier.fillMaxWidth()) { Text("Reemplazar") }
                OutlinedButton(enabled = !isSaving, onClick = { actions.saveShift(OccupiedDatePolicy.KEEP_OCCUPIED, false) }, modifier = Modifier.fillMaxWidth()) { Text("Conservar ocupadas") }
                OutlinedButton(enabled = !isSaving, onClick = { secondShift = true }, modifier = Modifier.fillMaxWidth()) { Text("Agregar segunda guardia") }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { actions.saveShift(OccupiedDatePolicy.CANCEL, false) }) { Text("Cancelar") }
        },
    )
}

@Composable
private fun WarningDialog(
    warnings: List<String>,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Revisá estas advertencias") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { warnings.forEach { Text("• $it") } } },
        confirmButton = {
            TextButton(enabled = !isSaving, onClick = onConfirm) { Text("Continuar igualmente") }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) {
                Text("Volver y corregir")
            }
        },
    )
}

@Composable
private fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) } else OutlinedButton(onClick = onClick) { Text(label) }
}

@Composable
private fun SaveButton(saving: Boolean, label: String, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
        if (saving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
        }
        Text(label)
    }
}

private fun surfaceTitle(surface: ManagementSurface): String = when (surface) {
    ManagementSurface.NONE -> ""
    ManagementSurface.SETTINGS -> "Objetivos y horarios"
    ManagementSurface.OBJECTIVE_FORM -> "Objetivo"
    ManagementSurface.SCHEDULE_FORM -> "Horario"
    ManagementSurface.SHIFT_FORM -> "Guardias"
}

private fun monthLabel(month: YearMonth): String {
    val locale = Locale.forLanguageTag("es-AR")
    val name = month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
    return "$name de ${month.year}"
}
