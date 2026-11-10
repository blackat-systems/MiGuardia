package com.blackatsystems.miguardia.stage12

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Stage12PrivateInventoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun directFilesExtraRootsEmptyDirectoriesAndSameSizeMutantsAreObserved() {
        val root = temporary.newFolder("qa")
        file(root, "direct", "abcd")
        file(root, "additional/deep/value", "data")
        File(root, "empty").mkdir()
        val inventory = Stage12PrivateInventory(root, null)
        val before = inventory.capture()
        assertEquals(setOf("direct", "additional", "additional/deep", "additional/deep/value", "empty"), before.keys)
        assertEquals(Stage12PrivateNodeKind.DIRECTORY, before.getValue("empty").kind)
        assertEquals("88d4266fd4e6338d13b845fcf289579d209c897823b9217da3e161936f031589", before.getValue("direct").sha256)
        file(root, "direct", "abce")
        val mutated = inventory.capture()
        assertEquals(before.getValue("direct").size, mutated.getValue("direct").size)
        assertNotEquals(before.getValue("direct"), mutated.getValue("direct"))
        file(root, "new-root/new-file", "new")
        val added = inventory.capture()
        assertEquals(setOf("new-root", "new-root/new-file"), added.keys - mutated.keys)
        assertTrue(File(root, "empty").delete())
        assertFalse("empty" in inventory.capture())
    }

    @Test fun onlyExactDatabaseMainWalAndShmBytesUseTheLogicalOracle() {
        val root = temporary.newFolder("qa")
        val excluded = listOf("databases/miguardia-v2.db", "databases/miguardia-v2.db-wal", "databases/miguardia-v2.db-shm")
        val included = listOf("databases/miguardia-v2.db.bak", "databases/miguardia-v2.db-wal.extra",
            "databases/other.db", "miguardia-v2.db", "extra/miguardia-v2.db")
        (excluded + included).forEach { file(root, it, it) }
        val access = Access()
        val inventory = Stage12PrivateInventory(root, null, access)
        assertEquals(File(root, excluded.first()).canonicalFile, inventory.databaseFile())
        val snapshot = inventory.capture()
        assertTrue(excluded.none { it in snapshot })
        assertTrue(included.all { it in snapshot })
        assertTrue(excluded.none { File(root, it).toPath() in access.opened })
        assertTrue(included.all { File(root, it).toPath() in access.opened })
    }

    @Test fun failedRootListingIsAnErrorWithNoPrivateCause() {
        val root = temporary.newFolder("qa")
        val access = Access().apply { list = { throw IOException("private-canary-path") } }
        failure("READBACK_DIRECTORY_LIST_FAILED") { Stage12PrivateInventory(root, null, access).capture() }
        assertTrue(access.opened.isEmpty())
    }

    @Test fun failedAttributesAndDisappearingFileAreNotAnEmptySnapshot() {
        val root = temporary.newFolder("qa")
        val target = file(root, "value", "abcd").toPath()
        val access = Access().apply { attrs = { path -> if (path == target) throw IOException("private-canary-path") else null } }
        failure("READBACK_NODE_ATTRIBUTES_FAILED") { Stage12PrivateInventory(root, null, access).capture() }
        access.attrs = null
        access.list = { path -> Stage12NioInventoryAccess.children(path).also { if (path == root.toPath()) target.toFile().delete() } }
        failure("READBACK_NODE_ATTRIBUTES_FAILED") { Stage12PrivateInventory(root, null, access).capture() }
    }

    @Test fun openReadAndCloseFailuresCannotLeakPrivatePaths() {
        listOf("open", "read", "close").forEach { phase ->
            val root = temporary.newFolder(phase)
            file(root, "value", "abcd")
            val access = Access().apply {
                opener = {
                    if (phase == "open") throw IOException("private-canary-path")
                    object : InputStream() {
                        override fun read(): Int { if (phase == "read") throw IOException("private-canary-path"); return -1 }
                        override fun close() { if (phase == "close") throw IOException("private-canary-path") }
                    }
                }
            }
            failure("READBACK_FILE_READ_FAILED") { Stage12PrivateInventory(root, null, access).capture() }
        }
    }

    @Test fun shortReadAndTypeReplacementFailInsteadOfProducingAPartialDigest() {
        val root = temporary.newFolder("qa")
        val target = file(root, "value", "abcd").toPath()
        val short = Access().apply { opener = { byteArrayOf(1).inputStream() } }
        failure("READBACK_FILE_CHANGED_WHILE_HASHING") { Stage12PrivateInventory(root, null, short).capture() }
        val replaced = Access().apply {
            opener = { path -> Stage12NioInventoryAccess.open(path).also { target.toFile().delete(); target.toFile().mkdir() } }
        }
        failure("READBACK_FILE_TYPE_CHANGED") { Stage12PrivateInventory(root, null, replaced).capture() }
    }

    @Test fun internalExternalAndCyclicLinksAreRejectedWithoutTargetReads() {
        val root = temporary.newFolder("qa")
        val other = temporary.newFolder("outside")
        file(other, "canary", "untouched")
        val link = file(root, "alias", "placeholder").toPath()
        listOf(root.toPath(), other.toPath(), link).forEach { target ->
            val access = linkedAccess(link, target)
            failure("READBACK_SYMLINK_NOT_ALLOWED") { Stage12PrivateInventory(root, null, access).capture() }
            assertTrue(access.opened.isEmpty())
            assertEquals(listOf(root.toPath()), access.listed)
            assertEquals("untouched", File(other, "canary").readText())
        }
    }

    @Test fun onlyExactContextAttestedRootLibraryAliasIsRepresentedWithoutFollowingIt() {
        val root = temporary.newFolder("qa")
        val library = temporary.newFolder("native-library")
        file(library, "canary", "untouched")
        val link = file(root, "lib", "placeholder").toPath()
        val access = linkedAccess(link, library.toPath())
        val snapshot = Stage12PrivateInventory(root, library, access).capture()
        assertEquals(setOf("lib"), snapshot.keys)
        assertEquals(Stage12PrivateNodeKind.ATTESTED_LIBRARY_ALIAS, snapshot.getValue("lib").kind)
        assertTrue(access.opened.isEmpty())
        assertEquals(listOf(root.toPath()), access.listed)
        val other = temporary.newFolder("wrong-library")
        failure("READBACK_LIBRARY_ALIAS_NOT_ATTESTED") { Stage12PrivateInventory(root, other, access).capture() }
        failure("READBACK_SYMLINK_NOT_ALLOWED") { Stage12PrivateInventory(root, null, access).capture() }
    }

    @Test fun libOrdinaryFileOrDirectoryHasNoNameBasedExemption() {
        val root = temporary.newFolder("qa")
        val lib = file(root, "lib", "data")
        assertEquals(Stage12PrivateNodeKind.FILE, Stage12PrivateInventory(root, null).capture().getValue("lib").kind)
        assertTrue(lib.delete()); assertTrue(lib.mkdir()); file(root, "lib/value", "child")
        val snapshot = Stage12PrivateInventory(root, null).capture()
        assertEquals(Stage12PrivateNodeKind.DIRECTORY, snapshot.getValue("lib").kind)
        assertTrue("lib/value" in snapshot)
    }

    @Test fun excludedDatabaseNamesCannotHideLinksOrDirectories() {
        val root = temporary.newFolder("qa")
        val db = file(root, "databases/miguardia-v2.db", "data").toPath()
        val linked = linkedAccess(db, temporary.newFolder("outside").toPath())
        failure("READBACK_DATABASE_BYTE_TYPE_CHANGED") { Stage12PrivateInventory(root, null, linked).capture() }
        failure("READBACK_FILE_TYPE_CHANGED") { Stage12PrivateInventory(root, null, linked).databaseFile() }
        assertTrue(db.toFile().delete()); assertTrue(db.toFile().mkdir())
        failure("READBACK_DATABASE_BYTE_TYPE_CHANGED") { Stage12PrivateInventory(root, null).capture() }
    }

    @Test fun unsupportedNodeFailsBeforeOpeningOrIgnoringIt() {
        val root = temporary.newFolder("qa")
        val node = file(root, "socket-placeholder", "data").toPath()
        val access = Access().apply { attrs = { path -> if (path == node) shape(path, other = true) else null } }
        failure("READBACK_UNSUPPORTED_PRIVATE_NODE") { Stage12PrivateInventory(root, null, access).capture() }
        assertTrue(access.opened.isEmpty())
    }

    @Test fun providerCannotReturnAnOutsideSiblingOrDuplicateEntry() {
        val root = temporary.newFolder("qa")
        val canary = temporary.newFile("outside-canary").apply { writeText("untouched") }
        val own = file(root, "own", "data").toPath()
        val escaped = Access().apply { list = { listOf(canary.toPath()) } }
        failure("READBACK_PATH_ESCAPE") { Stage12PrivateInventory(root, null, escaped).capture() }
        assertTrue(escaped.opened.isEmpty())
        val duplicate = Access().apply { list = { listOf(own, own) } }
        failure("READBACK_DUPLICATE_DIRECTORY_ENTRY") { Stage12PrivateInventory(root, null, duplicate).capture() }
        assertTrue(duplicate.opened.isEmpty())
        assertEquals("untouched", canary.readText())
    }

    @Test fun preferencesAreReopenedWithVerifiedTypeContainmentAndBytes() {
        val root = temporary.newFolder("qa")
        val prefs = file(root, "shared_prefs/settings.xml", "abcd")
        val inventory = Stage12PrivateInventory(root, null)
        val snapshot = inventory.capture().getValue("shared_prefs/settings.xml")
        assertArrayEquals("abcd".toByteArray(), inventory.readVerifiedBytes("shared_prefs/settings.xml", snapshot))
        prefs.writeText("abce")
        failure("READBACK_PREFERENCES_CHANGED_DURING_READ") { inventory.readVerifiedBytes("shared_prefs/settings.xml", snapshot) }
        failure("READBACK_PATH_ESCAPE") { inventory.readVerifiedBytes("../outside", snapshot) }
        failure("READBACK_PREFERENCE_NOT_FILE") { inventory.readVerifiedBytes("shared_prefs", snapshot.copy(kind = Stage12PrivateNodeKind.DIRECTORY)) }
        val linked = linkedAccess(prefs.toPath(), root.toPath())
        failure("READBACK_FILE_TYPE_CHANGED") { Stage12PrivateInventory(root, null, linked).readVerifiedBytes("shared_prefs/settings.xml", snapshot) }
    }

    @Test fun missingRootAndReplacedAncestorFailWithFixedCodes() {
        val root = temporary.newFolder("qa")
        val target = file(root, "nested/value", "abcd")
        val before = Stage12PrivateInventory(root, null).capture().getValue("nested/value")
        val linked = linkedAccess(requireNotNull(target.parentFile).toPath(), temporary.newFolder("outside").toPath())
        failure("READBACK_DIRECTORY_TYPE_CHANGED") { Stage12PrivateInventory(root, null, linked).readVerifiedBytes("nested/value", before) }
        failure("READBACK_NODE_ATTRIBUTES_FAILED") { Stage12PrivateInventory(File(root, "absent"), null).capture() }
    }

    private fun file(root: File, relative: String, text: String): File = File(root, relative).apply {
        parentFile!!.mkdirs(); writeText(text)
    }

    private fun failure(code: String, action: () -> Unit) {
        val error = assertThrows(Stage12InventoryFailure::class.java) { action() }
        assertEquals(code, error.message)
        assertNull(error.cause)
        assertTrue(error.suppressed.isEmpty())
        assertFalse(error.stackTraceToString().contains("private-canary-path"))
    }

    private fun linkedAccess(link: Path, target: Path): Access = Access().apply {
        attrs = { path -> if (path == link) shape(path, link = true) else null }
        linkReader = { target }
    }

    private fun shape(path: Path, link: Boolean = false, other: Boolean = false): BasicFileAttributes {
        val real = Stage12NioInventoryAccess.attributes(path)
        return object : BasicFileAttributes by real {
            override fun isSymbolicLink() = link
            override fun isOther() = other
            override fun isDirectory() = false
            override fun isRegularFile() = false
        }
    }

    private class Access : Stage12InventoryAccess {
        val opened = mutableListOf<Path>()
        val listed = mutableListOf<Path>()
        var attrs: ((Path) -> BasicFileAttributes?)? = null
        var list: ((Path) -> List<Path>)? = null
        var opener: ((Path) -> InputStream)? = null
        var linkReader: ((Path) -> Path)? = null
        override fun attributes(path: Path): BasicFileAttributes = attrs?.invoke(path) ?: Stage12NioInventoryAccess.attributes(path)
        override fun children(path: Path): List<Path> { listed.add(path); return list?.invoke(path) ?: Stage12NioInventoryAccess.children(path) }
        override fun open(path: Path): InputStream { opened.add(path); return opener?.invoke(path) ?: Stage12NioInventoryAccess.open(path) }
        override fun linkTarget(path: Path): Path = linkReader?.invoke(path) ?: Stage12NioInventoryAccess.linkTarget(path)
    }
}
