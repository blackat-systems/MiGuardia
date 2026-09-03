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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.blackatsystems.miguardia.ui.components.AdvancedOptionsSection
import com.blackatsystems.miguardia.ui.components.ContextHelp
import com.blackatsystems.miguardia.ui.theme.vigiliaColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class V2ManualShiftLoadActions(
    val start: (WorkSetupState) -> Unit = {},
    val confirmDates: (Set<LocalDate>) -> Unit = {},
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
            start = viewModel::start,
            confirmDates = viewModel::confirmDates,
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
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("v2-manual-shift-load"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (state.stage) {
            V2ManualShiftLoadStage.IDLE -> Unit
            V2ManualShiftLoadStage.SELECT_DATES -> DateSelectionStep(
                selectedDates = calendarSelectedDates,
                selectionConfirmed = calendarSelectionConfirmed,
                enabled = calendarContentReady && !state.isLoading && !state.isSaving,
                onConfirm = onConfirmCalendarSelection,
                onModify = onModifyCalendarSelection,
            )
            V2ManualShiftLoadStage.CHOOSE_TEMPLATE,
            V2ManualShiftLoadStage.CONFIRM_BACKFILL,
            V2ManualShiftLoadStage.CHOOSE_OCCUPIED_POLICY,
            V2ManualShiftLoadStage.CONFIRM_WARNINGS,
            -> TemplateSelectionStep(
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

        if (state.isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("v2-manual-shift-loading"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Text("Cargando horarios…", modifier = Modifier.padding(start = 12.dp))
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
}

@Composable
private fun DateSelectionStep(
    selectedDates: Set<LocalDate>,
    selectionConfirmed: Boolean,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onModify: () -> Unit,
) {
    Text(
        text = "Elegí uno o varios días",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
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
    Button(
        onClick = onConfirm,
        enabled = enabled && selectedDates.isNotEmpty() && !selectionConfirmed,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("v2-manual-confirm-dates"),
    ) {
        Text("Terminar de elegir días")
    }
    if (selectionConfirmed) {
        OutlinedButton(
            onClick = onModify,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("v2-manual-modify-confirmed-dates"),
        ) {
            Text("Cambiar días")
        }
    }
}

@Composable
private fun TemplateSelectionStep(
    state: V2ManualShiftLoadUiState,
    actions: V2ManualShiftLoadActions,
    onModifyCalendarSelection: () -> Unit,
) {
    Text(
        text = "Elegí lugar, tipo y horario",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = exactDateList(state.selectedDates),
        modifier = Modifier.testTag("v2-manual-exact-dates"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.templateOptions.forEach { option ->
            V2TemplateOptionCard(
                option = option,
                selected = option.template.id == state.selectedTemplateId,
                enabled = !state.isLoading && !state.isSaving,
                onClick = { actions.chooseTemplate(option.template.id) },
            )
        }
    }
    if (state.templateOptions.isNotEmpty()) {
        Button(
            onClick = actions.requestSave,
            enabled = state.selectedTemplateId != null && !state.isLoading && !state.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag("v2-manual-save-direct"),
        ) {
            Text(if (state.selectedDates.size == 1) "Guardar jornada" else "Guardar ${state.selectedDates.size} jornadas")
        }
        AdvancedOptionsSection(
            help = ContextHelp(
                title = "Detalles antes de guardar",
                whatItDoes = "Permite agregar un puesto o ver todas las fechas antes de guardar.",
                howToUseIt = "No hace falta abrirlo para una carga normal. MiGuardia igual controla días ocupados y advertencias.",
                example = "Podés escribir Puesto 3 o revisar una carga de diez días antes de confirmarla.",
            ),
        ) {
            OutlinedTextField(
                value = state.position,
                onValueChange = actions.updatePosition,
                enabled = !state.isLoading && !state.isSaving,
                label = { Text("Puesto o función (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("v2-manual-position"),
            )
            OutlinedButton(
                onClick = actions.requestReview,
                enabled = state.selectedTemplateId != null && !state.isLoading && !state.isSaving,
                modifier = Modifier.fillMaxWidth().testTag("v2-manual-review"),
            ) { Text("Ver fechas y detalles") }
        }
    }
    TextButton(
        onClick = onModifyCalendarSelection,
        enabled = !state.isLoading && !state.isSaving,
        modifier = Modifier.fillMaxWidth().testTag("v2-manual-modify-dates"),
    ) {
        Text("Cambiar días")
    }
}

@Composable
private fun V2TemplateOptionCard(
    option: V2ManualShiftTemplateOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colorHex = (option.template.colorArgb and 0x00FFFFFF)
        .toString(16)
        .padStart(6, '0')
        .uppercase(Locale.ROOT)
    val description = buildString {
        append(option.objective.abbreviation)
        append(", ")
        append(option.workType.name)
        append(", de ")
        append(option.template.startTime.format(TIME_FORMATTER))
        append(" a ")
        append(option.template.endTime.format(TIME_FORMATTER))
        append(", color #")
        append(colorHex)
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
            MaterialTheme.vigiliaColors.active.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.vigiliaColors.active else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .width(7.dp)
                    .heightIn(min = 48.dp)
                    .background(Color(option.template.colorArgb), MaterialTheme.shapes.extraSmall),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(option.objective.abbreviation, fontWeight = FontWeight.Bold)
                Text(option.workType.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${option.template.startTime.format(TIME_FORMATTER)}–" +
                        option.template.endTime.format(TIME_FORMATTER),
                )
                Text(
                    "Color #$colorHex",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (selected) {
                    Text(
                        "Seleccionado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.vigiliaColors.active,
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("v2-manual-preview"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Revisá antes de guardar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                OccupiedDatePolicy.REPLACE -> Text("Las jornadas existentes de esas fechas se reemplazarán.")
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
    Button(
        onClick = actions.save,
        enabled = state.plannedDates.isNotEmpty() && !state.isSaving && !state.isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("v2-manual-save"),
    ) {
        if (state.isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Text("Guardando jornadas…", modifier = Modifier.padding(start = 8.dp))
        } else {
            Text(if (state.plannedDates.size == 1) "Guardar jornada" else "Guardar ${state.plannedDates.size} jornadas")
        }
    }
    OutlinedButton(
        onClick = onModifyCalendarSelection,
        enabled = !state.isSaving && !state.isLoading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Cambiar días")
    }
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
                        Text("Extendiendo configuración…", modifier = Modifier.padding(start = 10.dp))
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
        title = { Text(if (single) "Ya hay una jornada" else "Hay fechas ocupadas") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (single) {
                        "La fecha elegida ya tiene una jornada. ¿Qué querés hacer?"
                    } else {
                        "Ya tienen jornadas: ${state.occupiedDates.sorted().joinToString { it.format(DATE_FORMATTER) }}."
                    },
                )
                Button(
                    enabled = !state.isSaving,
                    onClick = { actions.chooseOccupiedPolicy(OccupiedDatePolicy.REPLACE) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (single) "Reemplazar" else "Reemplazar en las fechas elegidas") }
                if (!single) {
                    OutlinedButton(
                        enabled = !state.isSaving,
                        onClick = { actions.chooseOccupiedPolicy(OccupiedDatePolicy.KEEP_OCCUPIED) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Agregar sólo en días libres") }
                }
                OutlinedButton(
                    enabled = !state.isSaving,
                    onClick = { actions.chooseOccupiedPolicy(OccupiedDatePolicy.ADD_SECOND_SHIFT) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (single) "Agregar segunda jornada" else "Agregar segunda jornada en las ocupadas") }
                OutlinedButton(
                    enabled = !state.isSaving,
                    onClick = { actions.chooseOccupiedPolicy(OccupiedDatePolicy.CANCEL) },
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
