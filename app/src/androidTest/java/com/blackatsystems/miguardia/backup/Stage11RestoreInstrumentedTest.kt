package com.blackatsystems.miguardia.backup

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.backup.*
import com.blackatsystems.miguardia.notifications.NotificationPreferencesStore
import com.blackatsystems.miguardia.profile.GuardProfileStore
import com.blackatsystems.miguardia.ui.summary.SummaryPreferencesStore
import com.blackatsystems.miguardia.upgrade.LegacyUpgradeFixtureContainer
import com.blackatsystems.miguardia.upgrade.UpgradeFixtureSnapshots as F
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual coordinator, real Room/DataStore/files; SQL oracle after close/reopen. */
@RunWith(AndroidJUnit4::class)
class Stage11RestoreInstrumentedTest {
    @Test fun v8PlainExportMergeAndRepeatedMergeKeepAllCurrentFields() = runBlocking {
        withCases("v8-plain") { source, target ->
            val fixture = fullFixture(source)
            source.seed(fixture)
            val prefs = source.prefs.capture(fixture.database).withValue("display.zoom_percent", "200")
            source.prefs.replace(prefs, fixture.database)
            source.export(null).use { artifact ->
                source.preserve("export-v8-plain.miguardia-backup", artifact.file)
                repeat(2) { iteration ->
                    target.open(artifact.file).use { session ->
                        if (iteration == 1) assertTrue(session.conflicts.isEmpty())
                        target.coordinator().apply(session, RestoreChoice.MERGE,
                            session.resolutions(BackupConflictResolution.USE_BACKUP), null)
                    }
                    target.verify(fixture, prefs, "merge-$iteration")
                }
            }
        }
    }

    @Test fun v8EncryptedReplaceRequiresExactConfirmationAndRemovesOnlyTargetCanary() = runBlocking {
        withCases("v8-encrypted") { source, target ->
            val desired = fullFixture(source)
            source.seed(desired)
            val password = UUID.randomUUID().toString()
            val previous = fullFixture(target).renameObjective("Canario ficticio anterior")
            target.seed(previous)
            val beforePrefs = target.prefs.capture(previous.database)
            val report = File(target.context.filesDir, "reports/artifacts/old-fictitious.txt").also {
                it.parentFile!!.mkdirs(); it.writeText("CANARIO SINTETICO")
            }
            val external = File(target.root, "outside-recoverable-canary.txt").also { it.writeText("FICTICIO") }
            source.export(password).use { artifact ->
                source.preserve("export-v8-encrypted.miguardia-backup", artifact.file)
                target.open(artifact.file, password).use { session ->
                    rejected { target.coordinator().apply(session, RestoreChoice.REPLACE_ALL, emptyList(), "reemplazar todo") }
                }
                target.verify(previous, beforePrefs, "wrong-confirmation")
                assertTrue(report.isFile)
                target.open(artifact.file, password).use { session ->
                    target.coordinator().apply(session, RestoreChoice.REPLACE_ALL, emptyList(), LocalBackupCoordinator.REPLACE_CONFIRMATION)
                }
                target.verify(desired, source.prefs.capture(desired.database), "replaced")
                assertFalse(report.exists()); assertEquals("FICTICIO", external.readText())
            }
        }
    }

    @Test fun genuineV5RestoresWithNullCoordinatesAndNoInventedNewTables() = runBlocking { legacy(5) }
    @Test fun genuineV6RestoresCoordinatesAndNoInventedNewTables() = runBlocking { legacy(6) }
    @Test fun genuineV7RestoresNonemptyNoteAttachmentWithoutInventedSituation() = runBlocking { legacy(7) }

    private suspend fun legacy(version: Int) = withCases("legacy-v$version") { source, target ->
        val photo = source.png(F.PHOTO_STORAGE_KEY, F.PHOTO_WIDTH, F.PHOTO_HEIGHT, Color.BLUE)
        var database = when (version) {
            5 -> F.legacyV5(DAY, photo.length())
            6 -> F.sourceV6(DAY, photo.length())
            else -> F.legacyV7(DAY, photo.length())
        }
        val files = linkedMapOf(F.PHOTO_STORAGE_KEY to photo)
        if (version == 7) {
            val attachment = source.png(NOTE_KEY, 2, 2, Color.GREEN)
            files[NOTE_KEY] = attachment
            database = database.rows("shift_note_attachments", listOf(attachmentRow(attachment)))
            MiGuardiaBackupSchemaV7.requireValid(database)
        }
        // Expected V8 shape is declared here, independently of the reader adapters.
        val expected = database.copy(roomVersion = 8, roomIdentityHash = MiGuardiaBackupContract.ROOM_IDENTITY_HASH,
            tables = MiGuardiaBackupSchemaV8.tables.map { spec ->
                val old = database.tables.find { it.name == spec.name }
                if (old == null) BackupTable(spec.name, spec.columns, spec.primaryKey, emptyList())
                else if (version == 5 && spec.name == "objectives") old.copy(columns = spec.columns,
                    records = old.records.map { it.copy(values = it.values + listOf(BackupValue.Null, BackupValue.Null)) })
                else old
            })
        val prefs = source.prefs.capture(source.store.backups.capture()).withValue("display.zoom_percent", "150")
        val container = source.legacyContainer(database, prefs, files)
        source.preserve("genuine-v$version.miguardia-backup", container)
        target.open(container).use { session ->
            assertEquals(version, session.preview.manifest.roomVersion)
            target.coordinator().apply(session, RestoreChoice.MERGE,
                session.resolutions(BackupConflictResolution.USE_BACKUP), null)
        }
        target.verify(Fixture(expected, files), prefs, "restored-v$version")
        val populated = Case(source.base, "legacy-v$version-preserve-current")
        try {
            val current = fullFixture(populated)
            populated.seed(current)
            val currentPrefs = populated.prefs.capture(current.database)
            populated.open(container).use { session ->
                populated.coordinator().apply(session, RestoreChoice.MERGE,
                    session.resolutions(BackupConflictResolution.KEEP_CURRENT), null)
            }
            populated.verify(current, currentPrefs, "legacy-keeps-current-new-fields")
        } catch (error: Throwable) {
            populated.preserveFailure(error)
            throw error
        } finally { populated.close() }
    }

    @Test fun previewCancellationAndMalformedInputsLeaveNonemptyAggregateIntact() = runBlocking {
        withCases("rejections") { source, target ->
            val incoming = fullFixture(source)
            source.seed(incoming)
            val previous = fullFixture(target).renameObjective("Datos previos ficticios")
            target.seed(previous)
            val prefs = target.prefs.capture(previous.database)
            source.export(null).use { plain ->
                // Real SAF content resolver path via controlled private FileProvider document.
                val document = target.document("preview")
                source.coordinator().copyToDocument(plain, target.uri(document))
                assertArrayEquals(plain.file.readBytes(), document.readBytes())
                val staged = target.coordinator().stageSource(target.uri(document))
                target.coordinator().openSource(staged, null).use { assertTrue(it.conflicts.isNotEmpty()) }
                assertFalse(staged.file.exists())
                target.verify(previous, prefs, "preview-cancel")
                val bytes = plain.file.readBytes()
                val future = bytes.copyOf().also { ByteBuffer.wrap(it).putInt(15, 999) }
                val truncated = bytes.copyOf(bytes.size / 2)
                val damaged = bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
                listOf("future" to future, "truncated" to truncated, "plain-integrity" to damaged).forEach { (name, data) ->
                    val bad = File(source.root, "$name.miguardia-backup").also { it.writeBytes(data) }
                    source.preserve(bad.name, bad)
                    rejected { target.open(bad).close() }
                    target.verify(previous, prefs, name)
                }
            }
            val password = UUID.randomUUID().toString()
            source.export(password).use { encrypted ->
                rejected { target.open(encrypted.file, UUID.randomUUID().toString()).close() }
                target.verify(previous, prefs, "wrong-password")
                val damaged = File(source.root, "gcm-damaged.miguardia-backup").also { file ->
                    file.writeBytes(encrypted.file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
                }
                source.preserve(damaged.name, damaged)
                rejected { target.open(damaged, password).close() }
                target.verify(previous, prefs, "gcm-integrity")
            }
        }
    }

    @Test fun mergeResolutionsKeepCurrentUseBackupAndRejectUnresolvedOrSameIdKeepBoth() = runBlocking {
        withCases("resolutions") { source, target ->
            val incoming = fullFixture(source)
            val previous = fullFixture(target).renameObjective("Objetivo actual ficticio")
            source.seed(incoming); target.seed(previous)
            val prefs = target.prefs.capture(previous.database)
            source.export(null).use { artifact ->
                target.open(artifact.file).use { s ->
                    assertTrue(s.conflicts.isNotEmpty())
                    rejected { target.coordinator().apply(s, RestoreChoice.MERGE, emptyList(), null) }
                    rejected { target.coordinator().apply(s, RestoreChoice.MERGE, s.resolutions(BackupConflictResolution.KEEP_BOTH), null) }
                }
                target.verify(previous, prefs, "unresolved-and-same-id")
                target.open(artifact.file).use { s -> target.coordinator().apply(s, RestoreChoice.MERGE, s.resolutions(BackupConflictResolution.KEEP_CURRENT), null) }
                target.verify(previous, prefs, "keep-current")
                target.open(artifact.file).use { s -> target.coordinator().apply(s, RestoreChoice.MERGE, s.resolutions(BackupConflictResolution.USE_BACKUP), null) }
                target.verify(incoming, source.prefs.capture(incoming.database), "use-backup")
            }
        }
    }

    @Test fun differentTimelineMergeIsRejectedAndReplaceRemainsAvailable() = runBlocking {
        withCases("timeline") { source, target ->
            val incoming = fullFixture(source)
            val previous = fullFixture(target).otherTimeline()
            source.seed(incoming); target.seed(previous)
            val prefs = target.prefs.capture(previous.database)
            source.export(null).use { artifact ->
                target.open(artifact.file).use { s ->
                    assertFalse(s.preview.timelineCompatible)
                    rejected { target.coordinator().apply(s, RestoreChoice.MERGE, s.resolutions(BackupConflictResolution.KEEP_CURRENT), null) }
                }
                target.verify(previous, prefs, "timeline-rejected")
                target.open(artifact.file).use { s -> target.coordinator().apply(s, RestoreChoice.REPLACE_ALL, emptyList(), LocalBackupCoordinator.REPLACE_CONFIRMATION) }
                target.verify(incoming, source.prefs.capture(incoming.database), "timeline-replaced")
            }
        }
    }

    @Test fun distinctOverlappingShiftCanKeepBothWithoutInventingIdentities() = runBlocking {
        withCases("keep-both") { source, target ->
            val previous = fullFixture(target)
            val incomingBase = fullFixture(source)
            val newId = "70000000-0000-4000-8000-000000000001"
            val shift = incomingBase.database.table("shifts").records.first().let { row ->
                row.copy(values = listOf(t(newId)) + row.values.drop(1))
            }
            val snapshot = incomingBase.database.table("shift_work_snapshots").records.first().let { row ->
                row.copy(values = listOf(t(newId)) + row.values.drop(1))
            }
            val incoming = incomingBase.copy(database = incomingBase.database
                .rows("shifts", incomingBase.database.table("shifts").records + shift)
                .rows("shift_work_snapshots", incomingBase.database.table("shift_work_snapshots").records + snapshot))
            source.seed(incoming); target.seed(previous)
            source.export(null).use { artifact ->
                target.open(artifact.file).use { s ->
                    assertTrue(s.conflicts.isNotEmpty())
                    assertTrue(s.conflicts.all { it.keepBothAllowed })
                    target.coordinator().apply(s, RestoreChoice.MERGE, s.resolutions(BackupConflictResolution.KEEP_BOTH), null)
                }
                target.verify(incoming, source.prefs.capture(incoming.database), "distinct-keep-both")
            }
        }
    }

    @Test fun journalFaultsRecoverNonemptyAttachmentsSituationsPreferencesAndPhotoHashes() = runBlocking {
        // Seven checkpoints x two failure modes, in addition to reused historical recovery tests.
        val failures = mutableListOf<Throwable>()
        for (processSimulation in listOf(false, true)) for (checkpoint in RestoreCheckpoint.entries) {
            try { withCases("journal-${checkpoint.name}-$processSimulation") { source, target ->
                val incoming = fullFixture(source)
                val previous = fullFixture(target, Color.RED).renameObjective("Anterior ficticio")
                source.seed(incoming); target.seed(previous)
                val oldPrefs = target.prefs.capture(previous.database)
                val newPrefs = source.prefs.capture(incoming.database).withValue("display.zoom_percent", "150")
                source.prefs.replace(newPrefs, incoming.database)
                var injected = false
                source.export(null).use { artifact ->
                    target.open(artifact.file).use { session ->
                        val error = rejected {
                            target.coordinator(RestoreFaultInjector { observed ->
                                if (observed == checkpoint && !injected) {
                                    injected = true
                                    if (processSimulation) throw SimulatedProcessDeath() else throw IOException("Fallo sintético")
                                }
                            }).apply(session, RestoreChoice.REPLACE_ALL, emptyList(), LocalBackupCoordinator.REPLACE_CONFIRMATION)
                        }
                        if (processSimulation) assertTrue(error is SimulatedProcessDeath)
                    }
                }
                assertTrue(injected)
                target.reopen()
                val recovered = target.coordinator().recoverAtStartup()
                assertEquals(processSimulation || checkpoint == RestoreCheckpoint.AFTER_COMMITTED, recovered)
                val newWins = if (processSimulation) checkpoint in setOf(RestoreCheckpoint.AFTER_VERIFIED, RestoreCheckpoint.AFTER_COMMITTED)
                    else checkpoint == RestoreCheckpoint.AFTER_COMMITTED
                target.verify(if (newWins) incoming else previous, if (newWins) newPrefs else oldPrefs, "recovered")
                assertFalse(File(target.context.noBackupFilesDir, "miguardia_backup_restore").exists())
                assertFalse(target.coordinator().recoverAtStartup())
            } } catch (error: Throwable) { failures += error }
        }
        if (failures.isNotEmpty()) throw AssertionError("Fallaron ${failures.size} checkpoints; estados adversos preservados").also {
            failures.forEach(it::addSuppressed)
        }
    }

    @Test fun replaceAllSameImageMetadataStillMustRestoreAuthenticatedIncomingBytes() = runBlocking {
        withCases("minimal-image-bytes") { source, target ->
            val incoming = fullFixture(source)
            val previous = fullFixture(target, Color.RED).renameObjective("Canario ficticio")
            source.seed(incoming); target.seed(previous)
            assertEquals(incoming.database.table("schedule_photos"), previous.database.table("schedule_photos"))
            assertEquals(incoming.database.table("shift_note_attachments"), previous.database.table("shift_note_attachments"))
            assertNotEquals(Stage11RestoreRunner.hash(incoming.files.getValue(NOTE_KEY)), Stage11RestoreRunner.hash(previous.files.getValue(NOTE_KEY)))
            source.export(null).use { artifact ->
                source.preserve("minimal-source.miguardia-backup", artifact.file)
                target.open(artifact.file).use { session ->
                    target.coordinator().apply(session, RestoreChoice.REPLACE_ALL, emptyList(), LocalBackupCoordinator.REPLACE_CONFIRMATION)
                }
                target.verify(incoming, source.prefs.capture(incoming.database), "replace-without-any-fault")
            }
        }
    }

    @Test fun failedEvidenceCopyKeepsOriginalsUntilARealCopySucceeds() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val case = Case(base, "preservation-guard")
        val fixture = fullFixture(case)
        case.seed(fixture)
        val original = IOException("Error ficticio original")
        case.preserveFailure(original) { throw IOException("Copia ficticia rechazada") }
        assertTrue(case.id in Stage11Preservation.pending)
        case.close()
        assertTrue(case.root.isDirectory)
        assertTrue(case.context.getDatabasePath(case.databaseName).isFile)
        case.preserveFailure(original)
        assertFalse(case.id in Stage11Preservation.pending)
        case.close()
        assertFalse(case.root.exists())
    }

    @Test fun mergeKeepsCurrentBytesAndStrictIncomingRejectsMissingOrWrongHash() = runBlocking {
        withCases("merge-image-origin") { source, target ->
            val incoming = fullFixture(source)
            val previous = fullFixture(target, Color.RED).renameObjective("Actual ficticio")
            source.seed(incoming); target.seed(previous)
            val beforePrefs = target.prefs.capture(previous.database)
            source.export(null).use { artifact ->
                target.open(artifact.file).use { session ->
                    target.coordinator().apply(session, RestoreChoice.MERGE,
                        session.resolutions(BackupConflictResolution.KEEP_CURRENT), null)
                }
            }
            target.verify(previous, beforePrefs, "merge-retains-current-bytes")
            val incomingHashes = incoming.files.mapValues { Stage11RestoreRunner.hash(it.value) }
            val strictDirectory = File(target.root, "strict-incoming").also { it.mkdirs() }
            val files = BackupPhotoFiles(target.context)
            rejected { files.materializeDesired(incoming.database, strictDirectory, File(target.root, "missing-result"),
                sourcePolicy = BackupPhotoSourcePolicy.INCOMING_ONLY, expectedSha256ByStorageKey = incomingHashes) }
            previous.files.forEach { (key, file) -> file.copyTo(File(strictDirectory, key)) }
            rejected { files.materializeDesired(incoming.database, strictDirectory, File(target.root, "hash-result"),
                sourcePolicy = BackupPhotoSourcePolicy.INCOMING_ONLY, expectedSha256ByStorageKey = incomingHashes) }
            target.verify(previous, beforePrefs, "strict-rejections-keep-live")
        }
    }

    @Test fun omittedPhotosKeepExistingMergePolicyAndReplaceRemovesOnlyOmittedImages() = runBlocking {
        withCases("omitted-photos") { source, target ->
            val incoming = fullFixture(source)
            val previous = fullFixture(target)
            source.seed(incoming); target.seed(previous)
            val oldPrefs = target.prefs.capture(previous.database)
            source.coordinator().prepareBackup(BackupCreateOptions(includePhotos = false)).use { artifact ->
                source.preserve("omitted-photos.miguardia-backup", artifact.file)
                target.open(artifact.file).use { session ->
                    assertEquals(BackupPhotoMode.OMITTED, session.extracted.payload.photoMode)
                    assertTrue(session.extracted.payload.photos.isEmpty())
                    target.coordinator().apply(session, RestoreChoice.MERGE,
                        session.resolutions(BackupConflictResolution.KEEP_CURRENT), null)
                }
                target.verify(previous, oldPrefs, "omitted-merge-keeps-current")
                val expected = incoming.copy(database = incoming.database.copy(tables = incoming.database.tables.map { table ->
                    when (table.name) {
                        "schedule_photos", "shift_note_attachments" -> table.copy(records = emptyList())
                        "shift_situations" -> table.copy(records = table.records.map { row -> row.copy(values = row.values.mapIndexed { index, value ->
                            if (table.columns[index] in setOf("evidenceStorageKey", "evidenceMimeType", "evidenceByteSize", "evidencePixelWidth", "evidencePixelHeight", "evidenceSha256")) BackupValue.Null else value
                        }) })
                        else -> table
                    }
                }), files = emptyMap())
                target.open(artifact.file).use { session ->
                    target.coordinator().apply(session, RestoreChoice.REPLACE_ALL, emptyList(), LocalBackupCoordinator.REPLACE_CONFIRMATION)
                }
                target.verify(expected, source.prefs.capture(incoming.database), "omitted-replace-removes-images")
            }
        }
    }

    @Test fun controlledDocumentPublicationHandlesSuccessFailureAndInterruptedHeader() = runBlocking {
        withCases("publication") { source, target ->
            source.seed(fullFixture(source))
            for (password in listOf(null, UUID.randomUUID().toString())) source.export(password).use { artifact ->
                val complete = target.document("complete")
                source.coordinator().copyToDocument(artifact, target.uri(complete))
                assertArrayEquals(artifact.file.readBytes(), complete.readBytes())
                target.open(complete, password).use { assertEquals(29, it.extracted.payload.database.tables.size) }
                val ordinary = target.document("ordinary")
                rejected { source.coordinator(documentFault = { throw IOException("Fallo ficticio") }).copyToDocument(artifact, target.uri(ordinary)) }
                assertTrue(!ordinary.exists() || ordinary.length() == 0L)
                val interrupted = target.document("interrupted")
                val failure = rejected { source.coordinator(documentFault = { throw SimulatedProcessDeath() }).copyToDocument(artifact, target.uri(interrupted)) }
                assertTrue(failure is SimulatedProcessDeath)
                assertTrue(interrupted.length() > BackupContainer.HEADER_BYTES)
                assertEquals(List(BackupContainer.HEADER_BYTES) { 0.toByte() }, interrupted.readBytes().take(BackupContainer.HEADER_BYTES))
                rejected { BackupContainer.readHeader(interrupted) }
                assertTrue(source.coordinator().discardIncompleteDocument(target.uri(interrupted)))
                assertTrue(!interrupted.exists() || interrupted.length() == 0L)
            }
        }
    }

    private suspend fun withCases(label: String, block: suspend (Case, Case) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        check(base.javaClass == Application::class.java && base.applicationContext === base)
        check(base.filesDir.path.contains("stage11-"))
        val source = Case(base, "$label-source")
        val target = Case(base, "$label-target")
        try { block(source, target) }
        catch (error: Throwable) {
            source.preserveFailure(error); target.preserveFailure(error)
            throw error
        } finally { try { source.close() } finally { target.close() } }
    }

    private inner class Case(val base: Context, val label: String) {
        val id = UUID.randomUUID().toString()
        val root = File(base.cacheDir, "stage11-case-$id").also { check(it.mkdirs()) }
        val databaseName = "stage11-$id.db"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(root, "files").also { it.mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no_backup").also { it.mkdirs() }
            override fun getCacheDir() = File(root, "cache").also { it.mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("case-$id-$name", mode)
        }
        var job = SupervisorJob()
        var scope = CoroutineScope(job + Dispatchers.IO)
        var store = LocalDataStore.create(context, databaseName)
        var prefs = preferences()
        var revision = 0
        private val evidence = File(base.filesDir.parentFile, "evidence/$label-$id").also { it.mkdirs() }

        private fun preferences() = PortablePreferencesGateway(context,
            GuardProfileStore(File(root, "profile.preferences_pb"), scope),
            SummaryPreferencesStore(File(root, "summary.preferences_pb"), scope),
            NotificationPreferencesStore(File(root, "notifications.preferences_pb"), scope),
            WeatherPreferencesStore(File(root, "weather.preferences_pb"), scope))
        fun coordinator(fault: RestoreFaultInjector = RestoreFaultInjector.NONE, documentFault: (android.net.Uri) -> Unit = {}) =
            LocalBackupCoordinator(context, store, prefs, clock = CLOCK, zoneId = F.ZONE,
                faultInjector = fault, afterDocumentPayloadStaged = { documentFault(it) })
        suspend fun seed(fixture: Fixture) {
            store.backups.replace(fixture.database)
            fixture.files.forEach { (key, source) ->
                val destination = File(context.filesDir, "schedule_photos/$key").also { it.parentFile!!.mkdirs() }
                if (source.canonicalFile != destination.canonicalFile) source.copyTo(destination, overwrite = false)
            }
            val p = prefs.capture(fixture.database).withValue("profile.display_name", "Persona ficticia Stage11")
            // Discovering the key is unnecessary: profile key is checked by schema below.
            prefs.replace(p, fixture.database)
        }
        suspend fun export(password: String?) = coordinator().prepareBackup(BackupCreateOptions(true, password))
        suspend fun open(file: File, password: String? = null): BackupImportSession {
            val selected = File(root, "selected-${UUID.randomUUID()}.miguardia-backup")
            file.copyTo(selected)
            val source = try { StagedBackupSource(selected, BackupContainer.readHeader(selected)) }
                catch (error: Throwable) { selected.delete(); throw error }
            return try { coordinator().openSource(source, password) } catch (error: Throwable) { source.close(); throw error }
        }
        fun png(key: String, width: Int, height: Int, color: Int): File = File(root, key).also { file ->
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try { bitmap.eraseColor(color); file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
        }
        fun document(label: String) = File(base.filesDir, "reports/artifacts/stage11-$label-${UUID.randomUUID()}.miguardia-backup").also { it.parentFile!!.mkdirs(); check(it.createNewFile()) }
        fun uri(file: File) = FileProvider.getUriForFile(base, "${base.packageName}.fileprovider", file)
        fun preserve(name: String, file: File) { file.copyTo(File(evidence, name), overwrite = false) }
        suspend fun preserveFailure(original: Throwable, copyFault: () -> Unit = {}) {
            Stage11Preservation.pending.add(id)
            val pending = File(base.filesDir.parentFile, "preservation-pending/$id.txt")
            try {
                pending.parentFile!!.mkdirs()
                java.io.FileOutputStream(pending).use { it.write(id.toByteArray()); it.fd.sync() }
                store.close(); job.cancelAndJoin()
                copyFault()
                val failure = File(evidence, "failure-${UUID.randomUUID()}").also { check(it.mkdirs()) }
                check(root.copyRecursively(File(failure, "private-case"), overwrite = false))
                val database = context.getDatabasePath(databaseName)
                listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
                    File(database.path + suffix).takeIf { it.isFile }?.copyTo(File(failure, databaseName + suffix))
                }
                File(failure, "error-type.txt").writeText(original.javaClass.name)
                check(pending.delete())
                Stage11Preservation.pending.remove(id)
            } catch (preservation: Throwable) { original.addSuppressed(preservation) }
        }
        suspend fun reopen() {
            store.close(); job.cancelAndJoin()
            job = SupervisorJob(); scope = CoroutineScope(job + Dispatchers.IO)
            store = LocalDataStore.create(context, databaseName); prefs = preferences()
        }
        suspend fun verify(expected: Fixture, expectedPrefs: List<BackupPreference>, phase: String) {
            store.close(); job.cancelAndJoin()
            val db = context.getDatabasePath(databaseName)
            check(db.canonicalPath.contains("stage11-") && db.canonicalPath.endsWith(databaseName))
            SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { sql ->
                sql.rawQuery("PRAGMA user_version", null).use { assertTrue(it.moveToFirst()); assertEquals(8, it.getInt(0)) }
                sql.rawQuery("PRAGMA integrity_check", null).use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
                sql.rawQuery("PRAGMA foreign_key_check", null).use { assertEquals(0, it.count) }
                expected.database.tables.forEach { table ->
                    val order = table.primaryKey.joinToString(",") { "`$it`" }
                    val columns = table.columns.joinToString(",") { "`$it`" }
                    sql.rawQuery("SELECT $columns FROM `${table.name}` ORDER BY $order", null).use { cursor ->
                        val actual = mutableListOf<BackupRecord>()
                        while (cursor.moveToNext()) actual += BackupRecord(table.columns.indices.map { column ->
                            when (cursor.getType(column)) {
                                Cursor.FIELD_TYPE_NULL -> BackupValue.Null
                                Cursor.FIELD_TYPE_INTEGER -> BackupValue.Integer(cursor.getLong(column))
                                Cursor.FIELD_TYPE_FLOAT -> BackupValue.Real(cursor.getDouble(column))
                                Cursor.FIELD_TYPE_STRING -> BackupValue.Text(cursor.getString(column))
                                else -> BackupValue.Binary(cursor.getBlob(column))
                            }
                        })
                        assertEquals("$phase ${table.name}", table.records, actual)
                    }
                }
            }
            val prefix = "${revision++}-$phase"
            preserve("$prefix.db", db)
            File(evidence, "$prefix-expected.bin").outputStream().use { BackupPayloadCodec.writeDatabase(expected.database, it) }
            job = SupervisorJob(); scope = CoroutineScope(job + Dispatchers.IO)
            store = LocalDataStore.create(context, databaseName); prefs = preferences()
            assertEquals(expectedPrefs, prefs.capture(expected.database))
            val photos = File(context.filesDir, "schedule_photos").listFiles().orEmpty().associate { it.name to Stage11RestoreRunner.hash(it) }
            assertEquals(expected.files.mapValues { Stage11RestoreRunner.hash(it.value) }, photos)
            val actualPrefs = prefs.capture(expected.database)
            File(evidence, "$prefix-preferences.bin").outputStream().use { BackupPayloadCodec.writePreferences(actualPrefs, it) }
            File(evidence, "$prefix.json").writeText(JSONObject().put("phase", phase).put("room", 8)
                .put("table_counts", JSONObject(expected.database.tables.associate { it.name to it.records.size }))
                .put("image_sha256", JSONObject(photos)).put("portable_count", actualPrefs.size)
                .put("sql_oracle_after_close", "PASS").put("datastore_reopened", true).toString(2))
        }
        fun legacyContainer(database: BackupDatabaseSnapshot, prefs: List<BackupPreference>, files: Map<String, File>): File {
            fun encoded(block: (java.io.OutputStream) -> Unit) = ByteArrayOutputStream().also(block).toByteArray()
            val assets = files.map { (key, file) -> BackupPhotoMetadata(
                if (key == F.PHOTO_STORAGE_KEY) F.PHOTO_ID else ATTACHMENT_ID, key, "image/png", file.length(),
                if (key == F.PHOTO_STORAGE_KEY) F.PHOTO_WIDTH else 2,
                if (key == F.PHOTO_STORAGE_KEY) F.PHOTO_HEIGHT else 2, Stage11RestoreRunner.hash(file)) }
            val entries = linkedMapOf("database.bin" to LegacyUpgradeFixtureContainer.databaseBytes(database),
                "preferences.bin" to encoded { BackupPayloadCodec.writePreferences(prefs, it) },
                "photos.bin" to encoded { BackupPayloadCodec.writePhotos(assets, it) })
            files.forEach { (key, file) -> entries["photos/$key"] = file.readBytes() }
            val manifest = BackupManifest(UUID.randomUUID().toString(), NOW, F.ZONE_ID, database.roomVersion,
                database.timelineId, BackupPhotoMode.INCLUDED, database.tables.associate { it.name to it.records.size },
                entries.map { (name, bytes) -> BackupEntryManifest(name, bytes.size.toLong(), digest(bytes)) }.sortedBy { it.name })
            entries["manifest.bin"] = encoded { BackupPayloadCodec.writeManifest(manifest, it) }
            return File(root, "legacy-v${database.roomVersion}.miguardia-backup").also { LegacyUpgradeFixtureContainer.write(it, entries, NOW) }
        }
        suspend fun close() {
            store.close(); job.cancelAndJoin()
            if (id in Stage11Preservation.pending) return
            context.deleteDatabase(databaseName); check(root.deleteRecursively())
        }
    }

    private data class Fixture(val database: BackupDatabaseSnapshot, val files: Map<String, File>) {
        fun renameObjective(name: String) = copy(database = database.copy(tables = database.tables.map { table ->
            if (table.name != "objectives") table else table.copy(records = table.records.map { row ->
                row.copy(values = row.values.mapIndexed { index, value -> if (table.columns[index] == "fullName") BackupValue.Text(name) else value })
            })
        }))
        fun otherTimeline(): Fixture {
            val other = "60000000-0000-4000-8000-000000000001"
            return copy(database = database.copy(timelineId = other, tables = database.tables.map { table ->
                table.copy(records = table.records.map { row -> row.copy(values = row.values.map { value ->
                    if (value == BackupValue.Text(F.TIMELINE_ID)) BackupValue.Text(other) else value
                }) })
            }))
        }
    }
    private fun fullFixture(case: Case, color: Int = Color.BLUE): Fixture {
        val photo = case.png(F.PHOTO_STORAGE_KEY, F.PHOTO_WIDTH, F.PHOTO_HEIGHT, color)
        val attachment = case.png(NOTE_KEY, 2, 2, color)
        val cancellation = case.png(CANCEL_KEY, 3, 3, color)
        var db = F.targetV8(DAY, photo.length()).rows("shift_note_attachments", listOf(attachmentRow(attachment)))
        val shifts = db.table("shifts")
        db = db.rows("shifts", shifts.records.map { row ->
            if (row.values.first() != BackupValue.Text(F.AUTOMATIC_SHIFT_ID)) row else row.copy(values = row.values.mapIndexed { index, value ->
                if (shifts.columns[index] == "status") BackupValue.Text("CANCELLED") else value
            })
        })
        db = db.rows("shift_situations", listOf(BackupRecord(listOf(
            t(F.AUTOMATIC_SHIFT_ID), t("CANCELLED"), t("Motivo ficticio"), t("Nota privada ficticia"), t("EMPLOYER"), n(NOW),
            t(CANCEL_KEY), t("image/png"), n(cancellation.length()), n(3), n(3), t(Stage11RestoreRunner.hash(cancellation)), n(NOW), n(NOW),
        ))))
        MiGuardiaBackupSchemaV8.requireValid(db)
        return Fixture(db, linkedMapOf(F.PHOTO_STORAGE_KEY to photo, NOTE_KEY to attachment, CANCEL_KEY to cancellation))
    }
    private fun attachmentRow(file: File) = BackupRecord(listOf(t(ATTACHMENT_ID), t(F.NOTE_ID), t(NOTE_KEY), t("image/png"), n(file.length()), n(2), n(2), n(0), n(NOW), n(NOW)))
    private fun BackupDatabaseSnapshot.rows(name: String, records: List<BackupRecord>) = copy(tables = tables.map { if (it.name == name) it.copy(records = records) else it })
    private fun BackupImportSession.resolutions(choice: BackupConflictResolution) = conflicts.map { ResolvedBackupConflict(it.id, choice) }
    private fun List<BackupPreference>.withValue(key: String, value: String) = map { if (it.key == key) it.copy(values = listOf(value)) else it }
    private suspend fun rejected(block: suspend () -> Unit): Throwable {
        try { block() } catch (error: Throwable) { if (error is AssertionError) throw error; return error }
        throw AssertionError("La entrada o la operación debía rechazarse")
    }
    private class SimulatedProcessDeath : Error()
    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 23)
        const val NOW = 1792728000000L
        val CLOCK: Clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneId.of(F.ZONE_ID))
        const val ATTACHMENT_ID = "50000000-0000-4000-8000-000000000001"
        const val NOTE_KEY = "note_50000000-0000-4000-8000-000000000001.png"
        const val CANCEL_KEY = "cancel_50000000-0000-4000-8000-000000000002.png"
        fun t(value: String) = BackupValue.Text(value)
        fun n(value: Long) = BackupValue.Integer(value)
        fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
