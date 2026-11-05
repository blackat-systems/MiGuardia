package com.blackatsystems.miguardia.stage12

import java.util.UUID

/** The ten V5 variants; a comparator is a fixture role, never an eleventh acceptance. */
internal enum class Stage12TemporalVariant(val wire: String, val key: String) {
    SAME_DAY("c026-minutes", "C026-mismo-dia-minutos"),
    OVERNIGHT("c026-overnight", "C026-nocturna"),
    TWENTY_FOUR_HOURS("c026-24h", "C026-24h"),
    YEAR_CROSSING_SHIFT("c026-year", "C026-mes-ano"),
    HISTORICAL("c026-historical", "C026-historicos"),
    TWENTY_FIVE_HOURS("a119-25h", "A119-25h"),
    SEVERAL_DAYS("a119-days", "A119-varios-dias"),
    MONTH_CROSSING("a119-month", "A119-fin-mes"),
    YEAR_CROSSING_WINDOW("a119-year", "A119-fin-ano"),
    RESUMPTION("a119-resumed", "A119-segmento-reanudado-otra-fecha");

    companion object {
        fun fromKey(key: String) = checkNotNull(entries.singleOrNull { it.key == key }) { "TEMPORAL_VARIANT_NOT_CLOSED" }
    }
}

internal enum class Stage12TemporalFixtureRole { PRIMARY, ONE_HOUR_COMPARATOR }

internal data class Stage12TemporalScope(
    val variant: Stage12TemporalVariant,
    val role: Stage12TemporalFixtureRole = Stage12TemporalFixtureRole.PRIMARY,
) {
    init {
        check(role == Stage12TemporalFixtureRole.PRIMARY || variant == Stage12TemporalVariant.TWENTY_FIVE_HOURS) {
            "TEMPORAL_COMPARATOR_OUTSIDE_25H"
        }
    }
    val wire: String get() = variant.wire + if (role == Stage12TemporalFixtureRole.ONE_HOUR_COMPARATOR) "-comparator60m" else ""
    val literal: Stage12TemporalLiteralOracle.Literal get() = when (role) {
        Stage12TemporalFixtureRole.PRIMARY -> Stage12TemporalLiteralOracle.literal(variant.key)
        Stage12TemporalFixtureRole.ONE_HOUR_COMPARATOR -> Stage12TemporalLiteralOracle.oneHourComparator
    }

    fun databaseName(run: String): String {
        check(UUID.fromString(run).toString() == run) { "TEMPORAL_RUN_NOT_CANONICAL" }
        return "stage12-temporal-$run-$wire.db"
    }
    fun privateRootName(run: String): String {
        databaseName(run)
        return "stage12-temporal-private-$run-$wire"
    }
}
