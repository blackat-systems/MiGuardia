package com.blackatsystems.miguardia.stage12

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.*
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.QaMainActivityComposition
import kotlinx.coroutines.*

/** Real Android frame clock, explicit parent job, and the actual content View retained until cese. */
internal class Stage12TemporalComposition(private val framework: Context, retain: (Throwable) -> Unit) : QaMainActivityComposition {
    private val failure = Stage12TemporalFailure(retain)
    private var owner: ComponentActivity? = null
    private var root: Job? = null
    private var settingsJob: Job? = null
    private var settingsObserver: ContentObserver? = null
    private var settingsHandler: Handler? = null
    private var recomposer: Recomposer? = null
    private var view: ComposeView? = null
    private var attempted = false
    private var initialized = false
    private var contentBound = false
    private var settingsRegistered = false
    private var settingsRegistrationUnknown = false
    private var settingsUnregistered = false
    private var settingsUnregisterAttempted = false
    private var viewDisposed = false
    private var handlerDrained = false
    private var destroyed = false
    private var closeAttempted = false
    private var closed = false
    private fun asynchronousFailure(error: Throwable) {
        failure.remember(error)
        root?.cancel(CancellationException("TEMPORAL_COMPOSITION_FIRST_NEGATIVE", error))
    }
    private fun unregisterOriginalObserver() {
        if (settingsObserver == null) return
        check(!settingsUnregisterAttempted); settingsUnregisterAttempted = true
        framework.contentResolver.unregisterContentObserver(checkNotNull(settingsObserver))
        settingsUnregistered = true
        checkNotNull(settingsHandler).removeCallbacksAndMessages(null)
        handlerDrained = true
    }

    fun initialize(activity: ComponentActivity): CompositionContext {
        try {
            failure.requireActive()
            check(Looper.myLooper() === Looper.getMainLooper() && !attempted && !closeAttempted)
            attempted = true; owner = activity
            val parent = SupervisorJob(); root = parent
            val context = AndroidUiDispatcher.CurrentThread + parent + CoroutineExceptionHandler { _, error -> asynchronousFailure(error) }
            fun readScale(): Float = Settings.Global.getFloat(framework.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
                .also { check(it.isFinite() && it >= 0f) }
            val scale = object : MotionDurationScale {
                var actual by mutableFloatStateOf(readScale())
                override val scaleFactor: Float get() = actual
            }
            val handler = Handler(Looper.getMainLooper()); settingsHandler = handler
            val observer = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    if (destroyed || closeAttempted || root?.isActive != true) return
                    try { failure.requireActive(); scale.actual = readScale() }
                    catch (error: Throwable) { asynchronousFailure(error) }
                }
            }
            settingsObserver = observer
            settingsJob = CoroutineScope(context).launch(start = CoroutineStart.UNDISPATCHED) {
                settingsRegistrationUnknown = true
                framework.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
                settingsRegistered = true; settingsRegistrationUnknown = false
                try { awaitCancellation() }
                finally { unregisterOriginalObserver() }
            }
            failure.requireActive()
            val actualView = ComposeView(activity); view = actualView
            activity.setContentView(actualView)
            // The public factory preserves lifecycle pause/resume and the real Android frame clock.
            // Our actual scale element avoids its detached, application-context MainScope cache.
            val composer = actualView.createLifecycleAwareWindowRecomposer(context + scale, activity.lifecycle)
            recomposer = composer
            activity.lifecycle.addObserver(LifecycleEventObserver { source, event ->
                if (event == Lifecycle.Event.ON_DESTROY && source === activity) {
                    destroyed = true
                    composer.cancel()
                    parent.cancel()
                }
            })
            initialized = true
            return composer
        } catch (error: Throwable) { failure.reject(error) }
    }
    fun bindContent(activity: ComponentActivity) {
        try {
            failure.requireActive()
            check(owner === activity && initialized && !destroyed && !contentBound && !closeAttempted)
            val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            check(content.childCount == 1)
            check(content.getChildAt(0) === checkNotNull(view)) { "TEMPORAL_CONTENT_VIEW_FOREIGN" }
            contentBound = true
        } catch (error: Throwable) { failure.reject(error) }
    }
    override fun parent(activity: MainActivity): CompositionContext = initialize(activity)
    override fun contentInstalled(activity: MainActivity) = bindContent(activity)
    fun observeDestroyed(activity: ComponentActivity) {
        try {
            if (owner == null && !attempted && jobs.isEmpty()) owner = activity
            check(owner === activity && activity.lifecycle.currentState == Lifecycle.State.DESTROYED)
            destroyed = true
        } catch (error: Throwable) { failure.reject(error) }
    }
    override fun ownerDestroyed(activity: MainActivity) = observeDestroyed(activity)
    override val jobs: List<Job> get() = listOfNotNull(root, settingsJob)
    override val closureProven: Boolean get() = closed && destroyed &&
        (view == null || viewDisposed) && (settingsObserver == null || settingsUnregistered && handlerDrained) && jobs.all { it.isCompleted }

    /** Dispose the original view on UI, join the entire recomposer and parent off UI, exactly once. */
    suspend fun closeAfterDestroyed(onUi: ((() -> Unit) -> Unit)) {
        try {
            check(!closeAttempted); closeAttempted = true
            val activity = checkNotNull(owner)
            check(destroyed && activity.isDestroyed && activity.lifecycle.currentState == Lifecycle.State.DESTROYED)
            onUi {
                check(Looper.myLooper() === Looper.getMainLooper())
                try { view?.disposeComposition(); viewDisposed = true } catch (error: Throwable) { failure.remember(error) }
                try { recomposer?.cancel() } catch (error: Throwable) { failure.remember(error) }
                root?.cancel()
            }
            check(Looper.myLooper() !== Looper.getMainLooper())
            withTimeout(30_000) {
                recomposer?.join()
                jobs.joinAll()
            }
            // A registration that threw is still retired through the exact captured observer, once.
            if (settingsObserver != null && !settingsUnregisterAttempted) onUi { unregisterOriginalObserver() }
            check(jobs.all { it.isCompleted } && (settingsObserver == null || settingsUnregistered && handlerDrained) &&
                (view == null || viewDisposed))
            closed = true
            failure.requireActive()
        } catch (error: Throwable) { failure.reject(error) }
    }
}
