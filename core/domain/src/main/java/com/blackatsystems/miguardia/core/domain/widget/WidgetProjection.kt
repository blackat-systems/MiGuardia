package com.blackatsystems.miguardia.core.domain.widget

import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections

enum class WidgetMode {
    NEXT_SHIFT,
    NEXT_DAY_OFF,
    AUTOMATIC,
}

enum class WidgetPrivacy {
    COMPLETE,
    REDUCED,
    HIDDEN,
}

enum class WidgetSize {
    COMPACT,
    EXPANDED,
}

enum class WidgetContentState {
    CONFIGURATION_INCOMPLETE,
    EVENTS,
    DAY_OFF,
    EMPTY,
}

enum class WidgetEventKind {
    SHIFT,
    AVAILABILITY,
}

data class WidgetProjectionConfig(
    val mode: WidgetMode = WidgetMode.AUTOMATIC,
    val privacy: WidgetPrivacy = WidgetPrivacy.HIDDEN,
    val size: WidgetSize = WidgetSize.COMPACT,
    val configured: Boolean = false,
)

sealed interface WidgetNavigation {
    data class Shift(val shiftId: java.util.UUID, val ownerLocalDate: LocalDate) : WidgetNavigation
    data class Date(val ownerLocalDate: LocalDate) : WidgetNavigation
    data object Calendar : WidgetNavigation
    data object Configure : WidgetNavigation
}

data class WidgetEventDetails(
    val kind: WidgetEventKind,
    val workTypeName: String?,
    val placeName: String?,
    val placeAbbreviation: String?,
    val position: String?,
    val availabilityLabel: String?,
    val isResumption: Boolean,
    val colorArgb: Int?,
)

data class WidgetEventPresentation(
    val identity: NextEventIdentity,
    val ownerLocalDate: LocalDate,
    val zoneId: ZoneId,
    val start: Instant,
    val end: Instant,
    val isActive: Boolean,
    val details: WidgetEventDetails?,
    val navigation: WidgetNavigation,
)

data class WidgetCountdown(
    val target: Instant,
    val countsToEnd: Boolean,
)

data class WidgetProjection(
    val referenceInstant: Instant,
    val mode: WidgetMode,
    val privacy: WidgetPrivacy,
    val size: WidgetSize,
    val state: WidgetContentState,
    val events: List<WidgetEventPresentation>,
    val totalSimultaneousEvents: Int,
    val dayOff: LocalDate?,
    val countdown: WidgetCountdown?,
    val navigation: WidgetNavigation,
)

/**
 * Adapts the shared V2 event result for a home-screen widget. It never
 * recalculates eligibility or priority; it only applies the selected widget
 * mode, size and privacy.
 */
fun projectWidget(
    result: NextEventResult,
    config: WidgetProjectionConfig,
): WidgetProjection {
    if (!config.configured) {
        return WidgetProjection(
            referenceInstant = result.referenceInstant,
            mode = config.mode,
            privacy = WidgetPrivacy.HIDDEN,
            size = config.size,
            state = WidgetContentState.CONFIGURATION_INCOMPLETE,
            events = emptyList(),
            totalSimultaneousEvents = 0,
            dayOff = null,
            countdown = null,
            navigation = WidgetNavigation.Configure,
        )
    }

    val selected = when (config.mode) {
        WidgetMode.NEXT_SHIFT -> selectNextShifts(result)
        WidgetMode.NEXT_DAY_OFF -> emptyList()
        WidgetMode.AUTOMATIC -> result.primaryEvents
    }
    val dayOff = when (config.mode) {
        WidgetMode.NEXT_DAY_OFF -> result.nextDayOff
        WidgetMode.AUTOMATIC -> result.nextDayOff.takeIf {
            result.primaryEvent == NextEventPrimary.DAY_OFF && selected.isEmpty()
        }
        WidgetMode.NEXT_SHIFT -> null
    }

    if (selected.isNotEmpty()) {
        val first = selected.first()
        val navigation = first.toNavigation()
        if (config.privacy == WidgetPrivacy.HIDDEN) {
            return WidgetProjection(
                referenceInstant = result.referenceInstant,
                mode = config.mode,
                privacy = config.privacy,
                size = config.size,
                state = WidgetContentState.EVENTS,
                events = emptyList(),
                totalSimultaneousEvents = 0,
                dayOff = null,
                countdown = null,
                navigation = navigation,
            )
        }
        val limit = if (config.size == WidgetSize.COMPACT) 1 else 3
        return WidgetProjection(
            referenceInstant = result.referenceInstant,
            mode = config.mode,
            privacy = config.privacy,
            size = config.size,
            state = WidgetContentState.EVENTS,
            events = Collections.unmodifiableList(
                selected.take(limit).map { event ->
                    event.toPresentation(result.referenceInstant, config.privacy)
                },
            ),
            totalSimultaneousEvents = selected.size,
            dayOff = null,
            countdown = selected.countdown(result.referenceInstant, config.mode),
            navigation = navigation,
        )
    }

    if (dayOff != null) {
        return WidgetProjection(
            referenceInstant = result.referenceInstant,
            mode = config.mode,
            privacy = config.privacy,
            size = config.size,
            state = WidgetContentState.DAY_OFF,
            events = emptyList(),
            totalSimultaneousEvents = 0,
            dayOff = dayOff.takeUnless { config.privacy == WidgetPrivacy.HIDDEN },
            countdown = null,
            navigation = WidgetNavigation.Date(dayOff),
        )
    }

    return WidgetProjection(
        referenceInstant = result.referenceInstant,
        mode = config.mode,
        privacy = config.privacy,
        size = config.size,
        state = WidgetContentState.EMPTY,
        events = emptyList(),
        totalSimultaneousEvents = 0,
        dayOff = null,
        countdown = null,
        navigation = WidgetNavigation.Calendar,
    )
}

/** Returns the earliest refresh boundary needed by all configured instances. */
fun nextWidgetBoundary(
    result: NextEventResult,
    modes: Collection<WidgetMode>,
): Instant {
    val now = result.referenceInstant
    val nextMidnight = now.atZone(result.zoneId).toLocalDate().plusDays(1)
        .atStartOfDay(result.zoneId)
        .toInstant()
    return buildList {
        add(nextMidnight)
        modes.distinct().forEach { mode ->
            when (mode) {
                WidgetMode.NEXT_SHIFT -> selectNextShifts(result).firstOrNull()?.start?.let(::add)
                WidgetMode.NEXT_DAY_OFF -> Unit
                WidgetMode.AUTOMATIC -> {
                    val activeEnds = result.primaryEvents
                        .filter { event -> event.start <= now && now < event.end }
                        .map(NextEventItem::end)
                    if (activeEnds.isNotEmpty()) {
                        add(requireNotNull(activeEnds.minOrNull()))
                    } else {
                        result.primaryEvents.firstOrNull()?.start?.let(::add)
                    }
                }
            }
        }
    }.filter { it > now }.minOrNull() ?: nextMidnight
}

/** Converts an epoch target to the monotonic base required by Chronometer. */
fun widgetChronometerBase(
    elapsedRealtime: Long,
    now: Instant,
    target: Instant,
): Long? {
    val remainingMillis = Duration.between(now, target).toMillis()
    return if (remainingMillis > 0L) elapsedRealtime + remainingMillis else null
}

private fun selectNextShifts(result: NextEventResult): List<NextEventItem.Shift> {
    val future = result.events.filterIsInstance<NextEventItem.Shift>()
        .filter { event -> event.start > result.referenceInstant }
    val firstStart = future.minOfOrNull(NextEventItem.Shift::start) ?: return emptyList()
    return future.filter { event -> event.start == firstStart }
}

private fun NextEventItem.toNavigation(): WidgetNavigation = when (this) {
    is NextEventItem.Shift -> WidgetNavigation.Shift(shiftId, ownerLocalDate)
    is NextEventItem.Availability -> WidgetNavigation.Date(ownerLocalDate)
}

private fun NextEventItem.toPresentation(
    now: Instant,
    privacy: WidgetPrivacy,
): WidgetEventPresentation = WidgetEventPresentation(
    identity = identity,
    ownerLocalDate = ownerLocalDate,
    zoneId = zoneId,
    start = start,
    end = end,
    isActive = start <= now && now < end,
    details = when (privacy) {
        WidgetPrivacy.COMPLETE -> when (this) {
            is NextEventItem.Shift -> WidgetEventDetails(
                kind = WidgetEventKind.SHIFT,
                workTypeName = workTypeNameSnapshot,
                placeName = placeNameSnapshot,
                placeAbbreviation = placeAbbreviationSnapshot,
                position = positionSnapshot,
                availabilityLabel = null,
                isResumption = false,
                colorArgb = colorArgbSnapshot,
            )
            is NextEventItem.Availability -> WidgetEventDetails(
                kind = WidgetEventKind.AVAILABILITY,
                workTypeName = null,
                placeName = null,
                placeAbbreviation = null,
                position = null,
                availabilityLabel = labelSnapshot,
                isResumption = isResumption,
                colorArgb = null,
            )
        }
        WidgetPrivacy.REDUCED,
        WidgetPrivacy.HIDDEN,
        -> null
    },
    navigation = toNavigation(),
)

private fun List<NextEventItem>.countdown(now: Instant, mode: WidgetMode): WidgetCountdown? {
    val first = firstOrNull() ?: return null
    val active = first.start <= now && now < first.end
    return when {
        mode == WidgetMode.AUTOMATIC && active -> WidgetCountdown(first.end, countsToEnd = true)
        !active -> WidgetCountdown(first.start, countsToEnd = false)
        else -> null
    }
}
