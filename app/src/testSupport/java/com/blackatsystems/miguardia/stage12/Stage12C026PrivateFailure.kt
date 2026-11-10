package com.blackatsystems.miguardia.stage12

import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.io.Writer
import java.util.Base64
import java.util.UUID

/** One RAM-only diagnostic for the existing C026 failure notification. Never a log Throwable. */
internal class Stage12C026PrivateFailure(private val context: Context) {
    data class Context(val selector: String, val run: String, val qa: String, val runner: String) {
        init {
            require(selector == SELECTOR && UUID.fromString(run).toString() == run)
            require(qa.matches(Regex("[0-9a-f]{64}")) && runner.matches(Regex("[0-9a-f]{64}")))
        }
    }

    private var captured = false
    private var consumed = false
    private var pending: String? = null

    @Synchronized fun capture(current: Context, active: String?, original: Throwable): Boolean {
        if (captured || consumed || current != context || active != SELECTOR) return false
        captured = true
        val writer = BoundedUtf8Writer()
        var state = "COMPLETE"
        try {
            val printer = PrintWriter(writer)
            original.printStackTrace(printer) // Explicit RAM destination, never the default stderr.
            printer.flush()
            writer.finish()
            if (printer.checkError()) state = "WRITER_ERROR"
        } catch (_: LimitReached) {
            state = "TRUNCATED"
        } catch (_: Throwable) {
            state = "WRITER_ERROR" // Capture errors neither escape nor replace the first failure.
        }
        val bytes = writer.bytes()
        pending = listOf("v1", context.selector, context.run, context.qa, context.runner,
            state, bytes.size.toString(), Base64.getEncoder().encodeToString(bytes)).joinToString("|")
        return true
    }

    /** A terminal notification consumes the slot even when its context is rejected. */
    @Synchronized fun consume(current: Context, active: String?, method: String, number: Int,
        code: Int, hasStack: Boolean, keyAlreadyPresent: Boolean): String? {
        if (code == 1 || consumed) return null
        consumed = true
        val value = pending
        pending = null
        return value.takeIf { current == context && active == SELECTOR && method == SELECTOR && number == 1 &&
            code in setOf(-1, -2) && hasStack && !keyAlreadyPresent }
    }

    private class LimitReached : RuntimeException(null, null, false, false)

    /** Encodes whole UTF-8 code points, including pairs split across Writer calls. */
    private class BoundedUtf8Writer : Writer() {
        private val out = ByteArrayOutputStream(MAX_BYTES)
        private var high: Char? = null
        override fun write(chars: CharArray, offset: Int, length: Int) {
            for (i in offset until offset + length) {
                val c = chars[i]
                val previous = high
                high = null
                if (previous != null) {
                    if (c.isLowSurrogate()) { emit(Character.toCodePoint(previous, c)); continue }
                    emit(0xfffd)
                }
                when {
                    c.isHighSurrogate() -> high = c
                    c.isLowSurrogate() -> emit(0xfffd)
                    else -> emit(c.code)
                }
            }
        }
        private fun emit(point: Int) {
            val bytes = String(Character.toChars(point)).toByteArray(Charsets.UTF_8)
            if (out.size() + bytes.size > MAX_BYTES) throw LimitReached()
            out.write(bytes)
        }
        fun finish() { if (high != null) { high = null; emit(0xfffd) } }
        fun bytes(): ByteArray = out.toByteArray()
        override fun flush() = Unit
        override fun close() = Unit
    }

    companion object {
        const val KEY = "stage12C026PrivateFailure"
        const val SELECTOR = "com.blackatsystems.miguardia.stage12.Stage12TemporalLiteralAndroidTest#shiftEndpointsAndHistoricalDetailStayExact"
        const val MAX_BYTES = 32768
        fun publicFailure(): AssertionError = AssertionError("STAGE12_SELECTION_BATCH_FAILED")
    }
}
