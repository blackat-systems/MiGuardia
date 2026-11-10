package com.blackatsystems.miguardia.stage12

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Sesión humana de la etapa 12.2 (A158, A159, A161): guardar con el selector real de documentos y
 * abrir el selector real de compartir, cerrándolo sin enviar. Sólo con Joaquín frente al teléfono.
 */
class Stage12CalculationsHumanTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private lateinit var graph: Stage12CalculationStore

    @Before fun openIsolatedApp() {
        val context = instrumentation.targetContext
        check(context.packageName == QA) { "Sólo en el paquete QA" }
        graph = Stage12CalculationStore(context, UUID.randomUUID(), realDestination = true)
        runBlocking { graph.seed() }
        compose.setContent { AndroidFontScaleTheme { graph.Host() } }
        waitTag("month-grid", WAIT)
    }

    @After fun closeIsolatedApp() {
        // Primero se detienen los ViewModels de la pantalla: si siguen vivos pueden reabrir la base ya cerrada.
        runCatching { compose.runOnUiThread { compose.activity.viewModelStore.clear() } }
        compose.waitForIdle()
        if (::graph.isInitialized) graph.close()
    }

    @Test fun saveWithTheRealPickerAndCancelTheRealShareSheet() {
        compose.onNodeWithTag("main-menu-button").performClick()
        waitTag("main-destination-summary", WAIT)
        compose.onNodeWithTag("main-destination-summary").performScrollTo().performClick()
        waitTag("summary-menu", WAIT)
        compose.onNodeWithTag("summary-menu").performClick()
        compose.onNodeWithTag("summary-menu-generate-report").performClick()
        waitTag("reports-surface", WAIT)
        compose.onNodeWithTag("reports-format-pdf").performScrollTo().performClick()
        compose.onNodeWithTag("reports-generate").performScrollTo().performClick()
        waitTag("reports-ready", WAIT)
        val artifact = graph.generatedArtifacts().single { it.name.endsWith(".pdf") }
        val bytes = artifact.readBytes()

        Log.i(TAG, "PASO 1: selector de documentos abierto; elegir carpeta y tocar Guardar")
        compose.onNodeWithTag("reports-save").performScrollTo().performClick()
        compose.waitUntil(HUMAN_WAIT) { graph.savedDestinations.isNotEmpty() }
        val destination = graph.savedDestinations.single()
        val stored = instrumentation.targetContext.contentResolver.openInputStream(destination)!!.use { it.readBytes() }
        assertArrayEquals("El documento guardado es idéntico al informe generado", bytes, stored)
        Log.i(TAG, "PASO 1 OK: guardado ${stored.size} bytes idénticos")
        waitForOwnApp()

        Log.i(TAG, "PASO 2: selector de compartir abierto; cerrarlo con Atrás sin elegir nada")
        compose.onNodeWithTag("reports-share").performScrollTo().performClick()
        compose.waitUntil(OPEN_WAIT) { device.currentPackageName != QA }
        Log.i(TAG, "PASO 2: selector visible (${device.currentPackageName})")
        waitForOwnApp()
        waitTag("reports-ready", WAIT)
        assertTrue("El informe privado sigue listo después de cerrar el selector", artifact.isFile)
        assertArrayEquals(bytes, artifact.readBytes())
        assertEquals("Un solo guardado real", 1, graph.savedDestinations.size)
        compose.onNodeWithTag("reports-back-summary").performScrollTo().performClick()
        waitTag("summary-overview", WAIT)
        Log.i(TAG, "PASO 2 OK: selector cerrado, informe intacto y regreso al Resumen")
    }

    private fun waitForOwnApp() {
        compose.waitUntil(HUMAN_WAIT) { device.currentPackageName == QA }
        device.waitForIdle()
    }

    private fun waitTag(tag: String, timeout: Long) {
        compose.waitUntil(timeout) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val QA = "com.blackatsystems.miguardia.qa"
        const val TAG = "MiGuardia122"
        const val WAIT = 30_000L
        const val OPEN_WAIT = 15_000L
        const val HUMAN_WAIT = 180_000L
    }
}
