package com.blackatsystems.miguardia.ui.availability

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.model.AvailabilityBreakdown
import com.blackatsystems.miguardia.core.domain.model.AvailabilityTemporalState
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.ui.components.EmptyState
import com.blackatsystems.miguardia.ui.components.AutomaticTimeField
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.components.SurfaceHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class AvailabilityActions(
    val retry: () -> Unit = {},
    val openOverview: () -> Unit = {},
    val close: () -> Unit = {},
    val dismissDiscard: () -> Unit = {},
    val confirmDiscard: () -> Unit = {},
    val clearMessage: () -> Unit = {},
    val openConfiguration: () -> Unit = {},
    val updateConfiguration: (AvailabilityConfigurationDraft) -> Unit = {},
    val reviewConfiguration: () -> Unit = {},
    val backConfiguration: () -> Unit = {},
    val saveConfiguration: () -> Unit = {},
    val openCreate: (LocalDate) -> Unit = {},
    val openCorrect: (AvailabilityWindowRecord) -> Unit = {},
    val updateWindow: (AvailabilityWindowDraftState) -> Unit = {},
    val reviewWindow: () -> Unit = {},
    val backWindow: () -> Unit = {},
    val saveWindow: () -> Unit = {},
    val requestDelete: (AvailabilityWindowRecord) -> Unit = {},
    val dismissDelete: () -> Unit = {},
    val confirmDelete: () -> Unit = {},
) {
    companion object {
        fun from(viewModel: AvailabilityViewModel): AvailabilityActions = AvailabilityActions(
            retry = viewModel::retry,
            openOverview = viewModel::openOverview,
            close = viewModel::close,
            dismissDiscard = viewModel::dismissDiscard,
            confirmDiscard = viewModel::confirmDiscard,
            clearMessage = viewModel::clearMessage,
            openConfiguration = viewModel::openConfiguration,
            updateConfiguration = viewModel::updateConfigurationDraft,
            reviewConfiguration = viewModel::reviewConfiguration,
            backConfiguration = viewModel::backConfiguration,
            saveConfiguration = viewModel::saveConfiguration,
            openCreate = viewModel::openCreate,
            openCorrect = viewModel::openCorrect,
            updateWindow = viewModel::updateWindowDraft,
            reviewWindow = viewModel::reviewWindow,
            backWindow = viewModel::backWindow,
            saveWindow = viewModel::saveWindow,
            requestDelete = viewModel::requestDelete,
            dismissDelete = viewModel::dismissDelete,
            confirmDelete = viewModel::confirmDelete,
        )
    }
}

@Composable
fun AvailabilitySurfaceHost(
    state: AvailabilityUiState,
    actions: AvailabilityActions,
) {
    when (state.surface) {
        AvailabilitySurface.NONE -> Unit
        AvailabilitySurface.OVERVIEW -> FullAvailabilitySurface("Guardias pasivas y disponibilidad", actions.close) {
            BackHandler(onBack = actions.close)
            AvailabilityOverview(state, actions)
        }
        AvailabilitySurface.CONFIG_EDITOR -> FullAvailabilitySurface("Configurar disponibilidad", actions.close) {
            BackHandler(onBack = actions.close)
            ConfigurationEditor(state, actions)
        }
        AvailabilitySurface.CONFIG_REVIEW -> FullAvailabilitySurface(
            "Revisar configuración",
            actions.backConfiguration,
            actionLabel = "Volver",
        ) {
            BackHandler(onBack = actions.backConfiguration)
            ConfigurationReview(state, actions)
        }
        AvailabilitySurface.WINDOW_EDITOR -> FullAvailabilitySurface("Disponibilidad", actions.close) {
            BackHandler(onBack = actions.close)
            WindowEditor(state, actions)
        }
        AvailabilitySurface.WINDOW_REVIEW -> FullAvailabilitySurface(
            "Revisar disponibilidad",
            actions.backWindow,
            actionLabel = "Volver",
        ) {
            BackHandler(onBack = actions.backWindow)
            WindowReview(state, actions)
        }
        AvailabilitySurface.DELETE_CONFIRMATION -> DeleteConfirmation(state, actions)
    }
    if (state.showDiscardConfirmation) DiscardConfirmation(actions)
}

@Composable
private fun FullAvailabilitySurface(
    title: String,
    onBack: () -> Unit,
    actionLabel: String = "Cerrar",
    content: @Composable () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            SurfaceHeader(title, actionLabel, onBack)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun AvailabilityOverview(state: AvailabilityUiState, actions: AvailabilityActions) {
    AvailabilityBody(state, actions) { source ->
        val label = source.labelOn(source.today)
        SectionCard(
            title = label?.displayName ?: "No uso disponibilidad",
            supportingText = "Esta elección tiene vigencia por fecha y no modifica tu meta de horas.",
        ) {
            Text("Ventanas registradas: ${source.windows.size}")
        }
        Button(
            onClick = actions.openConfiguration,
            modifier = Modifier.fillMaxWidth().testTag("availability-configure"),
        ) { Text("Configurar o cambiar") }
    }
}

@Composable
private fun ConfigurationEditor(state: AvailabilityUiState, actions: AvailabilityActions) {
    AvailabilityBody(state, actions) {
        val draft = state.configurationDraft ?: return@AvailabilityBody
        Text("Elegí el nombre que MiGuardia debe usar.", fontWeight = FontWeight.SemiBold)
        (listOf<AvailabilityLabel?>(
            AvailabilityLabel.PASSIVE_GUARD,
            AvailabilityLabel.AVAILABLE_FOR_CALL,
            AvailabilityLabel.ON_CALL_RETAINER,
            null,
        )).forEach { option ->
            OutlinedButton(
                onClick = { actions.updateConfiguration(draft.copy(label = option)) },
                modifier = Modifier.fillMaxWidth().testTag(
                    "availability-label-${option?.name ?: "none"}",
                ),
            ) {
                Text(
                    (if (draft.label == option) "✓ " else "") +
                        (option?.displayName ?: "No uso disponibilidad"),
                )
            }
        }
        OutlinedTextField(
            value = draft.effectiveDate,
            onValueChange = { actions.updateConfiguration(draft.copy(effectiveDate = it)) },
            label = { Text("Vigente desde") },
            supportingText = { Text("AAAA-MM-DD") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("availability-config-date"),
        )
        Button(
            onClick = actions.reviewConfiguration,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth().testTag("availability-config-review"),
        ) { Text("Revisar") }
    }
}

@Composable
private fun ConfigurationReview(state: AvailabilityUiState, actions: AvailabilityActions) {
    AvailabilityBody(state, actions) {
        val draft = state.configurationDraft ?: return@AvailabilityBody
        SectionCard("Confirmá el cambio") {
            Text("Nombre: ${draft.label?.displayName ?: "No uso disponibilidad"}")
            Text("Vigente desde: ${draft.effectiveDate}")
            Text("El historial anterior se conserva. Desactivar sólo bloquea cargas nuevas desde esa fecha.")
            runCatching { LocalDate.parse(draft.effectiveDate) }.getOrNull()?.let { effectiveDate ->
                if (state.source?.today?.let(effectiveDate::isBefore) == true) {
                    Text("Como la fecha es anterior a hoy, ese tramo histórico pasa a usar este nombre. No se crean ventanas ni se cambian sus fotografías guardadas.")
                }
            }
        }
        Button(
            onClick = actions.saveConfiguration,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth().testTag("availability-config-save"),
        ) { Text(if (state.isSaving) "Guardando…" else "Guardar configuración") }
    }
}

@Composable
private fun WindowEditor(state: AvailabilityUiState, actions: AvailabilityActions) {
    AvailabilityBody(state, actions) {
        val draft = state.windowDraft ?: return@AvailabilityBody
        SectionCard(
            title = "Fecha ${draft.ownerDate ?: ""}",
            supportingText = "La fecha dueña no cambia al corregir. Para moverla, eliminá y creá otra ventana.",
        ) { }
        AutomaticTimeField(
            value = draft.startTime,
            onValueChange = { actions.updateWindow(draft.copy(startTime = it)) },
            label = "Inicio",
            modifier = Modifier.fillMaxWidth().testTag("availability-start"),
        )
        OutlinedTextField(
            value = draft.endDate,
            onValueChange = { actions.updateWindow(draft.copy(endDate = it)) },
            label = { Text("Fecha final") },
            supportingText = { Text("AAAA-MM-DD; puede cruzar mes o año") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("availability-end-date"),
        )
        AutomaticTimeField(
            value = draft.endTime,
            onValueChange = { actions.updateWindow(draft.copy(endTime = it)) },
            label = "Final",
            modifier = Modifier.fillMaxWidth().testTag("availability-end"),
        )
        Text("Una disponibilidad puede superar 24 horas. No se combina automáticamente con ventanas contiguas.")
        Button(
            onClick = actions.reviewWindow,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth().testTag("availability-window-review"),
        ) { Text(if (state.isSaving) "Revisando…" else "Revisar") }
    }
}

@Composable
private fun WindowReview(state: AvailabilityUiState, actions: AvailabilityActions) {
    AvailabilityBody(state, actions) {
        val review = state.windowReview ?: return@AvailabilityBody
        val record = review.record
        SectionCard(
            title = record.labelSnapshot,
            supportingText = "Confirmá fecha, inicio y final exactos.",
        ) {
            Text("Inicio: ${record.start.atZone(record.zoneId).format(LocalDateTimeFormatter)}")
            Text("Final: ${record.end.atZone(record.zoneId).format(LocalDateTimeFormatter)}")
            Text("Duración programada: ${minutesLabel(record.durationMinutes)}")
        }
        AvailabilityResultLines(review.breakdown)
        if (review.isProtected) {
            Text("La fecha está protegida por vacaciones o licencia médica; la ventana se conserva sin aportar disponibilidad efectiva.")
        }
        Button(
            onClick = actions.saveWindow,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth().testTag("availability-window-save"),
        ) { Text(if (state.isSaving) "Guardando…" else "Guardar") }
    }
}

@Composable
private fun AvailabilityBody(
    state: AvailabilityUiState,
    actions: AvailabilityActions,
    content: @Composable (AvailabilitySource) -> Unit,
) {
    when (state.loadState) {
        AvailabilityLoadState.LOADING -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }
        AvailabilityLoadState.ERROR -> state.source?.let { source ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PersistentMessage(
                    message = state.message ?: "No pudimos actualizar los datos. Se conserva la última información válida.",
                    onDismiss = actions.clearMessage,
                )
                OutlinedButton(onClick = actions.retry, modifier = Modifier.fillMaxWidth()) {
                    Text("Reintentar")
                }
                content(source)
            }
        } ?: EmptyState(
            title = "No pudimos abrir disponibilidad",
            message = state.message ?: "Los datos no están disponibles.",
            actionLabel = "Reintentar",
            onAction = actions.retry,
            modifier = Modifier.fillMaxSize().padding(24.dp),
        )
        AvailabilityLoadState.CONTENT -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.message?.let { PersistentMessage(message = it, onDismiss = actions.clearMessage) }
            content(requireNotNull(state.source))
        }
    }
}

@Composable
fun AvailabilityDaySection(
    date: LocalDate,
    state: AvailabilityUiState,
    actions: AvailabilityActions,
) {
    val source = state.source ?: return
    val windows = state.windowsOn(date)
    val label = source.labelOn(date)
    val calculationError = source.calculationError
    SectionCard(
        title = label?.displayName ?: "Disponibilidad",
        supportingText = if (label == null) {
            "No estaba habilitada para esta fecha. El historial ya cargado sigue visible."
        } else {
            "Tiempo disponible para ser llamado; no se suma como trabajo realizado."
        },
    ) {
        calculationError?.let {
            Text("No pudimos recalcular el resultado. Las ventanas guardadas siguen visibles.")
        }
        if (state.loadState == AvailabilityLoadState.ERROR) {
            Text("No pudimos actualizar los datos. Se muestra la última información válida.")
        }
        if (windows.isEmpty()) Text("No hay ventanas registradas.")
        windows.forEach { record ->
            val result = source.breakdowns[record.id]
            Text(record.labelSnapshot, fontWeight = FontWeight.SemiBold)
            Text(
                "${record.start.atZone(record.zoneId).toLocalTime()}–" +
                    "${record.end.atZone(record.zoneId).format(EndFormatter)}",
                modifier = Modifier.testTag("availability-window-${record.id}"),
            )
            result?.let { Text(resultShortLabel(it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { actions.openCorrect(record) },
                    modifier = Modifier.testTag("availability-correct-${record.id}"),
                ) { Text("Corregir") }
                TextButton(
                    onClick = { actions.requestDelete(record) },
                    modifier = Modifier.testTag("availability-delete-${record.id}"),
                ) { Text("Eliminar") }
            }
        }
        if (label != null) {
            OutlinedButton(
                onClick = { actions.openCreate(date) },
                modifier = Modifier.fillMaxWidth().testTag("availability-add-$date"),
            ) { Text("Registrar ${label.displayName.lowercase()}") }
        }
    }
}

@Composable
fun AvailabilityHoursSection(state: AvailabilityUiState) {
    val source = state.source ?: return
    if (source.windows.isEmpty()) return
    if (source.calculationError != null) {
        SectionCard(
            title = "Disponibilidad",
            supportingText = "Las ventanas siguen guardadas, pero el detalle no está disponible hasta poder recalcularlo.",
        ) { Text(source.calculationError) }
        return
    }
    val totals = source.totals ?: return
    SectionCard(
        title = "Disponibilidad",
        supportingText = "Este detalle no altera el avance de horas trabajadas.",
    ) {
        if (state.loadState == AvailabilityLoadState.ERROR) {
            Text("No pudimos actualizar los datos. Se muestra la última información válida.")
        }
        ResultLine("Programada", totals.programmedMinutes)
        ResultLine("Efectiva transcurrida", totals.effectiveElapsedMinutes)
        ResultLine("Reemplazada por trabajo activo", totals.replacedElapsedMinutes)
        ResultLine("Futura pendiente", totals.futurePendingMinutes)
        ResultLine("Efectiva proyectada al final", totals.effectiveProjectedAtEndMinutes)
        ResultLine("Futura ocupada por trabajo planificado", totals.futureOccupiedByPlannedWorkMinutes)
    }
}

@Composable
private fun AvailabilityResultLines(result: AvailabilityBreakdown) {
    SectionCard("Resultado de disponibilidad") {
        Text("Estado: ${stateLabel(result.state)}")
        ResultLine("Programada", result.programmedMinutes)
        ResultLine("Efectiva transcurrida", result.effectiveElapsedMinutes)
        ResultLine("Reemplazada por trabajo activo", result.replacedElapsedMinutes)
        ResultLine("Futura pendiente", result.futurePendingMinutes)
        ResultLine("Efectiva proyectada al final", result.effectiveProjectedAtEndMinutes)
        ResultLine("Futura ocupada por trabajo planificado", result.futureOccupiedByPlannedWorkMinutes)
    }
}

@Composable
private fun ResultLine(label: String, minutes: Long) {
    Text("$label: ${minutesLabel(minutes)}")
}

private fun resultShortLabel(result: AvailabilityBreakdown): String = when (result.state) {
    AvailabilityTemporalState.PROTECTED -> "Protegida · no aporta disponibilidad efectiva"
    AvailabilityTemporalState.FUTURE -> "Pendiente ${minutesLabel(result.futurePendingMinutes)}"
    AvailabilityTemporalState.IN_PROGRESS ->
        "Efectiva ${minutesLabel(result.effectiveElapsedMinutes)} · reemplazada ${minutesLabel(result.replacedElapsedMinutes)}"
    AvailabilityTemporalState.COMPLETED ->
        "Efectiva ${minutesLabel(result.effectiveElapsedMinutes)} · reemplazada ${minutesLabel(result.replacedElapsedMinutes)}"
}

private fun stateLabel(state: AvailabilityTemporalState): String = when (state) {
    AvailabilityTemporalState.FUTURE -> "Futura"
    AvailabilityTemporalState.IN_PROGRESS -> "En curso"
    AvailabilityTemporalState.COMPLETED -> "Finalizada"
    AvailabilityTemporalState.PROTECTED -> "Protegida"
}

private fun minutesLabel(minutes: Long): String = "${minutes / 60} h ${minutes % 60} min"

@Composable
private fun DeleteConfirmation(state: AvailabilityUiState, actions: AvailabilityActions) {
    val record = state.deletingRecord ?: return
    val start = record.start.atZone(record.zoneId).format(LocalDateTimeFormatter)
    val end = record.end.atZone(record.zoneId).format(LocalDateTimeFormatter)
    AlertDialog(
        onDismissRequest = actions.dismissDelete,
        title = { Text("Eliminar ${record.labelSnapshot}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Se eliminará exactamente esta ventana: $start–$end (${minutesLabel(record.durationMinutes)}). " +
                        "La acción no modifica jornadas ni horas trabajadas.",
                )
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = actions.confirmDelete,
                enabled = !state.isSaving,
                modifier = Modifier.testTag("availability-delete-confirm"),
            ) { Text(if (state.isSaving) "Eliminando…" else "Eliminar") }
        },
        dismissButton = {
            TextButton(onClick = actions.dismissDelete, enabled = !state.isSaving) { Text("Cancelar") }
        },
    )
}

@Composable
private fun DiscardConfirmation(actions: AvailabilityActions) {
    AlertDialog(
        onDismissRequest = actions.dismissDiscard,
        title = { Text("Descartar cambios") },
        text = { Text("Los datos que todavía no guardaste se perderán.") },
        confirmButton = {
            Button(onClick = actions.confirmDiscard, modifier = Modifier.testTag("availability-discard-confirm")) {
                Text("Descartar")
            }
        },
        dismissButton = { TextButton(onClick = actions.dismissDiscard) { Text("Seguir editando") } },
    )
}

private val LocalDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val EndFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")
