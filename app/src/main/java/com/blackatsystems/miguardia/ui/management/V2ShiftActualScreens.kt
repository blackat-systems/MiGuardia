package com.blackatsystems.miguardia.ui.management

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftActualAggregate
import com.blackatsystems.miguardia.core.domain.model.ShiftActualDifferenceChoice
import com.blackatsystems.miguardia.core.domain.work.ExtraWorkClass
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.AutomaticTimeField
import com.blackatsystems.miguardia.ui.components.AdvancedOptionsSection
import com.blackatsystems.miguardia.ui.components.ContextHelp
import com.blackatsystems.miguardia.ui.components.ContextHelpButton
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.components.SurfaceHeader
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class V2ShiftActualActions(
    val resume: (WorkSetupState) -> Unit = {},
    val inspectDay: (WorkSetupState, LocalDate, List<Shift>, Instant) -> Unit = { _, _, _, _ -> },
    val retryInspection: () -> Unit = {},
    val clearInspection: () -> Unit = {},
    val begin: (UUID, Int, Int, LocalDate) -> Boolean = { _, _, _, _ -> false },
    val updateDraft: (((V2ActualEditorDraft) -> V2ActualEditorDraft)) -> Unit = {},
    val next: () -> Unit = {},
    val back: () -> Unit = {},
    val addFragment: () -> Unit = {},
    val updateFragment: (UUID, (V2ActualFragmentInput) -> V2ActualFragmentInput) -> Unit = { _, _ -> },
    val removeFragment: (UUID) -> Unit = {},
    val startInlineClass: () -> Unit = {},
    val cancelInlineClass: () -> Unit = {},
    val retryClasses: () -> Unit = {},
    val refreshEditorSource: () -> Unit = {},
    val save: () -> Unit = {},
    val requestReturnToPlanned: (UUID) -> Boolean = { false },
    val dismissReturnConfirmation: () -> Unit = {},
    val confirmReturnToPlanned: () -> Unit = {},
    val openCatalog: (WorkSetupState) -> Unit = {},
    val startNewClass: () -> Unit = {},
    val editClass: (UUID) -> Unit = {},
    val updateClassEditor: ((V2ExtraClassEditorState) -> V2ExtraClassEditorState) -> Unit = {},
    val saveClass: () -> Unit = {},
    val toggleClassActive: (UUID) -> Unit = {},
    val cancelClassEditor: () -> Unit = {},
    val close: () -> Unit = {},
    val dismissDiscardConfirmation: () -> Unit = {},
    val confirmDiscard: () -> Unit = {},
    val discardUnavailableRestoredDraft: () -> Unit = {},
    val clearMessage: () -> Unit = {},
    val consumeSuccess: (Int) -> Unit = {},
) {
    companion object {
        fun from(viewModel: V2ShiftActualViewModel): V2ShiftActualActions = V2ShiftActualActions(
            resume = viewModel::resume,
            inspectDay = viewModel::inspectDay,
            retryInspection = viewModel::retryInspection,
            clearInspection = viewModel::clearInspection,
            begin = viewModel::begin,
            updateDraft = viewModel::updateDraft,
            next = viewModel::next,
            back = viewModel::back,
            addFragment = viewModel::addFragment,
            updateFragment = viewModel::updateFragment,
            removeFragment = viewModel::removeFragment,
            startInlineClass = viewModel::startInlineClass,
            cancelInlineClass = viewModel::cancelInlineClass,
            retryClasses = viewModel::retryClasses,
            refreshEditorSource = viewModel::refreshEditorSource,
            save = viewModel::save,
            requestReturnToPlanned = viewModel::requestReturnToPlanned,
            dismissReturnConfirmation = viewModel::dismissReturnConfirmation,
            confirmReturnToPlanned = viewModel::confirmReturnToPlanned,
            openCatalog = viewModel::openCatalog,
            startNewClass = viewModel::startNewClass,
            editClass = viewModel::editClass,
            updateClassEditor = viewModel::updateClassEditor,
            saveClass = viewModel::saveClass,
            toggleClassActive = viewModel::toggleClassActive,
            cancelClassEditor = viewModel::cancelClassEditor,
            close = viewModel::close,
            dismissDiscardConfirmation = viewModel::dismissDiscardConfirmation,
            confirmDiscard = viewModel::confirmDiscard,
            discardUnavailableRestoredDraft = viewModel::discardUnavailableRestoredDraft,
            clearMessage = viewModel::clearMessage,
            consumeSuccess = viewModel::consumeSuccess,
        )
    }
}

@Composable
fun V2ShiftActualDetailCard(
    shift: Shift,
    ordinal: Int,
    count: Int,
    ownerDate: LocalDate,
    rowState: V2ShiftActualRowState?,
    actions: V2ShiftActualActions,
    modifier: Modifier = Modifier,
) {
    val tagPrefix = "v2-actual-${shift.id}"
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("$tagPrefix-card")
            .semantics {
                contentDescription = "Horario real de la jornada $ordinal de $count, identificador ${shift.id}"
            },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Planificado / Real", fontWeight = FontWeight.Bold)
            Text("Planificado: ${shift.startAt.visibleAt(shift.zoneId)} – ${shift.endAt.visibleAt(shift.zoneId)}")
            Text("Zona: ${shift.zoneId.id}", style = MaterialTheme.typography.bodySmall)
            when (rowState) {
                null,
                V2ShiftActualRowState.Loading,
                -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.testTag("$tagPrefix-loading"))
                        Text("Leyendo horario real…")
                    }
                }

                is V2ShiftActualRowState.Error -> {
                    Text(rowState.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(
                        onClick = actions.retryInspection,
                        modifier = Modifier.fillMaxWidth().testTag("$tagPrefix-retry"),
                    ) { Text("Reintentar") }
                }

                is V2ShiftActualRowState.Content -> {
                    val actual = rowState.expectation.previousActual
                    if (actual == null) {
                        Text("Todavía no hay un horario real guardado.")
                        rowState.unavailableMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (rowState.canRegister) {
                            Button(
                                onClick = { actions.begin(shift.id, ordinal, count, ownerDate) },
                                modifier = Modifier.fillMaxWidth().testTag("$tagPrefix-register"),
                            ) { Text("Registrar horario real") }
                        }
                    } else {
                        ActualSummary(actual, shift.zoneId)
                        Button(
                            onClick = { actions.begin(shift.id, ordinal, count, ownerDate) },
                            modifier = Modifier.fillMaxWidth().testTag("$tagPrefix-correct"),
                        ) { Text("Corregir horario real") }
                        OutlinedButton(
                            onClick = { actions.requestReturnToPlanned(shift.id) },
                            modifier = Modifier.fillMaxWidth().testTag("$tagPrefix-return-planned"),
                        ) { Text("Volver al horario planificado") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActualSummary(actual: ShiftActualAggregate, zoneId: ZoneId) {
    Text("Real: ${actual.record.actualStart.visibleAt(zoneId)} – ${actual.record.actualEnd.visibleAt(zoneId)}")
    Text("Motivo: ${actual.record.differenceReason}")
    actual.record.explanation?.let { Text("Explicación: $it") }
    Text("Horas trabajadas sin extras: ${actual.regularMinutes.asHoursAndMinutes()}")
    actual.extraIntervals.forEachIndexed { index, fragment ->
        Text(
            "Extra ${index + 1}: ${fragment.classNameSnapshot} · " +
                "${fragment.start.visibleAt(zoneId)} – ${fragment.end.visibleAt(zoneId)} · " +
                fragment.durationMinutes.asHoursAndMinutes(),
        )
    }
    Text("Total real: ${actual.totalMinutes.asHoursAndMinutes()}", fontWeight = FontWeight.SemiBold)
}

@Composable
fun V2ShiftActualSurfaceHost(
    state: V2ShiftActualUiState,
    actions: V2ShiftActualActions,
) {
    if (state.surface == V2ShiftActualSurface.NONE && state.restoredDraftError == null) return
    if (state.surface != V2ShiftActualSurface.NONE) {
        BackHandler(enabled = !state.isSaving && !state.isRefreshingSource) {
            if (state.surface == V2ShiftActualSurface.EDITOR) actions.back() else actions.close()
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (state.surface) {
                V2ShiftActualSurface.EDITOR -> ActualEditorScreen(state, actions)
                V2ShiftActualSurface.CLASS_CATALOG -> ExtraClassCatalogScreen(state, actions)
                V2ShiftActualSurface.NONE -> Unit
            }
        }
    } else {
        BackHandler(enabled = true) { }
    }
    state.restoredDraftError?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("No pudimos recuperar el borrador") },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = actions.retryInspection,
                    modifier = Modifier.testTag("v2-actual-restored-retry"),
                ) { Text("Reintentar lectura") }
            },
            dismissButton = {
                TextButton(
                    onClick = actions.discardUnavailableRestoredDraft,
                    modifier = Modifier.testTag("v2-actual-restored-discard"),
                ) { Text("Descartar borrador") }
            },
        )
    }
    state.editor?.takeIf { it.showReturnConfirmation }?.let { editor ->
        AlertDialog(
            onDismissRequest = actions.dismissReturnConfirmation,
            title = { Text("¿Volver al horario planificado?") },
            text = {
                Text(
                    "Se quitarán únicamente el horario real y sus extras de la jornada ${editor.ordinal} de ${editor.count}. " +
                        "Las notas, avisos y demás datos se conservan.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = actions.confirmReturnToPlanned,
                    enabled = !state.isSaving,
                    modifier = Modifier.testTag("v2-actual-confirm-return-planned"),
                ) { Text("Volver al planificado") }
            },
            dismissButton = {
                TextButton(onClick = actions.dismissReturnConfirmation, enabled = !state.isSaving) {
                    Text("Conservar horario real")
                }
            },
        )
    }
    state.editor?.takeIf { it.showDiscardConfirmation && !it.showReturnConfirmation }?.let {
        AlertDialog(
            onDismissRequest = actions.dismissDiscardConfirmation,
            title = { Text("¿Descartar este borrador?") },
            text = {
                Text("Los cambios que todavía no guardaste se perderán. El horario real persistido no se modificará.")
            },
            confirmButton = {
                TextButton(
                    onClick = actions.confirmDiscard,
                    enabled = !state.isSaving && !state.isRefreshingSource,
                    modifier = Modifier.testTag("v2-actual-confirm-discard"),
                ) { Text("Descartar borrador") }
            },
            dismissButton = {
                TextButton(onClick = actions.dismissDiscardConfirmation) { Text("Seguir editando") }
            },
        )
    }
}

@Composable
private fun ActualEditorScreen(state: V2ShiftActualUiState, actions: V2ShiftActualActions) {
    val editor = state.editor ?: return
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        SurfaceHeader("Horario real", "Cerrar", actions.close)
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("v2-actual-editor-${editor.expectation.planned.shift.id}"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Jornada ${editor.ordinal} de ${editor.count}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text("UUID: ${editor.expectation.planned.shift.id}", style = MaterialTheme.typography.bodySmall)
            editor.errorMessage?.let { PersistentMessage(it) }
            if (editor.sourceConflict) {
                OutlinedButton(
                    onClick = actions.refreshEditorSource,
                    enabled = !state.isSaving && !state.isRefreshingSource,
                    modifier = Modifier.fillMaxWidth().testTag("v2-actual-refresh-source"),
                ) {
                    Text(if (state.isRefreshingSource) "Refrescando…" else "Refrescar jornada conservando borrador")
                }
            }
            when (editor.stage) {
                V2ShiftActualStage.IDENTITY -> IdentityStage(editor)
                V2ShiftActualStage.ACTUAL_TIME -> ActualTimeStage(editor, actions)
                V2ShiftActualStage.CLASSIFICATION -> ClassificationStage(state, editor, actions)
                V2ShiftActualStage.REVIEW -> ReviewStage(editor)
            }
            if (editor.expectation.previousActual != null) {
                OutlinedButton(
                    onClick = { actions.requestReturnToPlanned(editor.expectation.planned.shift.id) },
                    enabled = !state.isSaving && !state.isRefreshingSource,
                    modifier = Modifier.fillMaxWidth().testTag("v2-actual-editor-return-planned"),
                ) { Text("Volver al horario planificado") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = actions.back,
                    enabled = !state.isSaving && !state.isRefreshingSource,
                    modifier = Modifier.weight(1f),
                ) { Text(if (editor.stage == V2ShiftActualStage.IDENTITY) "Cancelar" else "Atrás") }
                Button(
                    onClick = if (editor.stage == V2ShiftActualStage.REVIEW) actions.save else actions.next,
                    enabled = !state.isSaving && !state.isRefreshingSource && !editor.sourceConflict,
                    modifier = Modifier.weight(1f).testTag("v2-actual-next-save"),
                ) {
                    Text(
                        when {
                            state.isSaving -> "Guardando…"
                            state.isRefreshingSource -> "Refrescando…"
                            editor.stage == V2ShiftActualStage.REVIEW -> "Guardar"
                            else -> "Continuar"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentityStage(editor: V2ShiftActualEditorState) {
    val shift = editor.expectation.planned.shift
    SectionCard(
        title = "Identidad y planificación",
        supportingText = "La jornada sigue perteneciendo al ${editor.ownerDate}. Su planificación no se modificará.",
    ) {
        Text("Lugar: ${shift.objectiveNameSnapshot} (${shift.objectiveAbbreviationSnapshot})")
        Text("Planificado: ${shift.startAt.visibleAt(shift.zoneId)} – ${shift.endAt.visibleAt(shift.zoneId)}")
        Text("Zona inmutable: ${shift.zoneId.id}")
    }
}

@Composable
private fun ActualTimeStage(editor: V2ShiftActualEditorState, actions: V2ShiftActualActions) {
    val draft = editor.draft
    val shift = editor.expectation.planned.shift
    SectionCard(
        title = "Horario realmente trabajado",
        supportingText = "Ingresá fecha y hora completas. El intervalo usa minutos enteros y debe haber terminado.",
    ) {
        DateTimeFields(
            prefix = "Inicio real",
            date = draft.startDate,
            time = draft.startTime,
            offset = draft.startOffset,
            zoneId = shift.zoneId,
            tag = "v2-actual-start",
            onDate = { value -> actions.updateDraft { it.copy(startDate = value, startOffset = null) } },
            onTime = { value -> actions.updateDraft { it.copy(startTime = value, startOffset = null) } },
            onOffset = { value -> actions.updateDraft { it.copy(startOffset = value.ifBlank { null }) } },
        )
        DateTimeFields(
            prefix = "Final real",
            date = draft.endDate,
            time = draft.endTime,
            offset = draft.endOffset,
            zoneId = shift.zoneId,
            tag = "v2-actual-end",
            onDate = { value -> actions.updateDraft { it.copy(endDate = value, endOffset = null) } },
            onTime = { value -> actions.updateDraft { it.copy(endTime = value, endOffset = null) } },
            onOffset = { value -> actions.updateDraft { it.copy(endOffset = value.ifBlank { null }) } },
        )
        Text("Zona: ${editor.expectation.planned.shift.zoneId.id} (no editable)")
        OutlinedTextField(
            value = draft.reason,
            onValueChange = { value -> actions.updateDraft { it.copy(reason = value) } },
            label = { Text("Motivo de la diferencia") },
            supportingText = { Text("Obligatorio si el horario real difiere del planificado") },
            modifier = Modifier.fillMaxWidth().testTag("v2-actual-reason"),
        )
        OutlinedTextField(
            value = draft.explanation,
            onValueChange = { value -> actions.updateDraft { it.copy(explanation = value) } },
            label = { Text("Explicación opcional") },
            modifier = Modifier.fillMaxWidth().testTag("v2-actual-explanation"),
        )
    }
}

@Composable
private fun DateTimeFields(
    prefix: String,
    date: String,
    time: String,
    offset: String?,
    zoneId: ZoneId,
    tag: String,
    onDate: (String) -> Unit,
    onTime: (String) -> Unit,
    onOffset: (String) -> Unit,
) {
    Text(prefix, fontWeight = FontWeight.SemiBold)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 360.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = onDate,
                    label = { Text("Fecha AAAA-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("$tag-date"),
                )
                AutomaticTimeField(
                    value = time,
                    onValueChange = onTime,
                    label = "Hora",
                    modifier = Modifier.fillMaxWidth().testTag("$tag-time"),
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = onDate,
                    label = { Text("Fecha AAAA-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.weight(1.35f).testTag("$tag-date"),
                )
                AutomaticTimeField(
                    value = time,
                    onValueChange = onTime,
                    label = "Hora",
                    modifier = Modifier.weight(1f).testTag("$tag-time"),
                )
            }
        }
    }
    val validOffsets = runCatching {
        zoneId.rules.getValidOffsets(
            LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)),
        )
    }.getOrDefault(emptyList())
    if (validOffsets.size > 1) {
        SectionCard(
            title = "Esta hora aparece dos veces",
            supportingText = "Elegí la primera o la segunda. Esto sólo ocurre en lugares que cambian el reloj.",
        ) {
            ContextHelpButton(
                ContextHelp(
                    title = "Hora repetida por cambio de reloj",
                    whatItDoes = "Distingue dos instantes que comparten la misma fecha y hora local.",
                    howToUseIt = "Elegí Primera vez salvo que sepas que trabajaste después de que el reloj retrocedió; en ese caso elegí Segunda vez.",
                    example = "Si el reloj pasa de 03:00 a 02:00, las 02:30 ocurren dos veces.",
                ),
            )
            validOffsets.forEachIndexed { index, validOffset ->
                ChoiceButton(
                    label = if (index == 0) "Primera vez" else "Segunda vez",
                    selected = offset == validOffset.id,
                    tag = "$tag-offset-${index + 1}",
                    onClick = { onOffset(validOffset.id) },
                )
            }
        }
    }
}

@Composable
private fun ClassificationStage(
    state: V2ShiftActualUiState,
    editor: V2ShiftActualEditorState,
    actions: V2ShiftActualActions,
) {
    val draft = editor.draft
    SectionCard(
        title = "Clasificar la diferencia",
        supportingText = "MiGuardia no crea extras por tu meta, cobertura, noche, feriado ni fin de semana.",
    ) {
        ChoiceButton(
            label = "Contarla como horas normales",
            selected = draft.choice == ShiftActualDifferenceChoice.ALL_REGULAR,
            tag = "v2-actual-choice-regular",
            onClick = {
                actions.updateDraft {
                    it.copy(
                        choice = ShiftActualDifferenceChoice.ALL_REGULAR,
                        selectedClassId = null,
                        selectedClassUpdatedAt = null,
                        isCreatingInlineClass = false,
                        inlineClassId = null,
                        inlineClassName = "",
                        inlineHelpsReference = null,
                        inlineDedicatedSummary = null,
                        fragments = emptyList(),
                    )
                }
            },
        )
        ChoiceButton(
            label = "La diferencia corresponde a un tipo de horas extra",
            selected = draft.choice == ShiftActualDifferenceChoice.EXTRA_CLASS,
            tag = "v2-actual-choice-extra",
            onClick = { actions.updateDraft { it.copy(choice = ShiftActualDifferenceChoice.EXTRA_CLASS) } },
        )
        if (draft.choice == ShiftActualDifferenceChoice.EXTRA_CLASS) {
            Text("Tipo de horas extra", fontWeight = FontWeight.SemiBold)
            if (state.isLoadingClasses) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.testTag("v2-actual-classes-loading"))
                    Text("Leyendo tipos de horas extra…")
                }
            }
            state.classesLoadError?.let { message ->
                PersistentMessage(message)
                OutlinedButton(
                    onClick = actions.retryClasses,
                    modifier = Modifier.fillMaxWidth().testTag("v2-actual-classes-retry"),
                ) { Text("Reintentar tipos") }
            }
            state.classes
                .filter { it.isActive || it.id == draft.selectedClassId }
                .forEach { extraClass ->
                    ChoiceButton(
                        label = extraClass.name + if (extraClass.isActive) "" else " (archivada; sólo conservar)",
                        selected = draft.selectedClassId == extraClass.id && !draft.isCreatingInlineClass,
                        tag = "v2-actual-class-${extraClass.id}",
                        onClick = {
                            if (extraClass.isActive || editor.expectation.observedClass?.id == extraClass.id) {
                                actions.updateDraft {
                                    it.copy(
                                        selectedClassId = extraClass.id,
                                        selectedClassUpdatedAt = extraClass.updatedAt.toString(),
                                        isCreatingInlineClass = false,
                                        inlineClassId = null,
                                        inlineClassName = "",
                                        inlineHelpsReference = null,
                                        inlineDedicatedSummary = null,
                                    )
                                }
                            }
                        },
                    )
                }
            OutlinedButton(
                onClick = actions.startInlineClass,
                enabled = !state.isLoadingClasses && state.classesLoadError == null,
                modifier = Modifier.fillMaxWidth().testTag("v2-actual-inline-class"),
            ) { Text("Crear tipo al guardar") }
            if (draft.isCreatingInlineClass) InlineClassDraft(draft, actions)
            Text("Fragmentos exactos", fontWeight = FontWeight.SemiBold)
            Text(
                "Elegí las porciones reales fuera del intervalo planificado. Deben sumar exactamente la diferencia.",
                style = MaterialTheme.typography.bodySmall,
            )
            draft.fragments.forEachIndexed { index, fragment ->
                FragmentEditor(index, fragment, editor.expectation.planned.shift.zoneId, actions)
            }
            OutlinedButton(
                onClick = actions.addFragment,
                modifier = Modifier.fillMaxWidth().testTag("v2-actual-add-fragment"),
            ) { Text("Agregar fragmento") }
        }
    }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics { this.selected = selected },
    ) { Text(if (selected) "✓ $label" else label) }
}

@Composable
private fun InlineClassDraft(draft: V2ActualEditorDraft, actions: V2ShiftActualActions) {
    SectionCard(
        title = "Nueva clase",
        supportingText = "Será un borrador hasta la confirmación final del horario real.",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Horas extras", "Extensión de turno", "Servicio extra").forEach { suggestion ->
                TextButton(onClick = { actions.updateDraft { it.copy(inlineClassName = suggestion) } }) {
                    Text(suggestion)
                }
            }
        }
        OutlinedTextField(
            value = draft.inlineClassName,
            onValueChange = { value -> actions.updateDraft { it.copy(inlineClassName = value) } },
            label = { Text("Nombre") },
            modifier = Modifier.fillMaxWidth().testTag("v2-actual-inline-name"),
        )
        val missingAnswers = listOf(draft.inlineHelpsReference, draft.inlineDedicatedSummary).count { it == null }
        if (missingAnswers > 0) Text("Faltan $missingAnswers respuestas en Opciones avanzadas.")
        AdvancedOptionsSection(
            help = ContextHelp(
                title = "Opciones de esta hora extra",
                whatItDoes = "Define si cuenta para tu meta y si querés verla por separado.",
                howToUseIt = "Respondé ambas preguntas según tu forma de trabajo.",
                example = "Una extensión puede sumar al total trabajado sin contar para tu meta.",
            ),
        ) {
            YesNoChoice(
                title = "¿Estas horas extra cuentan para tu meta?",
                value = draft.inlineHelpsReference,
                tag = "v2-actual-inline-helps",
                onValue = { value -> actions.updateDraft { it.copy(inlineHelpsReference = value) } },
            )
            ContextHelpButton(
                ContextHelp(
                    title = "Cuenta para tu meta",
                    whatItDoes = "Decide si estas horas se suman al avance de tu meta.",
                    howToUseIt = "Elegí Sí si completan la meta; No si sólo querés sumarlas al total trabajado.",
                    example = "Con 150 h y una meta de 160 h, 10 h con Sí completan la meta. Con No, el total es 160 h pero la meta sigue en 150 h.",
                ),
            )
            YesNoChoice(
                title = "¿Querés ver este tipo por separado?",
                value = draft.inlineDedicatedSummary,
                tag = "v2-actual-inline-dedicated",
                onValue = { value -> actions.updateDraft { it.copy(inlineDedicatedSummary = value) } },
            )
            ContextHelpButton(
                ContextHelp(
                    title = "Ver este tipo por separado",
                    whatItDoes = "Muestra una fila con el nombre de este tipo sin cambiar ningún total.",
                    howToUseIt = "Elegí Sí para distinguirlo; No para dejarlo sólo dentro de Extras y Total trabajado.",
                    example = "Con Sí: Servicio extra, 8 h. Con No, las 8 h siguen contando en Extras.",
                ),
            )
        }
        TextButton(onClick = actions.cancelInlineClass) { Text("Cancelar tipo nuevo") }
    }
}

@Composable
private fun YesNoChoice(title: String, value: Boolean?, tag: String, onValue: (Boolean) -> Unit) {
    Text(title)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(true to "Sí", false to "No").forEach { (choice, label) ->
            OutlinedButton(
                onClick = { onValue(choice) },
                modifier = Modifier.weight(1f).testTag("$tag-${label.lowercase()}")
                    .semantics { selected = value == choice },
            ) { Text(if (value == choice) "✓ $label" else label) }
        }
    }
}

@Composable
private fun FragmentEditor(
    index: Int,
    fragment: V2ActualFragmentInput,
    zoneId: ZoneId,
    actions: V2ShiftActualActions,
) {
    SectionCard(title = "Fragmento ${index + 1}", supportingText = "UUID: ${fragment.id}") {
        DateTimeFields(
            prefix = "Inicio extra",
            date = fragment.startDate,
            time = fragment.startTime,
            offset = fragment.startOffset,
            zoneId = zoneId,
            tag = "v2-actual-fragment-${fragment.id}-start",
            onDate = { value -> actions.updateFragment(fragment.id) { it.copy(startDate = value, startOffset = null) } },
            onTime = { value -> actions.updateFragment(fragment.id) { it.copy(startTime = value, startOffset = null) } },
            onOffset = { value -> actions.updateFragment(fragment.id) { it.copy(startOffset = value.ifBlank { null }) } },
        )
        DateTimeFields(
            prefix = "Final extra",
            date = fragment.endDate,
            time = fragment.endTime,
            offset = fragment.endOffset,
            zoneId = zoneId,
            tag = "v2-actual-fragment-${fragment.id}-end",
            onDate = { value -> actions.updateFragment(fragment.id) { it.copy(endDate = value, endOffset = null) } },
            onTime = { value -> actions.updateFragment(fragment.id) { it.copy(endTime = value, endOffset = null) } },
            onOffset = { value -> actions.updateFragment(fragment.id) { it.copy(endOffset = value.ifBlank { null }) } },
        )
        TextButton(onClick = { actions.removeFragment(fragment.id) }) { Text("Quitar fragmento") }
    }
}

@Composable
private fun ReviewStage(editor: V2ShiftActualEditorState) {
    val mutation = editor.preparedMutation ?: return
    val shift = editor.expectation.planned.shift
    SectionCard(
        title = "Revisar antes de guardar",
        supportingText = "Sólo esta confirmación escribe el horario real, sus fragmentos y una clase nueva si corresponde.",
    ) {
        Text("Jornada ${editor.ordinal} de ${editor.count} · ${shift.id}")
        Text("Fecha dueña: ${editor.ownerDate}")
        Text("Planificado: ${shift.startAt.visibleAt(shift.zoneId)} – ${shift.endAt.visibleAt(shift.zoneId)}")
        ActualSummary(mutation.replacement, shift.zoneId)
        mutation.classToCreate?.let { newClass ->
            Text("Se creará el tipo de horas extra: ${newClass.name}", fontWeight = FontWeight.SemiBold)
        }
        Text("Este paso sólo guarda lo que pasó en la jornada.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ExtraClassCatalogScreen(state: V2ShiftActualUiState, actions: V2ShiftActualActions) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        SurfaceHeader("Tipos de horas extra", "Cerrar", actions.close)
        HorizontalDivider()
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.infoMessage?.let { PersistentMessage(it, onDismiss = actions.clearMessage) }
            state.classesLoadError?.let { message ->
                PersistentMessage(message)
                OutlinedButton(
                    onClick = actions.retryClasses,
                    modifier = Modifier.fillMaxWidth().testTag("v2-extra-class-retry"),
                ) { Text("Reintentar") }
            }
            Text(
                "Estas clases sólo se usan cuando vos clasificás tiempo adicional concreto. No se crean extras automáticamente.",
            )
            Button(
                onClick = actions.startNewClass,
                enabled = !state.isSaving && !state.isLoadingClasses && state.classesLoadError == null,
                modifier = Modifier.fillMaxWidth().testTag("v2-extra-class-new"),
            ) { Text("Crear clase") }
            state.classEditor?.let { ClassEditor(it, state.isSaving, actions) }
            if (state.isLoadingClasses) CircularProgressIndicator()
            state.classes.forEach { extraClass -> ClassCatalogRow(extraClass, state.isSaving, actions) }
            if (!state.isLoadingClasses && state.classes.isEmpty()) {
                Text("Todavía no hay clases. Las sugerencias no se guardan hasta que completes una creación consciente.")
            }
        }
    }
}

@Composable
private fun ClassEditor(
    editor: V2ExtraClassEditorState,
    isSaving: Boolean,
    actions: V2ShiftActualActions,
) {
    SectionCard(
        title = if (editor.expected == null) "Nueva clase" else "Editar clase",
        supportingText = "Poné un nombre simple. Las decisiones técnicas quedan en Opciones avanzadas.",
    ) {
        val missingAnswers = listOf(editor.helpsReference, editor.dedicatedSummary).count { it == null }
        editor.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Horas extras", "Extensión de turno", "Servicio extra").forEach { suggestion ->
                TextButton(onClick = { actions.updateClassEditor { it.copy(name = suggestion) } }) {
                    Text(suggestion)
                }
            }
        }
        OutlinedTextField(
            value = editor.name,
            onValueChange = { value -> actions.updateClassEditor { it.copy(name = value) } },
            label = { Text("Nombre") },
            modifier = Modifier.fillMaxWidth().testTag("v2-extra-class-name"),
        )
        if (missingAnswers > 0) {
            Text("Faltan $missingAnswers respuestas en Opciones avanzadas.", color = MaterialTheme.colorScheme.primary)
        }
        AdvancedOptionsSection(
            help = ContextHelp(
                title = "Opciones de este tipo de horas extra",
                whatItDoes = "Define si estas horas cuentan para tu meta y si querés verlas por separado.",
                howToUseIt = "Respondé Sí o No en ambas preguntas. No hay una respuesta automática porque depende de tu forma de trabajo.",
                example = "Servicio extra puede sumar al total trabajado sin contar para una meta mensual.",
            ),
            initiallyExpanded = editor.expected != null,
        ) {
            YesNoChoice(
                "¿Estas horas extra cuentan para tu meta?",
                editor.helpsReference,
                "v2-extra-class-helps",
            ) { value -> actions.updateClassEditor { it.copy(helpsReference = value) } }
            ContextHelpButton(
                ContextHelp(
                    title = "Cuenta para tu meta",
                    whatItDoes = "Decide si las horas de este tipo se suman al avance de tu meta.",
                    howToUseIt = "Elegí Sí si se usan para completar la meta. Elegí No si querés registrarlas como trabajadas y extra, pero sin acercar la meta.",
                    example = "Meta 160 h: 150 h sin extras y 10 h de este tipo. Con Sí muestra 160 h; con No muestra 150 h, aunque el total trabajado sigue en 160 h.",
                ),
            )
            YesNoChoice(
                "¿Querés ver este tipo por separado?",
                editor.dedicatedSummary,
                "v2-extra-class-dedicated",
            ) { value -> actions.updateClassEditor { it.copy(dedicatedSummary = value) } }
            ContextHelpButton(
                ContextHelp(
                    title = "Ver este tipo por separado",
                    whatItDoes = "Muestra una fila con el nombre de este tipo en Tus horas y en el Resumen.",
                    howToUseIt = "Elegí Sí si querés distinguirlo. Elegí No si alcanza con verlo dentro de Extras y Total trabajado.",
                    example = "Con Sí: Servicio extra, 8 h. Con No, esas 8 h siguen dentro de Extras y Total trabajado.",
                ),
            )
            Text("Cambiar estas opciones no reinterpreta horas que ya guardaste.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions.cancelClassEditor, modifier = Modifier.weight(1f)) {
                Text("Cancelar")
            }
            Button(
                onClick = actions.saveClass,
                enabled = !isSaving && editor.name.isNotBlank() &&
                    editor.helpsReference != null && editor.dedicatedSummary != null,
                modifier = Modifier.weight(1f).testTag("v2-extra-class-save"),
            ) { Text(if (isSaving) "Guardando…" else "Guardar") }
        }
    }
}

@Composable
private fun ClassCatalogRow(
    extraClass: ExtraWorkClass,
    isSaving: Boolean,
    actions: V2ShiftActualActions,
) {
    SectionCard(
        title = extraClass.name,
        supportingText = if (extraClass.isActive) "Activa" else "Archivada",
    ) {
        Text(if (extraClass.helpsMeetHoursReference) "Cuenta para tu meta: Sí" else "Cuenta para tu meta: No")
        Text(if (extraClass.showDedicatedSummary) "Se ve por separado: Sí" else "Se ve por separado: No")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { actions.editClass(extraClass.id) },
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
            ) { Text("Editar") }
            OutlinedButton(
                onClick = { actions.toggleClassActive(extraClass.id) },
                enabled = !isSaving,
                modifier = Modifier.weight(1f).testTag("v2-extra-class-toggle-${extraClass.id}"),
            ) { Text(if (extraClass.isActive) "Archivar" else "Reactivar") }
        }
    }
}

private fun Instant.visibleAt(zoneId: ZoneId): String =
    VISIBLE_DATE_TIME.format(atZone(zoneId))

private fun Long.asHoursAndMinutes(): String =
    "${this / 60} h ${this % 60} min"

private val VISIBLE_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm XXX")
