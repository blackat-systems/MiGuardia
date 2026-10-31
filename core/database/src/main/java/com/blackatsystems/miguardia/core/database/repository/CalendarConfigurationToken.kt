package com.blackatsystems.miguardia.core.database.repository

import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory

/**
 * History contains non-data containers (EffectiveDateTimeline and PerPeriodHoursValues).
 * Their inherited toString includes object identity, which changes on every Room read.
 * Include their complete persisted value instead: no configuration field is omitted from CAS.
 */
internal fun calendarConfigurationToken(history: WorkConfigurationHistory?): String =
    if (history == null) "unconfigured" else buildString {
        append("timeline=").append(history.timeline.id)
        append(";revisions=").append(history.timeline.revisions)
        append(";periodValues=").append(history.perPeriodHoursValues.entries)
    }
