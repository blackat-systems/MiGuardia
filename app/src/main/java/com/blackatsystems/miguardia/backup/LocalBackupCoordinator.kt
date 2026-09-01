package com.blackatsystems.miguardia.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.storage.StorageManager
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.backup.BackupComparator
import com.blackatsystems.miguardia.core.domain.backup.BackupComparison
import com.blackatsystems.miguardia.core.domain.backup.BackupConflict
import com.blackatsystems.miguardia.core.domain.backup.BackupContainer
import com.blackatsystems.miguardia.core.domain.backup.BackupContainerHeader
import com.blackatsystems.miguardia.core.domain.backup.BackupDatabaseSnapshot
import com.blackatsystems.miguardia.core.domain.backup.BackupManifest
import com.blackatsystems.miguardia.core.domain.backup.BackupMemoryBudget
import com.blackatsystems.miguardia.core.domain.backup.BackupPhotoAsset
import com.blackatsystems.miguardia.core.domain.backup.BackupPhotoMode
import com.blackatsystems.miguardia.core.domain.backup.BackupPayloadCodec
import com.blackatsystems.miguardia.core.domain.backup.BackupPreference
import com.blackatsystems.miguardia.core.domain.backup.BackupPreview
import com.blackatsystems.miguardia.core.domain.backup.BackupValue
import com.blackatsystems.miguardia.core.domain.backup.ExtractedBackup
import com.blackatsystems.miguardia.core.domain.backup.InvalidBackupException
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupContract
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupSchemaV5
import com.blackatsystems.miguardia.core.domain.backup.ResolvedBackupConflict
import com.blackatsystems.miguardia.core.domain.backup.backupKey
import com.blackatsystems.miguardia.reports.ReportArtifactStore
import com.blackatsystems.miguardia.reports.ReportPhotoStager
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class BackupCreateOptions(
    val includePhotos: Boolean = true,
    val password: String? = null,
) {
    init {
        if (password != null) require(password.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH)
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 256
    }
}

class PreparedBackupArtifact internal constructor(
    val file: File,
    val suggestedName: String,
    val manifest: BackupManifest,
) : Closeable {
    override fun close() {
        file.deletePrivateFileChecked("la copia privada preparada")
    }
}

class StagedBackupSource internal constructor(
    val file: File,
    val header: BackupContainerHeader,
) : Closeable {
    override fun close() {
        file.deletePrivateFileChecked("la copia privada seleccionada")
    }
}

class BackupImportSession internal constructor(
    val source: StagedBackupSource,
    val extracted: ExtractedBackup,
    val incomingPreferences: List<BackupPreference>,
    val currentDatabase: BackupDatabaseSnapshot,
    val currentPreferences: List<BackupPreference>,
    val comparison: BackupComparison,
    val preview: BackupPreview,
) : Closeable {
    val conflicts: List<BackupConflict> get() = comparison.conflicts

    override fun close() {
        var failure: Throwable? = null
        try {
            extracted.close()
        } catch (error: Throwable) {
            failure = error
        }
        try {
            source.close()
        } catch (error: Throwable) {
            failure?.addSuppressed(error) ?: run { failure = error }
        }
        failure?.let { throw it }
    }
}

enum class RestoreChoice {
    MERGE,
    REPLACE_ALL,
}

class RestoreRecoveryRequiredException(
    message: String,
    cause: Throwable,
) : IOException(message, cause)

class IncompleteBackupDocumentException(
    message: String,
    cause: Throwable,
) : IOException(message, cause)

enum class RestoreCheckpoint {
    AFTER_PREPARED,
    AFTER_SWAPPED,
    AFTER_DATABASE,
    AFTER_PREFERENCES,
    AFTER_PHOTOS,
    AFTER_VERIFIED,
    AFTER_COMMITTED,
}

fun interface RestoreFaultInjector {
    fun hit(checkpoint: RestoreCheckpoint)

    companion object {
        val NONE = RestoreFaultInjector { }
    }
}

class LocalBackupCoordinator(
    context: Context,
    private val localDataStore: LocalDataStore,
    private val preferences: PortablePreferencesGateway,
    private val pauseRuntimes: suspend () -> Unit = {},
    private val resumeRuntimes: suspend () -> Unit = {},
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = AppDefaults.zoneId(),
    private val mutationGate: LocalDataMutationGate = LocalDataMutationGate(),
    private val faultInjector: RestoreFaultInjector = RestoreFaultInjector.NONE,
    private val afterPrivateBackupValidated: suspend (attempt: Int) -> Unit = {},
    private val afterDocumentPayloadStaged: suspend (destination: Uri) -> Unit = {},
    private val journal: BackupRestoreJournal = BackupRestoreJournal(context.applicationContext),
    private val operationalMemoryLimitBytes: Long = BackupMemoryBudget.operationalHeapBytes(),
    private val startupStagingCleanup: (File) -> Unit = { staging ->
        staging.deletePrivateTreeChecked("el staging privado de copias")
    },
) {
    private val applicationContext = context.applicationContext
    private val contentResolver = applicationContext.contentResolver
    private val privateStaging = File(applicationContext.filesDir, "backups/staging")
    private val photoFiles = BackupPhotoFiles(applicationContext)
    private val operationMutex = Mutex()
    private val snapshotMemoryLimitBytes = BackupMemoryBudget.perSnapshotBytes(
        operationalMemoryLimitBytes,
        BackupMemoryBudget.RECOMMENDED_PEAK_FACTOR,
    )

    suspend fun prepareBackup(options: BackupCreateOptions): PreparedBackupArtifact = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            privateStaging.mkdirs()
            for (attempt in 1..MAX_CAPTURE_ATTEMPTS) {
                val captured = localDataStore.backups.capture(snapshotMemoryLimitBytes)
                val database = captured.withPhotoChoice(options.includePhotos)
                val portablePreferences = preferences.capture(database)
                preferences.decode(portablePreferences, database)
                val photoAssets = if (options.includePhotos) {
                    photoFiles.assetsForCurrentSnapshot(database)
                } else {
                    emptyList()
                }
                ensureInternalSpace(database, photoAssets.sumOf { it.metadata.byteSize }, multiplier = 4)
                val createdAt = clock.millis()
                val file = File(
                    privateStaging,
                    "prepared-${UUID.randomUUID()}${MiGuardiaBackupContract.FILE_EXTENSION}",
                )
                var completed = false
                var attemptFailure: Throwable? = null
                try {
                    val manifest = BackupContainer.create(
                        target = file,
                        workingDirectory = privateStaging,
                        backupId = UUID.randomUUID(),
                        createdAtEpochMillis = createdAt,
                        zoneId = zoneId,
                        database = database,
                        preferences = portablePreferences,
                        photoMode = if (options.includePhotos) BackupPhotoMode.INCLUDED else BackupPhotoMode.OMITTED,
                        photoAssets = photoAssets,
                        password = options.password?.toCharArray(),
                    )
                    BackupContainer.extract(
                        file,
                        privateStaging,
                        options.password?.toCharArray(),
                        snapshotMemoryLimitBytes,
                    ).use { extracted ->
                        preferences.decode(extracted.payload.preferences, extracted.payload.database)
                        localDataStore.backups.validateCandidate(
                            extracted.payload.database,
                            snapshotMemoryLimitBytes,
                        )
                        validateExtractedPhotos(extracted)
                    }
                    afterPrivateBackupValidated(attempt)
                    val recapturedDatabase = localDataStore.backups.capture(snapshotMemoryLimitBytes)
                        .withPhotoChoice(options.includePhotos)
                    val recapturedPreferences = preferences.capture(recapturedDatabase)
                    val recapturedPhotos = if (options.includePhotos) {
                        photoFiles.assetsForCurrentSnapshot(recapturedDatabase)
                    } else {
                        emptyList()
                    }
                    val stable = database == recapturedDatabase &&
                        portablePreferences == recapturedPreferences &&
                        photoAssets.map(BackupPhotoAsset::metadata) ==
                        recapturedPhotos.map(BackupPhotoAsset::metadata)
                    if (stable) {
                        completed = true
                        return@withContext PreparedBackupArtifact(file, suggestedName(createdAt), manifest)
                    }
                } catch (error: Throwable) {
                    attemptFailure = error
                    throw error
                } finally {
                    if (!completed) {
                        if (attemptFailure != null) {
                            file.addPrivateFileCleanupFailure(
                                attemptFailure,
                                "la captura privada incompleta",
                            )
                        } else {
                            file.deletePrivateFileChecked("la captura privada descartada")
                        }
                    }
                }
            }
            throw IOException(
                "Los datos cambiaron mientras se preparaba la copia. Esperá un momento y volvé a intentarlo.",
            )
        }
    }

    suspend fun copyToDocument(artifact: PreparedBackupArtifact, destination: Uri) =
        operationMutex.withLock {
            withContext(Dispatchers.IO) {
                requireContentUri(destination)
                try {
                    val expected = FileInputStream(artifact.file).use {
                        it.sha256Bounded(MiGuardiaBackupContract.MAX_CONTAINER_BYTES)
                    }
                    val header = ByteArray(BackupContainer.HEADER_BYTES)
                    val expectedPayload = FileInputStream(artifact.file).use { input ->
                        input.readExactly(header)
                        input.sha256Bounded(
                            MiGuardiaBackupContract.MAX_CONTAINER_BYTES - BackupContainer.HEADER_BYTES,
                        )
                    }
                    val descriptor = contentResolver.openFileDescriptor(destination, "rwt")
                        ?: throw IOException("El destino elegido no se pudo abrir.")
                    descriptor.use { parcel ->
                        FileInputStream(artifact.file).use { input ->
                            input.skipExactly(BackupContainer.HEADER_BYTES.toLong())
                            FileOutputStream(parcel.fileDescriptor).use { output ->
                                val channel = output.channel
                                channel.truncate(0L)
                                output.write(ByteArray(BackupContainer.HEADER_BYTES))
                                output.flush()
                                output.fd.sync()
                                channel.position(BackupContainer.HEADER_BYTES.toLong())
                                val copied = input.copyBoundedTo(
                                    output,
                                    MiGuardiaBackupContract.MAX_CONTAINER_BYTES - BackupContainer.HEADER_BYTES,
                                )
                                output.flush()
                                output.fd.sync()
                                if (copied != artifact.file.length() - BackupContainer.HEADER_BYTES) {
                                    throw IOException("El destino recibió una copia incompleta.")
                                }
                            }
                        }
                    }
                    val stagedPayload = contentResolver.openFileDescriptor(destination, "r")
                        ?: throw IOException("El destino preparado no se pudo volver a abrir.")
                    val actualPayload = stagedPayload.use { parcel ->
                        FileInputStream(parcel.fileDescriptor).use { input ->
                            input.skipExactly(BackupContainer.HEADER_BYTES.toLong())
                            input.sha256Bounded(
                                MiGuardiaBackupContract.MAX_CONTAINER_BYTES - BackupContainer.HEADER_BYTES,
                            )
                        }
                    }
                    if (actualPayload != expectedPayload) {
                        throw IOException("El destino preparado no coincide con la copia interna.")
                    }
                    afterDocumentPayloadStaged(destination)
                    val publishDescriptor = contentResolver.openFileDescriptor(destination, "rw")
                        ?: throw IOException("El destino preparado no permite publicar la copia completa.")
                    publishDescriptor.use { parcel ->
                        FileOutputStream(parcel.fileDescriptor).use { output ->
                            output.channel.position(0L)
                            output.write(header)
                            output.flush()
                            output.fd.sync()
                        }
                    }
                    val stored = contentResolver.openFileDescriptor(destination, "r")
                        ?: throw IOException("El destino guardado no se pudo volver a abrir.")
                    val actual = stored.use { parcel ->
                        FileInputStream(parcel.fileDescriptor).use {
                            it.sha256Bounded(MiGuardiaBackupContract.MAX_CONTAINER_BYTES)
                        }
                    }
                    if (actual != expected) {
                        throw IOException("El destino guardado no coincide con la copia preparada.")
                    }
                } catch (cancelled: CancellationException) {
                    if (!neutralizeIncompleteDocument(destination)) {
                        cancelled.addSuppressed(
                            IOException("Android no pudo retirar ni vaciar el documento incompleto."),
                        )
                    }
                    throw cancelled
                } catch (error: Exception) {
                    if (!neutralizeIncompleteDocument(destination)) {
                        throw IncompleteBackupDocumentException(
                            "No pudimos guardar ni retirar el documento incompleto. Eliminá manualmente el archivo elegido.",
                            error,
                        )
                    }
                    throw IOException("No pudimos guardar la copia en el destino elegido.", error)
                }
            }
        }

    suspend fun discardIncompleteDocument(destination: Uri): Boolean = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            if (destination.scheme != ContentResolver.SCHEME_CONTENT) return@withContext false
            neutralizeIncompleteDocument(destination)
        }
    }

    private fun neutralizeIncompleteDocument(destination: Uri): Boolean {
        val truncated = runCatching {
            val descriptor = contentResolver.openFileDescriptor(destination, "rwt")
                ?: return@runCatching false
            descriptor.use { parcel ->
                FileOutputStream(parcel.fileDescriptor).use { output ->
                    output.channel.truncate(0L)
                    output.flush()
                    output.fd.sync()
                }
            }
            true
        }.getOrDefault(false)
        val deleted = runCatching { contentResolver.delete(destination, null, null) > 0 }
            .getOrDefault(false)
        return truncated || deleted
    }

    suspend fun stageSource(source: Uri): StagedBackupSource = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            requireContentUri(source)
            privateStaging.mkdirs()
            val target = File(privateStaging, "selected-${UUID.randomUUID()}${MiGuardiaBackupContract.FILE_EXTENSION}")
            try {
                val descriptor = contentResolver.openFileDescriptor(source, "r")
                    ?: throw IOException("No se pudo abrir la copia elegida.")
                descriptor.use { parcel ->
                    val declaredSize = parcel.statSize
                    if (declaredSize > MiGuardiaBackupContract.MAX_CONTAINER_BYTES) {
                        throw InvalidBackupException("La copia elegida supera el límite seguro.")
                    }
                    ensureInternalSpace(
                        null,
                        declaredSize.takeIf { it >= 0L } ?: MiGuardiaBackupContract.MAX_CONTAINER_BYTES,
                        multiplier = 1,
                    )
                    FileInputStream(parcel.fileDescriptor).use { input ->
                        FileOutputStream(target).use { output ->
                            val copied = input.copyBoundedTo(output, MiGuardiaBackupContract.MAX_CONTAINER_BYTES)
                            if (declaredSize >= 0L && copied != declaredSize) {
                                throw IOException("La copia elegida quedó truncada al leerse.")
                            }
                            output.fd.sync()
                        }
                    }
                    ensureInternalSpace(
                        null,
                        MiGuardiaBackupContract.maximumExpandedPayloadBytes(target.length()),
                        multiplier = 2,
                    )
                }
                StagedBackupSource(target, BackupContainer.readHeader(target))
            } catch (error: Exception) {
                target.addPrivateFileCleanupFailure(error, "la copia seleccionada incompleta")
                throw error
            }
        }
    }

    suspend fun openSource(
        source: StagedBackupSource,
        password: String?,
    ): BackupImportSession = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            val extracted = BackupContainer.extract(
                source.file,
                privateStaging,
                password?.toCharArray(),
                snapshotMemoryLimitBytes,
            )
            try {
                val incomingPreferences = preferences.normalize(
                    extracted.payload.preferences,
                    extracted.payload.database,
                )
                preferences.decode(incomingPreferences, extracted.payload.database)
                localDataStore.backups.validateCandidate(
                    extracted.payload.database,
                    snapshotMemoryLimitBytes,
                )
                validateExtractedPhotos(extracted)
                val currentDatabase = localDataStore.backups.capture(snapshotMemoryLimitBytes)
                val currentPreferences = preferences.capture(currentDatabase)
                BackupMemoryBudget.requirePeakFits(
                    current = currentDatabase,
                    incoming = extracted.payload.database,
                    operationalBytes = operationalMemoryLimitBytes,
                )
                val comparison = BackupComparator.compare(
                    currentDatabase,
                    extracted.payload.database,
                    currentPreferences,
                    incomingPreferences,
                )
                val currentPhotoRecords = currentDatabase.table("schedule_photos").records
                val incomingPhotoRecords = extracted.payload.database.table("schedule_photos").records
                val incomingPhotos = incomingPhotoRecords.size
                val incomingPhotoSet = incomingPhotoRecords.toSet()
                val photosMissingFromBackup = currentPhotoRecords.count { it !in incomingPhotoSet }
                val preview = BackupPreview(
                    manifest = extracted.manifest,
                    historicalSectors = historicalSectors(extracted.payload.database),
                    currentCounts = currentDatabase.tables.associate { it.name to it.records.size },
                    incomingCounts = extracted.payload.database.tables.associate { it.name to it.records.size },
                    newRecords = comparison.newRecords,
                    identicalRecords = comparison.identicalRecords,
                    conflicts = comparison.conflicts,
                    photosInBackup = incomingPhotos,
                    photosMissingFromBackup = photosMissingFromBackup,
                    timelineCompatible = comparison.timelineCompatible,
                    destinationEmpty = comparison.destinationEmpty,
                    mergeBlockedReason = comparison.mergeBlockedReason,
                    currentRecordsRemovedOrReplaced = currentDatabase.recordsMissingOrChangedIn(
                        extracted.payload.database,
                    ),
                    currentPreferencesRemovedOrReplaced = currentPreferences.count { current ->
                        incomingPreferences.none { incoming -> incoming.key == current.key && incoming == current }
                    },
                    incomingPreferenceCount = incomingPreferences.size,
                )
                BackupImportSession(
                    source,
                    extracted,
                    incomingPreferences,
                    currentDatabase,
                    currentPreferences,
                    comparison,
                    preview,
                )
            } catch (error: Exception) {
                extracted.close()
                throw error
            }
        }
    }

    suspend fun apply(
        session: BackupImportSession,
        choice: RestoreChoice,
        resolutions: List<ResolvedBackupConflict>,
        replaceConfirmation: String?,
    ): PortableSettings = operationMutex.withLock {
        val desiredDatabase: BackupDatabaseSnapshot
        val desiredPreferences: List<BackupPreference>
        when (choice) {
            RestoreChoice.MERGE -> {
                if (!session.comparison.timelineCompatible) {
                    throw InvalidBackupException("La copia pertenece a otra línea temporal y no se puede combinar.")
                }
                BackupMemoryBudget.requirePotentialMergeFits(
                    current = session.currentDatabase,
                    incoming = session.extracted.payload.database,
                    maximumSnapshotBytes = snapshotMemoryLimitBytes,
                )
                desiredDatabase = BackupComparator.mergeDatabase(
                    session.currentDatabase,
                    session.extracted.payload.database,
                    session.conflicts,
                    resolutions,
                )
                desiredPreferences = BackupComparator.mergePreferences(
                    session.currentPreferences,
                    session.incomingPreferences,
                    session.conflicts,
                    resolutions,
                )
            }
            RestoreChoice.REPLACE_ALL -> {
                if (replaceConfirmation != REPLACE_CONFIRMATION) {
                    throw InvalidBackupException("La segunda confirmación de reemplazo no coincide.")
                }
                desiredDatabase = session.extracted.payload.database
                desiredPreferences = session.incomingPreferences
            }
        }
        BackupMemoryBudget.requirePeakFits(
            current = session.currentDatabase,
            incoming = session.extracted.payload.database,
            merged = desiredDatabase,
            operationalBytes = operationalMemoryLimitBytes,
        )
        localDataStore.backups.validateCandidate(desiredDatabase, snapshotMemoryLimitBytes)
        preferences.decode(desiredPreferences, desiredDatabase)
        mutationGate.withExclusiveMutation {
            applyWithJournal(
                desiredDatabase = desiredDatabase,
                desiredPreferences = desiredPreferences,
                incomingPhotoDirectory = session.extracted.photoDirectory,
                incomingDatabase = session.extracted.payload.database,
                expectedCurrentDatabase = session.currentDatabase,
                expectedCurrentPreferences = session.currentPreferences,
            )
        }
    }

    suspend fun recoverAtStartup(): Boolean = operationMutex.withLock {
        mutationGate.withExclusiveMutation {
            withContext(Dispatchers.IO) {
                journal.cleanupInterruptedPreparation()
                photoFiles.cleanupInterruptedSwap()
                val recovered = if (journal.exists) {
                    when (journal.phaseOrNull()) {
                        RestoreJournalPhase.PREPARED -> journal.cleanup()
                        RestoreJournalPhase.SWAPPED, null -> rollbackFromJournal()
                        RestoreJournalPhase.VERIFIED -> {
                            val newIsValid = if (journal.canVerifyNew) {
                                runCatching {
                                    journal.openNew(snapshotMemoryLimitBytes).use { desired -> verifyState(desired) }
                                }.isSuccess
                            } else {
                                false
                            }
                            if (newIsValid) {
                                journal.writePhase(RestoreJournalPhase.COMMITTED)
                                cleanupCommittedArtifacts()
                            } else {
                                rollbackFromJournal()
                            }
                        }
                        RestoreJournalPhase.COMMITTED -> cleanupCommittedArtifacts()
                    }
                    true
                } else {
                    false
                }
                startupStagingCleanup(privateStaging)
                recovered
            }
        }
    }

    suspend fun recoverAndResume(): PortableSettings {
        recoverAtStartup()
        resumeRuntimesSafely()
        val database = localDataStore.backups.capture(snapshotMemoryLimitBytes)
        return preferences.decode(preferences.capture(database), database)
    }

    private suspend fun applyWithJournal(
        desiredDatabase: BackupDatabaseSnapshot,
        desiredPreferences: List<BackupPreference>,
        incomingPhotoDirectory: File,
        incomingDatabase: BackupDatabaseSnapshot,
        expectedCurrentDatabase: BackupDatabaseSnapshot,
        expectedCurrentPreferences: List<BackupPreference>,
    ): PortableSettings = withContext(Dispatchers.IO) {
        val materialized = File(privateStaging, "desired-photos-${UUID.randomUUID()}")
        var operationFailure: Throwable? = null
        try {
            ensureInternalSpace(
                desiredDatabase,
                photoBytes(expectedCurrentDatabase) + photoBytes(desiredDatabase),
                multiplier = 4,
            )
            val newPhotos = photoFiles.materializeDesired(
                desiredDatabase,
                incomingPhotoDirectory,
                materialized,
                currentSnapshot = expectedCurrentDatabase,
                incomingSnapshot = incomingDatabase,
            )
            pauseRuntimesSafely()
            try {
                localDataStore.backups.replaceWithWriteBarrier(
                    expectedCurrent = expectedCurrentDatabase,
                    replacement = desiredDatabase,
                    decodedMemoryLimitBytes = snapshotMemoryLimitBytes,
                    beforeReplace = { oldDatabase ->
                        val oldPreferences = preferences.capture(oldDatabase)
                        if (oldPreferences != expectedCurrentPreferences) {
                            throw InvalidBackupException(
                                "Los datos cambiaron después de la vista previa. Volvé a validar la copia antes de restaurar.",
                            )
                        }
                        val oldPhotos = photoFiles.assetsForCurrentSnapshot(oldDatabase)
                        ensureInternalSpace(
                            desiredDatabase,
                            photoBytes(oldDatabase) + photoBytes(desiredDatabase),
                            multiplier = 4,
                        )
                        journal.prepare(
                            oldDatabase,
                            oldPreferences,
                            oldPhotos,
                            desiredDatabase,
                            desiredPreferences,
                            newPhotos,
                            clock.millis(),
                            zoneId,
                        )
                        faultInjector.hit(RestoreCheckpoint.AFTER_PREPARED)
                        journal.writePhase(RestoreJournalPhase.SWAPPED)
                        faultInjector.hit(RestoreCheckpoint.AFTER_SWAPPED)
                    },
                    afterReplace = {
                        faultInjector.hit(RestoreCheckpoint.AFTER_DATABASE)
                        preferences.replace(desiredPreferences, desiredDatabase)
                        faultInjector.hit(RestoreCheckpoint.AFTER_PREFERENCES)
                        photoFiles.replaceFrom(desiredDatabase, materialized)
                        faultInjector.hit(RestoreCheckpoint.AFTER_PHOTOS)
                    },
                )
            } catch (cancelled: CancellationException) {
                recoverFailedApplyOrResume(cancelled)
                throw cancelled
            } catch (error: Exception) {
                recoverFailedApplyOrResume(error)
                throw error
            }
            val settings = try {
                val verifiedSettings = verifyState(
                    desiredDatabase,
                    desiredPreferences,
                    newPhotos.associate { it.metadata.storageKey to it.metadata.sha256 },
                )
                journal.writePhase(RestoreJournalPhase.VERIFIED)
                faultInjector.hit(RestoreCheckpoint.AFTER_VERIFIED)
                verifiedSettings
            } catch (cancelled: CancellationException) {
                rollbackOrEscalate(cancelled)
                throw cancelled
            } catch (error: Exception) {
                rollbackOrEscalate(error)
                throw error
            }
            try {
                journal.writePhase(RestoreJournalPhase.COMMITTED)
            } catch (error: Exception) {
                throw RestoreRecoveryRequiredException(
                    "Los datos nuevos quedaron verificados, pero falta cerrar su recuperación de forma segura.",
                    error,
                )
            }
            try {
                faultInjector.hit(RestoreCheckpoint.AFTER_COMMITTED)
            } catch (error: Exception) {
                throw RestoreRecoveryRequiredException(
                    "Los datos nuevos quedaron confirmados, pero falta terminar su recuperación.",
                    error,
                )
            }
            try {
                materialized.deletePrivateTreeChecked("las fotografías temporales de restauración")
            } catch (error: Exception) {
                throw RestoreRecoveryRequiredException(
                    "Los datos nuevos quedaron confirmados, pero falta retirar fotografías temporales privadas.",
                    error,
                )
            }
            val cleanupFailure = runCatching { cleanupCommittedArtifacts() }.exceptionOrNull()
            if (cleanupFailure != null) {
                throw RestoreRecoveryRequiredException(
                    "Los datos se restauraron y verificaron, pero falta retirar el journal de recuperación.",
                    cleanupFailure,
                )
            }
            try {
                resumeRuntimesSafely()
            } catch (error: Exception) {
                throw RestoreRecoveryRequiredException(
                    "Los datos se restauraron y verificaron, pero falta recuperar avisos y Widgets. Reintentá la recuperación.",
                    error,
                )
            }
            settings
        } catch (error: Throwable) {
            operationFailure = error
            throw error
        } finally {
            if (operationFailure != null) {
                materialized.addPrivateTreeCleanupFailure(
                    operationFailure,
                    "las fotografías temporales de restauración",
                )
            } else {
                materialized.deletePrivateTreeChecked("las fotografías temporales de restauración")
            }
        }
    }

    private suspend fun rollbackOrEscalate(original: Throwable) = withContext(NonCancellable) {
        try {
            rollbackFromJournal()
            resumeRuntimesSafely()
        } catch (recovery: Exception) {
            recovery.addSuppressed(original)
            throw RestoreRecoveryRequiredException(
                "La restauración se interrumpió y necesita recuperación automática al volver a abrir MiGuardia.",
                recovery,
            )
        }
    }

    private suspend fun pauseRuntimesSafely() {
        try {
            pauseRuntimes()
        } catch (cancelled: CancellationException) {
            resumeUnchangedStateOrEscalate(cancelled)
            throw cancelled
        } catch (error: Exception) {
            resumeUnchangedStateOrEscalate(error)
            throw IOException("No se pudieron pausar las superficies que leen los datos.", error)
        }
    }

    private suspend fun recoverFailedApplyOrResume(original: Throwable) {
        if (journal.exists) rollbackOrEscalate(original) else resumeUnchangedStateOrEscalate(original)
    }

    private suspend fun resumeUnchangedStateOrEscalate(original: Throwable) = withContext(NonCancellable) {
        try {
            resumeRuntimesSafely()
        } catch (recovery: Exception) {
            recovery.addSuppressed(original)
            throw RestoreRecoveryRequiredException(
                "Los datos no cambiaron, pero falta reactivar avisos y Widgets.",
                recovery,
            )
        }
    }

    private suspend fun resumeRuntimesSafely() {
        try {
            resumeRuntimes()
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                runCatching { pauseRuntimes() }.exceptionOrNull()?.let { pauseFailure ->
                    error.addSuppressed(pauseFailure)
                }
            }
            throw error
        }
    }

    private suspend fun rollbackFromJournal() {
        if (!journal.canRollback) {
            throw RestoreRecoveryRequiredException(
                "El journal no conserva un estado anterior recuperable.",
                InvalidBackupException("Falta el estado anterior."),
            )
        }
        journal.openOld(snapshotMemoryLimitBytes).use { previous ->
            applyState(previous.payload.database, previous.payload.preferences, previous.photoDirectory)
            verifyState(previous)
        }
        journal.writePhase(RestoreJournalPhase.COMMITTED)
        journal.cleanup()
    }

    private suspend fun applyState(
        database: BackupDatabaseSnapshot,
        portablePreferences: List<BackupPreference>,
        photoDirectory: File,
    ) {
        localDataStore.backups.replace(database)
        faultInjector.hit(RestoreCheckpoint.AFTER_DATABASE)
        preferences.replace(portablePreferences, database)
        faultInjector.hit(RestoreCheckpoint.AFTER_PREFERENCES)
        photoFiles.replaceFrom(database, photoDirectory)
        faultInjector.hit(RestoreCheckpoint.AFTER_PHOTOS)
    }

    private suspend fun verifyState(extracted: ExtractedBackup): PortableSettings = verifyState(
        extracted.payload.database,
        extracted.payload.preferences,
        extracted.payload.photos.associate { it.storageKey to it.sha256 },
    )

    private suspend fun verifyState(
        expectedDatabase: BackupDatabaseSnapshot,
        expectedPreferences: List<BackupPreference>,
        expectedPhotoHashes: Map<String, String>,
    ): PortableSettings {
        localDataStore.backups.verifyLiveAfterReopen(expectedDatabase, snapshotMemoryLimitBytes)
        val actualPreferences = preferences.capture(expectedDatabase)
        if (actualPreferences != expectedPreferences) {
            throw InvalidBackupException("Las preferencias reabiertas no coinciden con la restauración.")
        }
        photoFiles.verify(expectedDatabase, expectedPhotoHashes)
        return preferences.decode(actualPreferences, expectedDatabase)
    }

    private suspend fun validateExtractedPhotos(extracted: ExtractedBackup) {
        val temporary = File(privateStaging, "validated-photos-${UUID.randomUUID()}")
        var validationFailure: Throwable? = null
        try {
            photoFiles.materializeDesired(
                extracted.payload.database,
                extracted.photoDirectory,
                temporary,
            )
        } catch (error: Throwable) {
            validationFailure = error
            throw error
        } finally {
            if (validationFailure != null) {
                temporary.addPrivateTreeCleanupFailure(
                    validationFailure,
                    "las fotografías temporales de validación",
                )
            } else {
                temporary.deletePrivateTreeChecked("las fotografías temporales de validación")
            }
        }
    }

    private fun cleanupCommittedArtifacts() {
        listOf(ReportPhotoStager.STAGING_DIRECTORY, ReportArtifactStore.ARTIFACT_DIRECTORY).forEach { relativePath ->
            val reportDirectory = File(applicationContext.filesDir, relativePath)
            if (reportDirectory.exists() && !reportDirectory.deleteRecursively()) {
                throw IOException("No se pudieron retirar los informes privados anteriores.")
            }
        }
        journal.cleanup()
    }

    private fun suggestedName(createdAtEpochMillis: Long): String {
        val local = java.time.Instant.ofEpochMilli(createdAtEpochMillis).atZone(zoneId)
        return "MiGuardia_copia_${SUGGESTED_NAME_FORMAT.format(local)}${MiGuardiaBackupContract.FILE_EXTENSION}"
    }

    private fun ensureInternalSpace(
        database: BackupDatabaseSnapshot?,
        photoBytes: Long,
        multiplier: Int,
    ) {
        val logicalEstimate = database?.let(::encodedDatabaseBytes) ?: 0L
        val required = runCatching {
            Math.addExact(Math.multiplyExact(photoBytes + logicalEstimate, multiplier.toLong()), SPACE_MARGIN_BYTES)
        }.getOrElse { Long.MAX_VALUE }
        val storage = applicationContext.getSystemService(StorageManager::class.java)
        if (storage.getAllocatableBytes(StorageManager.UUID_DEFAULT) < required) {
            throw IOException("No hay espacio interno suficiente para completar la operación de forma recuperable.")
        }
    }

    private fun encodedDatabaseBytes(database: BackupDatabaseSnapshot): Long {
        val output = CountingOutputStream()
        BackupPayloadCodec.writeDatabase(database, output)
        return output.bytesWritten
    }

    private fun photoBytes(snapshot: BackupDatabaseSnapshot): Long {
        val table = snapshot.table("schedule_photos")
        val byteSizeIndex = table.columns.indexOf("byteSize")
        return table.records.sumOf { record ->
            (record.values[byteSizeIndex] as? BackupValue.Integer)?.value
                ?: throw InvalidBackupException("Una fotografía no declara un tamaño válido.")
        }
    }

    private fun BackupDatabaseSnapshot.withPhotoChoice(includePhotos: Boolean): BackupDatabaseSnapshot =
        if (includePhotos) this else localDataStore.backups.withoutSchedulePhotos(this)

    private fun historicalSectors(snapshot: BackupDatabaseSnapshot): List<String> = snapshot.tables
        .flatMap { table ->
            val sectorIndex = table.columns.indexOf("sector")
            if (sectorIndex < 0) emptyList() else table.records.mapNotNull { record ->
                (record.values[sectorIndex] as? BackupValue.Text)?.value
            }
        }
        .distinct()
        .sorted()

    private fun BackupDatabaseSnapshot.recordsMissingOrChangedIn(
        replacement: BackupDatabaseSnapshot,
    ): Int = MiGuardiaBackupSchemaV5.tables.sumOf { spec ->
        val replacementByKey = replacement.table(spec.name).records.associateBy { record ->
            record.backupKey(spec)
        }
        table(spec.name).records.count { current ->
            replacementByKey[current.backupKey(spec)] != current
        }
    }

    private fun requireContentUri(uri: Uri) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            throw InvalidBackupException("El documento elegido no es un URI de contenido válido.")
        }
    }

    private suspend fun java.io.InputStream.copyBoundedTo(output: FileOutputStream, limit: Long): Long {
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw InvalidBackupException("La copia supera el límite seguro.")
            output.write(buffer, 0, count)
        }
        return total
    }

    private fun java.io.InputStream.readExactly(target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val count = read(target, offset, target.size - offset)
            if (count < 0) throw IOException("La copia preparada tiene una cabecera truncada.")
            offset += count
        }
    }

    private fun java.io.InputStream.skipExactly(bytes: Long) {
        var remaining = bytes
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (remaining > 0L) {
            val count = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) throw IOException("La copia preparada tiene una cabecera truncada.")
            remaining -= count
        }
    }

    private suspend fun java.io.InputStream.sha256Bounded(limit: Long): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = read(buffer)
            if (count < 0) break
            total = Math.addExact(total, count.toLong())
            if (total > limit) throw InvalidBackupException("La copia supera el límite seguro.")
            digest.update(buffer, 0, count)
        }
        return total to digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val REPLACE_CONFIRMATION = "Reemplazar todo"
        private val SUGGESTED_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
        private const val SPACE_MARGIN_BYTES = 16L * 1024L * 1024L
        private const val MAX_CAPTURE_ATTEMPTS = 2
    }

    private class CountingOutputStream : OutputStream() {
        var bytesWritten: Long = 0L
            private set

        override fun write(value: Int) {
            bytesWritten = Math.addExact(bytesWritten, 1L)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            bytesWritten = Math.addExact(bytesWritten, length.toLong())
        }
    }
}
