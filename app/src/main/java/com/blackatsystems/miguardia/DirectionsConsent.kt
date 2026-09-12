package com.blackatsystems.miguardia

/** Created after gated lookup; never restored from an Intent or saved state. */
internal class DirectionsConsent(val address: String) {
    private var consumed = false
    fun cancel() { consumed = true }
    fun confirm(allowed: Boolean, send: (String) -> Unit) {
        if (consumed) return
        consumed = true
        if (allowed) send(address)
    }
    override fun toString() = "DirectionsConsent(redacted)"
}
