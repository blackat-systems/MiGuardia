package com.blackatsystems.miguardia.beta

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal data class BetaPending(val requestId: String, val opinion: BetaOpinion, val signature: String) {
    override fun toString() = "BetaPending(redacted)"
}
internal data class BetaRecord(
    val installationHash: String? = null,
    val activation: BetaEnvelope? = null,
    val dueObserved: Boolean = false,
    val pending: BetaPending? = null,
    val feedbackReceipt: BetaEnvelope? = null,
    val feedbackRequestId: String? = null,
    val feedbackHash: String? = null,
    val retryAt: Instant? = null,
    val terminalError: Boolean = false,
) {
    override fun toString() = "BetaRecord(redacted)"
}
internal interface BetaStorage {
    suspend fun read(): BetaRecord
    suspend fun write(value: BetaRecord)
}
internal class BetaStore(file: File, scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())) : BetaStorage {
    constructor(context: Context) : this(File(context.noBackupFilesDir, "beta-v1.preferences_pb"))
    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope, produceFile = { file },
    )
    override suspend fun read(): BetaRecord = store.data.first()[key]?.let(BetaRecordCodec::decode) ?: BetaRecord()
    override suspend fun write(value: BetaRecord) { store.edit { it[key] = BetaRecordCodec.encode(value) } }
    private companion object { val key = stringPreferencesKey("record_v1") }
}
internal object BetaRecordCodec {
    fun encode(record: BetaRecord): String = BetaJson.encode(linkedMapOf(
        "version" to 1, "installation_hash" to record.installationHash,
        "activation" to record.activation?.fields(), "due_observed" to record.dueObserved,
        "pending" to record.pending?.let { linkedMapOf("request_id" to it.requestId, "opinion" to it.opinion.fields(), "signature" to it.signature) },
        "feedback_receipt" to record.feedbackReceipt?.fields(), "feedback_request_id" to record.feedbackRequestId,
        "feedback_hash" to record.feedbackHash, "retry_at" to record.retryAt?.toString(), "terminal_error" to record.terminalError,
    ))
    fun decode(text: String): BetaRecord {
        val map = BetaJson.parse(text)
        map.exact("version", "installation_hash", "activation", "due_observed", "pending", "feedback_receipt", "feedback_request_id", "feedback_hash", "retry_at", "terminal_error")
        require(map["version"] == 1L && map["due_observed"] is Boolean && map["terminal_error"] is Boolean)
        fun nullableText(key: String): String? = if (map[key] == null) null else map.string(key)
        val hash = nullableText("installation_hash")?.also(BetaReceiptVerifier::hex)
        val activation = if (map["activation"] == null) null else BetaEnvelope.parse(map.obj("activation"))
        val pending = if (map["pending"] == null) null else map.obj("pending").let { p ->
            p.exact("request_id", "opinion", "signature")
            val opinion = p.obj("opinion"); opinion.exact("category", "area", "message", "consent")
            val normalized = BetaOpinion.create(BetaCategory.valueOf(opinion.string("category")), BetaArea.valueOf(opinion.string("area")), opinion.string("message"), opinion["consent"] == true)
            require(normalized.message == opinion["message"])
            BetaPending(p.string("request_id").also(BetaReceiptVerifier::identifier), normalized, p.string("signature")).also {
                BetaCrypto.p1363ToDer(BetaCrypto.decode(it.signature))
            }
        }
        val receipt = if (map["feedback_receipt"] == null) null else BetaEnvelope.parse(map.obj("feedback_receipt"))
        val requestId = nullableText("feedback_request_id")?.also(BetaReceiptVerifier::identifier)
        val bodyHash = nullableText("feedback_hash")?.also(BetaReceiptVerifier::hex)
        require(activation == null || hash != null)
        require(activation != null || (pending == null && receipt == null && map["due_observed"] == false))
        require((receipt == null && requestId == null && bodyHash == null) || (receipt != null && requestId != null && bodyHash != null && pending == null))
        return BetaRecord(hash, activation, map["due_observed"] as Boolean, pending, receipt, requestId, bodyHash,
            nullableText("retry_at")?.let(Instant::parse), map["terminal_error"] as Boolean)
    }
}
