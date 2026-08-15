package com.blackatsystems.miguardia.ui.nextevent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import com.blackatsystems.miguardia.ui.components.SectionCard
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ArgentineDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("es-AR"))
private val EventTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("es-AR"))

@Composable
fun NextEventCard(
    state: NextEventUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accessibility = state.result?.accessibilityDescription()
        ?: state.errorMessage
        ?: "Próximo evento, cargando"
    SectionCard(
        title = "Próximo evento",
        modifier = modifier
            .testTag("next-event-card")
            .semantics { contentDescription = accessibility },
    ) {
        state.result?.let { NextEventContent(it) }
        when (state.loadState) {
            NextEventLoadState.LOADING -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.width(22.dp))
                Text("Buscando guardias y francos…")
            }

            NextEventLoadState.ERROR -> PersistentMessage(
                message = state.errorMessage ?: "No pudimos actualizar el próximo evento.",
                onRetry = onRetry,
            )

            NextEventLoadState.CONTENT -> Unit
        }
    }
}

@Composable
private fun NextEventContent(result: NextEventResult) {
    when (result.primaryEvent) {
        NextEventPrimary.ONGOING_SHIFT -> ShiftEventContent(
            title = "Guardia en curso",
            shift = result.ongoingShifts.first(),
            count = result.ongoingShifts.size,
            remainingLabel = "Termina en ${formatRemaining(result.remaining)}",
        )

        NextEventPrimary.UPCOMING_SHIFT -> ShiftEventContent(
            title = "Próxima guardia",
            shift = result.upcomingShifts.first(),
            count = result.upcomingShifts.size,
            remainingLabel = "Comienza en ${formatRemaining(result.remaining)}",
        )

        NextEventPrimary.DAY_OFF -> DayOffContent(result.nextDayOff!!, result.referenceInstant.atZone(com.blackatsystems.miguardia.core.domain.AppDefaults.zoneId()).toLocalDate())
        NextEventPrimary.NONE -> {
            Text("Sin próximos eventos", fontWeight = FontWeight.SemiBold)
            Text("No hay guardias planificadas ni francos marcados explícitamente desde hoy.")
        }
    }
    val secondaryDayOff = result.nextDayOff
    if (
        secondaryDayOff != null &&
        (result.primaryEvent == NextEventPrimary.ONGOING_SHIFT || result.primaryEvent == NextEventPrimary.UPCOMING_SHIFT)
    ) {
        Text(
            "Próximo franco: ${secondaryDayOff.format(ArgentineDateFormatter)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ShiftEventContent(
    title: String,
    shift: Shift,
    count: Int,
    remainingLabel: String,
) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .width(7.dp)
                .height(58.dp)
                .background(Color(shift.colorArgbSnapshot), MaterialTheme.shapes.extraSmall),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${shift.objectiveNameSnapshot} (${shift.objectiveAbbreviationSnapshot})",
                fontWeight = FontWeight.SemiBold,
            )
            Text(shift.localStartDate.format(ArgentineDateFormatter))
            Text("${shift.startTimeSnapshot.format(EventTimeFormatter)}–${shift.endTimeSnapshot.format(EventTimeFormatter)}")
            shift.position?.takeIf(String::isNotBlank)?.let { Text("Puesto: $it") }
        }
    }
    Text(remainingLabel, fontWeight = FontWeight.SemiBold)
    if (count > 1) {
        Text("$count guardias comparten este estado.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DayOffContent(date: LocalDate, today: LocalDate) {
    Text("Próximo franco", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(date.format(ArgentineDateFormatter))
    Text(dayDistanceLabel(today, date), fontWeight = FontWeight.SemiBold)
}

internal fun formatRemaining(duration: Duration): String {
    val totalMinutes = duration.toMinutes().coerceAtLeast(0L)
    val days = totalMinutes / (24L * 60L)
    val hours = (totalMinutes % (24L * 60L)) / 60L
    val minutes = totalMinutes % 60L
    return buildList {
        if (days > 0L) add("$days d")
        if (hours > 0L) add("$hours h")
        if (minutes > 0L || isEmpty()) add("$minutes min")
    }.joinToString(" ")
}

internal fun dayDistanceLabel(today: LocalDate, date: LocalDate): String = when (val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)) {
    0L -> "Hoy"
    1L -> "Mañana"
    else -> "En $days días"
}

private fun NextEventResult.accessibilityDescription(): String = when (primaryEvent) {
    NextEventPrimary.ONGOING_SHIFT -> ongoingShifts.first().let { shift ->
        "Guardia en curso, ${shift.objectiveNameSnapshot}, ${shift.localStartDate.format(ArgentineDateFormatter)}, " +
            "${shift.startTimeSnapshot.format(EventTimeFormatter)} a ${shift.endTimeSnapshot.format(EventTimeFormatter)}, " +
            "termina en ${formatRemaining(remaining)}"
    }
    NextEventPrimary.UPCOMING_SHIFT -> upcomingShifts.first().let { shift ->
        "Próxima guardia, ${shift.objectiveNameSnapshot}, ${shift.localStartDate.format(ArgentineDateFormatter)}, " +
            "${shift.startTimeSnapshot.format(EventTimeFormatter)} a ${shift.endTimeSnapshot.format(EventTimeFormatter)}, " +
            "comienza en ${formatRemaining(remaining)}"
    }
    NextEventPrimary.DAY_OFF -> "Próximo franco, ${nextDayOff?.format(ArgentineDateFormatter)}"
    NextEventPrimary.NONE -> "Sin próximos eventos"
}
