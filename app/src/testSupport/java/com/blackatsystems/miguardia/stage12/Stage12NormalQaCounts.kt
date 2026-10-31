package com.blackatsystems.miguardia.stage12

/** Pure exact-method accounting. Closing seals the stream; it never grants a run. */
internal class Stage12NormalQaCounts(private val expected: Set<String>) {
    private val started = mutableSetOf<String>()
    private val passed = mutableSetOf<String>()
    private val failed = mutableSetOf<String>()
    private val skipped = mutableSetOf<String>()
    private var malformed = false
    private var closed = false

    init { require(expected.isNotEmpty() && expected.size <= 256) }

    @Synchronized fun observe(id: String, numberOfTests: Int, code: Int, hasStack: Boolean) {
        check(!closed) { "Status after method stream closed" }
        if (id !in expected || numberOfTests != expected.size || hasStack) malformed = true
        when (code) {
            1 -> if (!started.add(id) || id in passed || id in failed || id in skipped) malformed = true
            0 -> if (id !in started || !passed.add(id) || id in failed || id in skipped) malformed = true
            -3, -4 -> { if (!skipped.add(id) || id in passed || id in failed) malformed = true }
            -1, -2 -> { if (!failed.add(id) || id in passed || id in skipped) malformed = true }
            else -> { malformed = true; failed.add(id) }
        }
    }

    @Synchronized fun finish(resultOkay: Boolean): Snapshot {
        check(!closed) { "Method stream closed twice" }
        closed = true
        return Snapshot(expected.size, started.size, passed.size, failed.size, skipped.size, malformed,
            resultOkay && !malformed && started == expected && passed == expected && failed.isEmpty() && skipped.isEmpty())
    }

    data class Snapshot(val expected: Int, val started: Int, val passed: Int, val failed: Int,
                        val skipped: Int, val malformed: Boolean, val complete: Boolean)
}
