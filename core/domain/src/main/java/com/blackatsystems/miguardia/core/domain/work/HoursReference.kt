package com.blackatsystems.miguardia.core.domain.work

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.UUID

data class DateWindow(
    val startInclusive: LocalDate,
    val endExclusive: LocalDate,
) {
    init {
        require(endExclusive.isAfter(startInclusive)) {
            "El final del período debe ser posterior a su inicio"
        }
    }

    operator fun contains(date: LocalDate): Boolean =
        !date.isBefore(startInclusive) && date.isBefore(endExclusive)
}

sealed interface HoursPeriod {
    fun windowContaining(date: LocalDate): DateWindow

    data object Monthly : HoursPeriod {
        override fun windowContaining(date: LocalDate): DateWindow {
            val start = YearMonth.from(date).atDay(1)
            return DateWindow(startInclusive = start, endExclusive = start.plusMonths(1))
        }
    }

    data class Weekly(
        val firstDay: DayOfWeek,
    ) : HoursPeriod {
        override fun windowContaining(date: LocalDate): DateWindow {
            val daysFromStart = Math.floorMod(date.dayOfWeek.value - firstDay.value, DAYS_IN_WEEK)
            val start = date.minusDays(daysFromStart.toLong())
            return DateWindow(startInclusive = start, endExclusive = start.plusDays(DAYS_IN_WEEK.toLong()))
        }

        companion object {
            val suggestedFirstDay: DayOfWeek = DayOfWeek.MONDAY
        }
    }

    data class Cycle(
        val anchorDate: LocalDate,
        val lengthDays: Int,
    ) : HoursPeriod {
        init {
            require(lengthDays > 0) { "La cantidad de días del ciclo debe ser positiva" }
        }

        override fun windowContaining(date: LocalDate): DateWindow {
            val daysFromAnchor = ChronoUnit.DAYS.between(anchorDate, date)
            val cycleIndex = Math.floorDiv(daysFromAnchor, lengthDays.toLong())
            val start = anchorDate.plusDays(cycleIndex * lengthDays)
            return DateWindow(startInclusive = start, endExclusive = start.plusDays(lengthDays.toLong()))
        }
    }

    private companion object {
        const val DAYS_IN_WEEK: Int = 7
    }
}

data class PositiveMinutes(
    val value: Long,
) {
    init {
        require(value > 0L) { "La cantidad de minutos debe ser positiva" }
        require(value <= MAX_DURATION_MINUTES) {
            "La cantidad de minutos excede el máximo representable"
        }
    }

    fun toDuration(): Duration = Duration.ofMinutes(value)

    companion object {
        fun from(duration: Duration): PositiveMinutes {
            require(!duration.isNegative && !duration.isZero) {
                "La duración debe ser positiva"
            }
            require(duration.nano == 0 && duration.seconds % SECONDS_PER_MINUTE == 0L) {
                "La duración debe expresarse en minutos enteros"
            }
            return PositiveMinutes(duration.toMinutes())
        }

        private const val SECONDS_PER_MINUTE: Long = 60L
        private const val MAX_DURATION_MINUTES: Long = Long.MAX_VALUE / SECONDS_PER_MINUTE
    }
}

sealed interface HoursReference {
    data object NotUsed : HoursReference

    data class Unknown(
        val period: HoursPeriod? = null,
    ) : HoursReference

    data class Fixed(
        val period: HoursPeriod,
        val requiredMinutes: PositiveMinutes,
    ) : HoursReference

    data class PerPeriod(
        val definitionId: UUID,
        val period: HoursPeriod,
    ) : HoursReference {
        fun keyFor(window: DateWindow): PerPeriodHoursKey = PerPeriodHoursKey(
            definitionId = definitionId,
            period = period,
            window = window,
        )

        fun keyContaining(date: LocalDate): PerPeriodHoursKey = keyFor(period.windowContaining(date))
    }
}

data class PerPeriodHoursKey(
    val definitionId: UUID,
    val period: HoursPeriod,
    val window: DateWindow,
) {
    init {
        require(period.windowContaining(window.startInclusive) == window) {
            "La ventana debe corresponder a la definición de su período"
        }
    }
}

data class PerPeriodHoursEntry(
    val id: UUID,
    val key: PerPeriodHoursKey,
    val requiredMinutes: PositiveMinutes,
)

sealed interface PerPeriodHoursLookup {
    data object Missing : PerPeriodHoursLookup

    data class Defined(
        val entry: PerPeriodHoursEntry,
    ) : PerPeriodHoursLookup
}

class PerPeriodHoursValues(
    entries: Iterable<PerPeriodHoursEntry>,
) {
    val entries: List<PerPeriodHoursEntry> = Collections.unmodifiableList(
        entries
            .toList()
            .sortedWith(compareBy<PerPeriodHoursEntry> { it.key.window.startInclusive }
                .thenBy { it.key.window.endExclusive }
                .thenBy { it.id })
            .also { ordered ->
                require(ordered.map { it.id }.distinct().size == ordered.size) {
                    "No puede haber dos valores por período con el mismo identificador"
                }
                require(ordered.map { it.key.logicalIdentity() }.distinct().size == ordered.size) {
                    "No puede haber dos valores para la misma definición y ventana"
                }
                require(ordered
                    .groupBy { it.key.definitionId }
                    .values
                    .all { sameDefinition -> sameDefinition.map { it.key.period }.distinct().size == 1 }) {
                    "Una definición por período no puede cambiar su patrón"
                }
            },
    )

    private val periodByDefinitionId: Map<UUID, HoursPeriod> = this.entries
        .associate { entry -> entry.key.definitionId to entry.key.period }

    private val entriesByIdentity: Map<PerPeriodHoursIdentity, PerPeriodHoursEntry> = this.entries
        .associateBy { entry -> entry.key.logicalIdentity() }

    fun valueFor(key: PerPeriodHoursKey): PerPeriodHoursLookup {
        val canonicalPeriod = periodByDefinitionId[key.definitionId] ?: return PerPeriodHoursLookup.Missing
        require(key.period == canonicalPeriod) {
            "La clave consultada no usa el patrón canónico de su definición"
        }
        return entriesByIdentity[key.logicalIdentity()]
            ?.let(PerPeriodHoursLookup::Defined)
            ?: PerPeriodHoursLookup.Missing
    }
}

private data class PerPeriodHoursIdentity(
    val definitionId: UUID,
    val window: DateWindow,
)

private fun PerPeriodHoursKey.logicalIdentity(): PerPeriodHoursIdentity = PerPeriodHoursIdentity(
    definitionId = definitionId,
    window = window,
)
