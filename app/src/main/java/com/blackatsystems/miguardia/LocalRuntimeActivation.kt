package com.blackatsystems.miguardia

import android.content.Context
import com.blackatsystems.miguardia.MiGuardiaApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Exterior publication waits for recovery; lock privacy remains the presenter contract. */
internal fun localAllowsPublication(context: Context): Boolean {
    val application = context.applicationContext as? MiGuardiaApplication ?: return false
    return application.startupRecoveryGate.isReady
}

/** A failed transition remains retryable on the next foreground/state event, without polling. */
internal class LocalRuntimeActivation(
    private val isAllowed: () -> Boolean,
    private val start: suspend () -> Unit,
) {
    private val mutex = Mutex()
    private var started = false

    suspend fun pause(block: suspend () -> Unit) = mutex.withLock {
        started = false
        block()
    }

    suspend fun ensureStarted(): Boolean = mutex.withLock {
        if (!isAllowed()) { started = false; return@withLock false }
        if (started) return@withLock true
        try {
            start()
            started = isAllowed()
            started
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }
}
