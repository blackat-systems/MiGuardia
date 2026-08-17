package com.blackatsystems.miguardia.ui.weather

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.weather.ShiftWeatherSummary
import com.blackatsystems.miguardia.core.domain.weather.WeatherCondition
import com.blackatsystems.miguardia.core.domain.weather.WeatherCoverage
import com.blackatsystems.miguardia.core.domain.weather.WeatherFreshness
import com.blackatsystems.miguardia.core.domain.weather.WeatherHour
import com.blackatsystems.miguardia.core.domain.weather.WeatherUnitSystem
import com.blackatsystems.miguardia.core.domain.weather.roundedTemperature
import com.blackatsystems.miguardia.core.domain.weather.spanishLabel
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.ScreenHeading
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.components.TransientConfirmation
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class WeatherActions(
    val openGlobal: () -> Unit = {},
    val openShift: (UUID) -> Unit = {},
    val loadBriefs: (Set<UUID>) -> Unit = {},
    val clearBriefs: () -> Unit = {},
    val close: () -> Unit = {},
    val enableAfterExplanation: () -> Unit = {},
    val setEnabled: (Boolean) -> Unit = {},
    val setUnitSystem: (WeatherUnitSystem) -> Unit = {},
    val setIncludeInNotifications: (Boolean) -> Unit = {},
    val refresh: () -> Unit = {},
    val clearCache: () -> Unit = {},
    val clearMessage: () -> Unit = {},
    val externalLinkFailed: () -> Unit = {},
) {
    companion object {
        fun from(viewModel: WeatherViewModel) = WeatherActions(
            openGlobal = viewModel::openGlobal,
            openShift = viewModel::openShift,
            loadBriefs = viewModel::loadBriefs,
            clearBriefs = viewModel::clearBriefs,
            close = viewModel::close,
            enableAfterExplanation = viewModel::enableAfterExplanation,
            setEnabled = viewModel::setEnabled,
            setUnitSystem = viewModel::setUnitSystem,
            setIncludeInNotifications = viewModel::setIncludeInNotifications,
            refresh = viewModel::manualRefresh,
            clearCache = viewModel::clearCache,
            clearMessage = viewModel::clearMessage,
            externalLinkFailed = viewModel::externalLinkFailed,
        )
    }
}

@Composable
fun WeatherSurfaceHost(state: WeatherUiState, actions: WeatherActions) {
    if (state.surface == WeatherSurface.NONE) return
    val context = LocalContext.current
    val openUrl: (String) -> Unit = { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            actions.externalLinkFailed()
        }
    }
    Dialog(onDismissRequest = actions.close) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            TransientConfirmation(state.infoMessage, actions.clearMessage) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ScreenHeading(
                        title = if (state.surface == WeatherSurface.GLOBAL) "Clima" else "Clima de la guardia",
                        supportingText = if (state.surface == WeatherSurface.GLOBAL) {
                            "Córdoba Capital fija. MiGuardia no usa la ubicación del teléfono."
                        } else {
                            state.selectedShift?.let { "${it.objectiveNameSnapshot} · ${it.timeRange()}" }.orEmpty()
                        },
                    )
                    state.errorMessage?.let {
                        PersistentMessage(it, onDismiss = actions.clearMessage, onRetry = actions.refresh)
                    }
                    if (state.isLoading) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                    } else if (state.surface == WeatherSurface.GLOBAL) {
                        GlobalWeatherSettings(state, actions, openUrl)
                    } else {
                        ShiftWeatherDetail(state, actions, openUrl)
                    }
                    OutlinedButton(onClick = actions.close, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
                }
            }
        }
    }
}

@Composable
private fun GlobalWeatherSettings(
    state: WeatherUiState,
    actions: WeatherActions,
    openUrl: (String) -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    SectionCard(
        title = "Proveedor y privacidad",
        supportingText = "Pronóstico orientativo; no reemplaza alertas oficiales ni garantiza condiciones de trabajo.",
    ) {
        Text("Open-Meteo recibe sólo la coordenada fija de Córdoba y la IP habitual de conexión. No se envían guardias, objetivos, direcciones ni datos del teléfono.")
        Text("El endpoint gratuito se usa únicamente durante desarrollo privado/no comercial. Una publicación comercial requiere un plan compatible u otro proveedor.")
        AttributionLinks(openUrl)
        if (!state.preferences.providerExplanationAccepted) {
            Button(onClick = actions.enableAfterExplanation, modifier = Modifier.fillMaxWidth()) {
                Text("Entiendo y habilitar Clima")
            }
        } else {
            ToggleRow("Habilitar Clima", state.preferences.enabled, actions.setEnabled)
        }
    }
    SectionCard("Unidades") {
        UnitChoice("Celsius (°C)", WeatherUnitSystem.CELSIUS, state, actions)
        UnitChoice("Fahrenheit (°F)", WeatherUnitSystem.FAHRENHEIT, state, actions)
        Text("Cambiar la unidad convierte la presentación; no vuelve a descargar.")
    }
    SectionCard("Notificaciones") {
        ToggleRow(
            label = "Incluir clima en avisos completos",
            checked = state.preferences.includeInNotifications,
            onChange = actions.setIncludeInNotifications,
            enabled = state.preferences.enabled,
        )
        Text("Los avisos reducidos u ocultos nunca muestran clima. Un fallo meteorológico no retrasa ni elimina el aviso.")
    }
    SectionCard("Caché privado") {
        Text(cacheStatus(state))
        Button(
            enabled = state.preferences.enabled && !state.isRefreshing,
            onClick = actions.refresh,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.isRefreshing) "Actualizando…" else "Actualizar") }
        TextButton(onClick = { confirmClear = true }) { Text("Borrar caché meteorológico") }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Borrar caché") },
            text = { Text("Se eliminará únicamente el último pronóstico guardado. La configuración se conserva.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; actions.clearCache() }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ShiftWeatherDetail(
    state: WeatherUiState,
    actions: WeatherActions,
    openUrl: (String) -> Unit,
) {
    val shift = state.selectedShift
    if (shift == null) {
        Text(state.ineligibleReason ?: "No pudimos encontrar la guardia.")
        return
    }
    ShiftIdentity(shift)
    Text("Ubicación: Córdoba Capital, Argentina", fontWeight = FontWeight.SemiBold)
    state.ineligibleReason?.let { Text(it) }
    if (!state.preferences.providerExplanationAccepted) {
        SectionCard(
            title = "Proveedor y privacidad",
            supportingText = "Pronóstico orientativo; no reemplaza alertas oficiales ni garantiza condiciones de trabajo.",
        ) {
            Text("Open-Meteo recibe sólo la coordenada fija de Córdoba y la IP habitual de conexión. No se envían guardias, objetivos, direcciones ni datos del teléfono.")
            Text("El endpoint gratuito se usa únicamente durante desarrollo privado/no comercial. Una publicación comercial requiere un plan compatible u otro proveedor.")
            AttributionLinks(openUrl)
            Button(onClick = actions.enableAfterExplanation, modifier = Modifier.fillMaxWidth()) {
                Text("Entiendo y habilitar Clima")
            }
        }
        return
    }
    if (!state.preferences.enabled) {
        SectionCard("Clima desactivado") {
            Text("La explicación ya fue aceptada. Podés volver a habilitar el pronóstico fijo de Córdoba.")
            Button(onClick = actions.enableAfterExplanation, modifier = Modifier.fillMaxWidth()) { Text("Habilitar Clima") }
        }
        AttributionLinks(openUrl)
        return
    }
    state.shiftSummary?.let { summary -> SummaryCard(summary, state.preferences.unitSystem) }
        ?: Text("No hay pronóstico disponible para esta guardia.")
    Text(cacheStatus(state))
    if (state.shiftHours.isEmpty()) {
        Text("No hay horas cubiertas dentro del horizonte disponible.")
    } else {
        SectionCard(
            title = "Temperatura durante la guardia",
            supportingText = "Deslizá hacia la derecha.",
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().testTag("weather-hourly-carousel"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.shiftHours, key = { it.validFrom }) { hour ->
                    HourCard(hour, state.preferences.unitSystem, shift.zoneId)
                }
            }
        }
    }
    Button(
        enabled = !state.isRefreshing && state.ineligibleReason == null,
        onClick = actions.refresh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.isRefreshing) "Actualizando…" else "Actualizar")
    }
    AttributionLinks(openUrl)
}

@Composable
private fun ShiftIdentity(shift: Shift) {
    SectionCard("Guardia") {
        Text("${shift.objectiveNameSnapshot} (${shift.objectiveAbbreviationSnapshot})", fontWeight = FontWeight.Bold)
        Text(shift.localStartDate.format(DateFormatter))
        Text(shift.timeRange())
    }
}

@Composable
private fun SummaryCard(summary: ShiftWeatherSummary, unit: WeatherUnitSystem) {
    SectionCard(
        title = "Resumen de toda la guardia",
        supportingText = when (summary.coverage) {
            WeatherCoverage.COMPLETE -> "Cobertura completa"
            WeatherCoverage.PARTIAL -> "Cobertura parcial: no se rellenan horas faltantes"
            WeatherCoverage.NONE -> "Fuera del horizonte disponible"
        },
    ) {
        summary.condition?.let { Text("Condición relevante: ${it.spanishLabel()}") }
        rangeText("Temperatura", summary.minimumTemperatureCelsius, summary.maximumTemperatureCelsius, unit)?.let { Text(it) }
        rangeText("Sensación", summary.minimumApparentTemperatureCelsius, summary.maximumApparentTemperatureCelsius, unit)?.let { Text(it) }
        summary.maximumPrecipitationProbabilityPercent?.let { Text("Máxima probabilidad de lluvia: $it %") }
        summary.precipitationMillimeters?.let { Text("Precipitación estimada en el intervalo: ${"%.1f".format(Locale.US, it)} mm") }
        summary.maximumWindSpeedKmh?.let { Text("Viento máximo: ${it.toInt()} km/h") }
        summary.maximumWindGustKmh?.let { Text("Ráfaga máxima: ${it.toInt()} km/h") }
    }
}

@Composable
private fun HourCard(hour: WeatherHour, unit: WeatherUnitSystem, zoneId: ZoneId) {
    Card(
        modifier = Modifier.width(148.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                hour.validFrom.atZone(zoneId).format(HourFormatter),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                hour.temperatureCelsius?.let { temperatureText(it, unit) } ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(hour.condition.spanishLabel(), maxLines = 2)
            hour.precipitationProbabilityPercent?.let {
                Text("Lluvia $it %", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AttributionLinks(openUrl: (String) -> Unit) {
    TextButton(onClick = { openUrl("https://open-meteo.com/") }) { Text("Datos meteorológicos: Open-Meteo") }
    TextButton(onClick = { openUrl("https://open-meteo.com/en/terms") }) { Text("Ver términos y privacidad") }
}

@Composable
private fun UnitChoice(label: String, unit: WeatherUnitSystem, state: WeatherUiState, actions: WeatherActions) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { actions.setUnitSystem(unit) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = state.preferences.unitSystem == unit, onClick = { actions.setUnitSystem(unit) })
        Text(label)
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

private fun cacheStatus(state: WeatherUiState): String {
    val forecast = state.forecast ?: return "Sin datos meteorológicos guardados."
    val status = when (state.freshness) {
        WeatherFreshness.FRESH -> "fresco"
        WeatherFreshness.STALE -> "desactualizado, todavía visible"
        WeatherFreshness.EXPIRED -> "vencido; no se usa como vigente"
        null -> "sin estado"
    }
    return "Última actualización: ${forecast.fetchedAt.atZone(forecast.location.zoneId).format(TimestampFormatter)} · $status."
}

private fun rangeText(label: String, minimum: Double?, maximum: Double?, unit: WeatherUnitSystem): String? {
    if (minimum == null && maximum == null) return null
    if (minimum == null) return "$label: hasta ${temperatureText(maximum!!, unit)}"
    if (maximum == null) return "$label: desde ${temperatureText(minimum, unit)}"
    return "$label: ${roundedTemperature(minimum, unit)}–${temperatureText(maximum, unit)}"
}

private fun temperatureText(value: Double, unit: WeatherUnitSystem): String =
    "${roundedTemperature(value, unit)} ${if (unit == WeatherUnitSystem.CELSIUS) "°C" else "°F"}"

private fun Shift.timeRange(): String =
    "${startTimeSnapshot.format(TimeFormatter)}–${endTimeSnapshot.format(TimeFormatter)}"

private val SpanishArgentina = Locale.forLanguageTag("es-AR")
private val DateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", SpanishArgentina)
private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm", SpanishArgentina)
private val HourFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm", SpanishArgentina)
private val TimestampFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", SpanishArgentina)
