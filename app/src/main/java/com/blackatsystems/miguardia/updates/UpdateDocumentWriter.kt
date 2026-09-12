package com.blackatsystems.miguardia.updates

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import java.io.InputStream
import java.io.OutputStream
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor

internal class CreateUpdateDocument : ActivityResultContracts.CreateDocument(APK_MIME) {
    override fun createIntent(context: Context, input: String): Intent = super.createIntent(context, input).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
    }
}

internal enum class UpdateSaveVerification { READ_BACK, WRITE_ONLY }
internal interface UpdateDocument {
    fun openEmptyOutput(): OutputStream
    fun openInput(): InputStream?
    fun discardCreated()
    fun cancelPendingIo() = Unit
}
internal interface UpdateExporter {
    suspend fun save(artifact: VerifiedUpdate, document: UpdateDocument, allowed: () -> Boolean): UpdateSaveVerification
}

internal class UpdateDocumentWriter(
    private val executor: UpdateSaveExecutor = UpdateSaveExecutor.process,
    private val timeoutMillis: Long = 120_000,
) : UpdateExporter {
    init { require(timeoutMillis in 1..120_000) }
    override suspend fun save(artifact: VerifiedUpdate, document: UpdateDocument, allowed: () -> Boolean): UpdateSaveVerification = executor.run(document, timeoutMillis, allowed) { operation ->
        val release = artifact.selection.release
        operation.checkActive()
        val output = document.openEmptyOutput()
        operation.using(output) {
                artifact.file.inputStream().use { input ->
                    require(copyUpdate(input, output, release.byteSize, operation::stillAllowed).sha256 == release.apkSha256)
                }
                operation.checkActive()
                output.flush()
        }
        operation.checkActive()
        val readBack = document.openInput()
        if (readBack == null) {
            operation.checkActive()
            UpdateSaveVerification.WRITE_ONLY
        } else {
            operation.using(readBack) {
                require(copyUpdate(readBack, null, release.byteSize, operation::stillAllowed).sha256 == release.apkSha256)
            }
            operation.checkActive()
            UpdateSaveVerification.READ_BACK
        }
        // Never delete a document after abandonment: another operation/user might
        // have reused its URI meanwhile. A failed export can leave a partial file.
    }
}

/** Constructed only from this operation's ACTION_CREATE_DOCUMENT result; never from saved state. */
internal class AndroidUpdateDocument(
    private val context: Context,
    private val uri: Uri,
    private val resolver: ContentResolver = context.contentResolver,
    private val isDocument: (Uri) -> Boolean = { DocumentsContract.isDocumentUri(context, it) },
) : UpdateDocument {
    private val cancellation = CancellationSignal()
    override fun openEmptyOutput(): OutputStream {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT && isDocument(uri))
        // Refuse a non-empty or uninspectable destination; never truncate a preexisting document.
        cancellation.throwIfCanceled()
        val descriptor = checkNotNull(resolver.openFileDescriptor(uri, "rw", cancellation))
        try {
            require(descriptor.statSize == 0L)
            val output = android.os.ParcelFileDescriptor.AutoCloseOutputStream(descriptor)
            return output
        } catch (error: Throwable) {
            try { descriptor.close() } catch (_: Throwable) { throw UnreleasedUpdateResource() }
            throw error
        }
    }
    override fun openInput(): InputStream? = try {
        cancellation.throwIfCanceled()
        resolver.openFileDescriptor(uri, "r", cancellation)?.let { descriptor ->
            try { ParcelFileDescriptor.AutoCloseInputStream(descriptor) }
            catch (error: Throwable) {
                try { descriptor.close() } catch (_: Throwable) { throw UnreleasedUpdateResource() }
                throw error
            }
        }
    } catch (cancelled: OperationCanceledException) { throw cancelled
    } catch (unreleased: UnreleasedUpdateResource) { throw unreleased
    } catch (_: Exception) { null }
    override fun cancelPendingIo() { cancellation.cancel() }
    override fun discardCreated() = Unit // No deletion after abandonment or URI reuse.
    override fun toString() = "AndroidUpdateDocument(redacted)"
}
