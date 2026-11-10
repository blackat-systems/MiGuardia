package com.blackatsystems.miguardia.support

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etapa 14 · A286 (parcial): el aviso de «no se pudo abrir el navegador» vive en una función privada
 * de MainActivity y sólo se ve lanzando la Activity real. Esta guarda de fuentes comprueba el texto
 * del aviso y que la rama de fallo de `openSupportLink` lo muestre; no reemplaza verlo en pantalla.
 * Sólo lee archivos del proyecto.
 */
class Stage14SupportNoBrowserNoticeGuardTest {
    private val main: File = listOf(File("src/main"), File("app/src/main"), File(System.getProperty("user.dir"), "src/main"),
        File(System.getProperty("user.dir"), "app/src/main")).firstOrNull { File(it, "AndroidManifest.xml").isFile }
        ?: error("app/src/main not found from ${File("").absolutePath}")
    private val strings get() = File(main, "res/values/strings.xml").readText()
    private val activity get() = File(main, "java/com/blackatsystems/miguardia/MainActivity.kt").readText()

    // A286
    @Test fun noticeSaysInPlainWordsThatTheBrowserDidNotOpenAndThatTheAppKeepsWorking() {
        val text = Regex("<string name=\"support_open_failed\">(.*?)</string>").findAll(strings).map { it.groupValues[1] }.toList()
        assertEquals(listOf("No pudimos abrir el navegador. Podés seguir usando MiGuardia y volver a intentarlo desde el menú."), text)
        // No address, error code or technical wording reaches the person.
        for (technical in listOf("http", "Exception", "error", "URL", "blackatsystems"))
            assertFalse("Notice must not mention $technical", text.single().contains(technical, ignoreCase = true))
    }

    // A286
    @Test fun failedOpeningShowsThatNoticeOnceAndNeverRetriesByItself() {
        val source = activity
        val calls = Regex("openSupportLink\\s*\\{").findAll(source).toList()
        assertEquals("MainActivity must open the support link from a single place", 1, calls.size)
        val call = calls.single().range.first
        val branch = source.substring(source.lastIndexOf("if (", call), (call + 400).coerceAtMost(source.length))
        assertTrue("The notice must belong to the failure branch", Regex("if \\(!\\s*[\\w.]*openSupportLink\\s*\\{").containsMatchIn(branch))
        val shown = Regex("Toast\\.makeText\\(\\s*this,\\s*R\\.string\\.support_open_failed,\\s*Toast\\.LENGTH_LONG\\s*\\)\\s*\\.show\\(\\)")
        assertTrue("A failed opening must show support_open_failed", shown.containsMatchIn(branch))
        assertEquals("The notice has a single origin", 1, Regex("R\\.string\\.support_open_failed").findAll(source).count())
    }
}
