package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.QaMainActivityLease
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

/** Actual RAM loan with controlled identities, counters and failures. No Android lifecycle credit. */
class QaMainActivityLeaseTest {
    private val run = "b119c026-0000-4000-8000-000000000003"
    private val generation = UUID.fromString("b119c026-0000-4000-8000-000000000004")
    private val owner = Any()
    private val graph = Any()
    private val activity = Any()
    private fun lease(retain: (Throwable) -> Unit = {}) = QaMainActivityLease(run, owner, generation, graph, retain)
    private fun start(lease: QaMainActivityLease) {
        lease.publish(activity) {}
        lease.begin(activity, graph, owner, generation)
    }
    private fun active(lease: QaMainActivityLease) = lease.requireActive(activity, graph, owner, generation)

    @Test fun completePublicationPrecedesBusinessAndSameInstanceReuseDoesNotRebuild() {
        val lease = lease(); var published = 0; var constructed = 0
        lease.publish(activity) { published++ }
        assertEquals(QaMainActivityLease.Phase.PUBLISHED, lease.phase)
        lease.requireAttached(activity, graph, owner, generation)
        lease.begin(activity, graph, owner, generation)
        lease.requireStoreWitness(activity, graph); active(lease); constructed++
        repeat(3) { active(lease); lease.requireStoreWitness(activity, graph) }
        assertEquals(1, published); assertEquals(1, constructed)
    }

    @Test fun missingPublicationBlocksFactoriesCaptureConsumptionAndFallback() {
        val lease = lease(); var downstream = 0
        assertThrows(IllegalStateException::class.java) { active(lease); downstream++ }
        assertThrows(IllegalStateException::class.java) { lease.publish(activity) { downstream++ } }
        assertEquals(0, downstream)
    }

    @Test fun wrongInstanceGraphOwnerGenerationAndLostLinkAreTerminal() {
        val variants = listOf(Triple(Any(), owner, generation), Triple(graph, Any(), generation),
            Triple(graph, owner, UUID.randomUUID()), Triple(null, owner, generation))
        for ((candidateGraph, candidateOwner, candidateGeneration) in variants) {
            val lease = lease(); start(lease); var downstream = 0
            assertThrows(IllegalStateException::class.java) {
                lease.requireActive(activity, candidateGraph, candidateOwner, candidateGeneration); downstream++
            }
            assertThrows(IllegalStateException::class.java) { active(lease); downstream++ }
            assertEquals(0, downstream)
        }
        val lease = lease(); start(lease)
        assertThrows(IllegalStateException::class.java) { lease.requireActive(Any(), graph, owner, generation) }
        val wrongRun = lease(); start(wrongRun)
        assertThrows(IllegalStateException::class.java) {
            wrongRun.requireActive(activity, graph, owner, generation, "b119c026-0000-4000-8000-000000000099")
        }
    }

    @Test fun factoryCannotRunBeforeCreateAndARejectedCreateCannotResumeBusiness() {
        val lease = lease(); lease.publish(activity) {}; var downstream = 0
        assertThrows(IllegalStateException::class.java) { active(lease); downstream++ }
        assertThrows(IllegalStateException::class.java) { lease.begin(activity, graph, owner, generation); downstream++ }
        assertEquals(0, downstream)
    }

    @Test fun lossAfterAValidStoreWitnessBlocksLaterBusinessAndKeepsOriginalCleanupIdentity() {
        val lease = lease(); start(lease); lease.requireStoreWitness(activity, graph)
        var business = 0; active(lease); business++
        val first = assertThrows(IllegalStateException::class.java) { lease.requireActive(activity, null, owner, generation); business++ }
        assertSame(first, assertThrows(IllegalStateException::class.java) { active(lease); business++ })
        assertEquals(1, business)
        lease.requireTeardown(activity, graph); lease.destroyed(activity, graph); lease.close(activity, graph, true)
        assertSame(first, lease.firstFailure)
    }

    @Test fun foreignOrMissingRetainedWitnessCannotAdoptAnOrdinaryViewModelStore() {
        for (witness in listOf(Any(), null)) {
            val lease = lease(); start(lease); var factories = 0
            assertThrows(IllegalStateException::class.java) { lease.requireStoreWitness(activity, witness); factories++ }
            assertEquals(0, factories)
        }
    }

    @Test fun failedPublicationRetainsOriginalBeforeAnyDependentOperation() {
        val failure = IllegalStateException("CONTROLLED_PUBLICATION_FAILURE")
        val retained = mutableListOf<Throwable>(); val lease = lease { retained += it }; var downstream = 0
        assertSame(failure, assertThrows(IllegalStateException::class.java) { lease.publish(activity) { throw failure } })
        assertSame(failure, retained.single())
        assertEquals(QaMainActivityLease.Phase.PUBLISHING, lease.phase)
        assertSame(failure, assertThrows(IllegalStateException::class.java) { active(lease); downstream++ })
        assertEquals(0, downstream)
        lease.destroyed(activity, graph); lease.close(activity, graph, true)
        assertSame(failure, lease.firstFailure)
    }

    @Test fun publicationReentryOrASwallowedFirstNegativeCannotBecomePublished() {
        val lease = lease(); var nestedPublication = 0
        assertThrows(IllegalStateException::class.java) {
            lease.publish(activity) {
                try { lease.publish(Any()) { nestedPublication++ } } catch (_: IllegalStateException) { }
            }
        }
        assertEquals(0, nestedPublication)
        assertEquals(QaMainActivityLease.Phase.PUBLISHING, lease.phase)
    }

    @Test fun retainerFailureDoesNotReplaceTheFirstFailureAndLaterErrorsKeepOrder() {
        val first = IllegalStateException("FIRST"); val retainerError = IllegalArgumentException("RETAINER")
        val second = IllegalStateException("SECOND"); var calls = 0
        val lease = lease { calls++; if (calls == 1) throw retainerError }
        assertSame(first, assertThrows(IllegalStateException::class.java) { lease.reject(first) })
        assertSame(first, assertThrows(IllegalStateException::class.java) { lease.reject(second) })
        assertEquals(listOf(retainerError, second), first.suppressed.toList())
    }

    @Test fun repeatedPublicationOrCreateCannotRebindToAnotherInstance() {
        for (repeatPublication in listOf(true, false)) {
            val lease = lease(); start(lease); var downstream = 0
            assertThrows(IllegalStateException::class.java) {
                if (repeatPublication) lease.publish(Any()) { downstream++ }
                else lease.begin(activity, graph, owner, generation)
                downstream++
            }
            assertEquals(0, downstream)
        }
    }

    @Test fun closingRequiresDestroyedOwnerAndCompletedJobsAndLateBusinessAlwaysStops() {
        val premature = lease(); start(premature)
        assertThrows(IllegalStateException::class.java) { premature.close(activity, graph, true) }
        val busy = lease(); start(busy); busy.destroyed(activity, graph)
        assertThrows(IllegalStateException::class.java) { busy.close(activity, graph, false) }
        assertEquals(QaMainActivityLease.Phase.DESTROYED, busy.phase)
        val valid = lease(); start(valid); valid.destroyed(activity, graph); valid.close(activity, graph, true)
        assertEquals(QaMainActivityLease.Phase.CLOSED, valid.phase)
        var dependent = 0
        assertThrows(IllegalStateException::class.java) { active(valid); dependent++ }
        assertEquals(0, dependent)
    }

    @Test fun cleanupAfterPrimaryFailureKeepsOriginalIdentityWithoutResumingBusiness() {
        val lease = lease(); start(lease); val first = IllegalStateException("PRIMARY")
        assertThrows(IllegalStateException::class.java) { lease.reject(first) }
        lease.destroyed(activity, graph); lease.close(activity, graph, true)
        assertSame(first, lease.firstFailure)
        assertSame(first, assertThrows(IllegalStateException::class.java) { active(lease) })
    }
}
