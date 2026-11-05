package com.blackatsystems.miguardia.stage12

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.QaMainActivityGraph
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.core.domain.widget.*
import com.blackatsystems.miguardia.ui.availability.AvailabilityViewModel
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.widget.WidgetRemoteViewsRenderer
import com.blackatsystems.miguardia.widget.resolveWidgetPalette
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/** The original visible RemoteViews host and the Main receiver born from its original PendingIntent. */
internal class Stage12TemporalMainOwner(
    private val framework: Context,
    private val case: Stage12TemporalCase,
    retain: (Throwable) -> Unit,
    private val active: () -> Unit,
) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val failure = Stage12TemporalFailure(retain)
    private lateinit var resources: Stage12TemporalResources
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private var host: ComponentActivity? = null
    private var applied: View? = null
    private var tokens: Stage12OwnedWidgetIntents? = null
    private var hostAttempted = false
    private var applyAttempted = false
    private var applyReturned = false
    private var reserved = false
    private var reservedGraph: QaMainActivityGraph? = null
    private var mainCloseAttempted = false
    private var hostCloseAttempted = false
    private var releaseAttempted = false
    private var mainClosed = false
    private var hostClosed = false
    private var released = false
    @Volatile var activity: MainActivity? = null; private set
    @Volatile var incoming: QaMainActivityGraph.IncomingDestination? = null; private set
    lateinit var calendar: CalendarViewModel; private set
    lateinit var availability: AvailabilityViewModel; private set
    val gestures = JSONArray()
    val textObservations = JSONArray()
    private val graphEvents = JSONArray()
    val graph get() = resources.graph

    fun attach(resources: Stage12TemporalResources) {
        check(!this::resources.isInitialized); this.resources = resources
    }
    fun event(value: QaMainActivityGraph.Event) { synchronized(graphEvents) { graphEvents.put(value.name) } }
    fun projection(): JSONObject {
        val destination = incoming?.let { value ->
            JSONObject().put("action", value.action).put("component_package", value.componentPackage)
                .put("component_class", value.componentClass).put("data_uri", value.dataUri).put("flags", value.flags)
                .put("owner_date_present", value.ownerDatePresent).put("owner_date", value.ownerDate)
                .put("shift_id_present", value.shiftIdPresent).put("extra_keys", JSONArray(value.extraKeys.sorted()))
        }
        return JSONObject().put("run_uuid", case.binding.run).put("generation", reservedGraph?.lease?.generation ?: JSONObject.NULL)
            .put("receiver_observed", activity != null).put("receiver_identity", activity?.let(System::identityHashCode))
            .put("incoming", destination ?: JSONObject.NULL).put("widget_id", tokens?.widgetId)
            .put("graph_events", synchronized(graphEvents) { JSONArray(graphEvents.toString()) }).put("gestures", gestures).put("text_observations", textObservations)
            .put("main_close_attempted", mainCloseAttempted).put("main_closed", mainClosed)
            .put("host_close_attempted", hostCloseAttempted).put("host_closed", hostClosed)
            .put("binding_release_attempted", releaseAttempted).put("binding_released", released)
    }
    fun published(receiver: MainActivity) {
        // Keep the original first, including a constructor/attach/onCreate failure.
        val previous = activity
        if (previous == null) activity = receiver
        check(Looper.myLooper() === Looper.getMainLooper() && previous == null && reserved)
    }
    fun incoming(receiver: MainActivity, value: QaMainActivityGraph.IncomingDestination) {
        check(Looper.myLooper() === Looper.getMainLooper() && receiver === activity && incoming == null)
        incoming = value
        val ownTokens = checkNotNull(tokens)
        val date = LocalDate.of(2026, 10, 23)
        check(value.action == MainActivity.ACTION_VIEW_DATE && value.componentPackage == framework.packageName &&
            value.componentClass == MainActivity::class.java.name)
        check(value.dataUri == "miguardia://widget-action/${ownTokens.widgetId}/VIEW_DATE?target=$date")
        check(value.ownerDatePresent && value.ownerDate == date.toString() && !value.shiftIdPresent &&
            value.extraKeys == setOf(MainActivity.EXTRA_OWNER_LOCAL_DATE))
        val required = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        check(value.flags and required == required)
    }
    fun reserve() {
        active(); check(this::resources.isInitialized && !reserved && activity == null)
        reservedGraph = graph
        (instrumentation as Stage12NormalQaRunner).reserveTemporalMainGraph(graph)
        reserved = true
    }
    fun showAndTouch(projection: WidgetProjection) {
        try {
            active(); check(reserved && !hostAttempted); hostAttempted = true
            check(projection.navigation == WidgetNavigation.Date(LocalDate.of(2026, 10, 23)))
            tokens = Stage12OwnedWidgetIntents(framework, case.fixture.recipe.namespace, emptySet(), setOf(LocalDate.of(2026, 10, 23)))
            scenario = ActivityScenario.launch(Intent().setComponent(ComponentName(framework.packageName, ComponentActivity::class.java.name)))
            checkNotNull(scenario).onActivity { owner ->
                check(host == null); host = owner
                val ownTokens = checkNotNull(tokens)
                val renderer = WidgetRemoteViewsRenderer(framework)
                val dark = owner.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                val rendered = try { renderer.createViews(projection, ownTokens.widgetId, null,
                    resolveWidgetPalette(graph.capturedThemeMode, dark)) }
                catch (error: Throwable) { failure.remember(error); null }
                // Capture original capabilities even after a partial renderer failure, preserving its primary.
                try { ownTokens.retainCreated() } catch (error: Throwable) { failure.remember(error) }
                failure.requireActive(); active()
                val views = checkNotNull(rendered)
                ownTokens.requireRetainedDate(LocalDate.of(2026, 10, 23))
                applyAttempted = true
                val view = Stage12AppliedRemoteViews.apply(owner, views)
                applied = view; applyReturned = true
                val frame = view.parent as FrameLayout
                check(frame.parent == null && frame.childCount == 1)
                owner.setContentView(frame)
            }
            instrumentation.waitForIdleSync(); active()
            var point: Pair<Int, Int>? = null
            checkNotNull(scenario).onActivity { owner ->
                check(owner === host && owner.lifecycle.currentState == Lifecycle.State.RESUMED && !owner.isDestroyed)
                val view = checkNotNull(applied)
                check(view.isAttachedToWindow && view.isShown && owner.hasWindowFocus())
                observeText(view.findViewById(R.id.widget_primary), "Guardia pasiva · reanudada")
                point = observeText(view.findViewById(R.id.widget_schedule), "24/10/2026 · 08:00–12:00")
                check(listOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3).all { view.findViewById<View>(it).visibility == View.GONE })
                val root = view.findViewById<View>(R.id.widget_root)
                check(root.isClickable && root.isEnabled)
                checkNotNull(tokens).requireRetainedDate(LocalDate.of(2026, 10, 23))
            }
            failure.inputAfterReadiness({ active(); checkNotNull(point) }) { (x, y) ->
                active()
                val event = JSONObject().put("kind", "REMOTE_VIEWS_ORIGINAL_PENDING_INTENT_TOUCH")
                    .put("x", x).put("y", y).put("input_entered", true).put("transition_observed", false)
                gestures.put(event)
                active()
                val result = UiDevice.getInstance(instrumentation).click(x, y)
                event.put("api_return", result).put("input_returned", true)
            }
            // The caller must observe the actual receiver and exact day before granting a transition.
            active()
        } catch (error: Throwable) { failure.reject(error) }
    }
    private fun observeText(text: TextView, expected: String): Pair<Int, Int> {
        check(text.text.toString() == expected && text.isShown && text.isAttachedToWindow && text.width > 0 && text.height > 0)
        val layout = checkNotNull(text.layout)
        check(layout.lineCount > 0 && (0 until layout.lineCount).none { layout.getEllipsisCount(it) != 0 })
        check((0 until layout.lineCount).all { layout.getLineLeft(it) >= 0f && layout.getLineRight(it) <= layout.width.toFloat() })
        check(layout.height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
        val location = IntArray(2); text.getLocationOnScreen(location)
        val visible = android.graphics.Rect(); check(text.getGlobalVisibleRect(visible))
        check(visible.left == location[0] && visible.top == location[1] && visible.width() == text.width && visible.height() == text.height)
        textObservations.put(JSONObject().put("text", expected).put("line_count", layout.lineCount).put("ellipsized", false)
            .put("screen_bounds", JSONArray(listOf(visible.left, visible.top, visible.right, visible.bottom)))
            .put("font_scale", text.resources.configuration.fontScale))
        return visible.centerX() to visible.centerY()
    }
    fun captureCreatedModels() {
        active()
        instrumentation.runOnMainSync {
            val receiver = checkNotNull(activity)
            check(receiver.lifecycle.currentState == Lifecycle.State.RESUMED && incoming != null)
            check(!this::calendar.isInitialized && !this::availability.isInitialized)
            calendar = graph.existingModel(receiver, CalendarViewModel::class.java)
            availability = graph.existingModel(receiver, AvailabilityViewModel::class.java)
        }
    }
    suspend fun closeOriginalMain() {
        try {
            check(!mainCloseAttempted); mainCloseAttempted = true
            val receiver = checkNotNull(activity) { "TEMPORAL_MAIN_RECEIVER_CESE_UNPROVEN" }
            instrumentation.runOnMainSync { if (!receiver.isDestroyed) receiver.finish() }
            withTimeout(30_000) { while (!receiver.isDestroyed) delay(20) }
            check(receiver.lifecycle.currentState == Lifecycle.State.DESTROYED)
            resources.composition.closeAfterDestroyed { instrumentation.runOnMainSync(it) }
            withTimeout(30_000) { graph.activityJobs().joinAll() }
            check(graph.modelClosureProven())
            mainClosed = true
            failure.requireActive()
        } catch (error: Throwable) { failure.reject(error) }
    }
    fun closeOriginalHost() {
        check(!hostCloseAttempted); hostCloseAttempted = true
        var negative: Throwable? = null
        fun closing(body: () -> Unit) { try { body() } catch (error: Throwable) { failure.remember(error); if (negative == null) negative = error } }
        closing { instrumentation.runOnMainSync { applied?.let(Stage12AppliedRemoteViews::release) } }
        closing { checkNotNull(scenario).close() }
        closing { checkNotNull(tokens).close() }
        closing { check(checkNotNull(host).isDestroyed && (!applyAttempted || applyReturned) && applied?.parent == null) }
        if (negative == null) hostClosed = true
        negative?.let { throw it }
    }
    fun releaseAfterQuiescence() {
        check(!releaseAttempted); releaseAttempted = true
        check(mainClosed && hostClosed && resources.closureProven)
        (instrumentation as Stage12NormalQaRunner).releaseTemporalMainGraph(checkNotNull(activity), graph, true)
        released = true
    }
    val mainClosureProven get() = mainClosed && graph.modelClosureProven()
    val closureProven get() = mainClosed && hostClosed && released && resources.closureProven
}
