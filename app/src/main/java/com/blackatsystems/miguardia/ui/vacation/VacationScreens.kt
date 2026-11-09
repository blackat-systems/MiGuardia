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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.blackatsystems.miguardia.core.domain.model.Vacation
import com.blackatsystems.miguardia.ui.components.TransientConfirmation
import com.blackatsystems.miguardia.ui.components.DestructiveAction
import com.blackatsystems.miguardia.ui.components.EmptyState
import com.blackatsystems.miguardia.ui.components.MiGuardiaButtonShape
import com.blackatsystems.miguardia.ui.components.MiGuardiaDatePickerDialog
import com.blackatsystems.miguardia.ui.components.MiGuardiaSpacing
import com.blackatsystems.miguardia.ui.components.MonthNavigator
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.PrimaryAction
import com.blackatsystems.miguardia.ui.components.SurfaceHeader
import com.blackatsystems.miguardia.ui.components.cardBorder
import com.blackatsystems.miguardia.ui.components.destructiveButtonColors
import com.blackatsystems.miguardia.ui.components.vigiliaCardSurface
import com.blackatsystems.miguardia.ui.components.vigiliaScreenBackground
import java.time.LocalDate
import java.time.YearMonth
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
    TransientConfirmation(state.infoMessage, actions.clearMessage, reserveSpace = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize().vigiliaScreenBackground().safeDrawingPadding()) {
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
                    modifier = Modifier.padding(
                        horizontal = MiGuardiaSpacing.large,
                        vertical = MiGuardiaSpacing.extraSmall,
                    ),
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
                FilledTonalButton(
                    onClick = actions.confirmDiscard,
                    shape = MiGuardiaButtonShape,
                    colors = destructiveButtonColors(),
                ) { Text("Descartar") }
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
                FilledTonalButton(
                    onClick = actions.confirmDelete,
                    shape = MiGuardiaButtonShape,
                    colors = destructiveButtonColors(),
                ) { Text("Eliminar") }
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MiGuardiaSpacing.large),
        verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.large),
    ) {
        MonthNavigator(
            monthLabel = state.visibleMonth.displayName(),
            previousDescription = "Mes anterior de vacaciones",
            nextDescription = "Mes siguiente de vacaciones",
            onPrevious = actions.previousMonth,
            onNext = actions.nextMonth,
        )
        Column(verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small)) {
            Text("Los días se cuentan de forma corrida e inclusiva.")
            Text(
                "Una guardia normal dentro del período se conserva, pero no computa horas. " +
                    "Ausencias y cancelaciones explícitas mantienen su clasificación.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PrimaryAction(
            label = "Agregar vacaciones",
            onClick = { actions.openCreate(state.visibleMonth, null) },
        )
        when {
            state.isLoading -> Row(
                modifier = Modifier.fillMaxWidth().padding(MiGuardiaSpacing.extraLarge),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Text("Cargando vacaciones…", Modifier.padding(start = MiGuardiaSpacing.medium))
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VacationCard(vacation: Vacation, actions: VacationActions) {
    val days = java.time.temporal.ChronoUnit.DAYS.between(
        vacation.startDate,
        vacation.endDateInclusive,
    ) + 1
    VacationPlainCard {
        Column(
            modifier = Modifier.padding(MiGuardiaSpacing.large),
            verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small),
        ) {
            Text(
                "${vacation.startDate.displayName()} – ${vacation.endDateInclusive.displayName()}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(dayCountLabel(days), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small)) {
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MiGuardiaSpacing.large),
        verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.large),
    ) {
        // Style v2: one title per screen. The header already names the editor, so the period
        // explanation stays as supporting text instead of a second heading.
        Text(
            "Elegí el primer y el último día. Ambos se incluyen en el período.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VacationDateField(
            label = "Fecha inicial",
            date = state.draft.startDate,
            onSelected = actions.updateStartDate,
            enabled = !state.isSaving,
        )
        VacationDateField(
            label = "Fecha final inclusiva",
            date = state.draft.endDateInclusive,
            onSelected = actions.updateEndDate,
            enabled = !state.isSaving,
        )
        val preview = state.draft.inclusiveDayCount
        VacationPlainCard {
            Text(
                text = preview?.let { "Vista previa: ${dayCountLabel(it)}" }
                    ?: "Elegí un rango válido para ver la cantidad de días.",
                modifier = Modifier.padding(MiGuardiaSpacing.large).semantics {
                    contentDescription = preview?.let { "${dayCountLabel(it)}, fechas inclusivas" }
                        ?: "Rango de vacaciones inválido"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(MiGuardiaSpacing.small)) {
            Text("Una guardia normal dentro de estas fechas no computará horas.")
            Text(
                "Las vacaciones no pueden superponerse con otro período ni con una carpeta médica.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PrimaryAction(
            label = "Guardar vacaciones",
            onClick = actions.save,
            enabled = preview != null && !state.isSaving,
            working = state.isSaving,
        )
    }
}

/**
 * Style v2: the same button and text as before, now opening the shared calendar picker
 * (Argentine Spanish, weeks from Monday like the Calendar, exact UTC date). Picking only
 * updates the draft through [onSelected]; it never saves. Cancel changes nothing.
 */
@Composable
private fun VacationDateField(
    label: String,
    date: LocalDate?,
    onSelected: (LocalDate) -> Unit,
    enabled: Boolean,
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { showPicker = true },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MiGuardiaButtonShape,
    ) {
        Text("$label: ${date?.displayName() ?: "Elegir"}")
    }
    if (showPicker) {
        MiGuardiaDatePickerDialog(
            initial = date ?: LocalDate.now(),
            // As before stage 13.5: an empty date opens on this month with no day chosen.
            preselect = date != null,
            onConfirm = { picked ->
                showPicker = false
                onSelected(picked)
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** Common card surface (style v2) for the vacation rows and the preview. */
@Composable
private fun VacationPlainCard(content: @Composable () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Card(
        modifier = Modifier.fillMaxWidth().vigiliaCardSurface(shape, MaterialTheme.colorScheme.surfaceContainer),
        shape = shape,
        border = cardBorder(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        content()
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
