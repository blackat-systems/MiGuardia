package com.blackatsystems.miguardia.stage12

/** Retains the three original checks in order, using only the layout returned by semantics. */
internal fun <T> requireTemporalTextLayout(
    layouts: List<T>,
    expected: String,
    text: (T) -> String,
    hasOverflow: (T) -> Boolean,
    overflowMetrics: (T) -> String,
): T {
    check(layouts.size == 1) { "TEMPORAL_TEXT_LAYOUT_COUNT_MISMATCH" }
    val layout = layouts.single()
    check(text(layout) == expected) { "TEMPORAL_TEXT_LAYOUT_VALUE_MISMATCH" }
    if (hasOverflow(layout)) {
        val primary = IllegalStateException("TEMPORAL_TEXT_LAYOUT_OVERFLOW")
        // Accessory measurements never replace the failed oracle.
        runCatching { primary.addSuppressed(IllegalStateException(overflowMetrics(layout))) }
        throw primary
    }
    return layout
}
