package com.blackatsystems.miguardia.ui.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.shift.OccupiedDatePolicy
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.MiGuardiaButtonShape
import com.blackatsystems.miguardia.ui.components.MiGuardiaSpacing
import com.blackatsystems.miguardia.ui.components.PrimaryAction
import com.blackatsystems.miguardia.ui.components.SecondaryAction
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.components.destructiveButtonColors
import com.blackatsystems.miguardia.ui.components.AdvancedOptionsSection
import com.blackatsystems.miguardia.ui.components.ContextHelp
import com.blackatsystems.miguardia.ui.theme.vigiliaColors
import com.blackatsystems.miguardia.ui.worksetup.WorkCreationKind
import com.blackatsystems.miguardia.ui.worksetup.WorkCreationResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class V2ManualShiftLoadActions(
    val beginTemplateForPlace: (UUID) -> Unit = {},
    val updateNote: (String) -> Unit = {},
    val importNoteImages: (List<android.net.Uri>) -> Unit = {},
    val removeNoteImage: (UUID) -> Unit = {},
    val requestClear: () -> Unit = {},
    val confirmBatch: () -> Unit = {},
    val cancelBatch: () -> Unit = {},
    val beginCreation: (WorkCreationKind) -> Unit = {},
    val receiveCreationResult: (WorkCreationResult) -> Unit = {},
    val start: (WorkSetupState) -> Unit = {},
    val confirmDates: (Set<LocalDate>) -> Unit = {},
    val updateSelectedDates: (Set<LocalDate>) -> Unit = {},
    val requestDayOff: (Set<LocalDate>, Boolean) -> Unit = { _, _ -> },
    val confirmDayOff: () -> Unit = {},
    val cancelDayOff: () -> Unit = {},
    val chooseTemplate: (UUID) -> Unit = {},
    val updatePosition: (String) -> Unit = {},
    val requestReview: () -> Unit = {},
    val requestSave: () -> Unit = {},
    val confirmBackfill: () -> Unit = {},
    val cancelBackfill: () -> Unit = {},
    val chooseOccupiedPolicy: (OccupiedDatePolicy) -> Unit = {},
    val confirmWarnings: () -> Unit = {},
    val dismissWarnings: () -> Unit = {},
    val save: () -> Unit = {},
    val backToDateSelection: () -> Unit = {},
    val retry: () -> Unit = {},
    val cancel: () -> Unit = {},
    val discardIncompatible: () -> Unit = {},
    val clearMessage: () -> Unit = {},
    val consumeSuccess: (Int) -> Unit = {},
) {
    companion object {
        fun from(viewModel: V2ManualShiftLoadViewModel) = V2ManualShiftLoadActions(
            beginTemplateForPlace = viewModel::beginTemplateForPlace,
            updateNote = viewModel::updateNote,
            importNoteImages = viewModel::importNoteImages,
            removeNoteImage = viewModel::removeNoteImage,
            requestClear = viewModel::requestClear,
            confirmBatch = viewModel::confirmBatch,
            cancelBatch = viewModel::cancelBatch,
            beginCreation = viewModel::beginCreation,
            receiveCreationResult = viewModel::receiveCreationResult,
            start = viewModel::start,
            confirmDates = viewModel::confirmDates,
            updateSelectedDates = viewModel::updateSelectedDates,
            requestDayOff = viewModel::requestDayOff,
            confirmDayOff = viewModel::confirmDayOff,
            cancelDayOff = viewModel::cancelDayOff,
            chooseTemplate = viewModel::chooseTemplate,
            updatePosition = viewModel::updatePosition,
            requestReview = viewModel::requestReview,
            requestSave = viewModel::requestSave,
            confirmBackfill = viewModel::confirmBackfill,
            cancelBackfill = viewModel::cancelBackfill,
            chooseOccupiedPolicy = viewModel::chooseOccupiedPolicy,
            confirmWarnings = viewModel::confirmWarnings,
            dismissWarnings = viewModel::dismissWarnings,
            save = viewModel::save,
            backToDateSelection = viewModel::backToDateSelection,
            retry = viewModel::retry,
            cancel = viewModel::cancel,
            discardIncompatible = viewModel::discardIncompatible,
            clearMessage = viewModel::clearMessage,
            consumeSuccess = viewModel::consumeSuccess,
        )
    }
}

@Composable
fun V2ManualShiftLoadContent(
    state: V2ManualShiftLoadUiState,
    calendarSelectedDates: Set<LocalDate>,
    calendarSelectionConfirmed: Boolean,
    calendarContentReady: Boolean,
    actions: V2ManualShiftLoadActions,
    onConfirmCalendarSelection: () -> Unit,
    onModifyCalendarSelection: () -> Unit,
    modifier: Modifier = Modifier,
    showDateSelectionActions: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("v2-manual-shift-load"),
        verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.medium),
    ) {
        when (state.stage) {
            V2ManualShiftLoadStage.IDLE -> Unit
            V2ManualShiftLoadStage.SELECT_DATES -> if (showDateSelectionActions) {
                DateSelectionStep(
                    selectedDates = calendarSelectedDates,
                    selectionConfirmed = calendarSelectionConfirmed,
                    enabled = calendarContentReady && !state.isLoading && !state.isSaving && state.creationRequest == null && state.pendingDayOff == null,
                    onConfirm = onConfirmCalendarSelection,
                    onModify = onModifyCalendarSelection,
                    showActions = true,
                )
            }
            V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
            V2ManualShiftLoadStage.CONFIRM_BACKFILL,
            V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY,
            V2ManualShiftLoadStage.CONFIRM_WARNINGS,
            -> UnifiedTemplateSelectionStep(
                state = state,
                actions = actions,
                onModifyCalendarSelection = onModifyCalendarSelection,
            )
            V2ManualShiftLoadStage.REVIEW -> ReviewStep(
                state = state,
                actions = actions,
                onModifyCalendarSelection = onModifyCalendarSelection,
            )
        }

        if (state.stage == V2ManualShiftLoadStage.SELECT_DATES) {
            val dates = if (state.stage == V2ManualShiftLoadStage.SELECT_DATES) calendarSelectedDates else state.selectedDates
            val enabled = calendarContentReady && dates.isNotEmpty() && !state.isLoading && !state.isSaving &&
                state.creationRequest == null && state.pendingDayOff == null
            Text("Franco: no requiere lugar ni horario.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { actions.requestDayOff(dates, true) }, enabled = enabled,
                shape = MiGuardiaButtonShape,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("v2-mark-day-off")) {
                Text("Marcar Franco")
            }
            TextButton(onClick = { actions.requestDayOff(dates, false) }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("v2-remove-day-off")) {
                Text("Quitar Franco")
            }
        }

        if (state.isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MiGuardiaSpacing.small)
                    .testTag("v2-manual-shift-loading"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Text("Cargando horarios…", modifier = Modifier.padding(start = MiGuardiaSpacing.medium))
            }
        }

        state.errorMessage?.let { message ->
            PersistentMessage(message = message, onRetry = actions.retry)
        }
    }

    when (state.stage) {
        V2ManualShiftLoadStage.CONFIRM_BACKFILL -> BackfillDialog(state, actions)
        V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY -> OccupiedDatesDialog(state, actions)
        V2ManualShiftLoadStage.CONFIRM_WARNINGS -> WarningsDialog(state, actions)
        else -> Unit
    }
    state.batchPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = actions.cancelBatch,
            title = { Text(if (preview.action == com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAction.CLEAR) "Revisá la limpieza" else "Revisá los francos") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                preview.results.forEach { Text("${it.date.format(DATE_FORMATTER)}: ${it.explanation}") }
                Text("Cancelar no aplica ningún cambio.")
            } },
            confirmButton = {
                val enabled = !state.isSaving && preview.conflicts.isEmpty()
                if (preview.action == com.blackatsystems.miguardia.core.domain.repository.CalendarBatchAction.CLEAR) {
                    // Clearing removes content: tonal red, as in the other destructive confirmations.
                    FilledTonalButton(onClick = actions.confirmBatch, enabled = enabled,
                        shape = MiGuardiaButtonShape, colors = destructiveButtonColors()) { Text("Confirmar") }
                } else {
                    TextButton(onClick = actions.confirmBatch, enabled = enabled) { Text("Confirmar") }
                }
            },
            dismissButton = { TextButton(onClick = actions.cancelBatch, enabled = !state.isSaving) { Text("Cancelar") } },
            modifier = Modifier.testTag("manual-batch-preview"),
        )
    }
    state.pendingDayOff?.let { mark ->
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) actions.cancelDayOff() },
            title = { Text(if (mark) "Marcar Franco" else "Quitar Franco") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.medium)) {
                    Text(exactDateList(state.selectedDates))
                    Text(if (mark) "Se marcarán estos días como franco. Si alguno tiene una jornada, no se cambiará ninguno."
                        else "Sólo se quitará la marca de franco de estos días. Las jornadas y otras marcas se conservan.")
                    // "Reintentar" stays the dialog's confirm button, so the message has no own actions.
                    state.errorMessage?.let { PersistentMessage(message = it) }
                    if (state.isSaving) Text("Guardando…")
                }
            },
            confirmButton = {
                TextButton(onClick = actions.confirmDayOff, enabled = !state.isSaving,
                    modifier = Modifier.testTag("v2-confirm-day-off")) {
                    Text(if (state.errorMessage != null) "Reintentar" else "Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = actions.cancelDayOff, enabled = !state.isSaving) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun DateSelectionStep(
    selectedDates: Set<LocalDate>,
    selectionConfirmed: Boolean,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onModify: () -> Unit,
    showActions: Boolean,
) {
    Text(
        text = "Elegí uno o varios días",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = if (selectedDates.isEmpty()) {
            "Tocá directamente las fechas en la grilla mensual."
        } else if (selectedDates.size == 1) {
            "1 día seleccionado"
        } else {
            "${selectedDates.size} días seleccionados"
        },
        modifier = Modifier.testTag("v2-manual-selected-date-count"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (showActions) {
        PrimaryAction(
            label = "Terminar de elegir días",
            onClick = onConfirm,
            enabled = enabled && selectedDates.isNotEmpty() && !selectionConfirmed,
            modifier = Modifier.testTag("v2-manual-confirm-dates"),
        )
    }
    if (showActions && selectionConfirmed) {
        SecondaryAction(
            label = "Cambiar días",
            onClick = onModify,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("v2-manual-modify-confirmed-dates"),
        )
    }
}


@Composable
internal fun V2TemplateOptionCard(
    option: V2ManualShiftTemplateOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val description = buildString {
        append(option.objective.abbreviation)
        append(", ")
        append(option.workType.name)
        append(", de ")
        append(option.template.startTime.format(TIME_FORMATTER))
        append(" a ")
        append(option.template.endTime.format(TIME_FORMATTER))
        append(if (selected) ", seleccionado" else ", sin seleccionar")
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics {
                contentDescription = description
            }
            .testTag("v2-template-${option.template.id}"),
        color = if (selected) {
            MaterialTheme.vigiliaColors.selection
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            selected -> MaterialTheme.vigiliaColors.onSelection
            else -> MaterialTheme.colorScheme.onSurface
        },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            when {
                !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                selected -> MaterialTheme.vigiliaColors.active
                else -> MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MiGuardiaSpacing.medium, vertical = MiGuardiaSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.medium),
        ) {
            Box(
                Modifier
                    .width(7.dp)
                    .heightIn(min = 48.dp)
                    .background(Color(option.template.colorArgb), MaterialTheme.shapes.extraSmall),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.extraSmall)) {
                Text(option.objective.abbreviation, fontWeight = FontWeight.SemiBold)
                Text(option.workType.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${option.template.startTime.format(TIME_FORMATTER)}–" +
                        option.template.endTime.format(TIME_FORMATTER),
                )
                if (selected) {
                    Text(
                        "Seleccionado",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) MaterialTheme.vigiliaColors.active else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewStep(
    state: V2ManualShiftLoadUiState,
    actions: V2ManualShiftLoadActions,
    onModifyCalendarSelection: () -> Unit,
) {
    val option = state.selectedOption ?: return
    SectionCard(
        title = "Revisá antes de guardar",
        modifier = Modifier.testTag("v2-manual-preview"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small)) {
            Text(if (state.plannedDates.size == 1) "1 jornada nueva" else "${state.plannedDates.size} jornadas nuevas")
            Text("Fechas: ${exactDateList(state.plannedDates)}", modifier = Modifier.testTag("v2-manual-preview-dates"))
            Text("Lugar: ${option.objective.abbreviation}")
            Text("Tipo: ${option.workType.name}")
            Text(
                "Horario: ${option.template.startTime.format(TIME_FORMATTER)}–" +
                    option.template.endTime.format(TIME_FORMATTER),
            )
            if (option.template.endTime <= option.template.startTime) {
                Text("Termina al día siguiente", modifier = Modifier.testTag("v2-manual-next-day"))
            }
            state.position.trim().takeIf(String::isNotEmpty)?.let { Text("Puesto o función: $it") }
            when (state.occupiedPolicy) {
                OccupiedDatePolicy.REPLACE -> Text("Se reemplazarán sólo jornadas superpuestas y francos incompatibles. Se conservan las demás jornadas.")
                OccupiedDatePolicy.KEEP_OCCUPIED -> Text("Las fechas ocupadas se conservarán sin cambios.")
                OccupiedDatePolicy.ADD_SECOND_SHIFT -> if (state.occupiedDates.isNotEmpty()) {
                    Text("Las fechas ocupadas conservarán su jornada y sumarán una segunda.")
                }
                else -> Unit
            }
            if (state.omittedDates.isNotEmpty()) {
                Text("Se conservarán sin cambios: ${exactDateList(state.omittedDates)}")
            }
        }
    }
    PrimaryAction(
        label = when {
            state.isSaving -> "Guardando jornadas…"
            state.plannedDates.size == 1 -> "Guardar jornada"
            else -> "Guardar ${state.plannedDates.size} jornadas"
        },
        onClick = actions.save,
        // Disabled while saving through `working`, which also shows the progress indicator.
        enabled = state.plannedDates.isNotEmpty() && !state.isLoading,
        working = state.isSaving,
        modifier = Modifier.testTag("v2-manual-save"),
    )
    SecondaryAction(
        label = "Cambiar días",
        onClick = onModifyCalendarSelection,
        enabled = !state.isSaving && !state.isLoading,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BackfillDialog(state: V2ManualShiftLoadUiState, actions: V2ManualShiftLoadActions) {
    val configuredFrom = state.configuredFrom ?: return
    val backfillFrom = state.backfillFrom ?: return
    AlertDialog(
        onDismissRequest = actions.cancelBackfill,
        modifier = Modifier.testTag("v2-backfill-dialog"),
        title = { Text("Usar esta configuración en fechas anteriores") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "MiGuardia empezó a usar esta forma de trabajar desde ${configuredFrom.format(DATE_FORMATTER)}. " +
                        "¿Querés usar la misma configuración desde ${backfillFrom.format(DATE_FORMATTER)}?",
                )
                if (state.isSaving) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text("Extendiendo configuración…", modifier = Modifier.padding(start = MiGuardiaSpacing.small))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !state.isSaving, onClick = actions.confirmBackfill) { Text("Usar desde esa fecha") }
        },
        dismissButton = {
            TextButton(enabled = !state.isSaving, onClick = actions.cancelBackfill) { Text("Cancelar") }
        },
    )
}

@Composable
private fun OccupiedDatesDialog(state: V2ManualShiftLoadUiState, actions: V2ManualShiftLoadActions) {
    val single = state.selectedDates.size == 1
    AlertDialog(
        onDismissRequest = { actions.chooseOccupiedPolicy(OccupiedDatePolicy.CANCEL) },
        modifier = Modifier.testTag("v2-occupied-dialog"),
        title = { Text("Revisá los conflictos") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (single) {
                        "La fecha elegida ya tiene contenido. ¿Qué querés hacer?"
                    } else {
                        "Fechas con contenido: ${state.occupiedDates.sorted().joinToString { it.format(DATE_FORMATTER) }}."
                    },
                )
                FilledTonalButton(
                    enabled = !state.isSaving,
                    onClick = { actions.chooseOccupiedPolicy(OccupiedDatePolicy.REPLACE) },
                    shape = MiGuardiaButtonShape,
                    colors = destructiveButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sobrescribir") }
                state.conflictDetails.forEach { Text(it) }
                Text("Sobrescribir reemplaza sólo los intervalos incompatibles y el franco. Superponer conserva los registros existentes.")
                if (!single) {
                    OutlinedButton(
                        enabled = !state.isSaving,
                        onClick = { actions.chooseOccupiedPolicy(OccupiedDatePolicy.KEEP_OCCUPIED) },
                        shape = MiGuardiaButtonShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Agregar sólo en días libres") }
                }
                OutlinedButton(
                    enabled = !state.isSaving,
                    onClick = { actions.chooseOccupiedPolicy(OccupiedDatePolicy.ADD_SECOND_SHIFT) },
                    shape = MiGuardiaButtonShape,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Superponer") }
                OutlinedButton(
                    enabled = !state.isSaving,
                    onClick = { actions.chooseOccupiedPolicy(OccupiedDatePolicy.CANCEL) },
                    shape = MiGuardiaButtonShape,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cancelar") }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

@Composable
private fun WarningsDialog(state: V2ManualShiftLoadUiState, actions: V2ManualShiftLoadActions) {
    AlertDialog(
        onDismissRequest = actions.dismissWarnings,
        modifier = Modifier.testTag("v2-warning-dialog"),
        title = { Text("Revisá estas advertencias") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.warnings.forEach { warning -> Text("• $warning") }
            }
        },
        confirmButton = {
            TextButton(enabled = !state.isSaving, onClick = actions.confirmWarnings) {
                Text("Continuar igualmente")
            }
        },
        dismissButton = {
            TextButton(enabled = !state.isSaving, onClick = actions.dismissWarnings) {
                Text("Volver y corregir")
            }
        },
    )
}

private fun exactDateList(dates: Set<LocalDate>): String =
    dates.sorted().joinToString { it.format(DATE_FORMATTER) }

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
