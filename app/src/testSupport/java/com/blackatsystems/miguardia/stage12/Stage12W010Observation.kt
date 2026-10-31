package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetSize

/** Diagnostics only. The existing oracle alone decides whether a value is rejected. */
internal object Stage12W010Observation {
    enum class Field {
        VIEW_DESCRIPTION, VIEW_STATE, VIEW_TOOLTIP, VIEW_PANE,
        NODE_TEXT, NODE_DESCRIPTION, NODE_STATE, NODE_HINT, NODE_ERROR, NODE_TOOLTIP, NODE_PANE,
        TEXT, HINT, ERROR,
    }
    enum class Node { ROOT, TITLE, PRIMARY, SCHEDULE, COUNTDOWN, SIMULTANEOUS, ROW_1, ROW_2, ROW_3,
        WEATHER_AREA, WEATHER, WEATHER_CREDIT, CONFIGURE, OTHER }
    enum class Kind { CHRONOMETER, TEXT, IMAGE, GROUP, OTHER }
    enum class Visibility { VISIBLE, INVISIBLE, GONE, UNKNOWN }
    data class Variant(val dark: Boolean, val size: WidgetSize, val mode: WidgetMode,
        val boundary: Stage12W010Fixture.Boundary)
    data class Timer(val baseZero: Boolean, val formatNull: Boolean, val countDown: Boolean, val textEmpty: Boolean)

    fun failure(value: String, forbidden: Set<String>, variant: Variant, field: Field, node: Node,
        kind: Kind, visibility: Visibility, effectivelyVisible: Boolean, nodesVisited: Int, timer: Timer?): IllegalStateException {
        require(nodesVisited in 1..128)
        // Only enums, bounded counts and booleans leave this function. Never the value,
        // matching literal, resource string/name, hash, timer base, path or complete tree.
        val code = buildString {
            append("W010_APPLIED_SECRET_TEXT;phase=HIDDEN_REAPPLIED")
            append(";theme=").append(if (variant.dark) "DARK" else "LIGHT")
            append(";size=").append(variant.size.name).append(";mode=").append(variant.mode.name)
            append(";boundary=").append(variant.boundary.name)
            append(";field=").append(field.name).append(";node=").append(node.name).append(";kind=").append(kind.name)
            append(";visibility=").append(visibility.name).append(";effectively_visible=").append(effectivelyVisible)
            append(";nodes_visited=").append(nodesVisited)
            append(";has_digit=").append(value.any(Char::isDigit))
            append(";matches_forbidden_literal=").append(forbidden.any { value.contains(it, ignoreCase = true) })
            if (timer != null) {
                append(";timer_base_zero=").append(timer.baseZero).append(";timer_format_null=").append(timer.formatNull)
                append(";timer_count_down=").append(timer.countDown).append(";timer_text_empty=").append(timer.textEmpty)
            }
        }
        return IllegalStateException(code)
    }
}
