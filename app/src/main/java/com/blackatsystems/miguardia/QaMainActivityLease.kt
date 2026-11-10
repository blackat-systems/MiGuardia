package com.blackatsystems.miguardia

import java.util.UUID

/** One finite QA Activity loan. This value cannot be installed by portable or Intent data. */
internal class QaMainActivityLease(
    val run: String,
    val owner: Any,
    val generation: UUID,
    val graph: Any,
    private val retain: (Throwable) -> Unit,
) {
    enum class Phase { RESERVED, PUBLISHING, PUBLISHED, ACTIVE, DESTROYED, CLOSED }
    @Volatile var phase = Phase.RESERVED; private set
    @Volatile var firstFailure: Throwable? = null; private set
    private var instance: java.lang.ref.WeakReference<Any>? = null

    init { check(UUID.fromString(run).toString() == run) { "QA_MAIN_RUN_NOT_CANONICAL" } }

    @Synchronized
    fun reject(error: Throwable): Nothing {
        val first = firstFailure
        if (first != null && (error === first || first.suppressed.any { it === error })) throw first
        if (first == null) firstFailure = error
        else if (error !== first && first.suppressed.none { it === error }) first.addSuppressed(error)
        try { retain(error) } catch (secondary: Throwable) {
            val primary = checkNotNull(firstFailure)
            if (secondary !== primary && primary.suppressed.none { it === secondary }) primary.addSuppressed(secondary)
        }
        throw checkNotNull(firstFailure)
    }

    private fun guard(condition: Boolean, code: String) {
        if (!condition) reject(IllegalStateException(code))
    }
    private fun activeFailure() { firstFailure?.let { throw it } }

    /** The runner publishes exactly the object returned by newActivity, before attach/onCreate. */
    @Synchronized
    fun publish(activity: Any, publish: () -> Unit) {
        activeFailure()
        guard(phase == Phase.RESERVED && instance == null, "QA_MAIN_PUBLICATION_REPEATED")
        instance = java.lang.ref.WeakReference(activity)
        phase = Phase.PUBLISHING
        try { publish() } catch (error: Throwable) { reject(error) }
        activeFailure()
        guard(phase == Phase.PUBLISHING, "QA_MAIN_PUBLICATION_PHASE_CHANGED")
        phase = Phase.PUBLISHED
    }

    @Synchronized
    fun requireAttached(activity: Any, linkedGraph: Any?, linkedOwner: Any?, linkedGeneration: UUID?, linkedRun: String? = run) {
        activeFailure()
        guard(instance?.get() === activity && linkedGraph === graph && linkedOwner === owner && linkedGeneration == generation && linkedRun == run,
            "QA_MAIN_LINK_MISSING_OR_FOREIGN")
        guard(phase == Phase.PUBLISHED || phase == Phase.ACTIVE, "QA_MAIN_LIFETIME_NOT_ACTIVE")
    }

    @Synchronized
    fun begin(activity: Any, linkedGraph: Any?, linkedOwner: Any?, linkedGeneration: UUID?, linkedRun: String? = run) {
        requireAttached(activity, linkedGraph, linkedOwner, linkedGeneration, linkedRun)
        guard(phase == Phase.PUBLISHED, "QA_MAIN_CREATE_REPEATED")
        phase = Phase.ACTIVE
    }

    @Synchronized
    fun requireActive(activity: Any, linkedGraph: Any?, linkedOwner: Any?, linkedGeneration: UUID?, linkedRun: String? = run) {
        requireAttached(activity, linkedGraph, linkedOwner, linkedGeneration, linkedRun)
        guard(phase == Phase.ACTIVE, "QA_MAIN_BEFORE_CREATE")
    }

    /** A retained store may contain only the same graph witness; a foreign witness is never adopted. */
    @Synchronized
    fun requireStoreWitness(activity: Any, witness: Any?) {
        requireActive(activity, graph, owner, generation)
        guard(witness === graph, "QA_MAIN_VIEWMODEL_STORE_FOREIGN")
    }

    /** Teardown keeps the original identity even after a primary failure. It never resumes business. */
    @Synchronized
    fun requireTeardown(activity: Any, linkedGraph: Any?) {
        guard(instance?.get() === activity && linkedGraph === graph, "QA_MAIN_TEARDOWN_IDENTITY_FOREIGN")
        guard(phase == Phase.ACTIVE || phase == Phase.PUBLISHED || phase == Phase.PUBLISHING && firstFailure != null,
            "QA_MAIN_TEARDOWN_LIFETIME_INVALID")
    }

    @Synchronized
    fun destroyed(activity: Any, linkedGraph: Any?) {
        requireTeardown(activity, linkedGraph)
        phase = Phase.DESTROYED
    }

    @Synchronized
    fun close(activity: Any, linkedGraph: Any?, resourcesQuiescent: Boolean) {
        guard(instance?.get() === activity && linkedGraph === graph, "QA_MAIN_CLOSE_IDENTITY_FOREIGN")
        guard(phase == Phase.DESTROYED, "QA_MAIN_CLOSE_BEFORE_DESTROY")
        guard(resourcesQuiescent, "QA_MAIN_CLOSE_WITH_LIVE_RESOURCES")
        phase = Phase.CLOSED
    }
}
