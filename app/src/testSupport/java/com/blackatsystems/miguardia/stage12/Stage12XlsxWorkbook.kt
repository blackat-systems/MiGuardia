package com.blackatsystems.miguardia.stage12

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Lector mínimo de las hojas con texto en línea que produce [XlsxReportWriter]. */
class Stage12XlsxWorkbook(private val entries: Map<String, ByteArray>) {
    fun sheet(name: String): Stage12XlsxSheet {
        val workbook = parse(entries.getValue("xl/workbook.xml"))
        val sheets = workbook.getElementsByTagName("sheet")
        val index = (0 until sheets.length).first { (sheets.item(it) as Element).getAttribute("name") == name }
        return Stage12XlsxSheet(parse(entries.getValue("xl/worksheets/sheet${index + 1}.xml")))
    }

    fun allText(): String = entries.filterKeys { it.endsWith(".xml") }.values
        .joinToString("\n") { it.toString(Charsets.UTF_8) }

    companion object {
        fun read(bytes: ByteArray): Stage12XlsxWorkbook {
            val entries = linkedMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes()
                }
            }
            return Stage12XlsxWorkbook(entries)
        }

        private fun parse(bytes: ByteArray): Element = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes))
            .documentElement
    }
}

class Stage12XlsxSheet(root: Element) {
    /** Filas en orden; cada una mapea columna → valor de la celda. */
    val rows: List<Map<String, String>> = buildList {
        val nodes = root.getElementsByTagName("row")
        for (index in 0 until nodes.length) {
            val row = nodes.item(index) as Element
            val cells = row.getElementsByTagName("c")
            val values = linkedMapOf<String, String>()
            for (cellIndex in 0 until cells.length) {
                val cell = cells.item(cellIndex) as Element
                val column = cell.getAttribute("r").takeWhile(Char::isLetter)
                val text = cell.getElementsByTagName("t").let { if (it.length > 0) it.item(0).textContent else null }
                val number = cell.getElementsByTagName("v").let { if (it.length > 0) it.item(0).textContent else null }
                (text ?: number)?.let { values[column] = it }
            }
            add(values)
        }
    }

    fun metricValues(): Map<String, Long> {
        val header = rows.indexOfFirst { it["C"] == "Minutos / cantidad" }
        require(header >= 0)
        return rows.drop(header + 1)
            .takeWhile { it.isNotEmpty() }
            .associate { it.getValue("B") to it.getValue("C").toLong() }
    }

    fun rowAfterHeader(headerText: String): Map<String, String> =
        rows[rows.indexOfFirst { row -> row.values.contains(headerText) } + 1]

    fun dataRows(): List<Map<String, String>> = rows.drop(1).filter { it.isNotEmpty() }

    fun columnSum(column: String): Long = dataRows().sumOf { it.getValue(column).toLong() }
}
