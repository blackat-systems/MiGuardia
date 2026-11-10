package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.stage12.Stage12CalculationFixture.Canary
import com.blackatsystems.miguardia.stage12.Stage12CalculationFixture.Expected

/**
 * Oráculo único de la etapa 12.2 para un XLSX real de [Stage12CalculationFixture]: lo usan la PC y el
 * teléfono. Falla con [IllegalStateException] y un mensaje en castellano.
 */
object Stage12CalculationOracle {
    const val TOTAL_LABEL: String = "Total trabajado"
    const val REGULAR_LABEL: String = "Horas trabajadas sin extras"
    const val EXTRAS_LABEL: String = "Extras"

    /** Texto que muestran Tus horas y Resumen para cada cantidad esperada. */
    object Screen {
        const val REGULAR: String = "16 h"
        const val EXTRAS: String = "4 h 30 min"
        const val TOTAL: String = "20 h 30 min"
        const val HELPS_REFERENCE: String = "17 h"
        const val DOES_NOT_HELP: String = "3 h 30 min"
        const val MISSING: String = "3 h"
        const val TARGET: String = "20 h"
        const val AVAILABILITY: String = "12 h"
    }

    fun checkXlsx(bytes: ByteArray, privateOptionsIncluded: Boolean, displayName: String?) {
        val workbook = Stage12XlsxWorkbook.read(bytes)
        val metrics = workbook.sheet("Resumen").metricValues()
        expect("total del Resumen", Expected.TOTAL, metrics[TOTAL_LABEL])
        expect("sin extras del Resumen", Expected.REGULAR, metrics[REGULAR_LABEL])
        expect("extras del Resumen", Expected.EXTRAS, metrics[EXTRAS_LABEL])
        val goal = workbook.sheet("Resumen").rowAfterHeader("Meta (min)")
        expect("meta", Expected.TARGET, goal["D"]?.toLong())
        expect("horas que cuentan", Expected.HELPS_REFERENCE, goal["E"]?.toLong())
        expect("faltante", Expected.MISSING, goal["F"]?.toLong())
        val work = workbook.sheet("Jornadas")
        expect("filas de jornadas y extras", Expected.WORK_ROWS.toLong(), work.dataRows().size.toLong())
        expect("minutos contabilizados", Expected.TOTAL, work.columnSum("O"))
        expect("minutos sin extras", Expected.REGULAR, work.columnSum("Q"))
        expect("minutos extra", Expected.EXTRAS, work.columnSum("R"))
        expect("disponibilidad programada", Expected.AVAILABILITY_PROGRAMMED, workbook.sheet("Disponibilidad").columnSum("I"))

        val text = workbook.allText()
        listOf(Canary.ADDRESS, Canary.DIFFERENCE_REASON).forEach { forbidden ->
            check(!text.contains(forbidden)) { "El informe nunca debe incluir «$forbidden»" }
        }
        listOf(Canary.POSITION, Canary.SHIFT_NOTE, Canary.MEDICAL_NOTE).forEach { optional ->
            check(text.contains(optional) == privateOptionsIncluded) {
                if (privateOptionsIncluded) "Falta «$optional» aunque se eligió incluirlo" else "Apareció «$optional» sin elegirlo"
            }
        }
        displayName?.let { name ->
            check(text.contains(name) == privateOptionsIncluded) {
                if (privateOptionsIncluded) "Falta el nombre elegido" else "Apareció el nombre sin elegirlo"
            }
        }
        check(!UUID_PATTERN.containsMatchIn(text)) { "El informe no debe incluir identificadores técnicos" }
    }

    private fun expect(what: String, expected: Long, actual: Long?) {
        check(actual == expected) { "XLSX: $what esperado $expected, obtenido $actual" }
    }

    private val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
}
