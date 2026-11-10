package com.blackatsystems.miguardia.stage12

import android.app.KeyguardManager
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.MiGuardiaApplication
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.backup.BackupPayloadCodec
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.weather.WeatherSurface
import com.blackatsystems.miguardia.ui.weather.WeatherViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

/** Real MainActivity/input/Android clock. Normal QA data use; no ComposeRule or invented snapshot. */
class Stage12NormalQaActivityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val app get() = context.applicationContext as MiGuardiaApplication
    private var scenario: ActivityScenario<MainActivity>? = null
    private var facts = JSONArray()

    @After fun closeOwnedActivity() { scenario?.close(); scenario = null }

    @Test fun actualMonthNavigationCrossesYearsAndReturnsToToday() = runCase("month-navigation", "REAL") {
        val month = YearMonth.from(calendar().referenceInstant.atZone(AppDefaults.zoneId()))
        goToMonth(month)
        openDate(month.atDay(1)); device.pressBack()
        await { calendar().detailDate == null }
        clickMonth(-1)
        fact("C002-mes-anterior.activity_native", "EXACT", JSONObject().put("from", month.toString()).put("to", month.minusMonths(1).toString()))
        goToMonth(month); clickMonth(1)
        fact("C003-mes-siguiente.activity_native", "EXACT", JSONObject().put("from", month.toString()).put("to", month.plusMonths(1).toString()))
        val january = YearMonth.of(month.year + 1, 1)
        goToMonth(january); clickMonth(-1)
        fact("C002-enero-a-diciembre-anterior.activity_native", "EXACT", JSONObject().put("from", january.toString()).put("to", january.minusMonths(1).toString()))
        clickMonth(1)
        fact("C003-diciembre-a-enero-siguiente.activity_native", "EXACT", JSONObject().put("from", january.minusMonths(1).toString()).put("to", january.toString()))
        click(requireNotNull(device.findObject(By.pkg(QA).desc("Abrir menú"))))
        await { device.hasObject(By.pkg(QA).text("Ir a hoy")) }
        click(requireNotNull(device.findObject(By.pkg(QA).text("Ir a hoy"))))
        val todayMonth = YearMonth.from(calendar().referenceInstant.atZone(AppDefaults.zoneId()))
        awaitMonth(todayMonth)
        assertEquals(null, calendar().detailDate)
        fact("C005-contrato-completo.activity_native", "EXACT", JSONObject().put("month", todayMonth.toString()))
    }

    @Test fun todayDetailRemainsOpenAcrossCloseAndReopen() = runCase("today-detail", "REAL") {
        val today = calendar().referenceInstant.atZone(AppDefaults.zoneId()).toLocalDate()
        goToMonth(YearMonth.from(today))
        val count = calendar().days.single { it.date == today }.shifts.size
        repeat(2) {
            val callbacks = openDate(today); stableDetail(today)
            val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, LOCALE).replaceFirstChar { c -> c.titlecase(LOCALE) }
            assertTrue(device.hasObject(By.pkg(QA).text("$weekday ${fullDate(today)}")))
            assertTrue(device.hasObject(By.pkg(QA).text("Editar día")))
            fact("C023-contrato-completo.activity_native", "EXACT", JSONObject().put("date", today.toString()).put("opening", it + 1).put("shifts", count))
            fact("C034-hoy-cerrar-reabrir-nativo.activity_native", "EXACT", JSONObject().put("date", today.toString()).put("opening", it + 1).put("open_date_callbacks", callbacks))
            device.pressBack(); await { calendar().detailDate == null }
        }
    }

    @Test fun fixedDateWeatherAndFutureDetailsKeepTheirOwnerAndReturn() = runCase("fixed-date-weather") {
        val today = calendar().referenceInstant.atZone(AppDefaults.zoneId()).toLocalDate()
        assertEquals(LocalDate.of(2026, 10, 23), today)
        goToMonth(YearMonth.from(today))
        val future = today.plusDays(1)
        repeat(2) {
            val callbacks = openDate(future); stableDetail(future)
            fact("C034-futuro-cerrar-reabrir-nativo.activity_native", "EXACT", JSONObject()
                .put("date", future.toString()).put("opening", it + 1).put("open_date_callbacks", callbacks))
            device.pressBack(); await { calendar().detailDate == null }
        }
        val day = calendar().days.single { it.date == today }
        check(day.shifts.size == 1) { "FIXTURE_PRECONDITION_20261023_REQUIRES_ONE_IDENTIFIABLE_SHIFT" }
        val shift = day.shifts.single().shift
        repeat(2) {
            openDate(day.date); stableDetail(day.date)
            var opened = false
            val panel = ownDetailPanel()
            repeat(8) {
                if (!opened) {
                    assertQa()
                    assertEquals(day.date, calendar().detailDate)
                    check(panel.applicationPackage == QA && panel.isScrollable)
                    val nodes = panel.findObjects(By.pkg(QA).textContains("Clima durante la jornada"))
                    check(nodes.size <= 1) { "Ambiguous weather control" }
                    if (nodes.size == 1) {
                        var target = nodes.single()
                        var steps = 0
                        while (!target.isClickable && target != panel && steps++ < 10) {
                            val parent = requireNotNull(target.parent)
                            check(parent.applicationPackage == QA && panel.visibleBounds.contains(parent.visibleBounds))
                            target = parent
                        }
                        check(target.isClickable) { "Weather card has no clickable owned ancestor" }
                        click(target); opened = true
                    } else panel.scroll(Direction.DOWN, .6f)
                }
            }
            check(opened) { "Weather control not reachable in the owned detail panel" }
            await {
                var matches = false
                scenario!!.onActivity { activity ->
                    val weather = ViewModelProvider(activity)[WeatherViewModel::class.java].uiState.value
                    matches = weather.surface == WeatherSurface.SHIFT && weather.selectedShift?.id == shift.id &&
                        weather.selectedShift?.sourceObjectiveId == shift.sourceObjectiveId
                }
                matches
            }
            assertEquals(day.date, calendar().detailDate)
            fact("C030-abrir-clima-desde-fecha.activity_native", "EXACT", JSONObject().put("date", day.date.toString()).put("shift_uuid", shift.id.toString()).put("opening", it + 1))
            val close = device.findObjects(By.pkg(QA).text("Cerrar"))
            check(close.size == 1) { "Expected exactly one QA weather close action" }
            click(close.single())
            await {
                var closed = false
                scenario!!.onActivity { activity -> closed = ViewModelProvider(activity)[WeatherViewModel::class.java].uiState.value.surface == WeatherSurface.NONE }
                closed && calendar().detailDate == day.date
            }
            stableDetail(day.date)
            check(device.findObjects(By.pkg(QA).text("Editar día")).size == 1) { "Duplicate visible detail surface" }
            fact("C030-cerrar-retorna-mismo-dia.activity_native", "EXACT", JSONObject().put("date", day.date.toString()).put("opening", it + 1))
            fact("C034-clima-retorno-animacion-real.activity_native", "EXACT", JSONObject().put("date", day.date.toString()).put("opening", it + 1))
            device.pressBack(); await { calendar().detailDate == null }
        }
    }

    private fun calendar(): CalendarUiState {
        var value: CalendarUiState? = null
        scenario!!.onActivity { value = ViewModelProvider(it)[CalendarViewModel::class.java].uiState.value }
        return requireNotNull(value)
    }

    private fun goToMonth(target: YearMonth) {
        var steps = 0
        while (calendar().visibleMonth != target) {
            check(steps++ < 36) { "Bounded month preparation exhausted" }
            clickMonth(if (calendar().visibleMonth < target) 1 else -1)
        }
        awaitMonth(target)
    }

    private fun clickMonth(direction: Int) {
        val before = calendar().visibleMonth
        click(requireNotNull(device.findObject(By.pkg(QA).desc(if (direction < 0) "Mes anterior" else "Mes siguiente"))))
        awaitMonth(before.plusMonths(direction.toLong()))
        assertEquals(null, calendar().detailDate)
    }

    private fun awaitMonth(month: YearMonth) {
        val name = month.month.getDisplayName(TextStyle.FULL, LOCALE)
        val title = Pattern.compile("(?is).*" + Pattern.quote(name) + ".*" + month.year + ".*")
        await { calendar().visibleMonth == month && calendar().loadState == CalendarLoadState.CONTENT && device.hasObject(By.pkg(QA).text(title)) }
    }

    private fun openDate(date: LocalDate): Int {
        val before = requireNotNull(com.blackatsystems.miguardia.QaInstrumentationScope.calendarDateSelectionCount(context, date))
        val cells = device.findObjects(By.pkg(QA).desc(Stage12CalendarCellSelector.descriptionPattern(date)))
        Stage12CalendarCellSelector.requireUnique(cells.size)
        click(cells.single())
        await { calendar().detailDate == date && device.hasObject(By.pkg(QA).text("Editar día")) }
        val after = requireNotNull(com.blackatsystems.miguardia.QaInstrumentationScope.calendarDateSelectionCount(context, date))
        assertEquals("A native date tap must invoke its real UI callback exactly once", before + 1, after)
        return after - before
    }

    private fun stableDetail(date: LocalDate) {
        // Closing Weather may preserve the detail scroll position. Bring its own
        // controls back into view; visibility is not inferred from existence.
        var visible = device.hasObject(By.pkg(QA).text("Editar día"))
        repeat(8) {
            if (!visible) {
                assertQa(); assertEquals(date, calendar().detailDate)
                val panels = device.findObjects(By.pkg(QA).scrollable(true)).filter { !it.visibleBounds.isEmpty }
                check(panels.size == 1) { "Owned detail scroll container is ambiguous" }
                panels.single().scroll(Direction.UP, .6f)
                visible = device.hasObject(By.pkg(QA).text("Editar día"))
            }
        }
        check(visible) { "Detail edit action is not reachable after return" }
        val until = android.os.SystemClock.elapsedRealtime() + 1200
        while (android.os.SystemClock.elapsedRealtime() < until) {
            assertQa(); assertEquals(date, calendar().detailDate)
            assertTrue(device.hasObject(By.pkg(QA).text("Editar día")))
            android.os.SystemClock.sleep(100)
        }
    }

    private fun ownDetailPanel(): UiObject2 {
        var node = requireNotNull(device.findObject(By.pkg(QA).text("Editar día")))
        repeat(12) { if (node.isScrollable) return node; node = requireNotNull(node.parent) }
        error("Owned detail scroll container not found")
    }

    private fun click(node: UiObject2) { assertQa(); check(node.applicationPackage == QA); node.click(); assertQa() }
    private fun assertQa() {
        check(context.packageName == QA) { "ACTION_CONTEXT_NOT_QA" }
        check(device.isScreenOn) { "ACTION_SCREEN_OFF" }
        check(!(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) { "ACTION_KEYGUARD_LOCKED" }
        check(device.currentPackageName == QA) { "ACTION_FOREGROUND_NOT_QA" }
    }

    private fun await(predicate: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + 30_000
        while (true) {
            assertQa()
            if (predicate()) return
            check(android.os.SystemClock.elapsedRealtime() < end) { "Native QA state deadline" }
            android.os.SystemClock.sleep(50)
        }
    }

    private fun businessHash(): String {
        val snapshot = runBlocking { app.localDataStore.backups.capture() }
        val digest = MessageDigest.getInstance("SHA-256")
        val sink = object : OutputStream() { override fun write(b: Int) {} ; override fun write(b: ByteArray, off: Int, len: Int) {} }
        BackupPayloadCodec.writeDatabase(snapshot, DigestOutputStream(sink, digest))
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun fact(key: String, binding: String, observed: JSONObject) {
        facts.put(JSONObject().put("assertion_key", key).put("level", "ACTIVITY_NATIVE")
            .put("fixture_binding", binding).put("observed", observed).put("check", "PASS"))
    }

    private fun runCase(name: String, clockMode: String = "STAGE12_20261023", body: () -> Unit) {
        check(instrumentation is Stage12NormalQaRunner && context.packageName == QA)
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("stage12CalendarClock") == clockMode) { "Exact case calendar clock required" }
        val admission = Stage12ActivityAdmission(technicalOnly = false)
        (instrumentation as Stage12NormalQaRunner).beginReadiness(admission)
        admission.phase(Stage12ActivityAdmission.Phase.FILES)
        val uuid = requireNotNull(args.getString("stage12RunId")); require(UUID.fromString(uuid).toString() == uuid)
        val identity = JSONObject().put("run_uuid", uuid).put("qa_sha256", args.getString("stage12QaSha256"))
            .put("runner_sha256", args.getString("stage12RunnerSha256")).toString()
        var output: File? = null
        facts = JSONArray()
        val receipt = JSONObject().put("schema", "stage12-normal-native-v1").put("case", name).put("run_uuid", uuid)
            .put("qa_sha256", args.getString("stage12QaSha256")).put("runner_sha256", args.getString("stage12RunnerSha256"))
            .put("status", "INCOMPLETE").put("facts", facts).put("screenshots", false).put("global_settings_changed", false)
            .put("calendar_clock_mode", clockMode).put("calendar_zone", AppDefaults.zoneId().id)
            .put("system_zone", java.time.ZoneId.systemDefault().id)
            .put("calendar_fixture_instant", if (clockMode == "REAL") JSONObject.NULL else "2026-10-23T12:00:00Z")
            .put("animation_clock", "Android SystemClock real")
        var before: String? = null
        var primaryFailure: Throwable? = null
        try {
            output = Stage12NativeReceiptFiles.prepareOutput(context.filesDir, uuid, identity, name)
            admission.phase(Stage12ActivityAdmission.Phase.LAUNCH)
            val launchBefore = java.time.Instant.now()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            Stage12OwnActivityAdmission.awaitReady(scenario!!, context, device, admission)
            await { app.startupRecoveryGate.isReady && calendar().loadState == CalendarLoadState.CONTENT }
            val observed = calendar().referenceInstant
            val readyAfter = java.time.Instant.now()
            receipt.put("launch_before", launchBefore.toString()).put("ready_after", readyAfter.toString())
                .put("calendar_reference_instant", observed.toString())
                .put("calendar_reference_date", observed.atZone(AppDefaults.zoneId()).toLocalDate().toString())
            if (clockMode == "REAL") {
                assertTrue("Calendar reference must use the observed real clock", observed >= launchBefore && observed <= readyAfter)
            } else assertEquals(java.time.Instant.parse("2026-10-23T12:00:00Z"), observed)
            before = businessHash(); receipt.put("business_before", before)
            admission.phase(Stage12ActivityAdmission.Phase.BODY)
            body()
        } catch (failure: Throwable) {
            admission.failed(failure)
            primaryFailure = failure
            receipt.put("failure_class", failure.javaClass.simpleName)
            throw failure
        } finally {
            try {
                try {
                    admission.phase(Stage12ActivityAdmission.Phase.CLOSE)
                    scenario?.close(); scenario = null
                    receipt.put("activity_closed", true)
                } catch (failure: Throwable) {
                    receipt.put("activity_closed", false).put("status", "FAILED_ACTIVITY_CLOSE")
                        .put("close_failure_class", failure.javaClass.simpleName)
                    throw failure
                }
                admission.phase(Stage12ActivityAdmission.Phase.POSTCONDITION)
                if (before != null) {
                    val after = businessHash(); receipt.put("business_after", after).put("business_unchanged", before == after)
                    if (before != after) receipt.put("status", "FAILED_BUSINESS_CHANGE")
                    assertEquals("Read-only navigation changed QA business", before, after)
                } else receipt.put("business_after", JSONObject.NULL)
                if (before != null && !receipt.has("failure_class")) receipt.put("status", "COMPLETE")
            } catch (failure: Throwable) {
                admission.failed(failure)
                if (receipt.optString("status") == "INCOMPLETE") receipt.put("status", "FAILED_POSTCONDITION")
                receipt.put("postcondition_failure_class", failure.javaClass.simpleName)
                val primary = primaryFailure
                if (primary == null) {
                    primaryFailure = failure
                    throw failure
                }
                if (failure !== primary) primary.addSuppressed(failure)
            } finally {
                try {
                    admission.phase(Stage12ActivityAdmission.Phase.PUBLISH)
                    receipt.put("readiness", JSONObject(admission.wire()))
                    output?.let { Stage12NativeReceiptFiles.publish(it, receipt.toString(2), primaryFailure) }
                } catch (failure: Throwable) {
                    admission.failed(failure)
                    throw failure
                } finally { admission.finish(primaryFailure == null) }
            }
        }
    }

    companion object {
        private const val QA = "com.blackatsystems.miguardia.qa"
        private val LOCALE = Locale.forLanguageTag("es-AR")
        private fun fullDate(date: LocalDate) = "${date.dayOfMonth} de ${date.month.getDisplayName(TextStyle.FULL, LOCALE)} de ${date.year}"
    }
}
