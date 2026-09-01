package com.blackatsystems.miguardia.backup

import android.content.Context
import android.graphics.BitmapFactory
import android.os.storage.StorageManager
import com.blackatsystems.miguardia.core.domain.backup.BackupDatabaseSnapshot
import com.blackatsystems.miguardia.core.domain.backup.BackupPhotoAsset
import com.blackatsystems.miguardia.core.domain.backup.BackupPhotoMetadata
import com.blackatsystems.miguardia.core.domain.backup.BackupRecord
import com.blackatsystems.miguardia.core.domain.backup.BackupValue
import com.blackatsystems.miguardia.core.domain.backup.InvalidBackupException
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupContract
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupPhotoFiles(context: Context) {
    private val applicationContext = context.applicationContext
    private val root = File(applicationContext.filesDir, PHOTO_DIRECTORY)

    suspend fun assetsForCurrentSnapshot(snapshot: BackupDatabaseSnapshot): List<BackupPhotoAsset> =
        withContext(Dispatchers.IO) {
            snapshot.photoRows().map { photo ->
                val file = safePhotoFile(root, photo.storageKey)
                requireMatchingFile(file, photo)
                BackupPhotoAsset(photo.copy(sha256 = file.sha256()), file)
            }
        }

    suspend fun materializeDesired(
        snapshot: BackupDatabaseSnapshot,
        incomingPhotoDirectory: File?,
        destination: File,
        currentSnapshot: BackupDatabaseSnapshot? = null,
        incomingSnapshot: BackupDatabaseSnapshot? = null,
    ): List<BackupPhotoAsset> = withContext(Dispatchers.IO) {
        if ((currentSnapshot == null) != (incomingSnapshot == null)) {
            throw InvalidBackupException("Falta la procedencia completa de las fotografías restauradas.")
        }
        destination.deletePrivateTreeChecked("una preparación anterior de fotografías")
        if (!destination.mkdirs()) throw IOException("No se pudo preparar las fotografías restauradas.")
        val rows = snapshot.photoRows()
        val desiredRecords = snapshot.photoRecordsByStorageKey()
        val currentRecords = currentSnapshot?.photoRecordsByStorageKey()
        val incomingRecords = incomingSnapshot?.photoRecordsByStorageKey()
        ensureSpace(rows.sumOf(BackupPhotoMetadata::byteSize))
        rows.map { photo ->
            val desiredRecord = desiredRecords.getValue(photo.storageKey)
            val current = safePhotoFile(root, photo.storageKey)
                .takeIf {
                    currentRecords == null || currentRecords[photo.storageKey] == desiredRecord
                }
                ?.takeIf { candidate -> runCatching { requireMatchingFile(candidate, photo) }.isSuccess }
            val incoming = incomingPhotoDirectory?.let { safePhotoFile(it, photo.storageKey) }
                ?.takeIf {
                    incomingRecords == null || incomingRecords[photo.storageKey] == desiredRecord
                }
                ?.takeIf { candidate -> runCatching { requireMatchingFile(candidate, photo) }.isSuccess }
            val source = (if (currentRecords == null) incoming ?: current else current ?: incoming)
                ?: throw InvalidBackupException("No está disponible el archivo de la foto ${photo.storageKey}.")
            val target = safePhotoFile(destination, photo.storageKey)
            copyDurably(source, target)
            requireMatchingFile(target, photo)
            BackupPhotoAsset(photo.copy(sha256 = target.sha256()), target)
        }
    }

    suspend fun replaceFrom(
        snapshot: BackupDatabaseSnapshot,
        sourceDirectory: File?,
    ) = withContext(Dispatchers.IO) {
        val peer = File(applicationContext.filesDir, "$PHOTO_DIRECTORY.restore-new-${UUID.randomUUID()}")
        val previous = File(applicationContext.filesDir, "$PHOTO_DIRECTORY.restore-previous")
        var replacementFailure: Throwable? = null
        try {
            materializeDesired(snapshot, sourceDirectory, peer)
            if (!root.exists() && previous.exists() && !previous.renameTo(root)) {
                throw IOException("No se pudo recuperar el directorio de fotografías anterior.")
            }
            if (previous.exists() && !previous.deleteRecursively()) {
                throw IOException("No se pudo limpiar una restauración de fotografías anterior.")
            }
            if (root.exists() && !root.renameTo(previous)) {
                throw IOException("No se pudo preparar el reemplazo de fotografías.")
            }
            if (!peer.renameTo(root)) {
                if (previous.exists()) previous.renameTo(root)
                throw IOException("No se pudo activar el nuevo conjunto de fotografías.")
            }
            if (previous.exists() && !previous.deleteRecursively()) {
                throw IOException("Las fotografías se restauraron, pero no se pudo retirar el conjunto anterior.")
            }
        } catch (error: Throwable) {
            replacementFailure = error
            throw error
        } finally {
            if (replacementFailure != null) {
                peer.addPrivateTreeCleanupFailure(
                    replacementFailure,
                    "la preparación incompleta de fotografías",
                )
            } else {
                peer.deletePrivateTreeChecked("la preparación de fotografías ya aplicada")
            }
        }
    }

    suspend fun verify(
        snapshot: BackupDatabaseSnapshot,
        expectedSha256ByStorageKey: Map<String, String>? = null,
    ) = withContext(Dispatchers.IO) {
        val expected = snapshot.photoRows()
        val expectedKeys = expected.mapTo(linkedSetOf(), BackupPhotoMetadata::storageKey)
        val actualKeys = root.listFiles().orEmpty()
            .filter(File::isFile)
            .mapTo(linkedSetOf(), File::getName)
        if (actualKeys != expectedKeys) {
            throw InvalidBackupException("El directorio privado de fotografías no coincide con Room.")
        }
        if (expectedSha256ByStorageKey != null && expectedSha256ByStorageKey.keys != expectedKeys) {
            throw InvalidBackupException("Faltan huellas esperadas para verificar las fotografías restauradas.")
        }
        expected.forEach { photo ->
            val file = safePhotoFile(root, photo.storageKey)
            requireMatchingFile(file, photo)
            expectedSha256ByStorageKey?.getValue(photo.storageKey)?.let { expectedHash ->
                if (file.sha256() != expectedHash) {
                    throw InvalidBackupException("Una fotografía restaurada no coincide con su huella autenticada.")
                }
            }
        }
    }

    fun cleanupInterruptedSwap() {
        val previous = File(applicationContext.filesDir, "$PHOTO_DIRECTORY.restore-previous")
        if (!root.exists() && previous.exists() && !previous.renameTo(root)) {
            throw IOException("No se pudo recuperar el conjunto anterior de fotografías.")
        }
        if (root.exists() && previous.exists() && !previous.deleteRecursively()) {
            throw IOException("No se pudo retirar el conjunto anterior de fotografías.")
        }
        applicationContext.filesDir.listFiles().orEmpty()
            .filter { it.name.startsWith("$PHOTO_DIRECTORY.restore-new-") }
            .forEach { candidate ->
                if (!candidate.deleteRecursively()) {
                    throw IOException("No se pudo retirar una preparación incompleta de fotografías.")
                }
            }
    }

    private fun BackupDatabaseSnapshot.photoRows(): List<BackupPhotoMetadata> {
        val table = table("schedule_photos")
        return table.records.map { row -> row.toPhotoMetadata(table.columns) }.sortedBy {
            it.storageKey
        }
    }

    private fun BackupDatabaseSnapshot.photoRecordsByStorageKey(): Map<String, BackupRecord> {
        val table = table("schedule_photos")
        val storageKeyIndex = table.columns.indexOf("storageKey")
        return table.records.associateBy { record ->
            (record.values[storageKeyIndex] as? BackupValue.Text)?.value
                ?: throw InvalidBackupException("Un registro de fotografía no tiene storageKey.")
        }
    }

    private fun BackupRecord.toPhotoMetadata(columns: List<String>): BackupPhotoMetadata {
        fun text(name: String): String = (values[columns.indexOf(name)] as? BackupValue.Text)?.value
            ?: throw InvalidBackupException("Un registro de fotografía no tiene $name.")
        fun integer(name: String): Long = (values[columns.indexOf(name)] as? BackupValue.Integer)?.value
            ?: throw InvalidBackupException("Un registro de fotografía no tiene $name.")
        return BackupPhotoMetadata(
            recordId = text("id"),
            storageKey = text("storageKey"),
            mimeType = text("mimeType"),
            byteSize = integer("byteSize"),
            pixelWidth = integer("pixelWidth").toIntExact("ancho"),
            pixelHeight = integer("pixelHeight").toIntExact("alto"),
            sha256 = ZERO_SHA256,
        )
    }

    private fun requireMatchingFile(file: File, metadata: BackupPhotoMetadata) {
        if (!file.isFile || file.length() != metadata.byteSize ||
            file.length() !in 1..MiGuardiaBackupContract.MAX_SINGLE_PHOTO_BYTES
        ) {
            throw InvalidBackupException("Una fotografía privada no coincide con su tamaño registrado.")
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth != metadata.pixelWidth || options.outHeight != metadata.pixelHeight ||
            options.outWidth <= 0 || options.outHeight <= 0
        ) {
            throw InvalidBackupException("Una fotografía privada no coincide con sus dimensiones registradas.")
        }
        val decodedMime = options.outMimeType
        if (decodedMime != metadata.mimeType &&
            !(metadata.mimeType == "image/jpeg" && decodedMime == "image/jpg")
        ) {
            throw InvalidBackupException("Una fotografía privada no coincide con su tipo registrado.")
        }
    }

    private fun safePhotoFile(directory: File, storageKey: String): File {
        if (!SAFE_STORAGE_KEY.matches(storageKey)) {
            throw InvalidBackupException("Una fotografía contiene una referencia de archivo inválida.")
        }
        val canonicalDirectory = directory.canonicalFile
        val candidate = File(directory, storageKey).canonicalFile
        if (candidate.parentFile != canonicalDirectory) {
            throw InvalidBackupException("Una fotografía intenta salir del directorio privado.")
        }
        return candidate
    }

    private fun ensureSpace(requiredBytes: Long) {
        if (requiredBytes < 0L || requiredBytes > MiGuardiaBackupContract.MAX_ALL_PHOTOS_BYTES) {
            throw InvalidBackupException("Las fotografías superan el límite total seguro.")
        }
        val storage = applicationContext.getSystemService(StorageManager::class.java)
        val available = storage.getAllocatableBytes(StorageManager.UUID_DEFAULT)
        val requiredWithMargin = requiredBytes + SPACE_MARGIN_BYTES
        if (available < requiredWithMargin) {
            throw IOException("No hay espacio interno suficiente para restaurar con seguridad.")
        }
    }

    private fun copyDurably(source: File, target: File) {
        target.parentFile?.mkdirs()
        source.inputStream().buffered().use { input ->
            FileOutputStream(target).use { output ->
                val copied = input.copyTo(output)
                if (copied != source.length()) throw IOException("Una fotografía quedó truncada al copiarse.")
                output.fd.sync()
            }
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun Long.toIntExact(label: String): Int {
        if (this !in 1..Int.MAX_VALUE.toLong()) {
            throw InvalidBackupException("El $label de una fotografía es inválido.")
        }
        return toInt()
    }

    private companion object {
        const val PHOTO_DIRECTORY = "schedule_photos"
        const val SPACE_MARGIN_BYTES = 8L * 1024L * 1024L
        val ZERO_SHA256 = "0".repeat(64)
        val SAFE_STORAGE_KEY = Regex(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" +
                "(?:_[0-9a-f]{8})?\\.(?:jpg|jpeg|png|webp)",
        )
    }
}
