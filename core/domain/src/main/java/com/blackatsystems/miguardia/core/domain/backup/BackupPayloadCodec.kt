package com.blackatsystems.miguardia.core.domain.backup

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

object BackupPayloadCodec {
    fun writeDatabase(snapshot: BackupDatabaseSnapshot, output: OutputStream) = writeDatabase(
        snapshot = snapshot,
        output = output,
        encodedLimitBytes = MiGuardiaBackupContract.MAX_LOGICAL_BYTES,
    )

    internal fun writeDatabase(
        snapshot: BackupDatabaseSnapshot,
        output: OutputStream,
        encodedLimitBytes: Long,
    ) {
        MiGuardiaBackupSchemaV6.requireValid(snapshot)
        val bounded = BoundedOutputStream(output, encodedLimitBytes, "database.bin")
        DataOutputStream(BufferedOutputStream(bounded)).use { data ->
            data.writeInt(DATABASE_MAGIC)
            data.writeInt(snapshot.roomVersion)
            data.writeUtf8(snapshot.roomIdentityHash)
            data.writeNullableUtf8(snapshot.timelineId)
            data.writeInt(snapshot.tables.size)
            snapshot.tables.forEach { table ->
                data.writeUtf8(table.name)
                data.writeStrings(table.columns)
                data.writeStrings(table.primaryKey)
                data.writeInt(table.records.size)
                table.records.forEach { record ->
                    record.values.forEach { value -> data.writeValue(value) }
                }
            }
        }
    }

    fun readDatabase(input: InputStream): BackupDatabaseSnapshot = readDatabase(
        input = input,
        decodedMemoryLimitBytes = BackupMemoryBudget.operationalHeapBytes(),
    )

    internal fun readDatabase(
        input: InputStream,
        decodedMemoryLimitBytes: Long,
    ): BackupDatabaseSnapshot = guardedDecode("datos") {
        val estimator = BackupDatabaseMemoryEstimator(decodedMemoryLimitBytes)
        DataInputStream(BufferedInputStream(input)).use { data ->
            data.requireMagic(DATABASE_MAGIC)
            val roomVersion = data.readInt()
            estimator.consumeSnapshotObject()
            val identityHash = data.readUtf8(estimator)
            val timelineId = data.readNullableUtf8(estimator)
            val tableCount = data.readBoundedCount(27, "tablas")
            estimator.consumeTableReferences(tableCount)
            val tables = ArrayList<BackupTable>(tableCount)
            var totalRows = 0
            repeat(tableCount) {
                estimator.consumeTableObject()
                val name = data.readUtf8(estimator)
                val columns = data.readStrings(128, "columnas", estimator)
                val primaryKey = data.readStrings(16, "clave primaria", estimator)
                val rowCount = data.readBoundedCount(MiGuardiaBackupContract.MAX_TABLE_ROWS, "filas")
                totalRows += rowCount
                if (totalRows > MiGuardiaBackupContract.MAX_TOTAL_ROWS) {
                    throw InvalidBackupException("La copia supera el límite seguro de registros.")
                }
                estimator.reserveRecordReferences(rowCount)
                val records = ArrayList<BackupRecord>(rowCount)
                repeat(rowCount) {
                    estimator.consumeRecordObject(
                        valueCount = columns.size,
                        recordReferenceAlreadyReserved = true,
                    )
                    records += BackupRecord(List(columns.size) { data.readValue(estimator) })
                }
                tables += BackupTable(name, columns, primaryKey, records)
            }
            data.requireFinished()
            BackupDatabaseSnapshot(roomVersion, identityHash, timelineId, tables).also(
                ::requireSupportedBackupSchema,
            )
        }
    }

    fun writePreferences(preferences: List<BackupPreference>, output: OutputStream) = writePreferences(
        preferences = preferences,
        output = output,
        encodedLimitBytes = MiGuardiaBackupContract.MAX_PREFERENCES_BYTES,
    )

    internal fun writePreferences(
        preferences: List<BackupPreference>,
        output: OutputStream,
        encodedLimitBytes: Long,
    ) {
        if (preferences.size > MAX_PREFERENCES ||
            preferences.any { it.values.size > it.maximumPortableValueCount() }
        ) {
            throw InvalidBackupException("Las preferencias portables superan los límites seguros.")
        }
        require(preferences.map { it.key } == preferences.map { it.key }.sorted()) {
            "Las preferencias portables deben estar ordenadas."
        }
        require(preferences.map { it.key }.distinct().size == preferences.size) {
            "Las preferencias portables no pueden repetirse."
        }
        val bounded = BoundedOutputStream(output, encodedLimitBytes, "preferences.bin")
        DataOutputStream(BufferedOutputStream(bounded)).use { data ->
            data.writeInt(PREFERENCES_MAGIC)
            data.writeInt(preferences.size)
            preferences.forEach { preference ->
                data.writeUtf8(preference.key)
                data.writeByte(preference.type.ordinal)
                data.writeStrings(preference.values)
            }
        }
    }

    fun readPreferences(input: InputStream): List<BackupPreference> = guardedDecode("preferencias") {
        DataInputStream(BufferedInputStream(input)).use { data ->
            data.requireMagic(PREFERENCES_MAGIC)
            val count = data.readBoundedCount(MAX_PREFERENCES, "preferencias")
            val result = List(count) {
                val key = data.readUtf8()
                val typeIndex = data.readUnsignedByte()
                val type = BackupPreferenceType.entries.getOrNull(typeIndex)
                    ?: throw InvalidBackupException("La copia contiene un tipo de preferencia desconocido.")
                val maximumValues = if (key == MiGuardiaBackupContract.DISMISSED_EVENT_KEYS_PREFERENCE) {
                    MiGuardiaBackupContract.MAX_DISMISSED_EVENT_KEYS
                } else {
                    MAX_PREFERENCE_VALUES
                }
                BackupPreference(key, type, data.readStrings(maximumValues, "valores"))
            }
            data.requireFinished()
            if (result.map { it.key } != result.map { it.key }.sorted() ||
                result.map { it.key }.distinct().size != result.size
            ) {
                throw InvalidBackupException("Las preferencias de la copia no son canónicas.")
            }
            result
        }
    }

    fun writePhotos(photos: List<BackupPhotoMetadata>, output: OutputStream) {
        if (photos.size > MiGuardiaBackupContract.MAX_PHOTO_COUNT) {
            throw InvalidBackupException("La cantidad de fotografías es inválida.")
        }
        if (photos.map { it.storageKey } != photos.map { it.storageKey }.sorted() ||
            photos.map { it.storageKey }.distinct().size != photos.size ||
            photos.map { it.recordId }.distinct().size != photos.size
        ) {
            throw InvalidBackupException("Las fotografías de la copia no son canónicas.")
        }
        photos.forEach(::requireValidPhotoMetadata)
        DataOutputStream(BufferedOutputStream(output)).use { data ->
            data.writeInt(PHOTOS_MAGIC)
            data.writeInt(photos.size)
            photos.forEach { photo ->
                data.writeUtf8(photo.recordId)
                data.writeUtf8(photo.storageKey)
                data.writeUtf8(photo.mimeType)
                data.writeLong(photo.byteSize)
                data.writeInt(photo.pixelWidth)
                data.writeInt(photo.pixelHeight)
                data.writeUtf8(photo.sha256)
            }
        }
    }

    fun readPhotos(input: InputStream): List<BackupPhotoMetadata> = guardedDecode("fotografías") {
        DataInputStream(BufferedInputStream(input)).use { data ->
            data.requireMagic(PHOTOS_MAGIC)
            val count = data.readBoundedCount(MiGuardiaBackupContract.MAX_PHOTO_COUNT, "fotografías")
            val photos = List(count) {
                BackupPhotoMetadata(
                    recordId = data.readUtf8(),
                    storageKey = data.readUtf8(),
                    mimeType = data.readUtf8(),
                    byteSize = data.readLong(),
                    pixelWidth = data.readInt(),
                    pixelHeight = data.readInt(),
                    sha256 = data.readUtf8(),
                )
            }
            data.requireFinished()
            if (photos.map { it.storageKey } != photos.map { it.storageKey }.sorted() ||
                photos.map { it.storageKey }.distinct().size != photos.size ||
                photos.map { it.recordId }.distinct().size != photos.size
            ) {
                throw InvalidBackupException("Las fotografías de la copia no son canónicas.")
            }
            photos.forEach(::requireValidPhotoMetadata)
            photos
        }
    }

    fun writeManifest(manifest: BackupManifest, output: OutputStream) {
        DataOutputStream(BufferedOutputStream(output)).use { data ->
            data.writeInt(MANIFEST_MAGIC)
            data.writeInt(MiGuardiaBackupContract.FORMAT_VERSION)
            data.writeUtf8(manifest.backupId)
            data.writeLong(manifest.createdAtEpochMillis)
            data.writeUtf8(manifest.zoneId)
            data.writeInt(manifest.roomVersion)
            data.writeNullableUtf8(manifest.timelineId)
            data.writeByte(manifest.photoMode.ordinal)
            data.writeInt(manifest.tableCounts.size)
            manifest.tableCounts.toSortedMap().forEach { (name, count) ->
                data.writeUtf8(name)
                data.writeInt(count)
            }
            data.writeInt(manifest.entries.size)
            manifest.entries.sortedBy { it.name }.forEach { entry ->
                data.writeUtf8(entry.name)
                data.writeLong(entry.uncompressedBytes)
                data.writeUtf8(entry.sha256)
            }
        }
    }

    fun readManifest(input: InputStream): BackupManifest = guardedDecode("manifiesto") {
        DataInputStream(BufferedInputStream(input)).use { data ->
            data.requireMagic(MANIFEST_MAGIC)
            if (data.readInt() != MiGuardiaBackupContract.FORMAT_VERSION) {
                throw InvalidBackupException("La versión del manifiesto no es compatible.")
            }
            val backupId = data.readUtf8().also(::requireUuid)
            val createdAt = data.readLong()
            if (createdAt <= 0L) throw InvalidBackupException("La fecha de la copia es inválida.")
            val zoneId = data.readUtf8().also(::requireZoneId)
            val roomVersion = data.readInt()
            val timelineId = data.readNullableUtf8()?.also(::requireUuid)
            val photoMode = BackupPhotoMode.entries.getOrNull(data.readUnsignedByte())
                ?: throw InvalidBackupException("El modo de fotografías es inválido.")
            val countSize = data.readBoundedCount(27, "conteos")
            val counts = linkedMapOf<String, Int>()
            repeat(countSize) {
                val name = data.readUtf8()
                val count = data.readBoundedCount(MiGuardiaBackupContract.MAX_TABLE_ROWS, "filas")
                if (counts.put(name, count) != null) {
                    throw InvalidBackupException("El manifiesto repite una tabla.")
                }
            }
            val entryCount = data.readBoundedCount(MiGuardiaBackupContract.MAX_PHOTO_COUNT + 3, "entradas")
            val entries = List(entryCount) {
                BackupEntryManifest(data.readUtf8(), data.readLong(), data.readUtf8())
            }
            data.requireFinished()
            if (entries.map { it.name } != entries.map { it.name }.sorted() ||
                entries.map { it.name }.distinct().size != entries.size
            ) {
                throw InvalidBackupException("Las entradas del manifiesto no son canónicas.")
            }
            BackupManifest(
                backupId,
                createdAt,
                zoneId,
                roomVersion,
                timelineId,
                photoMode,
                counts,
                entries,
            )
        }
    }

    private inline fun <T> guardedDecode(section: String, block: () -> T): T = try {
        block()
    } catch (error: InvalidBackupException) {
        throw error
    } catch (error: EOFException) {
        throw InvalidBackupException("La sección de $section está truncada.", error)
    } catch (error: RuntimeException) {
        throw InvalidBackupException("La sección de $section no se puede leer.", error)
    }

    private fun requireValidPhotoMetadata(photo: BackupPhotoMetadata) {
        requireUuid(photo.recordId)
        if (!SAFE_STORAGE_KEY.matches(photo.storageKey)) {
            throw InvalidBackupException("Una fotografía usa un nombre de archivo inválido.")
        }
        if (photo.mimeType !in ALLOWED_IMAGE_MIME_TYPES) {
            throw InvalidBackupException("Una fotografía usa un tipo de archivo no permitido.")
        }
        if (photo.byteSize !in 1..MiGuardiaBackupContract.MAX_SINGLE_PHOTO_BYTES ||
            photo.pixelWidth !in 1..MAX_IMAGE_DIMENSION || photo.pixelHeight !in 1..MAX_IMAGE_DIMENSION
        ) {
            throw InvalidBackupException("Una fotografía supera los límites seguros.")
        }
        if (!SHA256.matches(photo.sha256)) {
            throw InvalidBackupException("La huella de una fotografía es inválida.")
        }
    }

    private fun requireUuid(value: String) {
        if (runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false).not()) {
            throw InvalidBackupException("La copia contiene un UUID inválido.")
        }
    }

    private fun requireZoneId(value: String) {
        if (runCatching { java.time.ZoneId.of(value) }.isFailure) {
            throw InvalidBackupException("La copia contiene una zona horaria inválida.")
        }
    }

    private fun DataOutputStream.writeValue(value: BackupValue) = when (value) {
        BackupValue.Null -> writeByte(VALUE_NULL)
        is BackupValue.Text -> {
            writeByte(VALUE_TEXT)
            writeUtf8(value.value)
        }
        is BackupValue.Integer -> {
            writeByte(VALUE_INTEGER)
            writeLong(value.value)
        }
        is BackupValue.Real -> {
            writeByte(VALUE_REAL)
            writeLong(java.lang.Double.doubleToRawLongBits(value.value))
        }
        is BackupValue.Binary -> {
            writeByte(VALUE_BINARY)
            if (value.value.size > MiGuardiaBackupContract.MAX_TEXT_BYTES) {
                throw InvalidBackupException("Una celda binaria supera el límite seguro.")
            }
            writeInt(value.value.size)
            write(value.value)
        }
    }

    private fun DataInputStream.readValue(estimator: BackupDatabaseMemoryEstimator): BackupValue = when (
        readUnsignedByte()
    ) {
        VALUE_NULL -> BackupValue.Null
        VALUE_TEXT -> {
            estimator.consumeValueObject()
            BackupValue.Text(readUtf8(estimator))
        }
        VALUE_INTEGER -> {
            estimator.consumeValueObject()
            BackupValue.Integer(readLong())
        }
        VALUE_REAL -> {
            estimator.consumeValueObject()
            BackupValue.Real(java.lang.Double.longBitsToDouble(readLong()))
        }
        VALUE_BINARY -> {
            val size = readBoundedCount(MiGuardiaBackupContract.MAX_TEXT_BYTES, "bytes de celda")
            estimator.consumeBinaryValue(size)
            BackupValue.Binary(ByteArray(size).also(::readFully))
        }
        else -> throw InvalidBackupException("La copia contiene un tipo de celda desconocido.")
    }

    private fun DataOutputStream.writeStrings(values: List<String>) {
        writeInt(values.size)
        values.forEach { value -> writeUtf8(value) }
    }

    private fun DataInputStream.readStrings(
        max: Int,
        label: String,
        estimator: BackupDatabaseMemoryEstimator? = null,
    ): List<String> {
        val count = readBoundedCount(max, label)
        estimator?.consumeListReferences(count)
        return List(count) { readUtf8(estimator) }
    }

    private fun DataOutputStream.writeNullableUtf8(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUtf8(value)
    }

    private fun DataInputStream.readNullableUtf8(
        estimator: BackupDatabaseMemoryEstimator? = null,
    ): String? = if (readBoolean()) readUtf8(estimator) else null

    private fun DataOutputStream.writeUtf8(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MiGuardiaBackupContract.MAX_TEXT_BYTES) {
            throw InvalidBackupException("Un texto supera el límite seguro.")
        }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readUtf8(estimator: BackupDatabaseMemoryEstimator? = null): String {
        val size = readBoundedCount(MiGuardiaBackupContract.MAX_TEXT_BYTES, "texto")
        estimator?.consumeEncodedString(size)
        val bytes = ByteArray(size).also(::readFully)
        val value = String(bytes, StandardCharsets.UTF_8)
        if (!value.toByteArray(StandardCharsets.UTF_8).contentEquals(bytes)) {
            throw InvalidBackupException("La copia contiene texto UTF-8 inválido.")
        }
        return value
    }

    private fun DataInputStream.readBoundedCount(max: Int, label: String): Int = readInt().also {
        if (it !in 0..max) throw InvalidBackupException("La cantidad de $label es inválida.")
    }

    private fun DataInputStream.requireMagic(expected: Int) {
        if (readInt() != expected) throw InvalidBackupException("La sección de la copia no es reconocible.")
    }

    private fun DataInputStream.requireFinished() {
        if (read() != -1) throw InvalidBackupException("La sección contiene datos adicionales no permitidos.")
    }

    private fun BackupPreference.maximumPortableValueCount(): Int =
        if (key == MiGuardiaBackupContract.DISMISSED_EVENT_KEYS_PREFERENCE) {
            MiGuardiaBackupContract.MAX_DISMISSED_EVENT_KEYS
        } else {
            MAX_PREFERENCE_VALUES
        }

    private const val DATABASE_MAGIC: Int = 0x4D474442
    private const val PREFERENCES_MAGIC: Int = 0x4D475046
    private const val PHOTOS_MAGIC: Int = 0x4D475048
    private const val MANIFEST_MAGIC: Int = 0x4D474D46
    private const val VALUE_NULL = 0
    private const val VALUE_TEXT = 1
    private const val VALUE_INTEGER = 2
    private const val VALUE_REAL = 3
    private const val VALUE_BINARY = 4
    private const val MAX_PREFERENCES = 128
    private const val MAX_PREFERENCE_VALUES = 128
    private const val MAX_IMAGE_DIMENSION = 65_535
    private val SHA256 = Regex("[0-9a-f]{64}")
    private val SAFE_STORAGE_KEY = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:_[0-9a-f]{8})?\\.(?:jpg|jpeg|png|webp)")
    private val ALLOWED_IMAGE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
}
