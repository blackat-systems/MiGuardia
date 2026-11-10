package com.blackatsystems.miguardia.stage12

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.MainActivity
import org.json.JSONObject
import org.junit.Test
import java.io.File

/** Its PASS is technical Activity admission only. Never executes a functional test body. */
class Stage12ActivityAdmissionTest {
    @Test fun observesOwnActivityAdmissionWithoutGestures() {
        val instrumentation = InstrumentationRegistry.getInstrumentation() as Stage12NormalQaRunner
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        val report = Stage12ActivityAdmission(technicalOnly = true)
        instrumentation.beginReadiness(report)
        var scenario: ActivityScenario<MainActivity>? = null
        var output: File? = null
        var primary: Throwable? = null
        val identity = JSONObject().put("run_uuid", args.getString("stage12RunId"))
            .put("qa_sha256", args.getString("stage12QaSha256"))
            .put("runner_sha256", args.getString("stage12RunnerSha256"))
        try {
            report.phase(Stage12ActivityAdmission.Phase.FILES)
            output = Stage12NativeReceiptFiles.prepareOutput(context.filesDir,
                requireNotNull(args.getString("stage12RunId")), identity.toString(), "activity-admission")
            report.phase(Stage12ActivityAdmission.Phase.LAUNCH)
            scenario = ActivityScenario.launch(MainActivity::class.java)
            Stage12OwnActivityAdmission.awaitReady(scenario, context, UiDevice.getInstance(instrumentation), report)
        } catch (failure: Throwable) {
            primary = failure; report.failed(failure)
            throw failure
        } finally {
            try {
                report.phase(Stage12ActivityAdmission.Phase.CLOSE)
                scenario?.close()
            } catch (failure: Throwable) {
                report.failed(failure)
                val previous = primary
                if (previous == null) { primary = failure; throw failure }
                if (previous !== failure) previous.addSuppressed(failure)
            } finally {
                try {
                    report.phase(Stage12ActivityAdmission.Phase.PUBLISH)
                    output?.let {
                        val receipt = JSONObject(identity.toString()).put("schema", "stage12-activity-admission-receipt-v1")
                            .put("scope", "TECHNICAL_ADMISSION_ONLY_ZERO_GESTURES")
                            .put("publication_at_serialization", "PENDING")
                            .put("admission", JSONObject(report.wire()))
                        Stage12NativeReceiptFiles.publish(it, receipt.toString(2), primary)
                    }
                } catch (failure: Throwable) {
                    report.failed(failure)
                    throw failure
                } finally { report.finish(primary == null) }
            }
        }
    }
}
