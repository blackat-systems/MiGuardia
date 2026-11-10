package com.blackatsystems.miguardia.stage12

import com.blackatsystems.miguardia.QaMainActivityLease
import com.blackatsystems.miguardia.QaMainActivityLoans
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class QaMainActivityLoansTest {
    private val run = "b119c026-0000-4000-8000-000000000005"
    private fun lease(run: String = this.run) = QaMainActivityLease(run, Any(), UUID.randomUUID(), Any()) {}

    @Test fun absentOrdinaryBindingUsesTheOrdinaryPathWithoutConstructingAnOwnedGraph() {
        val registry = QaMainActivityLoans(); val activity = Any()
        assertNull(registry.born(activity)); assertNull(registry.begin(activity))
        assertNull(registry.requireActive(activity, null)); registry.destroyed(activity, null)
    }

    @Test fun exactNewInstanceCanReuseTheSameLoanWithoutPublishingAgain() {
        val registry = QaMainActivityLoans(); val loan = lease(); val activity = Any(); var publications = 0
        registry.reserve(loan, run)
        assertSame(loan, registry.born(activity) { publications++ })
        assertSame(loan, registry.begin(activity))
        repeat(3) { assertSame(loan, registry.requireActive(activity, loan)) }
        assertEquals(1, publications)
    }

    @Test fun ordinaryPreexistingReceiverCannotConsumeAnOwnedDestination() {
        val registry = QaMainActivityLoans(); val ordinary = Any()
        registry.born(ordinary); registry.begin(ordinary)
        registry.reserve(lease(), run); var capture = 0
        assertThrows(IllegalStateException::class.java) { registry.requireActive(ordinary, null); capture++ }
        assertEquals(0, capture)
    }

    @Test fun reservationWithoutPublicationAndForeignInstanceStopBeforeFactoriesOrFallback() {
        val registry = QaMainActivityLoans(); val loan = lease(); registry.reserve(loan, run); var downstream = 0
        assertThrows(IllegalStateException::class.java) { registry.begin(Any()); downstream++ }
        assertEquals(0, downstream)
        val registry2 = QaMainActivityLoans(); val loan2 = lease(); registry2.reserve(loan2, run)
        val owned = Any(); registry2.born(owned); registry2.begin(owned)
        assertThrows(IllegalStateException::class.java) { registry2.requireActive(Any(), loan2); downstream++ }
        assertEquals(0, downstream)
    }

    @Test fun partialOrFailedPublicationDoesNotPermitCreateOrOrdinaryFallback() {
        val registry = QaMainActivityLoans(); val loan = lease(); val activity = Any()
        val failure = IllegalStateException("CONTROLLED_PUBLISH_FAILURE"); var business = 0
        registry.reserve(loan, run)
        assertSame(failure, assertThrows(IllegalStateException::class.java) { registry.born(activity) { throw failure } })
        assertSame(failure, assertThrows(IllegalStateException::class.java) { registry.begin(activity); business++ })
        assertEquals(0, business)
    }

    @Test fun wrongRunnerRunCannotReserveOrLaterInstallAuthority() {
        val registry = QaMainActivityLoans(); val loan = lease("b119c026-0000-4000-8000-000000000099")
        assertThrows(IllegalStateException::class.java) { registry.reserve(loan, run) }
        assertNotNull(loan.firstFailure)
        assertThrows(IllegalStateException::class.java) { registry.reserve(loan, loan.run) }
    }

    @Test fun anotherGenerationCannotAdoptTheFirstInstanceOrItsViewModelStore() {
        val registry = QaMainActivityLoans(); val first = lease(); val activity = Any()
        registry.reserve(first, run); registry.born(activity); registry.begin(activity)
        val second = lease(); var downstream = 0
        assertThrows(IllegalStateException::class.java) { registry.requireActive(activity, second); downstream++ }
        assertEquals(0, downstream)
    }

    @Test fun missingClientExpectationCannotReclassifyAnOwnedActivityAsOrdinary() {
        val registry = QaMainActivityLoans(); val loan = lease(); val activity = Any(); var downstream = 0
        registry.reserve(loan, run); registry.born(activity); registry.begin(activity)
        assertThrows(IllegalStateException::class.java) { registry.requireActive(activity, null); downstream++ }
        assertEquals(0, downstream)
    }

    @Test fun releaseRequiresDestroyedOwnerAndQuiescentResourcesAndKeepsTheTombstone() {
        val registry = QaMainActivityLoans(); val loan = lease(); val activity = Any()
        registry.reserve(loan, run); registry.born(activity); registry.begin(activity)
        registry.destroyed(activity, loan); loan.close(activity, loan.graph, true); registry.release(loan)
        var downstream = 0
        assertThrows(IllegalStateException::class.java) { registry.requireActive(activity, loan); downstream++ }
        assertEquals(0, downstream)
        assertThrows(IllegalStateException::class.java) { registry.reserve(lease(), run); downstream++ }
        assertEquals(0, downstream)
    }

    @Test fun prematureReleaseOrAnotherReservationDoesNotSilentlyReplaceTheLoan() {
        val registry = QaMainActivityLoans(); val loan = lease(); registry.reserve(loan, run)
        assertThrows(IllegalStateException::class.java) { registry.release(loan) }
        val registry2 = QaMainActivityLoans(); val first = lease(); registry2.reserve(first, run)
        assertThrows(IllegalStateException::class.java) { registry2.reserve(lease(), run) }
        assertNotNull(first.firstFailure)
        var publication = 0
        assertThrows(IllegalStateException::class.java) { registry2.born(Any()) { publication++ } }
        assertEquals(0, publication)
    }

    @Test fun repeatedMethodDemandStopsTheOriginalLoanBeforeAnyFurtherBusiness() {
        val registry = QaMainActivityLoans(); val loan = lease(); val activity = Any(); var business = 0
        registry.reserve(loan, run); registry.born(activity); registry.begin(activity)
        val first = assertThrows(IllegalStateException::class.java) { registry.expectOwnedMethod(run) }
        assertSame(first, assertThrows(IllegalStateException::class.java) { registry.requireActive(activity, loan); business++ })
        assertEquals(0, business)
    }

    @Test fun demandedMethodWithNoGraphCannotFallBackThroughANewOrExistingOrdinaryActivity() {
        for (existing in listOf(true, false)) {
            val registry = QaMainActivityLoans(); val activity = Any(); var business = 0
            if (existing) { registry.born(activity); registry.begin(activity) }
            registry.expectOwnedMethod(run)
            val first = assertThrows(IllegalStateException::class.java) { registry.begin(activity); business++ }
            assertSame(first, assertThrows(IllegalStateException::class.java) { registry.reserve(lease(), run); business++ })
            assertEquals(0, business); assertFalse(registry.completeOwnedMethod(run))
        }
    }

    @Test fun greenMethodRequiresAnActuallyReleasedHealthyLoanAndOtherwiseKeepsHold() {
        val missing = QaMainActivityLoans(); missing.expectOwnedMethod(run)
        assertFalse(missing.completeOwnedMethod(run))
        val registry = QaMainActivityLoans(); val loan = lease(); val activity = Any()
        registry.expectOwnedMethod(run); registry.reserve(loan, run); registry.born(activity); registry.begin(activity)
        registry.destroyed(activity, loan); loan.close(activity, loan.graph, true); registry.release(loan)
        assertTrue(registry.completeOwnedMethod(run))
        assertThrows(IllegalStateException::class.java) { registry.completeOwnedMethod(run) }
        assertNull(registry.begin(Any()))
        val held = QaMainActivityLoans(); val live = lease(); val receiver = Any(); var business = 0
        held.reserve(live, run); held.born(receiver); held.begin(receiver)
        assertFalse(held.completeOwnedMethod(run))
        assertThrows(IllegalStateException::class.java) { held.requireActive(receiver, live); business++ }
        assertEquals(0, business)
        held.destroyed(receiver, live); live.close(receiver, live.graph, true); held.release(live)
        assertThrows(IllegalStateException::class.java) { held.completeOwnedMethod(run) }
    }
}
