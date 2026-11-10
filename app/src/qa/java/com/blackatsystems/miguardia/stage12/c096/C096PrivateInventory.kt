package com.blackatsystems.miguardia.stage12.c096

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

internal enum class C096PrivateNodeKind { FILE, DIRECTORY, ATTESTED_LIBRARY_ALIAS }
internal data class C096FileSnapshot(
    val size: Long,
    val sha256: String,
    val kind: C096PrivateNodeKind = C096PrivateNodeKind.FILE,
)

/** Fixed public codes: filesystem exceptions and their private paths never become causes. */
internal class C096InventoryFailure(val code: String) : IllegalStateException(code)

internal interface C096InventoryAccess {
    fun attributes(path: Path): BasicFileAttributes
    fun children(path: Path): List<Path>
    fun open(path: Path): InputStream
    fun linkTarget(path: Path): Path
}

internal object C096NioInventoryAccess : C096InventoryAccess {
    override fun attributes(path: Path): BasicFileAttributes =
        Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
    override fun children(path: Path): List<Path> = Files.newDirectoryStream(path).use { it.toList() }
    override fun open(path: Path): InputStream = Files.newInputStream(path, NOFOLLOW_LINKS)
    override fun linkTarget(path: Path): Path = Files.readSymbolicLink(path)
}

/**
 * Read-only, complete inventory of one canonical QA data root. Each capture discovers
 * the root again. This brackets observations; it is not an atomic filesystem snapshot.
 * The only outside-root node accepted is an attested native-library alias: recorded,
 * never followed. The SQLite main/WAL/SHM bytes have a separate logical SQL oracle.
 */
internal class C096PrivateInventory(
    root: File,
    trustedNativeLibraryDirectory: File?,
    private val access: C096InventoryAccess = C096NioInventoryAccess,
) {
    private val root = root.toPath().toAbsolutePath().normalize()
    private val libraryTarget = trustedNativeLibraryDirectory?.let {
        io("READBACK_LIBRARY_IDENTITY_FAILED") { it.canonicalFile.toPath() }
    }
    private val databaseBytes = setOf(
        "databases/miguardia-v2.db", "databases/miguardia-v2.db-wal", "databases/miguardia-v2.db-shm",
    )

    fun capture(): Map<String, C096FileSnapshot> {
        val rootAttributes = attributes(root)
        requireNode(rootAttributes.isDirectory && !rootAttributes.isSymbolicLink, "READBACK_ROOT_NOT_DIRECTORY")
        requireCanonical(root)
        val result = sortedMapOf<String, C096FileSnapshot>()
        visitDirectory(root, result)
        return result
    }

    fun databaseFile(): File = root.resolve("databases/miguardia-v2.db").also {
        checkedRegularFile(it)
    }.toFile()

    fun readVerifiedBytes(relative: String, expected: C096FileSnapshot): ByteArray {
        requireNode(expected.kind == C096PrivateNodeKind.FILE, "READBACK_PREFERENCE_NOT_FILE")
        val path = root.resolve(relative).normalize()
        requireNode(path != root && path.startsWith(root), "READBACK_PATH_ESCAPE")
        val before = checkedRegularFile(path)
        val bytes = io("READBACK_FILE_READ_FAILED") { access.open(path).use { it.readBytes() } }
        val after = checkedRegularFile(path)
        requireUnchanged(before, after, bytes.size.toLong())
        requireNode(expected == C096FileSnapshot(bytes.size.toLong(), digest(bytes)),
            "READBACK_PREFERENCES_CHANGED_DURING_READ")
        return bytes
    }

    private fun visitDirectory(directory: Path, result: MutableMap<String, C096FileSnapshot>) {
        requireDirectoryChain(directory)
        val children = io("READBACK_DIRECTORY_LIST_FAILED") { access.children(directory) }
        requireNode(children.distinct().size == children.size, "READBACK_DUPLICATE_DIRECTORY_ENTRY")
        children.sortedBy { it.fileName.toString() }.forEach { child ->
            val path = child.toAbsolutePath().normalize()
            requireNode(path.parent == directory && path.startsWith(root), "READBACK_PATH_ESCAPE")
            val relative = root.relativize(path).toString().replace(File.separatorChar, '/')
            val attributes = attributes(path)
            if (relative in databaseBytes) requireNode(attributes.isRegularFile && !attributes.isSymbolicLink,
                "READBACK_DATABASE_BYTE_TYPE_CHANGED")
            val snapshot = when {
                attributes.isSymbolicLink -> libraryAlias(path, relative)
                attributes.isDirectory -> {
                    requireCanonical(path)
                    C096FileSnapshot(0, EMPTY_DIGEST, C096PrivateNodeKind.DIRECTORY)
                }
                attributes.isRegularFile -> {
                    // Names never waive type, alias, ancestor or containment checks.
                    requireDirectoryChain(path.parent)
                    requireCanonical(path)
                    if (relative in databaseBytes) null else fingerprint(path, attributes)
                }
                else -> throw C096InventoryFailure("READBACK_UNSUPPORTED_PRIVATE_NODE")
            }
            if (snapshot != null) {
                requireNode(result.put(relative, snapshot) == null, "READBACK_DUPLICATE_PRIVATE_NODE")
            }
            if (attributes.isDirectory && !attributes.isSymbolicLink) visitDirectory(path, result)
        }
    }

    private fun libraryAlias(path: Path, relative: String): C096FileSnapshot {
        requireNode(relative == "lib" && path.parent == root && libraryTarget != null,
            "READBACK_SYMLINK_NOT_ALLOWED")
        requireDirectoryChain(path.parent)
        val target = io("READBACK_LIBRARY_ALIAS_FAILED") {
            val raw = access.linkTarget(path)
            val absolute = if (raw.isAbsolute) raw else path.parent.resolve(raw)
            absolute.toFile().canonicalFile.toPath()
        }
        requireNode(target == libraryTarget, "READBACK_LIBRARY_ALIAS_NOT_ATTESTED")
        return C096FileSnapshot(0, digest(target.toString().toByteArray(Charsets.UTF_8)),
            C096PrivateNodeKind.ATTESTED_LIBRARY_ALIAS)
    }

    private fun fingerprint(path: Path, before: BasicFileAttributes): C096FileSnapshot {
        val hash = MessageDigest.getInstance("SHA-256")
        var size = 0L
        io("READBACK_FILE_READ_FAILED") {
            access.open(path).use { stream ->
                val buffer = ByteArray(65536)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    hash.update(buffer, 0, count)
                    size += count
                }
            }
        }
        val after = checkedRegularFile(path)
        requireUnchanged(before, after, size)
        return C096FileSnapshot(size, hex(hash.digest()))
    }

    private fun checkedRegularFile(path: Path): BasicFileAttributes {
        requireDirectoryChain(path.parent)
        val attributes = attributes(path)
        requireNode(attributes.isRegularFile && !attributes.isSymbolicLink, "READBACK_FILE_TYPE_CHANGED")
        requireCanonical(path)
        return attributes
    }

    private fun requireUnchanged(before: BasicFileAttributes, after: BasicFileAttributes, size: Long) {
        requireNode(before.isRegularFile && after.isRegularFile && !after.isSymbolicLink &&
            before.size() == size && after.size() == size &&
            before.lastModifiedTime() == after.lastModifiedTime() &&
            before.creationTime() == after.creationTime() && before.fileKey() == after.fileKey(),
            "READBACK_FILE_CHANGED_WHILE_HASHING")
    }

    private fun requireDirectoryChain(path: Path) {
        requireNode(path.startsWith(root), "READBACK_PATH_ESCAPE")
        var directory = root
        val parts = root.relativize(path)
        fun checkDirectory() {
            val attributes = attributes(directory)
            requireNode(attributes.isDirectory && !attributes.isSymbolicLink, "READBACK_DIRECTORY_TYPE_CHANGED")
            requireCanonical(directory)
        }
        checkDirectory()
        if (path != root) parts.forEach { part -> directory = directory.resolve(part); checkDirectory() }
    }

    private fun attributes(path: Path): BasicFileAttributes =
        io("READBACK_NODE_ATTRIBUTES_FAILED") { access.attributes(path) }

    private fun requireCanonical(path: Path) {
        requireNode(io("READBACK_CANONICAL_PATH_FAILED") { path.toFile().canonicalFile.toPath() } == path,
            "READBACK_PATH_ALIAS_NOT_ALLOWED")
    }

    private fun requireNode(condition: Boolean, code: String) {
        if (!condition) throw C096InventoryFailure(code)
    }

    private fun <T> io(code: String, action: () -> T): T = try {
        action()
    } catch (error: C096InventoryFailure) {
        throw error
    } catch (_: Exception) {
        throw C096InventoryFailure(code)
    }

    private fun digest(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }

    companion object {
        private const val EMPTY_DIGEST = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}
