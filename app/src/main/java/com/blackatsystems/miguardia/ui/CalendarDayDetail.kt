package com.blackatsystems.miguardia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.core.domain.calendar.CalendarDay
import com.blackatsystems.miguardia.core.domain.calendar.CalendarShift
import com.blackatsystems.miguardia.core.domain.calendar.ShiftTemporalStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.nextevent.isEligibleForWeather
import com.blackatsystems.miguardia.core.domain.weather.WeatherCoverage
import com.blackatsystems.miguardia.core.domain.weather.WeatherFreshness
import com.blackatsystems.miguardia.core.domain.weather.WeatherUnitSystem
import com.blackatsystems.miguardia.core.domain.weather.roundedTemperature
import com.blackatsystems.miguardia.core.domain.weather.spanishLabel
import com.blackatsystems.miguardia.ui.availability.AvailabilityActions
import com.blackatsystems.miguardia.ui.availability.AvailabilityDaySection
import com.blackatsystems.miguardia.ui.availability.AvailabilityUiState
import com.blackatsystems.miguardia.ui.hours.IndependentExtraDetailCard
import com.blackatsystems.miguardia.ui.management.V2DayEditEntry
import com.blackatsystems.miguardia.ui.management.V2ShiftEditUiState
import com.blackatsystems.miguardia.ui.management.V2ShiftActualActions
import com.blackatsystems.miguardia.ui.management.V2ShiftActualDetailCard
import com.blackatsystems.miguardia.ui.management.V2ShiftActualUiState
import com.blackatsystems.miguardia.ui.weather.ShiftWeatherBrief
import com.blackatsystems.miguardia.ui.weather.WeatherUiState
import com.blackatsystems.miguardia.weather.WeatherCredits

@Composable
internal fun DayDetailSheet(
    day: CalendarDay,
    referenceInstant: java.time.Instant,
    onEditDay: (() -> Unit)?,
    onOpenNotes: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
    onOpenNotifications: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
    onOpenWeather: (java.util.UUID) -> Unit,
    weatherState: WeatherUiState,
    v2ShiftEditState: V2ShiftEditUiState? = null,
    onRetryV2Inspection: () -> Unit = {},
    v2ShiftActualState: V2ShiftActualUiState = V2ShiftActualUiState(),
    v2ShiftActualActions: V2ShiftActualActions = V2ShiftActualActions(),
    independentExtras: List<IndependentExtraWorkRecord> = emptyList(),
    canRegisterIndependentExtra: Boolean = false,
    onRegisterIndependentExtra: () -> Unit = {},
    onCorrectIndependentExtra: (IndependentExtraWorkRecord) -> Unit = {},
    onDeleteIndependentExtra: (IndependentExtraWorkRecord) -> Unit = {},
    availabilityState: AvailabilityUiState = AvailabilityUiState(),
    availabilityActions: AvailabilityActions = AvailabilityActions(),
    onManageDayOff: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = day.date.fullDisplayName(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        if (
            day.shifts.isEmpty() &&
            independentExtras.isEmpty() &&
            availabilityState.windowsOn(day.date).isEmpty() &&
            day.explicitStatus == null &&
            !day.hasMedicalLeave
        ) {
            Text(stringResource(R.string.undefined_implicit_detail))
        }
        day.shifts.forEachIndexed { index, calendarShift ->
            if (index > 0) HorizontalDivider()
            ShiftDetail(
                calendarShift = calendarShift,
                excludedByVacation = day.vacation != null && calendarShift.shift.status == ShiftStatus.PLANNED,
                onOpenNotes = onOpenNotes,
                onOpenNotifications = onOpenNotifications,
                onOpenWeather = if (
                    calendarShift.shift.isEligibleForWeather(referenceInstant, listOfNotNull(day.vacation))
                ) {
                    onOpenWeather
                } else {
                    null
                },
                weatherEnabled = weatherState.preferences.enabled,
                weatherUnit = weatherState.preferences.unitSystem,
                weatherBrief = weatherState.shiftBriefs[calendarShift.shift.id],
                weatherLoading = calendarShift.shift.id in weatherState.loadingBriefIds,
            )
            V2ShiftActualDetailCard(
                shift = calendarShift.shift,
                ordinal = index + 1,
                count = day.shifts.size,
                ownerDate = day.date,
                rowState = v2ShiftActualState.rows[calendarShift.shift.id],
                actions = v2ShiftActualActions,
            )
        }
        independentExtras.forEach { record ->
            HorizontalDivider()
            IndependentExtraDetailCard(
                record = record,
                onCorrect = { onCorrectIndependentExtra(record) },
                onDelete = { onDeleteIndependentExtra(record) },
            )
        }
        when (day.explicitStatus) {
            ExplicitDayStatusType.DAY_OFF -> Text(stringResource(R.string.day_off_explicit_detail))
            ExplicitDayStatusType.UNDEFINED -> Text(stringResource(R.string.undefined_explicit_detail))
            null -> Unit
        }
        onManageDayOff?.let { openDayOff ->
            OutlinedButton(
                onClick = openDayOff,
                modifier = Modifier.fillMaxWidth().testTag("day-detail-manage-day-off"),
            ) {
                Text(if (day.explicitStatus == ExplicitDayStatusType.DAY_OFF) "Quitar Franco…" else "Marcar Franco…")
            }
        }
        if (day.hasMedicalLeave) {
            Text(stringResource(R.string.medical_leave_detail))
        }
        day.holiday?.let { holiday ->
            Text("Feriado: ${holiday.name ?: "sin nombre"}", fontWeight = FontWeight.SemiBold)
        }
        day.vacation?.let { vacation ->
            Text(
                "Vacaciones: ${vacation.startDate.fullDisplayName()} – ${vacation.endDateInclusive.fullDisplayName()}",
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (v2ShiftEditState != null && onEditDay != null) {
            V2DayEditEntry(
                state = v2ShiftEditState,
                date = day.date,
                onBegin = onEditDay,
                onRetry = onRetryV2Inspection,
            )
        }
        if (canRegisterIndependentExtra) {
            OutlinedButton(
                onClick = onRegisterIndependentExtra,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register-independent-extra-${day.date}"),
            ) {
                Text("Registrar trabajo extra")
            }
        }
        AvailabilityDaySection(
            date = day.date,
            state = availabilityState,
            actions = availabilityActions,
        )
    }
}

@Composable
private fun ShiftDetail(
    calendarShift: CalendarShift,
    excludedByVacation: Boolean = false,
    onOpenNotes: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
    onOpenNotifications: (com.blackatsystems.miguardia.core.domain.model.Shift) -> Unit,
    onOpenWeather: ((java.util.UUID) -> Unit)? = null,
    weatherEnabled: Boolean = false,
    weatherUnit: WeatherUnitSystem = WeatherUnitSystem.CELSIUS,
    weatherBrief: ShiftWeatherBrief? = null,
    weatherLoading: Boolean = false,
) {
    val shift = calendarShift.shift
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(48.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(Color(shift.colorArgbSnapshot)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = "${shift.objectiveNameSnapshot} (${shift.objectiveAbbreviationSnapshot})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(shift.timeRange())
            Text(calendarShift.temporalStatus.displayLabel(), fontWeight = FontWeight.Medium)
            shift.position?.takeIf { it.isNotBlank() }?.let { position ->
                Text(stringResource(R.string.position_value, position))
            }
            shift.objectiveAddressSnapshot?.takeIf { it.isNotBlank() }?.let { address ->
                Text(address)
            }
            OutlinedButton(onClick = { onOpenNotes(shift) }, modifier = Modifier.fillMaxWidth()) {
                Text("Notas")
            }
            OutlinedButton(
                onClick = { onOpenNotifications(shift) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shift-notifications-${shift.id}"),
            ) {
                Text("Avisos de esta jornada")
            }
            if (onOpenWeather != null) {
                ShiftWeatherBriefCard(
                    enabled = weatherEnabled,
                    loading = weatherLoading,
                    brief = weatherBrief,
                    unit = weatherUnit,
                    onOpen = { onOpenWeather(shift.id) },
                )
            }
            if (excludedByVacation) {
                Text(
                    "Esta jornada se conserva, pero no computa horas porque su fecha inicial está en vacaciones.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun ShiftWeatherBriefCard(
    enabled: Boolean,
    loading: Boolean,
    brief: ShiftWeatherBrief?,
    unit: WeatherUnitSystem,
    onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen,
        enabled = enabled && brief != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Clima durante la jornada", fontWeight = FontWeight.Bold)
            when {
                !enabled -> Text("Clima está desactivado. Podés activarlo desde el menú Clima.")
                brief != null -> {
                    val summary = brief.summary
                    val minimumTemperature = summary.minimumTemperatureCelsius
                    val maximumTemperature = summary.maximumTemperatureCelsius
                    val temperatures = if (minimumTemperature != null && maximumTemperature != null) {
                        val suffix = if (unit == WeatherUnitSystem.CELSIUS) "°C" else "°F"
                        "${roundedTemperature(minimumTemperature, unit)}–" +
                            "${roundedTemperature(maximumTemperature, unit)} $suffix"
                    } else {
                        null
                    }
                    Text(
                        listOfNotNull(summary.condition?.spanishLabel(), temperatures).joinToString(" · ")
                            .ifBlank { "Pronóstico disponible" },
                    )
                    summary.maximumPrecipitationProbabilityPercent?.let { probability ->
                        Text("Probabilidad máxima de lluvia: $probability %")
                    }
                    Text(
                        when (summary.coverage) {
                            WeatherCoverage.COMPLETE -> "Cobertura completa del horario"
                            WeatherCoverage.PARTIAL -> "Cobertura parcial; no se inventan las horas faltantes"
                            WeatherCoverage.NONE -> "Sin cobertura"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        when (brief.freshness) {
                            WeatherFreshness.FRESH -> "Actualizado · tocá para ver el detalle horario"
                            WeatherFreshness.STALE -> "Pronóstico antiguo · tocá para actualizar o ver el detalle"
                            WeatherFreshness.EXPIRED -> "Pronóstico vencido · tocá para intentar actualizar"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (summary.coverage != WeatherCoverage.NONE) {
                        Text(WeatherCredits.SUMMARY, style = MaterialTheme.typography.bodySmall)
                        Text(WeatherCredits.OPEN, style = MaterialTheme.typography.bodySmall)
                    }
                    if (loading) {
                        Text(
                            "Actualizando sin ocultar el último pronóstico…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                loading -> Text("Consultando el clima del objetivo…")
                else -> Text("No hay cobertura meteorológica para todo o parte de este horario.")
            }
        }
    }
}

@Composable
private fun ShiftTemporalStatus.displayLabel(): String = stringResource(
    when (this) {
        ShiftTemporalStatus.UPCOMING -> R.string.shift_upcoming
        ShiftTemporalStatus.IN_PROGRESS -> R.string.shift_in_progress
        ShiftTemporalStatus.COMPLETED -> R.string.shift_completed
        ShiftTemporalStatus.CANCELLED -> R.string.shift_cancelled
        ShiftTemporalStatus.ABSENT -> R.string.shift_absent
    },
)
