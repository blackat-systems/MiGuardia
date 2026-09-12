package com.blackatsystems.miguardia.beta

import java.time.Instant
import com.blackatsystems.miguardia.updates.UpdateTimeBudget

/** Memory-only capability. No receipt, installation id or signature in diagnostics. */
internal class BetaUpdateContext internal constructor(
    internal val owner: Any,
    internal val generation: Long,
    internal val installationHash: String,
    internal val activationId: String,
    internal val receipt: BetaEnvelope,
    val installed: InstalledBetaApp,
    val releaseId: String,
) {
    override fun toString() = "BetaUpdateContext(redacted)"
}

internal class BetaDownloadChallenge(
    val releaseId: String,
    val id: String,
    val challenge: String,
    val expiresAt: Instant,
    internal val budget: UpdateTimeBudget,
    private val context: BetaUpdateContext,
) {
    private var consumed = false
    init {
        BetaReceiptVerifier.identifier(releaseId)
        BetaReceiptVerifier.identifier(id)
        require(challenge.length == 43 && BetaCrypto.decode(challenge).size == 32)
    }
    @Synchronized fun requireFor(context: BetaUpdateContext) {
        require(this.context === context && releaseId == context.releaseId && !consumed)
        budget.requireRemaining()
    }
    @Synchronized fun consumeFor(context: BetaUpdateContext) {
        requireFor(context)
        consumed = true // A lost authorization response must use a new challenge.
    }
    fun proof(activationId: String): String {
        BetaReceiptVerifier.identifier(activationId)
        return "miguardia-beta-download-proof-v1\nactivation_id=$activationId\n" +
            "release_id=$releaseId\nchallenge_id=$id\nchallenge=$challenge\n"
    }
    override fun toString() = "BetaDownloadChallenge(redacted)"
}

internal interface BetaUpdateAccess {
    suspend fun capture(releaseId: String): BetaUpdateContext
    suspend fun validate(context: BetaUpdateContext)
    suspend fun sign(context: BetaUpdateContext, challenge: BetaDownloadChallenge): String
}
