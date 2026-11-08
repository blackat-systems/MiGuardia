package com.blackatsystems.miguardia.ui.nextevent

import com.blackatsystems.miguardia.ui.EventTimeRangeStyle
import com.blackatsystems.miguardia.ui.formatEventTimeRange
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult
import com.blackatsystems.miguardia.core.domain.nextevent.TodayCardPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.TodayCardProjection
import com.blackatsystems.miguardia.core.domain.nextevent.TodayShiftState
import com.blackatsystems.miguardia.core.domain.nextevent.TodayShiftSummary
import com.blackatsystems.miguardia.ui.components.HeroCard
import com.blackatsystems.miguardia.ui.components.DateBadge
import com.blackatsystems.miguardia.ui.components.PersistentMessage
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ArgentineDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("es-AR"))

@Composable
fun NextEventCard(
    state: NextEventUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A refresh may temporarily remove result content. Keep the date-bound saved
    // expansion at the stable card owner, not inside that conditional subtree.
    var expandedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val accessibility = state.result?.accessibilityDescription()
        ?: state.errorMessage
        ?: "Eventos laborales, cargando"
    HeroCard(
        title = state.result?.cardTitle() ?: "Eventos laborales",
        compact = true,
        modifier = modifier
            .testTag("next-event-card")
            .semantics { contentDescription = accessibility },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.result?.let { result ->
                val currentDate = result.date.toString()
                val expanded = expandedDate == currentDate
                TodayCardContent(result, expanded = expanded,
                    onToggleExpanded = { expandedDate = if (expanded) null else currentDate })
            }
            when (state.loadState) {
                NextEventLoadState.LOADING -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.width(22.dp))
                    Text("Buscando jornadas, disponibilidad y francos…")
                }

                NextEventLoadState.ERROR -> PersistentMessage(
                    message = state.errorMessage ?: "No pudimos actualizar los eventos laborales de hoy.",
                    onRetry = onRetry,
                )

                NextEventLoadState.CONTENT -> Unit
            }
        }
    }
}

@Composable
private fun TodayCardContent(result: TodayCardProjection, expanded: Boolean, onToggleExpanded: () -> Unit) {
    Column(
        modifier = Modifier.testTag("today-card-summary"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // A completed/cancelled day must not hide the next eligible journey.
        val futureShiftShown = result.futureEvent.primaryEvent == NextEventPrimary.UPCOMING_SHIFT &&
            result.primary != TodayCardPrimary.UPCOMING_SHIFT
        if (futureShiftShown) FutureEventContent(result.futureEvent)
        when (result.primary) {
            TodayCardPrimary.ONGOING_SHIFT -> {
                val primary = requireNotNull(result.primaryShift)
                PrimaryShiftContent(
                    summary = primary,
                    remainingLabel = "Termina en ${formatRemaining(result.remaining)}",
                    todayShiftCount = result.todayShiftCount,
                    today = result.date,
                )
                WorkEventCompanions(result.futureEvent, primary.event)
            }

            TodayCardPrimary.UPCOMING_SHIFT -> {
                val primary = requireNotNull(result.primaryShift)
                PrimaryShiftContent(
                    summary = primary,
                    remainingLabel = "Comienza en ${formatRemaining(result.remaining)}",
                    todayShiftCount = result.todayShiftCount,
                    today = result.date,
                )
                WorkEventCompanions(result.futureEvent, primary.event)
            }

            TodayCardPrimary.COMPLETED_SUMMARY -> {
                val noun = if (result.completedTodayCount == 1) "jornada completada" else "jornadas completadas"
                Text(
                    "Hoy: ${result.completedTodayCount} $noun",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TodayShiftCount(result.todayShiftCount, result.completedTodayCount)
            }

            TodayCardPrimary.NO_WORK_TODAY -> {
                Text(
                    "Hoy no tenés trabajo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TodayShiftCount(result.todayShiftCount)
                if (!futureShiftShown && result.futureEvent.primaryEvent != NextEventPrimary.NONE) {
                    FutureEventContent(result.futureEvent, heading = "Próximo evento")
                }
            }

            TodayCardPrimary.FUTURE_EVENT -> if (!futureShiftShown) FutureEventContent(result.futureEvent)
            TodayCardPrimary.EMPTY -> EmptyEventContent()
        }

        // A lone active/upcoming journey is already shown in full above. Keep
        // expansion for other journeys and for historical summary-only states.
        val singleJourneyAlreadyShown = result.shifts.singleOrNull()?.event?.shiftId
            ?.let { it == result.primaryShift?.event?.shiftId } == true
        if (result.canExpand && !singleJourneyAlreadyShown) {
            TextButton(
                onClick = onToggleExpanded,
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
    summary: TodayShiftSummary,
    remainingLabel: String,
    todayShiftCount: Int,
    today: LocalDate,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DateBadge(
            label = journeyDateLabel(today, summary.event.start.atZone(summary.event.zoneId).toLocalDate()),
            modifier = Modifier.testTag("next-journey-date"),
        )
        Text(
            remainingLabel,
            modifier = Modifier.testTag("next-journey-countdown"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        ShiftIdentity(summary.event)
        if (summary.startedBeforeToday) {
            val prefix = if (summary.event.ownerLocalDate == today.minusDays(1)) "Inició ayer," else "Inició el"
            Text(
                "$prefix ${summary.event.ownerLocalDate.format(ArgentineDateFormatter)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        summary.event.positionSnapshot?.takeIf(String::isNotBlank)?.let { position ->
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
            .testTag("today-card-shift-${summary.event.shiftId}"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .width(7.dp)
                .fillMaxHeight()
                .background(Color(summary.event.colorArgbSnapshot), MaterialTheme.shapes.extraSmall),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "${summary.event.placeAbbreviationSnapshot} · ${summary.event.placeNameSnapshot}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Horario planificado: ${summary.event.eventTimeRange(EventTimeRangeStyle.COMPACT_DATED)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                summary.displayState(),
                modifier = Modifier.testTag("today-card-shift-state-${summary.event.shiftId}"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            summary.event.positionSnapshot?.takeIf(String::isNotBlank)?.let { position ->
                Text("Puesto: $position", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ShiftIdentity(shift: NextEventItem.Shift) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .testTag("next-journey-identity"),
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
                "${shift.placeAbbreviationSnapshot} · ${shift.placeNameSnapshot}",
                style = MaterialTheme.typography.titleMedium,
            )
            // Style v2: the time range is the big figure of the card.
            Text(
                AnnotatedString(shift.eventTimeRange(EventTimeRangeStyle.COMPACT)),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
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
    val primary = result.primaryEvents.firstOrNull()
    when (result.primaryEvent) {
        NextEventPrimary.ONGOING_SHIFT -> FutureShiftContent(
            today = result.referenceInstant.atZone(result.zoneId).toLocalDate(),
            shift = primary as NextEventItem.Shift,
            count = result.primaryEvents.count { it is NextEventItem.Shift },
            remainingLabel = "Termina en ${formatRemaining(result.remaining)}",
        )

        NextEventPrimary.UPCOMING_SHIFT -> FutureShiftContent(
            today = result.referenceInstant.atZone(result.zoneId).toLocalDate(),
            shift = primary as NextEventItem.Shift,
            count = result.primaryEvents.count { it is NextEventItem.Shift },
            remainingLabel = "Comienza en ${formatRemaining(result.remaining)}",
        )

        NextEventPrimary.ONGOING_AVAILABILITY -> AvailabilityContent(
            title = "Disponibilidad activa",
            event = primary as NextEventItem.Availability,
            remainingLabel = "Termina en ${formatRemaining(result.remaining)}",
        )

        NextEventPrimary.UPCOMING_AVAILABILITY -> AvailabilityContent(
            title = "Próxima disponibilidad",
            event = primary as NextEventItem.Availability,
            remainingLabel = "Comienza en ${formatRemaining(result.remaining)}",
        )

        NextEventPrimary.DAY_OFF -> DayOffContent(
            date = requireNotNull(result.nextDayOff),
            today = result.referenceInstant.atZone(result.zoneId).toLocalDate(),
        )

        NextEventPrimary.NONE -> EmptyEventContent()
    }
    WorkEventCompanions(result, primary)
}

@Composable
private fun WorkEventCompanions(
    result: NextEventResult,
    primary: NextEventItem?,
) {
    val secondaryEvents = when (result.primaryEvent) {
        NextEventPrimary.ONGOING_SHIFT,
        NextEventPrimary.ONGOING_AVAILABILITY,
        -> result.activeEvents.filterNot { it.identity == primary?.identity }
        NextEventPrimary.UPCOMING_SHIFT,
        NextEventPrimary.UPCOMING_AVAILABILITY,
        -> result.primaryEvents.drop(1)
        NextEventPrimary.DAY_OFF,
        NextEventPrimary.NONE,
        -> emptyList()
    }
    secondaryEvents.forEach { event -> SecondaryEventContent(event) }
    val secondaryDayOff = result.nextDayOff
    if (
        secondaryDayOff != null &&
        (result.primaryEvent == NextEventPrimary.ONGOING_SHIFT ||
            result.primaryEvent == NextEventPrimary.UPCOMING_SHIFT ||
            result.primaryEvent == NextEventPrimary.ONGOING_AVAILABILITY ||
            result.primaryEvent == NextEventPrimary.UPCOMING_AVAILABILITY)
    ) {
        Text(
            "Próximo franco: ${secondaryDayOff.format(ArgentineDateFormatter)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun FutureShiftContent(
    today: LocalDate,
    shift: NextEventItem.Shift,
    count: Int,
    remainingLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DateBadge(
            label = journeyDateLabel(today, shift.start.atZone(shift.zoneId).toLocalDate()),
            modifier = Modifier.testTag("next-journey-date"),
        )
        Text(
            remainingLabel,
            modifier = Modifier.testTag("next-journey-countdown"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        ShiftIdentity(shift)
        shift.positionSnapshot?.takeIf(String::isNotBlank)?.let { position ->
            Text("Puesto: $position", style = MaterialTheme.typography.bodyMedium)
        }
        if (count > 1) Text("$count jornadas comparten este estado.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AvailabilityContent(
    title: String,
    event: NextEventItem.Availability,
    remainingLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(event.labelSnapshot, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            AnnotatedString(event.eventTimeRange(EventTimeRangeStyle.COMPACT_DATED)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(remainingLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryEventContent(event: NextEventItem) {
    when (event) {
        is NextEventItem.Shift -> Text(
            "También: Jornada · ${event.placeAbbreviationSnapshot} · " +
                event.eventTimeRange(EventTimeRangeStyle.COMPACT_DATED),
            style = MaterialTheme.typography.bodySmall,
        )
        is NextEventItem.Availability -> Text(
            "También: ${event.labelSnapshot} · " +
                event.eventTimeRange(EventTimeRangeStyle.COMPACT_DATED),
            style = MaterialTheme.typography.bodySmall,
        )
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
            fontWeight = FontWeight.SemiBold,
        )
        Text(date.format(ArgentineDateFormatter), style = MaterialTheme.typography.bodyMedium)
    }
    Text(dayDistanceLabel(today, date), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun EmptyEventContent() {
    Text("Sin próximos eventos", fontWeight = FontWeight.SemiBold)
    Text("No hay jornadas, disponibilidad ni francos explícitos pendientes desde hoy.")
}

internal fun formatRemaining(duration: Duration): String {
    val totalMinutes = duration.toMinutes().coerceAtLeast(0L)
    if (totalMinutes == 0L) return "menos de un minuto"
    val days = totalMinutes / (24L * 60L)
    val hours = (totalMinutes % (24L * 60L)) / 60L
    val minutes = totalMinutes % 60L
    return buildList {
        if (days > 0L) add("$days ${if (days == 1L) "día" else "días"}")
        if (hours > 0L) add("$hours ${if (hours == 1L) "hora" else "horas"}")
        if (minutes > 0L) add("$minutes ${if (minutes == 1L) "minuto" else "minutos"}")
    }.joinToString(", ")
}

internal fun journeyDateLabel(today: LocalDate, date: LocalDate): String = when (date) {
    today -> "Hoy"
    today.plusDays(1) -> "Mañana"
    else -> date.format(ArgentineDateFormatter)
}

private fun TodayCardProjection.cardTitle(): String = when (futureEvent.primaryEvent) {
    NextEventPrimary.UPCOMING_SHIFT -> "Próxima jornada"
    NextEventPrimary.ONGOING_SHIFT -> "Jornada en curso"
    else -> "Eventos laborales"
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
    append(cardTitle()).append(". ")
    if (futureEvent.primaryEvent == NextEventPrimary.UPCOMING_SHIFT ||
        futureEvent.primaryEvent == NextEventPrimary.ONGOING_SHIFT
    ) {
        append(futureEvent.accessibilityDescription()).append(". ")
    }
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
            .append(summary.event.placeAbbreviationSnapshot)
            .append(", ")
            .append(summary.event.placeNameSnapshot)
            .append(", ")
            .append(summary.event.eventTimeRange(EventTimeRangeStyle.COMPACT_DATED))
            .append(", ")
            .append(summary.displayState())
    }
}

private fun NextEventResult.accessibilityDescription(): String = when (primaryEvent) {
    NextEventPrimary.ONGOING_SHIFT -> (primaryEvents.first() as NextEventItem.Shift).let { shift ->
        "Jornada en curso, ${shift.placeNameSnapshot}, " +
            "${shift.eventTimeRange(EventTimeRangeStyle.COMPACT_DATED)}, " +
            "termina en ${formatRemaining(remaining)}"
    }
    NextEventPrimary.UPCOMING_SHIFT -> (primaryEvents.first() as NextEventItem.Shift).let { shift ->
        "Próxima jornada, ${shift.placeNameSnapshot}, " +
            "${shift.eventTimeRange(EventTimeRangeStyle.COMPACT_DATED)}, " +
            "comienza en ${formatRemaining(remaining)}"
    }
    NextEventPrimary.ONGOING_AVAILABILITY ->
        (primaryEvents.first() as NextEventItem.Availability).let { availability ->
            "Disponibilidad activa, ${availability.labelSnapshot}, " +
                "${availability.eventTimeRange(EventTimeRangeStyle.COMPACT_DATED)}, " +
                "termina en ${formatRemaining(remaining)}"
        }
    NextEventPrimary.UPCOMING_AVAILABILITY ->
        (primaryEvents.first() as NextEventItem.Availability).let { availability ->
            "Próxima disponibilidad, ${availability.labelSnapshot}, " +
                "${availability.eventTimeRange(EventTimeRangeStyle.COMPACT_DATED)}, " +
                "comienza en ${formatRemaining(remaining)}"
        }
    NextEventPrimary.DAY_OFF -> "Próximo franco, ${nextDayOff?.format(ArgentineDateFormatter)}"
    NextEventPrimary.NONE -> "Sin próximos eventos"
}

private fun NextEventItem.eventTimeRange(style: EventTimeRangeStyle): String =
    formatEventTimeRange(start, end, zoneId, style)
