package com.blackatsystems.miguardia.ui.management

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.shift.OccupiedDatePolicy
import com.blackatsystems.miguardia.core.domain.shift.areColorsTooSimilar
import com.blackatsystems.miguardia.ui.components.TransientConfirmation
import com.blackatsystems.miguardia.ui.components.DestructiveAction
import com.blackatsystems.miguardia.ui.components.EmptyState
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.PrimaryAction
import com.blackatsystems.miguardia.ui.components.ScreenHeading
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.components.SurfaceHeader
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
    val openDayOffs: (YearMonth, LocalDate?) -> Unit = { _, _ -> },
    val openAddShiftForDates: (YearMonth, Set<LocalDate>) -> Unit = { _, _ -> },
    val openDayOffsForDates: (YearMonth, Set<LocalDate>) -> Unit = { _, _ -> },
    val updateCalendarSelection: (Set<LocalDate>) -> Unit = {},
    val openEditShift: (Shift) -> Unit = {},
    val chooseCombination: (UUID) -> Unit = {},
    val updatePosition: (String) -> Unit = {},
    val saveShift: (OccupiedDatePolicy?, Boolean) -> Unit = { _, _ -> },
    val confirmWarnings: () -> Unit = {},
    val dismissWarnings: () -> Unit = {},
    val deleteShift: (UUID) -> Unit = {},
    val saveDayOffs: () -> Unit = {},
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
            openDayOffs = viewModel::openDayOffs,
            openAddShiftForDates = viewModel::openAddShift,
            openDayOffsForDates = viewModel::openDayOffs,
            updateCalendarSelection = viewModel::updateCalendarSelection,
            openEditShift = viewModel::openEditShift,
            chooseCombination = viewModel::chooseShiftCombination,
            updatePosition = viewModel::updateShiftPosition,
            saveShift = viewModel::requestSaveShift,
            confirmWarnings = viewModel::confirmShiftWarnings,
            dismissWarnings = viewModel::dismissShiftWarnings,
            deleteShift = viewModel::deleteShift,
            saveDayOffs = viewModel::saveDayOffs,
            clearMessage = viewModel::clearMessage,
        )
    }
}

@Composable
fun ManagementSurfaceHost(
    state: ManagementUiState,
    actions: ManagementActions,
    onOpenNotifications: (Shift) -> Unit = {},
) {
    var confirmClose by rememberSaveable { mutableStateOf(false) }
    val formOpen = state.surface in setOf(
        ManagementSurface.OBJECTIVE_FORM,
        ManagementSurface.SCHEDULE_FORM,
        ManagementSurface.SHIFT_FORM,
        ManagementSurface.DAY_OFF_FORM,
    )
    val requestClose = {
        if (formOpen) confirmClose = true else actions.close()
    }
    BackHandler(onBack = requestClose)

    TransientConfirmation(state.infoMessage, actions.clearMessage) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            SurfaceHeader(
                title = surfaceTitle(state.surface),
                navigationLabel = if (formOpen) "Volver" else "Cerrar",
                onNavigation = requestClose,
            )
            HorizontalDivider()
            state.errorMessage?.let { MessageCard(it, isError = true, actions.clearMessage) }
            when (state.surface) {
                ManagementSurface.NONE -> Unit
                ManagementSurface.SETTINGS -> SettingsManagementContent(state, actions)
                ManagementSurface.OBJECTIVE_FORM -> ObjectiveForm(state, actions)
                ManagementSurface.SCHEDULE_FORM -> ScheduleForm(state, actions)
                ManagementSurface.SHIFT_FORM -> ShiftForm(state, actions, onOpenNotifications, inline = false)
                ManagementSurface.DAY_OFF_FORM -> DayOffForm(state, actions, inline = false)
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
fun CalendarManagementInlineContent(
    state: ManagementUiState,
    actions: ManagementActions,
    onOpenNotifications: (Shift) -> Unit = {},
) {
    if (state.surface !in setOf(ManagementSurface.SHIFT_FORM, ManagementSurface.DAY_OFF_FORM)) return
    var confirmClose by rememberSaveable { mutableStateOf(false) }
    val requestClose = { confirmClose = true }
    BackHandler(onBack = requestClose)
    TransientConfirmation(state.infoMessage, actions.clearMessage) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("calendar-inline-management"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.errorMessage?.let { MessageCard(it, isError = true, actions.clearMessage) }
            when (state.surface) {
                ManagementSurface.SHIFT_FORM -> ShiftForm(state, actions, onOpenNotifications, inline = true)
                ManagementSurface.DAY_OFF_FORM -> DayOffForm(state, actions, inline = true)
                else -> Unit
            }
            OutlinedButton(onClick = requestClose, modifier = Modifier.fillMaxWidth()) {
                Text("Volver a las herramientas")
            }
        }
    }
    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Descartar cambios") },
            text = { Text("Hay datos del formulario sin guardar. ¿Querés volver a la selección de días?") },
            confirmButton = {
                TextButton(onClick = { confirmClose = false; actions.discardForm() }) { Text("Descartar") }
            },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Seguir editando") } },
        )
    }
}

@Composable
private fun DayOffForm(state: ManagementUiState, actions: ManagementActions, inline: Boolean) {
    val draft = state.dayOffDraft ?: return
    Column(
        modifier = if (inline) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeading(
            title = "Agregar francos",
            supportingText = "Se marcarán las fechas elegidas arriba. Marcar F no elimina guardias ni otros datos del día.",
        )
        Text(monthLabel(draft.month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            when (draft.selectedDates.size) {
                0 -> "No hay fechas seleccionadas."
                1 -> "1 fecha seleccionada."
                else -> "${draft.selectedDates.size} fechas seleccionadas."
            },
            fontWeight = FontWeight.SemiBold,
        )
        PrimaryAction(
            label = if (draft.selectedDates.size == 1) "Agregar franco" else "Agregar francos",
            onClick = actions.saveDayOffs,
            enabled = draft.selectedDates.isNotEmpty(),
            working = state.isSaving,
        )
    }
}

@Composable
private fun MessageCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    PersistentMessage(
        message = message,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        onDismiss = onDismiss,
    )
}

@Composable
private fun SettingsManagementContent(state: ManagementUiState, actions: ManagementActions) {
    var pendingAction by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeading(
            title = "Plantillas de guardia",
            supportingText = "Administrá objetivos y horarios futuros. Las guardias históricas no cambian.",
        )
        PrimaryAction(label = "Crear objetivo", onClick = { actions.openObjective(null) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.showHidden, onCheckedChange = actions.showHidden)
            Text("Mostrar ocultos", modifier = Modifier.padding(start = 8.dp))
        }
        val visible = state.objectives.filter { it.isActive || state.showHidden }
        if (visible.isEmpty()) {
            EmptyState(
                title = "Sin objetivos",
                message = "Creá un objetivo y después agregale uno o más horarios.",
            )
        }
        visible.forEach { objective ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${objective.fullName} (${objective.abbreviation})", fontWeight = FontWeight.Bold)
                    if (!objective.isActive) Text("Oculto", color = MaterialTheme.colorScheme.secondary)
                    objective.address?.takeIf(String::isNotBlank)?.let { Text(it) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(onClick = { actions.openObjective(objective) }) { Text("Editar") }
                        if (objective.isActive) {
                            TextButton(onClick = { pendingAction = "hide-objective:${objective.id}" }) { Text("Ocultar") }
                        }
                    }
                    DestructiveAction(
                        label = "Eliminar",
                        onClick = { pendingAction = "delete-objective:${objective.id}" },
                    )
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).background(Color(schedule.colorArgb), CircleShape))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text("${schedule.startTime}–${schedule.endTime}", fontWeight = FontWeight.SemiBold)
                if (schedule.endTime <= schedule.startTime) Text("Termina al día siguiente", style = MaterialTheme.typography.bodySmall)
                if (!schedule.isActive) Text("Oculto", style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onEdit) { Text("Editar") }
            if (schedule.isActive) TextButton(onClick = onHide) { Text("Ocultar") }
            DestructiveAction(label = "Eliminar", onClick = onDelete)
        }
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
    val initialHsv = FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) }
    var hue by rememberSaveable(initialColor) { mutableStateOf(initialHsv[0]) }
    var saturation by rememberSaveable(initialColor) { mutableStateOf(initialHsv[1]) }
    var brightness by rememberSaveable(initialColor) { mutableStateOf(initialHsv[2]) }
    val selectedColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val red = selectedColor ushr 16 and 0xFF
    val green = selectedColor ushr 8 and 0xFF
    val blue = selectedColor and 0xFF
    val selectedComposeColor = Color(selectedColor)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selector de color") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Saturación y luminosidad", fontWeight = FontWeight.SemiBold)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("color-saturation-brightness")
                        .semantics {
                            contentDescription = "Área de saturación y luminosidad"
                            stateDescription =
                                "Saturación ${(saturation * 100).toInt()} %, luminosidad ${(brightness * 100).toInt()} %"
                        }
                        .pointerInput(hue) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                fun updateColor(position: Offset) {
                                    saturation = (position.x / size.width).coerceIn(0f, 1f)
                                    brightness = (1f - position.y / size.height).coerceIn(0f, 1f)
                                }
                                updateColor(down.position)
                                down.consume()
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { change ->
                                        if (change.pressed) updateColor(change.position)
                                        change.consume()
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                ) {
                    drawRect(
                        brush = Brush.horizontalGradient(listOf(Color.White, hueColor)),
                    )
                    drawRect(
                        brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
                    )
                    val markerCenter = Offset(
                        x = (saturation * size.width).coerceIn(10.dp.toPx(), size.width - 10.dp.toPx()),
                        y = ((1f - brightness) * size.height).coerceIn(10.dp.toPx(), size.height - 10.dp.toPx()),
                    )
                    drawCircle(Color.Black, radius = 10.dp.toPx(), center = markerCenter)
                    drawCircle(Color.White, radius = 8.dp.toPx(), center = markerCenter)
                    drawCircle(selectedComposeColor, radius = 5.dp.toPx(), center = markerCenter)
                }

                Text("Tono", fontWeight = FontWeight.SemiBold)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("color-hue")
                        .semantics {
                            contentDescription = "Barra arcoíris de tono"
                            stateDescription = "Tono ${hue.toInt()} grados"
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                fun updateHue(position: Offset) {
                                    hue = ((position.x / size.width).coerceIn(0f, 1f) * 360f)
                                        .coerceAtMost(359.999f)
                                }
                                updateHue(down.position)
                                down.consume()
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { change ->
                                        if (change.pressed) updateHue(change.position)
                                        change.consume()
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                ) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red,
                            ),
                        ),
                    )
                    val markerRadius = 10.dp.toPx()
                    val markerCenter = Offset(
                        x = (hue / 360f * size.width).coerceIn(markerRadius, size.width - markerRadius),
                        y = size.height / 2f,
                    )
                    drawCircle(Color.Black, radius = markerRadius, center = markerCenter)
                    drawCircle(Color.White, radius = 8.dp.toPx(), center = markerCenter)
                    drawCircle(hueColor, radius = 5.dp.toPx(), center = markerCenter)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .background(selectedComposeColor, MaterialTheme.shapes.medium)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                            .semantics { contentDescription = "Vista previa del color" },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("RGB: $red, $green, $blue", fontWeight = FontWeight.SemiBold)
                        Text("HEX: #${selectedColor.toUInt().toString(16).takeLast(6).uppercase()}")
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
private fun ShiftForm(
    state: ManagementUiState,
    actions: ManagementActions,
    onOpenNotifications: (Shift) -> Unit,
    inline: Boolean,
) {
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
        modifier = if (inline) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (inline) {
            ScreenHeading(
                title = "Agregar guardia",
                supportingText = "La selección pertenece a la grilla principal; elegí objetivo, horario y puesto.",
            )
        }
        Text(monthLabel(draft.month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            if (draft.selectedDates.size == 1) {
                "1 fecha elegida arriba: ${draft.selectedDates.single().dayOfMonth}"
            } else {
                "${draft.selectedDates.size} fechas elegidas arriba: ${draft.selectedDates.sorted().joinToString { it.dayOfMonth.toString() }}"
            },
            fontWeight = FontWeight.SemiBold,
        )

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
        OutlinedButton(
            onClick = { actions.openObjective(null) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Crear objetivo") }
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
        OutlinedTextField(
            value = draft.position,
            onValueChange = actions.updatePosition,
            label = { Text("Puesto opcional") },
            modifier = Modifier.fillMaxWidth(),
        )
        draft.editingShift?.let { shift ->
            SectionCard(
                title = "Avisos de esta guardia",
                supportingText = "Usa la configuración global salvo que elijas una excepción para esta guardia.",
            ) {
                OutlinedButton(
                    onClick = { onOpenNotifications(shift) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Configurar avisos") }
            }
        }
        ShiftPreview(state)
        SaveButton(state.isSaving, "Revisar y guardar") { finalConfirmation = true }
        Spacer(Modifier.height(24.dp))
    }

    if (finalConfirmation) {
        AlertDialog(
            onDismissRequest = { finalConfirmation = false },
            title = { Text("Confirmar guardias") },
            text = {
                Text(
                    "Vas a guardar ${draft.selectedDates.size} guardia(s) en ${monthLabel(draft.month)}. " +
                        "Si alguna fecha ya pasó, MiGuardia la marcará automáticamente como realizada.",
                )
            },
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
    val selectedDates = draft.selectedDates.sorted()
    val dateSummary = when (selectedDates.size) {
        0 -> "Ninguna fecha seleccionada"
        1 -> "1 fecha seleccionada: ${selectedDates.single().dayOfMonth}"
        else -> "${selectedDates.size} fechas seleccionadas: ${selectedDates.joinToString { it.dayOfMonth.toString() }}"
    }
    val previewColor = option?.combination?.colorArgb
    SectionCard(
        title = "Vista previa",
        supportingText = "Así se verá la guardia antes de confirmar.",
    ) {
        Text(dateSummary, fontWeight = FontWeight.SemiBold)
        if (option != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                previewColor?.let { Box(Modifier.size(16.dp).background(Color(it), CircleShape)) }
                Column(Modifier.padding(start = 8.dp)) {
                    Text(option.objective.fullName, fontWeight = FontWeight.Bold)
                    Text("${option.combination.startTime}–${option.combination.endTime}")
                }
            }
            if (option.combination.endTime <= option.combination.startTime) {
                Text("Termina al día siguiente", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Text("Elegí un objetivo y horario para completar la vista previa.")
        }
        draft.position.takeIf(String::isNotBlank)?.let { Text("Puesto: $it") }
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
    if (draft.selectedDates.size == 1) {
        AlertDialog(
            onDismissRequest = { actions.saveShift(OccupiedDatePolicy.CANCEL, false) },
            title = { Text("¿Reemplazar la guardia existente?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("La fecha elegida ya tiene una guardia. ¿Qué querés hacer?")
                    Button(
                        enabled = !isSaving,
                        onClick = { actions.saveShift(OccupiedDatePolicy.REPLACE, false) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Reemplazar") }
                    OutlinedButton(
                        enabled = !isSaving,
                        onClick = { secondShift = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Agregar segunda guardia") }
                    OutlinedButton(
                        enabled = !isSaving,
                        onClick = { actions.saveShift(OccupiedDatePolicy.CANCEL, false) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Cancelar") }
                }
            },
            confirmButton = {},
            dismissButton = {},
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
                OutlinedButton(enabled = !isSaving, onClick = { actions.saveShift(OccupiedDatePolicy.KEEP_OCCUPIED, false) }, modifier = Modifier.fillMaxWidth()) { Text("Agregar sólo en días libres") }
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
private fun SaveButton(saving: Boolean, label: String, onClick: () -> Unit) {
    PrimaryAction(label = label, onClick = onClick, enabled = !saving, working = saving)
}

private fun surfaceTitle(surface: ManagementSurface): String = when (surface) {
    ManagementSurface.NONE -> ""
    ManagementSurface.SETTINGS -> "Objetivos y horarios"
    ManagementSurface.OBJECTIVE_FORM -> "Objetivo"
    ManagementSurface.SCHEDULE_FORM -> "Horario"
    ManagementSurface.SHIFT_FORM -> "Guardias"
    ManagementSurface.DAY_OFF_FORM -> "Francos"
}

private fun monthLabel(month: YearMonth): String {
    val locale = Locale.forLanguageTag("es-AR")
    val name = month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
    return "$name de ${month.year}"
}
