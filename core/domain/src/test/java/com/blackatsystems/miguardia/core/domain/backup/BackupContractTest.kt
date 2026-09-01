package com.blackatsystems.miguardia.core.domain.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupContractTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun publicContractIsExactAndVersioned() {
        assertEquals(".miguardia-backup", MiGuardiaBackupContract.FILE_EXTENSION)
        assertEquals("application/vnd.blackatsystems.miguardia.backup", MiGuardiaBackupContract.MIME_TYPE)
        assertEquals(1, MiGuardiaBackupContract.FORMAT_VERSION)
        assertEquals(1, MiGuardiaBackupContract.MIN_READER_VERSION)
        assertEquals(310_000, MiGuardiaBackupContract.PBKDF2_ITERATIONS)
        assertEquals(256, MiGuardiaBackupContract.AES_KEY_BITS)
        assertEquals(128, MiGuardiaBackupContract.GCM_TAG_BITS)
    }

    @Test
    fun databaseCodecIsDeterministicAndRoundTripsAllTables() {
        val snapshot = emptySnapshot().withObjective(OBJECTIVE_ID, "Objetivo Norte")
        val first = ByteArrayOutputStream().also { BackupPayloadCodec.writeDatabase(snapshot, it) }.toByteArray()
        val second = ByteArrayOutputStream().also { BackupPayloadCodec.writeDatabase(snapshot, it) }.toByteArray()

        assertTrue(first.contentEquals(second))
        assertEquals(snapshot, BackupPayloadCodec.readDatabase(ByteArrayInputStream(first)))
        assertEquals(27, snapshot.tables.size)
    }

    @Test
    fun plaintextContainerRoundTripsWithoutPhotos() {
        val target = File(temporary.root, "plain.miguardia-backup")
        val createWork = temporary.newFolder("plain-work")
        val manifest = BackupContainer.create(
            target = target,
            workingDirectory = createWork,
            backupId = UUID.fromString(BACKUP_ID),
            createdAtEpochMillis = CREATED_AT,
            zoneId = ZoneId.of("America/Argentina/Buenos_Aires"),
            database = emptySnapshot(),
            preferences = emptyList(),
            photoMode = BackupPhotoMode.OMITTED,
            photoAssets = emptyList(),
            password = null,
        )

        assertTrue(createWork.listFiles().orEmpty().isEmpty())
        assertFalse(BackupContainer.readHeader(target).encrypted)
        val readWork = temporary.newFolder("plain-read")
        BackupContainer.extract(target, readWork, null).use { extracted ->
            assertEquals(manifest, extracted.manifest)
            assertEquals(emptySnapshot(), extracted.payload.database)
            assertEquals(BackupPhotoMode.OMITTED, extracted.payload.photoMode)
        }
        assertTrue(readWork.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun passwordlessContainerIsSelfSealedButOpensWithoutPassword() {
        val target = File(temporary.root, "passwordless-sealed.miguardia-backup")
        createPlaintext(target, temporary.newFolder("passwordless-sealed-work"), emptySnapshot())

        assertFalse(BackupContainer.readHeader(target).encrypted)
        val payloadPrefix = target.inputStream().use { input ->
            input.skip(BackupContainer.HEADER_BYTES.toLong())
            ByteArray(2).also { input.read(it) }
        }
        assertFalse(payloadPrefix.contentEquals(byteArrayOf(0x50, 0x4B)))
        BackupContainer.extract(target, temporary.newFolder("passwordless-sealed-read"), null).use {
            assertEquals(emptySnapshot(), it.payload.database)
        }

        RandomAccessFile(target, "rw").use { file ->
            file.seek(file.length() - 1)
            val byte = file.readByte().toInt()
            file.seek(file.length() - 1)
            file.writeByte(byte xor 0x01)
        }
        assertFails<InvalidBackupException> {
            BackupContainer.extract(target, temporary.newFolder("passwordless-tampered-read"), null)
        }
    }

    @Test
    fun legacyPlaintextV1StillImports() {
        val current = File(temporary.root, "current-passwordless.miguardia-backup")
        createPlaintext(current, temporary.newFolder("current-passwordless-work"), emptySnapshot())
        val legacy = File(temporary.root, "legacy-plaintext.miguardia-backup")
        writePlainEntries(legacy, readLogicalEntries(current))

        assertFalse(BackupContainer.readHeader(legacy).encrypted)
        BackupContainer.extract(legacy, temporary.newFolder("legacy-plaintext-read"), null).use {
            assertEquals(emptySnapshot(), it.payload.database)
        }
    }

    @Test
    fun emptyPasswordIsNeverInterpretedAsAnExplicitPlaintextChoice() {
        val emptyPassword = CharArray(0)
        assertFails<InvalidBackupException> {
            BackupContainer.create(
                target = File(temporary.root, "empty-password.miguardia-backup"),
                workingDirectory = temporary.newFolder("empty-password-work"),
                backupId = UUID.fromString(BACKUP_ID),
                createdAtEpochMillis = CREATED_AT,
                zoneId = ZoneId.of("America/Argentina/Buenos_Aires"),
                database = emptySnapshot(),
                preferences = emptyList(),
                photoMode = BackupPhotoMode.OMITTED,
                photoAssets = emptyList(),
                password = emptyPassword,
            )
        }

        val plaintext = File(temporary.root, "explicit-plaintext.miguardia-backup")
        createPlaintext(plaintext, temporary.newFolder("explicit-plaintext-work"), emptySnapshot())
        assertFails<InvalidBackupException> {
            BackupContainer.extract(plaintext, temporary.newFolder("empty-read"), CharArray(0))
        }
    }

    @Test
    fun plaintextContainerRoundTripsAValidatedPhotoWithItsLogicalRow() {
        val png = Base64.getDecoder().decode(ONE_PIXEL_PNG_BASE64)
        val photoFile = File(temporary.root, PHOTO_STORAGE_KEY).also { it.writeBytes(png) }
        val snapshot = emptySnapshot().withPhoto(png.size.toLong())
        val metadata = BackupPhotoMetadata(
            recordId = PHOTO_ID,
            storageKey = PHOTO_STORAGE_KEY,
            mimeType = "image/png",
            byteSize = png.size.toLong(),
            pixelWidth = 1,
            pixelHeight = 1,
            sha256 = "0".repeat(64),
        )
        val target = File(temporary.root, "with-photo.miguardia-backup")

        BackupContainer.create(
            target = target,
            workingDirectory = temporary.newFolder("photo-work"),
            backupId = UUID.fromString(BACKUP_ID),
            createdAtEpochMillis = CREATED_AT,
            zoneId = ZoneId.of("America/Argentina/Buenos_Aires"),
            database = snapshot,
            preferences = emptyList(),
            photoMode = BackupPhotoMode.INCLUDED,
            photoAssets = listOf(BackupPhotoAsset(metadata, photoFile)),
            password = null,
        )

        BackupContainer.extract(target, temporary.newFolder("photo-read"), null).use { extracted ->
            assertEquals(snapshot, extracted.payload.database)
            assertEquals(BackupPhotoMode.INCLUDED, extracted.payload.photoMode)
            assertEquals(1, extracted.payload.photos.size)
            assertTrue(extracted.payload.photos.single().sha256 != "0".repeat(64))
            assertTrue(
                File(extracted.photoDirectory, PHOTO_STORAGE_KEY).readBytes().contentEquals(png),
            )
        }
    }

    @Test
    fun encryptedContainerRejectsWrongPasswordAndTamperingBeforePayload() {
        val target = File(temporary.root, "encrypted.miguardia-backup")
        val createWork = temporary.newFolder("encrypted-work")
        BackupContainer.create(
            target = target,
            workingDirectory = createWork,
            backupId = UUID.fromString(BACKUP_ID),
            createdAtEpochMillis = CREATED_AT,
            zoneId = ZoneId.of("America/Argentina/Buenos_Aires"),
            database = emptySnapshot(),
            preferences = emptyList(),
            photoMode = BackupPhotoMode.OMITTED,
            photoAssets = emptyList(),
            password = "correct horse".toCharArray(),
        )

        assertTrue(createWork.listFiles().orEmpty().isEmpty())
        assertTrue(BackupContainer.readHeader(target).encrypted)
        val wrongRead = temporary.newFolder("wrong-read")
        assertFails<BackupAuthenticationException> {
            BackupContainer.extract(target, wrongRead, "wrong password".toCharArray())
        }
        assertTrue(wrongRead.listFiles().orEmpty().isEmpty())
        RandomAccessFile(target, "rw").use { file ->
            file.seek(file.length() - 1)
            val original = file.readByte().toInt()
            file.seek(file.length() - 1)
            file.writeByte(original xor 0x01)
        }
        val tamperedRead = temporary.newFolder("tampered-read")
        assertFails<BackupAuthenticationException> {
            BackupContainer.extract(target, tamperedRead, "correct horse".toCharArray())
        }
        assertTrue(tamperedRead.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun stableHeaderIsAuthenticatedAsAad() {
        val target = File(temporary.root, "aad.miguardia-backup")
        BackupContainer.create(
            target = target,
            workingDirectory = temporary.newFolder("aad-work"),
            backupId = UUID.fromString(BACKUP_ID),
            createdAtEpochMillis = CREATED_AT,
            zoneId = ZoneId.of("America/Argentina/Buenos_Aires"),
            database = emptySnapshot(),
            preferences = emptyList(),
            photoMode = BackupPhotoMode.OMITTED,
            photoAssets = emptyList(),
            password = "correct horse".toCharArray(),
        )
        RandomAccessFile(target, "rw").use { file ->
            file.seek((MAGIC.size + 4 + 4 + 4).toLong())
            file.writeLong(CREATED_AT + 1)
        }

        assertFails<BackupAuthenticationException> {
            BackupContainer.extract(target, temporary.newFolder("aad-read"), "correct horse".toCharArray())
        }
    }

    @Test
    fun encryptedContainerRequiresPasswordAndUsesFreshSaltAndNonce() {
        val first = File(temporary.root, "first-encrypted.miguardia-backup")
        val second = File(temporary.root, "second-encrypted.miguardia-backup")
        listOf(first, second).forEachIndexed { index, target ->
            BackupContainer.create(
                target = target,
                workingDirectory = temporary.newFolder("random-work-$index"),
                backupId = UUID.fromString(BACKUP_ID),
                createdAtEpochMillis = CREATED_AT,
                zoneId = ZoneId.of("America/Argentina/Buenos_Aires"),
                database = emptySnapshot(),
                preferences = emptyList(),
                photoMode = BackupPhotoMode.OMITTED,
                photoAssets = emptyList(),
                password = "correct horse".toCharArray(),
            )
        }

        assertFails<BackupPasswordRequiredException> {
            BackupContainer.extract(first, temporary.newFolder("password-required"), null)
        }
        assertFalse(first.readBytes().contentEquals(second.readBytes()))
    }

    @Test
    fun truncatedCiphertextAndFutureHeaderAreRejected() {
        val encrypted = File(temporary.root, "truncated-encrypted.miguardia-backup")
        BackupContainer.create(
            target = encrypted,
            workingDirectory = temporary.newFolder("truncated-work"),
            backupId = UUID.fromString(BACKUP_ID),
            createdAtEpochMillis = CREATED_AT,
            zoneId = ZoneId.of("America/Argentina/Buenos_Aires"),
            database = emptySnapshot(),
            preferences = emptyList(),
            photoMode = BackupPhotoMode.OMITTED,
            photoAssets = emptyList(),
            password = "correct horse".toCharArray(),
        )
        val invalidParameters = File(temporary.root, "invalid-kdf.miguardia-backup")
        encrypted.copyTo(invalidParameters)
        RandomAccessFile(invalidParameters, "rw").use { file ->
            file.seek((MAGIC.size + 4 + 4 + 4 + 8).toLong())
            file.writeInt(1)
        }
        assertFails<InvalidBackupException> { BackupContainer.readHeader(invalidParameters) }

        RandomAccessFile(encrypted, "rw").use { it.setLength(it.length() - 1) }
        assertFails<InvalidBackupException> {
            BackupContainer.extract(
                encrypted,
                temporary.newFolder("truncated-read"),
                "correct horse".toCharArray(),
            )
        }

        val future = File(temporary.root, "future.miguardia-backup")
        writePlainContainer(future, listOf("manifest.bin", "database.bin", "preferences.bin", "photos.bin"))
        RandomAccessFile(future, "rw").use { file ->
            file.seek(MAGIC.size.toLong())
            file.writeInt(MiGuardiaBackupContract.FORMAT_VERSION + 1)
        }
        assertFails<InvalidBackupException> { BackupContainer.readHeader(future) }
    }

    @Test
    fun traversalAndMissingCanonicalSectionsAreRejectedDuringPrivateExtraction() {
        val traversal = File(temporary.root, "traversal.miguardia-backup")
        writePlainContainer(traversal, listOf("manifest.bin", "../escape"))
        assertFails<InvalidBackupException> {
            BackupContainer.extract(traversal, temporary.newFolder("traversal-read"), null)
        }
        assertFalse(File(temporary.root, "escape").exists())

        val incomplete = File(temporary.root, "incomplete.miguardia-backup")
        writePlainContainer(incomplete, listOf("manifest.bin", "database.bin"))
        assertFails<InvalidBackupException> {
            BackupContainer.extract(incomplete, temporary.newFolder("incomplete-read"), null)
        }
    }

    @Test
    fun disproportionateZipExpansionIsRejectedBeforePayloadParsing() {
        val bomb = File(temporary.root, "expansion.miguardia-backup")
        val payload = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                listOf("manifest.bin", "preferences.bin", "photos.bin").forEach { name ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(0)
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("database.bin"))
                val zeros = ByteArray(8 * 1024)
                repeat(2_048) { zip.write(zeros) }
                zip.closeEntry()
            }
        }.toByteArray()
        writePlainPayload(bomb, payload)

        assertFails<InvalidBackupException> {
            BackupContainer.extract(bomb, temporary.newFolder("expansion-read"), null)
        }
    }

    @Test
    fun incorrectManifestHashIsRejectedAfterCanonicalPayloadDecoding() {
        val original = File(temporary.root, "hash-original.miguardia-backup")
        BackupContainer.create(
            target = original,
            workingDirectory = temporary.newFolder("hash-work"),
            backupId = UUID.fromString(BACKUP_ID),
            createdAtEpochMillis = CREATED_AT,
            zoneId = ZoneId.of("America/Argentina/Buenos_Aires"),
            database = emptySnapshot(),
            preferences = emptyList(),
            photoMode = BackupPhotoMode.OMITTED,
            photoAssets = emptyList(),
            password = null,
        )
        val entries = readLogicalEntries(original)
        val manifest = BackupPayloadCodec.readManifest(ByteArrayInputStream(entries.getValue("manifest.bin")))
        entries["manifest.bin"] = ByteArrayOutputStream().also { output ->
            BackupPayloadCodec.writeManifest(
                manifest.copy(
                    entries = manifest.entries.mapIndexed { index, entry ->
                        if (index == 0) entry.copy(sha256 = "0".repeat(64)) else entry
                    },
                ),
                output,
            )
        }.toByteArray()
        val payload = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        val corrupted = File(temporary.root, "hash-corrupted.miguardia-backup")
        writePlainPayload(corrupted, payload)

        assertFails<InvalidBackupException> {
            BackupContainer.extract(corrupted, temporary.newFolder("hash-read"), null)
        }
    }

    @Test
    fun photoRowsCannotOutliveMissingOrFalselyTypedBytes() {
        val bytes = "not a png".toByteArray()
        val photoFile = File(temporary.root, PHOTO_STORAGE_KEY).also { it.writeBytes(bytes) }
        val snapshot = emptySnapshot().withPhoto(bytes.size.toLong())
        val metadata = BackupPhotoMetadata(
            recordId = PHOTO_ID,
            storageKey = PHOTO_STORAGE_KEY,
            mimeType = "image/png",
            byteSize = bytes.size.toLong(),
            pixelWidth = 1,
            pixelHeight = 1,
            sha256 = "0".repeat(64),
        )

        assertFails<InvalidBackupException> {
            BackupContainer.create(
                target = File(temporary.root, "missing-photo.miguardia-backup"),
                workingDirectory = temporary.newFolder("missing-photo-work"),
                backupId = UUID.fromString(BACKUP_ID),
                createdAtEpochMillis = CREATED_AT,
                zoneId = ZoneId.of("America/Argentina/Buenos_Aires"),
                database = snapshot,
                preferences = emptyList(),
                photoMode = BackupPhotoMode.INCLUDED,
                photoAssets = emptyList(),
                password = null,
            )
        }
        assertFails<InvalidBackupException> {
            BackupContainer.create(
                target = File(temporary.root, "false-photo.miguardia-backup"),
                workingDirectory = temporary.newFolder("false-photo-work"),
                backupId = UUID.fromString(BACKUP_ID),
                createdAtEpochMillis = CREATED_AT,
                zoneId = ZoneId.of("America/Argentina/Buenos_Aires"),
                database = snapshot,
                preferences = emptyList(),
                photoMode = BackupPhotoMode.INCLUDED,
                photoAssets = listOf(BackupPhotoAsset(metadata, photoFile)),
                password = null,
            )
        }
    }

    @Test
    fun authenticatedManifestCannotSmuggleAnUnlistedPhysicalPhoto() {
        val png = Base64.getDecoder().decode(ONE_PIXEL_PNG_BASE64)
        val photoFile = File(temporary.root, PHOTO_STORAGE_KEY).also { it.writeBytes(png) }
        val original = File(temporary.root, "photo-exact-original.miguardia-backup")
        BackupContainer.create(
            target = original,
            workingDirectory = temporary.newFolder("photo-exact-work"),
            backupId = UUID.fromString(BACKUP_ID),
            createdAtEpochMillis = CREATED_AT,
            zoneId = ZoneId.of("America/Argentina/Buenos_Aires"),
            database = emptySnapshot().withPhoto(png.size.toLong()),
            preferences = emptyList(),
            photoMode = BackupPhotoMode.INCLUDED,
            photoAssets = listOf(
                BackupPhotoAsset(
                    BackupPhotoMetadata(
                        PHOTO_ID,
                        PHOTO_STORAGE_KEY,
                        "image/png",
                        png.size.toLong(),
                        1,
                        1,
                        "0".repeat(64),
                    ),
                    photoFile,
                ),
            ),
            password = null,
        )
        val entries = readLogicalEntries(original)
        val extraName = "photos/99999999-9999-4999-8999-999999999999.png"
        entries[extraName] = png
        val manifest = BackupPayloadCodec.readManifest(ByteArrayInputStream(entries.getValue("manifest.bin")))
        entries["manifest.bin"] = ByteArrayOutputStream().also { output ->
            BackupPayloadCodec.writeManifest(
                manifest.copy(
                    entries = (manifest.entries + BackupEntryManifest(
                        extraName,
                        png.size.toLong(),
                        png.sha256(),
                    )).sortedBy(BackupEntryManifest::name),
                ),
                output,
            )
        }.toByteArray()
        val smuggled = File(temporary.root, "photo-exact-smuggled.miguardia-backup")
        writePlainEntries(smuggled, entries)

        assertFails<InvalidBackupException> {
            BackupContainer.extract(smuggled, temporary.newFolder("photo-exact-read"), null)
        }
    }

    @Test
    fun mergeRequiresExplicitResolutionAndNeverInventsAnId() {
        val current = emptySnapshot().withObjective(OBJECTIVE_ID, "Objetivo actual")
        val incoming = emptySnapshot().withObjective(OBJECTIVE_ID, "Objetivo de copia")
        val comparison = BackupComparator.compare(current, incoming)

        assertEquals(1, comparison.conflicts.size)
        assertFails<UnresolvedBackupConflictException> {
            BackupComparator.mergeDatabase(current, incoming, comparison.conflicts, emptyList())
        }
        val conflict = comparison.conflicts.single()
        val merged = BackupComparator.mergeDatabase(
            current,
            incoming,
            comparison.conflicts,
            listOf(ResolvedBackupConflict(conflict.id, BackupConflictResolution.USE_BACKUP)),
        )
        val objective = merged.table("objectives").records.single()
        assertEquals(BackupValue.Text(OBJECTIVE_ID), objective.values.first())
        assertEquals(BackupValue.Text("Objetivo de copia"), objective.values[1])
    }

    @Test
    fun changedPrimaryKeyRecordIsCheckedAgainstOtherNaturalIdentities() {
        val firstId = "01010101-0101-4101-8101-010101010101"
        val secondId = "02020202-0202-4202-8202-020202020202"
        val current = emptySnapshot().withRecords(
            "objectives",
            listOf(
                objective(firstId, "Primero", "P1"),
                objective(secondId, "Segundo", "P2"),
            ),
        )
        val incoming = emptySnapshot().withRecords(
            "objectives",
            listOf(objective(firstId, "Primero cambiado", "P2")),
        )

        val conflicts = BackupComparator.compare(current, incoming).conflicts

        assertEquals(2, conflicts.size)
        assertEquals(1, conflicts.count { it.currentKey == it.incomingKey })
        val naturalConflict = conflicts.single { it.currentKey != it.incomingKey }
        assertEquals(BackupValue.Text(secondId), naturalConflict.currentKey.primaryKeyValues.single())
        assertEquals(BackupValue.Text(firstId), naturalConflict.incomingKey.primaryKeyValues.single())
    }

    @Test
    fun changedPrimaryKeyRecordChecksOtherIntervalsButExcludesItsOwnCounterpart() {
        val firstId = "66666666-6666-4666-8666-666666666666"
        val secondId = "77777777-7777-4777-8777-777777777777"
        val current = emptySnapshot().withShifts(
            listOf(
                shift(firstId, 1_000L, 2_000L),
                shift(secondId, 3_000L, 4_000L),
            ),
        )
        val incoming = emptySnapshot().withShifts(listOf(shift(firstId, 3_500L, 4_500L)))

        val conflicts = BackupComparator.compare(current, incoming).conflicts

        assertEquals(2, conflicts.size)
        assertEquals(1, conflicts.count { it.currentKey == it.incomingKey })
        val temporalConflict = conflicts.single { it.currentKey != it.incomingKey }
        assertEquals(BackupValue.Text(secondId), temporalConflict.currentKey.primaryKeyValues.single())
        assertEquals(BackupValue.Text(firstId), temporalConflict.incomingKey.primaryKeyValues.single())

        val onlyOwnCounterpart = BackupComparator.compare(
            emptySnapshot().withShifts(listOf(shift(firstId, 1_000L, 2_000L))),
            emptySnapshot().withShifts(listOf(shift(firstId, 1_500L, 2_500L))),
        )
        assertEquals(1, onlyOwnCounterpart.conflicts.size)
        assertEquals(onlyOwnCounterpart.conflicts.single().currentKey, onlyOwnCounterpart.conflicts.single().incomingKey)
    }

    @Test
    fun mergeRecomputesConflictsAndRejectsAnIncompletePreview() {
        val current = emptySnapshot().withShifts(
            listOf(shift("66666666-6666-4666-8666-666666666666", 1_000L, 2_000L)),
        )
        val incoming = emptySnapshot().withShifts(
            listOf(shift("77777777-7777-4777-8777-777777777777", 1_500L, 2_500L)),
        )
        assertEquals(1, BackupComparator.compare(current, incoming).conflicts.size)

        val error = assertFails<UnresolvedBackupConflictException> {
            BackupComparator.mergeDatabase(current, incoming, conflicts = emptyList(), resolutions = emptyList())
        }

        assertTrue(error.message.orEmpty().contains("desactualizada o incompleta"))
    }

    @Test
    fun mergeRejectsDifferentTimelines() {
        val current = emptySnapshot().withTimeline(TIMELINE_ID)
        val incoming = emptySnapshot().withTimeline(OTHER_TIMELINE_ID)

        val comparison = BackupComparator.compare(current, incoming)

        assertFalse(comparison.timelineCompatible)
        assertFails<UnresolvedBackupConflictException> {
            BackupComparator.mergeDatabase(current, incoming, comparison.conflicts, emptyList())
        }
    }

    @Test
    fun destinationWithoutTimelineCanMergeEvenWhenItAlreadyContainsLocalData() {
        val current = emptySnapshot().withObjective(OBJECTIVE_ID, "Objetivo actual")
        val incoming = emptySnapshot().withTimeline(TIMELINE_ID)

        val comparison = BackupComparator.compare(current, incoming)
        val merged = BackupComparator.mergeDatabase(current, incoming, comparison.conflicts, emptyList())

        assertTrue(comparison.timelineCompatible)
        assertFalse(comparison.destinationEmpty)
        assertEquals(TIMELINE_ID, merged.timelineId)
        assertEquals(1, merged.table("objectives").records.size)
    }

    @Test
    fun repeatingTheSameMergeIsIdempotent() {
        val current = emptySnapshot()
        val incoming = emptySnapshot().withObjective(OBJECTIVE_ID, "Objetivo de copia")
        val firstComparison = BackupComparator.compare(current, incoming)
        val first = BackupComparator.mergeDatabase(
            current,
            incoming,
            firstComparison.conflicts,
            emptyList(),
        )
        val secondComparison = BackupComparator.compare(first, incoming)
        val second = BackupComparator.mergeDatabase(
            first,
            incoming,
            secondComparison.conflicts,
            emptyList(),
        )

        assertEquals(first, second)
        assertEquals(0, secondComparison.newRecords)
        assertEquals(1, secondComparison.identicalRecords)
        assertTrue(secondComparison.conflicts.isEmpty())
    }

    @Test
    fun contradictoryOverlapResolutionsAreRejectedWithoutInventingAPlan() {
        val current = emptySnapshot().withShifts(
            listOf(
                shift("66666666-6666-4666-8666-666666666666", 1_000L, 2_000L),
                shift("77777777-7777-4777-8777-777777777777", 2_000L, 3_000L),
            ),
        )
        val incoming = emptySnapshot().withShifts(
            listOf(shift("88888888-8888-4888-8888-888888888888", 1_500L, 2_500L)),
        )
        val comparison = BackupComparator.compare(current, incoming)
        assertEquals(2, comparison.conflicts.size)
        assertEquals(0, comparison.newRecords)

        assertFails<UnresolvedBackupConflictException> {
            BackupComparator.mergeDatabase(
                current,
                incoming,
                comparison.conflicts,
                listOf(
                    ResolvedBackupConflict(
                        comparison.conflicts[0].id,
                        BackupConflictResolution.KEEP_CURRENT,
                    ),
                    ResolvedBackupConflict(
                        comparison.conflicts[1].id,
                        BackupConflictResolution.USE_BACKUP,
                    ),
                ),
            )
        }
    }

    @Test
    fun distinctCompatibleOverlapsCanBeKeptWithoutInventingIds() {
        val currentId = "66666666-6666-4666-8666-666666666666"
        val incomingId = "88888888-8888-4888-8888-888888888888"
        val current = emptySnapshot().withShifts(listOf(shift(currentId, 1_000L, 2_000L)))
        val incoming = emptySnapshot().withShifts(listOf(shift(incomingId, 1_500L, 2_500L)))
        val comparison = BackupComparator.compare(current, incoming)
        val conflict = comparison.conflicts.single()

        assertTrue(conflict.keepBothAllowed)
        val merged = BackupComparator.mergeDatabase(
            current,
            incoming,
            comparison.conflicts,
            listOf(ResolvedBackupConflict(conflict.id, BackupConflictResolution.KEEP_BOTH)),
        )

        assertEquals(
            setOf(BackupValue.Text(currentId), BackupValue.Text(incomingId)),
            merged.table("shifts").records.map { it.values.first() }.toSet(),
        )
    }

    @Test
    fun usingBackupForAParentConflictCannotHideTheLossOfCurrentDescendants() {
        val current = emptySnapshot()
            .withShifts(listOf(shift(SHIFT_ID, 1_000L, 2_000L)))
            .withShiftNote(NOTE_ID, SHIFT_ID)
        val incoming = emptySnapshot()
            .withShifts(listOf(shift(SHIFT_ID, 1_000L, 3_000L)))
        val comparison = BackupComparator.compare(current, incoming)
        val conflict = comparison.conflicts.single()

        val error = assertFails<UnresolvedBackupConflictException> {
            BackupComparator.mergeDatabase(
                current,
                incoming,
                comparison.conflicts,
                listOf(ResolvedBackupConflict(conflict.id, BackupConflictResolution.USE_BACKUP)),
            )
        }

        assertTrue(error.message.orEmpty().contains("1 registros actuales relacionados"))
        assertTrue(error.message.orEmpty().contains("shift_notes: 1"))
    }

    @Test
    fun usingBackupForAChangedShiftKeepsAnIdenticalSnapshotFromTheBackup() {
        val snapshot = shiftSnapshot(SHIFT_ID)
        val current = emptySnapshot()
            .withShifts(listOf(shift(SHIFT_ID, 1_000L, 2_000L)))
            .withRecords("shift_work_snapshots", listOf(snapshot))
        val incoming = emptySnapshot()
            .withShifts(listOf(shift(SHIFT_ID, 1_000L, 3_000L)))
            .withRecords("shift_work_snapshots", listOf(snapshot))
        val comparison = BackupComparator.compare(current, incoming)
        val conflict = comparison.conflicts.single()

        val merged = BackupComparator.mergeDatabase(
            current,
            incoming,
            comparison.conflicts,
            listOf(ResolvedBackupConflict(conflict.id, BackupConflictResolution.USE_BACKUP)),
        )

        assertEquals(3_000L, (merged.table("shifts").records.single().values[2] as BackupValue.Integer).value)
        assertEquals(listOf(snapshot), merged.table("shift_work_snapshots").records)
    }

    @Test
    fun keepingCurrentNaturalIdentityDiscardsIncomingDependentAggregate() {
        val current = emptySnapshot()
            .withTimeline(TIMELINE_ID)
            .withObjective(OBJECTIVE_ID, "Objetivo actual")
        val incoming = emptySnapshot()
            .withTimeline(TIMELINE_ID)
            .withObjective(OTHER_OBJECTIVE_ID, "Objetivo de copia")
            .withWorkPlace(PLACE_ID, OTHER_OBJECTIVE_ID)
        val comparison = BackupComparator.compare(current, incoming)
        val conflict = comparison.conflicts.single { it.table == "objectives" }

        val merged = BackupComparator.mergeDatabase(
            current,
            incoming,
            comparison.conflicts,
            listOf(ResolvedBackupConflict(conflict.id, BackupConflictResolution.KEEP_CURRENT)),
        )

        assertEquals(
            setOf(BackupValue.Text(OBJECTIVE_ID)),
            merged.table("objectives").records.map { it.values.first() }.toSet(),
        )
        assertTrue(merged.table("work_places").records.isEmpty())
    }

    @Test
    fun allRoomUniqueIdentitiesAreDetectedAcrossCurrentAndIncomingStates() {
        val revisionConflict = uniqueConflict(
            tableName = "work_configuration_revisions",
            current = record(
                "work_configuration_revisions",
                "id" to BackupValue.Text("31313131-3131-4131-8131-313131313131"),
                "timelineId" to BackupValue.Text(TIMELINE_ID),
                "effectiveFrom" to BackupValue.Text("2026-08-01"),
            ),
            incoming = record(
                "work_configuration_revisions",
                "id" to BackupValue.Text("32323232-3232-4232-8232-323232323232"),
                "timelineId" to BackupValue.Text(TIMELINE_ID),
                "effectiveFrom" to BackupValue.Text("2026-08-01"),
            ),
        )
        val templateConflict = uniqueConflict(
            tableName = "work_templates",
            current = template("33333333-3333-4333-8333-333333333334"),
            incoming = template("34343434-3434-4434-8434-343434343434"),
        )
        val occurrenceConflict = uniqueConflict(
            tableName = "recurring_occurrences",
            current = occurrence("35353535-3535-4535-8535-353535353535", "2026-08-01", SHIFT_ID),
            incoming = occurrence("36363636-3636-4636-8636-363636363636", "2026-08-02", SHIFT_ID),
        )
        val nullableOccurrence = uniqueConflict(
            tableName = "recurring_occurrences",
            current = occurrence("37373737-3737-4737-8737-373737373737", "2026-08-03", null),
            incoming = occurrence("38383838-3838-4838-8838-383838383838", "2026-08-04", null),
        )

        assertEquals(1, revisionConflict.conflicts.size)
        assertEquals(1, templateConflict.conflicts.size)
        assertEquals(1, occurrenceConflict.conflicts.size)
        assertTrue(nullableOccurrence.conflicts.isEmpty())
    }

    @Test
    fun removingAnExtraClassDropsTheDependentActualAggregateInsteadOfLeavingPartialIntervals() {
        val currentClassId = "39393939-3939-4939-8939-393939393939"
        val incomingClassId = "40404040-4040-4040-8040-404040404040"
        val current = emptySnapshot()
            .withTimeline(TIMELINE_ID)
            .withRecords("extra_work_classes", listOf(extraClass(currentClassId)))
        val incoming = emptySnapshot()
            .withTimeline(TIMELINE_ID)
            .withRecords("extra_work_classes", listOf(extraClass(incomingClassId)))
            .withRecords("shifts", listOf(shift(SHIFT_ID, 1_000L, 2_000L)))
            .withRecords("shift_work_snapshots", listOf(shiftSnapshot(SHIFT_ID)))
            .withRecords("shift_actual_records", listOf(shiftActual(SHIFT_ID)))
            .withRecords("shift_extra_intervals", listOf(extraInterval(SHIFT_ID, incomingClassId)))
        val comparison = BackupComparator.compare(current, incoming)
        val conflict = comparison.conflicts.single { it.table == "extra_work_classes" }

        val merged = BackupComparator.mergeDatabase(
            current,
            incoming,
            comparison.conflicts,
            listOf(ResolvedBackupConflict(conflict.id, BackupConflictResolution.KEEP_CURRENT)),
        )

        assertEquals(1, merged.table("shifts").records.size)
        assertEquals(1, merged.table("shift_work_snapshots").records.size)
        assertTrue(merged.table("shift_actual_records").records.isEmpty())
        assertTrue(merged.table("shift_extra_intervals").records.isEmpty())
    }

    @Test
    fun removingAMandatorySnapshotAlsoRemovesItsShiftPair() {
        val currentTemplateId = "41414141-4141-4141-8141-414141414141"
        val incomingTemplateId = "42424242-4242-4242-8242-424242424242"
        val current = emptySnapshot()
            .withTimeline(TIMELINE_ID)
            .withRecords("work_templates", listOf(template(currentTemplateId)))
        val incoming = emptySnapshot()
            .withTimeline(TIMELINE_ID)
            .withRecords("work_templates", listOf(template(incomingTemplateId)))
            .withRecords("shifts", listOf(shift(SHIFT_ID, 1_000L, 2_000L)))
            .withRecords("shift_work_snapshots", listOf(shiftSnapshot(SHIFT_ID, incomingTemplateId)))
        val comparison = BackupComparator.compare(current, incoming)
        val conflict = comparison.conflicts.single { it.table == "work_templates" }

        val merged = BackupComparator.mergeDatabase(
            current,
            incoming,
            comparison.conflicts,
            listOf(ResolvedBackupConflict(conflict.id, BackupConflictResolution.KEEP_CURRENT)),
        )

        assertTrue(merged.table("shifts").records.isEmpty())
        assertTrue(merged.table("shift_work_snapshots").records.isEmpty())
    }

    @Test
    fun medicalLeaveAndVacationOverlapRequiresAResolutionAndNeverAllowsBoth() {
        val current = emptySnapshot().withVacation(VACATION_ID, "2026-08-10", "2026-08-20")
        val incoming = emptySnapshot().withMedicalLeave(MEDICAL_ID, "2026-08-15", "2026-08-16")
        val comparison = BackupComparator.compare(current, incoming)
        val conflict = comparison.conflicts.single()

        assertFalse(conflict.keepBothAllowed)
        val merged = BackupComparator.mergeDatabase(
            current,
            incoming,
            comparison.conflicts,
            listOf(ResolvedBackupConflict(conflict.id, BackupConflictResolution.KEEP_CURRENT)),
        )

        assertEquals(1, merged.table("vacations").records.size)
        assertTrue(merged.table("medical_leaves").records.isEmpty())
    }

    @Test
    fun validDismissedNotificationKeysAreUnionedWithoutAConflict() {
        val key = MiGuardiaBackupContract.DISMISSED_EVENT_KEYS_PREFERENCE
        val current = listOf(
            BackupPreference(key, BackupPreferenceType.TEXT_LIST, listOf("shift:$OBJECTIVE_ID")),
        )
        val incoming = listOf(
            BackupPreference(key, BackupPreferenceType.TEXT_LIST, listOf("shift:$PHOTO_ID")),
        )
        val comparison = BackupComparator.compare(
            emptySnapshot(),
            emptySnapshot(),
            current,
            incoming,
        )

        assertTrue(comparison.conflicts.isEmpty())
        assertEquals(
            listOf("shift:$OBJECTIVE_ID", "shift:$PHOTO_ID"),
            BackupComparator.mergePreferences(
                current,
                incoming,
                comparison.conflicts,
                emptyList(),
            ).single().values,
        )
    }

    @Test
    fun dismissedNotificationUnionOverTheLimitBlocksMergeBeforeWriting() {
        val key = MiGuardiaBackupContract.DISMISSED_EVENT_KEYS_PREFERENCE
        val current = listOf(
            BackupPreference(
                key,
                BackupPreferenceType.TEXT_LIST,
                List(MiGuardiaBackupContract.MAX_DISMISSED_EVENT_KEYS - 1) { index -> "shift:$index" },
            ),
        )
        val atLimit = listOf(
            BackupPreference(key, BackupPreferenceType.TEXT_LIST, listOf("shift:last")),
        )
        assertEquals(
            MiGuardiaBackupContract.MAX_DISMISSED_EVENT_KEYS,
            BackupComparator.mergePreferences(current, atLimit, emptyList(), emptyList()).single().values.size,
        )
        val incoming = listOf(
            BackupPreference(key, BackupPreferenceType.TEXT_LIST, listOf("shift:last", "shift:overflow")),
        )

        val comparison = BackupComparator.compare(
            emptySnapshot(),
            emptySnapshot(),
            current,
            incoming,
        )

        assertTrue(comparison.mergeBlockedReason.orEmpty().contains("límite seguro"))
        assertEquals(BackupRecordClassification.INVALID, comparison.conflicts.single().classification)
        assertFails<UnresolvedBackupConflictException> {
            BackupComparator.mergePreferences(current, incoming, emptyList(), emptyList())
        }
    }

    @Test
    fun duplicatePrimaryKeyIsRejectedBeforeAnyRestore() {
        val one = emptySnapshot().withObjective(OBJECTIVE_ID, "Uno")
        val objectiveTable = one.table("objectives")
        val duplicate = one.copy(
            tables = one.tables.map { table ->
                if (table.name == "objectives") {
                    objectiveTable.copy(records = objectiveTable.records + objectiveTable.records.single())
                } else {
                    table
                }
            },
        )

        assertFails<InvalidBackupException> { MiGuardiaBackupSchemaV5.requireValid(duplicate) }
    }

    @Test
    fun databaseRowsMustUseTheSameCanonicalPrimaryKeyOrderAsRoomCapture() {
        val first = objective("01010101-0101-4101-8101-010101010101", "Primero", "P1")
        val second = objective("02020202-0202-4202-8202-020202020202", "Segundo", "P2")
        val canonical = emptySnapshot().withRecords("objectives", listOf(first, second))
        val reversed = emptySnapshot().withRecords("objectives", listOf(second, first))

        MiGuardiaBackupSchemaV5.requireValid(canonical)
        assertFails<InvalidBackupException> { MiGuardiaBackupSchemaV5.requireValid(reversed) }
        assertFails<InvalidBackupException> {
            BackupPayloadCodec.writeDatabase(reversed, ByteArrayOutputStream())
        }
    }

    @Test
    fun decodedDatabaseHasAnExplicitMemoryBudget() {
        val encoded = ByteArrayOutputStream().also { output ->
            BackupPayloadCodec.writeDatabase(emptySnapshot(), output)
        }.toByteArray()

        assertFails<InvalidBackupException> {
            BackupPayloadCodec.readDatabase(ByteArrayInputStream(encoded), decodedMemoryLimitBytes = 32L)
        }
        assertEquals(emptySnapshot(), BackupPayloadCodec.readDatabase(ByteArrayInputStream(encoded)))
    }

    @Test
    fun conflictBudgetDisablesOnlyMergeAndKeepsTheIncomingSnapshotUsableForReplace() {
        val records = (1..MiGuardiaBackupContract.MAX_MERGE_CONFLICTS + 1).map { index ->
            val id = UUID(0L, index.toLong()).toString()
            objective(id, "Actual $index", "O$index")
        }
        val incomingRecords = records.mapIndexed { index, record ->
            record.copy(values = record.values.toMutableList().also { it[1] = BackupValue.Text("Copia ${index + 1}") })
        }
        val current = emptySnapshot().withRecords("objectives", records)
        val incoming = emptySnapshot().withRecords("objectives", incomingRecords)

        val comparison = BackupComparator.compare(current, incoming)

        assertTrue(comparison.mergeBlockedReason?.isNotBlank() == true)
        assertEquals(1, comparison.conflicts.size)
        assertEquals(BackupRecordClassification.INVALID, comparison.conflicts.single().classification)
        assertFails<UnresolvedBackupConflictException> {
            BackupComparator.mergeDatabase(current, incoming, comparison.conflicts, emptyList())
        }
        MiGuardiaBackupSchemaV5.requireValid(incoming)
    }

    @Test
    fun utf8TextLimitAcceptsTheBoundaryAndRejectsOneByteMore() {
        val boundary = "ñ".repeat(MiGuardiaBackupContract.MAX_TEXT_BYTES / 2)
        val valid = listOf(BackupPreference("test.boundary", BackupPreferenceType.TEXT, listOf(boundary)))
        val encoded = ByteArrayOutputStream().also { BackupPayloadCodec.writePreferences(valid, it) }.toByteArray()
        assertEquals(valid, BackupPayloadCodec.readPreferences(ByteArrayInputStream(encoded)))

        val tooLarge = boundary + "a"
        assertFails<InvalidBackupException> {
            BackupPayloadCodec.writePreferences(
                listOf(BackupPreference("test.too_large", BackupPreferenceType.TEXT, listOf(tooLarge))),
                ByteArrayOutputStream(),
            )
        }
    }

    @Test
    fun photoCountLimitAcceptsTheBoundaryAndRejectsOneMore() {
        val atLimit = (1..MiGuardiaBackupContract.MAX_PHOTO_COUNT).map { index ->
            val id = UUID(0L, index.toLong()).toString()
            BackupPhotoMetadata(
                recordId = id,
                storageKey = "$id.png",
                mimeType = "image/png",
                byteSize = 1,
                pixelWidth = 1,
                pixelHeight = 1,
                sha256 = "0".repeat(64),
            )
        }
        val encoded = ByteArrayOutputStream().also { BackupPayloadCodec.writePhotos(atLimit, it) }.toByteArray()

        assertEquals(atLimit, BackupPayloadCodec.readPhotos(ByteArrayInputStream(encoded)))
        assertFails<InvalidBackupException> {
            BackupPayloadCodec.writePhotos(
                atLimit + atLimit.last().copy(
                    recordId = UUID(0L, (MiGuardiaBackupContract.MAX_PHOTO_COUNT + 1L)).toString(),
                    storageKey = "${UUID(0L, (MiGuardiaBackupContract.MAX_PHOTO_COUNT + 1L))}.png",
                ),
                ByteArrayOutputStream(),
            )
        }
    }

    @Test
    fun dismissedNotificationKeysHaveTheirOwnBoundWithoutExpandingOtherPreferenceLists() {
        val dismissed = BackupPreference(
            MiGuardiaBackupContract.DISMISSED_EVENT_KEYS_PREFERENCE,
            BackupPreferenceType.TEXT_LIST,
            List(MiGuardiaBackupContract.MAX_DISMISSED_EVENT_KEYS) { index -> "shift:$index" },
        )
        val encoded = ByteArrayOutputStream().also { BackupPayloadCodec.writePreferences(listOf(dismissed), it) }
            .toByteArray()

        assertEquals(listOf(dismissed), BackupPayloadCodec.readPreferences(ByteArrayInputStream(encoded)))
        assertFails<InvalidBackupException> {
            BackupPayloadCodec.writePreferences(
                listOf(dismissed.copy(values = dismissed.values + "shift:overflow")),
                ByteArrayOutputStream(),
            )
        }
        assertFails<InvalidBackupException> {
            BackupPayloadCodec.writePreferences(
                listOf(BackupPreference("summary.hidden_families", BackupPreferenceType.TEXT_LIST, List(129) { "$it" })),
                ByteArrayOutputStream(),
            )
        }
    }

    private fun emptySnapshot(): BackupDatabaseSnapshot = BackupDatabaseSnapshot(
        timelineId = null,
        tables = MiGuardiaBackupSchemaV5.tables.map { spec ->
            BackupTable(spec.name, spec.columns, spec.primaryKey, emptyList())
        },
    )

    private fun BackupDatabaseSnapshot.withRecords(
        tableName: String,
        records: List<BackupRecord>,
    ): BackupDatabaseSnapshot = copy(
        tables = tables.map { table -> if (table.name == tableName) table.copy(records = records) else table },
    )

    private fun uniqueConflict(
        tableName: String,
        current: BackupRecord,
        incoming: BackupRecord,
    ): BackupComparison = BackupComparator.compare(
        emptySnapshot().withRecords(tableName, listOf(current)),
        emptySnapshot().withRecords(tableName, listOf(incoming)),
    )

    private fun record(tableName: String, vararg values: Pair<String, BackupValue>): BackupRecord {
        val spec = MiGuardiaBackupSchemaV5.byName.getValue(tableName)
        val byName = values.toMap()
        return BackupRecord(spec.columns.map { column -> byName[column] ?: BackupValue.Null })
    }

    private fun objective(id: String, name: String, abbreviation: String): BackupRecord = record(
        "objectives",
        "id" to BackupValue.Text(id),
        "fullName" to BackupValue.Text(name),
        "abbreviation" to BackupValue.Text(abbreviation),
        "isActive" to BackupValue.Integer(1),
        "createdAtEpochMillis" to BackupValue.Integer(CREATED_AT),
        "updatedAtEpochMillis" to BackupValue.Integer(CREATED_AT),
    )

    private fun template(id: String): BackupRecord = record(
        "work_templates",
        "id" to BackupValue.Text(id),
        "timelineId" to BackupValue.Text(TIMELINE_ID),
        "sector" to BackupValue.Text("PRIVATE_SECURITY"),
        "workPlaceId" to BackupValue.Text(PLACE_ID),
        "objectiveId" to BackupValue.Text(OBJECTIVE_ID),
        "workTypeId" to BackupValue.Text(WORK_TYPE_ID),
        "startTime" to BackupValue.Text("08:00"),
        "endTime" to BackupValue.Text("16:00"),
        "colorArgb" to BackupValue.Integer(0xFF000000),
        "isActive" to BackupValue.Integer(1),
        "createdAtEpochMillis" to BackupValue.Integer(CREATED_AT),
        "updatedAtEpochMillis" to BackupValue.Integer(CREATED_AT),
    )

    private fun occurrence(planId: String, localDate: String, shiftId: String?): BackupRecord = record(
        "recurring_occurrences",
        "planId" to BackupValue.Text(planId),
        "localDate" to BackupValue.Text(localDate),
        "revisionId" to BackupValue.Text(RECURRING_REVISION_ID),
        "shiftId" to (shiftId?.let(BackupValue::Text) ?: BackupValue.Null),
        "state" to BackupValue.Text("AUTOMATIC"),
        "createdAtEpochMillis" to BackupValue.Integer(CREATED_AT),
        "updatedAtEpochMillis" to BackupValue.Integer(CREATED_AT),
    )

    private fun extraClass(id: String): BackupRecord = record(
        "extra_work_classes",
        "id" to BackupValue.Text(id),
        "timelineId" to BackupValue.Text(TIMELINE_ID),
        "sector" to BackupValue.Text("PRIVATE_SECURITY"),
        "name" to BackupValue.Text("Extra ficticia"),
        "normalizedNameKey" to BackupValue.Text("extra ficticia"),
        "helpsMeetHoursReference" to BackupValue.Integer(1),
        "showDedicatedSummary" to BackupValue.Integer(1),
        "isActive" to BackupValue.Integer(1),
        "createdAtEpochMillis" to BackupValue.Integer(CREATED_AT),
        "updatedAtEpochMillis" to BackupValue.Integer(CREATED_AT),
    )

    private fun shiftSnapshot(shiftId: String, templateId: String? = null): BackupRecord = record(
        "shift_work_snapshots",
        "shiftId" to BackupValue.Text(shiftId),
        "timelineId" to BackupValue.Text(TIMELINE_ID),
        "sector" to BackupValue.Text("PRIVATE_SECURITY"),
        "configurationRevisionId" to BackupValue.Text(CONFIGURATION_REVISION_ID),
        "workPlaceId" to BackupValue.Text(PLACE_ID),
        "objectiveId" to BackupValue.Text(OBJECTIVE_ID),
        "templateId" to (templateId?.let(BackupValue::Text) ?: BackupValue.Null),
        "workTypeId" to BackupValue.Text(WORK_TYPE_ID),
        "workTypeNameSnapshot" to BackupValue.Text("Turno habitual"),
        "workTypeBehaviorSnapshot" to BackupValue.Text("WORKED"),
    )

    private fun shiftActual(shiftId: String): BackupRecord = record(
        "shift_actual_records",
        "shiftId" to BackupValue.Text(shiftId),
        "timelineId" to BackupValue.Text(TIMELINE_ID),
        "sector" to BackupValue.Text("PRIVATE_SECURITY"),
        "actualStartEpochMillis" to BackupValue.Integer(1_000L),
        "actualEndEpochMillis" to BackupValue.Integer(3_000L),
        "differenceReason" to BackupValue.Text("Motivo ficticio"),
        "createdAtEpochMillis" to BackupValue.Integer(CREATED_AT),
        "updatedAtEpochMillis" to BackupValue.Integer(CREATED_AT),
    )

    private fun extraInterval(shiftId: String, classId: String): BackupRecord = record(
        "shift_extra_intervals",
        "id" to BackupValue.Text(EXTRA_INTERVAL_ID),
        "shiftId" to BackupValue.Text(shiftId),
        "timelineId" to BackupValue.Text(TIMELINE_ID),
        "sector" to BackupValue.Text("PRIVATE_SECURITY"),
        "extraWorkClassId" to BackupValue.Text(classId),
        "startEpochMillis" to BackupValue.Integer(2_000L),
        "endEpochMillis" to BackupValue.Integer(3_000L),
        "classNameSnapshot" to BackupValue.Text("Extra ficticia"),
        "helpsMeetHoursReferenceSnapshot" to BackupValue.Integer(1),
        "showDedicatedSummarySnapshot" to BackupValue.Integer(1),
        "createdAtEpochMillis" to BackupValue.Integer(CREATED_AT),
        "updatedAtEpochMillis" to BackupValue.Integer(CREATED_AT),
    )

    private fun BackupDatabaseSnapshot.withObjective(id: String, name: String): BackupDatabaseSnapshot = copy(
        tables = tables.map { table ->
            if (table.name == "objectives") {
                table.copy(
                    records = listOf(
                        BackupRecord(
                            listOf(
                                BackupValue.Text(id),
                                BackupValue.Text(name),
                                BackupValue.Text("ON"),
                                BackupValue.Null,
                                BackupValue.Null,
                                BackupValue.Integer(1),
                                BackupValue.Integer(CREATED_AT),
                                BackupValue.Integer(CREATED_AT),
                            ),
                        ),
                    ),
                )
            } else {
                table
            }
        },
    )

    private fun BackupDatabaseSnapshot.withTimeline(id: String): BackupDatabaseSnapshot = copy(
        timelineId = id,
        tables = tables.map { table ->
            if (table.name == "work_configuration_roots") {
                table.copy(
                    records = listOf(
                        BackupRecord(listOf(BackupValue.Text(id), BackupValue.Integer(0))),
                    ),
                )
            } else {
                table
            }
        },
    )

    private fun BackupDatabaseSnapshot.withPhoto(byteSize: Long): BackupDatabaseSnapshot = copy(
        tables = tables.map { table ->
            if (table.name == "schedule_photos") {
                table.copy(
                    records = listOf(
                        BackupRecord(
                            listOf(
                                BackupValue.Text(PHOTO_ID),
                                BackupValue.Text("2026-08"),
                                BackupValue.Null,
                                BackupValue.Null,
                                BackupValue.Null,
                                BackupValue.Text(PHOTO_STORAGE_KEY),
                                BackupValue.Text("image/png"),
                                BackupValue.Integer(byteSize),
                                BackupValue.Integer(1),
                                BackupValue.Integer(1),
                                BackupValue.Integer(CREATED_AT),
                                BackupValue.Integer(CREATED_AT),
                            ),
                        ),
                    ),
                )
            } else {
                table
            }
        },
    )

    private fun BackupDatabaseSnapshot.withShifts(records: List<BackupRecord>): BackupDatabaseSnapshot = copy(
        tables = tables.map { table -> if (table.name == "shifts") table.copy(records = records) else table },
    )

    private fun BackupDatabaseSnapshot.withShiftNote(id: String, shiftId: String): BackupDatabaseSnapshot = copy(
        tables = tables.map { table ->
            if (table.name == "shift_notes") {
                table.copy(
                    records = listOf(
                        BackupRecord(
                            listOf(
                                BackupValue.Text(id),
                                BackupValue.Text(shiftId),
                                BackupValue.Text("Nota ficticia"),
                                BackupValue.Integer(CREATED_AT),
                                BackupValue.Integer(CREATED_AT),
                            ),
                        ),
                    ),
                )
            } else {
                table
            }
        },
    )

    private fun BackupDatabaseSnapshot.withWorkPlace(id: String, objectiveId: String): BackupDatabaseSnapshot = copy(
        tables = tables.map { table ->
            if (table.name == "work_places") {
                table.copy(
                    records = listOf(
                        BackupRecord(
                            listOf(
                                BackupValue.Text(id),
                                BackupValue.Text(TIMELINE_ID),
                                BackupValue.Text("PRIVATE_SECURITY"),
                                BackupValue.Text(objectiveId),
                                BackupValue.Integer(1),
                                BackupValue.Integer(CREATED_AT),
                                BackupValue.Integer(CREATED_AT),
                            ),
                        ),
                    ),
                )
            } else {
                table
            }
        },
    )

    private fun BackupDatabaseSnapshot.withVacation(id: String, start: String, end: String): BackupDatabaseSnapshot =
        withDateRange("vacations", id, start, end, includePrivateNote = false)

    private fun BackupDatabaseSnapshot.withMedicalLeave(id: String, start: String, end: String): BackupDatabaseSnapshot =
        withDateRange("medical_leaves", id, start, end, includePrivateNote = true)

    private fun BackupDatabaseSnapshot.withDateRange(
        tableName: String,
        id: String,
        start: String,
        end: String,
        includePrivateNote: Boolean,
    ): BackupDatabaseSnapshot = copy(
        tables = tables.map { table ->
            if (table.name == tableName) {
                val values = mutableListOf<BackupValue>(
                    BackupValue.Text(id),
                    BackupValue.Text(start),
                    BackupValue.Text(end),
                )
                if (includePrivateNote) values += BackupValue.Null
                values += BackupValue.Integer(CREATED_AT)
                values += BackupValue.Integer(CREATED_AT)
                table.copy(records = listOf(BackupRecord(values)))
            } else {
                table
            }
        },
    )

    private fun shift(id: String, start: Long, end: Long): BackupRecord = BackupRecord(
        listOf(
            BackupValue.Text(id),
            BackupValue.Integer(start),
            BackupValue.Integer(end),
            BackupValue.Text("America/Argentina/Buenos_Aires"),
            BackupValue.Text("2026-08-31"),
            BackupValue.Text("Objetivo ficticio"),
            BackupValue.Text("FIC"),
            BackupValue.Null,
            BackupValue.Text("08:00"),
            BackupValue.Text("16:00"),
            BackupValue.Integer(0xFF000000),
            BackupValue.Null,
            BackupValue.Text("PLANNED"),
            BackupValue.Null,
            BackupValue.Integer(CREATED_AT),
            BackupValue.Integer(CREATED_AT),
        ),
    )

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError("Se esperaba ${T::class.java.simpleName}, llegó ${error::class.java.simpleName}", error)
        }
        throw AssertionError("Se esperaba ${T::class.java.simpleName}")
    }

    private fun createPlaintext(target: File, work: File, snapshot: BackupDatabaseSnapshot) {
        BackupContainer.create(
            target = target,
            workingDirectory = work,
            backupId = UUID.fromString(BACKUP_ID),
            createdAtEpochMillis = CREATED_AT,
            zoneId = ZoneId.of("America/Argentina/Buenos_Aires"),
            database = snapshot,
            preferences = emptyList(),
            photoMode = BackupPhotoMode.OMITTED,
            photoAssets = emptyList(),
            password = null,
        )
    }

    private fun readLogicalEntries(container: File): LinkedHashMap<String, ByteArray> {
        val work = File(temporary.root, "logical-entries-${UUID.randomUUID()}").also { it.mkdirs() }
        return try {
            BackupContainer.extract(container, work, null).use { extracted ->
                linkedMapOf<String, ByteArray>().apply {
                    put(
                        "manifest.bin",
                        ByteArrayOutputStream().also { output ->
                            BackupPayloadCodec.writeManifest(extracted.manifest, output)
                        }.toByteArray(),
                    )
                    put(
                        "database.bin",
                        ByteArrayOutputStream().also { output ->
                            BackupPayloadCodec.writeDatabase(extracted.payload.database, output)
                        }.toByteArray(),
                    )
                    put(
                        "photos.bin",
                        ByteArrayOutputStream().also { output ->
                            BackupPayloadCodec.writePhotos(extracted.payload.photos, output)
                        }.toByteArray(),
                    )
                    put(
                        "preferences.bin",
                        ByteArrayOutputStream().also { output ->
                            BackupPayloadCodec.writePreferences(extracted.payload.preferences, output)
                        }.toByteArray(),
                    )
                    extracted.payload.photos.sortedBy { it.storageKey }.forEach { photo ->
                        put("photos/${photo.storageKey}", extracted.photoFile(photo.storageKey).readBytes())
                    }
                }
            }
        } finally {
            work.deleteRecursively()
        }
    }

    private fun writePlainEntries(target: File, entries: Map<String, ByteArray>) {
        val payload = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                val orderedNames = listOf("manifest.bin") + entries.keys
                    .filterNot { it == "manifest.bin" }
                    .sorted()
                orderedNames.forEach { name ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(entries.getValue(name))
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        writePlainPayload(target, payload)
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

    private fun writePlainContainer(target: File, entries: List<String>) {
        val payload = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                entries.forEach { name ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(byteArrayOf(0))
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        writePlainPayload(target, payload)
    }

    private fun writePlainPayload(target: File, payload: ByteArray) {
        FileOutputStream(target).use { output ->
            DataOutputStream(output).use { data ->
                data.write(MAGIC)
                data.writeInt(MiGuardiaBackupContract.FORMAT_VERSION)
                data.writeInt(MiGuardiaBackupContract.MIN_READER_VERSION)
                data.writeInt(0)
                data.writeLong(CREATED_AT)
                data.writeInt(0)
                data.writeByte(MiGuardiaBackupContract.SALT_BYTES)
                data.write(ByteArray(MiGuardiaBackupContract.SALT_BYTES))
                data.writeByte(MiGuardiaBackupContract.NONCE_BYTES)
                data.write(ByteArray(MiGuardiaBackupContract.NONCE_BYTES))
                data.writeLong(payload.size.toLong())
                data.write(payload)
            }
        }
    }

    private companion object {
        const val CREATED_AT = 1_788_131_400_000L
        const val BACKUP_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        const val OBJECTIVE_ID = "11111111-1111-4111-8111-111111111111"
        const val OTHER_OBJECTIVE_ID = "17171717-1717-4717-8717-171717171717"
        const val SHIFT_ID = "12121212-1212-4212-8212-121212121212"
        const val NOTE_ID = "13131313-1313-4313-8313-131313131313"
        const val PLACE_ID = "14141414-1414-4414-8414-141414141414"
        const val WORK_TYPE_ID = "18181818-1818-4818-8818-181818181818"
        const val CONFIGURATION_REVISION_ID = "19191919-1919-4919-8919-191919191919"
        const val RECURRING_REVISION_ID = "20202020-2020-4020-8020-202020202020"
        const val EXTRA_INTERVAL_ID = "21212121-2121-4121-8121-212121212121"
        const val VACATION_ID = "15151515-1515-4515-8515-151515151515"
        const val MEDICAL_ID = "16161616-1616-4616-8616-161616161616"
        const val TIMELINE_ID = "22222222-2222-4222-8222-222222222222"
        const val OTHER_TIMELINE_ID = "33333333-3333-4333-8333-333333333333"
        const val PHOTO_ID = "44444444-4444-4444-8444-444444444444"
        const val PHOTO_STORAGE_KEY = "55555555-5555-4555-8555-555555555555.png"
        const val ONE_PIXEL_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2ZQAAAABJRU5ErkJggg=="
        val MAGIC = "MIGUARDIA-BACKUP".toByteArray(Charsets.US_ASCII)
    }
}
