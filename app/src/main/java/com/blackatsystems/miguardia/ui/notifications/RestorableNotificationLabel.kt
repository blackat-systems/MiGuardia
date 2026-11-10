package com.blackatsystems.miguardia.ui.notifications

import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.ui.EventTimeRangeStyle
import com.blackatsystems.miguardia.ui.formatEventTimeRange

internal fun NextEventItem.restorableLabel(): String = when (this) {
    is NextEventItem.Shift ->
        "$placeAbbreviationSnapshot · " +
            formatEventTimeRange(start, end, zoneId, EventTimeRangeStyle.COMPACT_DATED)
    is NextEventItem.Availability ->
        "$labelSnapshot · " +
            formatEventTimeRange(start, end, zoneId, EventTimeRangeStyle.COMPACT_DATED)
}
