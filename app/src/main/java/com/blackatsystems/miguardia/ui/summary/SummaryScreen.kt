package com.blackatsystems.miguardia.ui.summary

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryProjection
import com.blackatsystems.miguardia.core.domain.summary.SummaryCompliancePeriod
import com.blackatsystems.miguardia.core.domain.summary.SummaryContribution
import com.blackatsystems.miguardia.core.domain.summary.SummaryMetric
import com.blackatsystems.miguardia.core.domain.summary.SummaryOptionalFamily
import com.blackatsystems.miguardia.core.domain.summary.SummaryOptionalSection
import com.blackatsystems.miguardia.core.domain.summary.SummaryValueUnit
import com.blackatsystems.miguardia.core.domain.work.HoursTargetState
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.ScreenHeading
import com.blackatsystems.miguardia.ui.components.SectionCard
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.absoluteValue

private val SpanishArgentina = Locale.forLanguageTag("es-AR")
private val SummaryDateFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", SpanishArgentina)
private val SummaryTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", SpanishArgentina)

@Composable
fun SummaryScreen(
    state: SummaryUiState,
    actions: SummaryActions,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    when (state.surface) {
        SummarySurface.PERSONALIZATION -> PersonalizationScreen(state, actions, contentPadding, modifier)
        SummarySurface.DETAIL -> MetricDetailScreen(state, actions, contentPadding, modifier)
        SummarySurface.OVERVIEW -> SummaryOverview(state, actions, contentPadding, modifier)
    }
}

@Composable
private fun SummaryOverview(
    state: SummaryUiState,
    actions: SummaryActions,
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    val scrollState = rememberScrollState(initial = state.overviewScrollPosition)
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect(actions.updateOverviewScrollPosition)
    }
    LaunchedEffect(state.visibleMonth) {
        scrollState.scrollTo(state.overviewScrollPosition)
    }
    val openMetric: (String) -> Unit = { id ->
        actions.updateOverviewScrollPosition(scrollState.value)
        actions.openMetric(id)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(scrollState)
            .padding(20.dp)
            .testTag("summary-overview"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            ScreenHeading(
                title = "Resumen",
                supportingText = "Una proyección mensual de sólo lectura, calculada desde tus fuentes locales.",
                modifier = Modifier.weight(1f),
            )
            SummaryMenu(
                actions = actions,
                onOpenPersonalization = {
                    actions.updateOverviewScrollPosition(scrollState.value)
                    actions.openPersonalization()
                },
            )
        }
        SummaryMonthControls(state.visibleMonth, actions)
        Button(
            onClick = actions.openReports,
            modifier = Modifier.fillMaxWidth().testTag("summary-generate-report"),
        ) {
            Text("Generar informe")
        }
        if (state.introVisible) {
            SectionCard(
                title = "Tu resumen, a tu manera",
                modifier = Modifier.testTag("summary-intro"),
                supportingText = "Podés mostrar, ocultar y ordenar los detalles sin cambiar ninguna cifra.",
            ) {
                Button(onClick = actions.dismissIntro, modifier = Modifier.testTag("summary-intro-understood")) {
                    Text("Entendido")
                }
            }
        }
        if (state.projection != null) {
            state.errorMessage?.let { message ->
                PersistentMessage(
                    message = message,
                    modifier = Modifier.testTag("summary-source-warning"),
                    onRetry = actions.retry,
                )
            }
        }
        PreferenceWriteError(state, actions)
        when (state.loadState) {
            SummaryLoadState.LOADING -> {
                Row(
                    modifier = Modifier.fillMaxWidth().testTag("summary-loading"),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            SummaryLoadState.ERROR -> {
                SectionCard("No pudimos cargar el Resumen", modifier = Modifier.testTag("summary-error")) {
                    Text("No mostramos ceros ni datos de otro mes cuando falta una fuente.")
                    Button(onClick = actions.retry) { Text("Reintentar") }
                }
            }
            SummaryLoadState.EMPTY -> {
                SectionCard(
                    title = "Todavía no hay datos para este mes",
                    modifier = Modifier.testTag("summary-empty"),
                ) {
                    Text("Cuando haya jornadas, extras, disponibilidad o situaciones existentes, van a aparecer acá.")
                }
            }
            SummaryLoadState.CONTENT -> state.projection?.let { projection ->
                SummaryContent(projection, state, openMetric)
            }
        }
        Spacer(Modifier.testTag("summary-overview-end"))
    }
}

@Composable
private fun SummaryMenu(
    actions: SummaryActions,
    onOpenPersonalization: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier
                .testTag("summary-menu")
                .semantics { contentDescription = "Más opciones del Resumen" },
        ) {
            Text("⋮", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Generar informe") },
                onClick = {
                    expanded = false
                    actions.openReports()
                },
                modifier = Modifier.testTag("summary-menu-generate-report"),
            )
            DropdownMenuItem(
                text = { Text("Personalizar resumen") },
                onClick = {
                    expanded = false
                    onOpenPersonalization()
                },
                modifier = Modifier.testTag("summary-menu-personalize"),
            )
            DropdownMenuItem(
                text = { Text("Cómo personalizar") },
                onClick = {
                    expanded = false
                    actions.showIntro()
                },
                modifier = Modifier.testTag("summary-menu-help"),
            )
        }
    }
}

@Composable
private fun SummaryMonthControls(month: YearMonth, actions: SummaryActions) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("summary-month-controls"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(month.displayName(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = actions.previousMonth,
                modifier = Modifier.fillMaxWidth().testTag("summary-previous-month"),
            ) {
                Text("Anterior")
            }
            OutlinedButton(
                onClick = actions.currentMonth,
                modifier = Modifier.fillMaxWidth().testTag("summary-current-month"),
            ) {
                Text("Hoy")
            }
            OutlinedButton(
                onClick = actions.nextMonth,
                modifier = Modifier.fillMaxWidth().testTag("summary-next-month"),
            ) {
                Text("Siguiente")
            }
        }
    }
}

@Composable
private fun SummaryContent(
    projection: MonthlySummaryProjection,
    state: SummaryUiState,
    onOpenMetric: (String) -> Unit,
) {
    val essentialMetrics = listOfNotNull(
        projection.essentials.totalWorked,
        projection.essentials.regularWorked,
        projection.essentials.extras,
        projection.essentials.pendingScheduled,
    )
    if (essentialMetrics.isNotEmpty()) {
        SectionCard("Lo esencial", supportingText = "Cada cifra se puede tocar para revisar su origen exacto.") {
            essentialMetrics.forEach { MetricRow(it, onOpenMetric) }
        }
    }
    if (projection.compliance.isNotEmpty()) {
        ComplianceSection(projection.compliance, onOpenMetric)
    }
    projection.availability?.let { availability ->
        SectionCard(
            title = "Disponibilidad",
            supportingText = "Permanece separada del trabajo y del avance de tu meta.",
            modifier = Modifier.testTag("summary-availability"),
        ) {
            listOfNotNull(
                availability.programmed,
                availability.effectiveElapsed,
                availability.replacedElapsed,
                availability.pending,
                availability.projectedEffectiveAtEnd,
            ).forEach { MetricRow(it, onOpenMetric) }
        }
    }
    val sectionsByFamily = projection.optionalSections.associateBy(SummaryOptionalSection::family)
    state.visibleOptionalFamilies().forEach { family ->
        sectionsByFamily[family]?.let { section ->
            SectionCard(
                title = family.displayLabel(),
                modifier = Modifier.testTag("summary-family-${family.name.lowercase()}"),
                supportingText = family.supportingText(),
            ) {
                section.metrics.forEach { MetricRow(it, onOpenMetric) }
            }
        }
    }
}

@Composable
private fun ComplianceSection(
    periods: List<SummaryCompliancePeriod>,
    onOpenMetric: (String) -> Unit,
) {
    SectionCard(
        title = "Avance de horas",
        supportingText = "Las semanas y ciclos se muestran completos aunque crucen el mes.",
        modifier = Modifier.testTag("summary-compliance"),
    ) {
        periods.forEachIndexed { index, period ->
            if (index > 0) HorizontalDivider()
            Text(period.displayRange(), fontWeight = FontWeight.Bold)
            when (period.segment.target) {
                is HoursTargetState.Defined -> {
                    listOfNotNull(
                        period.contributingWork,
                        period.target,
                        period.missing,
                        period.excess,
                    ).forEach { MetricRow(it, onOpenMetric) }
                    if (period.excess != null) {
                        Text("Superar la meta no crea una extra.")
                    }
                }
                HoursTargetState.MissingPerPeriodValue -> Text("Falta informar la meta de este período.")
                HoursTargetState.Unknown -> Text("Tenés una meta, pero no se indicó cuántas horas.")
                HoursTargetState.PendingSetup,
                HoursTargetState.NotUsed,
                -> Unit
            }
        }
    }
}

@Composable
private fun MetricRow(metric: SummaryMetric, onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(metric.id) }
            .padding(vertical = 8.dp)
            .testTag("summary-metric-${metric.id.toTestKey()}")
            .semantics {
                contentDescription = "${metric.label}: ${metric.formattedValue()}. Qué incluye este valor"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(metric.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Text(metric.formattedValue(), fontWeight = FontWeight.Bold)
        Text("›", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PersonalizationScreen(
    state: SummaryUiState,
    actions: SummaryActions,
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("summary-personalization"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeading(
            "Personalizar resumen",
            supportingText = "El orden y la visibilidad cambian sólo la presentación; las fórmulas y fuentes permanecen iguales.",
        )
        TextButton(onClick = actions.back, modifier = Modifier.testTag("summary-personalization-back")) {
            Text("Volver al resumen")
        }
        if (state.projection != null) {
            state.errorMessage?.let { message ->
                PersistentMessage(
                    message = message,
                    modifier = Modifier.testTag("summary-personalization-source-warning"),
                    onRetry = actions.retry,
                )
            }
        }
        PreferenceWriteError(state, actions)
        when {
            state.loadState == SummaryLoadState.LOADING -> {
                Row(
                    modifier = Modifier.fillMaxWidth().testTag("summary-personalization-loading"),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.loadState == SummaryLoadState.ERROR && state.projection == null -> {
                SectionCard(
                    title = "No pudimos cargar el Resumen",
                    modifier = Modifier.testTag("summary-personalization-error"),
                ) {
                    Text("No habilitamos controles con valores predeterminados mientras faltan sus fuentes.")
                    Button(onClick = actions.retry) { Text("Reintentar") }
                }
            }
            else -> PersonalizationControls(state, actions)
        }
    }
}

@Composable
private fun PersonalizationControls(
    state: SummaryUiState,
    actions: SummaryActions,
) {
    state.preferences.orderedFamilies.forEachIndexed { index, family ->
            Card(
                modifier = Modifier.fillMaxWidth().testTag("summary-preference-${family.name.lowercase()}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(family.displayLabel(), fontWeight = FontWeight.Bold)
                            Text(
                                family.supportingText(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.preferences.isVisible(family),
                            onCheckedChange = { actions.setFamilyVisible(family, it) },
                            modifier = Modifier
                                .testTag("summary-toggle-${family.name.lowercase()}")
                                .semantics { contentDescription = "Mostrar ${family.displayLabel()}" },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { actions.moveFamilyUp(family) },
                            enabled = index > 0,
                            modifier = Modifier
                                .testTag("summary-move-up-${family.name.lowercase()}")
                                .semantics { contentDescription = "Subir ${family.displayLabel()}" },
                        ) {
                            Text("Subir")
                        }
                        OutlinedButton(
                            onClick = { actions.moveFamilyDown(family) },
                            enabled = index < state.preferences.orderedFamilies.lastIndex,
                            modifier = Modifier
                                .testTag("summary-move-down-${family.name.lowercase()}")
                                .semantics { contentDescription = "Bajar ${family.displayLabel()}" },
                        ) {
                            Text("Bajar")
                        }
                    }
                }
            }
        }
}

@Composable
private fun MetricDetailScreen(
    state: SummaryUiState,
    actions: SummaryActions,
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("summary-detail"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeading(
            "Qué incluye este valor",
            supportingText = "Las filas son la misma proyección inmutable que produjo la cifra.",
        )
        TextButton(onClick = actions.back, modifier = Modifier.testTag("summary-detail-back")) {
            Text("Volver al resumen")
        }
        if (state.projection != null) {
            state.errorMessage?.let { message ->
                PersistentMessage(
                    message = message,
                    modifier = Modifier.testTag("summary-detail-source-warning"),
                    onRetry = actions.retry,
                )
            }
        }
        PreferenceWriteError(state, actions)
        if (state.loadState == SummaryLoadState.LOADING) {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("summary-detail-loading"),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Column
        }
        if (state.loadState == SummaryLoadState.ERROR && state.projection == null) {
            SectionCard(
                title = "No pudimos cargar esta cifra",
                modifier = Modifier.testTag("summary-detail-error"),
            ) {
                Text("No afirmamos que desapareció hasta terminar de restaurar sus fuentes.")
                Button(onClick = actions.retry) { Text("Reintentar") }
            }
            return@Column
        }
        val metric = state.selectedMetric
        if (metric == null) {
            Text("Esta cifra ya no está disponible porque sus fuentes cambiaron.")
            return@Column
        }
        SectionCard(metric.label, supportingText = "Suma exacta: ${metric.formattedValue()}") {
            if (metric.contributions.isEmpty()) {
                Text("No hay minutos transcurridos que integrar en esta cifra.")
            } else {
                metric.contributions.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider()
                    ContributionRow(row, metric.unit)
                }
            }
        }
        Text(
            "Las clasificaciones de noche, feriado y fin de semana pueden superponerse entre sí, pero nunca agregan minutos al total trabajado.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PreferenceWriteError(
    state: SummaryUiState,
    actions: SummaryActions,
) {
    state.preferenceErrorMessage?.let { message ->
        PersistentMessage(
            message = message,
            modifier = Modifier.testTag("summary-preference-write-error"),
            onDismiss = actions.dismissPreferenceError,
            onRetry = actions.retryPreferenceWrite,
        )
    }
}

@Composable
private fun ContributionRow(row: SummaryContribution, unit: SummaryValueUnit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("summary-contribution-${row.id.toTestKey()}"),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row {
            Text(row.sourceLabel, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(formatValue(row.value, unit), fontWeight = FontWeight.Bold)
        }
        Text("Fecha dueña: ${row.ownerLocalDate.displayName()}")
        val intervalStart = row.start
        val intervalEnd = row.end
        val intervalZone = row.zoneId
        if (intervalStart != null && intervalEnd != null && intervalZone != null) {
            val start = intervalStart.atZone(intervalZone)
            val end = intervalEnd.atZone(intervalZone)
            Text(
                "Horario: ${start.toLocalDate().displayName()} ${start.toLocalTime().format(SummaryTimeFormatter)} – " +
                    "${end.toLocalDate().displayName()} ${end.toLocalTime().format(SummaryTimeFormatter)}",
            )
        }
        row.workPlaceLabel?.let { Text("Lugar: $it") }
        row.workTypeLabel?.let { Text("Tipo: $it") }
        row.extraClassLabel?.let { Text("Clase extra: $it") }
        row.explanation?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun SummaryMetric.formattedValue(): String = formatValue(value, unit)

private fun formatValue(value: Long, unit: SummaryValueUnit): String = when (unit) {
    SummaryValueUnit.MINUTES -> formatMinutes(value)
    SummaryValueUnit.COUNT -> value.toString()
}

private fun formatMinutes(value: Long): String {
    val sign = if (value < 0L) "−" else ""
    val absolute = value.absoluteValue
    val hours = absolute / 60L
    val minutes = absolute % 60L
    return when {
        hours == 0L -> "$sign$minutes min"
        minutes == 0L -> "$sign$hours h"
        else -> "$sign$hours h $minutes min"
    }
}

private fun SummaryOptionalFamily.displayLabel(): String = when (this) {
    SummaryOptionalFamily.NIGHTS -> "Noches"
    SummaryOptionalFamily.HOLIDAYS -> "Feriados"
    SummaryOptionalFamily.WEEKENDS -> "Fines de semana"
    SummaryOptionalFamily.PLANNED_VS_ACTUAL -> "Planificado frente a real"
    SummaryOptionalFamily.WORK_PLACES -> "Lugares de trabajo"
    SummaryOptionalFamily.WORK_TYPES -> "Tipos de trabajo"
    SummaryOptionalFamily.EXTRA_CLASSES -> "Clases extra"
    SummaryOptionalFamily.SITUATIONS -> "Situaciones especiales e intercambios"
}

private fun SummaryOptionalFamily.supportingText(): String = when (this) {
    SummaryOptionalFamily.NIGHTS -> "Según la regla histórica de noche de cada lugar."
    SummaryOptionalFamily.HOLIDAYS -> "Sólo fechas cargadas y habilitadas por la regla histórica."
    SummaryOptionalFamily.WEEKENDS -> "Sábado, domingo o ambos según cada lugar."
    SummaryOptionalFamily.PLANNED_VS_ACTUAL -> "Compara únicamente jornadas con horario real confirmado."
    SummaryOptionalFamily.WORK_PLACES -> "Usa el nombre histórico guardado con cada fuente."
    SummaryOptionalFamily.WORK_TYPES -> "Usa el tipo histórico guardado con cada fuente."
    SummaryOptionalFamily.EXTRA_CLASSES -> "Conserva nombre y efecto históricos de cada clase."
    SummaryOptionalFamily.SITUATIONS -> "Ausencias, cancelaciones, carpeta médica, vacaciones y francos F ya existentes."
}

private fun SummaryCompliancePeriod.displayRange(): String =
    "${segment.startInclusive.displayName()} – ${segment.endExclusive.minusDays(1).displayName()}"

private fun LocalDate.displayName(): String = format(SummaryDateFormatter)
    .replaceFirstChar { it.titlecase(SpanishArgentina) }

private fun YearMonth.displayName(): String =
    "${month.getDisplayName(TextStyle.FULL, SpanishArgentina).replaceFirstChar { it.titlecase(SpanishArgentina) }} de $year"

private fun String.toTestKey(): String = lowercase()
    .map { if (it.isLetterOrDigit()) it else '-' }
    .joinToString("")
    .replace(Regex("-+"), "-")
    .trim('-')
