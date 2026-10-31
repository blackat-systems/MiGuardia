package com.blackatsystems.miguardia.core.database

import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Independent runner: stopping QA must not stop this instrumentation process.
 * Requires the freshly verified synthetic Stage4 cold seed and explicit opt-in.
 * Reads only QA accessibility nodes. Never dumps windows, captures pixels, seeds
 * data, changes display settings or reads the public application's sandbox.
 * Latency is an observation upper bound after am start -W; it includes shell
 * launch, accessibility traversal and 50 ms polling;
 * it measures process-cold content readiness, not storage-cold startup or FPS.
 */
@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
class Stage5ColdCalendarReadinessInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val args get() = InstrumentationRegistry.getArguments()
    private var stoppedForFocusLoss = false

    @Test fun twelveProcessColdOpeningsReachSeededMonthlyContent() {
        assertTrue("This dedicated cold probe requires API 36 or newer", Build.VERSION.SDK_INT >= 36)
        assertEquals(RUNNER_PACKAGE, instrumentation.targetContext.packageName)
        assertEquals("true", args.getString("stage5ColdAuthorized"))
        val phase = requireNotNull(args.getString("stage5ColdPhase"))
        require(phase in setOf("baseline", "candidate"))
        val month = YearMonth.parse(requireNotNull(args.getString("stage5ColdMonth")))
        val expectedSeedHash = requireNotNull(args.getString("stage5ColdSeedSha256"))
        require(expectedSeedHash.matches(Regex("[0-9a-f]{64}")))
        val suppliedWidth = args.getString("stage5ColdExpectedWidthPx")?.toInt()
        val suppliedHeight = args.getString("stage5ColdExpectedHeightPx")?.toInt()
        require((suppliedWidth == null) == (suppliedHeight == null))
        if (phase == "candidate") {
            require(suppliedWidth != null && suppliedHeight != null) {
                "Candidate must supply the observed baseline QA root dimensions"
            }
        }
        if (suppliedWidth != null) require(suppliedWidth > 0 && suppliedHeight!! > suppliedWidth)
        val runId = args.getString("stage5ColdRunId") ?: SystemClock.elapsedRealtimeNanos().toString()
        require(runId.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        val directory = File(instrumentation.targetContext.filesDir, "stage5-cold-readiness/$runId")
        check(!directory.exists() && directory.mkdirs()) { "Cold evidence directory already exists or is unavailable" }
        val reportFile = File(directory, "result.json")
        val samples = JSONArray()
        val report = JSONObject()
            .put("schema", 1).put("phase", phase).put("status", "incomplete")
            .put("metric", "process_cold_seeded_month_content_ready")
            .put("observation_upper_bound_after_android_launch_wait", true)
            .put("includes_shell_accessibility_and_polling", true)
            .put("poll_interval_ms", POLL_MS).put("timeout_ms", TIMEOUT_MS)
            .put("anchor_month", month.toString()).put("initial_database_sha256", expectedSeedHash)
            .put("theme", "LIGHT").put("internal_zoom_percent", 100)
            .put("expected_samples", SAMPLE_COUNT).put("samples", samples)
            .put("screenshots_or_ui_dumps", false)
        var failureCategory = "seed_validation"
        try {
            assertSafeActivePackage(allowLauncher = true, allowAbsent = false)
            val seedBytes = readSeedBytes()
            val seed = JSONObject(seedBytes.toString(Charsets.UTF_8))
            assertEquals(expectedSeedHash, seed.getString("initial_database_sha256"))
            assertEquals(month.toString(), seed.getString("anchor_month"))
            assertEquals(240, seed.getInt("initial_shifts"))
            assertEquals("LIGHT", seed.getString("theme"))
            assertEquals(100, seed.getInt("internal_zoom_percent"))
            val seedFileHash = sha256(seedBytes)
            report.put("seed_file_sha256", seedFileHash)
            val oracle = CalendarOracle(month)
            assertTrue("Synthetic seed must contain shifts in the observed month", oracle.shiftFragments.isNotEmpty())
            var referenceWidth = suppliedWidth
            var referenceHeight = suppliedHeight
            repeat(SAMPLE_COUNT) { iteration ->
                failureCategory = "prelaunch_focus"
                assertSafeActivePackage(allowLauncher = true, allowAbsent = false)
                assertEquals(seedFileHash, sha256(readSeedBytes()))
                failureCategory = "process_absence"
                check(shell("am force-stop $QA_PACKAGE").exit == 0) { "QA force-stop failed" }
                val process = shell("pidof $QA_PACKAGE")
                assertEquals("QA process must be absent before launch", 1, process.exit)
                assertTrue("QA process must be absent before launch", process.output.isBlank())
                failureCategory = "launch"
                val started = SystemClock.elapsedRealtimeNanos()
                val launch = shell("am start -W -n $QA_COMPONENT")
                check(launch.exit == 0 && Regex("(?m)^Status: ok\\s*$").containsMatchIn(launch.output)) {
                    "Android did not report a successful QA launch"
                }
                val totalTime = Regex("(?m)^TotalTime:\\s*(\\d+)\\s*$").find(launch.output)
                    ?.groupValues?.get(1)?.toLong() ?: error("Android TotalTime is missing")
                failureCategory = "content_readiness"
                val ready = awaitCalendar(oracle, started) { observation ->
                    report.put("last_observation", observation)
                }
                assertTrue("Cold QA window must remain portrait", ready.height > ready.width)
                if (referenceWidth == null) {
                    referenceWidth = ready.width
                    referenceHeight = ready.height
                }
                assertEquals("Cold QA root width changed", referenceWidth, ready.width)
                assertEquals("Cold QA root height changed", referenceHeight, ready.height)
                samples.put(JSONObject().put("iteration_zero_based", iteration)
                    .put("pidof_exit_before", process.exit).put("android_total_ms", totalTime)
                    .put("content_ready_ms", ready.elapsedNanos / 1_000_000.0)
                    .put("polls", ready.polls).put("root_width_px", ready.width)
                    .put("root_height_px", ready.height).put("orientation", "portrait")
                    .put("observed_dates", ready.observedDates)
                    .put("expected_dates", month.lengthOfMonth())
                    .put("seeded_shift_dates", ready.seededShiftDates))
                reportFile.writeText(report.toString(2))
            }
            failureCategory = "postrun_seed_validation"
            assertSafeActivePackage(allowLauncher = false, allowAbsent = false)
            assertEquals(seedFileHash, sha256(readSeedBytes()))
            assertEquals(SAMPLE_COUNT, samples.length())
            report.put("root_width_px", referenceWidth).put("root_height_px", referenceHeight)
                .put("status", "completed")
        } finally {
            if (report.getString("status") != "completed") {
                report.put("failure_category", if (stoppedForFocusLoss) "unexpected_focus" else failureCategory)
            }
            reportFile.writeText(report.toString(2))
        }
    }

    private fun awaitCalendar(oracle: CalendarOracle, started: Long, record: (JSONObject) -> Unit): ReadyWindow {
        var polls = 0
        while (SystemClock.elapsedRealtimeNanos() - started < TIMEOUT_MS * 1_000_000) {
            check(!stoppedForFocusLoss) { "Cold QA focus guard already stopped this run" }
            polls++
            val root = automation.rootInActiveWindow
            if (root == null) {
                SystemClock.sleep(POLL_MS)
                continue
            }
            try {
                val activePackage = root.packageName?.toString()
                if (activePackage == LAUNCHER_PACKAGE) {
                    // Launcher metadata only; never inspect its contents.
                    SystemClock.sleep(POLL_MS)
                    continue
                }
                requireQaPackage(activePackage)
                val bounds = Rect().also(root::getBoundsInScreen)
                val observation = observeQaTree(root, oracle)
                record(JSONObject().put("polls", polls).put("month_title_matches", observation.monthTitle)
                    .put("observed_dates", observation.dates.size)
                    .put("expected_dates", oracle.datePrefixes.size)
                    .put("seeded_shift_dates", observation.shiftDates.size)
                    .put("expected_seeded_shift_dates", oracle.shiftFragments.size)
                    .put("root_width_px", bounds.width()).put("root_height_px", bounds.height()))
                assertSafeActivePackage(allowLauncher = false, allowAbsent = true)
                if (observation.ready && bounds.width() > 0 && bounds.height() > 0) {
                    // The guard above may have seen a transient missing root. A
                    // second QA metadata check is required before accepting data.
                    if (assertSafeActivePackage(allowLauncher = false, allowAbsent = true)) {
                        return ReadyWindow(SystemClock.elapsedRealtimeNanos() - started, polls,
                            bounds.width(), bounds.height(), observation.dates.size,
                            observation.shiftDates.size)
                    }
                }
            } finally {
                root.recycle()
            }
            SystemClock.sleep(POLL_MS)
        }
        error("Seeded monthly QA content did not become ready within the declared timeout")
    }

    private fun observeQaTree(root: AccessibilityNodeInfo, oracle: CalendarOracle): Observation {
        val observation = Observation(oracle)
        var visited = 0
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            check(depth <= 64 && ++visited <= 4_000) { "QA accessibility tree exceeded the bounded traversal" }
            requireQaPackage(node.packageName?.toString())
            // No text or description is touched before checking this node's owner.
            if (node.text?.toString() == oracle.monthTitle) observation.monthTitle = true
            val description = node.contentDescription?.toString()
            if (node.isClickable && node.isEnabled && description != null) {
                val datePrefix = description.substringBefore(',')
                if (datePrefix in oracle.datePrefixes) {
                    observation.dates += datePrefix
                    val fragment = oracle.shiftFragments[datePrefix]
                    if (fragment != null && description.contains(fragment)) observation.shiftDates += datePrefix
                }
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try { visit(child, depth + 1) } finally { child.recycle() }
            }
        }
        visit(root, 0)
        return observation
    }

    private fun assertSafeActivePackage(allowLauncher: Boolean, allowAbsent: Boolean): Boolean {
        check(!stoppedForFocusLoss) { "Cold QA focus guard already stopped this run" }
        val root = automation.rootInActiveWindow
        if (root == null) {
            if (allowAbsent) return false
            stoppedForFocusLoss = true
            error("QA focus is unavailable; stopped without reading another app")
        }
        return try {
            val owner = root.packageName?.toString()
            if (owner == QA_PACKAGE) true
            else if (allowLauncher && owner == LAUNCHER_PACKAGE) false
            else requireQaPackage(owner).let { true }
        } finally { root.recycle() }
    }

    private fun requireQaPackage(owner: String?) {
        if (owner != QA_PACKAGE) {
            stoppedForFocusLoss = true
            error("Unexpected active package; stopped without reading or capturing another app")
        }
    }

    private fun readSeedBytes(): ByteArray {
        val read = shell("run-as $QA_PACKAGE cat files/stage4/cold-seed.json")
        check(read.exit == 0) { "The synthetic cold seed marker is unavailable" }
        return read.output.toByteArray(Charsets.UTF_8)
    }

    private fun shell(command: String): ShellResult {
        // Call sites contain fixed QA-only commands. No operator-supplied string
        // is interpolated into shell text, and raw output is never logged.
        // executeShellCommand(String) uses Runtime.exec rather than parsing
        // shell operators. Feed a fixed script to an explicit shell's stdin.
        val descriptors = automation.executeShellCommandRw("sh")
        android.os.ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1]).use { input ->
            input.write(("$command\nqa_command_status=${'$'}?\n" +
                "printf '\\nSTAGE5_EXIT=%s\\n' \"${'$'}qa_command_status\"\nexit\n").toByteArray(Charsets.UTF_8))
        }
        val bytes = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptors[0]).use { input ->
            val buffer = input.readNBytes(65_537)
            check(buffer.size <= 65_536) { "QA shell response exceeded its bound" }
            buffer
        }
        val raw = bytes.toString(Charsets.UTF_8)
        val match = Regex("\\nSTAGE5_EXIT=(\\d+)\\r?\\n?$").find(raw)
            ?: error("QA shell exit status is missing")
        return ShellResult(raw.substring(0, match.range.first), match.groupValues[1].toInt())
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private class CalendarOracle(val month: YearMonth) {
        private val locale = Locale.forLanguageTag("es-AR")
        private val formatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", locale)
        private fun prefix(day: java.time.LocalDate): String = day.format(formatter)
            .replaceFirstChar { it.titlecase(locale) }
        val monthTitle = month.month.getDisplayName(TextStyle.FULL, locale)
            .replaceFirstChar { it.titlecase(locale) } + " de ${month.year}"
        val datePrefixes = (1..month.lengthOfMonth()).map { prefix(month.atDay(it)) }.toSet()
        val shiftFragments: Map<String, String> = (0 until 240).mapNotNull { index ->
            val date = month.minusMonths(11).atDay(1).plusDays(index.toLong() * 365 / 240)
            if (YearMonth.from(date) != month) null
            else prefix(date) to if (index % 3 == 0) "QAS de 19:00 a 07:00" else "QAS de 08:00 a 16:00"
        }.toMap()
    }

    private class Observation(private val oracle: CalendarOracle) {
        var monthTitle = false
        val dates = mutableSetOf<String>()
        val shiftDates = mutableSetOf<String>()
        val ready get() = monthTitle && dates == oracle.datePrefixes && shiftDates == oracle.shiftFragments.keys
    }
    private data class ReadyWindow(val elapsedNanos: Long, val polls: Int, val width: Int,
        val height: Int, val observedDates: Int, val seededShiftDates: Int)
    private data class ShellResult(val output: String, val exit: Int)

    private companion object {
        const val QA_PACKAGE = "com.blackatsystems.miguardia.qa"
        const val QA_COMPONENT = "$QA_PACKAGE/com.blackatsystems.miguardia.MainActivity"
        const val RUNNER_PACKAGE = "com.blackatsystems.miguardia.core.database.test"
        const val LAUNCHER_PACKAGE = "com.sec.android.app.launcher"
        const val SAMPLE_COUNT = 12
        const val POLL_MS = 50L
        const val TIMEOUT_MS = 30_000L
    }
}
