package com.blackatsystems.miguardia.reports

import android.content.Context
import android.graphics.BitmapFactory
import com.blackatsystems.miguardia.core.domain.model.SchedulePhoto
import com.blackatsystems.miguardia.core.domain.report.ReportPhotoRow
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ReportPhotoStager(
    context: Context,
    private val sourceStore: SchedulePhotoFileStore = SchedulePhotoFileStore(context),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val root = File(context.filesDir, STAGING_DIRECTORY)

    suspend fun freeze(
        photos: List<SchedulePhoto>,
        rows: List<ReportPhotoRow>,
    ): FrozenReportAssets = withContext(Dispatchers.IO) {
        require(photos.size == rows.size)
        require(photos.size <= 12)
        require(root.mkdirs() || root.isDirectory) { "No se pudo preparar el staging privado de Informes" }
        pruneExpiredSessions()
        if (photos.isEmpty()) return@withContext FrozenReportAssets.EMPTY
        val session = File(root, UUID.randomUUID().toString().replace("-", ""))
        require(session.mkdirs()) { "No se pudo crear el staging privado de esta sesión" }
        require(session.canonicalFile.parentFile == root.canonicalFile)
        registerActive(session)
        try {
            val frozen = photos.mapIndexed { index, metadata ->
                currentCoroutineContext().ensureActive()
                val source = sourceStore.file(metadata.storageKey)
                val target = File(session, "${UUID.randomUUID().toString().replace("-", "")}.img")
                copyAndVerify(source, target, metadata.byteSize)
                currentCoroutineContext().ensureActive()
                FrozenReportPhoto(
                    stableOrder = index,
                    file = target,
                    mimeType = metadata.mimeType,
                    caption = rows[index].caption,
                )
            }
            FrozenReportAssets(frozen, session)
        } catch (error: CancellationException) {
            deleteAndUnregister(session)
            throw error
        } catch (error: Exception) {
            deleteAndUnregister(session)
            throw when (error) {
                is ReportAssetException -> error
                else -> ReportAssetException(
                    "No pudimos congelar una foto elegida. Revisá que siga disponible y reintentá.",
                    error,
                )
            }
        }
    }

    suspend fun release(assets: FrozenReportAssets) = withContext(NonCancellable + Dispatchers.IO) {
        assets.stagingDirectory?.let(::deleteAndUnregister)
    }

    private fun copyAndVerify(source: File, target: File, expectedSize: Long) {
        if (!source.isFile || !source.canRead()) {
            throw ReportAssetException("Una foto elegida ya no se puede leer.")
        }
        val sourceLengthBefore = source.length()
        val sourceModifiedBefore = source.lastModified()
        if (sourceLengthBefore != expectedSize || expectedSize <= 0L) {
            throw ReportAssetException("Una foto elegida cambió desde que fue registrada.")
        }
        val copiedDigest = MessageDigest.getInstance("SHA-256")
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) {
                        copiedDigest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
                output.flush()
                output.fd.sync()
            }
        }
        if (
            source.length() != sourceLengthBefore ||
            source.lastModified() != sourceModifiedBefore ||
            target.length() != expectedSize
        ) {
            throw ReportAssetException("Una foto elegida cambió mientras se preparaba el informe.")
        }
        val sourceDigestAfter = digest(source)
        val targetDigest = digest(target)
        if (!copiedDigest.digest().contentEquals(sourceDigestAfter) || !sourceDigestAfter.contentEquals(targetDigest)) {
            throw ReportAssetException("No pudimos verificar la copia privada de una foto elegida.")
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(target.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw ReportAssetException("Una foto elegida no tiene una imagen legible.")
        }
    }

    private fun digest(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }

    private fun deleteStagingDirectory(directory: File) {
        val canonical = directory.canonicalFile
        if (canonical.parentFile != root.canonicalFile) {
            throw IOException("Directorio de staging fuera del alcance permitido")
        }
        canonical.walkBottomUp().forEach { candidate ->
            if (candidate.exists() && !candidate.delete()) {
                throw IOException("No se pudo limpiar el staging privado de Informes")
            }
        }
    }

    private fun pruneExpiredSessions() {
        val now = clock.millis()
        val canonicalRoot = root.canonicalFile
        root.listFiles().orEmpty().forEach { candidate ->
            if (!candidate.isDirectory || !SESSION_NAME.matches(candidate.name)) return@forEach
            val canonical = candidate.canonicalFile
            if (canonical.parentFile != canonicalRoot || isActive(canonical)) return@forEach
            val age = now - canonical.lastModified()
            if (age > STAGING_TTL.toMillis()) deleteStagingDirectory(canonical)
        }
    }

    private fun deleteAndUnregister(directory: File) {
        try {
            deleteStagingDirectory(directory)
        } finally {
            unregisterActive(directory)
        }
    }

    private fun registerActive(directory: File) = synchronized(ACTIVE_SESSIONS) {
        ACTIVE_SESSIONS += directory.canonicalPath
    }

    private fun unregisterActive(directory: File) = synchronized(ACTIVE_SESSIONS) {
        ACTIVE_SESSIONS -= directory.canonicalPath
    }

    private fun isActive(directory: File): Boolean = synchronized(ACTIVE_SESSIONS) {
        directory.canonicalPath in ACTIVE_SESSIONS
    }

    companion object {
        const val STAGING_DIRECTORY: String = "reports/staging"
        private val STAGING_TTL: Duration = Duration.ofHours(24)
        private val SESSION_NAME: Regex = Regex("[0-9a-f]{32}")
        private val ACTIVE_SESSIONS: MutableSet<String> = mutableSetOf()
    }
}

class ReportAssetException(message: String, cause: Throwable? = null) : IOException(message, cause)
