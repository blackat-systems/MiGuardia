package com.blackatsystems.miguardia.stage12

import androidx.test.platform.app.InstrumentationRegistry
import android.widget.Chronometer
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.core.domain.widget.WidgetSize
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.widget.WidgetRemoteViewsRenderer
import com.blackatsystems.miguardia.widget.resolveWidgetPalette
import com.blackatsystems.miguardia.core.domain.widget.WidgetProjectionConfig
import com.blackatsystems.miguardia.core.domain.widget.projectWidget
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.util.UUID

class Stage12W010NativeTest {
    @Test fun hiddenRealRemoteViewsClearAdversarialAppliedFieldsInBothSizes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(instrumentation is Stage12NormalQaRunner && context.packageName == "com.blackatsystems.miguardia.qa")
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("stage12CalendarClock") == "REAL")
        val id = requireNotNull(args.getString("stage12RunId"))
        val fixture = Stage12W010Fixture(UUID.fromString(id)); fixture.requireAllSecretsPresent()
        val identity = JSONObject().put("run_uuid", id).put("qa_sha256", args.getString("stage12QaSha256"))
            .put("runner_sha256", args.getString("stage12RunnerSha256"))
        val output = Stage12NativeReceiptFiles.prepareOutput(context.filesDir, id, identity.toString(), "w010-applied-widget")
        val receipt = JSONObject(identity.toString()).put("schema", "stage12-native-completion-v1").put("case", "W010")
            .put("status", "INCOMPLETE").put("fixture_owned", true).put("fixture_memory_only", true)
            .put("pending_intent_existence_proves_alarm_delivery", false).put("facts", JSONArray())
        var primary: Throwable? = null
        var lease: Stage12OwnedWidgetIntents? = null
        var variants = 0; var nodes = 0; var colors = 0; var publicDescriptionProofs = 0
        try {
            lease = Stage12OwnedWidgetIntents(context, fixture.namespace, fixture.shifts.map { it.shift.id }.toSet(), setOf(fixture.dayOff))
            val renderer = WidgetRemoteViewsRenderer(context)
            for (dark in listOf(false, true)) for (size in WidgetSize.entries) for (mode in WidgetMode.entries)
                for (boundary in Stage12W010Fixture.Boundary.entries) {
                    val projection = fixture.projection(boundary, mode, size)
                    check(projection.mode == mode && projection.size == size) { "W010_REQUESTED_VARIANT" }
                    Stage12W010Oracle.requireProjection(projection, fixture, boundary)
                    val palette = resolveWidgetPalette(if (dark) AppThemeMode.DARK else AppThemeMode.LIGHT, false)
                    val preload = renderer.createViews(fixture.projection(Stage12W010Fixture.Boundary.BEFORE,
                        WidgetMode.AUTOMATIC, size, WidgetPrivacy.COMPLETE), lease.widgetId, fixture.weather, palette,
                        elapsedRealtime = android.os.SystemClock.elapsedRealtime())
                    val hidden = renderer.createViews(projection, lease.widgetId, fixture.weather, palette,
                        elapsedRealtime = android.os.SystemClock.elapsedRealtime())
                    lease.retainCreated()
                    Stage12SummaryObservation.onMain({ action -> instrumentation.runOnMainSync(action) }) {
                        val view = Stage12AppliedRemoteViews.apply(context, preload)
                        var viewFailure: Throwable? = null
                        try {
                            check(Stage12W010AppliedInspector.allText(view).any { it.contains(fixture.institution) }) { "W010_PRELOAD_NOT_ADVERSARIAL" }
                            val counter = view.findViewById<Chronometer>(R.id.widget_countdown)
                            val publicControl = Stage12W010CleanChronometer.beforeReapply(counter.context)
                            hidden.reapply(context, view)
                            val inspected = Stage12W010AppliedInspector.requireHidden(view, fixture, Stage12W010Oracle.palette(dark), size,
                                Stage12W010Observation.Variant(dark, size, mode, boundary), publicControl)
                            nodes += inspected.nodes; colors += inspected.colorAttributes
                            publicDescriptionProofs += inspected.publicDescriptionProofs
                            preload.reapply(context, view)
                            Stage12W010AppliedInspector.requireComplete(view, fixture, size)
                            val reductions = listOf(
                                fixture.projection(Stage12W010Fixture.Boundary.BEFORE, WidgetMode.AUTOMATIC, size, WidgetPrivacy.REDUCED),
                                fixture.projection(Stage12W010Fixture.Boundary.END_EXACT, WidgetMode.NEXT_SHIFT, size, WidgetPrivacy.COMPLETE),
                                fixture.projection(Stage12W010Fixture.Boundary.END_EXACT, WidgetMode.NEXT_DAY_OFF, size, WidgetPrivacy.COMPLETE),
                                projectWidget(fixture.result(Stage12W010Fixture.Boundary.BEFORE),
                                    WidgetProjectionConfig(WidgetMode.AUTOMATIC, WidgetPrivacy.COMPLETE, size, configured = false)))
                            for (reduced in reductions) {
                                renderer.createViews(reduced, lease.widgetId, null, palette).reapply(context, view)
                                Stage12W010AppliedInspector.requireNoPriorDetails(view, fixture)
                                preload.reapply(context, view)
                                Stage12W010AppliedInspector.requireComplete(view, fixture, size)
                            }
                            if (size == WidgetSize.COMPACT) {
                                renderer.recoveryViews(lease.widgetId).reapply(context, view)
                                Stage12W010AppliedInspector.requireNoPriorDetails(view, fixture)
                                preload.reapply(context, view)
                                Stage12W010AppliedInspector.requireComplete(view, fixture, size)
                            }
                            lease.retainCreated()
                        } catch (error: Throwable) { viewFailure = error; throw error }
                        finally {
                            try { Stage12AppliedRemoteViews.release(view) } catch (error: Throwable) {
                                if (viewFailure == null) throw error else Stage12W010Oracle.retain(viewFailure, error)
                            }
                        }
                    }
                    variants++
                }
            receipt.getJSONArray("facts").put(JSONObject().put("assertion_key", Stage12W010Oracle.REMOTE_KEY)
                .put("check", "PASS").put("level", "REMOTE_VIEWS_REAL").put("variants", variants)
                .put("nodes", nodes).put("color_attributes", colors).put("preloaded_then_reapplied", true)
                .put("restores_visible_actions", true).put("equivalent_reductions", "minimal,empty,day-off,unconfigured,recovery-compact"))
            receipt.put("public_description_proofs", publicDescriptionProofs)
                .put("public_control_attestation_observed", publicDescriptionProofs > 0)
                .put("public_control_rule", "EXACT_PER_FIELD_FRESH_CONTROLS_STABLE_WINDOW_OR_REJECT")
                .put("universal_firmware_independence_claim", false)
            check(variants == 48)
            receipt.put("fixture_observation", JSONObject(Stage12ObservedFixture.memory(fixture, variants)))
        } catch (error: Throwable) { primary = error; receipt.put("failure_class", error.javaClass.simpleName) }
        finally {
            try { lease?.retainCreated() }
            catch (error: Throwable) { primary = Stage12W010Oracle.retain(primary, error); receipt.put("token_retention_failure_class", error.javaClass.simpleName) }
            try { lease?.close(); receipt.put("owned_tokens_closed", lease != null) }
            catch (error: Throwable) { primary = Stage12W010Oracle.retain(primary, error); receipt.put("cleanup_failure_class", error.javaClass.simpleName) }
            receipt.put("status", if (primary == null) "COMPLETE" else "FAILED").put("cleanup_complete", primary == null)
            try { Stage12NativeReceiptFiles.publish(output, receipt.toString(2), primary) }
            catch (error: Throwable) { primary = Stage12W010Oracle.retain(primary, error) }
        }
        primary?.let { throw it }
    }
}
