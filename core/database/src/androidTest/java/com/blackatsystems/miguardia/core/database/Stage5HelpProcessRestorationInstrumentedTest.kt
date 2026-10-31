package com.blackatsystems.miguardia.core.database

import android.graphics.Rect
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Independent runner: QA is never instrumented while its background process is killed.
 * Uses the existing QA task and productive Help UI; no seed, reset, settings or network.
 * Raw shell/UI trees are not retained. Binary DB/WAL changes are inconclusive, not
 * proof of a business mutation: opening SQLite may legitimately checkpoint its WAL.
 */
@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
class Stage5HelpProcessRestorationInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private var stopped = false
    private var guardedTaskId: Int? = null

    @Test
    fun backgroundProcessDeathRestoresReplayAndClosedReplayDoesNotReenter() {
        assertEquals(RUNNER_PACKAGE, instrumentation.targetContext.packageName)
        val arguments = InstrumentationRegistry.getArguments()
        assertEquals("true", arguments.getString("stage5ProcessDeathAuthorized"))
        val expectedApk = requireNotNull(arguments.getString("expectedQaSha256"))
        require(expectedApk.matches(Regex("[0-9a-f]{64}")))
        val directory = File(instrumentation.targetContext.filesDir,
            "stage5-help-process-restoration/${UUID.randomUUID()}")
        check(!directory.exists() && directory.mkdirs())
        val reportFile = File(directory, "result.json")
        val report = JSONObject().put("status", "incomplete")
            .put("qa_package", QA).put("qa_expected_sha256", expectedApk)
            .put("db_reset", false).put("recents_ui_opened", false)
            .put("public_app_opened", false).put("raw_ui_or_shell_retained", false)
        var stage = "preflight"
        fun checkpoint(name: String) {
            stage = name
            report.put("last_stage", name)
            reportFile.writeText(report.toString(2))
            instrumentation.sendStatus(0, Bundle().apply {
                putString("stage5_process_stage", name)
                putString("stage5_process_receipt", directory.relativeTo(
                    instrumentation.targetContext.filesDir).path + "/result.json")
            })
        }
        checkpoint("preflight")
        try {
            val initialOwner = activeOwner(allowHome = true)
            assertEquals(expectedApk, installedQaHash())
            if (initialOwner == HOME) {
                check(shell("am start -W -n $QA_COMPONENT").exit == 0)
                guardedTaskId = qaTask().id
                await("QA foreground") { activeOwner(allowHome = true) == QA }
            }
            requireQa()
            guardedTaskId = qaTask().id
            val initialDatabase = databaseFingerprints()
            report.put("database_initial", initialDatabase.json())
            val initialOnboarding = fingerprint(ONBOARDING)
            report.put("onboarding_before_sha256", initialOnboarding ?: JSONObject.NULL)
            click("Abrir menú")
            click("Ayuda", allowOwnScroll = true)
            click("Repetir recorrido inicial", allowOwnScroll = true)
            requireLabels("Primeros pasos · 1 de 3", "Organizá tu trabajo")
            click("Siguiente")
            requireLabels("Primeros pasos · 2 de 3", "Entendé lo que viene y lo que hiciste")
            click("Siguiente")
            requireLabels("Primeros pasos · 3 de 3", "Tus datos quedan bajo tu control")
            click("Ver recorrido")
            for (index in 0..3) {
                requireLabels("Recorrido · ${index + 1} de 8")
                click("Siguiente")
            }
            requireLabels("Recorrido · 5 de 8", "Fotos del cronograma")
            checkpoint("replay_photos_ready")
            val first = restartSameBackgroundTask(report, "replay")
            requireLabels("Recorrido · 5 de 8", "Fotos del cronograma")
            checkpoint("replay_restored_in_same_task")
            click("Atrás")
            requireLabels("Recorrido · 4 de 8", "Detalle de cada día")
            click("Cerrar y volver a Ayuda")
            assertHelpWithoutReplay()
            checkpoint("replay_closed")
            val second = restartSameBackgroundTask(report, "closed")
            assertHelpWithoutReplay()
            assertEquals(first.taskId, second.taskId)
            checkpoint("closed_replay_did_not_reenter")
            click("Abrir menú")
            click("Calendario", allowOwnScroll = true)
            requireLabels("Mes anterior", "Mes siguiente")
            assertEquals(expectedApk, installedQaHash())
            val finalOnboarding = fingerprint(ONBOARDING)
            report.put("onboarding_after_sha256", finalOnboarding ?: JSONObject.NULL)
            assertEquals("Replay must not change onboarding completion", initialOnboarding, finalOnboarding)
            val finalDatabase = databaseFingerprints()
            report.put("database_final", finalDatabase.json())
            val binaryUnchanged = initialDatabase == finalDatabase &&
                first.before == first.after && second.before == second.after &&
                initialDatabase == first.before && first.before == second.after
            report.put("database_binary_unchanged", binaryUnchanged)
                .put("labor_integrity", if (binaryUnchanged) "BINARY_UNCHANGED" else
                    "INCONCLUSIVE_BINARY_CHANGE_CHECKPOINT_NOT_EXCLUDED")
            report.put("status", if (binaryUnchanged) "navigation_verified_binary_unchanged" else
                "navigation_verified_integrity_inconclusive")
            instrumentation.sendStatus(0, Bundle().apply {
                putString("stage5_process_result", report.getString("status"))
            })
        } finally {
            if (report.getString("status") == "incomplete") {
                report.put("failure_stage", if (stopped) "unexpected_focus_or_lock" else stage)
            }
            reportFile.writeText(report.toString(2))
        }
    }

    private fun restartSameBackgroundTask(report: JSONObject, label: String): Restart {
        requireQa()
        val task = qaTask()
        check(task.id == guardedTaskId) { "QA task changed before backgrounding" }
        check(task.state == "RESUMED") { "Expected one resumed QA MainActivity task" }
        val oldPid = requireNotNull(qaPid()) { "QA process is absent before backgrounding" }
        check(shell("input keyevent KEYCODE_HOME").exit == 0)
        await("Home with stopped QA") {
            activeOwner(allowHome = true) == HOME && qaTask().let {
                it.id == task.id && it.state == "STOPPED"
            }
        }
        val before = databaseFingerprints()
        check(activeOwner(allowHome = true) == HOME)
        check(shell("am kill --user 0 $QA").exit == 0)
        await("QA process absent") {
            activeOwner(allowHome = true) == HOME && qaPid() == null
        }
        val retained = qaTask()
        check(retained.id == task.id && retained.state != "RESUMED")
        check(activeOwner(allowHome = true) == HOME)
        check(shell("am task focus ${task.id}").exit == 0)
        await("Same QA task restored with new process") {
            val owner = activeOwner(allowHome = true)
            owner == QA && qaPid()?.let { it != oldPid } == true && qaTask().let {
                it.id == task.id && it.state == "RESUMED"
            }
        }
        val newPid = requireNotNull(qaPid())
        val after = databaseFingerprints()
        report.put(label, JSONObject().put("task_id", task.id).put("pid_before", oldPid)
            .put("pid_after", newPid).put("process_absence_observed", true)
            .put("same_task_restored", true).put("database_before", before.json())
            .put("database_after", after.json()))
        return Restart(task.id, before, after)
    }

    private fun assertHelpWithoutReplay() {
        requireLabels("Ayuda", "Repetir recorrido inicial")
        check(!hasLabel("Cerrar y volver a Ayuda") && !hasLabel("Recorrido · 5 de 8") &&
            !hasLabel("Recorrido · 4 de 8") && !hasLabel("Primeros pasos · 1 de 3")) {
            "Closed replay reentered after navigation or process restoration"
        }
    }

    private fun requireLabels(vararg labels: String) = await("Expected QA labels") {
        labels.all(::hasLabel)
    }

    private fun hasLabel(label: String): Boolean {
        if (activeOwner(allowHome = false) != QA) return false
        val root = automation.rootInActiveWindow ?: run {
            activeOwner(allowHome = false)
            return false
        }
        try {
            requireNodeOwner(root)
            var found = false
            visit(root) { node -> if (node.isVisibleToUser && exactLabel(node, label)) found = true }
            return activeOwner(allowHome = false) == QA && found
        } finally { root.recycle() }
    }

    private fun click(label: String, allowOwnScroll: Boolean = false) {
        var scrolls = 0
        var showAttempts = 0
        await("one visible exact QA action") {
            if (activeOwner(allowHome = false) != QA) return@await false
            val root = automation.rootInActiveWindow ?: run {
                activeOwner(allowHome = false)
                return@await false
            }
            val candidates = mutableListOf<AccessibilityNodeInfo>()
            try {
                visit(root) { node ->
                    if (exactLabel(node, label)) clickableAncestor(node)?.let { candidate ->
                        if (candidates.none { it == candidate }) candidates += candidate else candidate.recycle()
                    }
                }
                check(candidates.size <= 1) { "Ambiguous exact QA action" }
                candidates.singleOrNull()?.let { target ->
                    if (!target.isVisibleToUser) {
                        if (allowOwnScroll && showAttempts < 2) {
                            showAttempts++
                            if (activeOwner(allowHome = false) != QA) return@await false
                            check(target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id))
                        }
                    } else {
                        val bounds = Rect().also(target::getBoundsInScreen)
                        check(!bounds.isEmpty)
                        if (activeOwner(allowHome = false) != QA) return@await false
                        check(target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            "Exact QA action was rejected"
                        }
                        return@await true
                    }
                }
                if (candidates.isEmpty() && allowOwnScroll && scrolls < MAX_SCROLLS) {
                    val scrollables = mutableListOf<AccessibilityNodeInfo>()
                    try {
                        visit(root) { node ->
                            if (node.isVisibleToUser && node.isEnabled && node.isScrollable &&
                                node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }) {
                                scrollables += AccessibilityNodeInfo.obtain(node)
                            }
                        }
                        check(scrollables.size <= 1) { "Own QA scroll container is ambiguous" }
                        scrollables.singleOrNull()?.let { container ->
                            if (activeOwner(allowHome = false) != QA) return@await false
                            scrolls++
                            if (!container.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                                scrolls = MAX_SCROLLS
                            }
                        }
                    } finally { scrollables.forEach { it.recycle() } }
                }
                false
            } finally { candidates.forEach { it.recycle() }; root.recycle() }
        }
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        repeat(10) {
            val owned = current ?: return null
            requireNodeOwner(owned)
            if (owned.isClickable && owned.isEnabled) return owned
            current = owned.parent
            owned.recycle()
        }
        current?.recycle()
        return null
    }

    private fun exactLabel(node: AccessibilityNodeInfo, label: String): Boolean {
        requireNodeOwner(node)
        return listOf(node.text?.toString(), node.contentDescription?.toString()).any { value ->
            value?.lineSequence()?.any { it.trim() == label } == true
        }
    }

    private fun visit(root: AccessibilityNodeInfo, action: (AccessibilityNodeInfo) -> Unit) {
        var count = 0
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            check(depth <= 64 && ++count <= 4_000) { "QA accessibility traversal exceeded bounds" }
            requireNodeOwner(node)
            action(node)
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try { walk(child, depth + 1) } finally { child.recycle() }
            }
        }
        walk(root, 0)
    }

    private fun requireNodeOwner(node: AccessibilityNodeInfo) {
        if (node.packageName?.toString() != QA) stop()
    }

    private fun requireQa() { check(activeOwner(allowHome = false) == QA) }

    private fun activeOwner(allowHome: Boolean): String? {
        check(!stopped) { "Focus guard already stopped this run" }
        val policy = shell("dumpsys window policy | grep -E 'isStatusBarKeyguard|showing=|inputRestricted='")
        check(policy.exit == 0)
        if (Regex("isStatusBarKeyguard=true|showing=true|inputRestricted=true").containsMatchIn(policy.output)) stop()
        val metadata = shell("dumpsys window | grep -E 'mCurrentFocus=|mFocusedApp='")
        check(metadata.exit == 0)
        fun owner(field: String): String? {
            val lines = metadata.output.lineSequence().filter { it.contains("$field=") }.toList()
            check(lines.size == 1) { "Focus metadata is absent or ambiguous" }
            return Regex("\\bu\\d+\\s+([A-Za-z0-9_.]+)/").find(lines.single())?.groupValues?.get(1)
        }
        val current = owner("mCurrentFocus")
        val focused = owner("mFocusedApp")
        fun allowed(value: String?) = value == QA || (allowHome && value == HOME)
        // Directly identified foreign focus always stops, even if the accessibility root is stale QA.
        if (current != null && !allowed(current)) stop()
        if (focused != null && !allowed(focused)) stop()
        fun positiveTransition(): Boolean = focused != null && allowed(focused) &&
            (current == null || current == focused) && guardedTaskId?.let { qaTask().id == it } == true
        val root = automation.rootInActiveWindow ?: return if (positiveTransition()) null else stop()
        return try {
            val rootOwner = root.packageName?.toString()
            if (!allowed(rootOwner)) stop()
            if (current == rootOwner || (current == null && focused == rootOwner && positiveTransition())) {
                rootOwner
            } else if (positiveTransition()) {
                null // Known same-task transition; never input until metadata and root agree.
            } else stop()
        } finally { root.recycle() }
    }

    private fun stop(): Nothing {
        stopped = true
        error("Unexpected focus or lock; stopped without reading or interacting with another app")
    }

    private fun qaTask(): QaTask {
        val result = shell("dumpsys activity -p $QA activities")
        check(result.exit == 0)
        val component = "${Regex.escape(QA)}/(?:com\\.blackatsystems\\.miguardia\\.MainActivity|\\.MainActivity)"
        val records = Regex("(?m)^.*Hist\\s+#\\d+: ActivityRecord\\{[^\\n]*\\bu(\\d+)\\s+$component\\s+t(\\d+)[^\\n]*\\}")
            .findAll(result.output).toList()
        check(records.size == 1) { "Expected exactly one filtered QA task activity" }
        val record = records.single()
        check(record.groupValues[1] == "0") { "QA task belongs to an unexpected Android user" }
        val following = result.output.substring(record.range.last + 1)
        val state = Regex("\\bstate=([A-Z_]+)\\b").find(following)?.groupValues?.get(1)
            ?: error("Filtered QA activity state is unavailable")
        return QaTask(record.groupValues[2].toInt().also { check(it > 0) }, state)
    }

    private fun qaPid(): Int? {
        val result = shell("pidof $QA")
        if (result.exit == 1 && result.output.isBlank()) return null
        check(result.exit == 0 && result.output.trim().matches(Regex("[1-9][0-9]*"))) {
            "Expected one exact QA process"
        }
        return result.output.trim().toInt()
    }

    private fun installedQaHash(): String {
        val result = shell("pm path $QA")
        check(result.exit == 0)
        val path = result.output.trim().removePrefix("package:")
        check(path.matches(Regex("/data/app/[A-Za-z0-9_~=/+.-]+\\.apk")))
        val digest = shell("sha256sum '$path'")
        check(digest.exit == 0)
        return digest.output.trim().substringBefore(' ').also { check(it.matches(Regex("[0-9a-f]{64}"))) }
    }

    private fun databaseFingerprints(): DatabaseFingerprints {
        val before = DatabaseFingerprints(requireNotNull(fingerprint(DB)), fingerprint(WAL))
        val after = DatabaseFingerprints(requireNotNull(fingerprint(DB)), fingerprint(WAL))
        check(before == after) { "QA database files changed during fingerprint collection" }
        return before
    }

    private fun fingerprint(path: String): String? {
        require(path in setOf(DB, WAL, ONBOARDING))
        val result = shell("run-as $QA sh -c 'if [ -f $path ]; then sha256sum $path; else printf ABSENT; fi'")
        check(result.exit == 0)
        if (result.output.trim() == "ABSENT") return null
        val match = Regex("([0-9a-f]{64})\\s+${Regex.escape(path)}").matchEntire(result.output.trim())
            ?: error("Expected one fixed QA file fingerprint")
        return match.groupValues[1]
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + WAIT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            check(!stopped)
            if (condition()) return
            SystemClock.sleep(POLL_MS)
        }
        error("Timed out waiting for $description")
    }

    private fun shell(command: String): ShellResult {
        // Only fixed call sites or validated task integers/APK paths reach this helper.
        val descriptors = automation.executeShellCommandRw("sh")
        ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1]).use { input ->
            input.write(("$command\nqa_command_status=${'$'}?\n" +
                "printf '\\nSTAGE5_EXIT=%s\\n' \"${'$'}qa_command_status\"\nexit\n").toByteArray())
        }
        val raw = ParcelFileDescriptor.AutoCloseInputStream(descriptors[0]).use { input ->
            input.readNBytes(MAX_SHELL_BYTES + 1).also { check(it.size <= MAX_SHELL_BYTES) }
        }.toString(Charsets.UTF_8)
        val marker = Regex("\\nSTAGE5_EXIT=(\\d+)\\r?\\n?$").find(raw)
            ?: error("QA shell exit marker is unavailable")
        return ShellResult(raw.substring(0, marker.range.first), marker.groupValues[1].toInt())
    }

    private data class QaTask(val id: Int, val state: String)
    private data class ShellResult(val output: String, val exit: Int)
    private data class DatabaseFingerprints(val database: String, val wal: String?) {
        fun json() = JSONObject().put("database_sha256", database).put("wal_sha256", wal ?: JSONObject.NULL)
    }
    private data class Restart(val taskId: Int, val before: DatabaseFingerprints, val after: DatabaseFingerprints)

    private companion object {
        const val QA = "com.blackatsystems.miguardia.qa"
        const val QA_COMPONENT = "$QA/com.blackatsystems.miguardia.MainActivity"
        const val RUNNER_PACKAGE = "com.blackatsystems.miguardia.core.database.test"
        const val HOME = "com.sec.android.app.launcher"
        const val DB = "databases/miguardia-v2.db"
        const val WAL = "$DB-wal"
        const val ONBOARDING = "files/datastore/onboarding.preferences_pb"
        const val WAIT_MS = 15_000L
        const val POLL_MS = 50L
        const val MAX_SCROLLS = 8
        const val MAX_SHELL_BYTES = 262_144
    }
}
