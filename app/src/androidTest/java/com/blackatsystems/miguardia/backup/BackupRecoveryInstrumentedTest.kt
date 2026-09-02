package com.blackatsystems.miguardia.backup

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.backup.BackupComparator
import com.blackatsystems.miguardia.core.domain.backup.BackupConflictResolution
import com.blackatsystems.miguardia.core.domain.backup.BackupContainer
import com.blackatsystems.miguardia.core.domain.backup.BackupDatabaseSnapshot
import com.blackatsystems.miguardia.core.domain.backup.BackupPhotoAsset
import com.blackatsystems.miguardia.core.domain.backup.BackupPhotoMetadata
import com.blackatsystems.miguardia.core.domain.backup.BackupPhotoMode
import com.blackatsystems.miguardia.core.domain.backup.BackupPreference
import com.blackatsystems.miguardia.core.domain.backup.BackupRecord
import com.blackatsystems.miguardia.core.domain.backup.BackupTable
import com.blackatsystems.miguardia.core.domain.backup.BackupValue
import com.blackatsystems.miguardia.core.domain.backup.InvalidBackupException
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupContract
import com.blackatsystems.miguardia.core.domain.backup.ResolvedBackupConflict
import com.blackatsystems.miguardia.notifications.NotificationPreferencesStore
import com.blackatsystems.miguardia.profile.GuardProfileStore
import com.blackatsystems.miguardia.security.AccessLockConfiguration
import com.blackatsystems.miguardia.security.AccessLockCoordinator
import com.blackatsystems.miguardia.security.AccessLockPreferencesStore
import com.blackatsystems.miguardia.security.AccessLockStoreRead
import com.blackatsystems.miguardia.security.AccessLockTimeout
import com.blackatsystems.miguardia.ui.summary.SummaryPreferencesStore
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRecoveryInstrumentedTest {
    @Test
    fun preparedBackupUsesTheExactExtensionAndADataFreeSuggestedName() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "backup-name-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val context = IsolatedBackupContext(base, root, "name-${UUID.randomUUID()}")
        val databaseName = "backup-name-${UUID.randomUUID()}.db"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = LocalDataStore.create(context, databaseName)
        try {
            val coordinator = LocalBackupCoordinator(
                context = context,
                localDataStore = store,
                preferences = preferencesGateway(context, root, scope),
                clock = CLOCK,
                zoneId = ZONE,
            )

            coordinator.prepareBackup(BackupCreateOptions(includePhotos = false, password = null)).use { artifact ->
                assertEquals("MiGuardia_copia_2026-08-30_2010.miguardia-backup", artifact.suggestedName)
                assertTrue(artifact.suggestedName.endsWith(MiGuardiaBackupContract.FILE_EXTENSION))
                assertFalse(artifact.suggestedName.contains("Joaquin", ignoreCase = true))
            }
        } finally {
            store.close()
            scope.cancel()
            context.clearIsolatedPreferences()
            base.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    @Test
    fun concurrentPreferenceMutationRetriesThenAbortsWithoutACompletedArtifact() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "backup-capture-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val context = IsolatedBackupContext(base, root, "capture-${UUID.randomUUID()}")
        val databaseName = "backup-capture-${UUID.randomUUID()}.db"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = LocalDataStore.create(context, databaseName)
        try {
            val profile = GuardProfileStore(File(root, "profile.preferences_pb"), scope)
            val preferences = PortablePreferencesGateway(
                context = context,
                guardProfile = profile,
                summaryStore = SummaryPreferencesStore(File(root, "summary.preferences_pb"), scope),
                notificationStore = NotificationPreferencesStore(File(root, "notifications.preferences_pb"), scope),
                weatherStore = WeatherPreferencesStore(File(root, "weather.preferences_pb"), scope),
            )
            var attempts = 0
            val coordinator = LocalBackupCoordinator(
                context = context,
                localDataStore = store,
                preferences = preferences,
                clock = CLOCK,
                zoneId = ZONE,
                afterPrivateBackupValidated = { attempt ->
                    attempts = attempt
                    profile.save("Mutación ficticia $attempt")
                },
            )

            val error = assertSuspendFails {
                coordinator.prepareBackup(BackupCreateOptions(includePhotos = false, password = null))
            }

            assertTrue(error.message.orEmpty().contains("cambiaron"))
            assertEquals(2, attempts)
            assertTrue(
                File(context.filesDir, "backups/staging").listFiles().orEmpty()
                    .none { it.name.startsWith("prepared-") },
            )
        } finally {
            store.close()
            scope.cancel()
            context.clearIsolatedPreferences()
            base.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    @Test
    fun mergeAppliesRoomPreferencesAndPhotosAndIsIdempotentWithAFreshSession() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "backup-merge-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val context = IsolatedBackupContext(base, root, "merge-${UUID.randomUUID()}")
        val databaseName = "backup-merge-${UUID.randomUUID()}.db"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = LocalDataStore.create(context, databaseName)
        var firstSession: BackupImportSession? = null
        var secondSession: BackupImportSession? = null
        try {
            val preferences = preferencesGateway(context, root, scope)
            val accessLock = AccessLockPreferencesStore(
                context.preferencesDataStoreFile(AccessLockPreferencesStore.DEFAULT_FILE_NAME),
                scope,
            )
            val localAccessLock = AccessLockConfiguration(true, AccessLockTimeout.FIFTEEN_MINUTES)
            accessLock.replace(localAccessLock)
            store.backups.replace(store.backups.capture().withObjectiveAndPhoto("Actual"))
            val currentDatabase = store.backups.capture()
            val currentPhotoBytes = Base64.getDecoder().decode(CURRENT_PNG)
            val incomingPhotoBytes = Base64.getDecoder().decode(INCOMING_PNG)
            check(currentPhotoBytes.size == incomingPhotoBytes.size)
            val livePhoto = File(context.filesDir, "schedule_photos/$PHOTO_STORAGE_KEY").also {
                it.parentFile?.mkdirs()
                it.writeBytes(currentPhotoBytes)
            }
            val currentPreferences = preferences.capture(currentDatabase)
            val incomingDatabase = currentDatabase.withObjectiveAndPhoto("Copia")
            val incomingPreferences = currentPreferences.withZoom(150)
            val template = createBackupTemplate(
                root = root,
                database = incomingDatabase,
                preferences = incomingPreferences,
                incomingPhotoBytes = incomingPhotoBytes,
            )
            val coordinator = LocalBackupCoordinator(
                context = context,
                localDataStore = store,
                preferences = preferences,
                clock = CLOCK,
                zoneId = ZONE,
            )

            val openedFirstSession = coordinator.openFreshSession(template, root)
            firstSession = openedFirstSession
            assertTrue(openedFirstSession.conflicts.isNotEmpty())
            coordinator.apply(
                session = openedFirstSession,
                choice = RestoreChoice.MERGE,
                resolutions = openedFirstSession.conflicts.map { conflict ->
                    ResolvedBackupConflict(conflict.id, BackupConflictResolution.USE_BACKUP)
                },
                replaceConfirmation = null,
            )
            assertEquals(incomingDatabase, store.backups.capture())
            assertEquals(incomingPreferences, preferences.capture(incomingDatabase))
            assertTrue(livePhoto.readBytes().contentEquals(incomingPhotoBytes))
            assertFalse(File(context.noBackupFilesDir, "miguardia_backup_restore").exists())
            assertEquals(AccessLockStoreRead.Ready(localAccessLock), accessLock.read())
            assertNewAccessLockSessionIsClosed(accessLock, scope)

            openedFirstSession.close()
            firstSession = null
            val openedSecondSession = coordinator.openFreshSession(template, root)
            secondSession = openedSecondSession
            assertTrue(openedSecondSession.conflicts.isEmpty())
            coordinator.apply(
                session = openedSecondSession,
                choice = RestoreChoice.MERGE,
                resolutions = emptyList(),
                replaceConfirmation = null,
            )

            assertEquals(incomingDatabase, store.backups.capture())
            assertEquals(incomingPreferences, preferences.capture(incomingDatabase))
            assertTrue(livePhoto.readBytes().contentEquals(incomingPhotoBytes))
            assertFalse(File(context.noBackupFilesDir, "miguardia_backup_restore").exists())
            assertEquals(AccessLockStoreRead.Ready(localAccessLock), accessLock.read())
            assertNewAccessLockSessionIsClosed(accessLock, scope)
        } finally {
            firstSession?.close()
            secondSession?.close()
            store.close()
            scope.cancel()
            context.clearIsolatedPreferences()
            base.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    @Test
    fun replaceAllWithWrongConfirmationLeavesRoomPreferencesAndPhotosUntouched() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "backup-replace-confirmation-${UUID.randomUUID()}")
            .also { check(it.mkdirs()) }
        val context = IsolatedBackupContext(base, root, "replace-confirmation-${UUID.randomUUID()}")
        val databaseName = "backup-replace-confirmation-${UUID.randomUUID()}.db"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = LocalDataStore.create(context, databaseName)
        var session: BackupImportSession? = null
        try {
            val preferences = preferencesGateway(context, root, scope)
            val accessLock = AccessLockPreferencesStore(
                context.preferencesDataStoreFile(AccessLockPreferencesStore.DEFAULT_FILE_NAME),
                scope,
            )
            val localAccessLock = AccessLockConfiguration(true, AccessLockTimeout.ONE_MINUTE)
            accessLock.replace(localAccessLock)
            store.backups.replace(store.backups.capture().withObjectiveAndPhoto("Actual"))
            val currentDatabase = store.backups.capture()
            val currentPreferences = preferences.capture(currentDatabase)
            val currentPhotoBytes = Base64.getDecoder().decode(CURRENT_PNG)
            val incomingPhotoBytes = Base64.getDecoder().decode(INCOMING_PNG)
            check(currentPhotoBytes.size == incomingPhotoBytes.size)
            val livePhoto = File(context.filesDir, "schedule_photos/$PHOTO_STORAGE_KEY").also {
                it.parentFile?.mkdirs()
                it.writeBytes(currentPhotoBytes)
            }
            val incomingDatabase = currentDatabase.withObjectiveAndPhoto("Copia")
            val incomingPreferences = currentPreferences.withZoom(150)
            val template = createBackupTemplate(
                root = root,
                database = incomingDatabase,
                preferences = incomingPreferences,
                incomingPhotoBytes = incomingPhotoBytes,
            )
            var pauseCalls = 0
            var resumeCalls = 0
            val coordinator = LocalBackupCoordinator(
                context = context,
                localDataStore = store,
                preferences = preferences,
                pauseRuntimes = { pauseCalls++ },
                resumeRuntimes = { resumeCalls++ },
                clock = CLOCK,
                zoneId = ZONE,
            )
            val openedSession = coordinator.openFreshSession(template, root)
            session = openedSession

            val failure = assertSuspendFails {
                coordinator.apply(
                    session = openedSession,
                    choice = RestoreChoice.REPLACE_ALL,
                    resolutions = emptyList(),
                    replaceConfirmation = "Reemplazar",
                )
            }

            assertTrue(failure is InvalidBackupException)
            assertTrue(failure.message.orEmpty().contains("confirmación"))
            assertEquals(currentDatabase, store.backups.capture())
            assertEquals(currentPreferences, preferences.capture(currentDatabase))
            assertTrue(livePhoto.readBytes().contentEquals(currentPhotoBytes))
            assertEquals(0, pauseCalls)
            assertEquals(0, resumeCalls)
            assertFalse(File(context.noBackupFilesDir, "miguardia_backup_restore").exists())
            assertEquals(AccessLockStoreRead.Ready(localAccessLock), accessLock.read())
            assertNewAccessLockSessionIsClosed(accessLock, scope)
        } finally {
            session?.close()
            store.close()
            scope.cancel()
            context.clearIsolatedPreferences()
            base.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    @Test
    fun committedCleanupFailureDoesNotResumeRuntimesAndPartialResumeIsPausedAgain() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "backup-runtime-cleanup-${UUID.randomUUID()}")
            .also { check(it.mkdirs()) }
        val context = IsolatedBackupContext(base, root, "runtime-cleanup-${UUID.randomUUID()}")
        val databaseName = "backup-runtime-cleanup-${UUID.randomUUID()}.db"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = LocalDataStore.create(context, databaseName)
        var session: BackupImportSession? = null
        try {
            val preferences = preferencesGateway(context, root, scope)
            val accessLock = AccessLockPreferencesStore(
                context.preferencesDataStoreFile(AccessLockPreferencesStore.DEFAULT_FILE_NAME),
                scope,
            )
            val localAccessLock = AccessLockConfiguration(true, AccessLockTimeout.ONE_MINUTE)
            accessLock.replace(localAccessLock)
            store.backups.replace(store.backups.capture().withObjectiveAndPhoto("Actual"))
            val currentDatabase = store.backups.capture()
            val currentPhotoBytes = Base64.getDecoder().decode(CURRENT_PNG)
            val incomingPhotoBytes = Base64.getDecoder().decode(INCOMING_PNG)
            check(currentPhotoBytes.size == incomingPhotoBytes.size)
            File(context.filesDir, "schedule_photos/$PHOTO_STORAGE_KEY").also {
                it.parentFile?.mkdirs()
                it.writeBytes(currentPhotoBytes)
            }
            val incomingDatabase = currentDatabase.withObjectiveAndPhoto("Copia")
            val incomingPreferences = preferences.capture(currentDatabase).withZoom(150)
            val template = createBackupTemplate(root, incomingDatabase, incomingPreferences, incomingPhotoBytes)
            var initialPauseCalls = 0
            var initialResumeCalls = 0
            val failingJournal = BackupRestoreJournal(context) { directory ->
                if (directory.name.startsWith("miguardia_backup_restore.cleanup-")) {
                    false
                } else {
                    directory.deleteRecursively()
                }
            }
            val coordinator = LocalBackupCoordinator(
                context = context,
                localDataStore = store,
                preferences = preferences,
                pauseRuntimes = { initialPauseCalls++ },
                resumeRuntimes = { initialResumeCalls++ },
                clock = CLOCK,
                zoneId = ZONE,
                journal = failingJournal,
            )
            val openedSession = coordinator.openFreshSession(template, root)
            session = openedSession

            val cleanupFailure = assertSuspendFails {
                coordinator.apply(
                    session = openedSession,
                    choice = RestoreChoice.REPLACE_ALL,
                    resolutions = emptyList(),
                    replaceConfirmation = LocalBackupCoordinator.REPLACE_CONFIRMATION,
                )
            }

            assertTrue(cleanupFailure is RestoreRecoveryRequiredException)
            assertEquals(1, initialPauseCalls)
            assertEquals(0, initialResumeCalls)
            assertEquals(incomingDatabase, store.backups.capture())
            assertTrue(
                context.noBackupFilesDir.listFiles().orEmpty()
                    .any { it.name.startsWith("miguardia_backup_restore.cleanup-") },
            )
            assertEquals(AccessLockStoreRead.Ready(localAccessLock), accessLock.read())
            assertNewAccessLockSessionIsClosed(accessLock, scope)

            var recoveryPauseCalls = 0
            var recoveryResumeCalls = 0
            val recoveryCoordinator = LocalBackupCoordinator(
                context = context,
                localDataStore = store,
                preferences = preferences,
                pauseRuntimes = { recoveryPauseCalls++ },
                resumeRuntimes = {
                    recoveryResumeCalls++
                    throw IOException("fallo parcial ficticio al reactivar")
                },
                clock = CLOCK,
                zoneId = ZONE,
            )

            val resumeFailure = assertSuspendFails { recoveryCoordinator.recoverAndResume() }

            assertTrue(resumeFailure is IOException)
            assertEquals(1, recoveryResumeCalls)
            assertEquals(1, recoveryPauseCalls)
            assertTrue(
                context.noBackupFilesDir.listFiles().orEmpty()
                    .none { it.name.startsWith("miguardia_backup_restore.cleanup-") },
            )
        } finally {
            session?.close()
            store.close()
            scope.cancel()
            context.clearIsolatedPreferences()
            base.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    @Test
    fun everyJournalCheckpointRecoversExactlyTheOldOrTheNewAggregate() = runBlocking {
        RestoreCheckpoint.entries.forEach { checkpoint ->
            runCheckpointScenario(checkpoint, FailureMode.PROCESS_DEATH)
        }
    }

    @Test
    fun ordinaryFailureAtEveryCheckpointRollsBackUnlessAlreadyCommitted() = runBlocking {
        RestoreCheckpoint.entries.forEach { checkpoint ->
            runCheckpointScenario(checkpoint, FailureMode.ORDINARY_FAILURE)
        }
    }

    @Test
    fun partiallyDeletedCommittedTombstoneIsSweptWithoutRollingBackTheVerifiedState() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "backup-cleanup-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val context = IsolatedBackupContext(base, root, "cleanup-${UUID.randomUUID()}")
        val databaseName = "backup-cleanup-${UUID.randomUUID()}.db"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var store: LocalDataStore? = null
        try {
            val preferences = preferencesGateway(context, root, scope)
            val initialStore = LocalDataStore.create(context, databaseName)
            store = initialStore
            val previousDatabase = initialStore.backups.capture()
            val desiredDatabase = previousDatabase.copy(
                tables = previousDatabase.tables.map { table ->
                    if (table.name == "objectives") {
                        table.copy(records = listOf(objectiveRecord("Estado verificado")))
                    } else {
                        table
                    }
                },
            )
            val previousPreferences = preferences.capture(previousDatabase)
            val desiredPreferences = preferences.capture(desiredDatabase)
            initialStore.backups.replace(desiredDatabase)

            val journal = BackupRestoreJournal(context)
            journal.prepare(
                oldDatabase = previousDatabase,
                oldPreferences = previousPreferences,
                oldPhotos = emptyList(),
                newDatabase = desiredDatabase,
                newPreferences = desiredPreferences,
                newPhotos = emptyList(),
                createdAtEpochMillis = CREATED_AT,
                zoneId = ZONE,
            )
            journal.writePhase(RestoreJournalPhase.COMMITTED)
            val active = File(context.noBackupFilesDir, "miguardia_backup_restore")
            val tombstone = File(
                context.noBackupFilesDir,
                "miguardia_backup_restore.cleanup-${UUID.randomUUID()}",
            )
            assertTrue(active.renameTo(tombstone))
            assertTrue(File(tombstone, "restore.phase").delete())
            assertTrue(File(tombstone, "previous.miguardia-backup").delete())

            initialStore.close()
            val restartedStore = LocalDataStore.create(context, databaseName)
            store = restartedStore
            val coordinator = LocalBackupCoordinator(
                context = context,
                localDataStore = restartedStore,
                preferences = preferences,
                clock = CLOCK,
                zoneId = ZONE,
            )

            assertFalse(coordinator.recoverAtStartup())
            assertEquals(desiredDatabase, restartedStore.backups.capture())
            assertFalse(active.exists())
            assertFalse(tombstone.exists())
        } finally {
            store?.close()
            scope.cancel()
            context.clearIsolatedPreferences()
            base.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    @Test
    fun failedJournalDeletionRemainsDurablyPendingUntilTheNextSuccessfulSweep() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "backup-journal-delete-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val context = IsolatedBackupContext(base, root, "journal-delete-${UUID.randomUUID()}")
        try {
            val active = File(context.noBackupFilesDir, "miguardia_backup_restore").also {
                check(it.mkdirs())
            }
            File(active, "previous.miguardia-backup").writeText("datos privados")
            val failingJournal = BackupRestoreJournal(context) { false }

            val failure = try {
                failingJournal.cleanup()
                throw AssertionError("Se esperaba un fallo de limpieza")
            } catch (error: AssertionError) {
                throw error
            } catch (error: IOException) {
                error
            }

            assertTrue(failure.message.orEmpty().contains("journal"))
            assertFalse(active.exists())
            val tombstones = context.noBackupFilesDir.listFiles().orEmpty()
                .filter { it.name.startsWith("miguardia_backup_restore.cleanup-") }
            assertEquals(1, tombstones.size)
            assertTrue(File(tombstones.single(), "previous.miguardia-backup").isFile)

            BackupRestoreJournal(context).cleanupInterruptedPreparation()
            assertTrue(
                context.noBackupFilesDir.listFiles().orEmpty()
                    .none { it.name.startsWith("miguardia_backup_restore.cleanup-") },
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun startupRecoveryFailsClosedWhenPrivateBackupStagingCannotBeRemoved() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "backup-staging-cleanup-${UUID.randomUUID()}")
            .also { check(it.mkdirs()) }
        val context = IsolatedBackupContext(base, root, "staging-cleanup-${UUID.randomUUID()}")
        val databaseName = "backup-staging-cleanup-${UUID.randomUUID()}.db"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = LocalDataStore.create(context, databaseName)
        try {
            val coordinator = LocalBackupCoordinator(
                context = context,
                localDataStore = store,
                preferences = preferencesGateway(context, root, scope),
                clock = CLOCK,
                zoneId = ZONE,
                startupStagingCleanup = {
                    throw IOException("fallo ficticio al retirar staging privado")
                },
            )

            val failure = assertSuspendFails { coordinator.recoverAtStartup() }

            assertTrue(failure is IOException)
            assertTrue(failure.message.orEmpty().contains("staging privado"))
        } finally {
            store.close()
            scope.cancel()
            context.clearIsolatedPreferences()
            base.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    private suspend fun runCheckpointScenario(
        checkpoint: RestoreCheckpoint,
        failureMode: FailureMode,
    ) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "backup-recovery-${checkpoint.name}-${UUID.randomUUID()}")
            .also { check(it.mkdirs()) }
        val context = IsolatedBackupContext(base, root, "${checkpoint.name}-${UUID.randomUUID()}")
        val databaseName = "backup-recovery-${UUID.randomUUID()}.db"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var store: LocalDataStore? = null
        var session: BackupImportSession? = null
        try {
            val preferences = preferencesGateway(context, root, scope)
            val initialStore = LocalDataStore.create(context, databaseName)
            store = initialStore
            initialStore.backups.replace(initialStore.backups.capture().withObjectiveAndPhoto("Actual"))
            val oldDatabase = initialStore.backups.capture()
            val currentPhotoBytes = Base64.getDecoder().decode(CURRENT_PNG)
            val incomingPhotoBytes = Base64.getDecoder().decode(INCOMING_PNG)
            check(currentPhotoBytes.size == incomingPhotoBytes.size)
            val livePhoto = File(context.filesDir, "schedule_photos/$PHOTO_STORAGE_KEY").also {
                it.parentFile?.mkdirs()
                it.writeBytes(currentPhotoBytes)
            }
            val privateReport = File(context.filesDir, "reports/artifacts/informe-anterior.pdf").also {
                it.parentFile?.mkdirs()
                it.writeText("informe privado anterior")
            }
            val stagedReport = File(context.filesDir, "reports/staging/informe-temporal.xlsx").also {
                it.parentFile?.mkdirs()
                it.writeText("informe temporal anterior")
            }
            val externalReport = File(root, "documentos/informe-guardado.pdf").also {
                it.parentFile?.mkdirs()
                it.writeText("documento externo")
            }
            val oldPreferences = preferences.capture(oldDatabase)
            val desiredDatabase = oldDatabase.withObjectiveAndPhoto("Copia")
            val desiredPreferences = oldPreferences.map { preference ->
                if (preference.key == "display.zoom_percent") {
                    preference.copy(values = listOf("150"))
                } else {
                    preference
                }
            }
            initialStore.backups.validateCandidate(desiredDatabase)
            preferences.decode(desiredPreferences, desiredDatabase)

            val source = File(root, "source${MiGuardiaBackupContract.FILE_EXTENSION}")
            val incomingPhoto = File(root, "incoming/$PHOTO_STORAGE_KEY").also {
                it.parentFile?.mkdirs()
                it.writeBytes(incomingPhotoBytes)
            }
            val manifest = BackupContainer.create(
                target = source,
                workingDirectory = File(root, "container-work"),
                backupId = UUID.fromString(BACKUP_ID),
                createdAtEpochMillis = CREATED_AT,
                zoneId = ZONE,
                database = desiredDatabase,
                preferences = desiredPreferences,
                photoMode = BackupPhotoMode.INCLUDED,
                photoAssets = listOf(
                    BackupPhotoAsset(
                        metadata = BackupPhotoMetadata(
                            recordId = PHOTO_ID,
                            storageKey = PHOTO_STORAGE_KEY,
                            mimeType = "image/png",
                            byteSize = incomingPhotoBytes.size.toLong(),
                            pixelWidth = 1,
                            pixelHeight = 1,
                            sha256 = "0".repeat(64),
                        ),
                        file = incomingPhoto,
                    ),
                ),
                password = null,
            )
            val staged = StagedBackupSource(source, BackupContainer.readHeader(source))
            val extracted = BackupContainer.extract(source, File(root, "container-read"), null)
            val comparison = BackupComparator.compare(
                oldDatabase,
                desiredDatabase,
                oldPreferences,
                desiredPreferences,
            )
            val importSession = BackupImportSession(
                source = staged,
                extracted = extracted,
                incomingPreferences = desiredPreferences,
                currentDatabase = oldDatabase,
                currentPreferences = oldPreferences,
                comparison = comparison,
                preview = com.blackatsystems.miguardia.core.domain.backup.BackupPreview(
                    manifest = manifest,
                    historicalSectors = emptyList(),
                    currentCounts = oldDatabase.counts(),
                    incomingCounts = desiredDatabase.counts(),
                    newRecords = comparison.newRecords,
                    identicalRecords = comparison.identicalRecords,
                    conflicts = comparison.conflicts,
                    photosInBackup = 0,
                    photosMissingFromBackup = 0,
                    timelineCompatible = comparison.timelineCompatible,
                    destinationEmpty = comparison.destinationEmpty,
                ),
            )
            session = importSession
            var faultInjected = false
            val crashing = LocalBackupCoordinator(
                context = context,
                localDataStore = initialStore,
                preferences = preferences,
                clock = CLOCK,
                zoneId = ZONE,
                faultInjector = RestoreFaultInjector { reached ->
                    if (reached == checkpoint && !faultInjected) {
                        faultInjected = true
                        when (failureMode) {
                            FailureMode.PROCESS_DEATH -> throw SimulatedProcessDeath()
                            FailureMode.ORDINARY_FAILURE -> throw InjectedRestoreFailure()
                        }
                    }
                },
            )

            val failure = try {
                crashing.apply(
                    session = importSession,
                    choice = RestoreChoice.REPLACE_ALL,
                    resolutions = emptyList(),
                    replaceConfirmation = LocalBackupCoordinator.REPLACE_CONFIRMATION,
                )
                throw AssertionError("El corte simulado no ocurrió en $checkpoint")
            } catch (error: Throwable) {
                error
            }
            when (failureMode) {
                FailureMode.PROCESS_DEATH -> assertTrue(failure is SimulatedProcessDeath)
                FailureMode.ORDINARY_FAILURE -> assertTrue(failure is Exception)
            }

            initialStore.close()
            val restartedStore = LocalDataStore.create(context, databaseName)
            store = restartedStore
            val restarted = LocalBackupCoordinator(
                context = context,
                localDataStore = restartedStore,
                preferences = preferences,
                clock = CLOCK,
                zoneId = ZONE,
            )
            val recoveredAtStartup = restarted.recoverAtStartup()
            val shouldHavePendingJournal = failureMode == FailureMode.PROCESS_DEATH ||
                checkpoint == RestoreCheckpoint.AFTER_COMMITTED
            assertEquals(shouldHavePendingJournal, recoveredAtStartup)

            val expectsNew = when (failureMode) {
                FailureMode.PROCESS_DEATH -> checkpoint in setOf(
                    RestoreCheckpoint.AFTER_VERIFIED,
                    RestoreCheckpoint.AFTER_COMMITTED,
                )
                FailureMode.ORDINARY_FAILURE -> checkpoint == RestoreCheckpoint.AFTER_COMMITTED
            }
            assertEquals(if (expectsNew) desiredDatabase else oldDatabase, restartedStore.backups.capture())
            val expectedDatabase = if (expectsNew) desiredDatabase else oldDatabase
            assertEquals(
                if (expectsNew) desiredPreferences else oldPreferences,
                preferences.capture(expectedDatabase),
            )
            assertTrue(
                livePhoto.readBytes().contentEquals(if (expectsNew) incomingPhotoBytes else currentPhotoBytes),
            )
            assertEquals(!expectsNew, privateReport.exists())
            assertEquals(!expectsNew, stagedReport.exists())
            assertTrue(externalReport.isFile)
            assertEquals("documento externo", externalReport.readText())
            assertFalse(File(context.noBackupFilesDir, "miguardia_backup_restore").exists())
        } finally {
            session?.close()
            store?.close()
            scope.cancel()
            context.clearIsolatedPreferences()
            base.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    private fun preferencesGateway(
        context: Context,
        root: File,
        scope: CoroutineScope,
    ) = PortablePreferencesGateway(
        context = context,
        guardProfile = GuardProfileStore(File(root, "profile.preferences_pb"), scope),
        summaryStore = SummaryPreferencesStore(File(root, "summary.preferences_pb"), scope),
        notificationStore = NotificationPreferencesStore(File(root, "notifications.preferences_pb"), scope),
        weatherStore = WeatherPreferencesStore(File(root, "weather.preferences_pb"), scope),
    )

    private fun createBackupTemplate(
        root: File,
        database: BackupDatabaseSnapshot,
        preferences: List<BackupPreference>,
        incomingPhotoBytes: ByteArray,
    ): File {
        val source = File(root, "template-${UUID.randomUUID()}${MiGuardiaBackupContract.FILE_EXTENSION}")
        val incomingPhoto = File(root, "incoming-${UUID.randomUUID()}/$PHOTO_STORAGE_KEY").also {
            it.parentFile?.mkdirs()
            it.writeBytes(incomingPhotoBytes)
        }
        BackupContainer.create(
            target = source,
            workingDirectory = File(root, "container-work"),
            backupId = UUID.fromString(BACKUP_ID),
            createdAtEpochMillis = CREATED_AT,
            zoneId = ZONE,
            database = database,
            preferences = preferences,
            photoMode = BackupPhotoMode.INCLUDED,
            photoAssets = listOf(
                BackupPhotoAsset(
                    metadata = BackupPhotoMetadata(
                        recordId = PHOTO_ID,
                        storageKey = PHOTO_STORAGE_KEY,
                        mimeType = "image/png",
                        byteSize = incomingPhotoBytes.size.toLong(),
                        pixelWidth = 1,
                        pixelHeight = 1,
                        sha256 = "0".repeat(64),
                    ),
                    file = incomingPhoto,
                ),
            ),
            password = null,
        )
        return source
    }

    private suspend fun LocalBackupCoordinator.openFreshSession(
        template: File,
        root: File,
    ): BackupImportSession {
        val sourceFile = File(
            root,
            "staged-${UUID.randomUUID()}${MiGuardiaBackupContract.FILE_EXTENSION}",
        )
        template.copyTo(sourceFile)
        val source = StagedBackupSource(sourceFile, BackupContainer.readHeader(sourceFile))
        return try {
            openSource(source, password = null)
        } catch (error: Throwable) {
            source.close()
            throw error
        }
    }

    private fun List<BackupPreference>.withZoom(percent: Int): List<BackupPreference> = map { preference ->
        if (preference.key == "display.zoom_percent") {
            preference.copy(values = listOf(percent.toString()))
        } else {
            preference
        }
    }

    private fun BackupDatabaseSnapshot.withObjectiveAndPhoto(objectiveName: String): BackupDatabaseSnapshot = copy(
        tables = tables.map { table ->
            when (table.name) {
                "objectives" -> BackupTable(
                    name = table.name,
                    columns = table.columns,
                    primaryKey = table.primaryKey,
                    records = listOf(
                        BackupRecord(
                            listOf(
                                BackupValue.Text(OBJECTIVE_ID),
                                BackupValue.Text(objectiveName),
                                BackupValue.Text("FIC"),
                                BackupValue.Null,
                                BackupValue.Null,
                                BackupValue.Integer(1),
                                BackupValue.Integer(CREATED_AT),
                                BackupValue.Integer(CREATED_AT),
                            ),
                        ),
                    ),
                )
                "schedule_photos" -> BackupTable(
                    name = table.name,
                    columns = table.columns,
                    primaryKey = table.primaryKey,
                    records = listOf(
                        BackupRecord(
                            listOf(
                                BackupValue.Text(PHOTO_ID),
                                BackupValue.Text("2026-08"),
                                BackupValue.Text(OBJECTIVE_ID),
                                BackupValue.Text(objectiveName),
                                BackupValue.Text("FIC"),
                                BackupValue.Text(PHOTO_STORAGE_KEY),
                                BackupValue.Text("image/png"),
                                BackupValue.Integer(67),
                                BackupValue.Integer(1),
                                BackupValue.Integer(1),
                                BackupValue.Integer(CREATED_AT),
                                BackupValue.Integer(CREATED_AT),
                            ),
                        ),
                    ),
                )
                else -> table
            }
        },
    )

    private fun BackupDatabaseSnapshot.counts(): Map<String, Int> =
        tables.associate { it.name to it.records.size }

    private fun objectiveRecord(name: String): BackupRecord = BackupRecord(
        listOf(
            BackupValue.Text(OBJECTIVE_ID),
            BackupValue.Text(name),
            BackupValue.Text("FIC"),
            BackupValue.Null,
            BackupValue.Null,
            BackupValue.Integer(1),
            BackupValue.Integer(CREATED_AT),
            BackupValue.Integer(CREATED_AT),
        ),
    )

    private suspend fun assertSuspendFails(block: suspend () -> Unit): Throwable = try {
        block()
        throw AssertionError("Se esperaba que la operación fallara")
    } catch (error: AssertionError) {
        throw error
    } catch (error: Throwable) {
        error
    }

    private suspend fun assertNewAccessLockSessionIsClosed(
        store: AccessLockPreferencesStore,
        scope: CoroutineScope,
    ) {
        val coordinator = AccessLockCoordinator(store, scope)
        coordinator.initializeAfterRecovery()
        coordinator.activityStarted(Any(), deviceLocked = false)
        assertTrue(coordinator.state.value.locked)
        assertFalse(coordinator.state.value.allowsSensitiveContent)
    }

    private class IsolatedBackupContext(
        base: Context,
        private val root: File,
        private val preferencePrefix: String,
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(root, "files").also { it.mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no-backup").also { it.mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").also { it.mkdirs() }

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("$preferencePrefix-$name", mode)

        fun clearIsolatedPreferences() {
            getSharedPreferences(MainActivity.DISPLAY_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private class SimulatedProcessDeath : Error()
    private class InjectedRestoreFailure : java.io.IOException()

    private enum class FailureMode {
        PROCESS_DEATH,
        ORDINARY_FAILURE,
    }

    private companion object {
        const val CREATED_AT = 1_788_131_400_000L
        const val BACKUP_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        const val OBJECTIVE_ID = "11111111-1111-4111-8111-111111111111"
        const val PHOTO_ID = "44444444-4444-4444-8444-444444444444"
        const val PHOTO_STORAGE_KEY = "55555555-5555-4555-8555-555555555555.png"
        const val CURRENT_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2ZQAAAABJRU5ErkJggg=="
        const val INCOMING_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAAAAAA6fptVAAAACklEQVR4nGNgAAAAAgABSK+kcQAAAABJRU5ErkJggg=="
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
        val CLOCK: Clock = Clock.fixed(Instant.ofEpochMilli(CREATED_AT), ZONE)
    }
}
