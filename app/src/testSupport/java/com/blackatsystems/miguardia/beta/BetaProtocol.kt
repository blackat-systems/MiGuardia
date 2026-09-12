package com.blackatsystems.miguardia.beta

import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Base64
import java.util.Locale

internal const val FEEDBACK_DELAY_MILLIS = 1_296_000_000L
internal data class InstalledBetaApp(val packageName: String, val certificateSha256: String, val versionCode: Long)
internal data class BetaEnvelope(val kid: String, val payload: String, val signature: String) {
    fun fields(): Map<String, Any?> = linkedMapOf(
        "contract_version" to 1, "alg" to "ES256", "kid" to kid, "signature_format" to "P1363",
        "payload_base64url" to payload, "signature_base64url" to signature,
    )
    override fun toString() = "BetaEnvelope(redacted)"
    companion object {
        fun parse(map: Map<String, Any?>): BetaEnvelope {
            map.exact("contract_version", "alg", "kid", "signature_format", "payload_base64url", "signature_base64url")
            require(map["contract_version"] == 1L && map["alg"] == "ES256" && map["signature_format"] == "P1363")
            return BetaEnvelope(map.string("kid"), map.string("payload_base64url"), map.string("signature_base64url")).also {
                require(Regex("[A-Za-z0-9._-]{1,40}").matches(it.kid))
                require(it.payload.length in 1..2048 && it.signature.length in 1..128)
                require(BetaCrypto.decode(it.signature).size == 64)
            }
        }
    }
}
internal data class BetaActivation(val envelope: BetaEnvelope, val id: String, val activatedAt: Instant, val dueAt: Instant) {
    override fun toString() = "BetaActivation(redacted)"
}

internal object BetaCrypto {
    private val order = BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16)
    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    fun hash(value: String): String = hash(value.toByteArray(Charsets.UTF_8))
    fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun decode(value: String): ByteArray {
        require(value.isNotEmpty() && Regex("[A-Za-z0-9_-]+").matches(value))
        return Base64.getUrlDecoder().decode(value).also { require(encode(it) == value) }
    }
    fun publicKey(spki: ByteArray): PublicKey {
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki)) as ECPublicKey
        require(key.params.order == order && key.params.curve.field.fieldSize == 256)
        require(key.encoded.contentEquals(spki))
        return key
    }
    fun p1363ToDer(raw: ByteArray): ByteArray {
        require(raw.size == 64)
        fun integer(start: Int): ByteArray {
            val value = BigInteger(1, raw.copyOfRange(start, start + 32))
            require(value.signum() > 0 && value < order)
            val bytes = value.toByteArray()
            return byteArrayOf(2, bytes.size.toByte()) + bytes
        }
        val body = integer(0) + integer(32)
        return byteArrayOf(0x30, body.size.toByte()) + body
    }
    fun derToP1363(der: ByteArray): ByteArray {
        require(der.size in 8..72 && der[0] == 0x30.toByte() && (der[1].toInt() and 255) == der.size - 2)
        var offset = 2
        fun integer(): ByteArray {
            require(offset + 2 <= der.size && der[offset++] == 2.toByte())
            val length = der[offset++].toInt() and 255
            require(length in 1..33 && offset + length <= der.size)
            val bytes = der.copyOfRange(offset, offset + length); offset += length
            require(bytes[0].toInt() >= 0)
            require(length == 1 || bytes[0] != 0.toByte() || bytes[1].toInt() < 0)
            val value = BigInteger(bytes)
            require(value.signum() > 0 && value < order)
            val unsigned = if (bytes[0] == 0.toByte()) bytes.drop(1).toByteArray() else bytes
            require(unsigned.size <= 32)
            return ByteArray(32 - unsigned.size) + unsigned
        }
        val raw = integer() + integer()
        require(offset == der.size && p1363ToDer(raw).contentEquals(der))
        return raw
    }
    fun verify(key: PublicKey, payload: ByteArray, raw: ByteArray): Boolean = Signature.getInstance("SHA256withECDSA").run {
        initVerify(key); update(payload); verify(p1363ToDer(raw))
    }
}

internal class BetaReceiptVerifier(private val keys: Map<String, PublicKey>) {
    private fun lines(envelope: BetaEnvelope, type: String, fields: List<String>): Map<String, String> {
        require(Regex("[A-Za-z0-9._-]{1,40}").matches(envelope.kid))
        require(envelope.payload.length in 1..2048 && envelope.signature.length in 1..128)
        val key = keys[envelope.kid] ?: error("Unknown receipt authority")
        BetaCrypto.publicKey(key.encoded)
        val bytes = BetaCrypto.decode(envelope.payload)
        require(BetaCrypto.verify(key, bytes, BetaCrypto.decode(envelope.signature)))
        val text = BetaJson.utf8(bytes)
        require(!text.contains('\r') && !text.contains('\uFEFF'))
        val lines = text.split('\n')
        require(lines.size == fields.size + 4 && lines.last() == "")
        require(lines.take(3) == listOf("miguardia-beta-receipt-v1", "receipt_type=$type", "contract_version=1"))
        return fields.mapIndexed { index, field ->
            val prefix = "$field="
            require(lines[index + 3].startsWith(prefix))
            field to lines[index + 3].removePrefix(prefix)
        }.toMap().also { require(it["kid"] == envelope.kid) }
    }
    fun activation(envelope: BetaEnvelope, installed: InstalledBetaApp, installationHash: String): BetaActivation {
        val values = lines(envelope, "ACTIVATION", listOf("kid", "activation_id", "activated_at", "feedback_due_at",
            "application_id", "release_id", "release_version_code", "release_certificate_sha256", "installation_public_key_sha256"))
        identifier(values.getValue("activation_id")); identifier(values.getValue("release_id"))
        val activated = instant(values.getValue("activated_at"))
        val due = instant(values.getValue("feedback_due_at"))
        require(due == activated.plusMillis(FEEDBACK_DELAY_MILLIS))
        require(values["application_id"] == installed.packageName)
        val version = values.getValue("release_version_code")
        require(Regex("[1-9][0-9]{0,15}").matches(version))
        require(version.toLong() in 1..minOf(installed.versionCode, 9_007_199_254_740_991))
        listOf("release_certificate_sha256", "installation_public_key_sha256").forEach { hex(values.getValue(it)) }
        require(values["release_certificate_sha256"] == installed.certificateSha256)
        require(values["installation_public_key_sha256"] == installationHash)
        return BetaActivation(envelope, values.getValue("activation_id"), activated, due)
    }
    fun feedback(envelope: BetaEnvelope, activationId: String, requestId: String, bodyHash: String) {
        val values = lines(envelope, "FEEDBACK", listOf("kid", "feedback_id", "activation_id", "request_id", "received_at", "body_sha256"))
        identifier(values.getValue("feedback_id")); instant(values.getValue("received_at")); hex(values.getValue("body_sha256"))
        require(values["activation_id"] == activationId && values["request_id"] == requestId && values["body_sha256"] == bodyHash)
    }
    companion object {
        fun identifier(value: String) { require(Regex("[A-Za-z0-9_-]{8,80}").matches(value)) }
        fun hex(value: String) { require(Regex("[a-f0-9]{64}").matches(value)) }
        fun instant(value: String): Instant {
            require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z").matches(value))
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withResolverStyle(ResolverStyle.STRICT)).toInstant(ZoneOffset.UTC)
        }
    }
}

internal enum class BetaCategory(val label: String) { BUG("Algo falló"), USABILITY("Uso de la app"), IDEA("Una idea"), OTHER("Otra opinión") }
internal enum class BetaArea(val label: String) { ACCESS("Acceso"), CALENDAR("Calendario"), HOURS("Horas"), NOTIFICATIONS("Notificaciones"), WEATHER("Clima"), BACKUP("Copias"), OTHER("Otra área") }
internal data class BetaOpinion(val category: BetaCategory, val area: BetaArea, val message: String) {
    fun fields(): Map<String, Any?> = linkedMapOf("category" to category.name, "area" to area.name, "message" to message, "consent" to true)
    val hash: String get() = BetaCrypto.hash(BetaJson.encode(fields()))
    override fun toString() = "BetaOpinion(redacted)"
    companion object {
        private fun ecmaSpace(c: Char) = c in "\u0009\u000B\u000C\u0020\u00A0\uFEFF\n\r\u2028\u2029\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u202F\u205F\u3000"
        fun create(category: BetaCategory, area: BetaArea, message: String, consent: Boolean): BetaOpinion {
            require(consent)
            val normalized = Normalizer.normalize(message, Normalizer.Form.NFC).trim(::ecmaSpace)
            require(normalized.length in 1..2000)
            require(!hasBetaControls(normalized, multiline = true))
            return BetaOpinion(category, area, normalized)
        }
    }
}

internal fun normalizeBetaCode(value: String): String {
    require(value.length <= 80 && !hasBetaControls(value))
    return value.trim().uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "").also {
        require(Regex("MG[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{24}").matches(it))
    }
}
private fun hasBetaControls(value: String, multiline: Boolean = false): Boolean = value.codePoints().anyMatch {
    !(multiline && it in intArrayOf(10, 13, 9)) &&
        Character.getType(it) in intArrayOf(Character.CONTROL.toInt(), Character.FORMAT.toInt())
}
internal fun feedbackProof(activationId: String, requestId: String, bodyHash: String): String =
    "miguardia-beta-feedback-proof-v1\nactivation_id=$activationId\nrequest_id=$requestId\nbody_sha256=$bodyHash\n"
