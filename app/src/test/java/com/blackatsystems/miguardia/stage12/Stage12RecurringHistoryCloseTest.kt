package com.blackatsystems.miguardia.stage12

import org.junit.Assert.*
import org.junit.Test

class Stage12RecurringHistoryCloseTest {
    private class Failure : RuntimeException()
    private fun exercise(bodyError: Throwable?, checkError: Throwable?, endError: Throwable?, closeError: Throwable?, active: Boolean): Pair<Throwable?, List<String>> {
        val calls = mutableListOf<String>()
        val error = try {
            Stage12RecurringHistoryClose.run(
                body = { calls += "body"; bodyError?.let { throw it } },
                finishTransaction = {
                    calls += "inTransaction"; checkError?.let { throw it }
                    if (active) { calls += "endTransaction"; endError?.let { throw it } }
                },
                close = { calls += "close"; closeError?.let { throw it } },
            )
            null
        } catch (error: Throwable) { error }
        assertEquals(listOf("body", "inTransaction") + (if (active && checkError == null) listOf("endTransaction") else emptyList()) + "close", calls)
        assertEquals(calls.size, calls.distinct().size)
        return error to calls
    }
    private fun expect(primary: Throwable?, secondary: List<Throwable>, body: Throwable?, check: Throwable?, end: Throwable?, close: Throwable?, active: Boolean = true) {
        val (actual, _) = exercise(body, check, end, close, active)
        assertSame(primary, actual)
        assertEquals(secondary.size, actual?.suppressed?.size ?: 0)
        secondary.forEachIndexed { index, value -> assertSame(value, actual!!.suppressed[index]) }
    }
    @Test fun bodyPrimarySurvivesEveryEndAndCloseCombination() {
        for (endFails in listOf(false, true)) for (closeFails in listOf(false, true)) {
            val body = Failure(); val end = if (endFails) Failure() else null; val close = if (closeFails) Failure() else null
            expect(body, listOfNotNull(end, close), body, null, end, close)
        }
    }
    @Test fun transactionCheckFailurePreservesBodyAndStillCloses() {
        for (closeFails in listOf(false, true)) {
            val body = Failure(); val check = Failure(); val close = if (closeFails) Failure() else null
            expect(body, listOfNotNull(check, close), body, check, Failure(), close)
        }
    }
    @Test fun normalBodyUsesFirstEndFailureAsPrimary() {
        for (closeFails in listOf(false, true)) {
            val end = Failure(); val close = if (closeFails) Failure() else null
            expect(end, listOfNotNull(close), null, null, end, close)
        }
    }
    @Test fun normalBodyUsesCheckFailureAndStillCloses() {
        for (closeFails in listOf(false, true)) {
            val check = Failure(); val close = if (closeFails) Failure() else null
            expect(check, listOfNotNull(close), null, check, Failure(), close)
        }
    }
    @Test fun normalBodyCloseAloneIsPrimary() { val close = Failure(); expect(close, emptyList(), null, null, null, close) }
    @Test fun inactiveTransactionSkipsEndAndStillClosesOnce() {
        val body = Failure(); val close = Failure(); expect(body, listOf(close), body, null, Failure(), close, false)
        expect(null, emptyList(), null, null, Failure(), null, false)
    }
    @Test fun sameErrorInstanceNeverSuppressesItselfOrDuplicatesSecondary() {
        val body = Failure(); expect(body, emptyList(), body, null, body, body)
        val first = Failure(); val secondary = Failure(); expect(first, listOf(secondary), first, null, secondary, secondary)
        val closePrimary = Failure(); expect(closePrimary, emptyList(), null, null, closePrimary, closePrimary)
    }
    @Test fun existingSuppressedOrderIsPreservedWithoutDuplication() {
        val body = Failure(); val old = Failure(); val close = Failure(); body.addSuppressed(old)
        expect(body, listOf(old, close), body, null, old, close)
    }
    @Test fun allNormalReturnsWithoutAnError() { expect(null, emptyList(), null, null, null, null) }
}
