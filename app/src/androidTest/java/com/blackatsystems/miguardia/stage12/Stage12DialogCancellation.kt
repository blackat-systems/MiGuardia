package com.blackatsystems.miguardia.stage12

/** Only the owned Selection dialog dismiss. Every UI callback is bound to one qualified selector. */
internal class Stage12DialogCancellation {
    enum class Context { BACKFILL, OCCUPIED, REMOVE_DAY_OFF, BATCH_REVIEW, WARNINGS }
    private val observations = mutableListOf<MutableMap<String, Any>>()
    private var halted = false

    fun cancel(context: Context, compatible: () -> Boolean, scopeCount: () -> Int,
        candidateCount: () -> Int, visibleQuery: () -> Boolean, scroll: () -> Unit,
        visible: () -> Boolean, enabled: () -> Boolean, interact: () -> Unit, postcondition: () -> Boolean) {
        check(!halted)
        val event = linkedMapOf<String, Any>("context" to context.name, "phase" to "CONTEXT")
        observations += event
        try {
            val contextOk = compatible()
            event["context_compatible"] = contextOk
            check(contextOk)
            event["phase"] = "SCOPE"
            val scopes = scopeCount()
            event["scope_count"] = scopes
            check(scopes == 1)
            event["phase"] = "CANDIDATE"
            val candidates = candidateCount()
            event["candidate_count"] = candidates
            check(candidates == 1)
            event["phase"] = "VISIBILITY_QUERY"
            val displayed = visibleQuery()
            event["visible_before"] = displayed
            event["decision"] = if (displayed) "TOUCH" else "SCROLL_THEN_TOUCH"
            if (!displayed) {
                event["phase"] = "SCROLL"
                event["scroll_started"] = true
                scroll()
                event["scroll_returned"] = true
            }
            event["phase"] = "VISIBLE"
            val displayedAfter = visible()
            event["visible"] = displayedAfter
            check(displayedAfter)
            event["phase"] = "ENABLED"
            val enabledNow = enabled()
            event["enabled"] = enabledNow
            check(enabledNow)
            event["phase"] = "ACTION"
            event["action_started"] = true
            interact()
            event["action_returned"] = true
            event["phase"] = "POST"
            val postOk = postcondition()
            event["postcondition"] = postOk
            check(postOk)
            event["phase"] = "COMPLETE"
        } catch (error: Throwable) {
            halted = true
            throw error
        }
    }

    fun projection(): Map<String, Any> = linkedMapOf("schema" to "stage12-selection-dialog-cancel-v1",
        "observations" to observations.map { it.toMap() })
}
