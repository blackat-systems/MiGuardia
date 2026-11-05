package com.blackatsystems.miguardia.ui.photos

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.storage.StorageManager
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupContract
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class StoredPhoto(val storageKey: String, val mimeType: String, val byteSize: Long, val width: Int, val height: Int)

class SchedulePhotoFileStore internal constructor(
    private val context: Context,
    private val draftOnly: Boolean,
    private val allocatableBytes: () -> Long,
) {
    constructor(context: Context, draftOnly: Boolean = false) : this(context, draftOnly, {
        context.getSystemService(StorageManager::class.java).getAllocatableBytes(StorageManager.UUID_DEFAULT)
    })
    private val root get() = File(context.filesDir, if (draftOnly) "manual_load_drafts" else "schedule_photos")

    /** Each resulting note owns its file: editing/deleting another note cannot break it. */
    suspend fun copyNote(source: File, metadata: StoredPhoto, id: UUID): StoredPhoto = withContext(Dispatchers.IO) {
        require(metadata.mimeType in ALLOWED_MIME_TYPES)
        require(source.isFile && source.length() == metadata.byteSize)
        if (!root.exists() && !root.mkdirs()) throw IOException("No se pudo preparar el almacenamiento")
        val extension = when (metadata.mimeType) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        val key = "note_$id.$extension"
        val target = file(key)
        val temp = file("$key.tmp")
        if (target.exists()) {
            fun digest(file: File): ByteArray {
                val hash = java.security.MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) { val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
                }
                return hash.digest()
            }
            require(digest(source).contentEquals(digest(target))) { "La imagen del reintento cambió." }
            return@withContext metadata.copy(storageKey = key)
        }
        val retained = root.listFiles().orEmpty().filter { it.isFile && STORAGE_KEY_PATTERN.matches(it.name) }
        require(retained.size < MiGuardiaBackupContract.MAX_PHOTO_COUNT) { "Alcanzaste el límite seguro de fotografías." }
        require(metadata.byteSize <= MiGuardiaBackupContract.MAX_SINGLE_PHOTO_BYTES &&
            retained.sumOf { it.length() } + metadata.byteSize <= MiGuardiaBackupContract.MAX_ALL_PHOTOS_BYTES) {
            "Las fotografías superan los límites seguros de MiGuardia."
        }
        try {
            source.inputStream().use { input -> FileOutputStream(temp).use { output -> input.copyTo(output); output.fd.sync() } }
            check(temp.length() == metadata.byteSize)
            check(temp.renameTo(target)) { "No se pudo confirmar la imagen privada." }
            metadata.copy(storageKey = key)
        } catch (error: Exception) { temp.delete(); throw error }
    }

    suspend fun import(
        uri: Uri,
        ownerId: UUID,
        versioned: Boolean = false,
        replacingStorageKey: String? = null,
        namespace: String? = null,
    ): StoredPhoto {
        com.blackatsystems.miguardia.QaInstrumentationScope.requireExternalAction(context, "photos.import")
        var ownedFiles: Pair<File, File>? = null
        try {
            return withContext(Dispatchers.IO) {
                if (!root.exists() && !root.mkdirs()) throw IOException("No se pudo preparar el almacenamiento de fotos")
                if (allocatableBytes() <= 0L) {
                    throw IOException("Sin espacio disponible")
                }
                val mime = context.contentResolver.getType(uri)?.lowercase()?.takeIf { it in ALLOWED_MIME_TYPES }
                    ?: throw InvalidLocalDataException("El archivo seleccionado no es una imagen compatible.")
                val extension = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
                val version = if (versioned) "_${UUID.randomUUID().toString().take(8)}" else ""
                require(namespace == null || namespace in setOf(NOTE_NAMESPACE, CANCEL_NAMESPACE))
                val prefix = namespace?.let { "${it}_" }.orEmpty()
                val key = "$prefix$ownerId$version.$extension"
                val temp = File(root, "$key.tmp")
                val target = File(root, key)
                // Import only creates a new identity; replacements use a distinct versioned key.
                // A rejected retry must never overwrite or remove an already confirmed image.
                if (target.exists() || temp.exists()) throw IOException("La imagen ya existe. Volvé a seleccionarla.")
                ownedFiles = temp to target
                try {
                    val retained = root.listFiles().orEmpty().filter { candidate ->
                        candidate.isFile && STORAGE_KEY_PATTERN.matches(candidate.name) &&
                            candidate.name != replacingStorageKey && candidate.name != key
                    }
                    if (retained.size >= MiGuardiaBackupContract.MAX_PHOTO_COUNT) {
                        throw InvalidLocalDataException("Alcanzaste el límite seguro de fotografías.")
                    }
                    val retainedBytes = runCatching {
                        retained.fold(0L) { total, file -> Math.addExact(total, file.length()) }
                    }.getOrElse {
                        throw InvalidLocalDataException("El almacenamiento de fotografías supera los límites seguros.")
                    }
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var copied = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                copied = Math.addExact(copied, count.toLong())
                                val totalBytes = runCatching { Math.addExact(retainedBytes, copied) }
                                    .getOrElse {
                                        throw InvalidLocalDataException(
                                            "El almacenamiento de fotografías supera los límites seguros.",
                                        )
                                    }
                                if (copied > MiGuardiaBackupContract.MAX_SINGLE_PHOTO_BYTES ||
                                    totalBytes > MiGuardiaBackupContract.MAX_ALL_PHOTOS_BYTES
                                ) {
                                    throw InvalidLocalDataException("La fotografía supera los límites seguros de MiGuardia.")
                                }
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                    } ?: throw IOException("No se pudo abrir la imagen")
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(temp.absolutePath, options)
                    val decodedMime = options.outMimeType?.lowercase()
                    if (options.outWidth !in 1..MAX_IMAGE_DIMENSION || options.outHeight !in 1..MAX_IMAGE_DIMENSION ||
                        temp.length() <= 0 || decodedMime != mime
                    ) {
                        throw InvalidLocalDataException("La imagen seleccionada no se puede leer.")
                    }
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = false)
                        temp.delete()
                    }
                    StoredPhoto(key, mime, target.length(), options.outWidth, options.outHeight)
                } catch (error: Exception) {
                    temp.delete(); target.delete(); throw error
                }
            }
        } catch (error: Exception) {
            // withContext may cancel while returning the completed IO result, before the
            // caller can record its key. Retire only the files created by this import.
            withContext(NonCancellable + Dispatchers.IO) {
                ownedFiles?.let { (temp, target) -> temp.delete(); target.delete() }
            }
            throw error
        }
    }

    fun file(storageKey: String): File = File(root, storageKey).also {
        require(it.parentFile?.canonicalFile == root.canonicalFile) { "Referencia de archivo inválida" }
    }

    suspend fun discardUnreferenced(storageKey: String) = withContext(Dispatchers.IO) {
        val candidate = file(storageKey)
        if (candidate.exists() && !candidate.delete()) {
            throw IOException("No se pudo retirar una imagen sin guardar")
        }
    }

    suspend fun reconcileNoteAttachments(expectedStorageKeys: Set<String>) = withContext(Dispatchers.IO) {
        root.listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith("${NOTE_NAMESPACE}_") }
            .forEach { candidate ->
                val stableKey = candidate.name.removeSuffix(".tmp").removeSuffix(".trash")
                if (candidate.name.endsWith(".tmp") || stableKey !in expectedStorageKeys) {
                    candidate.delete()
                } else if (candidate.name.endsWith(".trash")) {
                    candidate.renameTo(File(root, stableKey))
                }
            }
    }

    suspend fun reconcileCancellationEvidence(expectedStorageKeys: Set<String>) = withContext(Dispatchers.IO) {
        root.listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith("${CANCEL_NAMESPACE}_") }
            .forEach { candidate ->
                val stableKey = candidate.name.removeSuffix(".tmp").removeSuffix(".trash")
                when {
                    candidate.name.endsWith(".trash") && stableKey in expectedStorageKeys ->
                        candidate.renameTo(File(root, stableKey))
                    candidate.name.endsWith(".tmp") || stableKey !in expectedStorageKeys -> candidate.delete()
                }
            }
    }

    suspend fun removeRecoverably(storageKey: String, deleteMetadata: suspend () -> Unit) = withContext(Dispatchers.IO) {
        val original = file(storageKey)
        val trash = File(root, "$storageKey.trash")
        if (original.exists() && !original.renameTo(trash)) throw IOException("No se pudo preparar la eliminación")
        try {
            deleteMetadata()
            trash.delete()
        } catch (error: Exception) {
            if (trash.exists()) trash.renameTo(original)
            throw error
        }
    }

    suspend fun reconcile(expectedStorageKey: suspend (UUID) -> String?) = withContext(Dispatchers.IO) {
        root.listFiles()?.forEach { candidate ->
            // Note/evidence owners must recover their own trash before deciding whether
            // to delete it. The monthly-photo repository cannot resolve these identities.
            if (candidate.name.startsWith("${NOTE_NAMESPACE}_") || candidate.name.startsWith("${CANCEL_NAMESPACE}_")) return@forEach
            when {
                candidate.name.endsWith(".tmp") -> candidate.delete()
                candidate.name.endsWith(".trash") -> {
                    val originalName = candidate.name.removeSuffix(".trash")
                    val ownerId = originalName.ownerIdOrNull()
                    if (ownerId != null && expectedStorageKey(ownerId) == originalName) {
                        candidate.renameTo(File(root, originalName))
                    } else {
                        candidate.delete()
                    }
                }
                candidate.name.matches(STORAGE_KEY_PATTERN) -> {
                    val ownerId = candidate.name.ownerIdOrNull()
                    if (ownerId == null || expectedStorageKey(ownerId) != candidate.name) candidate.delete()
                }
            }
        }
    }

    private fun String.ownerIdOrNull(): UUID? =
        takeIf { length >= UUID_TEXT_LENGTH }
            ?.take(UUID_TEXT_LENGTH)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    companion object {
        const val UUID_TEXT_LENGTH = 36
        const val MAX_IMAGE_DIMENSION = 65_535
        internal const val NOTE_NAMESPACE = "note"
        const val CANCEL_NAMESPACE = "cancel"
        val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        val STORAGE_KEY_PATTERN = Regex(
            "(?:(?:note|cancel)_)?[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}" +
                "(?:_[0-9a-fA-F]{8})?\\.[a-z0-9]{2,8}",
        )
    }
}
