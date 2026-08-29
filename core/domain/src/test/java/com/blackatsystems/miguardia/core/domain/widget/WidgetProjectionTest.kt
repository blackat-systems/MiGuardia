package com.blackatsystems.miguardia.core.domain.widget

import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventItem
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventPrimary
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetProjectionTest {
    @Test
    fun `incomplete configuration is always hidden and only opens configuration`() {
        val projection = projectWidget(result(), WidgetProjectionConfig(configured = false))

        assertEquals(WidgetContentState.CONFIGURATION_INCOMPLETE, projection.state)
        assertEquals(WidgetPrivacy.HIDDEN, projection.privacy)
        assertTrue(projection.events.isEmpty())
        assertNull(projection.dayOff)
        assertNull(projection.countdown)
        assertEquals(WidgetNavigation.Configure, projection.navigation)
    }

    @Test
    fun `next shift excludes active work and groups the first simultaneous future shifts`() {
        val active = shift(1, NOW.minusSeconds(600), NOW.plusSeconds(600))
        val first = shift(2, NOW.plusSeconds(1200), NOW.plusSeconds(4800))
        val simultaneous = shift(3, first.start, first.end.plusSeconds(600))
        val later = shift(4, NOW.plusSeconds(1800), NOW.plusSeconds(5400))
        val projection = projectWidget(
            result(
                events = listOf(active, first, simultaneous, later),
                active = listOf(active),
                upcoming = listOf(first, simultaneous),
                primary = listOf(active),
                primaryKind = NextEventPrimary.ONGOING_SHIFT,
            ),
            config(WidgetMode.NEXT_SHIFT, size = WidgetSize.EXPANDED),
        )

        assertEquals(listOf(first.identity, simultaneous.identity), projection.events.map { it.identity })
        assertEquals(2, projection.totalSimultaneousEvents)
        assertFalse(projection.events.any { it.identity == active.identity })
        assertEquals(first.start, projection.countdown?.target)
        assertFalse(requireNotNull(projection.countdown).countsToEnd)
    }

    @Test
    fun `expanded keeps at most three deterministic simultaneous rows while compact keeps one`() {
        val future = (1..4).map { shift(it, NOW.plusSeconds(600), NOW.plusSeconds(3600L + it)) }
        val source = result(
            events = future,
            upcoming = future,
            primary = future,
            primaryKind = NextEventPrimary.UPCOMING_SHIFT,
        )

        val compact = projectWidget(source, config(WidgetMode.AUTOMATIC, WidgetSize.COMPACT))
        val expanded = projectWidget(source, config(WidgetMode.AUTOMATIC, WidgetSize.EXPANDED))

        assertEquals(listOf(future.first().identity), compact.events.map { it.identity })
        assertEquals(future.take(3).map { it.identity }, expanded.events.map { it.identity })
        assertEquals(4, compact.totalSimultaneousEvents)
        assertEquals(4, expanded.totalSimultaneousEvents)
    }

    @Test
    fun `projected event rows cannot be mutated by a consumer`() {
        val future = shift(1, NOW.plusSeconds(600), NOW.plusSeconds(3_600))
        val projection = projectWidget(
            result(
                events = listOf(future),
                upcoming = listOf(future),
                primary = listOf(future),
                primaryKind = NextEventPrimary.UPCOMING_SHIFT,
            ),
            config(WidgetMode.AUTOMATIC),
        )

        @Suppress("UNCHECKED_CAST")
        val exposed = projection.events as MutableList<WidgetEventPresentation>
        assertThrows(UnsupportedOperationException::class.java) { exposed.clear() }
        assertEquals(listOf(future.identity), projection.events.map { it.identity })
    }

    @Test
    fun `next day off uses only the explicit result and an empty result stays empty`() {
        val dayOff = TODAY.plusDays(2)
        val available = projectWidget(
            result(dayOff = dayOff, primaryKind = NextEventPrimary.DAY_OFF),
            config(WidgetMode.NEXT_DAY_OFF),
        )
        val absent = projectWidget(result(), config(WidgetMode.NEXT_DAY_OFF))

        assertEquals(WidgetContentState.DAY_OFF, available.state)
        assertEquals(dayOff, available.dayOff)
        assertEquals(WidgetNavigation.Date(dayOff), available.navigation)
        assertNull(available.countdown)
        assertEquals(WidgetContentState.EMPTY, absent.state)
        assertNull(absent.dayOff)
    }

    @Test
    fun `automatic consumes primary events in their complete shared order`() {
        val availability = availability(1, NOW.minusSeconds(300), NOW.plusSeconds(900), resumed = true)
        val second = availability(2, availability.start, availability.end.plusSeconds(60))
        val source = result(
            events = listOf(availability, second),
            active = listOf(availability, second),
            primary = listOf(availability, second),
            primaryKind = NextEventPrimary.ONGOING_AVAILABILITY,
        )

        val projection = projectWidget(source, config(WidgetMode.AUTOMATIC, WidgetSize.EXPANDED))

        assertEquals(source.primaryEvents.map { it.identity }, projection.events.map { it.identity })
        assertEquals(WidgetEventKind.AVAILABILITY, projection.events.first().details?.kind)
        assertTrue(requireNotNull(projection.events.first().details).isResumption)
        assertEquals(availability.end, projection.countdown?.target)
        assertTrue(requireNotNull(projection.countdown).countsToEnd)
    }

    @Test
    fun `automatic keeps active shifts that started at different times in shared order`() {
        val first = shift(1, NOW.minusSeconds(900), NOW.plusSeconds(900))
        val second = shift(2, NOW.minusSeconds(300), NOW.plusSeconds(1_200))
        val source = result(
            events = listOf(first, second),
            active = listOf(first, second),
            primary = listOf(first, second),
            primaryKind = NextEventPrimary.ONGOING_SHIFT,
        )

        val projection = projectWidget(source, config(WidgetMode.AUTOMATIC, WidgetSize.EXPANDED))

        assertEquals(source.primaryEvents.map { it.identity }, projection.events.map { it.identity })
        assertEquals(first.end, projection.countdown?.target)
    }

    @Test
    fun `automatic keeps a future availability distinct and counts to its start`() {
        val future = availability(3, NOW.plusSeconds(600), NOW.plusSeconds(2_400))
        val source = result(
            events = listOf(future),
            upcoming = listOf(future),
            primary = listOf(future),
            primaryKind = NextEventPrimary.UPCOMING_AVAILABILITY,
        )

        val projection = projectWidget(source, config(WidgetMode.AUTOMATIC))

        assertEquals(WidgetEventKind.AVAILABILITY, projection.events.single().details?.kind)
        assertFalse(projection.events.single().isActive)
        assertEquals(future.start, projection.countdown?.target)
        assertFalse(requireNotNull(projection.countdown).countsToEnd)
    }

    @Test
    fun `active countdown is allowed only in automatic mode`() {
        val active = shift(1, NOW.minusSeconds(300), NOW.plusSeconds(900))
        val source = result(
            events = listOf(active),
            active = listOf(active),
            primary = listOf(active),
            primaryKind = NextEventPrimary.ONGOING_SHIFT,
        )

        assertEquals(
            active.end,
            projectWidget(source, config(WidgetMode.AUTOMATIC)).countdown?.target,
        )
        assertNull(projectWidget(source, config(WidgetMode.NEXT_SHIFT)).countdown)
        assertNull(projectWidget(source, config(WidgetMode.NEXT_DAY_OFF)).countdown)
    }

    @Test
    fun `privacy complete reduced and hidden progressively remove labor detail`() {
        val event = shift(1, NOW.plusSeconds(600), NOW.plusSeconds(4200))
        val source = result(
            events = listOf(event),
            upcoming = listOf(event),
            primary = listOf(event),
            primaryKind = NextEventPrimary.UPCOMING_SHIFT,
        )

        val complete = projectWidget(source, config(privacy = WidgetPrivacy.COMPLETE))
        val reduced = projectWidget(source, config(privacy = WidgetPrivacy.REDUCED))
        val hidden = projectWidget(source, config(privacy = WidgetPrivacy.HIDDEN))

        assertEquals("Lugar ficticio", complete.events.single().details?.placeName)
        assertEquals(COLOR, complete.events.single().details?.colorArgb)
        assertNull(reduced.events.single().details)
        assertEquals(event.start, reduced.events.single().start)
        assertTrue(hidden.events.isEmpty())
        assertEquals(0, hidden.totalSimultaneousEvents)
        assertNull(hidden.countdown)
        assertEquals(WidgetNavigation.Shift(event.shiftId, event.ownerLocalDate), hidden.navigation)
    }

    @Test
    fun `hidden day off does not expose its date but keeps typed navigation`() {
        val date = TODAY.plusDays(1)
        val projection = projectWidget(
            result(dayOff = date, primaryKind = NextEventPrimary.DAY_OFF),
            config(mode = WidgetMode.AUTOMATIC, privacy = WidgetPrivacy.HIDDEN),
        )

        assertEquals(WidgetContentState.DAY_OFF, projection.state)
        assertNull(projection.dayOff)
        assertEquals(WidgetNavigation.Date(date), projection.navigation)
    }

    @Test
    fun `each mode has an intentional empty state and opens calendar`() {
        WidgetMode.entries.forEach { mode ->
            val projection = projectWidget(result(), config(mode))
            assertEquals(mode, projection.mode)
            assertEquals(WidgetContentState.EMPTY, projection.state)
            assertEquals(WidgetNavigation.Calendar, projection.navigation)
        }
    }

    @Test
    fun `one earliest boundary covers several widget modes`() {
        val future = shift(1, NOW.plusSeconds(600), NOW.plusSeconds(4200))
        val active = availability(2, NOW.minusSeconds(60), NOW.plusSeconds(300))
        val source = result(
            events = listOf(active, future),
            active = listOf(active),
            upcoming = listOf(future),
            primary = listOf(active),
            primaryKind = NextEventPrimary.ONGOING_AVAILABILITY,
        )

        assertEquals(
            active.end,
            nextWidgetBoundary(source, listOf(WidgetMode.NEXT_SHIFT, WidgetMode.AUTOMATIC)),
        )
        assertEquals(
            future.start,
            nextWidgetBoundary(source, listOf(WidgetMode.NEXT_SHIFT)),
        )
    }

    @Test
    fun `automatic boundary drops the first active event that ends even when it is not first`() {
        val firstInStableOrder = shift(1, NOW.minusSeconds(600), NOW.plusSeconds(1_200))
        val endsEarlier = shift(2, NOW.minusSeconds(300), NOW.plusSeconds(300))
        val source = result(
            events = listOf(firstInStableOrder, endsEarlier),
            active = listOf(firstInStableOrder, endsEarlier),
            primary = listOf(firstInStableOrder, endsEarlier),
            primaryKind = NextEventPrimary.ONGOING_SHIFT,
        )

        assertEquals(endsEarlier.end, nextWidgetBoundary(source, listOf(WidgetMode.AUTOMATIC)))
    }

    @Test
    fun `midnight is the fallback boundary for day off and empty states`() {
        val expected = TODAY.plusDays(1).atStartOfDay(ZONE).toInstant()

        assertEquals(expected, nextWidgetBoundary(result(), WidgetMode.entries))
        assertEquals(expected, nextWidgetBoundary(result(), emptyList()))
    }

    @Test
    fun `chronometer base is monotonic and a late boundary is hidden`() {
        assertEquals(16_000L, widgetChronometerBase(10_000L, NOW, NOW.plusSeconds(6)))
        assertNull(widgetChronometerBase(10_000L, NOW, NOW))
        assertNull(widgetChronometerBase(10_000L, NOW, NOW.minusSeconds(6)))
    }

    private fun config(
        mode: WidgetMode = WidgetMode.AUTOMATIC,
        size: WidgetSize = WidgetSize.COMPACT,
        privacy: WidgetPrivacy = WidgetPrivacy.COMPLETE,
    ) = WidgetProjectionConfig(mode, privacy, size, configured = true)

    private fun result(
        events: List<NextEventItem> = emptyList(),
        active: List<NextEventItem> = emptyList(),
        upcoming: List<NextEventItem> = emptyList(),
        primary: List<NextEventItem> = emptyList(),
        dayOff: LocalDate? = null,
        primaryKind: NextEventPrimary = NextEventPrimary.NONE,
    ) = NextEventResult.create(
        referenceInstant = NOW,
        zoneId = ZONE,
        events = events,
        activeEvents = active,
        upcomingEvents = upcoming,
        primaryEvents = primary,
        nextDayOff = dayOff,
        primaryEvent = primaryKind,
        remaining = when {
            primary.isEmpty() -> Duration.ZERO
            primary.first().start <= NOW -> Duration.between(NOW, primary.first().end)
            else -> Duration.between(NOW, primary.first().start)
        },
    )

    private fun shift(id: Int, start: Instant, end: Instant) = NextEventItem.Shift(
        shiftId = uuid(id),
        start = start,
        end = end,
        zoneId = ZONE,
        ownerLocalDate = start.atZone(ZONE).toLocalDate(),
        sector = WorkSector.PRIVATE_SECURITY,
        workTypeNameSnapshot = "Jornada ficticia",
        workTypeBehaviorSnapshot = WorkTypeBehavior.ACTIVE_WORK,
        placeNameSnapshot = "Lugar ficticio",
        placeAbbreviationSnapshot = "LF",
        startTimeSnapshot = start.atZone(ZONE).toLocalTime(),
        endTimeSnapshot = end.atZone(ZONE).toLocalTime(),
        colorArgbSnapshot = COLOR,
        positionSnapshot = "Acceso ficticio",
        hasHistoricalAddress = false,
    )

    private fun availability(id: Int, start: Instant, end: Instant, resumed: Boolean = false) =
        NextEventItem.Availability(
            windowId = uuid(id),
            start = start,
            end = end,
            zoneId = ZONE,
            ownerLocalDate = start.atZone(ZONE).toLocalDate(),
            labelSnapshot = "Disponibilidad ficticia",
            isResumption = resumed,
        )

    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Cordoba")
        val NOW: Instant = Instant.parse("2026-08-29T15:00:00Z")
        val TODAY: LocalDate = NOW.atZone(ZONE).toLocalDate()
        const val COLOR: Int = 0xFF315DA8.toInt()
        fun uuid(number: Int): UUID = UUID.fromString(
            "92000000-0000-0000-0000-${number.toString().padStart(12, '0')}",
        )
    }
}
