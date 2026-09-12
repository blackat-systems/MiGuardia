package com.blackatsystems.miguardia.ui.management

import org.junit.Assert.assertEquals
import org.junit.Test

class CommonColorPresetsTest {
    @Test
    fun `ofrece exactamente diez colores comunes con nombres y valores unicos`() {
        assertEquals(10, CommonColorPresets.size)
        assertEquals(10, CommonColorPresets.map { it.name }.toSet().size)
        assertEquals(10, CommonColorPresets.map { it.argb }.toSet().size)
        assertEquals(
            listOf(
                "Rojo",
                "Naranja",
                "Amarillo",
                "Verde",
                "Turquesa",
                "Celeste",
                "Azul",
                "Violeta",
                "Morado",
                "Rosa",
            ),
            CommonColorPresets.map { it.name },
        )
        assertEquals(List(10) { 0xFF }, CommonColorPresets.map { it.argb ushr 24 })
        assertEquals("Violeta", CommonColorPresets[7].name)
        assertEquals(0xFF5C4DFF.toInt(), CommonColorPresets[7].argb)
    }
}
