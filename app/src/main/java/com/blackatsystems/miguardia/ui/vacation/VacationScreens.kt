package com.blackatsystems.miguardia.ui.vacation

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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.ui.components.TransientConfirmation
import com.blackatsystems.miguardia.ui.components.DestructiveAction
import com.blackatsystems.miguardia.ui.components.EmptyState
import com.blackatsystems.miguardia.ui.components.MonthNavigator
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.PrimaryAction
import com.blackatsystems.miguardia.ui.components.ScreenHeading
import com.blackatsystems.miguardia.ui.components.SurfaceHeader
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class VacationActions(
    val openList: (YearMonth) -> Unit = {},
    val openCreate: (YearMonth, LocalDate?) -> Unit = { _, _ -> },
    val edit: (Vacation) -> Unit = {},
    val previousMonth: () -> Unit = {},
    val nextMonth: () -> Unit = {},
    val updateStartDate: (LocalDate) -> Unit = {},
    val updateEndDate: (LocalDate) -> Unit = {},
    val save: () -> Unit = {},
    val requestBack: () -> Unit = {},
    val dismissDiscard: () -> Unit = {},
    val confirmDiscard: () -> Unit = {},
    val requestDelete: (Vacation) -> Unit = {},
    val dismissDelete: () -> Unit = {},
    val confirmDelete: () -> Unit = {},
    val retry: () -> Unit = {},
    val clearMessage: () -> Unit = {},
) {
    companion object {
        fun from(viewModel: VacationViewModel) = VacationActions(
            openList = viewModel::openList,
            openCreate = viewModel::openCreate,
            edit = viewModel::edit,
            previousMonth = viewModel::previousMonth,
            nextMonth = viewModel::nextMonth,
            updateStartDate = viewModel::updateStartDate,
            updateEndDate = viewModel::updateEndDate,
            save = viewModel::save,
            requestBack = viewModel::requestBack,
            dismissDiscard = viewModel::dismissDiscard,
            confirmDiscard = viewModel::confirmDiscard,
            requestDelete = viewModel::requestDelete,
            dismissDelete = viewModel::dismissDelete,
            confirmDelete = viewModel::confirmDelete,
            retry = viewModel::retry,
            clearMessage = viewModel::clearMessage,
        )
    }
}

@Composable
fun VacationSurfaceHost(
    state: VacationUiState,
    actions: VacationActions,
) {
    BackHandler(onBack = actions.requestBack)
    TransientConfirmation(state.infoMessage, actions.clearMessage) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            SurfaceHeader(
                title = if (state.surface == VacationSurface.EDITOR) {
                    if (state.draft.editingId == null) "Agregar vacaciones" else "Editar vacaciones"
                } else {
                    "Vacaciones"
                },
                navigationLabel = if (state.surface == VacationSurface.EDITOR) "Volver" else "Cerrar",
                onNavigation = actions.requestBack,
            )
            HorizontalDivider()
            state.errorMessage?.let {
                PersistentMessage(
                    message = it,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    onDismiss = actions.clearMessage,
                    onRetry = actions.retry.takeIf { state.surface == VacationSurface.LIST },
                )
            }
            when (state.surface) {
                VacationSurface.NONE -> Unit
                VacationSurface.LIST -> VacationList(state, actions)
                VacationSurface.EDITOR -> VacationEditor(state, actions)
                }
            }
        }
    }

    if (state.showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = actions.dismissDiscard,
            title = { Text("Descartar cambios") },
            text = { Text("Los cambios todavía no fueron guardados. ¿Querés descartarlos?") },
            confirmButton = {
                TextButton(onClick = actions.confirmDiscard) { Text("Descartar") }
            },
            dismissButton = {
                TextButton(onClick = actions.dismissDiscard) { Text("Seguir editando") }
            },
        )
    }

    if (state.pendingDelete != null) {
        AlertDialog(
            onDismissRequest = actions.dismissDelete,
            title = { Text("Eliminar vacaciones") },
            text = { Text("Se eliminará este período completo. Las guardias y demás datos no serán modificados.") },
            confirmButton = {
                TextButton(onClick = actions.confirmDelete) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = actions.dismissDelete) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun VacationList(state: VacationUiState, actions: VacationActions) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthNavigator(
            monthLabel = state.visibleMonth.displayName(),
            previousDescription = "Mes anterior de vacaciones",
            nextDescription = "Mes siguiente de vacaciones",
            onPrevious = actions.previousMonth,
            onNext = actions.nextMonth,
        )
        Text("Los días se cuentan de forma corrida e inclusiva.")
        Text(
            "Una guardia normal dentro del período se conserva, pero no computa horas. " +
                "Ausencias y cancelaciones explícitas mantienen su clasificación.",
        )
        PrimaryAction(
            label = "Agregar vacaciones",
            onClick = { actions.openCreate(state.visibleMonth, null) },
        )
        when {
            state.isLoading -> Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Text("Cargando vacaciones…", Modifier.padding(start = 12.dp))
            }

            state.errorMessage != null -> Unit
            state.vacations.isEmpty() -> EmptyState(
                title = "Sin vacaciones",
                message = "No hay vacaciones que intersecten este mes.",
            )
            else -> state.vacations.forEach { vacation ->
                VacationCard(vacation, actions)
            }
        }
        Text(
            "MiGuardia registra los días de vacaciones sin calcular montos.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun VacationCard(vacation: Vacation, actions: VacationActions) {
    val days = java.time.temporal.ChronoUnit.DAYS.between(
        vacation.startDate,
        vacation.endDateInclusive,
    ) + 1
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "${vacation.startDate.displayName()} – ${vacation.endDateInclusive.displayName()}",
                fontWeight = FontWeight.Bold,
            )
            Text(dayCountLabel(days))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { actions.edit(vacation) },
                    modifier = Modifier.semantics {
                        contentDescription = "Editar vacaciones desde ${vacation.startDate.displayName()}"
                    },
                ) { Text("Editar") }
                DestructiveAction(
                    label = "Eliminar",
                    onClick = { actions.requestDelete(vacation) },
                    modifier = Modifier.semantics {
                        contentDescription = "Eliminar vacaciones desde ${vacation.startDate.displayName()}"
                    },
                )
            }
        }
    }
}

@Composable
private fun VacationEditor(state: VacationUiState, actions: VacationActions) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeading(
            title = "Período inclusivo",
            supportingText = "Elegí el primer y el último día. Ambos se incluyen en el período.",
        )
        VacationDateField(
            label = "Fecha inicial",
            date = state.draft.startDate,
            onSelected = actions.updateStartDate,
        )
        VacationDateField(
            label = "Fecha final inclusiva",
            date = state.draft.endDateInclusive,
            onSelected = actions.updateEndDate,
        )
        val preview = state.draft.inclusiveDayCount
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = preview?.let { "Vista previa: ${dayCountLabel(it)}" }
                    ?: "Elegí un rango válido para ver la cantidad de días.",
                modifier = Modifier.padding(16.dp).semantics {
                    contentDescription = preview?.let { "${dayCountLabel(it)}, fechas inclusivas" }
                        ?: "Rango de vacaciones inválido"
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text("Una guardia normal dentro de estas fechas no computará horas.")
        Text(
            "Las vacaciones no pueden superponerse con otro período ni con una carpeta médica.",
            style = MaterialTheme.typography.bodySmall,
        )
        PrimaryAction(
            label = "Guardar vacaciones",
            onClick = actions.save,
            enabled = preview != null && !state.isSaving,
            working = state.isSaving,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VacationDateField(
    label: String,
    date: LocalDate?,
    onSelected: (LocalDate) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
        Text("$label: ${date?.displayName() ?: "Elegir"}")
    }
    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        showPicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun MessageCard(text: String, isError: Boolean, dismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                modifier = Modifier.weight(1f),
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = dismiss) { Text("Cerrar") }
        }
    }
}

private val SpanishArgentina = Locale.forLanguageTag("es-AR")
private val DateFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", SpanishArgentina)

private fun LocalDate.displayName(): String = format(DateFormatter)

private fun YearMonth.displayName(): String {
    val name = month.getDisplayName(TextStyle.FULL, SpanishArgentina)
        .replaceFirstChar { it.titlecase(SpanishArgentina) }
    return "$name de $year"
}

private fun dayCountLabel(days: Long): String = if (days == 1L) {
    "1 día corrido"
} else {
    "$days días corridos"
}
