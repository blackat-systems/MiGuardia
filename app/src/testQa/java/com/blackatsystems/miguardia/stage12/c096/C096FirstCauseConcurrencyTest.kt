package com.blackatsystems.miguardia.stage12.c096

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

/** Exercises the real QA ledger; no Activity, Android API, sleeps or replacement state model. */
class C096FirstCauseConcurrencyTest {
    private fun await(latch:CountDownLatch){assertTrue("latch bound",latch.await(3,TimeUnit.SECONDS))}
    private fun start(failure:AtomicReference<Throwable?>,body:()->Unit)=Thread{
        try{body()}catch(error:Throwable){failure.compareAndSet(null,error)}
    }.apply{isDaemon=true;start()}
    private fun join(thread:Thread,failure:AtomicReference<Throwable?>){
        thread.join(3_000);assertFalse("thread bound",thread.isAlive)
        failure.get()?.let{throw AssertionError("worker failure",it)}
    }
    private fun assertStopped(attempt:C096NativeDiagnostics.ImageAttempt){
        try{attempt.active();fail("terminal ledger became active")}
        catch(error:C096NativeDiagnostics.ImageFailure){assertEquals("C096_IMAGE_AFTER_FAILURE",error.code)}
    }
    @Test fun hostRetentionClosesLedgerAtomicallyBeforeLateProvider(){
        val attempt=C096NativeDiagnostics.ImageAttempt()
        val callbackEntered=CountDownLatch(1);val providerTrying=CountDownLatch(1)
        val failure=AtomicReference<Throwable?>();val retained=AtomicReference<String?>()
        val host=start(failure){attempt.retainFirst{
            assertTrue("retention must hold provider's ledger monitor",Thread.holdsLock(attempt))
            retained.set("C096_HOST_TIMEOUT");callbackEntered.countDown();await(providerTrying)
            assertNull(attempt.failure())
        }}
        val provider=start(failure){await(callbackEntered);providerTrying.countDown();attempt.failed("C096_IMAGE_PROVIDER_REJECTED")}
        join(host,failure);join(provider,failure)
        assertEquals("C096_HOST_TIMEOUT",retained.get());assertNull(attempt.failure());assertStopped(attempt)
        attempt.failed("C096_IMAGE_PICKER_FAILED");assertNull(attempt.failure())
        attempt.retainFirst{assertTrue(Thread.holdsLock(attempt));assertEquals("C096_HOST_TIMEOUT",retained.get())}
        assertNull(attempt.failure())
    }
    @Test fun providerFailureBeforeHostClosureRemainsPrimary(){
        val attempt=C096NativeDiagnostics.ImageAttempt()
        val providerReturned=CountDownLatch(1);val failure=AtomicReference<Throwable?>()
        val retained=AtomicReference<String?>()
        val provider=start(failure){attempt.failed("C096_IMAGE_PROVIDER_REJECTED");providerReturned.countDown()}
        val host=start(failure){await(providerReturned);attempt.retainFirst{
            assertTrue("retention must hold provider's ledger monitor",Thread.holdsLock(attempt))
            retained.set(attempt.failure()?:"C096_HOST_TIMEOUT")
        }}
        join(provider,failure);join(host,failure)
        assertEquals("C096_IMAGE_PROVIDER_REJECTED",retained.get())
        assertEquals(retained.get(),attempt.failure());assertStopped(attempt)
        attempt.failed("C096_IMAGE_PICKER_FAILED");attempt.stop()
        attempt.retainFirst{assertTrue(Thread.holdsLock(attempt))}
        assertEquals("C096_IMAGE_PROVIDER_REJECTED",attempt.failure())
    }
}
