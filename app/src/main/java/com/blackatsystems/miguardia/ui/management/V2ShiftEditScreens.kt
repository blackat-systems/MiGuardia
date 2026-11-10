package com.blackatsystems.miguardia.ui.management

import com.blackatsystems.miguardia.ui.EventTimeRangeStyle
import com.blackatsystems.miguardia.ui.formatEventTimeRange
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.blackatsystems.miguardia.ui.components.MiGuardiaButtonShape
import com.blackatsystems.miguardia.ui.components.MiGuardiaSpacing
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.PrimaryAction
import com.blackatsystems.miguardia.ui.components.SecondaryAction
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.components.SurfaceHeader
import com.blackatsystems.miguardia.ui.components.destructiveButtonColors
import com.blackatsystems.miguardia.ui.components.vigiliaScreenBackground
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
    val addShift: (LocalDate) -> Unit = {},
    val markDayOff: (LocalDate) -> Unit = {},
    val markRetainer: (LocalDate) -> Unit = {},
    val editOnlyThisOccurrence: () -> Unit = {},
    val deleteOnlyThisOccurrence: () -> Unit = {},
    val cancelScopeChoice: () -> Unit = {},
    val changeSeriesFrom: (UUID, LocalDate) -> Unit = { _, _ -> },
    val finalizeSeriesFrom: (UUID, LocalDate) -> Unit = { _, _ -> },
    val handoffToRecurring: () -> Unit = {},
    val correctActual: (UUID) -> Unit = {},
    val returnActualToPlanned: (UUID) -> Unit = {},
    val handoffToActual: () -> Unit = {},
    val reportActualHandoffUnavailable: () -> Unit = {},
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
            handoffToActual = viewModel::handoffToActual,
            reportActualHandoffUnavailable = viewModel::reportActualHandoffUnavailable,
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
    label: String = "Editar este día",
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
            verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
        ) {
            PersistentMessage(
                message = state.errorMessage ?: "No pudimos identificar las jornadas de este día.",
                onRetry = onRetry,
            )
        }

        V2ShiftDayInspectionState.CONTENT -> {
            SecondaryAction(
                label = label,
                onClick = onBegin,
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("v2-edit-day-action"),
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
                .vigiliaScreenBackground()
                .safeDrawingPadding(),
        ) {
            EditSurfaceHeader(state = state, onBack = navigateBack)
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(MiGuardiaSpacing.large)
                    .testTag("v2-shift-edit-scroll"),
                verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.medium),
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

/** Test tag of the common header; its navigation button is the only "Cerrar"/"Volver" inside it. */
internal const val V2_SHIFT_EDIT_HEADER_TAG = "v2-shift-edit-header"

@Composable
private fun EditSurfaceHeader(state: V2ShiftEditUiState, onBack: () -> Unit) {
    // Common header (button on the right), disabled while reading or saving as before.
    val busy = state.isLoading || state.isSaving
    SurfaceHeader(
        title = when (state.stage) {
            V2ShiftEditStage.DAY_ACTIONS,
            V2ShiftEditStage.CHOOSE_EDIT_SCOPE,
            V2ShiftEditStage.CHOOSE_DELETE_SCOPE,
            V2ShiftEditStage.CONFIRM_DELETE,
            -> "Jornadas del día"

            V2ShiftEditStage.REVIEW -> "Revisar cambio"
            else -> "Editar jornada"
        },
        navigationLabel = if (state.stage == V2ShiftEditStage.DAY_ACTIONS) "Cerrar" else "Volver",
        onNavigation = onBack,
        navigationEnabled = !busy,
        navigationTestTag = "v2-shift-edit-back",
        modifier = Modifier.testTag(V2_SHIFT_EDIT_HEADER_TAG),
    )
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
        if (state.dayRows.isEmpty()) {
            "Todavía no hay jornadas. Podés agregar una o registrar el estado de este día."
        } else {
            "Cada acción de edición o eliminación afecta únicamente a la jornada indicada."
        },
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
    state.date?.let { date ->
        SecondaryAction(
            label = "Agregar jornada",
            onClick = { actions.addShift(date) },
            enabled = state.stage == V2ShiftEditStage.DAY_ACTIONS && !state.isLoading && !state.isSaving,
            modifier = Modifier.fillMaxWidth().testTag("v2-add-shift"),
        )
        SecondaryAction(
            label = "Marcar este día como franco",
            onClick = { actions.markDayOff(date) },
            enabled = state.stage == V2ShiftEditStage.DAY_ACTIONS && !state.isLoading && !state.isSaving,
            modifier = Modifier.fillMaxWidth().testTag("v2-mark-day-off"),
        )
        SecondaryAction(
            label = "Marcar este día como retén",
            onClick = { actions.markRetainer(date) },
            enabled = state.stage == V2ShiftEditStage.DAY_ACTIONS && !state.isLoading && !state.isSaving,
            modifier = Modifier.fillMaxWidth().testTag("v2-mark-retainer"),
        )
    }
    SecondaryAction(
        label = "Volver al detalle",
        onClick = actions.cancelToDetail,
        enabled = state.stage == V2ShiftEditStage.DAY_ACTIONS && !state.isLoading && !state.isSaving,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("v2-shift-back-to-detail"),
    )
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
            modifier = Modifier.padding(MiGuardiaSpacing.large),
            verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
        ) {
            Text(identity, fontWeight = FontWeight.SemiBold)
            HistoricalSummary(write = V2ShiftWrite(row.shift, row.snapshot))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
            ) {
                SecondaryAction(
                    label = "Editar jornada",
                    onClick = onEdit,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("v2-edit-shift-${row.shift.id}"),
                )
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
            modifier = Modifier.padding(MiGuardiaSpacing.large),
            fontWeight = FontWeight.SemiBold,
        )
    }
    Text("Jornada original", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    SummaryCard(
        modifier = Modifier.testTag("v2-shift-original-summary"),
    ) {
        HistoricalSummary(original)
    }
    if (state.actualExpectation?.previousActual != null) {
        SectionCard(
            title = "Esta jornada tiene horario real",
            supportingText = "Para cambiar el inicio o el final planificado, primero resolvé su horario real.",
        ) {
            SecondaryAction(
                label = "Descartar esta edición y corregir horario real",
                onClick = { actions.correctActual(original.shift.id) },
                enabled = editable,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("v2-shift-edit-correct-actual"),
            )
            SecondaryAction(
                label = "Descartar esta edición y volver al planificado",
                onClick = { actions.returnActualToPlanned(original.shift.id) },
                enabled = editable,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("v2-shift-edit-return-actual"),
            )
        }
    }
    Text("Plantilla", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(
        "Podés mantener la fotografía histórica o elegir una plantilla activa compatible.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
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
    PrimaryAction(
        label = "Revisar cambios",
        onClick = actions.requestReview,
        enabled = editable && state.hasUnconfirmedChanges && state.hasCoherentSelection(),
        modifier = Modifier.testTag("v2-shift-request-review"),
    )
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
        Text("Mantener plantilla histórica", fontWeight = FontWeight.SemiBold)
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
            append(", color de jornada")
            append(if (selected) ", seleccionado" else ", sin seleccionar")
        },
        testTag = "v2-shift-template-${option.template.id}",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(14.dp)
                    .background(Color(option.template.colorArgb), androidx.compose.foundation.shape.CircleShape)
                    .semantics { contentDescription = "Color de la jornada" },
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.extraSmall)) {
                Text(
                    "${option.objective.fullName} (${option.objective.abbreviation})",
                    fontWeight = FontWeight.SemiBold,
                )
                option.objective.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(option.workType.name, fontWeight = FontWeight.SemiBold)
                Text("${option.template.startTime.format(TIME_FORMATTER)}–${option.template.endTime.format(TIME_FORMATTER)}")
                if (selected) Text("Seleccionada",
                    color = if (enabled) MaterialTheme.vigiliaColors.active else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
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
        Column(
            modifier = Modifier.padding(horizontal = MiGuardiaSpacing.medium, vertical = MiGuardiaSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.extraSmall),
            content = content,
        )
    }
}

@Composable
private fun ReviewStep(state: V2ShiftEditUiState, actions: V2ShiftEditActions) {
    val original = state.originalWrite ?: return
    Text("Revisá antes de guardar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    SummaryCard(modifier = Modifier.testTag("v2-shift-final-summary")) {
        Text("Fecha fija: ${original.shift.localStartDate.fullDate()}", fontWeight = FontWeight.SemiBold)
        val option = state.selectedOption
        if (state.usesHistoricalTemplate || option == null) {
            Text(
                "Lugar: ${original.shift.objectiveNameSnapshot} " +
                    "(${original.shift.objectiveAbbreviationSnapshot})",
            )
            Text("Tipo: ${original.snapshot.workTypeNameSnapshot}")
            Text("Horario: ${original.shift.timeRange()}")
            ShiftColorIndicator(original.shift.colorArgbSnapshot)
            original.shift.objectiveAddressSnapshot?.let { Text("Dirección: $it") }
        } else {
            Text("Lugar: ${option.objective.fullName} (${option.objective.abbreviation})")
            Text("Tipo: ${option.workType.name}")
            Text("Horario: ${option.template.startTime.format(TIME_FORMATTER)}–${option.template.endTime.format(TIME_FORMATTER)}")
            ShiftColorIndicator(option.template.colorArgb)
            option.objective.address?.let { Text("Dirección: $it") }
        }
        Text("Puesto o función: ${normalizeOptionalWorkText(state.position) ?: "Sin puesto"}")
        if (state.warnings.isNotEmpty()) {
            Text("Advertencias confirmadas: ${state.warnings.size}", color = MaterialTheme.colorScheme.tertiary)
        }
    }
    PrimaryAction(
        label = if (state.isSaving) "Guardando cambios…" else "Guardar cambios",
        onClick = actions.save,
        enabled = state.reviewFingerprint != null && !state.isLoading,
        working = state.isSaving,
        modifier = Modifier.testTag("v2-shift-save"),
    )
    SecondaryAction(
        label = "Volver y corregir",
        onClick = actions.back,
        enabled = !state.isLoading && !state.isSaving,
        modifier = Modifier.fillMaxWidth(),
    )
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
            modifier = Modifier.padding(MiGuardiaSpacing.large),
            verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.extraSmall),
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
    ShiftColorIndicator(write.shift.colorArgbSnapshot)
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
                verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
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
            Column(verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small)) {
                Text(date.fullDate(), fontWeight = FontWeight.SemiBold)
                Text("Esta jornada pertenece a un plan recurrente.")
                Text("Cambiar sólo esta jornada la dejará personalizada y no modificará las demás.")
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.extraSmall)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small)) {
                Text(date.fullDate(), fontWeight = FontWeight.SemiBold)
                Text("Eliminar sólo esta jornada conservará una exclusión para que no reaparezca.")
                Text("Finalizar desde esta fecha conservará pasado, personalizaciones, notas, avisos y estados protegidos.")
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.extraSmall)) {
                FilledTonalButton(
                    onClick = actions.deleteOnlyThisOccurrence,
                    shape = MiGuardiaButtonShape,
                    colors = destructiveButtonColors(),
                    modifier = Modifier.testTag("v2-shift-delete-only-this"),
                ) { Text("Eliminar sólo esta jornada") }
                FilledTonalButton(
                    onClick = {
                        actions.handoffToRecurring()
                        actions.finalizeSeriesFrom(occurrence.planId, date)
                    },
                    shape = MiGuardiaButtonShape,
                    colors = destructiveButtonColors(),
                    modifier = Modifier.testTag("v2-shift-finalize-from-date"),
                ) { Text("Finalizar desde esta fecha") }
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
                verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
            ) {
                if (original != null) {
                    Text(original.shift.localStartDate.fullDate(), fontWeight = FontWeight.SemiBold)
                    HistoricalSummary(original)
                } else {
                    Text("Releyendo la jornada exacta…")
                }
                Text("Se eliminará solamente esta jornada. Las demás se conservarán.")
                if (state.actualExpectation?.previousActual != null) {
                    Text(
                        "También se quitarán el horario real y todos sus fragmentos extra.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
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
            FilledTonalButton(
                onClick = actions.confirmDelete,
                enabled = original != null && !state.isLoading && !state.isSaving,
                shape = MiGuardiaButtonShape,
                colors = destructiveButtonColors(),
                modifier = Modifier.testTag("v2-shift-confirm-delete"),
            ) {
                Text("Eliminar esta jornada")
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
            FilledTonalButton(
                onClick = actions.confirmDiscard,
                enabled = !state.isSaving,
                shape = MiGuardiaButtonShape,
                colors = destructiveButtonColors(),
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
            .padding(vertical = MiGuardiaSpacing.small),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(MiGuardiaSpacing.small))
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
    formatEventTimeRange(startAt, endAt, zoneId, EventTimeRangeStyle.DETAIL)

private fun LocalDate.fullDate(): String = format(FULL_DATE_FORMATTER)

@Composable
private fun ShiftColorIndicator(colorArgb: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
        modifier = Modifier.semantics { contentDescription = "Color de la jornada" },
    ) {
        Box(
            Modifier
                .size(14.dp)
                .background(Color(colorArgb), androidx.compose.foundation.shape.CircleShape),
        )
        Text("Color de la jornada", style = MaterialTheme.typography.bodySmall)
    }
}

private val FULL_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' uuuu", Locale.forLanguageTag("es-AR"))
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
