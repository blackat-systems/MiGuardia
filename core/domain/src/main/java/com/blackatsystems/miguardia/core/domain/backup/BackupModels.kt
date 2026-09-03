package com.blackatsystems.miguardia.core.domain.backup

import java.io.IOException

/** Public, provider-independent contract for a MiGuardia logical backup. */
object MiGuardiaBackupContract {
    const val FILE_EXTENSION: String = ".miguardia-backup"
    const val MIME_TYPE: String = "application/vnd.blackatsystems.miguardia.backup"
    const val FORMAT_VERSION: Int = 1
    const val MIN_READER_VERSION: Int = 1
    const val ROOM_VERSION: Int = 6
    const val ROOM_IDENTITY_HASH: String = "7eb39f6fab5a44e69350e206716554be"
    const val LEGACY_ROOM_VERSION_V5: Int = 5
    const val LEGACY_ROOM_IDENTITY_HASH_V5: String = "77adbc875d0f4ee466cdbd0dd74d5c5c"
    const val PBKDF2_ITERATIONS: Int = 310_000
    const val AES_KEY_BITS: Int = 256
    const val GCM_TAG_BITS: Int = 128
    const val SALT_BYTES: Int = 16
    const val NONCE_BYTES: Int = 12
    const val DISMISSED_EVENT_KEYS_PREFERENCE: String = "notifications.dismissed_event_keys"

    const val MAX_CONTAINER_BYTES: Long = 512L * 1024L * 1024L
    const val MAX_LOGICAL_BYTES: Long = 64L * 1024L * 1024L
    const val MAX_PREFERENCES_BYTES: Long = 4L * 1024L * 1024L
    const val MAX_SINGLE_PHOTO_BYTES: Long = 32L * 1024L * 1024L
    const val MAX_ALL_PHOTOS_BYTES: Long = 384L * 1024L * 1024L
    const val MAX_PHOTO_COUNT: Int = 2_000
    const val MAX_TABLE_ROWS: Int = 250_000
    const val MAX_TOTAL_ROWS: Int = 750_000
    const val MAX_TEXT_BYTES: Int = 1 * 1024 * 1024
    /** Dismissed notification identities are the only portable list allowed beyond 128 values. */
    const val MAX_DISMISSED_EVENT_KEYS: Int = 16_384
    /** Beyond this, merge is disabled while full replacement remains available. */
    const val MAX_MERGE_CONFLICTS: Int = 10_000
    const val MAX_ZIP_EXPANSION_RATIO: Long = 200L
    const val ZIP_EXPANSION_MARGIN_BYTES: Long = 8L * 1024L * 1024L

    fun maximumExpandedPayloadBytes(containerBytes: Long): Long = runCatching {
        Math.addExact(
            Math.multiplyExact(containerBytes.coerceAtLeast(0L), MAX_ZIP_EXPANSION_RATIO),
            ZIP_EXPANSION_MARGIN_BYTES,
        )
    }.getOrElse { Long.MAX_VALUE }.coerceAtMost(MAX_CONTAINER_BYTES)
}

sealed interface BackupValue {
    data object Null : BackupValue
    data class Text(val value: String) : BackupValue
    data class Integer(val value: Long) : BackupValue
    data class Real(val value: Double) : BackupValue

    class Binary(value: ByteArray) : BackupValue {
        val value: ByteArray = value.copyOf()

        override fun equals(other: Any?): Boolean =
            other is Binary && value.contentEquals(other.value)

        override fun hashCode(): Int = value.contentHashCode()

        override fun toString(): String = "Binary(${value.size} bytes)"
    }
}

data class BackupRecord(
    val values: List<BackupValue>,
)

data class BackupTable(
    val name: String,
    val columns: List<String>,
    val primaryKey: List<String>,
    val records: List<BackupRecord>,
)

data class BackupDatabaseSnapshot(
    val roomVersion: Int = MiGuardiaBackupContract.ROOM_VERSION,
    val roomIdentityHash: String = MiGuardiaBackupContract.ROOM_IDENTITY_HASH,
    val timelineId: String?,
    val tables: List<BackupTable>,
) {
    val totalRows: Int get() = tables.sumOf { it.records.size }
    val isEmpty: Boolean get() = totalRows == 0

    fun table(name: String): BackupTable = tables.single { it.name == name }
}

enum class BackupPreferenceType {
    BOOLEAN,
    LONG,
    TEXT,
    TEXT_LIST,
}

data class BackupPreference(
    val key: String,
    val type: BackupPreferenceType,
    /** Canonical scalar text or a length-prefixed list encoded by the payload codec. */
    val values: List<String>,
)

enum class BackupPhotoMode {
    INCLUDED,
    OMITTED,
}

data class BackupPhotoMetadata(
    val recordId: String,
    val storageKey: String,
    val mimeType: String,
    val byteSize: Long,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val sha256: String,
)

data class BackupPayload(
    val database: BackupDatabaseSnapshot,
    val preferences: List<BackupPreference>,
    val photoMode: BackupPhotoMode,
    val photos: List<BackupPhotoMetadata>,
)

data class BackupEntryManifest(
    val name: String,
    val uncompressedBytes: Long,
    val sha256: String,
)

data class BackupManifest(
    val backupId: String,
    val createdAtEpochMillis: Long,
    val zoneId: String,
    val roomVersion: Int,
    val timelineId: String?,
    val photoMode: BackupPhotoMode,
    val tableCounts: Map<String, Int>,
    val entries: List<BackupEntryManifest>,
)

data class BackupPreview(
    val manifest: BackupManifest,
    val historicalSectors: List<String>,
    val currentCounts: Map<String, Int>,
    val incomingCounts: Map<String, Int>,
    val newRecords: Int,
    val identicalRecords: Int,
    val conflicts: List<BackupConflict>,
    val photosInBackup: Int,
    val photosMissingFromBackup: Int,
    val timelineCompatible: Boolean,
    val destinationEmpty: Boolean,
    /** Non-null keeps Replace available but disables an unsafe or impractical merge. */
    val mergeBlockedReason: String? = null,
    /** Current Room rows whose exact primary-key/content pair is absent from the replacement. */
    val currentRecordsRemovedOrReplaced: Int = 0,
    /** Current portable preferences whose exact key/value pair is absent from the replacement. */
    val currentPreferencesRemovedOrReplaced: Int = 0,
    /** Semantic preferences contained in the validated replacement. */
    val incomingPreferenceCount: Int = 0,
)

data class BackupComparison(
    val timelineCompatible: Boolean,
    val destinationEmpty: Boolean,
    val newRecords: Int,
    val identicalRecords: Int,
    val conflicts: List<BackupConflict>,
    /** Non-null keeps Replace available but disables an unsafe or impractical merge. */
    val mergeBlockedReason: String? = null,
)

enum class BackupRecordClassification {
    NEW,
    IDENTICAL,
    CONFLICT,
    SIGNIFICANT_OVERLAP,
    INVALID,
}

enum class BackupConflictResolution {
    KEEP_CURRENT,
    USE_BACKUP,
    KEEP_BOTH,
}

data class BackupRecordKey(
    val table: String,
    val primaryKeyValues: List<BackupValue>,
) {
    fun stableText(): String = buildString {
        append(table)
        primaryKeyValues.forEach { value ->
            append('|')
            append(value.stableText())
        }
    }
}

data class BackupConflict(
    val id: String,
    val classification: BackupRecordClassification,
    val table: String,
    val currentKey: BackupRecordKey,
    val incomingKey: BackupRecordKey,
    val keepBothAllowed: Boolean,
    val summary: String,
    val currentDescription: String? = null,
    val incomingDescription: String? = null,
)

data class ResolvedBackupConflict(
    val conflictId: String,
    val resolution: BackupConflictResolution,
)

open class InvalidBackupException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

class BackupPasswordRequiredException : InvalidBackupException(
    "La copia está cifrada y necesita su contraseña.",
)

class BackupAuthenticationException(cause: Throwable? = null) : InvalidBackupException(
    "La contraseña no es correcta o la copia fue modificada.",
    cause,
)

class UnresolvedBackupConflictException(message: String) : InvalidBackupException(message)

internal fun BackupValue.stableText(): String = when (this) {
    BackupValue.Null -> "n:"
    is BackupValue.Text -> "t:$value"
    is BackupValue.Integer -> "i:$value"
    is BackupValue.Real -> "r:${java.lang.Double.toHexString(value)}"
    is BackupValue.Binary -> "b:${value.joinToString("") { "%02x".format(it) }}"
}
