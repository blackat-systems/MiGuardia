package com.blackatsystems.miguardia.stage12

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Literal V5 expectations. No production formatter, projector, duration getter or readback builds these values. */
internal object Stage12TemporalLiteralOracle {
    val zone: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
    data class Literal(
        val key: String, val owner: String, val localStart: String, val localEnd: String,
        val utcStart: String, val utcEnd: String, val minutes: Long, val civilDays: Long,
        val detail: String, val compact: String, val compactDated: String, val calendar: String,
        val reviewDuration: String,
    ) {
        val start: Instant get() = Instant.parse(utcStart)
        val end: Instant get() = Instant.parse(utcEnd)
        val ownerDate: LocalDate get() = LocalDate.parse(owner)
    }
    val literals: List<Literal> = listOf(
        Literal("C026-mismo-dia-minutos", "2026-10-23", "2026-10-23T08:15", "2026-10-23T16:45",
            "2026-10-23T11:15:00Z", "2026-10-23T19:45:00Z", 510, 0,
            "23/10/2026 08:15 – 23/10/2026 16:45", "08:15–16:45", "23/10/2026 · 08:15–16:45", "08:15–16:45", "8 h 30 min"),
        Literal("C026-nocturna", "2026-10-23", "2026-10-23T22:15", "2026-10-24T06:45",
            "2026-10-24T01:15:00Z", "2026-10-24T09:45:00Z", 510, 1,
            "23/10/2026 22:15 – 24/10/2026 06:45", "22:15–06:45 (+1 día)", "23/10/2026 · 22:15–06:45 (+1 día)", "22:15–06:45 +1", "8 h 30 min"),
        Literal("C026-24h", "2026-10-23", "2026-10-23T08:00", "2026-10-24T08:00",
            "2026-10-23T11:00:00Z", "2026-10-24T11:00:00Z", 1440, 1,
            "23/10/2026 08:00 – 24/10/2026 08:00", "08:00–08:00 (+1 día)", "23/10/2026 · 08:00–08:00 (+1 día)", "08:00–08:00 +1", "24 h 0 min"),
        Literal("C026-mes-ano", "2026-12-31", "2026-12-31T22:00", "2027-01-01T06:00",
            "2027-01-01T01:00:00Z", "2027-01-01T09:00:00Z", 480, 1,
            "31/12/2026 22:00 – 01/01/2027 06:00", "31/12/2026 22:00 – 01/01/2027 06:00", "31/12/2026 22:00 – 01/01/2027 06:00", "22:00–06:00 +1", "8 h 0 min"),
        Literal("C026-historicos", "2026-10-23", "2026-10-23T08:15", "2026-10-23T16:45",
            "2026-10-23T11:15:00Z", "2026-10-23T19:45:00Z", 510, 0,
            "23/10/2026 08:15 – 23/10/2026 16:45", "08:15–16:45", "23/10/2026 · 08:15–16:45", "08:15–16:45", "8 h 30 min"),
        Literal("A119-25h", "2026-10-23", "2026-10-23T08:00", "2026-10-24T09:00",
            "2026-10-23T11:00:00Z", "2026-10-24T12:00:00Z", 1500, 1,
            "23/10/2026 08:00 – 24/10/2026 09:00", "08:00–09:00 (+1 día)", "23/10/2026 · 08:00–09:00 (+1 día)", "08:00–09:00 +1", "25 h 0 min"),
        Literal("A119-varios-dias", "2026-10-23", "2026-10-23T08:00", "2026-10-26T09:00",
            "2026-10-23T11:00:00Z", "2026-10-26T12:00:00Z", 4380, 3,
            "23/10/2026 08:00 – 26/10/2026 09:00", "08:00–09:00 (+3 días)", "23/10/2026 · 08:00–09:00 (+3 días)", "08:00–09:00 +3", "73 h 0 min"),
        Literal("A119-fin-mes", "2026-10-31", "2026-10-31T22:00", "2026-11-01T06:00",
            "2026-11-01T01:00:00Z", "2026-11-01T09:00:00Z", 480, 1,
            "31/10/2026 22:00 – 01/11/2026 06:00", "31/10/2026 22:00 – 01/11/2026 06:00", "31/10/2026 22:00 – 01/11/2026 06:00", "22:00–06:00 +1", "8 h 0 min"),
        Literal("A119-fin-ano", "2026-12-31", "2026-12-31T22:00", "2027-01-01T06:00",
            "2027-01-01T01:00:00Z", "2027-01-01T09:00:00Z", 480, 1,
            "31/12/2026 22:00 – 01/01/2027 06:00", "31/12/2026 22:00 – 01/01/2027 06:00", "31/12/2026 22:00 – 01/01/2027 06:00", "22:00–06:00 +1", "8 h 0 min"),
        Literal("A119-segmento-reanudado-otra-fecha", "2026-10-23", "2026-10-23T08:00", "2026-10-24T12:00",
            "2026-10-23T11:00:00Z", "2026-10-24T15:00:00Z", 1680, 1,
            "23/10/2026 08:00 – 24/10/2026 12:00", "08:00–12:00 (+1 día)", "23/10/2026 · 08:00–12:00 (+1 día)", "08:00–12:00 +1", "28 h 0 min"),
    )
    val oneHourComparator = Literal("A119-25h-comparator-60m", "2026-10-23", "2026-10-23T08:00", "2026-10-23T09:00",
        "2026-10-23T11:00:00Z", "2026-10-23T12:00:00Z", 60, 0,
        "23/10/2026 08:00 – 23/10/2026 09:00", "08:00–09:00", "23/10/2026 · 08:00–09:00", "08–09", "1 h 0 min")
    val resumedActiveWork = Literal("A119-resumption-active-work", "2026-10-23", "2026-10-23T20:00", "2026-10-24T08:00",
        "2026-10-23T23:00:00Z", "2026-10-24T11:00:00Z", 720, 1,
        "23/10/2026 20:00 – 24/10/2026 08:00", "20:00–08:00 (+1 día)", "23/10/2026 · 20:00–08:00 (+1 día)", "20:00–08:00 +1", "12 h 0 min")
    data class Segment(val start: String, val end: String, val minutes: Long, val active: Boolean)
    val resumptionPartition = listOf(
        Segment("2026-10-23T11:00:00Z", "2026-10-23T23:00:00Z", 720, false),
        Segment("2026-10-23T23:00:00Z", "2026-10-24T11:00:00Z", 720, true),
        Segment("2026-10-24T11:00:00Z", "2026-10-24T15:00:00Z", 240, false),
    )
    const val resumptionDisplay = "24/10/2026 · 08:00–12:00"
    const val resumptionDetail = "24/10/2026 08:00 – 24/10/2026 12:00"
    const val resumptionOwner = "2026-10-23"
    const val resumptionVisibleDate = "2026-10-24"
    const val scheduledMinutes = 1680L
    const val replacedMinutes = 720L
    const val effectiveMinutes = 960L

    fun literal(key: String): Literal = literals.single { it.key == key }
    /** Validate the table using epoch arithmetic and independently stated civil offsets. */
    fun requireConsistent(literal: Literal) {
        val from = LocalDateTime.parse(literal.localStart)
        val to = LocalDateTime.parse(literal.localEnd)
        check(from.toLocalDate().toString() == literal.owner)
        check(literal.start.atZone(zone).toLocalDateTime() == from && literal.end.atZone(zone).toLocalDateTime() == to)
        check(literal.start.atZone(zone).offset.totalSeconds == -10800 && literal.end.atZone(zone).offset.totalSeconds == -10800)
        check(literal.end.toEpochMilli() - literal.start.toEpochMilli() == literal.minutes * 60000L)
        check(to.toLocalDate().toEpochDay() - from.toLocalDate().toEpochDay() == literal.civilDays)
        val independentMinutes = literal.civilDays * 1440L + to.hour * 60L + to.minute - from.hour * 60L - from.minute
        check(independentMinutes == literal.minutes && literal.minutes > 0)
    }
    fun requireEndpoints(literal: Literal, actualStart: Instant, actualEnd: Instant, storedZone: ZoneId,
        storedOwner: LocalDate) {
        check(actualStart == literal.start && actualEnd == literal.end && storedZone == zone && storedOwner == literal.ownerDate)
        check(actualEnd.toEpochMilli() - actualStart.toEpochMilli() == literal.minutes * 60000L)
    }
}
