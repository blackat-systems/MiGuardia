package com.blackatsystems.miguardia.ui.management

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.model.MonthlyOrdinal
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanAggregate
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevisionKind
import com.blackatsystems.miguardia.core.domain.shift.RecurringConflictPolicy
import com.blackatsystems.miguardia.core.domain.shift.RecurringDateAction
import com.blackatsystems.miguardia.core.domain.shift.RecurringMutationPreview
import com.blackatsystems.miguardia.core.domain.shift.RecurringOccupantKind
import com.blackatsystems.miguardia.core.domain.shift.ShiftPlanningWarning
import com.blackatsystems.miguardia.core.domain.shift.describeRecurringPattern
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class V2RecurringActions(
    val resume: (WorkSetupState) -> Unit = {},
    val openCreate: (WorkSetupState) -> Unit = {},
    val openPlans: (WorkSetupState) -> Unit = {},
    val openPlan: (UUID) -> Unit = {},
    val changeFrom: (UUID, LocalDate) -> Unit = { _, _ -> },
    val finalizeFrom: (UUID, LocalDate) -> Unit = { _, _ -> },
    val selectTemplate: (UUID) -> Unit = {},
    val updatePosition: (String) -> Unit = {},
    val selectPattern: (V2RecurringPatternKind) -> Unit = {},
    val toggleWeekday: (DayOfWeek) -> Unit = {},
    val updateInterval: (String) -> Unit = {},
    val selectMonthlyOrdinal: (MonthlyOrdinal) -> Unit = {},
    val selectMonthlyDay: (DayOfWeek) -> Unit = {},
    val updateStartDate: (String) -> Unit = {},
    val updateEndDate: (String) -> Unit = {},
    val selectConflictPolicy: (RecurringConflictPolicy) -> Unit = {},
    val review: () -> Unit = {},
    val save: () -> Unit = {},
    val retry: () -> Unit = {},
    val back: () -> Unit = {},
    val confirmDiscard: () -> Unit = {},
    val cancelDiscard: () -> Unit = {},
    val close: () -> Unit = {},
    val clearMessage: () -> Unit = {},
    val consumeSuccess: (Int) -> Unit = {},
) {
    companion object {
        fun from(viewModel: V2RecurringPlanViewModel) = V2RecurringActions(
            resume = viewModel::resume,
            openCreate = viewModel::openCreate,
            openPlans = viewModel::openPlans,
            openPlan = viewModel::openPlan,
            changeFrom = viewModel::changeFrom,
            finalizeFrom = viewModel::finalizeFrom,
            selectTemplate = viewModel::selectTemplate,
            updatePosition = viewModel::updatePosition,
            selectPattern = viewModel::selectPattern,
            toggleWeekday = viewModel::toggleWeekday,
            updateInterval = viewModel::updateInterval,
            selectMonthlyOrdinal = viewModel::selectMonthlyOrdinal,
            selectMonthlyDay = viewModel::selectMonthlyDay,
            updateStartDate = viewModel::updateStartDate,
            updateEndDate = viewModel::updateEndDate,
            selectConflictPolicy = viewModel::selectConflictPolicy,
            review = viewModel::review,
            save = viewModel::save,
            retry = viewModel::retry,
            back = viewModel::back,
            confirmDiscard = viewModel::confirmDiscard,
            cancelDiscard = viewModel::cancelDiscard,
            close = viewModel::close,
            clearMessage = viewModel::clearMessage,
            consumeSuccess = viewModel::consumeSuccess,
        )
    }
}

@Composable
fun V2RecurringPlanSurfaceHost(
    state: V2RecurringUiState,
    actions: V2RecurringActions,
) {
    if (!state.isBlocking) return
    BackHandler(enabled = !state.isSaving, onBack = actions.back)
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("v2-recurring-surface"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            RecurringHeader(state, actions.back)
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .testTag("v2-recurring-scroll"),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                state.errorMessage?.let { message ->
                    PersistentMessage(
                        message = message,
                        onDismiss = actions.clearMessage,
                        onRetry = if (state.canRetry) actions.retry else null,
                    )
                }
                when (state.stage) {
                    V2RecurringStage.IDLE -> Unit
                    V2RecurringStage.PLANS -> PlansStep(state, actions)
                    V2RecurringStage.PLAN_DETAIL -> PlanDetailStep(state, actions)
                    V2RecurringStage.FORM,
                    V2RecurringStage.CONFIRM_DISCARD,
                    -> EditorStep(state, actions)
                    V2RecurringStage.PREVIEW -> PreviewStep(state, actions)
                }
                if (state.isLoading || state.isSaving) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("v2-recurring-loading"),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(if (state.isSaving) "Guardando todo el plan…" else "Releyendo y calculando…")
                    }
                }
            }
        }
    }
    if (state.stage == V2RecurringStage.CONFIRM_DISCARD) {
        AlertDialog(
            onDismissRequest = actions.cancelDiscard,
            modifier = Modifier.testTag("v2-recurring-discard-dialog"),
            title = { Text("Descartar borrador") },
            text = { Text("Las opciones y la vista previa se perderán. No se escribió ninguna jornada.") },
            confirmButton = {
                TextButton(
                    onClick = actions.confirmDiscard,
                    modifier = Modifier.testTag("v2-recurring-confirm-discard"),
                ) { Text("Descartar") }
            },
            dismissButton = {
                TextButton(onClick = actions.cancelDiscard) { Text("Seguir editando") }
            },
        )
    }
}

@Composable
private fun RecurringHeader(state: V2RecurringUiState, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack, enabled = !state.isLoading && !state.isSaving) {
            Text(if (state.stage == V2RecurringStage.PLANS) "Cerrar" else "Volver")
        }
        Text(
            text = when (state.stage) {
                V2RecurringStage.PLANS,
                V2RecurringStage.PLAN_DETAIL,
                -> "Planes recurrentes"
                V2RecurringStage.PREVIEW -> "Revisar plan"
                else -> when (state.mode) {
                    V2RecurringMode.CREATE -> "Repetir jornadas"
                    V2RecurringMode.CHANGE -> "Cambiar desde una fecha"
                    V2RecurringMode.FINALIZE -> "Finalizar desde una fecha"
                }
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PlansStep(state: V2RecurringUiState, actions: V2RecurringActions) {
    if (!state.isLoading && state.plansReadSuccessfully && state.plans.isEmpty()) {
        Text("Todavía no hay planes recurrentes.", style = MaterialTheme.typography.titleMedium)
        Text(
            "Los planes aparecen acá después de confirmar una repetición finita.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    state.plans.forEach { plan ->
        val latest = plan.latestRevision
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.isLoading) { actions.openPlan(plan.plan.id) }
                .testTag("v2-recurring-plan-${plan.plan.id}"),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(latest.objectiveNameSnapshot, fontWeight = FontWeight.Bold)
                Text(describeRecurringPattern(latest.pattern))
                Text("${latest.startTimeSnapshot}–${latest.endTimeSnapshot} · hasta ${latest.endDateInclusive.fullDate()}")
                Text(
                    if (latest.kind == RecurringPlanRevisionKind.FINALIZED) "Estado: finalizado" else "Estado: activo",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlanDetailStep(state: V2RecurringUiState, actions: V2RecurringActions) {
    val plan = state.selectedPlan ?: return
    val latest = plan.latestRevision
    Text(latest.objectiveNameSnapshot, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    RevisionSummary(latest)
    Text(
        if (latest.kind == RecurringPlanRevisionKind.FINALIZED) "Estado: finalizado" else "Estado: activo",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text("Revisiones guardadas: ${plan.revisions.size}")
    Text("Ocurrencias registradas: ${plan.occurrences.size}")
    val counts = plan.occurrences.groupingBy { it.state }.eachCount()
    Text(
        "Automáticas ${counts[com.blackatsystems.miguardia.core.domain.model.RecurringOccurrenceState.AUTOMATIC] ?: 0} · " +
            "personalizadas ${counts[com.blackatsystems.miguardia.core.domain.model.RecurringOccurrenceState.CUSTOMIZED] ?: 0} · " +
            "excluidas ${counts[com.blackatsystems.miguardia.core.domain.model.RecurringOccurrenceState.EXCLUDED] ?: 0} · " +
            "retiradas ${counts[com.blackatsystems.miguardia.core.domain.model.RecurringOccurrenceState.RETIRED] ?: 0}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (latest.kind == RecurringPlanRevisionKind.ACTIVE) {
        val suggestedCut = maxOf(state.referenceDate ?: latest.effectiveFrom, latest.effectiveFrom)
        Button(
            onClick = { actions.changeFrom(plan.plan.id, suggestedCut) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("v2-recurring-change-plan"),
        ) { Text("Cambiar desde hoy") }
        OutlinedButton(
            onClick = { actions.finalizeFrom(plan.plan.id, suggestedCut) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("v2-recurring-finalize-plan"),
        ) { Text("Finalizar desde hoy") }
    } else {
        Text("Este plan está finalizado y se conserva como historia.")
    }
}

@Composable
private fun EditorStep(state: V2RecurringUiState, actions: V2RecurringActions) {
    if (state.mode == V2RecurringMode.FINALIZE) {
        Text("Preparando la finalización exacta desde ${state.cutDate?.fullDate().orEmpty()}.")
        return
    }
    state.cutDate?.let { cut ->
        Text("Fecha de corte fija", style = MaterialTheme.typography.labelLarge)
        Text(cut.fullDate(), fontWeight = FontWeight.Bold, modifier = Modifier.testTag("v2-recurring-cut-date"))
        Text("El pasado y las excepciones no se modificarán.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("1. Plantilla guardada", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.templateOptions.forEach { option ->
            val selected = option.template.id == state.selectedTemplateId
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        enabled = !state.isLoading && !state.isSaving,
                        role = Role.RadioButton,
                        onClick = { actions.selectTemplate(option.template.id) },
                    )
                    .testTag("v2-recurring-template-${option.template.id}"),
                border = BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(option.objective.fullName, fontWeight = FontWeight.SemiBold)
                        Text("${option.workType.name} · ${option.template.startTime}–${option.template.endTime}")
                        Text("Color ${option.template.colorArgb.toArgbLabel()}")
                    }
                    Box(
                        Modifier
                            .size(24.dp)
                            .background(Color(option.template.colorArgb)),
                    )
                }
            }
        }
    }
    OutlinedTextField(
        value = state.position,
        onValueChange = actions.updatePosition,
        enabled = !state.isLoading && !state.isSaving,
        label = { Text("Puesto o función (opcional)") },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("v2-recurring-position"),
        singleLine = true,
    )

    Text("2. Patrón", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    V2RecurringPatternKind.entries.forEach { kind ->
        ChoiceRow(
            label = kind.visibleName(),
            selected = state.patternKind == kind,
            enabled = !state.isLoading && !state.isSaving,
            tag = "v2-recurring-pattern-${kind.name}",
            onClick = { actions.selectPattern(kind) },
        )
    }
    when (state.patternKind) {
        V2RecurringPatternKind.WEEKDAYS -> DayOfWeek.entries.forEach { day ->
            MultiChoiceRow(
                label = day.visibleName(),
                selected = day in state.weekdays,
                enabled = !state.isLoading && !state.isSaving,
                tag = "v2-recurring-weekday-${day.name}",
                onClick = { actions.toggleWeekday(day) },
            )
        }
        V2RecurringPatternKind.EVERY_N_DAYS,
        V2RecurringPatternKind.EVERY_N_WEEKS,
        -> OutlinedTextField(
            value = state.intervalText,
            onValueChange = actions.updateInterval,
            enabled = !state.isLoading && !state.isSaving,
            label = { Text("Cantidad positiva") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("v2-recurring-interval"),
            singleLine = true,
        )
        V2RecurringPatternKind.MONTHLY -> {
            Text("Orden dentro del mes", fontWeight = FontWeight.SemiBold)
            MonthlyOrdinal.entries.forEach { ordinal ->
                ChoiceRow(
                    label = ordinal.visibleName(),
                    selected = state.monthlyOrdinal == ordinal,
                    enabled = !state.isLoading && !state.isSaving,
                    tag = "v2-recurring-ordinal-${ordinal.name}",
                    onClick = { actions.selectMonthlyOrdinal(ordinal) },
                )
            }
            Text("Día de la semana", fontWeight = FontWeight.SemiBold)
            DayOfWeek.entries.forEach { day ->
                ChoiceRow(
                    label = day.visibleName(),
                    selected = state.monthlyDayOfWeek == day,
                    enabled = !state.isLoading && !state.isSaving,
                    tag = "v2-recurring-monthly-day-${day.name}",
                    onClick = { actions.selectMonthlyDay(day) },
                )
            }
        }
    }

    Text("3. Rango inclusivo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = state.startDateText,
        onValueChange = actions.updateStartDate,
        enabled = state.cutDate == null && !state.isLoading && !state.isSaving,
        label = { Text("Inicio (AAAA-MM-DD)") },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("v2-recurring-start-date"),
        singleLine = true,
    )
    OutlinedTextField(
        value = state.endDateText,
        onValueChange = actions.updateEndDate,
        enabled = !state.isLoading && !state.isSaving,
        label = { Text("Final (AAAA-MM-DD)") },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("v2-recurring-end-date"),
        singleLine = true,
    )

    Text("4. Si una fecha ya está ocupada", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    RecurringConflictPolicy.entries.forEach { policy ->
        ChoiceRow(
            label = policy.visibleName(),
            selected = state.conflictPolicy == policy,
            enabled = !state.isLoading && !state.isSaving,
            tag = "v2-recurring-policy-${policy.name}",
            onClick = { actions.selectConflictPolicy(policy) },
        )
    }
    if (state.conflictPolicy == RecurringConflictPolicy.KEEP_BOTH) {
        Text(
            "Mantener ambas puede dejar dos jornadas el mismo día. La vista previa mostrará cada advertencia.",
            color = MaterialTheme.colorScheme.error,
        )
    }
    Button(
        onClick = actions.review,
        enabled = state.selectedOption != null && !state.isLoading && !state.isSaving,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("v2-recurring-review"),
    ) { Text("Ver todas las fechas") }
}

@Composable
private fun PreviewStep(state: V2RecurringUiState, actions: V2RecurringActions) {
    val preview = state.preview ?: return
    val option = state.selectedOption
    Text(preview.patternDescription, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    option?.let {
        Text("${it.objective.fullName} · ${it.workType.name}")
        Text("${it.template.startTime}–${it.template.endTime} · color ${it.template.colorArgb.toArgbLabel()}")
        Text("Puesto: ${state.position.ifBlank { "sin puesto" }}")
    }
    Text("Inicio ${state.startDateText} · final ${state.endDateText}")
    Text("Cantidad total de fechas: ${preview.dates.size}", fontWeight = FontWeight.Bold)
    if (preview.dates.isNotEmpty()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .testTag("v2-recurring-exact-dates"),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                preview.dates.forEach { date ->
                    val result = preview.results.firstOrNull { it.date == date }
                    Text("${date.fullDate()} — ${result?.action?.visibleName() ?: "sin cambio"}")
                    result?.occupants.orEmpty().forEach { occupant ->
                        Text(
                            "  Ocupa: ${occupant.shift.objectiveNameSnapshot} · ${occupant.kind.visibleName()}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    Text("Libres: ${preview.freeDates.size} · ocupadas: ${preview.occupiedDates.size} · protegidas: ${preview.protectedDates.size}")
    if (preview.medicalLeaveDates.isNotEmpty()) {
        Text(
            "Carpeta médica: ${preview.medicalLeaveDates.sorted().joinToString { it.fullDate() }}",
            color = MaterialTheme.colorScheme.error,
        )
    }
    WarningList(preview)
    if (state.mode == V2RecurringMode.FINALIZE) {
        val retired = preview.results.count { it.action == RecurringDateAction.RETIRE_AUTOMATIC }
        val preserved = preview.results.count {
            it.action in setOf(
                RecurringDateAction.PRESERVE_PROTECTED,
                RecurringDateAction.KEEP_CUSTOMIZED,
                RecurringDateAction.KEEP_EXCLUDED,
            )
        }
        Text("Se retirarán $retired jornadas automáticas intactas y se conservarán $preserved excepciones o protecciones.")
    }
    Button(
        onClick = actions.save,
        enabled = preview.canConfirm && !state.isLoading && !state.isSaving,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("v2-recurring-save"),
    ) {
        Text(
            when (state.mode) {
                V2RecurringMode.CREATE -> "Crear plan y jornadas"
                V2RecurringMode.CHANGE -> "Cambiar todo lo futuro"
                V2RecurringMode.FINALIZE -> "Finalizar desde esta fecha"
            },
        )
    }
    OutlinedButton(
        onClick = actions.back,
        enabled = !state.isLoading && !state.isSaving,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (state.mode == V2RecurringMode.FINALIZE) "Cancelar" else "Modificar opciones") }
}

@Composable
private fun WarningList(preview: RecurringMutationPreview) {
    if (preview.warnings.isEmpty()) return
    Text("Advertencias", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    preview.warnings.forEach { warning ->
        Text(
            "• ${warning.visibleName()}",
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 5.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label)
    }
}

@Composable
private fun MultiChoiceRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .padding(vertical = 5.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
        Text(label)
    }
}

@Composable
private fun RevisionSummary(revision: com.blackatsystems.miguardia.core.domain.model.RecurringPlanRevision) {
    Text(describeRecurringPattern(revision.pattern), fontWeight = FontWeight.SemiBold)
    Text("${revision.startTimeSnapshot}–${revision.endTimeSnapshot}")
    Text("Vigente desde ${revision.effectiveFrom.fullDate()} hasta ${revision.endDateInclusive.fullDate()}")
    Text("Tipo: ${revision.workTypeNameSnapshot}")
    Text("Puesto: ${revision.positionSnapshot ?: "sin puesto"}")
}

private fun V2RecurringPatternKind.visibleName(): String = when (this) {
    V2RecurringPatternKind.WEEKDAYS -> "Días de la semana"
    V2RecurringPatternKind.EVERY_N_DAYS -> "Cada N días"
    V2RecurringPatternKind.EVERY_N_WEEKS -> "Cada N semanas"
    V2RecurringPatternKind.MONTHLY -> "Mensual por orden y día"
}

private fun RecurringConflictPolicy.visibleName(): String = when (this) {
    RecurringConflictPolicy.KEEP_EXISTING -> "Conservar lo existente"
    RecurringConflictPolicy.REPLACE_AUTOMATIC_INTACT -> "Reemplazar sólo jornadas automáticas intactas"
    RecurringConflictPolicy.KEEP_BOTH -> "Mantener ambas"
    RecurringConflictPolicy.CANCEL -> "Cancelar"
}

private fun RecurringDateAction.visibleName(): String = when (this) {
    RecurringDateAction.CREATE -> "se creará"
    RecurringDateAction.UPDATE_AUTOMATIC -> "se actualizará"
    RecurringDateAction.KEEP_EXISTING_AS_EXCLUDED -> "se omitirá y quedará excluida"
    RecurringDateAction.REPLACE_AUTOMATIC -> "reemplazará una automática intacta"
    RecurringDateAction.KEEP_BOTH -> "se mantendrán ambas"
    RecurringDateAction.PRESERVE_PROTECTED -> "se conservará por protección"
    RecurringDateAction.RETIRE_AUTOMATIC -> "se retirará"
    RecurringDateAction.KEEP_CUSTOMIZED -> "se conservará personalizada"
    RecurringDateAction.KEEP_EXCLUDED -> "seguirá excluida"
    RecurringDateAction.KEEP_RETIRED -> "seguirá retirada"
    RecurringDateAction.BLOCKED_BY_CANCEL -> "cancelada"
}

private fun RecurringOccupantKind.visibleName(): String = when (this) {
    RecurringOccupantKind.MANUAL -> "jornada manual protegida"
    RecurringOccupantKind.AUTOMATIC_INTACT -> "automática intacta"
    RecurringOccupantKind.CUSTOMIZED -> "personalizada protegida"
    RecurringOccupantKind.PROTECTED -> "protegida por estado, nota o aviso"
}

private fun ShiftPlanningWarning.visibleName(): String = when (this) {
    is ShiftPlanningWarning.SameDate -> "Habrá una segunda jornada el mismo día."
    is ShiftPlanningWarning.Overlap -> "Hay horarios superpuestos."
    is ShiftPlanningWarning.ShortRest -> "El descanso entre jornadas será menor a 12 horas (${actualRest.toMinutes()} min)."
}

private fun DayOfWeek.visibleName(): String = when (this) {
    DayOfWeek.MONDAY -> "Lunes"
    DayOfWeek.TUESDAY -> "Martes"
    DayOfWeek.WEDNESDAY -> "Miércoles"
    DayOfWeek.THURSDAY -> "Jueves"
    DayOfWeek.FRIDAY -> "Viernes"
    DayOfWeek.SATURDAY -> "Sábado"
    DayOfWeek.SUNDAY -> "Domingo"
}

private fun MonthlyOrdinal.visibleName(): String = when (this) {
    MonthlyOrdinal.FIRST -> "Primero"
    MonthlyOrdinal.SECOND -> "Segundo"
    MonthlyOrdinal.THIRD -> "Tercero"
    MonthlyOrdinal.FOURTH -> "Cuarto"
    MonthlyOrdinal.LAST -> "Último"
}

private fun LocalDate.fullDate(): String = format(FULL_DATE_FORMATTER)

private fun Int.toArgbLabel(): String = "#%08X".format(this)

private val FULL_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "EEEE d 'de' MMMM 'de' yyyy",
    Locale.forLanguageTag("es-AR"),
)
