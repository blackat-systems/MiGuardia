package com.blackatsystems.miguardia.reports

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface ReportDestination {
    suspend fun save(artifact: ReportArtifact, destination: Uri)

    suspend fun discard(destination: Uri): Boolean = false
}

class ReportDestinationWriter(
    private val contentResolver: ContentResolver,
) : ReportDestination {
    override suspend fun save(artifact: ReportArtifact, destination: Uri) = withContext(Dispatchers.IO) {
        if (destination.scheme != ContentResolver.SCHEME_CONTENT) {
            throw ReportDestinationException("El destino elegido no es un documento válido.")
        }
        try {
            val descriptor = contentResolver.openFileDescriptor(destination, "wt")
                ?: throw IOException("El destino elegido no se pudo abrir.")
            descriptor.use { parcel ->
                FileInputStream(artifact.file).use { input ->
                    FileOutputStream(parcel.fileDescriptor).use { output ->
                        val copied = input.copyTo(output)
                        output.flush()
                        if (copied != artifact.byteSize) {
                            throw IOException("El destino recibió un archivo incompleto.")
                        }
                    }
                }
            }
        } catch (error: Exception) {
            runCatching { contentResolver.delete(destination, null, null) }
            throw ReportDestinationException(
                "No pudimos guardar el informe en el destino elegido. El archivo privado sigue listo para reintentar.",
                error,
            )
        }
    }

    override suspend fun discard(destination: Uri): Boolean = withContext(Dispatchers.IO) {
        if (destination.scheme != ContentResolver.SCHEME_CONTENT) return@withContext false
        runCatching {
            val isKnownEmpty = contentResolver.openFileDescriptor(destination, "r")
                ?.use { descriptor -> descriptor.statSize == 0L }
                ?: false
            isKnownEmpty && contentResolver.delete(destination, null, null) > 0
        }.getOrDefault(false)
    }
}

object ReportShareIntentFactory {
    fun createChooser(context: Context, artifact: ReportArtifact): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, artifact.file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = artifact.format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "Informe de MiGuardia", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Compartir informe de MiGuardia")
    }
}

class ReportDestinationException(message: String, cause: Throwable? = null) : IOException(message, cause)
