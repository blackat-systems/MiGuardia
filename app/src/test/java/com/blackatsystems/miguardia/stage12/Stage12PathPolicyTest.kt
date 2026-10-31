package com.blackatsystems.miguardia.stage12

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Stage12PathPolicyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun traversalAbsoluteAndSiblingCannotReachCanary() {
        val root = temporary.newFolder("owned")
        val canary = temporary.newFile("canary").apply { writeText("unchanged") }
        val fence = Stage12PathPolicy(root)
        val rejected = listOf("../canary", "/canary", "a/../../canary", "a//b", "a/./b", ".", "..", "C:/canary", "\\\\host\\canary", "a\\b", "a\u0000b")
        rejected.forEach { name ->
            assertThrows("Path: $name", IllegalArgumentException::class.java) { fence.resolve(name).writeText("bad") }
        }
        assertThrows(IllegalArgumentException::class.java) { fence.assertOwned(File(root.parentFile, "owned-sibling/item")) }
        assertEquals("unchanged", canary.readText())
        assertEquals(0, root.listFiles()!!.size)
    }

    @Test fun validPathIsCheckedBeforeCreationWithoutMakingDirectories() {
        val root = temporary.newFolder("owned")
        val fence = Stage12PathPolicy(root)
        val file = fence.resolve("files/nested/item")
        assertFalse(file.exists())
        assertFalse(file.parentFile!!.exists())
        assertEquals(File(root, "files/nested/item").canonicalFile, file)
        assertThrows(IllegalArgumentException::class.java) { fence.segment("nested/item") }
    }

    @Test fun existingDirectoryAliasCannotReadOrOverwriteSiblingCanary() {
        val root = temporary.newFolder("owned")
        val sibling = temporary.newFolder("sibling")
        val canary = File(sibling, "canary").apply { writeText("unchanged") }
        val alias = File(root, "alias").toPath()
        createDirectoryAlias(alias, sibling.toPath())
        try {
            val fence = Stage12PathPolicy(root)
            assertThrows(IllegalArgumentException::class.java) { fence.resolve("alias/canary").writeText("bad") }
            assertEquals("unchanged", canary.readText())
        } finally { Files.deleteIfExists(alias) }
        val rootFence = Stage12PathPolicy(root)
        assertTrue(root.delete())
        createDirectoryAlias(root.toPath(), sibling.toPath())
        try {
            assertThrows(IllegalStateException::class.java) { rootFence.resolve("canary").writeText("bad") }
            assertEquals("unchanged", canary.readText())
        } finally { Files.deleteIfExists(root.toPath()) }
    }

    private fun createDirectoryAlias(alias: Path, target: Path) {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) {
            Files.createSymbolicLink(alias, target)
            return
        }
        // A junction tests the Windows directory-reparse boundary without
        // changing Developer Mode, privileges, or any non-test directory.
        val temporaryRoot = temporary.root.toPath().toAbsolutePath().normalize()
        check(alias.toAbsolutePath().normalize().startsWith(temporaryRoot))
        check(target.toAbsolutePath().normalize().startsWith(temporaryRoot))
        check(!Files.exists(alias) && Files.isDirectory(target))
        fun literal(path: Path): String = "'" + path.toString().replace("'", "''") + "'"
        val command = "New-Item -ItemType Junction -Path ${literal(alias)} -Target ${literal(target)} -ErrorAction Stop | Out-Null"
        val encoded = Base64.getEncoder().encodeToString(command.toByteArray(Charsets.UTF_16LE))
        val executable = File(checkNotNull(System.getenv("SystemRoot")), "System32/WindowsPowerShell/v1.0/powershell.exe")
        check(executable.isFile)
        val process = ProcessBuilder(executable.path, "-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
            .redirectErrorStream(true).start()
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("Owned junction creation timed out")
        }
        check(process.exitValue() == 0) { "Owned junction creation failed" }
        check(Files.exists(alias))
        println("DIRECTORY_ALIAS_KIND=WINDOWS_JUNCTION")
    }
}
