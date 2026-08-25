package com.blackatsystems.miguardia.ui.management

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.core.domain.work.normalizeOptionalWorkText
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.theme.vigiliaColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class V2ShiftEditActions(
    val resume: (WorkSetupState) -> Unit = {},
    val inspectDay: (WorkSetupState, LocalDate) -> Unit = { _, _ -> },
    val retryInspection: () -> Unit = {},
    val clearInspection: () -> Unit = {},
    val beginDayEditing: () -> Unit = {},
    val editShift: (UUID) -> Unit = {},
    val requestDelete: (UUID) -> Unit = {},
    val editOnlyThisOccurrence: () -> Unit = {},
    val deleteOnlyThisOccurrence: () -> Unit = {},
    val cancelScopeChoice: () -> Unit = {},
    val changeSeriesFrom: (UUID, LocalDate) -> Unit = { _, _ -> },
    val finalizeSeriesFrom: (UUID, LocalDate) -> Unit = { _, _ -> },
    val handoffToRecurring: () -> Unit = {},
    val chooseHistoricalTemplate: () -> Unit = {},
    val chooseTemplate: (UUID) -> Unit = {},
    val updatePosition: (String) -> Unit = {},
    val requestReview: () -> Unit = {},
    val confirmWarnings: () -> Unit = {},
    val dismissWarnings: () -> Unit = {},
    val save: () -> Unit = {},
    val confirmDelete: () -> Unit = {},
    val cancelDelete: () -> Unit = {},
    val back: () -> Unit = {},
    val confirmDiscard: () -> Unit = {},
    val cancelDiscard: () -> Unit = {},
    val cancelToDetail: () -> Unit = {},
    val retry: () -> Unit = {},
    val discardIncompatible: () -> Unit = {},
    val clearMessage: () -> Unit = {},
    val consumeSuccess: (Int) -> Unit = {},
) {
    companion object {
        fun from(viewModel: V2ShiftEditViewModel) = V2ShiftEditActions(
            resume = viewModel::resume,
            inspectDay = viewModel::inspectDay,
            retryInspection = viewModel::retryInspection,
            clearInspection = viewModel::clearInspection,
            beginDayEditing = viewModel::beginDayEditing,
            editShift = viewModel::editShift,
            requestDelete = viewModel::requestDelete,
            editOnlyThisOccurrence = viewModel::editOnlyThisOccurrence,
            deleteOnlyThisOccurrence = viewModel::deleteOnlyThisOccurrence,
            cancelScopeChoice = viewModel::cancelScopeChoice,
            handoffToRecurring = viewModel::handoffToRecurring,
            chooseHistoricalTemplate = viewModel::chooseHistoricalTemplate,
            chooseTemplate = viewModel::chooseTemplate,
            updatePosition = viewModel::updatePosition,
            requestReview = viewModel::requestReview,
            confirmWarnings = viewModel::confirmWarnings,
            dismissWarnings = viewModel::dismissWarnings,
            save = viewModel::save,
            confirmDelete = viewModel::confirmDelete,
            cancelDelete = viewModel::cancelDelete,
            back = viewModel::back,
            confirmDiscard = viewModel::confirmDiscard,
            cancelDiscard = viewModel::cancelDiscard,
            cancelToDetail = viewModel::cancelToDetail,
            retry = viewModel::retry,
            discardIncompatible = viewModel::discardIncompatible,
            clearMessage = viewModel::clearMessage,
            consumeSuccess = viewModel::consumeSuccess,
        )
    }
}

@Composable
fun V2DayEditEntry(
    state: V2ShiftEditUiState,
    date: LocalDate,
    onBegin: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inspection = if (state.date == date) state.inspectionState else V2ShiftDayInspectionState.LOADING
    when (inspection) {
        V2ShiftDayInspectionState.IDLE,
        V2ShiftDayInspectionState.LOADING,
        -> NeutralLoading(
            text = "Identificando jornadas editables…",
            modifier = modifier.testTag("v2-shift-identification-loading"),
        )

        V2ShiftDayInspectionState.ERROR -> Column(
            modifier = modifier
                .fillMaxWidth()
                .testTag("v2-shift-identification-error"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PersistentMessage(
                message = state.errorMessage ?: "No pudimos identificar las jornadas de este día.",
                onRetry = onRetry,
            )
        }

        V2ShiftDayInspectionState.CONTENT -> if (state.hasEditableRows) {
            Button(
                onClick = onBegin,
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("v2-edit-day-action"),
            ) {
                Text("Editar este día")
            }
        } else {
            Text(
                text = "Este día no contiene jornadas V2 editables.",
                modifier = modifier.testTag("v2-no-editable-shifts"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun V2ShiftEditSurfaceHost(
    state: V2ShiftEditUiState,
    actions: V2ShiftEditActions,
) {
    if (!state.isBlocking) return

    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val navigateBack: () -> Unit = {
        if (imeVisible) {
            keyboardController?.hide()
        } else {
            actions.back()
        }
    }
    BackHandler(onBack = navigateBack)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("v2-shift-edit-surface"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            EditSurfaceHeader(state = state, onBack = navigateBack)
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp)
                    .testTag("v2-shift-edit-scroll"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.errorMessage?.let { message ->
                    PersistentMessage(
                        message = message,
                        onDismiss = actions.clearMessage,
                        onRetry = actions.retry,
                    )
                }

                when (state.stage) {
                    V2ShiftEditStage.IDLE -> Unit
                    V2ShiftEditStage.DAY_ACTIONS,
                    V2ShiftEditStage.CHOOSE_EDIT_SCOPE,
                    V2ShiftEditStage.CHOOSE_DELETE_SCOPE,
                    V2ShiftEditStage.CONFIRM_DELETE,
                    -> DayActionsStep(state = state, actions = actions)

                    V2ShiftEditStage.EDIT_FORM,
                    V2ShiftEditStage.CONFIRM_WARNINGS,
                    V2ShiftEditStage.CONFIRM_DISCARD,
                    -> EditorStep(state = state, actions = actions)

                    V2ShiftEditStage.REVIEW -> ReviewStep(state = state, actions = actions)
                }

                if (state.isLoading) {
                    NeutralLoading(
                        text = "Releyendo la información…",
                        modifier = Modifier.testTag("v2-shift-edit-loading"),
                    )
                }
            }
        }
    }

    when (state.stage) {
        V2ShiftEditStage.CONFIRM_WARNINGS -> WarningDialog(state, actions)
        V2ShiftEditStage.CHOOSE_EDIT_SCOPE -> EditScopeDialog(state, actions)
        V2ShiftEditStage.CHOOSE_DELETE_SCOPE -> DeleteScopeDialog(state, actions)
        V2ShiftEditStage.CONFIRM_DELETE -> DeleteDialog(state, actions)
        V2ShiftEditStage.CONFIRM_DISCARD -> DiscardDialog(state, actions)
        else -> Unit
    }
}

@Composable
private fun EditSurfaceHeader(state: V2ShiftEditUiState, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onBack,
            enabled = !state.isLoading && !state.isSaving,
            modifier = Modifier.testTag("v2-shift-edit-back"),
        ) {
            Text(if (state.stage == V2ShiftEditStage.DAY_ACTIONS) "Cerrar" else "Volver")
        }
        Text(
            text = when (state.stage) {
                V2ShiftEditStage.DAY_ACTIONS,
                V2ShiftEditStage.CHOOSE_EDIT_SCOPE,
                V2ShiftEditStage.CHOOSE_DELETE_SCOPE,
                V2ShiftEditStage.CONFIRM_DELETE,
                -> "Jornadas del día"

                V2ShiftEditStage.REVIEW -> "Revisar cambio"
                else -> "Editar jornada"
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DayActionsStep(state: V2ShiftEditUiState, actions: V2ShiftEditActions) {
    Text(
        text = state.date?.fullDate().orEmpty(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.testTag("v2-shift-day-actions-date"),
    )
    Text(
        "Elegí exactamente cuál jornada querés corregir o eliminar.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.dayRows.forEach { row ->
        DayActionCard(
            row = row,
            enabled = state.stage == V2ShiftEditStage.DAY_ACTIONS && !state.isLoading && !state.isSaving,
            onEdit = { actions.editShift(row.shift.id) },
            onDelete = { actions.requestDelete(row.shift.id) },
        )
    }
    OutlinedButton(
        onClick = actions.cancelToDetail,
        enabled = state.stage == V2ShiftEditStage.DAY_ACTIONS && !state.isLoading && !state.isSaving,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("v2-shift-back-to-detail"),
    ) {
        Text("Volver al detalle")
    }
}

@Composable
private fun DayActionCard(
    row: V2ShiftEditDayRow,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val identity = "Jornada ${row.ordinal} de ${row.total}"
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$identity. ${row.shift.visibleSummary(row.snapshot.workTypeNameSnapshot)}" }
            .testTag("v2-shift-row-${row.shift.id}"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(identity, fontWeight = FontWeight.Bold)
            HistoricalSummary(write = V2ShiftWrite(row.shift, row.snapshot))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("v2-edit-shift-${row.shift.id}"),
                ) {
                    Text("Editar jornada")
                }
                TextButton(
                    onClick = onDelete,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("v2-delete-shift-${row.shift.id}"),
                ) {
                    Text("Eliminar jornada", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun EditorStep(state: V2ShiftEditUiState, actions: V2ShiftEditActions) {
    val original = state.originalWrite
    if (original == null) {
        if (!state.isLoading) Text("No pudimos encontrar la jornada seleccionada.")
        return
    }
    val editable = state.stage == V2ShiftEditStage.EDIT_FORM && !state.isLoading && !state.isSaving
    Text("Fecha fija", style = MaterialTheme.typography.labelLarge)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Fecha fija. ${original.shift.localStartDate.fullDate()}" }
            .testTag("v2-shift-fixed-date"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            original.shift.localStartDate.fullDate(),
            modifier = Modifier.padding(14.dp),
            fontWeight = FontWeight.SemiBold,
        )
    }
    Text("Jornada original", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    SummaryCard(
        modifier = Modifier.testTag("v2-shift-original-summary"),
    ) {
        HistoricalSummary(original)
    }
    Text("Plantilla", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        "Podés mantener la fotografía histórica o elegir una plantilla activa compatible.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.templateOptions.none(V2ShiftEditTemplateOption::matchesHistoricalSelection)) {
            HistoricalTemplateOption(
                original = original,
                selected = state.usesHistoricalTemplate,
                enabled = editable,
                onClick = actions.chooseHistoricalTemplate,
            )
        }
        state.templateOptions.forEach { option ->
            TemplateOption(
                option = option,
                selected = if (option.matchesHistoricalSelection) {
                    state.usesHistoricalTemplate
                } else {
                    !state.usesHistoricalTemplate && state.selectedTemplateId == option.template.id
                },
                enabled = editable,
                onClick = { actions.chooseTemplate(option.template.id) },
            )
        }
    }
    OutlinedTextField(
        value = state.position,
        onValueChange = actions.updatePosition,
        enabled = editable,
        label = { Text("Puesto o función (opcional)") },
        supportingText = { Text("La fecha y la jornada elegida no cambian.") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("v2-shift-edit-position"),
    )
    Button(
        onClick = actions.requestReview,
        enabled = editable && state.hasUnconfirmedChanges && state.hasCoherentSelection(),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("v2-shift-request-review"),
    ) {
        Text("Revisar cambios")
    }
}

@Composable
private fun HistoricalTemplateOption(
    original: V2ShiftWrite,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SelectableTemplateSurface(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        contentDescription = "Mantener plantilla histórica. ${original.shift.visibleSummary(original.snapshot.workTypeNameSnapshot)}",
        testTag = "v2-shift-historical-template-${original.snapshot.templateId}",
    ) {
        Text("Mantener plantilla histórica", fontWeight = FontWeight.Bold)
        Text(
            "${original.shift.objectiveNameSnapshot} " +
                "(${original.shift.objectiveAbbreviationSnapshot})",
        )
        original.shift.objectiveAddressSnapshot?.let { Text(it) }
        Text(original.snapshot.workTypeNameSnapshot)
        Text(original.shift.timeRange())
        Text(
            "No se reactiva ni se ofrece para otras jornadas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TemplateOption(
    option: V2ShiftEditTemplateOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colorHex = option.template.colorHex()
    SelectableTemplateSurface(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        contentDescription = buildString {
            append(option.objective.fullName)
            append(" (")
            append(option.objective.abbreviation)
            append(')')
            option.objective.address?.let { address ->
                append(", ")
                append(address)
            }
            append(", ")
            append(option.workType.name)
            append(", ")
            append(option.template.startTime.format(TIME_FORMATTER))
            append(" a ")
            append(option.template.endTime.format(TIME_FORMATTER))
            append(", color #")
            append(colorHex)
            append(if (selected) ", seleccionado" else ", sin seleccionar")
        },
        testTag = "v2-shift-template-${option.template.id}",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(7.dp)
                    .height(52.dp)
                    .background(Color(option.template.colorArgb), MaterialTheme.shapes.extraSmall),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${option.objective.fullName} (${option.objective.abbreviation})",
                    fontWeight = FontWeight.Bold,
                )
                option.objective.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(option.workType.name, fontWeight = FontWeight.SemiBold)
                Text("${option.template.startTime.format(TIME_FORMATTER)}–${option.template.endTime.format(TIME_FORMATTER)}")
                Text("Color #$colorHex", style = MaterialTheme.typography.bodySmall)
                if (selected) Text("Seleccionada", color = MaterialTheme.vigiliaColors.active)
            }
        }
    }
}

@Composable
private fun SelectableTemplateSurface(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    testTag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag),
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
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
private fun ReviewStep(state: V2ShiftEditUiState, actions: V2ShiftEditActions) {
    val original = state.originalWrite ?: return
    Text("Revisá antes de guardar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    SummaryCard(modifier = Modifier.testTag("v2-shift-final-summary")) {
        Text("Fecha fija: ${original.shift.localStartDate.fullDate()}", fontWeight = FontWeight.Bold)
        val option = state.selectedOption
        if (state.usesHistoricalTemplate || option == null) {
            Text(
                "Lugar: ${original.shift.objectiveNameSnapshot} " +
                    "(${original.shift.objectiveAbbreviationSnapshot})",
            )
            Text("Tipo: ${original.snapshot.workTypeNameSnapshot}")
            Text("Horario: ${original.shift.timeRange()}")
            Text("Color: #${original.shift.colorArgbSnapshot.colorHex()}")
            original.shift.objectiveAddressSnapshot?.let { Text("Dirección: $it") }
        } else {
            Text("Lugar: ${option.objective.fullName} (${option.objective.abbreviation})")
            Text("Tipo: ${option.workType.name}")
            Text("Horario: ${option.template.startTime.format(TIME_FORMATTER)}–${option.template.endTime.format(TIME_FORMATTER)}")
            Text("Color: #${option.template.colorHex()}")
            option.objective.address?.let { Text("Dirección: $it") }
        }
        Text("Puesto o función: ${normalizeOptionalWorkText(state.position) ?: "Sin puesto"}")
        if (state.warnings.isNotEmpty()) {
            Text("Advertencias confirmadas: ${state.warnings.size}", color = MaterialTheme.colorScheme.tertiary)
        }
    }
    Button(
        onClick = actions.save,
        enabled = state.reviewFingerprint != null && !state.isLoading && !state.isSaving,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("v2-shift-save"),
    ) {
        if (state.isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Guardando cambios…")
        } else {
            Text("Guardar cambios")
        }
    }
    OutlinedButton(
        onClick = actions.back,
        enabled = !state.isLoading && !state.isSaving,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Volver y corregir")
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            content = content,
        )
    }
}

@Composable
private fun HistoricalSummary(write: V2ShiftWrite?) {
    if (write == null) return
    Text(
        "Lugar: ${write.shift.objectiveNameSnapshot} " +
            "(${write.shift.objectiveAbbreviationSnapshot})",
    )
    write.shift.objectiveAddressSnapshot?.let { Text("Dirección: $it") }
    Text("Tipo: ${write.snapshot.workTypeNameSnapshot}")
    Text("Horario: ${write.shift.timeRange()}")
    Text("Color: #${write.shift.colorArgbSnapshot.colorHex()}")
    write.shift.position?.let { Text("Puesto o función: $it") }
}

@Composable
private fun WarningDialog(state: V2ShiftEditUiState, actions: V2ShiftEditActions) {
    AlertDialog(
        onDismissRequest = actions.dismissWarnings,
        modifier = Modifier.testTag("v2-shift-warning-dialog"),
        title = { Text("Revisá estas advertencias") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.warnings.forEach { warning -> Text("• $warning") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = actions.confirmWarnings,
                enabled = !state.isLoading && !state.isSaving,
                modifier = Modifier.testTag("v2-shift-confirm-warnings"),
            ) {
                Text("Continuar igualmente")
            }
        },
        dismissButton = {
            TextButton(
                onClick = actions.dismissWarnings,
                enabled = !state.isSaving,
            ) {
                Text("Volver y corregir")
            }
        },
    )
}

@Composable
private fun EditScopeDialog(state: V2ShiftEditUiState, actions: V2ShiftEditActions) {
    val occurrence = state.recurringOccurrence ?: return
    val date = state.originalWrite?.shift?.localStartDate ?: return
    AlertDialog(
        onDismissRequest = actions.cancelScopeChoice,
        modifier = Modifier.testTag("v2-shift-edit-scope-dialog"),
        title = { Text("¿Qué querés cambiar?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(date.fullDate(), fontWeight = FontWeight.Bold)
                Text("Esta jornada pertenece a un plan recurrente.")
                Text("Cambiar sólo esta jornada la dejará personalizada y no modificará las demás.")
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = actions.editOnlyThisOccurrence,
                    modifier = Modifier.testTag("v2-shift-edit-only-this"),
                ) { Text("Cambiar sólo esta jornada") }
                TextButton(
                    onClick = {
                        actions.handoffToRecurring()
                        actions.changeSeriesFrom(occurrence.planId, date)
                    },
                    modifier = Modifier.testTag("v2-shift-edit-from-date"),
                ) { Text("Cambiar desde esta fecha") }
            }
        },
        dismissButton = {
            TextButton(onClick = actions.cancelScopeChoice) { Text("Cancelar") }
        },
    )
}

@Composable
private fun DeleteScopeDialog(state: V2ShiftEditUiState, actions: V2ShiftEditActions) {
    val occurrence = state.recurringOccurrence ?: return
    val date = state.originalWrite?.shift?.localStartDate ?: return
    AlertDialog(
        onDismissRequest = actions.cancelScopeChoice,
        modifier = Modifier.testTag("v2-shift-delete-scope-dialog"),
        title = { Text("¿Qué querés eliminar?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(date.fullDate(), fontWeight = FontWeight.Bold)
                Text("Eliminar sólo esta jornada conservará una exclusión para que no reaparezca.")
                Text("Finalizar desde esta fecha conservará pasado, personalizaciones, notas, avisos y estados protegidos.")
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = actions.deleteOnlyThisOccurrence,
                    modifier = Modifier.testTag("v2-shift-delete-only-this"),
                ) { Text("Eliminar sólo esta jornada", color = MaterialTheme.colorScheme.error) }
                TextButton(
                    onClick = {
                        actions.handoffToRecurring()
                        actions.finalizeSeriesFrom(occurrence.planId, date)
                    },
                    modifier = Modifier.testTag("v2-shift-finalize-from-date"),
                ) { Text("Finalizar desde esta fecha", color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = {
            TextButton(onClick = actions.cancelScopeChoice) { Text("Cancelar") }
        },
    )
}

@Composable
private fun DeleteDialog(state: V2ShiftEditUiState, actions: V2ShiftEditActions) {
    val original = state.originalWrite
    AlertDialog(
        onDismissRequest = actions.cancelDelete,
        modifier = Modifier.testTag("v2-shift-delete-dialog"),
        title = { Text("Eliminar jornada") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (original != null) {
                    Text(original.shift.localStartDate.fullDate(), fontWeight = FontWeight.Bold)
                    HistoricalSummary(original)
                } else {
                    Text("Releyendo la jornada exacta…")
                }
                Text("Se eliminará solamente esta jornada. Las demás se conservarán.")
                if (state.recurringOccurrence != null) {
                    Text("La fecha quedará excluida del plan y no reaparecerá en una revisión futura.")
                }
                Text("Esta acción no se puede deshacer.", color = MaterialTheme.colorScheme.error)
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.isSaving || state.isLoading) {
                    NeutralLoading(if (state.isSaving) "Eliminando jornada…" else "Releyendo jornada…")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = actions.confirmDelete,
                enabled = original != null && !state.isLoading && !state.isSaving,
                modifier = Modifier.testTag("v2-shift-confirm-delete"),
            ) {
                Text("Eliminar esta jornada", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(
                onClick = actions.cancelDelete,
                enabled = !state.isLoading && !state.isSaving,
            ) {
                Text("Conservar jornada")
            }
        },
    )
}

@Composable
private fun DiscardDialog(state: V2ShiftEditUiState, actions: V2ShiftEditActions) {
    AlertDialog(
        onDismissRequest = actions.cancelDiscard,
        modifier = Modifier.testTag("v2-shift-discard-dialog"),
        title = { Text("Descartar cambios") },
        text = { Text("Hay cambios sin guardar. La jornada original se conservará completa.") },
        confirmButton = {
            TextButton(
                onClick = actions.confirmDiscard,
                enabled = !state.isSaving,
                modifier = Modifier.testTag("v2-shift-confirm-discard"),
            ) {
                Text("Descartar")
            }
        },
        dismissButton = {
            TextButton(onClick = actions.cancelDiscard, enabled = !state.isSaving) {
                Text("Seguir editando")
            }
        },
    )
}

@Composable
private fun NeutralLoading(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun V2ShiftEditUiState.hasCoherentSelection(): Boolean {
    if (originalWrite == null || selectedTemplateId == null) return false
    return usesHistoricalTemplate || templateOptions.any { it.template.id == selectedTemplateId }
}

private fun Shift.visibleSummary(workTypeName: String?): String = buildString {
    append(objectiveAbbreviationSnapshot)
    if (workTypeName != null) {
        append(", ")
        append(workTypeName)
    }
    append(", ")
    append(timeRange())
}

private fun Shift.timeRange(): String =
    "${startTimeSnapshot.format(TIME_FORMATTER)}–${endTimeSnapshot.format(TIME_FORMATTER)}"

private fun LocalDate.fullDate(): String = format(FULL_DATE_FORMATTER)

private fun Int.colorHex(): String = (this and 0x00FFFFFF)
    .toString(16)
    .padStart(6, '0')
    .uppercase(Locale.ROOT)

private fun com.blackatsystems.miguardia.core.domain.work.WorkTemplate.colorHex(): String = colorArgb.colorHex()

private val FULL_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' uuuu", Locale.forLanguageTag("es-AR"))
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
