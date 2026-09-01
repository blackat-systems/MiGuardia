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
import kotlinx.coroutines.withContext

data class StoredPhoto(val storageKey: String, val mimeType: String, val byteSize: Long, val width: Int, val height: Int)

class SchedulePhotoFileStore(private val context: Context) {
    private val root get() = File(context.filesDir, "schedule_photos")

    suspend fun import(
        uri: Uri,
        ownerId: UUID,
        versioned: Boolean = false,
        replacingStorageKey: String? = null,
    ): StoredPhoto = withContext(Dispatchers.IO) {
        if (!root.exists() && !root.mkdirs()) throw IOException("No se pudo preparar el almacenamiento de fotos")
        val storageManager = context.getSystemService(StorageManager::class.java)
        if (storageManager.getAllocatableBytes(StorageManager.UUID_DEFAULT) <= 0L) {
            throw IOException("Sin espacio disponible")
        }
        val mime = context.contentResolver.getType(uri)?.lowercase()?.takeIf { it in ALLOWED_MIME_TYPES }
            ?: throw InvalidLocalDataException("El archivo seleccionado no es una imagen compatible.")
        val extension = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        val version = if (versioned) "_${UUID.randomUUID().toString().take(8)}" else ""
        val key = "$ownerId$version.$extension"
        val temp = File(root, "$key.tmp")
        val target = File(root, key)
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

    fun file(storageKey: String): File = File(root, storageKey).also {
        require(it.parentFile?.canonicalFile == root.canonicalFile) { "Referencia de archivo inválida" }
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

    private companion object {
        const val UUID_TEXT_LENGTH = 36
        const val MAX_IMAGE_DIMENSION = 65_535
        val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        val STORAGE_KEY_PATTERN = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}" +
                "(?:_[0-9a-fA-F]{8})?\\.[a-z0-9]{2,8}",
        )
    }
}
