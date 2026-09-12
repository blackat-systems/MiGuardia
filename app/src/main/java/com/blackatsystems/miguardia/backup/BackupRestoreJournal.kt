package com.blackatsystems.miguardia.backup

import android.content.Context
import com.blackatsystems.miguardia.core.domain.backup.BackupContainer
import com.blackatsystems.miguardia.core.domain.backup.BackupDatabaseSnapshot
import com.blackatsystems.miguardia.core.domain.backup.BackupMemoryBudget
import com.blackatsystems.miguardia.core.domain.backup.BackupPhotoAsset
import com.blackatsystems.miguardia.core.domain.backup.BackupPhotoMode
import com.blackatsystems.miguardia.core.domain.backup.BackupPreference
import com.blackatsystems.miguardia.core.domain.backup.ExtractedBackup
import com.blackatsystems.miguardia.core.domain.backup.InvalidBackupException
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.ZoneId
import java.util.UUID

enum class RestoreJournalPhase {
    PREPARED,
    SWAPPED,
    VERIFIED,
    COMMITTED,
}

class BackupRestoreJournal(
    context: Context,
    private val deleteDirectory: (File) -> Boolean = File::deleteRecursively,
) {
    private val applicationContext = context.applicationContext
    private val root = File(applicationContext.noBackupFilesDir, JOURNAL_DIRECTORY)
    private val preparingRoot = File(applicationContext.noBackupFilesDir, "$JOURNAL_DIRECTORY.preparing")
    private val working = File(applicationContext.filesDir, "backups/staging")
    private val oldContainer = File(root, OLD_CONTAINER)
    private val newContainer = File(root, NEW_CONTAINER)
    private val phaseFile = File(root, PHASE_FILE)

    val exists: Boolean get() = root.exists()
    val canRollback: Boolean get() = oldContainer.isFile
    val canVerifyNew: Boolean get() = newContainer.isFile

    fun prepare(
        oldDatabase: BackupDatabaseSnapshot,
        oldPreferences: List<BackupPreference>,
        oldPhotos: List<BackupPhotoAsset>,
        newDatabase: BackupDatabaseSnapshot,
        newPreferences: List<BackupPreference>,
        newPhotos: List<BackupPhotoAsset>,
        createdAtEpochMillis: Long,
        zoneId: ZoneId,
    ) {
        if (root.exists()) {
            throw IOException("Existe una recuperación anterior pendiente y no se puede reemplazar su journal.")
        }
        cleanupInterruptedPreparation()
        if (!preparingRoot.mkdirs()) throw IOException("No se pudo preparar el journal de restauración.")
        val preparingOld = File(preparingRoot, OLD_CONTAINER)
        val preparingNew = File(preparingRoot, NEW_CONTAINER)
        try {
            BackupContainer.create(
                target = preparingOld,
                workingDirectory = working,
                backupId = UUID.randomUUID(),
                createdAtEpochMillis = createdAtEpochMillis,
                zoneId = zoneId,
                database = oldDatabase,
                preferences = oldPreferences,
                photoMode = BackupPhotoMode.INCLUDED,
                photoAssets = oldPhotos,
                password = null,
            )
            BackupContainer.create(
                target = preparingNew,
                workingDirectory = working,
                backupId = UUID.randomUUID(),
                createdAtEpochMillis = createdAtEpochMillis,
                zoneId = zoneId,
                database = newDatabase,
                preferences = newPreferences,
                photoMode = BackupPhotoMode.INCLUDED,
                photoAssets = newPhotos,
                password = null,
            )
            writePhase(preparingRoot, RestoreJournalPhase.PREPARED)
            movePreparedJournal(preparingRoot, root)
        } catch (error: Exception) {
            if (preparingRoot.exists() && !preparingRoot.deleteRecursively()) {
                error.addSuppressed(IOException("No se pudo retirar la preparación incompleta del journal."))
            }
            throw error
        }
    }

    fun phaseOrNull(): RestoreJournalPhase? = try {
        if (!phaseFile.isFile) return null
        DataInputStream(FileInputStream(phaseFile).buffered()).use { input ->
            if (input.readInt() != JOURNAL_MAGIC || input.readInt() != JOURNAL_VERSION) return null
            val ordinal = input.readInt()
            if (input.read() != -1) return null
            RestoreJournalPhase.entries.getOrNull(ordinal)
        }
    } catch (_: Exception) {
        null
    }

    fun writePhase(phase: RestoreJournalPhase) {
        writePhase(root, phase)
    }

    fun cleanupInterruptedPreparation() {
        if (preparingRoot.exists() && !deleteDirectory(preparingRoot)) {
            throw IOException("No se pudo retirar una preparación incompleta del journal.")
        }
        sweepCleanupTombstones()
    }

    private fun writePhase(directory: File, phase: RestoreJournalPhase) {
        directory.mkdirs()
        val destination = File(directory, PHASE_FILE)
        val temporary = File(directory, "$PHASE_FILE.tmp")
        FileOutputStream(temporary).use { raw ->
            val output = DataOutputStream(raw.buffered())
            output.writeInt(JOURNAL_MAGIC)
            output.writeInt(JOURNAL_VERSION)
            output.writeInt(phase.ordinal)
            output.flush()
            raw.fd.sync()
        }
        moveReplacing(temporary, destination)
    }

    fun openOld(
        decodedMemoryLimitBytes: Long = BackupMemoryBudget.operationalHeapBytes(),
    ): ExtractedBackup {
        if (!oldContainer.isFile) throw InvalidBackupException("No está disponible el estado anterior del journal.")
        return BackupContainer.extract(oldContainer, working, null, decodedMemoryLimitBytes)
    }

    fun openNew(
        decodedMemoryLimitBytes: Long = BackupMemoryBudget.operationalHeapBytes(),
    ): ExtractedBackup {
        if (!newContainer.isFile) throw InvalidBackupException("No está disponible el estado nuevo del journal.")
        return BackupContainer.extract(newContainer, working, null, decodedMemoryLimitBytes)
    }

    fun cleanup() {
        if (root.exists()) {
            val tombstone = File(
                applicationContext.noBackupFilesDir,
                "$CLEANUP_TOMBSTONE_PREFIX${UUID.randomUUID()}",
            )
            moveActiveJournalToTombstone(root, tombstone)
            if (!deleteDirectory(tombstone)) {
                throw IOException("No se pudo retirar el journal ya cerrado de forma segura.")
            }
        }
        cleanupInterruptedPreparation()
    }

    /**
     * Once the active journal is renamed, a process death can only leave an inert tombstone.
     * Startup may delete it opportunistically without mistaking it for a rollback request.
     */
    private fun sweepCleanupTombstones() {
        applicationContext.noBackupFilesDir.listFiles().orEmpty()
            .filter(::isCleanupTombstone)
            .forEach { tombstone ->
                if (!deleteDirectory(tombstone)) {
                    throw IOException("No se pudo retirar un journal cerrado pendiente.")
                }
            }
    }

    private fun isCleanupTombstone(file: File): Boolean {
        if (!file.name.startsWith(CLEANUP_TOMBSTONE_PREFIX)) return false
        val suffix = file.name.removePrefix(CLEANUP_TOMBSTONE_PREFIX)
        val parsed = runCatching { UUID.fromString(suffix) }.getOrNull() ?: return false
        return parsed.toString().equals(suffix, ignoreCase = true)
    }

    private fun moveActiveJournalToTombstone(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            if (!source.renameTo(target)) {
                throw IOException("No se pudo cerrar atómicamente el journal de restauración.")
            }
        }
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun movePreparedJournal(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath())
        }
    }

    private companion object {
        const val JOURNAL_DIRECTORY = "miguardia_backup_restore"
        const val CLEANUP_TOMBSTONE_PREFIX = "$JOURNAL_DIRECTORY.cleanup-"
        const val OLD_CONTAINER = "previous.miguardia-backup"
        const val NEW_CONTAINER = "desired.miguardia-backup"
        const val PHASE_FILE = "restore.phase"
        const val JOURNAL_MAGIC = 0x4D47524A
        const val JOURNAL_VERSION = 1
    }
}
