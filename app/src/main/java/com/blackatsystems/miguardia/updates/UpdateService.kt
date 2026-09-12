package com.blackatsystems.miguardia.updates

import java.io.File
import java.io.FileOutputStream
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlin.coroutines.coroutineContext

internal class UpdateSelection(val release: UpdateRelease, val installed: InstalledUpdateApp, internal val owner: Any, internal val generation: Long) {
    override fun toString() = "UpdateSelection(redacted)"
}
internal class VerifiedUpdate(val selection: UpdateSelection, val file: File) {
    override fun toString() = "VerifiedUpdate(redacted)"
}
internal interface UpdateOperations {
    fun installed(): InstalledUpdateApp
    fun retryRemainingMillis(): Long = 0
    suspend fun release(): UpdateRelease?
    suspend fun select(release: UpdateRelease, installed: InstalledUpdateApp): UpdateSelection
    suspend fun download(selection: UpdateSelection, allowed: () -> Boolean, progress: (Long) -> Unit, verifying: () -> Unit): VerifiedUpdate
    suspend fun validate(artifact: VerifiedUpdate, allowed: () -> Boolean)
    fun discard(artifact: VerifiedUpdate)
}

internal class UpdateService(
    private val transport: UpdateTransport,
    private val inspector: UpdateApkInspector,
    private val files: UpdateFiles,
    private val clock: Clock = Clock.systemUTC(),
    private val nanoTime: () -> Long = System::nanoTime,
) : UpdateOperations {
    private val client = UpdateClient(transport, nanoTime, now = clock::instant).also { it.retryObserved = ::rememberRetry }
    private val owner = Any()
    private val generation = java.util.concurrent.atomic.AtomicLong()
    private val operation = kotlinx.coroutines.sync.Mutex()
    private var retryStarted = 0L
    private var retryDuration = 0L
    @Synchronized override fun retryRemainingMillis(): Long =
        (retryDuration - (nanoTime() - retryStarted) / 1_000_000).coerceAtLeast(0)
    @Synchronized private fun rememberRetry(error: UpdateServiceFailure) {
        if (error.status in setOf(429, 503) && retryRemainingMillis() == 0L) {
            retryStarted = nanoTime(); retryDuration = error.retryMillis
        }
    }
    private suspend fun <T> exclusive(block: suspend () -> T): T {
        check(operation.tryLock()) { "Another update operation owns the coordinator" }
        try {
            val remaining = retryRemainingMillis()
            if (remaining > 0) throw UpdateServiceFailure(429, "RETRY_WAIT", clock.instant().plusMillis(remaining), remaining)
            return block()
        } catch (error: UpdateServiceFailure) { generation.incrementAndGet(); throw error
        } finally { operation.unlock() }
    }
    override fun installed() = inspector.installed()
    override suspend fun release(): UpdateRelease? = exclusive {
        generation.incrementAndGet()
        client.release()
    }
    override suspend fun select(release: UpdateRelease, installed: InstalledUpdateApp): UpdateSelection {
        require(release.availability(installed) == UpdateAvailability.AVAILABLE)
        check(inspector.installed() == installed && retryRemainingMillis() == 0L)
        return UpdateSelection(release, installed, owner, generation.incrementAndGet())
    }
    private suspend fun validateSelection(selection: UpdateSelection) {
        check(selection.owner === owner && selection.generation == generation.get() && installed() == selection.installed)
        require(selection.release.availability(selection.installed) == UpdateAvailability.AVAILABLE)
        coroutineContext.ensureActive()
    }
    override suspend fun download(selection: UpdateSelection, allowed: () -> Boolean, progress: (Long) -> Unit, verifying: () -> Unit): VerifiedUpdate = exclusive {
        var ownedFile: File? = null
        try { withContext(Dispatchers.IO) {
        val release = selection.release
        validateSelection(selection)
        check(allowed())
        val budget = UpdateTimeBudget.start(nanoTime)
        var owned = files.createPartial().also { ownedFile = it }
        try {
            transport.exchange(UpdateRequest(UpdateEndpoint.DOWNLOAD, release = release, budget = budget)) { response ->
                if (response.status != 200) client.fail(response)
                require(response.status == 200 && response.contentType?.substringBefore(';')?.trim()?.equals(APK_MIME, true) == true)
                require(response.contentLength == release.byteSize)
                require(response.contentEncoding == null || response.contentEncoding.equals("identity", true))
                FileOutputStream(owned).use { output ->
                    val result = copyUpdate(response.body, output, release.byteSize,
                        { budget.requireRemaining(); allowed() }, progress)
                    require(result.sha256 == release.apkSha256)
                    output.flush()
                    output.fd.sync()
                }
            }
            budget.requireRemaining()
            coroutineContext.ensureActive()
            check(allowed())
            verifying()
            validateSelection(selection)
            validateUpdateApk(inspector.inspect(owned), release, selection.installed)
            budget.requireRemaining()
            coroutineContext.ensureActive()
            check(allowed())
            owned = files.finish(owned)
            ownedFile = owned
            budget.requireRemaining()
            VerifiedUpdate(selection, owned)
        } catch (error: Exception) {
            runCatching { files.discard(owned) }
            throw error
        }
        } } catch (error: Exception) {
            withContext(NonCancellable + Dispatchers.IO) { ownedFile?.let { runCatching { files.discard(it) } } }
            throw error
        }
    }
    override suspend fun validate(artifact: VerifiedUpdate, allowed: () -> Boolean) = withContext(Dispatchers.IO) {
        validateSelection(artifact.selection)
        check(allowed())
        files.requireScoped(artifact.file)
        val release = artifact.selection.release
        artifact.file.inputStream().use { require(copyUpdate(it, null, release.byteSize, allowed).sha256 == release.apkSha256) }
        validateUpdateApk(inspector.inspect(artifact.file), release, artifact.selection.installed)
        validateSelection(artifact.selection)
        check(allowed())
    }
    override fun discard(artifact: VerifiedUpdate) = files.discard(artifact.file)
}
