package com.blackatsystems.miguardia.stage12

import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.stage12.Stage12CalculationFixture.Expected
import com.blackatsystems.miguardia.stage12.Stage12CalculationOracle.Screen
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Etapa 12.2 en el teléfono: la app real (MiGuardiaApp con sus ViewModels y Room) sobre una base
 * propia de la corrida, sembrada con [Stage12CalculationFixture]. No abre ni modifica los datos QA:
 * la base `stage12-2-<uuid>.db` y su carpeta privada se borran al terminar.
 */
class Stage12CalculationsDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private lateinit var graph: Stage12CalculationStore
    private var setContentAt = 0L

    @Before fun openIsolatedApp() {
        val context = instrumentation.targetContext
        check(context.packageName == "com.blackatsystems.miguardia.qa") { "Sólo en el paquete QA" }
        graph = Stage12CalculationStore(context, UUID.randomUUID())
        runBlocking { graph.seed() }
        setContentAt = SystemClock.elapsedRealtime()
        compose.setContent { AndroidFontScaleTheme { graph.Host() } }
        waitTag("month-grid")
    }

    @After fun closeIsolatedApp() {
        // Primero se detienen los ViewModels de la pantalla: si siguen vivos pueden reabrir la base ya cerrada.
        runCatching { compose.runOnUiThread { compose.activity.viewModelStore.clear() } }
        compose.waitForIdle()
        if (::graph.isInitialized) graph.close()
    }

    /** Tus horas y tu meta muestra los mismos minutos que la PC (casos A086, A087). */
    @Test fun hoursShowTheFixtureMinutesAndKeepAvailabilitySeparate() {
        openHours()
        val content = hasAnyAncestor(hasTestTag("hours-progress-content"))
        listOf(Screen.REGULAR, Screen.EXTRAS, Screen.TOTAL, Screen.MISSING).forEach { value ->
            assertTextIn(value, content)
        }
        compose.onNode(hasTestTag("advanced-options-toggle") and content).performScrollTo().performClick()
        listOf(Screen.HELPS_REFERENCE, Screen.DOES_NOT_HELP).forEach { value -> assertTextIn(value, content) }
        assertTextIn("${Stage12CalculationFixture.HELPING_CLASS_NAME}: 1 h · cuenta para la meta", content)
        assertTextIn("${Stage12CalculationFixture.NOT_HELPING_CLASS_NAME}: ${Screen.DOES_NOT_HELP} · no cuenta para la meta", content)
        compose.onNodeWithTag("hours-pending-scheduled").performScrollTo()
        assertTrue(
            "La disponibilidad no se suma al trabajo",
            compose.onAllNodes(hasText(Screen.TOTAL) and content).fetchSemanticsNodes().size == 1,
        )
    }

    /** Resumen: mismos minutos, detalle exacto y navegación de meses (A128–A135, A144). */
    @Test fun summaryMatchesHoursAndNavigatesMonthsExactly() {
        val firstOpenMs = openSummary()
        Log.i(LOG_TAG, "summary_first_open_ms=$firstOpenMs since_content_ms=${SystemClock.elapsedRealtime() - setContentAt}")
        assertSummaryMonth("Septiembre de 2026")
        metricValue("essential-total").assertTextEquals(Screen.TOTAL)
        metricValue("essential-regular").assertTextEquals(Screen.REGULAR)
        metricValue("essential-extras").assertTextEquals(Screen.EXTRAS)
        assertTextIn(Screen.HELPS_REFERENCE, hasAnyAncestor(hasTestTag("summary-compliance")))
        assertTextIn(Screen.MISSING, hasAnyAncestor(hasTestTag("summary-compliance")))
        assertTextIn(Screen.AVAILABILITY, hasAnyAncestor(hasTestTag("summary-availability")))

        compose.onNodeWithTag("summary-metric-essential-total").performScrollTo().performClick()
        waitTag("summary-detail")
        val amounts = compose.onAllNodes(tagMatches { it.startsWith("summary-contribution-") && it.endsWith("-amount") }, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals("Contribuciones del total: diurna, su extra, nocturna y extra independiente", 4, amounts.size)
        compose.onNodeWithTag("summary-detail-back").performClick()
        waitTag("summary-overview")

        compose.onNodeWithTag("summary-previous-month").performClick()
        assertSummaryMonth("Agosto de 2026")
        waitTag("summary-empty")
        compose.onNodeWithTag("summary-next-month").performClick()
        assertSummaryMonth("Septiembre de 2026")
        compose.onNodeWithTag("summary-next-month").performClick()
        assertSummaryMonth("Octubre de 2026")
        compose.onNodeWithTag("summary-current-month").performClick()
        assertSummaryMonth("Septiembre de 2026")
        metricValue("essential-total").assertTextEquals(Screen.TOTAL)
    }

    /** Calendario → detalle → Resumen → volver conserva el mes elegido en Calendario. */
    @Test fun calendarDetailSummaryAndBackKeepTheCalendarMonth() {
        openDay(Stage12CalculationFixture.DAY_SHIFT_DATE)
        device.pressBack()
        waitFor() { compose.onAllNodesWithTag("day-detail-modal-sheet").fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasContentDescriptionExact("Mes anterior") and hasAnyAncestor(hasTestTag("calendar-month-controls")))
            .performClick()
        assertCalendarMonth("Agosto de 2026")
        openSummary()
        assertSummaryMonth("Septiembre de 2026")
        device.pressBack()
        waitTag("month-grid")
        assertCalendarMonth("Agosto de 2026")
    }

    /** Informes desde el menú y desde el final del Resumen: mes, vista previa, XLSX real y regreso. */
    @Test fun reportsFromSummaryKeepMonthMatchTheRealXlsxAndReturn() {
        openSummary()
        compose.onNodeWithTag("summary-menu").performClick()
        compose.onNodeWithTag("summary-menu-generate-report").performClick()
        waitTag("reports-surface")
        assertTextIn("Generá una fotografía privada y de sólo lectura de septiembre de 2026.", hasAnyAncestor(hasTestTag("reports-surface")))

        compose.onNodeWithTag("reports-format-xlsx").performScrollTo().performClick()
        compose.onNodeWithTag("reports-generate").performScrollTo().performClick()
        waitTag("reports-ready")
        val review = hasAnyAncestor(hasTestTag("reports-review"))
        assertTextIn("Total trabajado: 20 h 30 min", review)
        assertTextIn("Jornadas y extras: ${Expected.WORK_ROWS}", review)
        assertTextIn("Disponibilidades: 1 (separadas del trabajo)", review)
        assertTextIn("Notas incluidas: 0", review)
        val xlsx = newest(".xlsx")
        Stage12CalculationOracle.checkXlsx(xlsx.readBytes(), privateOptionsIncluded = false, displayName = null)

        compose.onNodeWithTag("reports-regenerate").performScrollTo().performClick()
        waitTag("reports-ready")
        Stage12CalculationOracle.checkXlsx(
            newest(".xlsx").readBytes(),
            privateOptionsIncluded = false,
            displayName = null,
        )

        compose.onNodeWithTag("reports-back-summary").performScrollTo().performClick()
        waitTag("summary-overview")
        assertSummaryMonth("Septiembre de 2026")

        compose.onNodeWithTag("summary-previous-month").performClick()
        assertSummaryMonth("Agosto de 2026")
        compose.onNodeWithTag("summary-generate-report").performScrollTo().performClick()
        waitTag("reports-surface")
        assertTextIn("Generá una fotografía privada y de sólo lectura de agosto de 2026.", hasAnyAncestor(hasTestTag("reports-surface")))
        compose.onNodeWithTag("reports-back-summary").performScrollTo().performClick()
        waitTag("summary-overview")
        assertSummaryMonth("Agosto de 2026")
    }

    /** El PDF real se genera con la misma proyección; la foto ofrece su aviso de privacidad (A147, A155). */
    @Test fun pdfIsGeneratedFromTheSameProjection() {
        openSummary()
        compose.onNodeWithTag("summary-menu").performClick()
        compose.onNodeWithTag("summary-menu-generate-report").performClick()
        waitTag("reports-surface")
        compose.onNodeWithTag("reports-format-pdf").performScrollTo().performClick()
        compose.onNodeWithTag("reports-generate").performScrollTo().performClick()
        waitTag("reports-ready")
        assertTextIn("Total trabajado: 20 h 30 min", hasAnyAncestor(hasTestTag("reports-review")))
        val pdf = newest(".pdf")
        val bytes = pdf.readBytes()
        assertTrue("Firma PDF", bytes.size > 4 && String(bytes, 0, 5, Charsets.US_ASCII) == "%PDF-")
        listOf(Stage12CalculationFixture.Canary.ADDRESS, Stage12CalculationFixture.Canary.DIFFERENCE_REASON).forEach {
            assertTrue("El PDF no debe incluir «$it»", !String(bytes, Charsets.ISO_8859_1).contains(it))
        }
        exportForPc(pdf)
    }

    /** El artefacto real más reciente del formato pedido dentro de la carpeta privada de la corrida. */
    private fun newest(extension: String): java.io.File =
        checkNotNull(graph.generatedArtifacts().filter { it.name.endsWith(extension) }.maxByOrNull { it.lastModified() }) {
            "No se generó ningún $extension"
        }

    /** Envía el PDF ficticio por logcat en base64 (sin dejar archivos en el teléfono) para leerlo en la PC. */
    private fun exportForPc(file: java.io.File) {
        val encoded = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
        val parts = encoded.chunked(3_000)
        parts.forEachIndexed { index, chunk -> Log.i(LOG_TAG, "pdf ${index + 1}/${parts.size} $chunk") }
    }

    private fun openHours() {
        compose.onNodeWithTag("main-menu-button").performClick()
        compose.onNodeWithTag("drawer-action-work-setup").performScrollTo().performClick()
        waitTag("work-setup-overview")
        compose.onNode(hasTestTag("advanced-options-toggle") and hasAnyAncestor(hasTestTag("work-setup-overview")))
            .performScrollTo().performClick()
        waitTag("work-setup-hours-progress")
        compose.onNodeWithTag("work-setup-hours-progress").performScrollTo().performClick()
        waitTag("hours-progress-content")
        waitFor() { compose.onAllNodes(hasText(Screen.TOTAL)).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Devuelve los milisegundos desde el toque hasta ver el total con datos. */
    private fun openSummary(): Long {
        compose.onNodeWithTag("main-menu-button").performClick()
        waitFor() { compose.onAllNodesWithTag("main-destination-summary").fetchSemanticsNodes().isNotEmpty() }
        val start = SystemClock.elapsedRealtime()
        compose.onNodeWithTag("main-destination-summary").performScrollTo().performClick()
        try {
            waitFor() { compose.onAllNodesWithTag("summary-metric-essential-total-value", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (error: Throwable) {
            dumpScreen("summary-open")
            throw error
        }
        return SystemClock.elapsedRealtime() - start
    }

    /** Diagnóstico sólo ante una espera vencida: estados del Resumen y árbol de la pantalla (datos ficticios). */
    private fun dumpScreen(where: String) {
        val states = listOf("summary-loading", "summary-error", "summary-empty", "summary-source-warning", "summary-overview",
            "summary-intro", "reports-surface", "month-grid")
            .filter { compose.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
        Log.i(LOG_TAG, "dump[$where] states=$states")
        val roots = compose.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().size
        for (index in 0 until roots) {
            compose.onAllNodes(androidx.compose.ui.test.isRoot())[index].printToString(Int.MAX_VALUE)
                .chunked(3_000).forEachIndexed { part, chunk -> Log.i(LOG_TAG, "dump[$where] root=$index part=$part: $chunk") }
        }
    }

    private fun openDay(date: LocalDate) {
        waitFor() {
            compose.onAllNodesWithTag("day-$date").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("completed-day-$date").fetchSemanticsNodes().isNotEmpty()
        }
        val tag = if (compose.onAllNodesWithTag("day-$date").fetchSemanticsNodes().isNotEmpty()) "day-$date" else "completed-day-$date"
        compose.onNodeWithTag(tag).performClick()
        waitTag("day-detail-modal-sheet")
    }

    private fun metricValue(key: String): SemanticsNodeInteraction {
        val tag = "summary-metric-$key-value"
        waitFor() { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        return compose.onNodeWithTag(tag, useUnmergedTree = true)
    }

    private fun assertSummaryMonth(label: String) {
        waitFor() {
            compose.onAllNodes(hasText(label) and hasAnyAncestor(hasTestTag("summary-month-controls"))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertCalendarMonth(label: String) {
        waitFor() {
            compose.onAllNodes(hasText(label) and hasAnyAncestor(hasTestTag("calendar-month-controls"))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertTextIn(text: String, scope: SemanticsMatcher) {
        waitFor() { compose.onAllNodes(hasText(text) and scope).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitFor(condition: () -> Boolean) {
        try {
            compose.waitUntil(WAIT, condition)
        } catch (error: Throwable) {
            runCatching { dumpScreen(Throwable().stackTrace.getOrNull(1)?.lineNumber?.toString() ?: "?") }
            throw error
        }
    }

    private fun waitTag(tag: String) {
        waitFor() { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun tagMatches(predicate: (String) -> Boolean) = SemanticsMatcher("testTag coincide") { node ->
        node.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.let(predicate) == true
    }

    private fun hasContentDescriptionExact(value: String) = SemanticsMatcher("descripción «$value»") { node ->
        node.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.contains(value) == true
    }

    private companion object {
        const val WAIT = 30_000L
        const val LOG_TAG = "MiGuardia122"
    }
}
