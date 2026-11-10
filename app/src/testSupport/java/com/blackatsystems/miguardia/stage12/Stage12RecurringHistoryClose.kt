package com.blackatsystems.miguardia.stage12

/** The owned history writer closes once, preserving the first original error in RAM. */
internal object Stage12RecurringHistoryClose {
    fun run(body: () -> Unit, finishTransaction: () -> Unit, close: () -> Unit) {
        var primary: Throwable? = null
        fun retain(error: Throwable) {
            val first = primary
            if (first == null) primary = error
            else if (first !== error && first.suppressed.none { it === error }) first.addSuppressed(error)
        }
        try { body() } catch (error: Throwable) { retain(error) }
        try { finishTransaction() } catch (error: Throwable) { retain(error) }
        try { close() } catch (error: Throwable) { retain(error) }
        primary?.let { throw it }
    }
}
