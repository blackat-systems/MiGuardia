package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.ui.fullDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class Stage12CalendarCellSelectorTest {
    @Test fun originalSubstringMatchesFourDaysButTheProducerPrefixIsUnique() {
        val dates = listOf(1, 11, 21, 31).map { LocalDate.of(2026, 10, it) }
        val descriptions = dates.map { it.fullDisplayName() + ", sin definir" }
        assertEquals(4, descriptions.count { it.contains("1 de octubre de 2026") })
        val pattern = Stage12CalendarCellSelector.descriptionPattern(dates.first())
        assertEquals(listOf(descriptions.first()), descriptions.filter { pattern.matcher(it).matches() })
    }

    @Test fun eachDaySelectsOnlyItsOwnCellAcrossMonthsYearsAndLeapDays() {
        val months = listOf(YearMonth.of(2024, 2), YearMonth.of(2026, 9),
            YearMonth.of(2026, 10), YearMonth.of(2026, 11), YearMonth.of(2027, 10))
        val dates = months.flatMap { month -> (1..month.lengthOfMonth()).map(month::atDay) }
        val cells = dates.map { it to (it.fullDisplayName() + ", hoy, sin definir") }
        dates.forEach { target ->
            val pattern = Stage12CalendarCellSelector.descriptionPattern(target)
            assertEquals(listOf(target), cells.filter { pattern.matcher(it.second).matches() }.map { it.first })
        }
    }

    @Test fun stateSelectionAndMultilineDetailsPreserveTheFirstDateSegment() {
        val date = LocalDate.of(2026, 10, 1)
        val pattern = Stage12CalendarCellSelector.descriptionPattern(date)
        listOf("", ", hoy", ", seleccionado", ", 2 turnos, próxima",
            ", una ventana de disponibilidad: noche\nfin", ", franco marcado explícitamente").forEach { suffix ->
            assertTrue(pattern.matcher(date.fullDisplayName() + suffix).matches())
        }
        assertFalse(pattern.matcher(date.fullDisplayName() + "0").matches())
        assertFalse(pattern.matcher("Otra superficie, " + date.fullDisplayName()).matches())
    }

    @Test fun aDateMentionedByAnotherCellsVacationNeverSelectsThatOtherCell() {
        val target = LocalDate.of(2026, 10, 1)
        val other = LocalDate.of(2026, 10, 15)
        val description = other.fullDisplayName() + ", vacaciones desde " + target.fullDisplayName() +
            " hasta " + LocalDate.of(2026, 10, 31).fullDisplayName() + " inclusive"
        assertTrue(description.contains("1 de octubre de 2026"))
        assertFalse(Stage12CalendarCellSelector.descriptionPattern(target).matcher(description).matches())
        assertTrue(Stage12CalendarCellSelector.descriptionPattern(other).matcher(description).matches())
    }

    @Test fun zeroOrMultipleMatchesStopWithOnlyTheTechnicalCount() {
        Stage12CalendarCellSelector.requireUnique(1)
        listOf(0, 2, 31).forEach { count ->
            val failure = assertThrows(IllegalStateException::class.java) {
                Stage12CalendarCellSelector.requireUnique(count)
            }
            assertEquals("Expected one owned calendar cell; matches=$count", failure.message)
        }
    }
}
