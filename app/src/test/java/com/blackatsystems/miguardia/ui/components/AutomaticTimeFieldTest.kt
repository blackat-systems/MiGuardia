package com.blackatsystems.miguardia.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticTimeFieldTest {
    @Test
    fun `agrega los dos puntos al escribir cuatro numeros`() {
        assertEquals("0", formatAutomaticTimeInput("0"))
        assertEquals("08", formatAutomaticTimeInput("08"))
        assertEquals("08:3", formatAutomaticTimeInput("083"))
        assertEquals("08:30", formatAutomaticTimeInput("0830"))
    }

    @Test
    fun `acepta cuatro numeros o un unico separador esperado`() {
        assertEquals("08:00", formatAutomaticTimeInput("0800"))
        assertEquals("08:00", formatAutomaticTimeInput("08:00"))
        assertEquals("08:30", formatAutomaticTimeInput("8:30"))
        assertEquals("01:2", formatAutomaticTimeInput("1:2"))
    }

    @Test
    fun `una hora corta completa se normaliza pero un borrador incompleto se conserva`() {
        assertEquals("08:30", canonicalAutomaticTimeOrDraft("8:30"))
        assertEquals("08:30", canonicalAutomaticTimeOrDraft("0830"))
        assertEquals("8:3", canonicalAutomaticTimeOrDraft("8:3"))
        assertEquals("24:00", canonicalAutomaticTimeOrDraft("24:00"))
    }

    @Test
    fun `rechaza pegados largos caracteres de ruido y digitos no ascii`() {
        assertNull(formatAutomaticTimeInput("123456"))
        assertNull(formatAutomaticTimeInput("06.00 texto"))
        assertNull(formatAutomaticTimeInput(" 08:30 "))
        assertNull(formatAutomaticTimeInput("08::30"))
        assertNull(formatAutomaticTimeInput(":30"))
        assertNull(formatAutomaticTimeInput("083:0"))
        assertNull(formatAutomaticTimeInput("٠٨٣٠"))
    }

    @Test
    fun `conserva limites horarios sin decidir su validez semantica`() {
        assertEquals("00:00", formatAutomaticTimeInput("0000"))
        assertEquals("23:59", formatAutomaticTimeInput("2359"))
        assertEquals("24:00", formatAutomaticTimeInput("2400"))
        assertEquals("12:60", formatAutomaticTimeInput("1260"))
    }

    @Test
    fun `marca como invalidos solamente los horarios completos fuera de rango`() {
        assertFalse(isCompleteInvalidAutomaticTime(""))
        assertFalse(isCompleteInvalidAutomaticTime("12:6"))
        assertFalse(isCompleteInvalidAutomaticTime("00:00"))
        assertFalse(isCompleteInvalidAutomaticTime("23:59"))
        assertTrue(isCompleteInvalidAutomaticTime("24:00"))
        assertTrue(isCompleteInvalidAutomaticTime("12:60"))
    }

    @Test
    fun `permite borrar sin dejar un separador huerfano`() {
        assertEquals("08:3", formatAutomaticTimeInput("08:3"))
        assertEquals("08", formatAutomaticTimeInput("08:"))
        assertEquals("", formatAutomaticTimeInput(":"))
    }

    @Test
    fun `borrar un digito no lo repone y permite corregir la hora`() {
        val deleted = applyAutomaticTimeEdit(
            previous = TextFieldValue("08:30", selection = TextRange(1)),
            candidate = TextFieldValue("8:30", selection = TextRange(0)),
        )
        assertEquals("8:30", deleted?.text)
        assertEquals(TextRange(0), deleted?.selection)

        val corrected = applyAutomaticTimeEdit(
            previous = requireNotNull(deleted),
            candidate = TextFieldValue("18:30", selection = TextRange(1)),
        )
        assertEquals("18:30", corrected?.text)
    }

    @Test
    fun `borrar solamente los dos puntos los repone sin mover el cursor al final`() {
        val result = applyAutomaticTimeEdit(
            previous = TextFieldValue("08:30", selection = TextRange(3)),
            candidate = TextFieldValue("0830", selection = TextRange(2)),
        )

        assertEquals("08:30", result?.text)
        assertEquals(TextRange(2), result?.selection)
    }

    @Test
    fun `reemplazar el valor completo sigue normalizando una hora corta`() {
        val replaced = applyAutomaticTimeEdit(
            previous = TextFieldValue("08:30", selection = TextRange(0, 5)),
            candidate = TextFieldValue("8:30", selection = TextRange(4)),
        )

        assertEquals("08:30", replaced?.text)
        assertEquals(TextRange(5), replaced?.selection)
    }
}
