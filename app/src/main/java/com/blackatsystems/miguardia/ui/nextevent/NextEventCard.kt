package com.blackatsystems.miguardia.ui.nextevent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.blackatsystems.miguardia.core.domain.nextevent.TodayCardPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.TodayCardProjection
import com.blackatsystems.miguardia.core.domain.nextevent.TodayShiftState
import com.blackatsystems.miguardia.core.domain.nextevent.TodayShiftSummary
import com.blackatsystems.miguardia.ui.components.HeroCard
import com.blackatsystems.miguardia.ui.components.PersistentMessage
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
        ?: "Hoy, cargando"
    HeroCard(
        title = "Hoy",
        compact = true,
        modifier = modifier
            .testTag("next-event-card")
            .semantics { contentDescription = accessibility },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.result?.let { TodayCardContent(it) }
            when (state.loadState) {
                NextEventLoadState.LOADING -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.width(22.dp))
                    Text("Buscando jornadas y francos…")
                }

                NextEventLoadState.ERROR -> PersistentMessage(
                    message = state.errorMessage ?: "No pudimos actualizar las jornadas de hoy.",
                    onRetry = onRetry,
                )

                NextEventLoadState.CONTENT -> Unit
            }
        }
    }
}

@Composable
private fun TodayCardContent(result: TodayCardProjection) {
    var expandedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val currentDate = result.date.toString()
    val expanded = expandedDate == currentDate
    Column(
        modifier = Modifier.testTag("today-card-summary"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (result.primary) {
            TodayCardPrimary.ONGOING_SHIFT -> PrimaryShiftContent(
                title = "Jornada en curso",
                summary = requireNotNull(result.primaryShift),
                remainingLabel = "Termina en ${formatRemaining(result.remaining)}",
                todayShiftCount = result.todayShiftCount,
            )

            TodayCardPrimary.UPCOMING_SHIFT -> PrimaryShiftContent(
                title = "Próxima jornada",
                summary = requireNotNull(result.primaryShift),
                remainingLabel = "Comienza en ${formatRemaining(result.remaining)}",
                todayShiftCount = result.todayShiftCount,
            )

            TodayCardPrimary.COMPLETED_SUMMARY -> {
                val noun = if (result.completedTodayCount == 1) "jornada completada" else "jornadas completadas"
                Text(
                    "Hoy: ${result.completedTodayCount} $noun",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TodayShiftCount(result.todayShiftCount, result.completedTodayCount)
            }

            TodayCardPrimary.NO_WORK_TODAY -> {
                Text(
                    "Hoy no tenés trabajo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TodayShiftCount(result.todayShiftCount)
                if (result.futureEvent.primaryEvent != NextEventPrimary.NONE) {
                    FutureEventContent(result.futureEvent, heading = "Próximo evento")
                }
            }

            TodayCardPrimary.FUTURE_EVENT -> FutureEventContent(result.futureEvent)
            TodayCardPrimary.EMPTY -> EmptyEventContent()
        }

        if (result.canExpand) {
            TextButton(
                onClick = { expandedDate = if (expanded) null else currentDate },
                modifier = Modifier.testTag("today-card-toggle"),
            ) {
                Text(if (expanded) "Ocultar jornadas de hoy" else "Ver jornadas de hoy")
            }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("today-card-list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    result.shifts.forEach { summary -> TodayShiftRow(summary) }
                }
            }
        }
    }
}

@Composable
private fun TodayShiftCount(
    todayShiftCount: Int,
    countAlreadyShown: Int = 0,
) {
    if (todayShiftCount > 0 && todayShiftCount != countAlreadyShown) {
        val noun = if (todayShiftCount == 1) "jornada registrada hoy" else "jornadas registradas hoy"
        Text("$todayShiftCount $noun.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PrimaryShiftContent(
    title: String,
    summary: TodayShiftSummary,
    remainingLabel: String,
    todayShiftCount: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            remainingLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        ShiftIdentity(summary.shift)
        if (summary.startedYesterday) {
            Text(
                "Inició ayer, ${summary.shift.localStartDate.format(ArgentineDateFormatter)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        summary.shift.position?.takeIf(String::isNotBlank)?.let { position ->
            Text("Puesto: $position", style = MaterialTheme.typography.bodyMedium)
        }
        if (todayShiftCount > 1) {
            Text("$todayShiftCount jornadas hoy.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TodayShiftRow(summary: TodayShiftSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .testTag("today-card-shift-${summary.shift.id}"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .width(7.dp)
                .fillMaxHeight()
                .background(Color(summary.shift.colorArgbSnapshot), MaterialTheme.shapes.extraSmall),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "${summary.shift.objectiveAbbreviationSnapshot} · ${summary.shift.objectiveNameSnapshot}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${summary.shift.localStartDate.format(ArgentineDateFormatter)} · " +
                    "Horario planificado: ${summary.shift.startTimeSnapshot.format(EventTimeFormatter)}–" +
                    summary.shift.endTimeSnapshot.format(EventTimeFormatter),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                summary.displayState(),
                modifier = Modifier.testTag("today-card-shift-state-${summary.shift.id}"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            summary.shift.position?.takeIf(String::isNotBlank)?.let { position ->
                Text("Puesto: $position", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ShiftIdentity(shift: Shift) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .width(7.dp)
                .fillMaxHeight()
                .background(Color(shift.colorArgbSnapshot), MaterialTheme.shapes.extraSmall),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "${shift.objectiveAbbreviationSnapshot} · ${shift.objectiveNameSnapshot}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${shift.localStartDate.format(ArgentineDateFormatter)} · " +
                    "Horario planificado: ${shift.startTimeSnapshot.format(EventTimeFormatter)}–" +
                    shift.endTimeSnapshot.format(EventTimeFormatter),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FutureEventContent(
    result: NextEventResult,
    heading: String? = null,
) {
    heading?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
    when (result.primaryEvent) {
        NextEventPrimary.ONGOING_SHIFT -> FutureShiftContent(
            title = "Jornada en curso",
            shift = result.ongoingShifts.first(),
            count = result.ongoingShifts.size,
            remainingLabel = "Termina en ${formatRemaining(result.remaining)}",
        )

        NextEventPrimary.UPCOMING_SHIFT -> FutureShiftContent(
            title = "Próxima jornada",
            shift = result.upcomingShifts.first(),
            count = result.upcomingShifts.size,
            remainingLabel = "Comienza en ${formatRemaining(result.remaining)}",
        )

        NextEventPrimary.DAY_OFF -> DayOffContent(
            date = requireNotNull(result.nextDayOff),
            today = result.referenceInstant
                .atZone(com.blackatsystems.miguardia.core.domain.AppDefaults.zoneId())
                .toLocalDate(),
        )

        NextEventPrimary.NONE -> EmptyEventContent()
    }
    val secondaryDayOff = result.nextDayOff
    if (
        secondaryDayOff != null &&
        (result.primaryEvent == NextEventPrimary.ONGOING_SHIFT ||
            result.primaryEvent == NextEventPrimary.UPCOMING_SHIFT)
    ) {
        Text(
            "Próximo franco: ${secondaryDayOff.format(ArgentineDateFormatter)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun FutureShiftContent(
    title: String,
    shift: Shift,
    count: Int,
    remainingLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ShiftIdentity(shift)
        shift.position?.takeIf(String::isNotBlank)?.let { position ->
            Text("Puesto: $position", style = MaterialTheme.typography.bodyMedium)
        }
        Text(remainingLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        if (count > 1) Text("$count jornadas comparten este estado.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DayOffContent(date: LocalDate, today: LocalDate) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Próximo franco",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(date.format(ArgentineDateFormatter), style = MaterialTheme.typography.bodyMedium)
    }
    Text(dayDistanceLabel(today, date), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun EmptyEventContent() {
    Text("Sin próximos eventos", fontWeight = FontWeight.SemiBold)
    Text("No hay jornadas pendientes ni francos marcados explícitamente desde hoy.")
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

internal fun dayDistanceLabel(today: LocalDate, date: LocalDate): String =
    when (val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)) {
        0L -> "Hoy"
        1L -> "Mañana"
        else -> "En $days días"
    }

private fun TodayShiftSummary.displayState(): String = buildList {
    add(
        when (state) {
            TodayShiftState.UPCOMING -> "Próxima"
            TodayShiftState.IN_PROGRESS -> "En curso"
            TodayShiftState.COMPLETED -> "Completada"
            TodayShiftState.CANCELLED -> "Cancelada"
            TodayShiftState.ABSENT -> "Ausente"
            TodayShiftState.PROTECTED -> "Protegida"
        },
    )
    if (hasActualTime) add("horario real registrado")
    if (isVacationProtected) add("Vacaciones")
    if (isMedicalLeaveProtected) add("carpeta médica")
}.joinToString(" · ")

private fun TodayCardProjection.accessibilityDescription(): String = buildString {
    append("Hoy, ").append(date.format(ArgentineDateFormatter)).append(". ")
    append(
        when (primary) {
            TodayCardPrimary.ONGOING_SHIFT -> "Jornada en curso"
            TodayCardPrimary.UPCOMING_SHIFT -> "Próxima jornada"
            TodayCardPrimary.COMPLETED_SUMMARY -> if (completedTodayCount == 1) {
                "1 jornada completada"
            } else {
                "$completedTodayCount jornadas completadas"
            }
            TodayCardPrimary.NO_WORK_TODAY -> buildString {
                append("Hoy no tenés trabajo")
                if (futureEvent.primaryEvent != NextEventPrimary.NONE) {
                    append(". ").append(futureEvent.accessibilityDescription())
                }
            }
            TodayCardPrimary.FUTURE_EVENT -> futureEvent.accessibilityDescription()
            TodayCardPrimary.EMPTY -> "Sin próximos eventos"
        },
    )
    if (todayShiftCount > 0) {
        append(". ")
        if (todayShiftCount == 1) append("1 jornada de hoy")
        else append("$todayShiftCount jornadas de hoy")
    }
    shifts.forEach { summary ->
        append(". ")
            .append(summary.shift.objectiveAbbreviationSnapshot)
            .append(", ")
            .append(summary.shift.objectiveNameSnapshot)
            .append(", ")
            .append(summary.shift.localStartDate.format(ArgentineDateFormatter))
            .append(", ")
            .append(summary.shift.startTimeSnapshot.format(EventTimeFormatter))
            .append(" a ")
            .append(summary.shift.endTimeSnapshot.format(EventTimeFormatter))
            .append(", ")
            .append(summary.displayState())
    }
}

private fun NextEventResult.accessibilityDescription(): String = when (primaryEvent) {
    NextEventPrimary.ONGOING_SHIFT -> ongoingShifts.first().let { shift ->
        "Jornada en curso, ${shift.objectiveNameSnapshot}, " +
            "${shift.localStartDate.format(ArgentineDateFormatter)}, " +
            "${shift.startTimeSnapshot.format(EventTimeFormatter)} a " +
            "${shift.endTimeSnapshot.format(EventTimeFormatter)}, " +
            "termina en ${formatRemaining(remaining)}"
    }
    NextEventPrimary.UPCOMING_SHIFT -> upcomingShifts.first().let { shift ->
        "Próxima jornada, ${shift.objectiveNameSnapshot}, " +
            "${shift.localStartDate.format(ArgentineDateFormatter)}, " +
            "${shift.startTimeSnapshot.format(EventTimeFormatter)} a " +
            "${shift.endTimeSnapshot.format(EventTimeFormatter)}, " +
            "comienza en ${formatRemaining(remaining)}"
    }
    NextEventPrimary.DAY_OFF -> "Próximo franco, ${nextDayOff?.format(ArgentineDateFormatter)}"
    NextEventPrimary.NONE -> "Sin próximos eventos"
}
