package com.blackatsystems.miguardia.ui.components

import java.time.LocalDate
import java.time.LocalTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativePickersTest {
    @Test
    fun pickedTimeIsTheSameTextAsTypedEntry() {
        assertEquals("00:00", formatPickedTime(0, 0))
        assertEquals("23:59", formatPickedTime(23, 59))
        assertEquals("08:30", formatPickedTime(8, 30))
        // Typing "0830" yields the same canonical text.
        assertEquals(formatPickedTime(8, 30), canonicalAutomaticTimeOrDraft(formatAutomaticTimeInput("0830")!!))
        assertEquals(formatPickedTime(23, 59), canonicalAutomaticTimeOrDraft(formatAutomaticTimeInput("2359")!!))
        assertEquals(formatPickedTime(0, 0), canonicalAutomaticTimeOrDraft(formatAutomaticTimeInput("0000")!!))
    }

    @Test
    fun timePickerStartsFromTheFieldValueOnlyWhenItIsValid() {
        val fallback = LocalTime.of(10, 15)
        assertEquals(LocalTime.of(22, 0), timePickerInitialValue("22:00", fallback))
        assertEquals(LocalTime.of(23, 59), timePickerInitialValue("23:59", fallback))
        assertEquals(fallback, timePickerInitialValue("", fallback))
        assertEquals(fallback, timePickerInitialValue("08", fallback))
        assertEquals(fallback, timePickerInitialValue("25:00", fallback))
    }

    @Test
    fun pickerMillisRoundTripKeepsTheCalendarDateInAnyZone() {
        val original = TimeZone.getDefault()
        try {
            listOf("America/Argentina/Buenos_Aires", "Pacific/Kiritimati", "Pacific/Pago_Pago", "UTC").forEach { zone ->
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                listOf(
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 12, 31),
                    LocalDate.of(2028, 2, 29),
                ).forEach { date ->
                    assertEquals(zone, date, localDateFromPickerMillis(pickerMillisFromLocalDate(date)))
                }
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun dateFormatsWriteAndReadTheTypedText() {
        val date = LocalDate.of(2026, 11, 8)
        assertEquals("2026-11-08", DateFieldFormat.ISO.format(date))
        assertEquals("08/11/2026", DateFieldFormat.DAY_MONTH_YEAR.format(date))
        assertEquals(date, DateFieldFormat.ISO.parseOrNull("2026-11-08"))
        assertEquals(date, DateFieldFormat.DAY_MONTH_YEAR.parseOrNull("08/11/2026"))
        assertNull(DateFieldFormat.ISO.parseOrNull("2026-02-30"))
        assertNull(DateFieldFormat.DAY_MONTH_YEAR.parseOrNull("31/04/2026"))
        assertNull(DateFieldFormat.ISO.parseOrNull("08/11/2026"))
        assertNull(DateFieldFormat.ISO.parseOrNull(""))
    }
}
