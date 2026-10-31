package com.blackatsystems.miguardia

import android.app.Instrumentation
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest

/** Storage and evidence supplied exclusively by the admitted Stage12 runner. */
internal interface QaInstrumentationContext {
    fun wrapActivity(base: Context): Context
    fun suppressed(operation: String)
    fun calendarClock(): java.time.Clock? = null
    fun calendarDateSelected(date: java.time.LocalDate) {}
    fun calendarDateSelectionCount(date: java.time.LocalDate): Int? = null
}

/**
 * A process-only instrumentation boundary. Normal startup has no binding.
 * No Intent, preference, public component or portable datum can install it.
 */
internal object QaInstrumentationScope {
    private const val QA = "com.blackatsystems.miguardia.qa"
    private const val TEST = "$QA.test"
    private const val CERT = "b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617"
    @Volatile private var binding: QaInstrumentationContext? = null
    @Volatile private var mode: String? = null

    @Synchronized
    fun install(
        runner: Instrumentation,
        qaSha256: String,
        runnerSha256: String,
        context: QaInstrumentationContext,
        mode: String,
    ) {
        check(binding == null) { "An instrumentation scope already exists" }
        val runnerName = runner.javaClass.name
        QaBootstrapPolicy.requireRunnerMode(mode, runnerName)
        val target = runner.targetContext
        val test = runner.context
        check(target.packageName == QA && test.packageName == TEST)
        check(target.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        check(test.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        check(qaSha256.matches(Regex("[0-9a-f]{64}")) && runnerSha256.matches(Regex("[0-9a-f]{64}")))
        check(hash(File(target.applicationInfo.sourceDir)) == qaSha256)
        check(hash(File(test.applicationInfo.sourceDir)) == runnerSha256)
        check(certificate(target, QA) == CERT && certificate(test, TEST) == CERT)
        val component = android.content.ComponentName(TEST, runnerName)
        @Suppress("DEPRECATION")
        val info = target.packageManager.getInstrumentationInfo(component, 0)
        check(info.targetPackage == QA && info.name == runnerName)
        this.mode = mode
        binding = context
    }

    fun wrapActivity(base: Context): Context {
        val scope = binding ?: return base
        check(base.packageName == QA)
        return scope.wrapActivity(base)
    }

    fun isBound(context: Context): Boolean = binding != null && context.packageName == QA
    fun isNeutral(context: Context): Boolean = isBound(context) && mode == "neutral"
    fun isIsolated(context: Context): Boolean = isBound(context) && QaBootstrapPolicy.isIsolationMode(mode)

    // Process-only test fixtures; no Intent, preference or normal application can install them.
    fun calendarClock(context: Context): java.time.Clock? =
        if (isBound(context) && mode == "normal-qa") binding?.calendarClock() else null

    fun calendarDateSelected(context: Context, date: java.time.LocalDate) {
        if (isBound(context) && mode == "normal-qa") binding?.calendarDateSelected(date)
    }

    fun calendarDateSelectionCount(context: Context, date: java.time.LocalDate): Int? =
        if (isBound(context) && mode == "normal-qa") binding?.calendarDateSelectionCount(date) else null

    /** A dedicated APK must never fall back to received storage after replacement or death. */
    fun requireProcessAdmission(context: Context) {
        if (context.packageName != QA) return
        @Suppress("DEPRECATION")
        val metadata = context.packageManager.getApplicationInfo(QA, PackageManager.GET_META_DATA).metaData
        QaBootstrapPolicy.requireAdmission(
            harnessBuild = metadata?.getBoolean("com.blackatsystems.miguardia.STAGE12_HARNESS", false) == true,
            bound = isBound(context),
        )
    }

    fun requireProductActivity(context: Context) {
        check(!isNeutral(context)) { "Product Activities are excluded from neutral instrumentation" }
    }

    fun requireActivityLaunch(context: Context, intent: android.content.Intent) {
        if (binding == null) return
        val component = intent.component
        if (isNeutral(context)) {
            check(component?.className == "com.blackatsystems.miguardia.stage12.Stage12CaptureActivity") {
                "Neutral instrumentation cannot launch product Activities"
            }
        }
        val internal = component?.packageName == QA && component.className in setOf(
            "com.blackatsystems.miguardia.MainActivity",
            "com.blackatsystems.miguardia.widget.WidgetConfigurationActivity",
            "com.blackatsystems.miguardia.stage12.Stage12CaptureActivity",
        )
        if (!internal) requireExternalAction(context, "activity.external-launch")
    }

    /** Returns before any service, recovery sweep or external side effect. */
    fun suppress(context: Context, operation: String): Boolean {
        val scope = binding ?: return false
        check(context.packageName == QA)
        // Explicit normal QA use is authorized separately. This mode is not storage
        // isolation and must not fabricate suppression/reconciliation evidence.
        if (!QaBootstrapPolicy.isIsolationMode(mode)) return false
        scope.suppressed(operation)
        return true
    }

    fun requireExternalAction(context: Context, operation: String) {
        if (suppress(context, operation)) {
            throw SecurityException("External action excluded from isolated instrumentation: $operation")
        }
    }

    private fun hash(file: File): String = file.inputStream().use { stream ->
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(65536)
        while (true) {
            val n = stream.read(bytes)
            if (n < 0) break
            digest.update(bytes, 0, n)
        }
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    @Suppress("DEPRECATION")
    private fun certificate(context: Context, name: String): String {
        val signatures = if (android.os.Build.VERSION.SDK_INT >= 28) {
            context.packageManager.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners.orEmpty()
        } else context.packageManager.getPackageInfo(name, PackageManager.GET_SIGNATURES).signatures.orEmpty()
        check(signatures.size == 1)
        return MessageDigest.getInstance("SHA-256").digest(signatures.single().toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
