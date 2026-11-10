package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatus
import com.blackatsystems.miguardia.core.domain.model.ExplicitDayStatusType
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.model.ShiftWorkSnapshot
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventInput
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.core.domain.widget.WidgetProjection
import com.blackatsystems.miguardia.core.domain.widget.WidgetProjectionConfig
import com.blackatsystems.miguardia.core.domain.widget.WidgetSize
import com.blackatsystems.miguardia.core.domain.widget.projectWidget
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** Memory-only source. No database, preferences, service or file is opened here. */
internal class Stage12W010Fixture(val namespace: UUID) {
    enum class Boundary { BEFORE, ACTIVE_BEFORE_YEAR, ACTIVE_AFTER_YEAR, END_EXACT }
    val zone: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
    val ownerDate: LocalDate = LocalDate.of(2026, 12, 31)
    val dayOff: LocalDate = LocalDate.of(2027, 1, 2)
    val start: Instant = ownerDate.atTime(22, 17).atZone(zone).toInstant()
    val end: Instant = ownerDate.plusDays(1).atTime(6, 43).atZone(zone).toInstant()
    val weather = "PRIVATE_WEATHER_W010 · lluvia 91 % · 13–19 °C"
    val type = "PRIVATE_EVENT_TYPE_W010"
    val institution = "PRIVATE_INSTITUTION_W010"
    val abbreviation = "PRIVATE_ABBREVIATION_W010"
    val position = "PRIVATE_POSITION_W010"
    val address = "PRIVATE_ADDRESS_W010"
    val sourceCanary = "PRIVATE_SYNTHETIC_W010"
    val privateColor: Int = 0xFFCC0077.toInt()
    val shifts: List<V2ShiftWrite> = (1..9).map { number ->
        val id = identity("shift-$number")
        val objective = identity("objective-$number")
        V2ShiftWrite(Shift(id, start, end, zone, ownerDate, institution, abbreviation, address,
            LocalTime.of(22, 17), LocalTime.of(6, 43), privateColor, "$position $sourceCanary", ShiftStatus.PLANNED,
            objective, Instant.EPOCH, Instant.EPOCH),
            ShiftWorkSnapshot(id, identity("timeline"), WorkSector.PRIVATE_SECURITY, identity("revision"),
                identity("workplace-$number"), objective, identity("template-$number"), identity("type-$number"),
                type, WorkTypeBehavior.ACTIVE_WORK))
    }
    val input = NextEventInput(shifts, emptyList(), emptyMap(), emptyList(),
        listOf(ExplicitDayStatus(dayOff, ExplicitDayStatusType.DAY_OFF)), emptyList(), emptyList())
    val forbiddenText: Set<String> = setOf(type, institution, abbreviation, position, address, sourceCanary, weather,
        "PRIVATE_WEATHER_W010", "31/12", "01/01", "02/01", "2026", "2027", "22:17", "06:43",
        "9 eventos", "+6", "Comienza en", "Finaliza en", "Open-Meteo")

    fun identity(label: String): UUID = UUID.nameUUIDFromBytes("$namespace/w010/$label".toByteArray(Charsets.UTF_8))
    fun now(boundary: Boundary): Instant = when (boundary) {
        Boundary.BEFORE -> ownerDate.atTime(21, 59).atZone(zone).toInstant()
        Boundary.ACTIVE_BEFORE_YEAR -> ownerDate.atTime(23, 59).atZone(zone).toInstant()
        Boundary.ACTIVE_AFTER_YEAR -> ownerDate.plusDays(1).atTime(0, 1).atZone(zone).toInstant()
        Boundary.END_EXACT -> end
    }
    fun result(boundary: Boundary) = projectNextEvent(now(boundary), zone, input)
    fun projection(boundary: Boundary, mode: WidgetMode, size: WidgetSize,
        privacy: WidgetPrivacy = WidgetPrivacy.HIDDEN): WidgetProjection =
        projectWidget(result(boundary), WidgetProjectionConfig(mode, privacy, size, configured = true))

    fun requireAllSecretsPresent() {
        check(shifts.size == 9 && shifts.map { it.shift.id }.distinct().size == 9) { "W010_SOURCE_COUNT" }
        check(shifts.all { it.shift.localStartDate == ownerDate && it.shift.startAt == start && it.shift.endAt == end &&
            it.shift.endAt.atZone(zone).toLocalDate() == ownerDate.plusDays(1) &&
            it.snapshot.workTypeNameSnapshot == type && it.shift.objectiveNameSnapshot == institution &&
            it.shift.objectiveAbbreviationSnapshot == abbreviation && it.shift.objectiveAddressSnapshot == address &&
            it.shift.position == "$position $sourceCanary" && it.shift.colorArgbSnapshot == privateColor }) { "W010_SOURCE_SECRETS" }
        check(weather.contains("PRIVATE_WEATHER_W010") && start < end) { "W010_SOURCE_WEATHER" }
        for (boundary in Boundary.entries) {
            val source = result(boundary)
            check(source.events.size == if (boundary == Boundary.END_EXACT) 0 else 9) { "W010_REAL_SOURCE_ELIGIBILITY" }
        }
    }
}
