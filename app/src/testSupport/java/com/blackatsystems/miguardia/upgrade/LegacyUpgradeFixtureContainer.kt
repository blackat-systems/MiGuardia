package com.blackatsystems.miguardia.upgrade

import com.blackatsystems.miguardia.core.domain.backup.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Synthetic legacy wire fixture only. Current production writers intentionally emit V8.
 * This freezes the already supported unsealed V1 test encoding used by BackupContractTest;
 * it changes neither the format nor crypto and must never receive a real backup/photo.
 * Every generated database is decoded by the real reader before it can be exported.
 */
object LegacyUpgradeFixtureContainer {
    fun databaseBytes(source: BackupDatabaseSnapshot): ByteArray {
        when (source.roomVersion) {
            5 -> MiGuardiaBackupSchemaV5.requireValid(source)
            6 -> MiGuardiaBackupSchemaV6.requireValid(source)
            7 -> MiGuardiaBackupSchemaV7.requireValid(source)
            else -> error("Only explicit legacy V5/V6/V7 synthetic sources are supported")
        }
        val bytes = encoded { data ->
            data.writeInt(0x4D474442)
            data.writeInt(source.roomVersion)
            data.string(source.roomIdentityHash)
            data.writeBoolean(source.timelineId != null)
            source.timelineId?.let { data.string(it) }
            data.writeInt(source.tables.size)
            source.tables.forEach { table ->
                data.string(table.name)
                data.strings(table.columns)
                data.strings(table.primaryKey)
                data.writeInt(table.records.size)
                table.records.forEach { record -> record.values.forEach { value ->
                    when (value) {
                        BackupValue.Null -> data.writeByte(0)
                        is BackupValue.Text -> { data.writeByte(1); data.string(value.value) }
                        is BackupValue.Integer -> { data.writeByte(2); data.writeLong(value.value) }
                        is BackupValue.Real -> { data.writeByte(3); data.writeLong(java.lang.Double.doubleToRawLongBits(value.value)) }
                        is BackupValue.Binary -> { data.writeByte(4); data.writeInt(value.value.size); data.write(value.value) }
                    }
                } }
            }
        }
        check(source == BackupPayloadCodec.readDatabase(bytes.inputStream()))
        return bytes
    }

    fun entries(
        source: BackupDatabaseSnapshot,
        preferences: List<BackupPreference>,
        syntheticPhoto: File,
        createdAtEpochMillis: Long,
    ): Map<String, ByteArray> {
        val table = source.table("schedule_photos")
        val row = table.records.single()
        fun text(column: String) = (row.values[table.columns.indexOf(column)] as BackupValue.Text).value
        fun integer(column: String) = (row.values[table.columns.indexOf(column)] as BackupValue.Integer).value
        check(source.tables.filter { it.name in setOf("shift_note_attachments", "shift_situations") }.all { it.records.isEmpty() })
        check(syntheticPhoto.length() == integer("byteSize") && syntheticPhoto.length() in 1..1_000_000)
        val photo = BackupPhotoMetadata(
            text("id"), text("storageKey"), text("mimeType"), integer("byteSize"),
            integer("pixelWidth").toInt(), integer("pixelHeight").toInt(), sha256(syntheticPhoto.readBytes()),
        )
        check(photo.storageKey == UpgradeFixtureSnapshots.PHOTO_STORAGE_KEY)
        val entries = linkedMapOf(
            "database.bin" to databaseBytes(source),
            "preferences.bin" to ByteArrayOutputStream().also { BackupPayloadCodec.writePreferences(preferences, it) }.toByteArray(),
            "photos.bin" to ByteArrayOutputStream().also { BackupPayloadCodec.writePhotos(listOf(photo), it) }.toByteArray(),
            "photos/${photo.storageKey}" to syntheticPhoto.readBytes(),
        )
        val manifest = BackupManifest(
            backupId = "90000000-0000-4000-8000-000000000009",
            createdAtEpochMillis = createdAtEpochMillis,
            zoneId = UpgradeFixtureSnapshots.ZONE_ID,
            roomVersion = source.roomVersion,
            timelineId = source.timelineId,
            photoMode = BackupPhotoMode.INCLUDED,
            tableCounts = source.tables.associate { it.name to it.records.size },
            entries = entries.map { (name, bytes) -> BackupEntryManifest(name, bytes.size.toLong(), sha256(bytes)) }.sortedBy { it.name },
        )
        entries["manifest.bin"] = ByteArrayOutputStream().also { BackupPayloadCodec.writeManifest(manifest, it) }.toByteArray()
        return entries
    }

    /** Also used by negative tests to supply corrupt entries to the production extractor. */
    fun write(target: File, entries: Map<String, ByteArray>, createdAtEpochMillis: Long) {
        check(!target.exists()) { "Never replace an earlier fixture" }
        check(createdAtEpochMillis > 0)
        val payload = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                (listOf("manifest.bin") + entries.keys.filterNot { it == "manifest.bin" }.sorted()).forEach { name ->
                    zip.putNextEntry(ZipEntry(name).also { it.time = 0 })
                    zip.write(entries.getValue(name))
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        target.outputStream().use { output -> DataOutputStream(output).use { data ->
            data.write("MIGUARDIA-BACKUP".toByteArray(Charsets.US_ASCII))
            data.writeInt(MiGuardiaBackupContract.FORMAT_VERSION)
            data.writeInt(MiGuardiaBackupContract.MIN_READER_VERSION)
            data.writeInt(0) // supported historical unsealed encoding, synthetic data only
            data.writeLong(createdAtEpochMillis)
            data.writeInt(0)
            data.writeByte(MiGuardiaBackupContract.SALT_BYTES)
            data.write(ByteArray(MiGuardiaBackupContract.SALT_BYTES))
            data.writeByte(MiGuardiaBackupContract.NONCE_BYTES)
            data.write(ByteArray(MiGuardiaBackupContract.NONCE_BYTES))
            data.writeLong(payload.size.toLong())
            data.write(payload)
        } }
    }

    private fun encoded(block: (DataOutputStream) -> Unit): ByteArray = ByteArrayOutputStream().also { output ->
        DataOutputStream(output).use(block)
    }.toByteArray()

    private fun DataOutputStream.string(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }
    private fun DataOutputStream.strings(values: List<String>) { writeInt(values.size); values.forEach { string(it) } }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
