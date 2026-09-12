package com.blackatsystems.miguardia.updates

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine

/** One process-wide lease is retained while ANY abandoned provider call is still running.
 * Neither coroutine cancellation nor a timeout can kill arbitrary Binder/native IO.
 * Separate bounded lanes let cancellation/close run without blocking the caller or timer.
 */
internal class UnreleasedUpdateResource : IOException("Document resource release could not be confirmed")

internal class UpdateSaveExecutor(private val nanoTime: () -> Long = System::nanoTime) {
    private val admission = Semaphore(1)
    private fun lane(name: String) = ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
        ArrayBlockingQueue<Runnable>(1), { task -> Thread(task, name).apply { isDaemon = true } })
        .apply { allowCoreThreadTimeOut(true) }
    private val io = lane("update-save-io")
    private val abort = lane("update-save-abort")
    private val close = lane("update-save-close")
    private val timer = ScheduledThreadPoolExecutor(1) { task ->
        Thread(task, "update-save-deadline").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true; setKeepAliveTime(10, TimeUnit.SECONDS); allowCoreThreadTimeOut(true) }

    suspend fun <T> run(document: UpdateDocument, timeoutMillis: Long, allowed: () -> Boolean,
        work: suspend (Operation<T>) -> T): T = suspendCancellableCoroutine { continuation ->
        if (!admission.tryAcquire()) {
            continuation.resumeWith(Result.failure(IOException("Previous document provider operation is still pending")))
            return@suspendCancellableCoroutine
        }
        val operation = Operation(continuation, document, allowed, timeoutMillis)
        continuation.invokeOnCancellation { operation.revoke() }
        val deadline = timer.schedule({
            operation.revoke()
            operation.deliver(Result.failure(IOException("Document provider deadline exceeded")))
        }, timeoutMillis, TimeUnit.MILLISECONDS)
        io.execute {
            val result = runCatching { runBlocking { operation.checkActive(); work(operation) } }
            deadline.cancel(false)
            operation.workerFinished(result)
        }
    }

    internal inner class Operation<T>(
        private val continuation: CancellableContinuation<T>,
        private val document: UpdateDocument,
        private val allowed: () -> Boolean,
        timeoutMillis: Long,
    ) {
        private val started = nanoTime()
        private val budgetNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        private fun expired() = nanoTime() - started >= budgetNanos
        private val lock = Any()
        private var revoked = false
        private var finished = false
        private var delivered = false
        private var pending = 1
        private var uncertainClose = false
        private val handles = mutableListOf<OwnedHandle>()

        fun checkActive() {
            if (expired() || synchronized(lock) { revoked } || !continuation.isActive || !allowed())
                throw IOException("Document operation is no longer allowed")
        }
        fun stillAllowed(): Boolean = !expired() && synchronized(lock) { !revoked } && continuation.isActive && allowed()

        suspend fun <R> using(resource: Closeable, block: suspend () -> R): R {
            val owned = OwnedHandle(resource)
            synchronized(lock) { handles.add(owned) }
            try { checkActive(); return block() }
            finally {
                owned.closeOnce()
                synchronized(lock) { handles.remove(owned) }
            }
        }

        private inner class OwnedHandle(private val resource: Closeable) {
            private val claimed = AtomicBoolean()
            fun closeOnce() {
                if (!claimed.compareAndSet(false, true)) return
                try { resource.close() }
                catch (error: Throwable) {
                    // We cannot prove a hostile resource was freed after a failed close.
                    // Keep the one lease until process restart, rather than leak on retries.
                    synchronized(lock) { uncertainClose = true }
                    throw error
                }
            }
        }

        fun revoke() {
            synchronized(lock) {
                if (revoked || finished) return
                revoked = true
                pending += 2
            }
            abort.execute { try { document.cancelPendingIo() } catch (_: Exception) { } finally { taskFinished() } }
            close.execute {
                try {
                    val current = synchronized(lock) { handles.toList() }
                    current.forEach { runCatching { it.closeOnce() } }
                } finally { taskFinished() }
            }
        }
        fun deliver(result: Result<T>) {
            synchronized(lock) { if (delivered) return; delivered = true }
            continuation.resumeWith(result)
        }
        fun workerFinished(result: Result<T>) {
            val permitted = !expired() && continuation.isActive && allowed()
            val final = synchronized(lock) {
                finished = true
                if (result.exceptionOrNull() is UnreleasedUpdateResource) uncertainClose = true
                if (revoked || !permitted) Result.failure<T>(IOException("Document operation was cancelled or expired")) else result
            }
            taskFinished()
            deliver(final)
        }
        private fun taskFinished() {
            synchronized(lock) {
                pending--
                if (pending == 0 && !uncertainClose) admission.release()
            }
        }
    }
    companion object { val process = UpdateSaveExecutor() }
}
