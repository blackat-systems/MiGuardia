package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.ui.fullDisplayName
import java.time.LocalDate
import java.util.regex.Pattern

/** Matches the first date segment emitted by CalendarDay.accessibilityDescription. */
internal object Stage12CalendarCellSelector {
    fun descriptionPattern(date: LocalDate): Pattern = Pattern.compile(
        "\\A" + Pattern.quote(date.fullDisplayName()) + "(?:, .*)?\\z",
        Pattern.DOTALL,
    )

    fun requireUnique(count: Int) {
        check(count == 1) { "Expected one owned calendar cell; matches=$count" }
    }
}
