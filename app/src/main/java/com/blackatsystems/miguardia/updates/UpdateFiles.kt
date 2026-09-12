package com.blackatsystems.miguardia.updates

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal data class UpdateDigest(val bytes: Long, val sha256: String)

internal suspend fun copyUpdate(
    input: InputStream,
    output: OutputStream?,
    expected: Long,
    allowed: () -> Boolean = { true },
    progress: (Long) -> Unit = {},
): UpdateDigest {
    require(expected in 1..MAX_APK_BYTES)
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(32 * 1024)
    var total = 0L
    while (true) {
        coroutineContext.ensureActive()
        check(allowed())
        val count = input.read(buffer)
        if (count < 0) break
        require(total + count <= expected)
        coroutineContext.ensureActive()
        check(allowed())
        output?.write(buffer, 0, count)
        digest.update(buffer, 0, count)
        total += count
        progress(total)
    }
    require(total == expected)
    check(allowed())
    return UpdateDigest(total, digest.digest().joinToString("") { "%02x".format(it) })
}

/** Only opaque, owned regular files in this dedicated cache directory can be removed. */
internal class UpdateFiles(private val root: File) {
    private var prepared = false
    private val ownedName = Regex("[0-9a-f-]{36}\\.(part|apk)")
    @Synchronized fun cleanupAfterProcessStart() {
        if (prepared) return
        ensureRoot()
        root.listFiles().orEmpty().filter { it.name.matches(ownedName) && !Files.isSymbolicLink(it.toPath()) && it.isFile }.forEach(::discard)
        prepared = true
    }
    fun createPartial(): File {
        cleanupAfterProcessStart()
        return File(root, "${UUID.randomUUID()}.part").also {
            requireScoped(it)
            check(it.createNewFile())
        }
    }
    fun finish(partial: File): File {
        requireScoped(partial)
        val target = File(root, partial.name.removeSuffix(".part") + ".apk")
        requireScoped(target)
        Files.move(partial.toPath(), target.toPath())
        return target
    }
    fun discard(file: File) {
        requireScoped(file)
        if (file.exists() && !file.delete()) throw IOException("Update cache cleanup failed")
    }
    fun requireScoped(file: File) {
        require(file.name.matches(ownedName) && !Files.isSymbolicLink(file.toPath()))
        require(file.canonicalFile.parentFile == root.canonicalFile)
        require(!file.exists() || file.isFile)
    }
    private fun ensureRoot() {
        check(!Files.isSymbolicLink(root.toPath()))
        check(root.mkdirs() || root.isDirectory)
    }
}
