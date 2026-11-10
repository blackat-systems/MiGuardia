package com.blackatsystems.miguardia.stage12

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.test.platform.app.InstrumentationRegistry
import android.os.PowerManager
import org.json.JSONObject
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.junit.runners.model.MultipleFailureException
import java.io.File

/** Outer rule: observes this rule's retained host after Compose teardown, never closes/relaunches it. */
internal class Stage12SummaryObservationRule : TestRule {
    @Volatile private var current: Stage12SummaryObservation? = null
    private val ownHost = Stage12SummaryObservation.OwnHost<Activity>()
    private val failureOrder = Stage12SummaryObservation.FailureOrder()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val report = (instrumentation as? Stage12NormalQaRunner)?.summaryObservation()
            if (report == null) { base.evaluate(); return }
            check("${description.className}#${description.methodName}" == Stage12SummaryObservation.SELECTOR)
            current = report
            var output: File? = null
            var primary: Throwable? = null
            val args = InstrumentationRegistry.getArguments()
            val identity = JSONObject().put("run_uuid", args.getString("stage12RunId"))
                .put("qa_sha256", args.getString("stage12QaSha256"))
                .put("runner_sha256", args.getString("stage12RunnerSha256"))
            fun retain(error: Throwable) {
                primary = Stage12SummaryObservation.retain(primary, error)
            }
            try {
                failureOrder.observeRule(block = {
                    report.phase(Stage12SummaryObservation.Phase.FILES)
                    output = Stage12NativeReceiptFiles.prepareOutput(context.filesDir,
                        requireNotNull(args.getString("stage12RunId")), identity.toString(), "summary-observation")
                    report.phase(Stage12SummaryObservation.Phase.RULE_ENTER)
                    base.evaluate()
                    report.ruleReturned()
                }, onFailure = { error, additional ->
                    if (report.phase == Stage12SummaryObservation.Phase.FILES)
                        report.failed(Stage12SummaryObservation.kind(error), Stage12SummaryObservation.Reason.FILES_FAILED)
                    else report.ruleFailed(error, additional)
                }, components = { (it as? MultipleFailureException)?.failures.orEmpty() })
            } catch (error: Throwable) {
                primary = error
            } finally {
                try {
                    report.phase(Stage12SummaryObservation.Phase.CLOSE_SAMPLE)
                    // The getter may fail after teardown. Use only the host capability retained while active.
                    val host = ownHost.existing()
                    if (host == null) report.closed(null) else {
                        val snapshot = snapshot(host, afterTeardown = true)
                        report.sample(snapshot)
                        report.requireOwnerSafe()
                        report.closed(snapshot.destroyed)
                    }
                } catch (error: Throwable) { report.closeFailed(error); retain(error) }
                if (primary == null) {
                    try { report.requireCompleteBeforePublish() } catch (error: Throwable) { retain(error) }
                }
                try {
                    report.phase(Stage12SummaryObservation.Phase.PUBLISH)
                    output?.let {
                        val receipt = JSONObject(identity.toString()).put("schema", "stage12-summary-observation-receipt-v1")
                            .put("publication_at_serialization", "PENDING").put("observation", JSONObject(report.wire()))
                        Stage12NativeReceiptFiles.publish(it, receipt.toString(2), primary)
                        report.published()
                    }
                } catch (error: Throwable) { report.publicationFailed(error); retain(error) }
                report.finish(primary == null)
                current = null
            }
            primary?.let { throw it }
        }
    }

    fun observeBody(provider: () -> Activity, block: (Stage12SummaryObservation?) -> Unit) {
        val report = current
        if (report == null) { block(null); return }
        report.beginBody()
        failureOrder.observeBody { try {
            check(Looper.myLooper() != Looper.getMainLooper())
            // Main avoids waitForIdleSync. ActivityScenario still invokes its ControlledLooper hook;
            // this observation can perturb timing and cannot establish the historical cause.
            val host = ownHost.acquire({ action -> instrumentation.runOnMainSync(action) }, provider)
            report.phase(Stage12SummaryObservation.Phase.HOST_BOUND)
            report.sample(snapshot(host))
            report.requireOwnerSafe()
            block(report)
            report.completedBody()
        } catch (error: Throwable) {
            report.bodyFailed(error); throw error
        } }
    }

    fun checkpoint(phase: Stage12SummaryObservation.Phase, action: Boolean = false) {
        val report = current ?: return
        report.phase(phase)
        try {
            val host = requireNotNull(ownHost.existing())
            report.sample(snapshot(host))
            if (action) report.requireActionSafe() else report.requireOwnerSafe()
        } catch (error: Throwable) {
            report.failed(Stage12SummaryObservation.kind(error), Stage12SummaryObservation.Reason.READ_FAILED)
            throw error
        }
    }

    fun overview(readCount: () -> Int) {
        val report = current ?: return
        report.phase(Stage12SummaryObservation.Phase.OVERVIEW_SAMPLE)
        try { report.overview(readCount()) } catch (error: Throwable) {
            report.failed(Stage12SummaryObservation.kind(error), Stage12SummaryObservation.Reason.ROOT_READ_FAILED)
            throw error
        }
    }

    private fun snapshot(host: Activity, afterTeardown: Boolean = false): Stage12SummaryObservation.Snapshot {
        val report = requireNotNull(current)
        var value = Stage12SummaryObservation.Snapshot(contextQa = context.packageName == QA,
            sameHost = host === ownHost.existing())
        try {
            check(Looper.myLooper() != Looper.getMainLooper())
            if (!afterTeardown) {
                value = value.copy(interactive = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive,
                    keyguardLocked = (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked)
            }
            Stage12SummaryObservation.onMain({ action -> instrumentation.runOnMainSync(action) }) {
                val onMain = Looper.myLooper() == Looper.getMainLooper()
                value = value.copy(onMain = onMain)
                check(onMain)
                val life = (host as? ComponentActivity)?.lifecycle?.currentState?.name
                value = value.copy(hostQa = host.packageName == QA,
                    uidQa = host.applicationInfo.uid == context.applicationInfo.uid,
                    life = life?.let(Stage12SummaryObservation.Life::valueOf) ?: Stage12SummaryObservation.Life.UNKNOWN,
                    finishing = host.isFinishing, destroyed = host.isDestroyed)
                if (!host.isDestroyed) {
                    val decor = host.window.peekDecorView()
                    value = value.copy(decorPresent = decor != null,
                        attached = decor?.isAttachedToWindow, focused = decor?.hasWindowFocus())
                }
            }
            return value
        } catch (error: Throwable) {
            report.sample(value) // Preserve measured fields; remaining fields stay UNKNOWN/null.
            report.failed(Stage12SummaryObservation.kind(error), Stage12SummaryObservation.Reason.READ_FAILED)
            throw error
        }
    }

    companion object { private const val QA = "com.blackatsystems.miguardia.qa" }
}
