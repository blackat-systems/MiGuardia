package com.blackatsystems.miguardia

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import com.blackatsystems.miguardia.core.domain.backup.BackupDatabaseSnapshot
import com.blackatsystems.miguardia.core.domain.backup.BackupPayloadCodec
import com.blackatsystems.miguardia.ui.summary.SummaryLoadState
import com.blackatsystems.miguardia.ui.summary.SummarySurface
import com.blackatsystems.miguardia.ui.summary.SummaryViewModel
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.weather.WeatherViewModel
import java.io.File
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

/** Lectura de la fixture recibida: nunca prepara datos, permisos ni preferencias. */
class FinalQaReadOnlyReviewTest {
    val compose = createEmptyComposeRule()
    // Esta regresión necesita el reloj real: el arnés Compose puede resolver la
    // animación antes de que lleguen las consultas y ocultar la carrera humana.
    @get:Rule val environment = TestRule { base, description ->
        if (description.methodName == "todayAndUpcomingDetailsStayOpenAndWeatherReturnsWithoutBusinessWrites") base
        else compose.apply(base, description)
    }
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private var scenario: ActivityScenario<MainActivity>? = null
    private var focusLost = false
    private var phase = "admisión"

    @After
    fun closeOnlyTheQaActivity() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun todayAndUpcomingDetailsStayOpenAndWeatherReturnsWithoutBusinessWrites() {
        check(InstrumentationRegistry.getArguments().getString("finalQaReadOnly") == "true")
        check(context.packageName == QA)
        check(Build.VERSION.SDK_INT == 36 && Build.MODEL == "SM-S938B")
        check(fileHash(File(context.applicationInfo.sourceDir)) == EXPECTED_QA)
        assertQaFocus(allowLifecycleTransition = true)
        val app = context.applicationContext as MiGuardiaApplication
        val before = runBlocking { app.localDataStore.backups.capture() }
        assertEquals(1956, before.table("shifts").records.size)
        val preferences = preferenceHashes()
        val result = JSONObject().put("qa_sha256", EXPECTED_QA).put("status", "INCOMPLETO")
        val openings = JSONArray()
        result.put("openings", openings)
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            waitForQaText("MiGuardia", allowLifecycleTransition = true)
            var today = java.time.LocalDate.MIN
            requireNotNull(scenario).onActivity { activity ->
                val state = ViewModelProvider(activity)[CalendarViewModel::class.java].uiState.value
                today = state.referenceInstant.atZone(com.blackatsystems.miguardia.core.domain.AppDefaults.zoneId()).toLocalDate()
            }
            listOf(today, today.plusDays(2), today, today.plusDays(3)).forEach { date ->
                phase = "detalle $date"
                assertQaFocus()
                val civilDate = "${date.dayOfMonth} de ${date.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("es-AR"))} de ${date.year}"
                val cells = device.findObjects(By.pkg(QA).descContains(civilDate))
                assertEquals("Debe existir una sola celda de esa fecha", 1, cells.size)
                cells.single().click()
                repeat(12) { android.os.SystemClock.sleep(100); assertQaFocus() }
                val observation = JSONObject().put("date", date.toString())
                requireNotNull(scenario).onActivity { activity ->
                    val calendar = ViewModelProvider(activity)[CalendarViewModel::class.java].uiState.value
                    val weather = ViewModelProvider(activity)[WeatherViewModel::class.java].uiState.value
                    observation.put("selected_date", calendar.detailDate?.toString() ?: JSONObject.NULL)
                        .put("weather_enabled", weather.preferences.enabled)
                        .put("briefs_loading", weather.loadingBriefIds.size)
                    assertEquals(date, calendar.detailDate)
                }
                openings.put(observation)
                check(device.hasObject(By.pkg(QA).text("Editar día"))) { "La hoja se ocultó después del toque real" }
                // Sólo la fixture acreditada; no solicita ubicación ni abre permisos.
                requireNotNull(device.findObject(By.pkg(QA).text("Clima durante la jornada"))).click()
                waitForQaText("Clima de la jornada")
                requireNotNull(device.findObject(By.pkg(QA).text("Cerrar"))).click()
                waitForQaText("Editar día")
                device.pressBack()
                waitForQaText("MiGuardia")
            }
            result.put("status", "COMPLETO")
        } finally {
            result.put("phase", phase)
            val after = runBlocking { app.localDataStore.backups.capture() }
            result.put("business_unchanged", before == after)
                .put("preferences_unchanged", preferences == preferenceHashes())
            val folder = File(context.filesDir, "day-detail-qa/${UUID.randomUUID()}")
            check(folder.mkdirs())
            File(folder, "review.json").writeText(result.toString(2))
            assertEquals("El recorrido no debe escribir negocio", databaseHash(before), databaseHash(after))
            assertEquals(preferences, preferenceHashes())
        }
    }

    private fun waitForQaText(text: String, allowLifecycleTransition: Boolean = false) {
        val deadline = android.os.SystemClock.elapsedRealtime() + WAIT_MILLIS
        while (true) {
            assertQaFocus(allowLifecycleTransition)
            if (device.hasObject(By.pkg(QA).text(text))) return
            check(android.os.SystemClock.elapsedRealtime() < deadline) { "No apareció el control esperado de QA" }
            android.os.SystemClock.sleep(50)
        }
    }

    @Test
    fun sixSummaryOpeningsAndRestoredDetailPreserveTheReceivedSyntheticBusiness() {
        check(InstrumentationRegistry.getArguments().getString("finalQaReadOnly") == "true") {
            "Falta la autorización explícita finalQaReadOnly."
        }
        check(context.packageName == QA)
        check(Build.VERSION.SDK_INT == 36 && Build.MODEL == "SM-S938B" && Build.MANUFACTURER.equals("samsung", true))
        check(fileHash(File(context.applicationInfo.sourceDir)) == EXPECTED_QA)
        assertQaFocus(allowLifecycleTransition = true)

        val id = UUID.randomUUID().toString()
        val folder = File(context.filesDir, "final-qa/$id")
        check(!folder.exists() && folder.mkdirs())
        val result = JSONObject()
            .put("format", "miguardia-final-qa-read-only/1")
            .put("run_id", id)
            .put("qa_sha256", EXPECTED_QA)
            .put("runner_sha256", fileHash(File(instrumentation.context.applicationInfo.sourceDir)))
            .put("requested_samples", SAMPLES)
            .put("status", "INCOMPLETO")
            .put("scope", "Seis aperturas calientes de la candidata actual sobre la misma fixture acreditada; admite caché. No es A-B-B-A, arranque frío, FPS ni causa histórica R4.")
            .put("clock", "System.nanoTime: antes de abrir menú → CONTENT/EMPTY del mes correcto y Compose idle; incluye sincronización y guardas.")
            .put("seed_clear_restore_or_preference_write_requested", false)
            .put("public_app_or_foreign_content_read", false)
        val samples = JSONArray()
        result.put("samples", samples)
        var before: BackupDatabaseSnapshot? = null
        var beforePreferences: Map<String, String?>? = null
        var failure: Throwable? = null
        var conservationFailure: Throwable? = null
        try {
            val application = context.applicationContext as MiGuardiaApplication
            phase = "recuperación inicial"
            compose.waitUntil(WAIT_MILLIS) {
                assertQaFocus(allowLifecycleTransition = true)
                application.startupRecoveryGate.isReady
            }
            before = runBlocking { application.localDataStore.backups.capture() }
            // A2 terminó 12 iteraciones: 1920 jornadas iniciales + 12 × 3 guardadas.
            assertEquals("La fixture recibida ya no es la acreditada por MAIN.", 1956, before.table("shifts").records.size)
            beforePreferences = preferenceHashes()
            result.put("database_before_sha256", databaseHash(before))
                .put("tables_before", before.tables.size)
                .put("shifts_before", before.table("shifts").records.size)
                .put("preferences_before_sha256", JSONObject(beforePreferences))
            phase = "abrir Calendario"
            scenario = ActivityScenario.launch(MainActivity::class.java)
            awaitTag("month-grid", allowLifecycleTransition = true)
            assertQaFocus()
            requireNotNull(scenario).onActivity { activity ->
                val configuration = activity.resources.configuration
                val themePreference = activity.getSharedPreferences("miguardia_display_preferences", Context.MODE_PRIVATE)
                    .getString("app_theme_mode", null)
                result.put("window", JSONObject()
                    .put("width_px", activity.window.decorView.width)
                    .put("height_px", activity.window.decorView.height)
                    .put("font_scale_android", configuration.fontScale.toDouble())
                    .put("density_dpi", configuration.densityDpi)
                    .put("orientation", configuration.orientation)
                    .put("system_night_mode", configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                    .put("theme_preference", themePreference ?: JSONObject.NULL))
            }
            repeat(SAMPLES) { index ->
                phase = "abrir Resumen ${index + 1}"
                assertQaFocus()
                val start = System.nanoTime()
                compose.onNodeWithTag("main-menu-button").performClick()
                assertQaFocus()
                compose.onNodeWithTag("main-destination-summary").performScrollTo().performClick()
                awaitSummaryReady()
                compose.waitForIdle()
                assertQaFocus()
                val elapsed = (System.nanoTime() - start) / 1_000_000.0
                val sample = JSONObject().put("iteration", index + 1).put("milliseconds", elapsed)
                requireNotNull(scenario).onActivity { activity ->
                    val state = ViewModelProvider(activity)[SummaryViewModel::class.java].uiState.value
                    sample.put("month", state.visibleMonth.toString()).put("load_state", state.loadState.name)
                    assertNotNull(state.projection)
                }
                samples.put(sample)
                assertTrue("La duración debe ser finita y positiva.", elapsed.isFinite() && elapsed > 0)
                compose.onNodeWithTag("summary-overview").assertIsDisplayed()
                if (index == 0) reviewDetailAndRecreation(result)
                phase = "volver al Calendario ${index + 1}"
                assertQaFocus()
                // El booleano de UiDevice no acredita navegación; verificar la pantalla resultante.
                pressBack()
                awaitTag("month-grid")
                compose.waitForIdle()
                assertQaFocus()
            }
            assertEquals(SAMPLES, samples.length())
            result.put("status", "COMPLETO")
        } catch (error: Throwable) {
            failure = error
            result.put("failed_phase", phase).put("error_type", error.javaClass.simpleName)
        } finally {
            // Sólo lecturas de la QA ya acreditada; no borrado ni restauración para fabricar igualdad.
            if (before != null) {
                try {
                    val after = runBlocking {
                        (context.applicationContext as MiGuardiaApplication).localDataStore.backups.capture()
                    }
                    val afterPreferences = preferenceHashes()
                    val sameDatabase = databaseHash(before) == databaseHash(after)
                    val samePreferences = beforePreferences == afterPreferences
                    result.put("database_after_sha256", databaseHash(after))
                        .put("tables_after", after.tables.size)
                        .put("shifts_after", after.table("shifts").records.size)
                        .put("preferences_after_sha256", JSONObject(afterPreferences))
                        .put("business_unchanged", sameDatabase)
                        .put("known_preferences_unchanged", samePreferences)
                    // No incluir las filas de negocio en el texto de una aserción fallida.
                    assertTrue("Cambió el contenido lógico de negocio.", before == after)
                    assertEquals("Cambiaron preferencias durante un recorrido de sólo lectura.", beforePreferences, afterPreferences)
                } catch (error: Throwable) {
                    conservationFailure = error
                    result.put("conservation_error_type", error.javaClass.simpleName)
                }
            }
            result.put("completed_samples", samples.length())
                .put("focus_lost", focusLost)
                .put("final_phase", phase)
            if (failure != null || conservationFailure != null) result.put("status", "INCOMPLETO")
            val output = File(folder, "summary-read-only.json")
            check(output.createNewFile())
            output.writeText(result.toString(2), Charsets.UTF_8)
        }
        failure?.let { throw it }
        conservationFailure?.let { throw it }
    }

    private fun reviewDetailAndRecreation(result: JSONObject) {
        phase = "detalle de métrica y recreación"
        assertQaFocus()
        compose.onNodeWithTag("summary-metric-essential-total").performScrollTo().performClick()
        awaitTag("summary-detail")
        var metricId: String? = null
        requireNotNull(scenario).onActivity { activity ->
            val state = ViewModelProvider(activity)[SummaryViewModel::class.java].uiState.value
            assertEquals(SummarySurface.DETAIL, state.surface)
            metricId = state.selectedMetricId
            assertNotNull(metricId)
        }
        assertQaFocus()
        requireNotNull(scenario).recreate()
        awaitTag("summary-detail", allowLifecycleTransition = true)
        awaitSummaryReady(allowLifecycleTransition = true)
        assertQaFocus()
        requireNotNull(scenario).onActivity { activity ->
            val state = ViewModelProvider(activity)[SummaryViewModel::class.java].uiState.value
            assertEquals(SummarySurface.DETAIL, state.surface)
            assertEquals(metricId, state.selectedMetricId)
        }
        compose.onNodeWithTag("summary-detail-back").performScrollTo().performClick()
        awaitTag("summary-overview")
        assertQaFocus()
        result.put("detail_recreation_and_return", "COMPROBADO")
    }

    private fun awaitSummaryReady(allowLifecycleTransition: Boolean = false) {
        compose.waitUntil(WAIT_MILLIS) {
            assertQaFocus(allowLifecycleTransition)
            var ready = false
            requireNotNull(scenario).onActivity { activity ->
                val state = ViewModelProvider(activity)[SummaryViewModel::class.java].uiState.value
                check(state.loadState != SummaryLoadState.ERROR) { "El Resumen publicó un error." }
                ready = state.loadState in setOf(SummaryLoadState.CONTENT, SummaryLoadState.EMPTY) &&
                    state.projection?.month == state.visibleMonth && state.errorMessage == null
            }
            ready
        }
    }

    private fun awaitTag(tag: String, allowLifecycleTransition: Boolean = false) {
        compose.waitUntil(WAIT_MILLIS) {
            assertQaFocus(allowLifecycleTransition)
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertQaFocus(allowLifecycleTransition: Boolean = false) {
        check(!focusLost) { "Una pérdida de foco anterior detuvo este recorrido." }
        val locked = (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
        val active = device.currentPackageName // Sólo identidad; nunca texto o nodos de otra aplicación.
        val launcher = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0,
        )?.activityInfo?.packageName
        val allowed = active == QA || (allowLifecycleTransition && (active == null || active == launcher))
        if (locked || !device.isScreenOn || !allowed) {
            focusLost = true
            error("Se detuvo la prueba: bloqueo o ventana ajena/no resuelta. No se leyó su contenido.")
        }
    }

    private fun preferenceHashes(): Map<String, String?> = PREFERENCE_PATHS.associateWith { relative ->
        File(context.applicationInfo.dataDir, relative).let { if (it.isFile) fileHash(it) else null }
    }

    private fun databaseHash(snapshot: BackupDatabaseSnapshot): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val sink = object : OutputStream() {
            override fun write(value: Int) = Unit
            override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
        }
        BackupPayloadCodec.writeDatabase(snapshot, DigestOutputStream(sink, digest))
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun fileHash(file: File): String = file.inputStream().use { stream ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65_536)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private companion object {
        const val QA = "com.blackatsystems.miguardia.qa"
        const val EXPECTED_QA = "682e529e79eb398b31c757e51d8d3500e165a8d90293cb9aea07875a6bed0f2e"
        const val SAMPLES = 6
        const val WAIT_MILLIS = 30_000L
        val PREFERENCE_PATHS = listOf(
            "files/datastore/guard_profile.preferences_pb.preferences_pb",
            "files/datastore/summary_preferences.preferences_pb.preferences_pb",
            "files/datastore/notification_preferences.preferences_pb.preferences_pb",
            "files/datastore/weather_preferences.preferences_pb.preferences_pb",
            "shared_prefs/miguardia_display_preferences.xml",
        )
    }
}
