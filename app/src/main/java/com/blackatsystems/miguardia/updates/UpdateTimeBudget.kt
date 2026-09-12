package com.blackatsystems.miguardia.updates

/** Local memory-only ceiling, not proof of the server's absolute expiry. Never restarted on receipt. */
internal class UpdateTimeBudget private constructor(private val nanoTime: () -> Long, private val started: Long) {
    private fun remainingNanos(): Long {
        val elapsed = nanoTime() - started
        require(elapsed >= 0 && elapsed < LIFETIME_NANOS) { "Update request budget exhausted" }
        return LIFETIME_NANOS - elapsed
    }
    fun requireRemaining() { remainingNanos() }
    fun remainingMillis(): Long = (remainingNanos() / 1_000_000L).also { require(it > 0) }
    override fun toString() = "UpdateTimeBudget(memory-only)"

    companion object {
        private const val LIFETIME_NANOS = 300_000_000_000L
        fun start(nanoTime: () -> Long = System::nanoTime) = UpdateTimeBudget(nanoTime, nanoTime())
    }
}
