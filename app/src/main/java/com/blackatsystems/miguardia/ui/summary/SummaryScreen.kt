package com.blackatsystems.miguardia.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.core.domain.hours.MonthlyHoursSummary
import com.blackatsystems.miguardia.core.domain.remuneration.SuvicoRemunerationEstimate
import com.blackatsystems.miguardia.ui.components.EmptyState
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.components.ScreenHeading
import com.blackatsystems.miguardia.ui.theme.vigiliaColors
import java.time.Duration
import java.time.YearMonth
import java.time.format.TextStyle
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private val SpanishArgentina = Locale.forLanguageTag("es-AR")

@Composable
fun SummaryScreen(
    state: SummaryUiState,
    contentPadding: PaddingValues,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onRetry: () -> Unit,
    onSeniorityYearsChange: (Int) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeading(
            title = "Resumen",
            supportingText = "Horas, eventos y estimaciones del mes seleccionado.",
        )
        SummaryMonthControls(state.visibleMonth, onPreviousMonth, onNextMonth, onToday)
        when (state.loadState) {
            SummaryLoadState.LOADING -> SummaryLoading()
            SummaryLoadState.ERROR -> SummaryError(
                state.errorMessage ?: stringResource(R.string.summary_error),
                onRetry,
            )
            SummaryLoadState.CONTENT -> SummaryContent(state, onSeniorityYearsChange)
        }
    }
}

@Composable
private fun SummaryMonthControls(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val previous = stringResource(R.string.summary_previous_month)
    val next = stringResource(R.string.summary_next_month)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.semantics { contentDescription = previous },
                ) { Text("‹", modifier = Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.headlineMedium) }
                Text(
                    month.displayName(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.semantics { contentDescription = next },
                ) { Text("›", modifier = Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.headlineMedium) }
            }
            TextButton(onClick = onToday) { Text(stringResource(R.string.today)) }
        }
    }
}

@Composable
private fun SummaryLoading() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.width(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.summary_loading))
    }
}

@Composable
private fun SummaryError(message: String, onRetry: () -> Unit) {
    PersistentMessage(message = message, onRetry = onRetry)
}

@Composable
private fun SummaryContent(state: SummaryUiState, onSeniorityYearsChange: (Int) -> Unit) {
    val summary = state.summary
    if (summary.shiftCount == 0) {
        EmptyState(
            title = "Mes sin guardias",
            message = stringResource(R.string.summary_no_shifts),
        )
    }
    SummarySection(stringResource(R.string.summary_hours_title)) {
        SummaryValue(R.string.summary_planned, summary.planned.asHoursAndMinutes())
        SummaryValue(R.string.summary_worked, summary.worked.asHoursAndMinutes())
        SummaryValue(R.string.summary_pending, summary.pending.asHoursAndMinutes())
        SummaryValue(R.string.summary_overtime, summary.overtime.asHoursAndMinutes())
    }
    SummarySection(stringResource(R.string.summary_special_hours_title)) {
        SummaryValue(R.string.summary_night, summary.nightWorked.asHoursAndMinutes())
        SummaryValue(R.string.summary_holiday, summary.holidayWorked.asHoursAndMinutes())
        Text(
            stringResource(R.string.summary_classification_notice),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    SummarySection(stringResource(R.string.summary_events_title)) {
        SummaryValue(R.string.summary_shifts, summary.shiftCount.toString())
        SummaryValue(R.string.summary_days_off, summary.dayOffCount.toString())
        SummaryValue(R.string.summary_vacations, summary.vacationDayCount.toString())
        SummaryValue(
            R.string.summary_medical,
            summary.medicalLeaveDayCount.asDayCount(),
        )
        SummaryValue(
            R.string.summary_absences,
            summary.absenceHours.asReadableHours(),
        )
    }
    RemunerationSection(state, onSeniorityYearsChange)
}

@Composable
private fun RemunerationSection(
    state: SummaryUiState,
    onSeniorityYearsChange: (Int) -> Unit,
) {
    var seniorityText by remember { mutableStateOf(state.seniorityYears.toString()) }
    LaunchedEffect(state.seniorityYears) {
        seniorityText = state.seniorityYears.toString()
    }
    SectionCard(
        title = "Estimación remunerativa",
        supportingText = "Escala SUVICO · categoría Vigilador. Se calcula al final del Resumen.",
    ) {
        OutlinedTextField(
            value = seniorityText,
            onValueChange = { value ->
                val filtered = value.filter(Char::isDigit).take(2)
                seniorityText = filtered
                filtered.toIntOrNull()?.takeIf { it in 0..60 }?.let(onSeniorityYearsChange)
            },
            label = { Text("Antigüedad (años)") },
            supportingText = { Text("Se guarda sólo en este teléfono. Valores admitidos: 0 a 60.") },
            isError = seniorityText.toIntOrNull()?.let { it !in 0..60 } ?: seniorityText.isBlank(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        state.remunerationErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val estimate = state.remuneration
        if (estimate == null) {
            Text(
                "No hay una escala SUVICO cargada para ${state.visibleMonth.displayName()}. " +
                    "MiGuardia no extrapola importes de otro mes.",
            )
            return@SectionCard
        }
        RemunerationEstimateRows(estimate)
        Text(
            "Estimación bruta orientativa: supone que se conservan presentismo, suma no remunerativa y viáticos del mes completo, " +
                "y que se cumplen las guardias pendientes. No calcula descuentos, neto, vacaciones ni pérdidas por ausencias.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (estimate.projectedOvertimeHours > Duration.ZERO) {
            Text(
                "El total se muestra como rango porque las escalas publican extras al 50 % y al 100 %, " +
                    "pero MiGuardia todavía no puede decidir cuál corresponde a cada hora.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RemunerationEstimateRows(estimate: SuvicoRemunerationEstimate) {
    val total = if (estimate.estimatedGrossAtFiftyPercent == estimate.estimatedGrossAtOneHundredPercent) {
        estimate.estimatedGrossAtFiftyPercent.asArgentineCurrency()
    } else {
        "${estimate.estimatedGrossAtFiftyPercent.asArgentineCurrency()} – " +
            estimate.estimatedGrossAtOneHundredPercent.asArgentineCurrency()
    }
    SummaryValue("Estimado bruto al cierre", total)
    HorizontalDivider()
    SummaryValue("Básico", estimate.scale.basicSalary.asArgentineCurrency())
    SummaryValue(
        "Antigüedad (${estimate.seniorityPercentage.stripTrailingZeros().toPlainString()} %)",
        estimate.seniorityAmount.asArgentineCurrency(),
    )
    SummaryValue("Presentismo", estimate.scale.presentism.asArgentineCurrency())
    SummaryValue("Suma no remunerativa", estimate.scale.nonRemunerativeAmount.asArgentineCurrency())
    SummaryValue("Viáticos", estimate.scale.viatics.asArgentineCurrency())
    SummaryValue("Adicional nocturno proyectado", estimate.nightAdditional.asArgentineCurrency())
    SummaryValue("Feriados proyectados", estimate.holidayAdditional.asArgentineCurrency())
    if (estimate.projectedOvertimeHours > Duration.ZERO) {
        SummaryValue("Extras proyectadas al 50 %", estimate.overtimeAtFiftyPercent.asArgentineCurrency())
        SummaryValue("Extras proyectadas al 100 %", estimate.overtimeAtOneHundredPercent.asArgentineCurrency())
    }
    Text(
        "Fuente: ${estimate.scale.sourceFileName}",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun SummarySection(title: String, content: @Composable () -> Unit) {
    SectionCard(title = title, content = content)
}

@Composable
private fun SummaryValue(@androidx.annotation.StringRes labelRes: Int, value: String) {
    val label = stringResource(labelRes)
    val semanticsModifier = Modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) { contentDescription = "$label, $value" }
    Row(
        modifier = semanticsModifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.vigiliaColors.onSurfaceMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SummaryValue(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$label, $value" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.vigiliaColors.onSurfaceMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private fun Duration.asHoursAndMinutes(): String {
    val totalMinutes = toMinutes()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (minutes == 0L) "${hours} h" else "${hours} h ${minutes} min"
}

private fun Int.asDayCount(): String = if (this == 1) "1 día" else "$this días"

private fun Duration.asReadableHours(): String {
    val totalMinutes = toMinutes()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val hoursText = if (hours == 1L) "1 hora" else "$hours horas"
    return when {
        hours == 0L && minutes != 0L -> "$minutes min"
        minutes == 0L -> hoursText
        else -> "$hoursText $minutes min"
    }
}

private fun YearMonth.displayName(): String {
    val name = month.getDisplayName(TextStyle.FULL, SpanishArgentina)
        .replaceFirstChar { it.titlecase(SpanishArgentina) }
    return "$name de $year"
}

private fun BigDecimal.asArgentineCurrency(): String =
    NumberFormat.getCurrencyInstance(SpanishArgentina).format(this)
