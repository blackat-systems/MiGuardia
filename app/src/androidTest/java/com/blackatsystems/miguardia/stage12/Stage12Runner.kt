package com.blackatsystems.miguardia.stage12

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.blackatsystems.miguardia.QaInstrumentationScope
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal data class Stage12Arguments(val selector: String, val runId: String, val qaSha: String, val runnerSha: String, val mode: String) {
    companion object {
        val selectors = setOf(
            "com.blackatsystems.miguardia.stage12.Stage12GuardInstrumentedTest#rootContextRoutesReviewedStorageApisAndPreservesCanaries",
            "com.blackatsystems.miguardia.stage12.Stage12CaptureInstrumentedTest#activityPixelCopyExcludesOwnDialogAndRedactsPassword",
            "com.blackatsystems.miguardia.stage12.Stage12CaptureInstrumentedTest#protectedWindowRejectsCaptureWithoutRemovingSecureFlag",
        )
        fun parse(bundle: Bundle): Stage12Arguments {
            require(bundle.keySet() == setOf("class", "stage12Consent", "stage12RunId", "stage12QaSha256", "stage12RunnerSha256", "stage12Mode"))
            require(bundle.getString("stage12Consent") == "STAGE12_SYNTHETIC_20261023")
            val selector = requireNotNull(bundle.getString("class"))
            require(selector in selectors)
            val runId = requireNotNull(bundle.getString("stage12RunId"))
            require(UUID.fromString(runId).toString() == runId)
            val qa = requireNotNull(bundle.getString("stage12QaSha256"))
            val runner = requireNotNull(bundle.getString("stage12RunnerSha256"))
            require(qa.matches(Regex("[0-9a-f]{64}")) && runner.matches(Regex("[0-9a-f]{64}")))
            val mode = requireNotNull(bundle.getString("stage12Mode"))
            require(mode == "neutral" || mode == "quiet")
            return Stage12Arguments(selector, runId, qa, runner, mode)
        }
    }
}

internal class Stage12Counts(private val selector: String) {
    private var started = 0
    private var passed = 0
    private var failed = 0
    private var skipped = 0
    private var malformed = false
    @Synchronized fun observe(code: Int, status: Bundle) {
        val identity = "${status.getString("class")}#${status.getString("test")}"
        if (identity != selector || status.getInt("numtests", -1) != 1 || (status.containsKey("current") && status.getInt("current", -1) != 1)) malformed = true
        if (!status.getString("stack").isNullOrBlank()) malformed = true
        when (code) {
            1 -> { if (started != 0 || passed != 0 || failed != 0 || skipped != 0) malformed = true; started++ }
            0 -> { if (started != 1 || passed != 0 || failed != 0 || skipped != 0) malformed = true; passed++ }
            -3, -4 -> skipped++
            else -> failed++
        }
    }
    @Synchronized fun isSinglePass(): Boolean = !malformed && started == 1 && passed == 1 && failed == 0 && skipped == 0
    @Synchronized fun json(): JSONObject = JSONObject().put("expected", 1).put("started", started).put("passed", passed).put("failed", failed).put("skipped", skipped)
}

/** Exact method allowlist; default Gradle configuration is disabled and cannot admit a run. */
class Stage12Runner : AndroidJUnitRunner() {
    private lateinit var binding: Stage12RootBinding
    private lateinit var session: Stage12Session
    private lateinit var counts: Stage12Counts
    private var admitted = false
    private var applicationCalled = false
    private var deferredApplication: Application? = null
    private var finished = false

    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application {
        check(Stage12BuildPins.ENABLED) { "Stage12 is disabled" }
        check(android.os.Build.VERSION.SDK_INT == 36) { "Only the dedicated Stage12 API36 protocol is admitted" }
        check(className == "com.blackatsystems.miguardia.MiGuardiaApplication")
        verifyPackage(context, "com.blackatsystems.miguardia.qa", Stage12BuildPins.QA_SHA256)
        @Suppress("DEPRECATION")
        val providers = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS or PackageManager.MATCH_DISABLED_COMPONENTS).providers.orEmpty()
        check(providers.none { it.enabled }) { "Bootstrap providers must be disabled in the dedicated Stage12 QA APK" }
        binding = Stage12RootBinding(context)
        return super.newApplication(cl, className, Stage12RootContext(context, binding)).also { binding.application = it }
    }

    override fun onCreate(arguments: Bundle) {
        check(::binding.isInitialized && !admitted)
        val args = Stage12Arguments.parse(arguments)
        check(args.qaSha == Stage12BuildPins.QA_SHA256)
        verifyPackage(binding.live, "com.blackatsystems.miguardia.qa", args.qaSha)
        verifyPackage(context, "com.blackatsystems.miguardia.qa.test", args.runnerSha)
        session = Stage12Session(binding, args) // Read-only baseline before creating the UUID root.
        binding.bind(args.runId)
        QaInstrumentationScope.install(this, args.qaSha, args.runnerSha, binding, args.mode)
        Stage12Session.current = session
        session.createCanaries()
        counts = Stage12Counts(args.selector)
        admitted = true
        deferredApplication?.let { deferredApplication = null; callApplicationOnCreate(it) }
        super.onCreate(arguments)
    }

    override fun callApplicationOnCreate(app: Application) {
        check(::binding.isInitialized && app === binding.application)
        if (!admitted) { check(deferredApplication == null); deferredApplication = app; return }
        check(!applicationCalled) { "Application.onCreate repeated" }
        applicationCalled = true
        super.callApplicationOnCreate(app)
    }

    override fun newActivity(cl: ClassLoader, className: String, intent: Intent): Activity {
        check(admitted && applicationCalled)
        check(className == "com.blackatsystems.miguardia.stage12.Stage12CaptureActivity") { "Activity is not admitted by this method allowlist" }
        return super.newActivity(cl, className, intent)
    }

    override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
        check(admitted && activity.baseContext is Stage12RootContext) { "Activity did not attach the owned context" }
        super.callActivityOnCreate(activity, icicle)
    }

    override fun sendStatus(resultCode: Int, results: Bundle) {
        if (::counts.isInitialized) counts.observe(resultCode, results)
        super.sendStatus(resultCode, results)
    }

    override fun finish(resultCode: Int, results: Bundle) {
        check(!finished)
        finished = true
        val terminal = Bundle(results)
        try {
            check(admitted && applicationCalled && resultCode == Activity.RESULT_OK)
            val zip = session.complete(counts)
            terminal.putString("stage12Status", "COMPLETE")
            terminal.putString("stage12RunId", session.args.runId)
            terminal.putString("stage12Bundle", zip.name)
            terminal.putString("stage12BundleSha256", stage12Hash(zip))
            terminal.putString("stage12QaSha256", session.args.qaSha)
            terminal.putString("stage12RunnerSha256", session.args.runnerSha)
        } catch (failure: Throwable) {
            terminal.putString("stage12Status", "INCOMPLETE")
            terminal.putString("stage12Failure", failure.javaClass.simpleName)
            // Keep own evidence and canaries on failure. Never repair received data or clear scope.
        }
        super.finish(resultCode, terminal)
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(context: Context, expected: String, sha: String) {
        check(context.packageName == expected)
        check(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        check(sha.matches(Regex("[0-9a-f]{64}")) && stage12Hash(File(context.applicationInfo.sourceDir)) == sha)
        val info = context.packageManager.getPackageInfo(expected, PackageManager.GET_SIGNING_CERTIFICATES)
        val signatures = info.signingInfo?.apkContentsSigners.orEmpty()
        check(signatures.size == 1)
        val cert = MessageDigest.getInstance("SHA-256").digest(signatures.single().toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        check(cert == "b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617")
    }
}
