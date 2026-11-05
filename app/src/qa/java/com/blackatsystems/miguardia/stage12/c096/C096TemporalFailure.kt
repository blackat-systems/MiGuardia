package com.blackatsystems.miguardia.stage12.c096

/** A recorder failure is secondary; it must never replace the first negative. */
internal class C096TemporalFailure(private val retain: (Throwable) -> Unit) {
    private var first: Throwable? = null
    @Synchronized fun remember(error: Throwable): Throwable {
        val original = first
        if (original == null) {
            first = error
            try { retain(error) } catch (secondary: Throwable) {
                if (secondary !== error && error.suppressed.none { it === secondary }) error.addSuppressed(secondary)
            }
        } else if (original !== error && original.suppressed.none { it === error }) original.addSuppressed(error)
        return checkNotNull(first)
    }
    fun reject(error: Throwable): Nothing = throw remember(error)
    @Synchronized fun requireActive() { first?.let { throw it } }
    /** Readiness can itself retain an asynchronous negative; dispatch must recheck after it. */
    fun <T, R> inputAfterReadiness(readiness: () -> T, dispatch: (T) -> R): R = try {
        requireActive()
        val input = readiness()
        requireActive()
        val result = dispatch(input)
        requireActive()
        result
    } catch (error: Throwable) { reject(error) }
}
