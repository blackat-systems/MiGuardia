package com.blackatsystems.miguardia.widget

import com.blackatsystems.miguardia.core.domain.widget.WidgetContentState
import com.blackatsystems.miguardia.core.domain.widget.WidgetEventKind
import com.blackatsystems.miguardia.core.domain.widget.WidgetEventPresentation
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.core.domain.widget.WidgetProjection
import com.blackatsystems.miguardia.ui.EventTimeRangeStyle
import com.blackatsystems.miguardia.ui.formatEventTimeRange
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class WidgetRenderText(
    val title: String,
    val primary: String,
    val schedule: String?,
    val rows: List<String>,
    val simultaneous: String?,
    val countdownFormat: String?,
)

internal data class WidgetPalette(
    val background: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val defaultAccent: Int,
    val action: Int,
)

internal fun buildWidgetRenderText(projection: WidgetProjection): WidgetRenderText {
    if (projection.privacy == WidgetPrivacy.HIDDEN &&
        projection.state != WidgetContentState.CONFIGURATION_INCOMPLETE
    ) {
        return WidgetRenderText(
            title = "MIGUARDIA",
            primary = "Tenés información en MiGuardia.",
            schedule = null,
            rows = emptyList(),
            simultaneous = null,
            countdownFormat = null,
        )
    }
    return when (projection.state) {
        WidgetContentState.CONFIGURATION_INCOMPLETE -> WidgetRenderText(
            title = "WIDGET DE INICIO",
            primary = "Configurá este widget para empezar.",
            schedule = null,
            rows = emptyList(),
            simultaneous = null,
            countdownFormat = null,
        )
        WidgetContentState.EMPTY -> WidgetRenderText(
            title = projection.mode.heading(),
            primary = when (projection.mode) {
                WidgetMode.NEXT_SHIFT -> "No hay próximas jornadas."
                WidgetMode.NEXT_DAY_OFF -> "No hay francos explícitos próximos."
                WidgetMode.AUTOMATIC -> "No hay próximos eventos."
            },
            schedule = null,
            rows = emptyList(),
            simultaneous = null,
            countdownFormat = null,
        )
        WidgetContentState.DAY_OFF -> WidgetRenderText(
            title = if (projection.privacy == WidgetPrivacy.COMPLETE) "PRÓXIMO FRANCO" else "PRÓXIMO EVENTO",
            primary = requireNotNull(projection.dayOff).format(DateFormatter),
            schedule = "Marcado explícitamente en MiGuardia",
            rows = emptyList(),
            simultaneous = null,
            countdownFormat = null,
        )
        WidgetContentState.EVENTS -> {
            val first = projection.events.first()
            val complete = projection.privacy == WidgetPrivacy.COMPLETE
            WidgetRenderText(
                title = if (complete) first.completeHeading() else first.genericHeading(),
                primary = if (complete) first.completePrimary() else "Evento en MiGuardia",
                schedule = first.schedule(),
                rows = projection.events.map { event ->
                    if (complete) event.completeRow() else event.schedule()
                },
                simultaneous = projection.totalSimultaneousEvents.takeIf { it > 1 }?.let { count ->
                    "$count eventos a la vez"
                },
                countdownFormat = projection.countdown?.let { countdown ->
                    if (countdown.countsToEnd) "Finaliza en %s" else "Comienza en %s"
                },
            )
        }
    }
}

internal fun resolveWidgetPalette(mode: AppThemeMode, systemDark: Boolean): WidgetPalette {
    val dark = mode.resolve(systemDark)
    return if (dark) {
        WidgetPalette(
            background = 0xFF15151E.toInt(),
            primaryText = 0xFFF2EFF8.toInt(),
            secondaryText = 0xFFB5AFC3.toInt(),
            defaultAccent = 0xFFB8A2F8.toInt(),
            action = 0xFFB8A2F8.toInt(),
        )
    } else {
        WidgetPalette(
            background = 0xFFF5F4F8.toInt(),
            primaryText = 0xFF292738.toInt(),
            secondaryText = 0xFF665F74.toInt(),
            defaultAccent = 0xFF6951B4.toInt(),
            action = 0xFF6951B4.toInt(),
        )
    }
}

private fun WidgetMode.heading(): String = when (this) {
    WidgetMode.NEXT_SHIFT -> "PRÓXIMA JORNADA"
    WidgetMode.NEXT_DAY_OFF -> "PRÓXIMO FRANCO"
    WidgetMode.AUTOMATIC -> "PRÓXIMO EVENTO"
}

private fun WidgetEventPresentation.completeHeading(): String = when (details?.kind) {
    WidgetEventKind.SHIFT -> if (isActive) "JORNADA EN CURSO" else "PRÓXIMA JORNADA"
    WidgetEventKind.AVAILABILITY -> if (isActive) "DISPONIBILIDAD ACTIVA" else "PRÓXIMA DISPONIBILIDAD"
    null -> genericHeading()
}

private fun WidgetEventPresentation.genericHeading(): String =
    if (isActive) "EVENTO EN CURSO" else "PRÓXIMO EVENTO"

private fun WidgetEventPresentation.completePrimary(): String = details?.let { detail ->
    when (detail.kind) {
        WidgetEventKind.SHIFT -> listOfNotNull(detail.workTypeName, detail.placeName)
            .filter(String::isNotBlank)
            .joinToString(" · ")
            .ifBlank { "Jornada" }
            .let { base ->
                buildString {
                    append(base)
                    detail.placeAbbreviation?.takeIf(String::isNotBlank)?.let { append(" ($it)") }
                    detail.position?.takeIf(String::isNotBlank)?.let { append(" · Puesto: $it") }
                }
            }
        WidgetEventKind.AVAILABILITY -> buildString {
            append(detail.availabilityLabel ?: "Disponibilidad")
            if (detail.isResumption) append(" · reanudada")
        }
    }
} ?: "Evento laboral"

private fun WidgetEventPresentation.completeRow(): String {
    val identity = details?.let { detail ->
        when (detail.kind) {
            WidgetEventKind.SHIFT -> detail.placeAbbreviation?.takeIf(String::isNotBlank)
                ?: detail.placeName
                ?: "Jornada"
            WidgetEventKind.AVAILABILITY -> detail.availabilityLabel ?: "Disponibilidad"
        }
    } ?: "Evento"
    val position = details?.position?.takeIf(String::isNotBlank)?.let { " · Puesto: $it" }.orEmpty()
    return "$identity · ${schedule()}$position"
}

private fun WidgetEventPresentation.schedule(): String =
    formatEventTimeRange(start, end, zoneId, EventTimeRangeStyle.COMPACT_DATED)

private val SpanishArgentina = Locale.forLanguageTag("es-AR")
private val DateFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SpanishArgentina)
