package com.blackatsystems.miguardia.ui.hours

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.work.HoursPeriod
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.HoursTargetState
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursLookup
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.ui.components.EmptyState
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.PrimaryAction
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.components.SurfaceHeader
import com.blackatsystems.miguardia.ui.availability.AvailabilityHoursSection
import com.blackatsystems.miguardia.ui.availability.AvailabilityUiState
import com.blackatsystems.miguardia.ui.management.RgbColorPickerDialog
import com.blackatsystems.miguardia.ui.theme.vigiliaColors
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

data class HoursAndExtrasActions(
    val retry: () -> Unit = {},
    val openProgress: () -> Unit = {},
    val close: () -> Unit = {},
    val clearMessage: () -> Unit = {},
    val openReferenceEditor: () -> Unit = {},
    val updateReferenceDraft: ((HoursReferenceDraft) -> HoursReferenceDraft) -> Unit = {},
    val reviewReference: () -> Unit = {},
    val backReference: () -> Unit = {},
    val saveReference: () -> Unit = {},
    val openPerPeriodValueEditor: () -> Unit = {},
    val updatePerPeriodValueDraft: ((PerPeriodValueDraft) -> PerPeriodValueDraft) -> Unit = {},
    val backPerPeriodValue: () -> Unit = {},
    val savePerPeriodValue: () -> Unit = {},
    val openCreateExtra: (java.time.LocalDate) -> Unit = {},
    val openCorrectExtra: (IndependentExtraWorkRecord) -> Unit = {},
    val requestDelete: (IndependentExtraWorkRecord) -> Unit = {},
    val updateExtraDraft: ((IndependentExtraDraftState) -> IndependentExtraDraftState) -> Unit = {},
    val selectTemplate: (UUID?) -> Unit = {},
    val reviewExtra: () -> Unit = {},
    val backExtra: () -> Unit = {},
    val saveExtra: () -> Unit = {},
    val confirmOverlap: (Boolean) -> Unit = {},
    val confirmProtection: (Boolean) -> Unit = {},
    val dismissDelete: () -> Unit = {},
    val confirmDelete: () -> Unit = {},
    val consumeSuccess: (Int) -> Unit = {},
) {
    companion object {
        fun from(viewModel: HoursAndExtrasViewModel) = HoursAndExtrasActions(
            retry = viewModel::retry,
            openProgress = viewModel::openProgress,
            close = viewModel::close,
            clearMessage = viewModel::clearMessage,
            openReferenceEditor = viewModel::openReferenceEditor,
            updateReferenceDraft = viewModel::updateReferenceDraft,
            reviewReference = viewModel::reviewReference,
            backReference = viewModel::backReference,
            saveReference = viewModel::saveReference,
            openPerPeriodValueEditor = viewModel::openPerPeriodValueEditor,
            updatePerPeriodValueDraft = viewModel::updatePerPeriodValueDraft,
            backPerPeriodValue = viewModel::backPerPeriodValue,
            savePerPeriodValue = viewModel::savePerPeriodValue,
            openCreateExtra = viewModel::openCreateExtra,
            openCorrectExtra = viewModel::openCorrectExtra,
            requestDelete = viewModel::requestDelete,
            updateExtraDraft = viewModel::updateExtraDraft,
            selectTemplate = viewModel::selectTemplate,
            reviewExtra = viewModel::reviewExtra,
            backExtra = viewModel::backExtra,
            saveExtra = viewModel::saveExtra,
            confirmOverlap = viewModel::confirmOverlap,
            confirmProtection = viewModel::confirmProtection,
            dismissDelete = viewModel::dismissDelete,
            confirmDelete = viewModel::confirmDelete,
            consumeSuccess = viewModel::consumeSuccess,
        )
    }
}

@Composable
fun HoursAndExtrasSurfaceHost(
    state: HoursAndExtrasUiState,
    actions: HoursAndExtrasActions,
    availabilityState: AvailabilityUiState = AvailabilityUiState(),
    onOpenExtraClassCatalog: () -> Unit = {},
) {
    when (state.surface) {
        HoursAndExtrasSurface.NONE -> Unit
        HoursAndExtrasSurface.PROGRESS -> {
            BackHandler(onBack = actions.close)
            HoursProgressScreen(state, actions, availabilityState)
        }
        HoursAndExtrasSurface.REFERENCE_EDITOR -> {
            BackHandler(onBack = actions.backReference)
            ReferenceEditorScreen(state, actions)
        }
        HoursAndExtrasSurface.PERIOD_VALUE_EDITOR -> {
            BackHandler(onBack = actions.backPerPeriodValue)
            PerPeriodValueEditorScreen(state, actions)
        }
        HoursAndExtrasSurface.EXTRA_EDITOR -> {
            BackHandler(onBack = actions.backExtra)
            IndependentExtraEditorScreen(state, actions, onOpenExtraClassCatalog)
        }
        HoursAndExtrasSurface.DELETE_CONFIRMATION -> DeleteConfirmation(state, actions)
    }
}

@Composable
private fun HoursProgressScreen(
    state: HoursAndExtrasUiState,
    actions: HoursAndExtrasActions,
    availabilityState: AvailabilityUiState,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            SurfaceHeader("Referencia y avance de horas", "Cerrar", actions.close)
            HorizontalDivider()
            when (state.loadState) {
                HoursAndExtrasLoadState.LOADING -> NeutralLoading("Calculando tu avance…")
                HoursAndExtrasLoadState.ERROR -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    EmptyState(
                        title = "No pudimos calcular el avance",
                        message = state.message ?: "Las fuentes laborales no están disponibles.",
                        actionLabel = "Reintentar",
                        onAction = actions.retry,
                        modifier = Modifier.testTag("hours-progress-error"),
                    )
                }
                HoursAndExtrasLoadState.CONTENT -> HoursProgressContent(state, actions, availabilityState)
            }
        }
    }
}

@Composable
private fun HoursProgressContent(
    state: HoursAndExtrasUiState,
    actions: HoursAndExtrasActions,
    availabilityState: AvailabilityUiState,
) {
    val source = requireNotNull(state.source)
    val progress = source.progress
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("hours-progress-content"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.message?.let { PersistentMessage(it, onDismiss = actions.clearMessage) }
        SectionCard(
            title = referenceLabel(progress.segment.ownerRevision.value.hoursReference),
            supportingText = if (progress.segment.endExclusive == java.time.LocalDate.MAX) {
                "Tramo vigente desde ${progress.segment.startInclusive}, sin límite periódico."
            } else {
                "Tramo ${progress.segment.startInclusive} a ${progress.segment.endExclusive.minusDays(1)}."
            },
        ) {
            progress.segment.ownerRevision.value.hoursReference.periodOrNull()?.let { period ->
                Text("Período: ${period.visibleLabel()}")
            }
            Text("Último inicio: ${progress.segment.ownerRevision.value.hoursReferenceStartedOn ?: "No corresponde"}")
            Text(
                "Próximo límite: " + if (progress.segment.endExclusive == java.time.LocalDate.MAX) {
                    "No corresponde"
                } else {
                    progress.segment.endExclusive.toString()
                },
            )
            Text("Meta: ${targetLabel(progress.segment.target)}", fontWeight = FontWeight.SemiBold)
            if (progress.segment.isShortNaturalSegment) {
                Text(
                    "Este tramo comenzó dentro del período y usa la meta completa, sin prorrateo.",
                    color = MaterialTheme.vigiliaColors.info,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        SectionCard(
            title = "Avance trabajado",
            supportingText = "Los resultados se recalculan; no se guardan como totales.",
        ) {
            ProgressLine("Trabajo habitual", progress.regularWorkedMinutes)
            ProgressLine(
                "Horas extra",
                Math.subtractExact(progress.totalWorkedMinutes, progress.regularWorkedMinutes),
            )
            ProgressLine("Total trabajado", progress.totalWorkedMinutes, emphasized = true)
            ProgressLine("Ayuda a cumplir", progress.helpsMeetReferenceMinutes)
            ProgressLine("No ayuda a cumplir", progress.doesNotHelpReferenceMinutes)
            ProgressLine("Pendiente programado", progress.pendingScheduledMinutes)
            progress.missingMinutes?.takeIf { it > 0L }?.let { ProgressLine("Faltan", it, emphasized = true) }
            progress.excessMinutes?.takeIf { it > 0L }?.let { ProgressLine("Superación", it, emphasized = true) }
            progress.completionPercentage?.let { value ->
                Text("Avance: ${"%.1f".format(SpanishArgentina, value)} %", fontWeight = FontWeight.Bold)
            }
        }
        val dedicatedExtras = progress.extrasByClass.filter { it.key.showDedicatedSummary }
        if (dedicatedExtras.isNotEmpty()) {
            SectionCard("Desglose de extras") {
                dedicatedExtras.forEach { item ->
                    Text(
                        "${item.key.name}: ${minutesLabel(item.totalMinutes)} · " +
                            if (item.key.helpsMeetHoursReference) "ayuda a cumplir" else "no ayuda a cumplir",
                    )
                }
            }
        }
        AvailabilityHoursSection(availabilityState)
        Button(
            onClick = actions.openReferenceEditor,
            modifier = Modifier.fillMaxWidth().testTag("hours-reference-configure"),
        ) { Text("Configurar o cambiar referencia") }
        if (progress.segment.ownerRevision.value.hoursReference is HoursReference.PerPeriod) {
            OutlinedButton(
                onClick = actions.openPerPeriodValueEditor,
                modifier = Modifier.fillMaxWidth().testTag("hours-period-value-edit"),
            ) {
                Text(
                    if (progress.segment.target == HoursTargetState.MissingPerPeriodValue) {
                        "Informar meta de este período"
                    } else {
                        "Corregir meta de este período"
                    },
                )
            }
        }
    }
}

@Composable
private fun PerPeriodValueEditorScreen(
    state: HoursAndExtrasUiState,
    actions: HoursAndExtrasActions,
) {
    val source = state.source
    val draft = state.periodValueDraft
    val reference = source?.segment?.ownerRevision?.value?.hoursReference as? HoursReference.PerPeriod
    val window = source?.segment?.naturalWindow
    val existing = if (reference != null && window != null) {
        when (val lookup = source.history.perPeriodHoursValues.valueFor(reference.keyFor(window))) {
            PerPeriodHoursLookup.Missing -> null
            is PerPeriodHoursLookup.Defined -> lookup.entry
        }
    } else {
        null
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            SurfaceHeader("Meta del período", "Atrás", actions.backPerPeriodValue)
            HorizontalDivider()
            if (state.loadState == HoursAndExtrasLoadState.ERROR) {
                EditorLoadError(state.message, actions.retry)
            } else if (state.loadState != HoursAndExtrasLoadState.CONTENT || draft == null || window == null) {
                NeutralLoading("Preparando el período…")
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    state.message?.let { PersistentMessage(it, onDismiss = actions.clearMessage) }
                    SectionCard(
                        title = if (existing == null) "Informar meta" else "Corregir meta",
                        supportingText = "Período ${window.startInclusive} a ${window.endExclusive.minusDays(1)}.",
                    ) {
                        existing?.let { Text("Valor actual: ${minutesLabel(it.requiredMinutes.value)}") }
                        OutlinedTextField(
                            value = draft.requiredMinutes,
                            onValueChange = { value ->
                                actions.updatePerPeriodValueDraft {
                                    it.copy(requiredMinutes = value, confirmCorrection = false)
                                }
                            },
                            label = { Text("Minutos del período") },
                            supportingText = { Text("Debe ser un entero positivo; no se prorratea.") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("hours-period-value-minutes"),
                        )
                        if (existing != null) {
                            ConfirmRow(
                                "Confirmo corregir sólo este período sin cambiar su identidad.",
                                draft.confirmCorrection,
                            ) { checked ->
                                actions.updatePerPeriodValueDraft { it.copy(confirmCorrection = checked) }
                            }
                        }
                    }
                    PrimaryAction(
                        label = if (existing == null) "Guardar meta" else "Guardar corrección",
                        onClick = actions.savePerPeriodValue,
                        enabled = !state.isSaving,
                        working = state.isSaving,
                        modifier = Modifier.testTag("hours-period-value-save"),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferenceEditorScreen(state: HoursAndExtrasUiState, actions: HoursAndExtrasActions) {
    val draft = state.referenceDraft
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            SurfaceHeader(
                if (draft?.stage == EditorStage.REVIEW) "Revisar referencia" else "Configurar referencia",
                "Atrás",
                actions.backReference,
            )
            HorizontalDivider()
            if (state.loadState == HoursAndExtrasLoadState.LOADING || draft == null) {
                NeutralLoading("Preparando la configuración…")
            } else if (state.loadState == HoursAndExtrasLoadState.ERROR) {
                EditorLoadError(state.message, actions.retry)
            } else if (draft.stage == EditorStage.REVIEW) {
                ReferenceReviewContent(state, actions)
            } else {
                ReferenceDraftContent(state, actions)
            }
        }
    }
}

@Composable
private fun ReferenceDraftContent(state: HoursAndExtrasUiState, actions: HoursAndExtrasActions) {
    val draft = requireNotNull(state.referenceDraft)
    val source = requireNotNull(state.source)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.message?.let { PersistentMessage(it, onDismiss = actions.clearMessage) }
        SectionCard("¿Qué referencia usás?") {
            ReferenceChoiceRow("Todavía no la configuré", HoursReferenceChoice.PENDING, draft, actions)
            ReferenceChoiceRow("No uso una referencia de horas", HoursReferenceChoice.NOT_USED, draft, actions)
            ReferenceChoiceRow("Tengo una referencia, pero no sé cuántas horas", HoursReferenceChoice.UNKNOWN, draft, actions)
            ReferenceChoiceRow("La cantidad es fija", HoursReferenceChoice.FIXED, draft, actions)
            ReferenceChoiceRow("La cantidad cambia en cada período", HoursReferenceChoice.PER_PERIOD, draft, actions)
        }
        if (draft.choice in setOf(
                HoursReferenceChoice.UNKNOWN,
                HoursReferenceChoice.FIXED,
                HoursReferenceChoice.PER_PERIOD,
            )
        ) {
            SectionCard(
                title = "Período",
                supportingText = "No se aplica ningún período por pertenecer a un rubro.",
            ) {
                if (draft.choice == HoursReferenceChoice.UNKNOWN) {
                    SimpleChoice("Todavía no sé el período", draft.periodChoice == HoursPeriodChoice.NONE) {
                        actions.updateReferenceDraft {
                            it.copy(
                                periodChoice = HoursPeriodChoice.NONE,
                                startChoice = ReferenceStartChoice.TODAY,
                            )
                        }
                    }
                }
                SimpleChoice("Mes calendario", draft.periodChoice == HoursPeriodChoice.MONTHLY) {
                    actions.updateReferenceDraft { it.copy(periodChoice = HoursPeriodChoice.MONTHLY) }
                }
                SimpleChoice("Semana", draft.periodChoice == HoursPeriodChoice.WEEKLY) {
                    actions.updateReferenceDraft { it.copy(periodChoice = HoursPeriodChoice.WEEKLY) }
                }
                SimpleChoice("Ciclo", draft.periodChoice == HoursPeriodChoice.CYCLE) {
                    actions.updateReferenceDraft { it.copy(periodChoice = HoursPeriodChoice.CYCLE) }
                }
                if (draft.periodChoice == HoursPeriodChoice.WEEKLY) {
                    Text("Primer día de la semana (lunes es sólo la sugerencia)")
                    DayOfWeek.entries.forEach { day ->
                        SimpleChoice(
                            day.getDisplayName(TextStyle.FULL, SpanishArgentina),
                            draft.weeklyFirstDay == day,
                        ) { actions.updateReferenceDraft { it.copy(weeklyFirstDay = day) } }
                    }
                }
                if (draft.periodChoice == HoursPeriodChoice.CYCLE) {
                    OutlinedTextField(
                        value = draft.cycleLengthDays,
                        onValueChange = { value -> actions.updateReferenceDraft { it.copy(cycleLengthDays = value) } },
                        label = { Text("Cantidad de días") },
                        modifier = Modifier.fillMaxWidth().testTag("hours-cycle-length"),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.cycleAnchorDate,
                        onValueChange = { value -> actions.updateReferenceDraft { it.copy(cycleAnchorDate = value) } },
                        label = { Text("Fecha de anclaje (AAAA-MM-DD)") },
                        modifier = Modifier.fillMaxWidth().testTag("hours-cycle-anchor"),
                        singleLine = true,
                    )
                }
            }
        }
        if (draft.choice == HoursReferenceChoice.FIXED) {
            SectionCard("Cantidad fija") {
                OutlinedTextField(
                    value = draft.requiredMinutes,
                    onValueChange = { value -> actions.updateReferenceDraft { it.copy(requiredMinutes = value) } },
                    label = { Text("Minutos de referencia") },
                    supportingText = { Text("Ingresá un entero positivo. MiGuardia no propone un valor por sector.") },
                    modifier = Modifier.fillMaxWidth().testTag("hours-fixed-minutes"),
                    singleLine = true,
                )
            }
        }
        if (draft.choice == HoursReferenceChoice.PER_PERIOD) {
            SectionCard(
                title = "Primer período",
                supportingText = "Podés dejarlo vacío: se mostrará “Falta informar”.",
            ) {
                OutlinedTextField(
                    value = draft.initialPerPeriodMinutes,
                    onValueChange = { value -> actions.updateReferenceDraft { it.copy(initialPerPeriodMinutes = value) } },
                    label = { Text("Minutos del primer período (opcional)") },
                    modifier = Modifier.fillMaxWidth().testTag("hours-period-minutes"),
                    singleLine = true,
                )
            }
        }
        SectionCard("¿Desde cuándo empieza el conteo?") {
            SimpleChoice("Desde hoy (${source.today})", draft.startChoice == ReferenceStartChoice.TODAY) {
                actions.updateReferenceDraft { it.copy(startChoice = ReferenceStartChoice.TODAY) }
            }
            val provisional = draftPreviewPeriod(draft)
            val next = provisional?.let { period -> period.windowContaining(source.today).endExclusive }
            next?.let { nextStart ->
                SimpleChoice(
                    nextStart.visibleNextStartLabel(requireNotNull(provisional)),
                    draft.startChoice == ReferenceStartChoice.NEXT_PERIOD,
                    tag = "hours-reference-next-period",
                ) { actions.updateReferenceDraft { it.copy(startChoice = ReferenceStartChoice.NEXT_PERIOD) } }
            }
            SimpleChoice("Elegir otra fecha", draft.startChoice == ReferenceStartChoice.CUSTOM) {
                actions.updateReferenceDraft { it.copy(startChoice = ReferenceStartChoice.CUSTOM) }
            }
            if (draft.startChoice == ReferenceStartChoice.CUSTOM) {
                OutlinedTextField(
                    value = draft.customStartDate,
                    onValueChange = { value -> actions.updateReferenceDraft { it.copy(customStartDate = value) } },
                    label = { Text("Fecha (AAAA-MM-DD)") },
                    supportingText = {
                        Text("No puede ser anterior a ${source.history.timeline.revisions.first().effectiveFrom}.")
                    },
                    modifier = Modifier.fillMaxWidth().testTag("hours-custom-start"),
                    singleLine = true,
                )
            }
        }
        PrimaryAction(
            label = "Revisar referencia",
            onClick = actions.reviewReference,
            enabled = !state.isSaving,
            working = state.isSaving,
            modifier = Modifier.testTag("hours-reference-review"),
        )
    }
}

@Composable
private fun ReferenceReviewContent(state: HoursAndExtrasUiState, actions: HoursAndExtrasActions) {
    val review = state.referenceReview
    val draft = requireNotNull(state.referenceDraft)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.message?.let { PersistentMessage(it, onDismiss = actions.clearMessage) }
        SectionCard("Revisión exacta") {
            if (review == null) {
                Text("El borrador fue restaurado. Al guardar se revalidarán todas las fuentes.")
            } else {
                Text(referenceLabel(review.reference), fontWeight = FontWeight.Bold)
                review.reference.periodOrNull()?.let { period ->
                    Text("Período: ${period.visibleLabel()}")
                }
                Text(
                    "Cantidad: ${review.reference.reviewTargetLabel(state.source?.history, review)}",
                    fontWeight = FontWeight.SemiBold,
                )
                review.previousSegmentEndInclusive?.let { previousEnd ->
                    Text("El tramo anterior termina: $previousEnd")
                }
                Text("El tramo nuevo comienza: ${review.startedOn}")
                review.naturalWindowEndExclusive?.let { Text("Primer límite natural: $it") }
                Text(
                    if (review.isShortFirstSegment) {
                        "El primer tramo es corto y conserva la meta completa."
                    } else {
                        "El primer tramo coincide con el límite elegido."
                    },
                )
                if (review.isPast) {
                    Text(
                        if (review.recalculationEndExclusive == java.time.LocalDate.MAX) {
                            "Se recalcularán los tramos y resultados desde ${review.startedOn} en adelante."
                        } else {
                            "Se recalcularán los tramos y resultados desde ${review.startedOn} " +
                                "hasta ${review.recalculationEndExclusive.minusDays(1)}."
                        },
                        color = MaterialTheme.vigiliaColors.info,
                    )
                }
            }
        }
        if (review?.isPast == true) {
            ConfirmRow(
                "Confirmo recalcular los tramos desde la fecha mostrada.",
                draft.confirmPastRecalculation,
            ) { checked -> actions.updateReferenceDraft { it.copy(confirmPastRecalculation = checked) } }
        }
        if (review?.isShortFirstSegment == true) {
            ConfirmRow(
                "Confirmo usar la meta completa aunque el primer tramo sea más corto.",
                draft.confirmShortFirstSegment,
            ) { checked -> actions.updateReferenceDraft { it.copy(confirmShortFirstSegment = checked) } }
        }
        PrimaryAction(
            label = "Guardar referencia",
            onClick = actions.saveReference,
            enabled = !state.isSaving && draft.stage == EditorStage.REVIEW,
            working = state.isSaving,
            modifier = Modifier.testTag("hours-reference-save"),
        )
        OutlinedButton(onClick = actions.backReference, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
            Text("Volver a editar")
        }
    }
}

@Composable
private fun IndependentExtraEditorScreen(
    state: HoursAndExtrasUiState,
    actions: HoursAndExtrasActions,
    onOpenExtraClassCatalog: () -> Unit,
) {
    val draft = state.extraDraft
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            SurfaceHeader(
                if (draft?.stage == EditorStage.REVIEW) "Revisar trabajo extra" else "Registrar trabajo extra",
                "Atrás",
                actions.backExtra,
            )
            HorizontalDivider()
            if (state.loadState == HoursAndExtrasLoadState.LOADING || draft == null) {
                NeutralLoading("Preparando el trabajo extra…")
            } else if (state.loadState == HoursAndExtrasLoadState.ERROR) {
                EditorLoadError(state.message, actions.retry)
            } else if (draft.stage == EditorStage.REVIEW) {
                IndependentExtraReviewContent(state, actions)
            } else {
                IndependentExtraDraftContent(state, actions, onOpenExtraClassCatalog)
            }
        }
    }
}

@Composable
private fun IndependentExtraDraftContent(
    state: HoursAndExtrasUiState,
    actions: HoursAndExtrasActions,
    onOpenExtraClassCatalog: () -> Unit,
) {
    val draft = requireNotNull(state.extraDraft)
    val source = requireNotNull(state.source)
    var choosingColor by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.message?.let { PersistentMessage(it, onDismiss = actions.clearMessage) }
        SectionCard(
            title = "Fecha elegida",
            supportingText = "Para cambiar de día, volvé a la única grilla mensual.",
        ) {
            Text(draft.ownerDate?.toString() ?: "Sin fecha", fontWeight = FontWeight.Bold)
        }
        SectionCard("Lugar") {
            source.catalog.workPlaces.forEach { place ->
                val objective = source.objectives.firstOrNull { it.id == place.objectiveId }
                if (place.isActive || place.id == draft.workPlaceId) {
                    SimpleChoice(
                        "${objective?.fullName ?: "Lugar"} (${objective?.abbreviation ?: "—"})" +
                            if (!place.isActive) " · archivado" else "",
                        draft.workPlaceId == place.id,
                        tag = "extra-place-${place.id}",
                    ) {
                        actions.updateExtraDraft {
                            it.copy(workPlaceId = place.id, templateId = null, colorArgb = null)
                        }
                    }
                }
            }
        }
        SectionCard("Tipo de trabajo") {
            source.catalog.workTypes.forEach { type ->
                if (
                    (type.isActive && type.behavior == WorkTypeBehavior.ACTIVE_WORK) ||
                    type.id == draft.workTypeId
                ) {
                    SimpleChoice(
                        type.name + if (!type.isActive) " · archivado" else "",
                        draft.workTypeId == type.id,
                        tag = "extra-type-${type.id}",
                    ) {
                        actions.updateExtraDraft {
                            it.copy(workTypeId = type.id, templateId = null, colorArgb = null)
                        }
                    }
                }
            }
        }
        SectionCard(
            title = "Plantilla opcional",
            supportingText = "Puede precargar horario y color; el intervalo realizado siempre se confirma.",
        ) {
            SimpleChoice("Sin plantilla", draft.templateId == null) { actions.selectTemplate(null) }
            source.catalog.workTemplates
                .filter { template ->
                    (template.isActive || template.id == draft.templateId) &&
                        template.workPlaceId == draft.workPlaceId &&
                        template.workTypeId == draft.workTypeId
                }
                .forEach { template ->
                    SimpleChoice(
                        "${template.startTime}–${template.endTime}" + if (!template.isActive) " · archivada" else "",
                        draft.templateId == template.id,
                        tag = "extra-template-${template.id}",
                    ) { actions.selectTemplate(template.id) }
                }
        }
        SectionCard("Horario exacto realizado") {
            OutlinedTextField(
                value = draft.startTime,
                onValueChange = { value -> actions.updateExtraDraft { it.copy(startTime = value.take(5)) } },
                label = { Text("Inicio (HH:mm)") },
                modifier = Modifier.fillMaxWidth().testTag("extra-start-time"),
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.endDate,
                onValueChange = { value -> actions.updateExtraDraft { it.copy(endDate = value.take(10)) } },
                label = { Text("Fecha final (AAAA-MM-DD)") },
                supportingText = { Text("Admite medianoche y trabajos de más de 24 horas.") },
                modifier = Modifier.fillMaxWidth().testTag("extra-end-date"),
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.endTime,
                onValueChange = { value -> actions.updateExtraDraft { it.copy(endTime = value.take(5)) } },
                label = { Text("Final (HH:mm)") },
                modifier = Modifier.fillMaxWidth().testTag("extra-end-time"),
                singleLine = true,
            )
        }
        SectionCard("Clase extra") {
            val available = source.extraClasses.filter { it.isActive || it.id == draft.extraClassId }
            if (source.extraClasses.none { it.isActive }) {
                Text("No hay una clase extra utilizable. El borrador se conservará si abrís el catálogo.")
                OutlinedButton(
                    onClick = onOpenExtraClassCatalog,
                    modifier = Modifier.fillMaxWidth().testTag("extra-open-class-catalog"),
                ) { Text("Crear clase de horas extra") }
            }
            available.forEach { extraClass ->
                SimpleChoice(
                    extraClass.name +
                        (if (extraClass.helpsMeetHoursReference) " · ayuda a cumplir" else " · no ayuda") +
                        if (!extraClass.isActive) " · archivada" else "",
                    draft.extraClassId == extraClass.id,
                    tag = "extra-class-${extraClass.id}",
                ) { actions.updateExtraDraft { it.copy(extraClassId = extraClass.id) } }
            }
        }
        SectionCard("Color y puesto") {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(32.dp).background(
                        draft.colorArgb?.let(::Color) ?: MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
                )
                OutlinedButton(
                    onClick = { choosingColor = true },
                    enabled = draft.templateId == null,
                    modifier = Modifier.weight(1f).testTag("extra-color"),
                ) {
                    Text(
                        when {
                            draft.templateId != null -> "Color de la plantilla"
                            draft.colorArgb == null -> "Elegir color"
                            else -> "Cambiar color"
                        },
                    )
                }
            }
            OutlinedTextField(
                value = draft.position,
                onValueChange = { value -> actions.updateExtraDraft { it.copy(position = value) } },
                label = { Text("Puesto o función (opcional)") },
                modifier = Modifier.fillMaxWidth().testTag("extra-position"),
            )
        }
        PrimaryAction(
            label = "Revisar trabajo extra",
            onClick = actions.reviewExtra,
            enabled = !state.isSaving,
            working = state.isSaving,
            modifier = Modifier.testTag("extra-review"),
        )
    }
    if (choosingColor) {
        RgbColorPickerDialog(
            initialColor = draft.colorArgb ?: DEFAULT_EXTRA_COLOR,
            onDismiss = { choosingColor = false },
            onConfirm = { selected ->
                actions.updateExtraDraft { it.copy(colorArgb = selected) }
                choosingColor = false
            },
        )
    }
}

@Composable
private fun IndependentExtraReviewContent(state: HoursAndExtrasUiState, actions: HoursAndExtrasActions) {
    val draft = requireNotNull(state.extraDraft)
    val review = state.extraReview
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.message?.let { PersistentMessage(it, onDismiss = actions.clearMessage) }
        SectionCard("Trabajo extra independiente") {
            if (review == null) {
                Text("El borrador fue restaurado. Al guardar se revalidarán fuentes, ocupación y protecciones.")
            } else {
                val record = review.record
                Text("${record.snapshot.workPlaceName} (${record.snapshot.workPlaceAbbreviation})", fontWeight = FontWeight.Bold)
                Text("${record.start.visibleAt(record.zoneId)} – ${record.end.visibleAt(record.zoneId)}")
                Text("Duración: ${minutesLabel(record.durationMinutes)}")
                Text("Tipo: ${record.snapshot.workTypeName}")
                Text("Clase: ${record.snapshot.className}")
                Text(if (record.snapshot.helpsMeetHoursReference) "Ayuda a cumplir la referencia" else "No ayuda a cumplir la referencia")
                record.snapshot.position?.let { Text("Puesto: $it") }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(18.dp).background(Color(record.snapshot.colorArgb), CircleShape))
                    Text("Color: ${record.snapshot.colorArgb.toArgbLabel()}")
                }
            }
        }
        if (review?.hasOverlap == true) {
            SectionCard(
                title = "Trabajos superpuestos",
                supportingText = "Cada intervalo confirmado se suma completo.",
            ) {
                review.expectation.observedShifts
                    .filter { occupied ->
                        occupied.startAt < review.record.end && occupied.endAt > review.record.start
                    }
                    .sortedBy { it.startAt }
                    .forEach { occupied ->
                    Text(
                        "Jornada: ${occupied.startAt.visibleAt(review.record.zoneId)} – " +
                            occupied.endAt.visibleAt(review.record.zoneId),
                    )
                }
                review.expectation.observedExtras
                    .filter { occupied ->
                        occupied.start < review.record.end && occupied.end > review.record.start
                    }
                    .sortedBy { it.start }
                    .forEach { occupied ->
                    Text(
                        "Extra independiente: ${occupied.start.visibleAt(review.record.zoneId)} – " +
                            occupied.end.visibleAt(review.record.zoneId),
                    )
                }
            }
            ConfirmRow(
                "Hay trabajos activos superpuestos. Confirmo conservarlos y sumar cada uno completo.",
                draft.overlapConfirmed,
                actions.confirmOverlap,
            )
        }
        if (review?.hasProtectedDates == true) {
            ConfirmRow(
                "Hay carpeta médica o vacaciones. Confirmo conservar el trabajo real sin borrar la protección.",
                draft.protectionConfirmed,
                actions.confirmProtection,
            )
        }
        PrimaryAction(
            label = "Guardar trabajo extra",
            onClick = actions.saveExtra,
            enabled = !state.isSaving,
            working = state.isSaving,
            modifier = Modifier.testTag("extra-save"),
        )
        OutlinedButton(onClick = actions.backExtra, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
            Text("Volver a editar")
        }
    }
}

@Composable
private fun DeleteConfirmation(state: HoursAndExtrasUiState, actions: HoursAndExtrasActions) {
    val record = state.deletingRecord
    AlertDialog(
        onDismissRequest = actions.dismissDelete,
        title = { Text("¿Eliminar trabajo extra?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se eliminará sólo este extra independiente. Las jornadas y el estado F/? no cambian.")
                record?.let { Text("${it.snapshot.className} · ${minutesLabel(it.durationMinutes)}") }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = actions.confirmDelete,
                enabled = record != null && !state.isSaving,
                modifier = Modifier.testTag("extra-delete-confirm"),
            ) { Text(if (state.isSaving) "Eliminando…" else "Eliminar") }
        },
        dismissButton = {
            TextButton(onClick = actions.dismissDelete, enabled = !state.isSaving) { Text("Cancelar") }
        },
    )
}

@Composable
fun IndependentExtraDetailCard(
    record: IndependentExtraWorkRecord,
    onCorrect: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("independent-extra-${record.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(18.dp).background(Color(record.snapshot.colorArgb), CircleShape))
                Text("Trabajo extra independiente", fontWeight = FontWeight.Bold)
            }
            Text("${record.snapshot.workPlaceName} (${record.snapshot.workPlaceAbbreviation})")
            Text("${record.start.visibleAt(record.zoneId)} – ${record.end.visibleAt(record.zoneId)}")
            Text("Duración: ${minutesLabel(record.durationMinutes)}")
            Text("Clase: ${record.snapshot.className}")
            Text(if (record.snapshot.helpsMeetHoursReference) "Ayuda a la referencia" else "No ayuda a la referencia")
            record.snapshot.position?.let { Text("Puesto: $it") }
            OutlinedButton(
                onClick = onCorrect,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("correct-independent-extra-${record.id}"),
            ) { Text("Corregir trabajo extra") }
            TextButton(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("delete-independent-extra-${record.id}"),
            ) { Text("Eliminar trabajo extra") }
        }
    }
}

@Composable
private fun NeutralLoading(message: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EditorLoadError(message: String?, retry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(
            title = "No pudimos leer los datos",
            message = message ?: "Las fuentes laborales no están disponibles.",
            actionLabel = "Reintentar",
            onAction = retry,
        )
    }
}

@Composable
private fun ReferenceChoiceRow(
    label: String,
    choice: HoursReferenceChoice,
    draft: HoursReferenceDraft,
    actions: HoursAndExtrasActions,
) = SimpleChoice(label, draft.choice == choice, tag = "hours-reference-${choice.name.lowercase()}") {
    actions.updateReferenceDraft { current ->
        current.copy(
            choice = choice,
            periodChoice = when (choice) {
                HoursReferenceChoice.PENDING,
                HoursReferenceChoice.NOT_USED,
                -> HoursPeriodChoice.NONE
                else -> current.periodChoice
            },
            startChoice = if (
                choice == HoursReferenceChoice.PENDING || choice == HoursReferenceChoice.NOT_USED
            ) {
                ReferenceStartChoice.TODAY
            } else {
                current.startChoice
            },
        )
    }
}

@Composable
private fun SimpleChoice(
    label: String,
    isSelected: Boolean,
    tag: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(tag?.let { Modifier.testTag(it) } ?: Modifier)
            .semantics {
                selected = isSelected
                role = Role.RadioButton
            }
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(18.dp).background(
                if (isSelected) MaterialTheme.vigiliaColors.active else MaterialTheme.colorScheme.outlineVariant,
                CircleShape,
            ),
        )
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ConfirmRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ProgressLine(label: String, minutes: Long, emphasized: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal)
        Text(minutesLabel(minutes), fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun minutesLabel(minutes: Long): String {
    val hours = minutes / 60L
    val remainder = minutes % 60L
    return when {
        hours == 0L -> "$remainder min"
        remainder == 0L -> "$hours h"
        else -> "$hours h $remainder min"
    }
}

private fun targetLabel(target: HoursTargetState): String = when (target) {
    HoursTargetState.PendingSetup -> "Todavía no configurada"
    HoursTargetState.NotUsed -> "No se usa"
    HoursTargetState.Unknown -> "Cantidad desconocida"
    HoursTargetState.MissingPerPeriodValue -> "Falta informar"
    is HoursTargetState.Defined -> minutesLabel(target.requiredMinutes.value)
}

private fun referenceLabel(reference: HoursReference): String = when (reference) {
    HoursReference.PendingSetup -> "Todavía no configurada"
    HoursReference.NotUsed -> "Sin referencia de horas"
    is HoursReference.Unknown -> "Referencia con cantidad desconocida"
    is HoursReference.Fixed -> "Referencia fija"
    is HoursReference.PerPeriod -> "Referencia variable por período"
}

private fun HoursPeriod.visibleLabel(): String = when (this) {
    HoursPeriod.Monthly -> "Mes calendario"
    is HoursPeriod.Weekly -> {
        val day = firstDay.getDisplayName(TextStyle.FULL, SpanishArgentina)
            .replaceFirstChar { it.lowercase(SpanishArgentina) }
        "Semana que comienza el $day"
    }
    is HoursPeriod.Cycle -> "Ciclo de $lengthDays días, anclado el ${anchorDate.visibleDate()}"
}

private fun java.time.LocalDate.visibleNextStartLabel(period: HoursPeriod): String = when (period) {
    HoursPeriod.Monthly -> "Desde el ${visibleDate()}"
    is HoursPeriod.Weekly -> {
        val day = period.firstDay.getDisplayName(TextStyle.FULL, SpanishArgentina)
            .replaceFirstChar { it.lowercase(SpanishArgentina) }
        "Desde el próximo $day (${visibleDate()})"
    }
    is HoursPeriod.Cycle -> "Desde el próximo inicio del ciclo (${visibleDate()})"
}

private fun java.time.LocalDate.visibleDate(): String = format(
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu", SpanishArgentina),
)

private fun HoursReference.reviewTargetLabel(
    history: WorkConfigurationHistory?,
    review: HoursReferenceReview,
): String = when (this) {
    HoursReference.PendingSetup -> "Todavía no configurada"
    HoursReference.NotUsed -> "No se usa"
    is HoursReference.Unknown -> "Cantidad desconocida"
    is HoursReference.Fixed -> minutesLabel(requiredMinutes.value)
    is HoursReference.PerPeriod -> {
        val entry = review.initialValue ?: history?.perPeriodHoursValues?.let { values ->
            when (val lookup = values.valueFor(keyContaining(review.startedOn))) {
                PerPeriodHoursLookup.Missing -> null
                is PerPeriodHoursLookup.Defined -> lookup.entry
            }
        }
        entry?.requiredMinutes?.value?.let(::minutesLabel) ?: "Falta informar"
    }
}

private fun Int.toArgbLabel(): String = "#%08X".format(this)

private fun draftPreviewPeriod(draft: HoursReferenceDraft): HoursPeriod? = when (draft.periodChoice) {
    HoursPeriodChoice.NONE -> null
    HoursPeriodChoice.MONTHLY -> HoursPeriod.Monthly
    HoursPeriodChoice.WEEKLY -> HoursPeriod.Weekly(draft.weeklyFirstDay)
    HoursPeriodChoice.CYCLE -> {
        val date = runCatching { java.time.LocalDate.parse(draft.cycleAnchorDate) }.getOrNull()
        val length = draft.cycleLengthDays.toIntOrNull()
        if (date != null && length != null && length > 0) HoursPeriod.Cycle(date, length) else null
    }
}

private fun java.time.Instant.visibleAt(zoneId: ZoneId): String =
    atZone(zoneId).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", SpanishArgentina))

private val SpanishArgentina = Locale.forLanguageTag("es-AR")
private const val DEFAULT_EXTRA_COLOR: Int = 0xFF5C4DFF.toInt()
