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
    private var runnerIdentity: Instrumentation? = null
    private val mainActivityLoans = QaMainActivityLoans()
    private val mainContexts = java.util.WeakHashMap<Context, Boolean>()
    private var reservedMainGraph: QaMainActivityGraph? = null
    private var explicitHumanSession: QaExplicitHumanActionSession? = null
    private var explicitHumanOwner: android.app.Activity? = null

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
        runnerIdentity = runner
        binding = context
    }

    /** A single verified runner supplies the entire graph; portable data supplies only navigation. */
    @Synchronized
    fun expectOwnedMainMethod(runner: Instrumentation, run: String) {
        check(binding != null && mode == "normal-qa" && runnerIdentity === runner)
        mainActivityLoans.expectOwnedMethod(run)
    }

    @Synchronized
    fun completeOwnedMainMethod(runner: Instrumentation, run: String): Boolean {
        check(binding != null && mode == "normal-qa" && runnerIdentity === runner)
        return mainActivityLoans.completeOwnedMethod(run)
    }

    @Synchronized
    fun reserveMainActivityGraph(runner: Instrumentation, runnerRun: String, graph: QaMainActivityGraph) {
        check(binding != null && mode == "normal-qa" && runnerIdentity === runner)
        reservedMainGraph?.lease?.reject(IllegalStateException("QA_MAIN_GRAPH_ALREADY_RESERVED"))
        if (mainContexts.containsKey(graph.filesContext))
            graph.lease.reject(IllegalStateException("QA_MAIN_GRAPH_ALREADY_RESERVED"))
        check(graph.filesContext.packageName == QA)
        check(graph.filesContext is QaMainActivityFileContext)
        mainActivityLoans.reserve(graph.lease, runnerRun)
        mainContexts[graph.filesContext] = true
        reservedMainGraph = graph
    }

    @Synchronized
    fun mainActivityConstructed(runner: Instrumentation, activity: MainActivity) {
        check(binding != null && mode == "normal-qa" && runnerIdentity === runner)
        val graph = reservedMainGraph
        mainActivityLoans.born(activity) { graph?.publishedReceiver(activity) }
    }

    @Synchronized
    fun wrapMainActivity(activity: MainActivity, base: Context): Context {
        val lease = mainActivityLoans.requireAttached(activity) ?: return wrapActivity(base)
        val graph = lease.graph as? QaMainActivityGraph
            ?: lease.reject(IllegalStateException("QA_MAIN_GRAPH_TYPE_MISSING"))
        return graph.wrapActivity(base)
    }

    @Synchronized
    fun attachedMainActivityGraph(activity: MainActivity): QaMainActivityGraph? {
        val lease = mainActivityLoans.requireAttached(activity) ?: return null
        return lease.graph as? QaMainActivityGraph
            ?: lease.reject(IllegalStateException("QA_MAIN_GRAPH_TYPE_MISSING"))
    }

    @Synchronized
    fun beginMainActivity(activity: MainActivity, hasSavedState: Boolean): QaMainActivityGraph? {
        val lease = mainActivityLoans.begin(activity) ?: return null
        val graph = lease.graph as? QaMainActivityGraph
            ?: lease.reject(IllegalStateException("QA_MAIN_GRAPH_TYPE_MISSING"))
        if (hasSavedState) graph.lease.reject(IllegalStateException("QA_MAIN_RECREATION_OUTSIDE_LOAN"))
        graph.event(QaMainActivityGraph.Event.BEFORE_CREATE)
        return graph
    }

    @Synchronized
    fun mainActivityGraph(activity: MainActivity, expected: QaMainActivityGraph?): QaMainActivityGraph? {
        val lease = mainActivityLoans.requireActive(activity, expected?.lease) ?: return null
        val graph = lease.graph as? QaMainActivityGraph
            ?: lease.reject(IllegalStateException("QA_MAIN_GRAPH_TYPE_MISSING"))
        if (binding == null || mode != "normal-qa" || reservedMainGraph !== graph)
            lease.reject(IllegalStateException("QA_MAIN_PROCESS_BINDING_LOST"))
        return graph
    }

    fun requireMainActivityGraph(activity: MainActivity, expected: QaMainActivityGraph): QaMainActivityGraph =
        mainActivityGraph(activity, expected) ?: expected.lease.reject(IllegalStateException("QA_MAIN_GRAPH_LOST"))

    @Synchronized
    fun requireMainActivityTeardown(activity: MainActivity, expected: QaMainActivityGraph?) {
        mainActivityLoans.requireTeardown(activity, expected?.lease)
    }

    @Synchronized
    fun mainActivityDestroyed(activity: MainActivity, expected: QaMainActivityGraph?) {
        mainActivityLoans.destroyed(activity, expected?.lease)
        expected?.observeDestroyedStore(activity)
        expected?.event(QaMainActivityGraph.Event.DESTROYED)
    }

    @Synchronized
    fun releaseMainActivityGraph(runner: Instrumentation, activity: MainActivity, graph: QaMainActivityGraph,
        resourcesQuiescent: Boolean) {
        check(binding != null && mode == "normal-qa" && runnerIdentity === runner)
        if (reservedMainGraph !== graph) graph.lease.reject(IllegalStateException("QA_MAIN_RELEASE_GRAPH_FOREIGN"))
        graph.lease.close(activity, graph, resourcesQuiescent && graph.modelClosureProven())
        graph.forgetCompletedModels()
        mainActivityLoans.release(graph.lease)
        reservedMainGraph = null
        // A live private Context keeps its deny-only guard; weak keys do not retain completed graphs.
    }


    @Synchronized internal fun beginExplicitHumanSession(runner: Instrumentation, run: String,
        owner: android.app.Activity, session: QaExplicitHumanActionSession) {
        check(binding != null && mode == "normal-qa" && runnerIdentity === runner)
        check(explicitHumanSession == null && explicitHumanOwner == null)
        check(java.util.UUID.fromString(run).toString() == run && session.runId == run)
        check(session.ownerInstance === owner && owner.packageName == QA &&
            owner.javaClass.name == "com.blackatsystems.miguardia.stage12.Stage12HumanSessionActivity" && !owner.isDestroyed && !owner.isFinishing)
        explicitHumanSession = session; explicitHumanOwner = owner
    }
    @Synchronized internal fun endExplicitHumanSession(runner: Instrumentation, session: QaExplicitHumanActionSession,
        owner: android.app.Activity, ownClosureProven: Boolean) {
        check(binding != null && mode == "normal-qa" && runnerIdentity === runner)
        check(explicitHumanSession === session && explicitHumanOwner === owner && owner.isDestroyed && ownClosureProven)
        explicitHumanSession = null; explicitHumanOwner = null
    }
    private fun explicitHumanFor(owner: android.app.Activity): QaExplicitHumanActionSession = synchronized(this) {
        check(binding != null && mode == "normal-qa" && explicitHumanOwner === owner)
        checkNotNull(explicitHumanSession)
    }
    internal fun requireExplicitHumanMap(owner: android.app.Activity, intent: android.content.Intent, requestCode: Int) {
        val session = explicitHumanFor(owner)
        try { session.requireMapLaunch(owner,intent,requestCode) } catch(failure: Throwable) { session.retainFailure(failure); throw failure }
    }
    internal fun requireExplicitHumanPicker(owner: android.app.Activity, intent: android.content.Intent, requestCode: Int) {
        val session = explicitHumanFor(owner)
        try { session.requirePickerLaunch(owner,intent,requestCode) } catch(failure: Throwable) { session.retainFailure(failure); throw failure }
    }
    internal fun explicitHumanPickerReturned(owner: android.app.Activity, requestCode: Int, error: Throwable?) {
        val session = explicitHumanFor(owner)
        try { session.pickerLaunchReturned(owner,requestCode,error) } catch(failure: Throwable) { session.retainFailure(failure); throw failure }
    }
    internal fun explicitHumanPickerResult(owner: android.app.Activity, requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        val session = explicitHumanFor(owner)
        try { session.pickerResult(owner,requestCode,resultCode,data) } catch(failure: Throwable) { session.retainFailure(failure); throw failure }
    }
    internal fun explicitHumanPickerResultForwarded(owner: android.app.Activity, requestCode: Int) {
        val session = explicitHumanFor(owner)
        try { session.pickerResultForwarded(owner,requestCode) } catch(failure: Throwable) { session.retainFailure(failure); throw failure }
    }
    /** Normal product/other modes retain their original behavior; stale human contexts never fall through. */
    fun withExplicitHumanApproximateLocationRequest(context: Context, request: (() -> Unit) -> Unit) {
        val own = context as? QaExplicitHumanActionContext ?: context.applicationContext as? QaExplicitHumanActionContext
        if (own == null) { request({}); return }
        val session = explicitHumanFor(own.humanOwnerInstance)
        try { session.approximateLocationRequest(own) {
            request {
                try {
                    // This callback retains the original capability even after context/global teardown.
                    session.approximateLocationResult(own)
                    check(explicitHumanFor(own.humanOwnerInstance) === session)
                } catch (failure: Throwable) { session.retainFailure(failure); throw failure }
            }
        } }
        catch (failure: Throwable) { session.retainFailure(failure); throw failure }
    }
    private fun requireExplicitHumanAction(context: QaExplicitHumanActionContext, operation: String) {
        val session = explicitHumanFor(context.humanOwnerInstance)
        try {
            check(session === context.explicitHumanSession && session.ownerInstance === context.humanOwnerInstance && !context.humanOwnerInstance.isDestroyed)
            session.requireAction(context,operation)
        } catch(failure: Throwable) { session.retainFailure(failure); throw failure }
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
        val scope = binding ?: run {
            (context as? QaMainActivityFileContext)?.rejectFixtureExternalEffect(operation)
            return false
        }
        check(context.packageName == QA)
        val own = context as? QaMainActivityFileContext ?: context.applicationContext as? QaMainActivityFileContext
        if (own is QaExplicitHumanActionContext) { requireExplicitHumanAction(own, operation); return false }
        own?.rejectFixtureExternalEffect(operation)
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
