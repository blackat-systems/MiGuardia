package com.blackatsystems.miguardia.stage12

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.blackatsystems.miguardia.QaInstrumentationContext
import com.blackatsystems.miguardia.QaInstrumentationScope
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Normal use of QA explicitly authorized by Joaquin; not a neutral/isolated bootstrap. */
class Stage12NormalQaRunner : AndroidJUnitRunner() {
    private var admitted = false
    private lateinit var expected: Set<String>
    private lateinit var runId: String
    private lateinit var qaHash: String
    private lateinit var runnerHash: String
    private lateinit var counts: Stage12NormalQaCounts
    private var finished = false
    private var calendarClockMode = "REAL"
    private var admissionOnly = false
    private var readiness: Stage12ActivityAdmission? = null
    private var sanitizedReadinessFailure = false
    private var lastSafeReadinessFailure: Stage12ActivityAdmission? = null
    private var summaryReport: Stage12SummaryObservation? = null

    private fun runtimeTarget() = Stage12RuntimeTarget.identify(
        android.os.Build.VERSION.SDK_INT, android.os.Build.MODEL, android.os.Build.MANUFACTURER,
        android.os.Build.ID, android.os.Build.FINGERPRINT, android.os.Build.SUPPORTED_ABIS.firstOrNull(),
    )

    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application {
        check(Stage12BuildPins.ENABLED)
        runtimeTarget() // Identify both explicit targets before Application construction.
        check(className == "com.blackatsystems.miguardia.MiGuardiaApplication")
        verify(context, QA, Stage12BuildPins.QA_SHA256)
        @Suppress("DEPRECATION")
        val metadata = context.packageManager.getApplicationInfo(QA, PackageManager.GET_META_DATA).metaData
        check(metadata?.getBoolean("com.blackatsystems.miguardia.STAGE12_HARNESS", false) != true) {
            "Normal QA runner cannot bypass the dedicated harness bootstrap"
        }
        // Ordinary providers/Application may run. No received-data immutability claim.
        return super.newApplication(cl, className, context)
    }

    override fun onCreate(arguments: Bundle) {
        check(!admitted)
        val required = setOf("class", "stage12NormalUse", "stage12RunId", "stage12QaSha256", "stage12RunnerSha256")
        require(arguments.keySet() == required || arguments.keySet() == required + "stage12CalendarClock" ||
            arguments.keySet() == required + setOf("stage12CalendarClock", Stage12SummaryObservation.ARGUMENT))
        require(arguments.getString("stage12NormalUse") == "NORMAL_QA_AUTHORIZED_20261024")
        runId = requireNotNull(arguments.getString("stage12RunId"))
        require(UUID.fromString(runId).toString() == runId)
        val selectors = requireNotNull(arguments.getString("class")).split(',')
        require(selectors.isNotEmpty() && selectors.size <= 256 && selectors.size == selectors.toSet().size)
        expected = selectors.toSet()
        counts = Stage12NormalQaCounts(expected)
        qaHash = requireNotNull(arguments.getString("stage12QaSha256"))
        runnerHash = requireNotNull(arguments.getString("stage12RunnerSha256"))
        check(qaHash == Stage12BuildPins.QA_SHA256)
        verify(targetContext, QA, qaHash)
        verify(context, "$QA.test", runnerHash)
        calendarClockMode = if (arguments.containsKey("stage12CalendarClock"))
            requireNotNull(arguments.getString("stage12CalendarClock")) else "REAL"
        Stage12RuntimeTarget.requireSelection(runtimeTarget(), selectors, calendarClockMode,
            arguments.containsKey(Stage12SummaryObservation.ARGUMENT), arguments.containsKey("stage12CalendarClock"))
        admissionOnly = Stage12AdmissionProjection.requireScope(selectors, Stage12NormalQaSelectors.allowed,
            Stage12NormalQaSelectors.activityAdmissionSelector, calendarClockMode)
        if (arguments.containsKey(Stage12SummaryObservation.ARGUMENT)) {
            Stage12SummaryObservation.requireSelection(selectors, calendarClockMode,
                requireNotNull(arguments.getString(Stage12SummaryObservation.ARGUMENT)))
            check(!admissionOnly)
            summaryReport = Stage12SummaryObservation()
        }
        if (admissionOnly) readiness = Stage12ActivityAdmission(true)
        val fixtureClock = Stage12CalendarFixtureClock.forMode(calendarClockMode,
            com.blackatsystems.miguardia.core.domain.AppDefaults.zoneId())
        QaInstrumentationScope.install(this, qaHash, runnerHash, object : QaInstrumentationContext {
            private val selections = java.util.concurrent.ConcurrentHashMap<java.time.LocalDate, java.util.concurrent.atomic.AtomicInteger>()
            override fun wrapActivity(base: Context): Context = base
            override fun suppressed(operation: String) = error("Normal QA does not claim suppressed effects")
            override fun calendarClock(): java.time.Clock? = fixtureClock
            override fun calendarDateSelected(date: java.time.LocalDate) {
                selections.computeIfAbsent(date) { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet()
            }
            override fun calendarDateSelectionCount(date: java.time.LocalDate): Int = selections[date]?.get() ?: 0
        }, "normal-qa")
        admitted = true
        super.onCreate(arguments)
    }

    override fun newActivity(cl: ClassLoader, className: String, intent: Intent): Activity {
        check(admitted)
        check(intent.component == ComponentName(QA, className)) { "Activity component differs from the owned manifest entry" }
        @Suppress("DEPRECATION")
        val info = targetContext.packageManager.getActivityInfo(ComponentName(QA, className), 0)
        check(info.packageName == QA && info.applicationInfo.uid == targetContext.applicationInfo.uid)
        return super.newActivity(cl, className, intent)
    }

    @Synchronized override fun sendStatus(resultCode: Int, results: Bundle) {
        check(!finished) { "Status after runner finish" }
        if (admitted) {
            val id = "${results.getString("class")}#${results.getString("test")}"
            counts.observe(id, results.getInt("numtests", -1), resultCode, !results.getString("stack").isNullOrBlank())
            if (resultCode == 1 && !admissionOnly) readiness = null
        }
        summaryReport?.let { report ->
            if (resultCode !in setOf(0, 1)) report.runnerFailed()
            super.sendStatus(resultCode, safeBundle(report.statusFields(results.getInt("numtests", -1),
                resultCode, !results.getString("stack").isNullOrBlank())))
            return
        }
        val selected = if (::expected.isInitialized) expected else emptySet()
        val selector = if (admissionOnly) Stage12NormalQaSelectors.activityAdmissionSelector
            else Stage12AdmissionProjection.statusSelector("${results.getString("class")}#${results.getString("test")}", selected, admitted)
        val x015 = Stage12AdmissionProjection.X015_SELECTOR in selected
        val sanitize = Stage12AdmissionProjection.needsSafeStatus(admissionOnly, selector, readiness, selected, admitted)
        val report = readiness ?: if (sanitize) Stage12ActivityAdmission(admissionOnly).apply {
            if (x015) x015Step(Stage12ActivityAdmission.X015Step.UNKNOWN)
        } else null
        if (report != null && resultCode !in setOf(0, 1)) report.failed(Stage12ActivityAdmission.Failure.OTHER)
        if (sanitize && report != null) {
            if (resultCode !in setOf(0, 1)) {
                sanitizedReadinessFailure = true
                lastSafeReadinessFailure = report
            }
            super.sendStatus(resultCode, safeBundle(Stage12AdmissionProjection.status(selector,
                mapOf("numtests" to results.getInt("numtests", -1), "stack" to results.getString("stack")), resultCode, report)))
        } else super.sendStatus(resultCode, results)
    }

    @Synchronized internal fun beginReadiness(report: Stage12ActivityAdmission) {
        check(admitted && report.technicalOnly == admissionOnly)
        readiness = report
    }

    @Synchronized internal fun summaryObservation(): Stage12SummaryObservation? {
        check(admitted)
        return summaryReport
    }

    @Synchronized override fun finish(resultCode: Int, results: Bundle) {
        check(!finished) { "Runner finish repeated" }
        finished = true
        val report = Stage12AdmissionProjection.terminalReport(admissionOnly, sanitizedReadinessFailure,
            readiness, lastSafeReadinessFailure)
        val selected = if (::expected.isInitialized) expected else emptySet()
        val summary = summaryReport
        val terminal = when {
            summary != null -> safeBundle(mapOf("stream" to summary.safeSummary(), "stage12SummaryObservation" to summary.wire()))
            Stage12AdmissionProjection.needsSafeTerminal(admissionOnly, sanitizedReadinessFailure, selected, admitted) ->
                safeBundle(Stage12AdmissionProjection.terminal(report ?: Stage12ActivityAdmission(admissionOnly).apply {
                    if (Stage12AdmissionProjection.X015_SELECTOR in selected) x015Step(Stage12ActivityAdmission.X015Step.UNKNOWN)
                    if (!admitted) failed(Stage12ActivityAdmission.Failure.OTHER)
                }))
            else -> Bundle(results)
        }
        if (report != null) terminal.putString("stage12ActivityAdmission", report.wire())
        val snapshot = if (admitted) counts.finish(resultCode == Activity.RESULT_OK) else null
        val complete = snapshot?.complete == true
        terminal.putString("stage12NormalStatus", if (complete) "COMPLETE" else "INCOMPLETE")
        if (admitted) {
            terminal.putString("stage12RunId", runId)
            terminal.putString("stage12QaSha256", qaHash)
            terminal.putString("stage12RunnerSha256", runnerHash)
            terminal.putString("stage12CalendarClock", calendarClockMode)
            terminal.putString("stage12MethodCounts", JSONObject().put("expected", snapshot!!.expected)
                .put("started", snapshot.started).put("passed", snapshot.passed).put("failed", snapshot.failed)
                .put("skipped", snapshot.skipped).put("malformed", snapshot.malformed).toString())
        }
        super.finish(resultCode, terminal)
    }

    private fun safeBundle(fields: Map<String, Any>) = Bundle().apply {
        fields.forEach { (key, value) -> when (value) {
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            else -> error("Unsupported safe metadata type")
        } }
    }

    @Suppress("DEPRECATION")
    private fun verify(context: Context, packageName: String, hash: String) {
        check(context.packageName == packageName && hash.matches(Regex("[0-9a-f]{64}")))
        check(context.applicationInfo.splitSourceDirs.isNullOrEmpty()) { "Universal APK required" }
        check(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        check(stage12Hash(File(context.applicationInfo.sourceDir)) == hash)
        val signatures = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners.orEmpty()
        check(signatures.size == 1 && MessageDigest.getInstance("SHA-256").digest(signatures.single().toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) } == CERT)
    }

    companion object {
        private const val QA = "com.blackatsystems.miguardia.qa"
        private const val CERT = "b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617"
    }
}
