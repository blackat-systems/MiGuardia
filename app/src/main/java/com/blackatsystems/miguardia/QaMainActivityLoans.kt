package com.blackatsystems.miguardia

import java.util.WeakHashMap

/** Registry for this one QA receiver. Ordinary instances are never promoted by an Intent. */
internal class QaMainActivityLoans {
    private var current: QaMainActivityLease? = null
    private var demandedRun: String? = null
    private var demandFailure: Throwable? = null
    private var released: QaMainActivityLease? = null
    private var everReserved = false
    private var completion: Boolean? = null
    // MainActivity is final and has Object equality. The lease separately checks exact object identity.
    // Weak keys plus a weak instance in the lease retain tombstones only while the actual receiver is live.
    private val owned = WeakHashMap<Any, QaMainActivityLease>()
    private val ordinary = java.util.Collections.newSetFromMap(WeakHashMap<Any, Boolean>())

    @Synchronized
    fun expectOwnedMethod(run: String) {
        if (demandedRun != null || current != null || everReserved) {
            val error = demandFailure ?: IllegalStateException("QA_MAIN_METHOD_ALREADY_DEMANDED").also { demandFailure = it }
            (current ?: released)?.reject(error)
            throw error
        }
        check(java.util.UUID.fromString(run).toString() == run) { "QA_MAIN_DEMAND_RUN_NOT_CANONICAL" }
        demandedRun = run
    }

    private fun missingGraph(): Nothing {
        val failure = demandFailure ?: IllegalStateException("QA_MAIN_DEMANDED_GRAPH_MISSING").also { demandFailure = it }
        throw failure
    }

    @Synchronized
    fun reserve(lease: QaMainActivityLease, runnerRun: String) {
        if (current != null) checkNotNull(current).reject(IllegalStateException("QA_MAIN_LOAN_ALREADY_RESERVED"))
        if (completion != null) lease.reject(IllegalStateException("QA_MAIN_METHOD_ALREADY_SEALED"))
        if (demandedRun == null && !everReserved) expectOwnedMethod(runnerRun)
        demandFailure?.let { lease.reject(it) }
        if (everReserved || demandedRun != runnerRun)
            lease.reject(IllegalStateException("QA_MAIN_SECOND_OR_FOREIGN_METHOD_LOAN"))
        if (lease.run != runnerRun || lease.phase != QaMainActivityLease.Phase.RESERVED || lease.firstFailure != null)
            lease.reject(IllegalStateException("QA_MAIN_RESERVATION_FOREIGN_OR_USED"))
        current = lease
        everReserved = true
    }

    @Synchronized
    fun born(activity: Any, published: () -> Unit = {}): QaMainActivityLease? {
        val lease = current
        if (lease == null) {
            if (demandedRun != null) missingGraph()
            check(activity !in owned) { "QA_MAIN_CLOSED_INSTANCE_CANNOT_BECOME_ORDINARY" }
            ordinary += activity
            return null
        }
        if (activity in ordinary || activity in owned)
            lease.reject(IllegalStateException("QA_MAIN_EXISTING_INSTANCE_CANNOT_REBIND"))
        lease.publish(activity) {
            owned[activity] = lease
            published()
        }
        return lease
    }

    @Synchronized
    fun requireAttached(activity: Any, expected: QaMainActivityLease? = null): QaMainActivityLease? {
        demandFailure?.let { throw it }
        val lease = owned[activity]
        if (lease != null) {
            if (lease !== current || expected != null && expected !== lease)
                lease.reject(IllegalStateException("QA_MAIN_CURRENT_LOAN_FOREIGN_OR_LOST"))
            lease.requireAttached(activity, lease.graph, lease.owner, lease.generation, lease.run)
            return lease
        }
        expected?.reject(IllegalStateException("QA_MAIN_PUBLISHED_LINK_LOST"))
        current?.reject(IllegalStateException("QA_MAIN_RECEIVER_OUTSIDE_RESERVED_LOAN"))
        if (demandedRun != null) missingGraph()
        // No QA reservation: this is the unchanged ordinary receiver path.
        ordinary += activity
        return null
    }

    @Synchronized
    fun begin(activity: Any): QaMainActivityLease? = requireAttached(activity)?.also {
        it.begin(activity, it.graph, it.owner, it.generation, it.run)
    }

    @Synchronized
    fun requireActive(activity: Any, expected: QaMainActivityLease?): QaMainActivityLease? {
        if (owned[activity] != null && expected == null)
            checkNotNull(owned[activity]).reject(IllegalStateException("QA_MAIN_CLIENT_EXPECTATION_LOST"))
        return requireAttached(activity, expected)?.also { it.requireActive(activity, it.graph, it.owner, it.generation, it.run) }
    }

    /** Only identity is inspected during destruction; an existing primary failure is preserved. */
    @Synchronized
    fun requireTeardown(activity: Any, expected: QaMainActivityLease?) {
        if (expected == null) {
            if (owned[activity] != null || current != null)
                (owned[activity] ?: checkNotNull(current)).reject(IllegalStateException("QA_MAIN_TEARDOWN_EXPECTATION_LOST"))
            return
        }
        if (owned[activity] !== expected || current !== expected)
            expected.reject(IllegalStateException("QA_MAIN_TEARDOWN_LOAN_FOREIGN"))
        expected.requireTeardown(activity, expected.graph)
    }

    @Synchronized
    fun destroyed(activity: Any, expected: QaMainActivityLease?) {
        requireTeardown(activity, expected)
        expected?.destroyed(activity, expected.graph)
    }

    @Synchronized
    fun release(lease: QaMainActivityLease) {
        if (current !== lease || lease.phase != QaMainActivityLease.Phase.CLOSED)
            lease.reject(IllegalStateException("QA_MAIN_RELEASE_BEFORE_CLOSE_OR_FOREIGN"))
        // Retain the per-instance tombstone; a late callback must never select ordinary storage.
        current = null
        released = lease
    }

    /** False preserves the demanded scope and original loan as HOLD; it never repairs or releases it. */
    @Synchronized
    fun completeOwnedMethod(run: String): Boolean {
        check(completion == null) { "QA_MAIN_METHOD_SEALED_TWICE" }
        val loan = released
        val complete = demandedRun == run && current == null && demandFailure == null && loan != null &&
            loan.run == run && loan.phase == QaMainActivityLease.Phase.CLOSED && loan.firstFailure == null
        completion = complete
        if (complete) { demandedRun = null; released = null }
        else {
            val error = (current ?: released)?.firstFailure ?: demandFailure ?: IllegalStateException("QA_MAIN_METHOD_CLOSURE_INCOMPLETE")
            demandFailure = error
            try { (current ?: released)?.reject(error) } catch (_: Throwable) { /* Boolean false retains HOLD and the original failure. */ }
        }
        return complete
    }
}
