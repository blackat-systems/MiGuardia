package com.blackatsystems.miguardia

/** Fail closed before Application startup effects, including package replacement and process death. */
internal object QaBootstrapPolicy {
    fun isIsolationMode(mode: String?): Boolean = mode == "neutral" || mode == "quiet"

    fun requireRunnerMode(mode: String, runnerName: String) {
        require(mode in setOf("neutral", "quiet", "normal-qa"))
        val expected = if (mode == "normal-qa")
            "com.blackatsystems.miguardia.stage12.Stage12NormalQaRunner"
        else "com.blackatsystems.miguardia.stage12.Stage12Runner"
        check(runnerName == expected) { "Runner is not admitted for this mode" }
    }

    fun requireAdmission(harnessBuild: Boolean, bound: Boolean) {
        if (harnessBuild && !bound) {
            throw SecurityException("Stage12 harness process has no admitted instrumentation scope")
        }
    }
}
