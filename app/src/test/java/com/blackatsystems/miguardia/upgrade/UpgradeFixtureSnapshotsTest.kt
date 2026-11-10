package com.blackatsystems.miguardia.upgrade

import com.blackatsystems.miguardia.core.domain.backup.*
import com.blackatsystems.miguardia.core.domain.summary.SummaryOptionalFamily
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpgradeFixtureSnapshotsTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun legacyV7ConstructorUsesItsOwnVersionAndIdentity() {
        val legacy = UpgradeFixtureSnapshots.legacyV7(DAY, 123)
        assertEquals(7, legacy.roomVersion)
        assertEquals(MiGuardiaBackupContract.LEGACY_ROOM_IDENTITY_HASH_V7, legacy.roomIdentityHash)
        assertEquals(28, legacy.tables.size)
        MiGuardiaBackupSchemaV7.requireValid(legacy)
    }

    @Test
    fun omittedMetadataReproducesTheOriginalVersionAndIdentityRejections() {
        val legacy = UpgradeFixtureSnapshots.legacyV7(DAY, 123)
        // This is the actual old constructor call, including the production defaults.
        val oldCall = BackupDatabaseSnapshot(timelineId = legacy.timelineId, tables = legacy.tables)
        assertEquals(8, oldCall.roomVersion)
        assertEquals(MiGuardiaBackupContract.ROOM_IDENTITY_HASH, oldCall.roomIdentityHash)
        assertTrue(rejects { MiGuardiaBackupSchemaV7.requireValid(oldCall) }.message!!.contains("versión"))
        val wrongIdentityOnly = oldCall.copy(roomVersion = 7)
        assertTrue(rejects { MiGuardiaBackupSchemaV7.requireValid(wrongIdentityOnly) }.message!!.contains("identidad"))
    }

    @Test
    fun explicitV6AndV8ConserveEveryOriginalTableRecordAndValue() {
        val source = UpgradeFixtureSnapshots.sourceV6(DAY, 123)
        val target = UpgradeFixtureSnapshots.targetV8(DAY, 123)
        assertEquals(6, source.roomVersion)
        assertEquals(MiGuardiaBackupContract.LEGACY_ROOM_IDENTITY_HASH_V6, source.roomIdentityHash)
        assertEquals(27, source.tables.size)
        assertTrue(source.tables.all { it.records.isNotEmpty() })
        assertEquals(8, target.roomVersion)
        assertEquals(MiGuardiaBackupContract.ROOM_IDENTITY_HASH, target.roomIdentityHash)
        assertEquals(29, target.tables.size)
        assertEquals(source.timelineId, target.timelineId)
        source.tables.forEach { assertEquals(it, target.table(it.name)) }
        listOf("shift_note_attachments", "shift_situations").forEach { name ->
            val table = target.table(name)
            val spec = MiGuardiaBackupSchemaV8.byName.getValue(name)
            assertEquals(spec.columns, table.columns)
            assertEquals(spec.primaryKey, table.primaryKey)
            assertTrue(table.records.isEmpty())
        }
        val allValues = source.tables.flatMap { it.records }.flatMap { it.values }
        assertTrue(allValues.any { it === BackupValue.Null })
        assertTrue(allValues.any { it is BackupValue.Text })
        assertTrue(allValues.any { it is BackupValue.Integer })
        assertTrue(allValues.any { it is BackupValue.Real })
        MiGuardiaBackupSchemaV6.requireValid(source)
        MiGuardiaBackupSchemaV8.requireValid(target)
        assertEquals(target, upgradeSupportedBackupSchema(source))
    }

    @Test
    fun legacyV5V6AndV7WireReadersPreserveSourcesBeforeExplicitAdaptation() {
        val sources = listOf(
            UpgradeFixtureSnapshots.legacyV5(DAY, 123),
            UpgradeFixtureSnapshots.sourceV6(DAY, 123),
            UpgradeFixtureSnapshots.legacyV7(DAY, 123),
        )
        sources.forEach { source ->
            val encoded = LegacyUpgradeFixtureContainer.databaseBytes(source)
            val before = sha256(encoded)
            val decoded = BackupPayloadCodec.readDatabase(encoded.inputStream())
            assertEquals(source, decoded)
            val expected = if (source.roomVersion == 5) expectedV5Adaptation(123) else {
                UpgradeFixtureSnapshots.targetV8(DAY, 123)
            }
            assertEquals(expected, upgradeSupportedBackupSchema(decoded))
            assertEquals(before, sha256(encoded))
            assertEquals(source, BackupPayloadCodec.readDatabase(encoded.inputStream()))
        }
    }

    @Test
    fun legacyV7ExistingAttachmentSurvivesAdaptationWithItsNoteAssociationAndOrder() {
        val source = legacyV7WithAttachment()
        MiGuardiaBackupSchemaV7.requireValid(source)
        val decoded = BackupPayloadCodec.readDatabase(LegacyUpgradeFixtureContainer.databaseBytes(source).inputStream())
        assertEquals(source, decoded)
        val target = upgradeSupportedBackupSchema(decoded)
        source.tables.forEach { assertEquals(it, target.table(it.name)) }
        assertTrue(target.table("shift_situations").records.isEmpty())
        MiGuardiaBackupSchemaV8.requireValid(target)
    }

    @Test
    fun legacyV7RejectsOrphanAttachmentsAndNonContinuousPositions() {
        val source = legacyV7WithAttachment()
        rejects {
            MiGuardiaBackupSchemaV7.requireValid(source.changeValue("shift_note_attachments", "noteId", BackupValue.Text(OTHER_ID)))
        }
        rejects {
            MiGuardiaBackupSchemaV7.requireValid(source.changeValue("shift_note_attachments", "position", BackupValue.Integer(1)))
        }
    }

    @Test
    fun supportedVersionNumbersDoNotBypassIdentityTablesColumnsKeysOrRecordShape() {
        val source = UpgradeFixtureSnapshots.sourceV6(DAY, 123)
        val first = source.tables.first()
        val malformed = listOf(
            source.copy(roomIdentityHash = MiGuardiaBackupContract.ROOM_IDENTITY_HASH),
            source.copy(tables = source.tables.dropLast(1)),
            source.replaceTable(first.copy(columns = first.columns.reversed())),
            source.replaceTable(first.copy(primaryKey = listOf("name"))),
            source.replaceTable(first.copy(records = listOf(first.records.single().copy(values = first.records.single().values.dropLast(1))))),
            source.replaceTable(first.copy(records = listOf(first.records.single(), first.records.single()))),
            source.copy(timelineId = OTHER_ID),
        )
        malformed.forEach { bad -> rejects { upgradeSupportedBackupSchema(bad) } }
    }

    @Test
    fun currentWriterRoundTripsTheExplicitV8FixtureWithoutChangingTheInput() {
        val source = UpgradeFixtureSnapshots.targetV8(DAY, 123)
        val bytes = encoded { BackupPayloadCodec.writeDatabase(source, it) }
        assertEquals(source, BackupPayloadCodec.readDatabase(bytes.inputStream()))
        assertEquals(source, upgradeSupportedBackupSchema(source))
        rejects { BackupPayloadCodec.writeDatabase(UpgradeFixtureSnapshots.sourceV6(DAY, 123), ByteArrayOutputStream()) }
    }

    @Test
    fun legacyV6ContainerKeepsItsManifestAndAdaptsThePayloadWithAllPreferencesAndPhoto() {
        val fixture = containerFixture()
        val target = File(temporary.root, "origin-v6.miguardia-backup")
        LegacyUpgradeFixtureContainer.write(target, fixture.entries, CREATED_AT)
        val beforeContainer = sha256(target.readBytes())
        val beforePhoto = sha256(fixture.photo.readBytes())
        val expected = UpgradeFixtureSnapshots.targetV8(DAY, fixture.photo.length())
        val work = temporary.newFolder()
        repeat(2) {
            BackupContainer.extract(target, work, null).use { extracted ->
                assertEquals(6, extracted.manifest.roomVersion)
                assertEquals(fixture.source.timelineId, extracted.manifest.timelineId)
                assertEquals(fixture.source.tables.associate { table -> table.name to table.records.size }, extracted.manifest.tableCounts)
                assertEquals(expected, extracted.payload.database)
                assertEquals(fixture.preferences, extracted.payload.preferences)
                assertEquals(17, extracted.payload.preferences.size)
                assertEquals(BackupPhotoMode.INCLUDED, extracted.payload.photoMode)
                val photo = extracted.payload.photos.single()
                assertEquals(UpgradeFixtureSnapshots.PHOTO_ID, photo.recordId)
                assertEquals(UpgradeFixtureSnapshots.PHOTO_WIDTH, photo.pixelWidth)
                assertEquals(UpgradeFixtureSnapshots.PHOTO_HEIGHT, photo.pixelHeight)
                assertEquals(beforePhoto, photo.sha256)
                assertArrayEquals(fixture.photo.readBytes(), extracted.photoFile(photo.storageKey).readBytes())
                val comparison = BackupComparator.compare(expected, extracted.payload.database, fixture.preferences, extracted.payload.preferences)
                assertEquals(expected.totalRows + 17, comparison.identicalRecords)
                assertEquals(0, comparison.newRecords)
                assertTrue(comparison.conflicts.isEmpty())
                assertEquals(expected, extracted.payload.database)
            }
            assertTrue(work.listFiles()!!.isEmpty())
        }
        assertEquals(beforeContainer, sha256(target.readBytes()))
        assertEquals(beforePhoto, sha256(fixture.photo.readBytes()))
        exportSyntheticFixtureIfRequested(fixture, target, expected)
    }

    @Test
    fun currentV8ContainerRoundTripsDatabasePreferencesAndTheSameSyntheticPhoto() {
        val fixture = containerFixture()
        val database = UpgradeFixtureSnapshots.targetV8(DAY, fixture.photo.length())
        val photoMetadata = BackupPayloadCodec.readPhotos(fixture.entries.getValue("photos.bin").inputStream()).single()
        val target = File(temporary.root, "current-v8.miguardia-backup")
        val beforePhoto = sha256(fixture.photo.readBytes())
        val manifest = BackupContainer.create(
            target, temporary.newFolder(), UUID.fromString(OTHER_ID), CREATED_AT,
            UpgradeFixtureSnapshots.ZONE, database, fixture.preferences,
            BackupPhotoMode.INCLUDED, listOf(BackupPhotoAsset(photoMetadata, fixture.photo)), null,
        )
        val beforeContainer = sha256(target.readBytes())
        BackupContainer.extract(target, temporary.newFolder(), null).use { extracted ->
            assertEquals(manifest, extracted.manifest)
            assertEquals(8, extracted.manifest.roomVersion)
            assertEquals(database, extracted.payload.database)
            assertEquals(fixture.preferences, extracted.payload.preferences)
            assertEquals(listOf(photoMetadata), extracted.payload.photos)
            assertArrayEquals(fixture.photo.readBytes(), extracted.photoFile(photoMetadata.storageKey).readBytes())
        }
        assertEquals(beforeContainer, sha256(target.readBytes()))
        assertEquals(beforePhoto, sha256(fixture.photo.readBytes()))
    }

    @Test
    fun missingRequiredContainerEntriesAreRejectedAndExtractionIsCleaned() {
        val fixture = containerFixture()
        listOf("database.bin", "preferences.bin", "photos.bin", "photos/${UpgradeFixtureSnapshots.PHOTO_STORAGE_KEY}").forEach { name ->
            assertRejectedContainer(fixture.entries - name)
        }
    }

    @Test
    fun truncatedDatabaseIsRejectedEvenAfterRehashingItsManifestEntry() {
        val fixture = containerFixture()
        val bytes = fixture.entries.getValue("database.bin")
        assertRejectedContainer(rehashed(fixture.entries + ("database.bin" to bytes.copyOf(bytes.size - 1))))
    }

    @Test
    fun wrongDatabaseIdentityIsRejectedEvenWithCorrectEntryHash() {
        val fixture = containerFixture()
        val bytes = fixture.entries.getValue("database.bin").copyOf()
        // Database wire header is magic(4), version(4), UTF-8 identity length(4), identity.
        bytes[12] = (if (bytes[12] == 'a'.code.toByte()) 'b'.code else 'a'.code).toByte()
        assertRejectedContainer(rehashed(fixture.entries + ("database.bin" to bytes)))
    }

    @Test
    fun manifestCannotClaimV8ForAV6Payload() {
        val fixture = containerFixture()
        val manifest = BackupPayloadCodec.readManifest(fixture.entries.getValue("manifest.bin").inputStream())
        val changed = encoded { BackupPayloadCodec.writeManifest(manifest.copy(roomVersion = 8), it) }
        assertRejectedContainer(fixture.entries + ("manifest.bin" to changed))
    }

    @Test
    fun sameLengthPhotoMutationIsRejectedByThePhotoHashEvenAfterEntryRehashing() {
        val fixture = containerFixture()
        val name = "photos/${UpgradeFixtureSnapshots.PHOTO_STORAGE_KEY}"
        val altered = fixture.entries.getValue(name).copyOf()
        altered[altered.lastIndex] = (altered.last().toInt() xor 1).toByte()
        assertEquals(fixture.entries.getValue(name).size, altered.size)
        assertRejectedContainer(rehashed(fixture.entries + (name to altered)))
    }

    @Test
    fun photoRecordAssociationIsRejectedEvenWhenEveryEntryHashIsCorrect() {
        val fixture = containerFixture()
        val photos = BackupPayloadCodec.readPhotos(fixture.entries.getValue("photos.bin").inputStream())
        val altered = encoded { BackupPayloadCodec.writePhotos(listOf(photos.single().copy(recordId = OTHER_ID)), it) }
        assertRejectedContainer(rehashed(fixture.entries + ("photos.bin" to altered)))
    }

    @Test
    fun malformedPreferencePayloadIsRejectedEvenAfterEntryRehashing() {
        val fixture = containerFixture()
        val bytes = fixture.entries.getValue("preferences.bin")
        assertRejectedContainer(rehashed(fixture.entries + ("preferences.bin" to bytes.copyOf(bytes.size - 1))))
        val invalidType = bytes.copyOf()
        // Preferences header magic(4), count(4), first key's UTF-8 length(4), key, type.
        val firstKeyBytes = fixture.preferences.first().key.toByteArray(Charsets.UTF_8).size
        invalidType[12 + firstKeyBytes] = 127
        assertRejectedContainer(rehashed(fixture.entries + ("preferences.bin" to invalidType)))
    }

    private data class ContainerFixture(
        val source: BackupDatabaseSnapshot,
        val photo: File,
        val preferences: List<BackupPreference>,
        val entries: Map<String, ByteArray>,
    )

    private fun containerFixture(): ContainerFixture {
        val photo = File(temporary.newFolder(), UpgradeFixtureSnapshots.PHOTO_STORAGE_KEY)
        val image = BufferedImage(UpgradeFixtureSnapshots.PHOTO_WIDTH, UpgradeFixtureSnapshots.PHOTO_HEIGHT, BufferedImage.TYPE_INT_RGB)
        check(ImageIO.write(image, "png", photo))
        val source = UpgradeFixtureSnapshots.sourceV6(DAY, photo.length())
        val preferences = portablePreferences()
        return ContainerFixture(source, photo, preferences, LegacyUpgradeFixtureContainer.entries(source, preferences, photo, CREATED_AT))
    }

    /**
     * Explicit fictitious values for the 17 keys the public origin writes. The current app adds three
     * optional ones (PortablePreferencesGateway); an origin copy does not carry them.
     */
    private fun portablePreferences(): List<BackupPreference> = listOf(
        preference("profile.display_name", BackupPreferenceType.TEXT, "Fixture ficticia Stage 9"),
        preference("display.theme", BackupPreferenceType.TEXT, "LIGHT"),
        preference("display.zoom_percent", BackupPreferenceType.LONG, "150"),
        BackupPreference("summary.ordered_families", BackupPreferenceType.TEXT_LIST, SummaryOptionalFamily.entries.reversed().map { it.name }),
        preference("summary.hidden_families", BackupPreferenceType.TEXT_LIST, "HOLIDAYS", "NIGHTS"),
        preference("summary.intro_seen", BackupPreferenceType.BOOLEAN, "true"),
        preference("notifications.enabled", BackupPreferenceType.BOOLEAN, "true"),
        preference("notifications.precise_timing", BackupPreferenceType.BOOLEAN, "true"),
        preference("notifications.reminder_minutes", BackupPreferenceType.TEXT_LIST, "60", "180"),
        preference("notifications.persistent", BackupPreferenceType.BOOLEAN, "false"),
        preference("notifications.privacy", BackupPreferenceType.TEXT, "REDUCED"),
        preference("notifications.attention", BackupPreferenceType.TEXT, "VIBRATION_ONLY"),
        preference(MiGuardiaBackupContract.DISMISSED_EVENT_KEYS_PREFERENCE, BackupPreferenceType.TEXT_LIST, "shift:${UpgradeFixtureSnapshots.SHIFT_ID}"),
        preference("weather.enabled", BackupPreferenceType.BOOLEAN, "true"),
        preference("weather.unit", BackupPreferenceType.TEXT, "FAHRENHEIT"),
        preference("weather.include_notifications", BackupPreferenceType.BOOLEAN, "true"),
        preference("weather.explanation_accepted", BackupPreferenceType.BOOLEAN, "true"),
    ).sortedBy { it.key }

    private fun preference(key: String, type: BackupPreferenceType, vararg values: String) = BackupPreference(key, type, values.toList())

    private fun legacyV7WithAttachment(): BackupDatabaseSnapshot {
        val source = UpgradeFixtureSnapshots.legacyV7(DAY, 123)
        val table = source.table("shift_note_attachments")
        val values = mapOf(
            "id" to BackupValue.Text(ATTACHMENT_ID),
            "noteId" to BackupValue.Text(UpgradeFixtureSnapshots.NOTE_ID),
            "storageKey" to BackupValue.Text("$ATTACHMENT_ID.png"),
            "mimeType" to BackupValue.Text("image/png"),
            "byteSize" to BackupValue.Integer(91),
            "pixelWidth" to BackupValue.Integer(2),
            "pixelHeight" to BackupValue.Integer(3),
            "position" to BackupValue.Integer(0),
            "createdAtEpochMillis" to BackupValue.Integer(CREATED_AT),
            "updatedAtEpochMillis" to BackupValue.Integer(CREATED_AT),
        )
        return source.replaceTable(table.copy(records = listOf(BackupRecord(table.columns.map(values::getValue)))))
    }

    private fun expectedV5Adaptation(photoBytes: Long): BackupDatabaseSnapshot {
        val expected = UpgradeFixtureSnapshots.targetV8(DAY, photoBytes)
        return expected.changeValue("objectives", "weatherLatitude", BackupValue.Null)
            .changeValue("objectives", "weatherLongitude", BackupValue.Null)
    }

    private fun BackupDatabaseSnapshot.replaceTable(replacement: BackupTable): BackupDatabaseSnapshot =
        copy(tables = tables.map { if (it.name == replacement.name) replacement else it })

    private fun BackupDatabaseSnapshot.changeValue(tableName: String, column: String, value: BackupValue): BackupDatabaseSnapshot {
        val table = table(tableName)
        val index = table.columns.indexOf(column).also { check(it >= 0) }
        return replaceTable(table.copy(records = table.records.map { record ->
            record.copy(values = record.values.mapIndexed { offset, old -> if (offset == index) value else old })
        }))
    }

    private fun rehashed(entries: Map<String, ByteArray>): Map<String, ByteArray> {
        val manifest = BackupPayloadCodec.readManifest(entries.getValue("manifest.bin").inputStream())
        val updated = manifest.copy(entries = manifest.entries.map { entry ->
            entries[entry.name]?.let { entry.copy(uncompressedBytes = it.size.toLong(), sha256 = sha256(it)) } ?: entry
        })
        return entries + ("manifest.bin" to encoded { BackupPayloadCodec.writeManifest(updated, it) })
    }

    private fun assertRejectedContainer(entries: Map<String, ByteArray>) {
        val target = File(temporary.newFolder(), "invalid-synthetic.miguardia-backup")
        LegacyUpgradeFixtureContainer.write(target, entries, CREATED_AT)
        val before = sha256(target.readBytes())
        val work = temporary.newFolder()
        rejects { BackupContainer.extract(target, work, null).use { fail("Invalid synthetic container was accepted") } }
        assertEquals(before, sha256(target.readBytes()))
        assertTrue(work.listFiles()!!.isEmpty())
    }

    private fun exportSyntheticFixtureIfRequested(fixture: ContainerFixture, sourceContainer: File, target: BackupDatabaseSnapshot) {
        val exportPath = System.getenv("MIGUARDIA_STAGE9_FIXTURE_EXPORT") ?: return
        val directory = Files.createDirectory(File(exportPath).toPath()).toFile()
        val outputs = linkedMapOf(
            "source-v6.bin" to LegacyUpgradeFixtureContainer.databaseBytes(fixture.source),
            "target-v8.bin" to encoded { BackupPayloadCodec.writeDatabase(target, it) },
            "preferences.bin" to fixture.entries.getValue("preferences.bin"),
            "synthetic.png" to fixture.photo.readBytes(),
            "origin-v6.miguardia-backup" to sourceContainer.readBytes(),
        )
        outputs.forEach { (name, bytes) -> Files.write(File(directory, name).toPath(), bytes, java.nio.file.StandardOpenOption.CREATE_NEW) }
        val hashes = outputs.entries.joinToString("\n", postfix = "\n") { (name, bytes) -> "${sha256(bytes)}  $name" }
        Files.writeString(File(directory, "hashes.sha256").toPath(), hashes, java.nio.file.StandardOpenOption.CREATE_NEW)
    }

    private fun encoded(write: (ByteArrayOutputStream) -> Unit): ByteArray = ByteArrayOutputStream().also(write).toByteArray()
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun rejects(block: () -> Unit): InvalidBackupException = assertThrows(InvalidBackupException::class.java) { block() }

    private companion object {
        val DAY: LocalDate = LocalDate.parse("2026-10-21")
        const val CREATED_AT = 1_792_555_200_000L
        const val OTHER_ID = "90000000-0000-4000-8000-000000000099"
        const val ATTACHMENT_ID = "90000000-0000-4000-8000-000000000098"
    }
}
