package com.blackatsystems.miguardia.stage12

import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Stage12NativeReceiptFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    private val uuid = UUID.randomUUID().toString()
    private fun prepare(files: File) = Stage12NativeReceiptFiles.prepareOutput(files, uuid, "exact-owner", "today-detail")

    @Test fun trustedBaseAliasResolvesAndReusesOnlyItsExactOwner() {
        val files = temporary.newFolder("files")
        val alias = File(temporary.root, "base-alias")
        alias(alias, files)
        try {
            val output = prepare(alias)
            assertTrue(output.toPath().startsWith(files.canonicalFile.toPath()))
            assertFalse(output.exists())
            assertEquals(output, prepare(alias))
        } finally { Files.delete(alias.toPath()) }
    }

    @Test fun descendantAliasCannotCreateOrChangeTheSiblingCanary() {
        val files = temporary.newFolder("files")
        val sibling = temporary.newFolder("sibling")
        val canary = File(sibling, "canary").apply { writeText("keep") }
        val alias = File(files, "reports")
        alias(alias, sibling)
        try {
            assertThrows(Exception::class.java) { prepare(files) }
            assertEquals("keep", canary.readText())
            assertFalse(File(sibling, "artifacts").exists())
        } finally { Files.delete(alias.toPath()) }
    }

    @Test fun danglingOutputEntryIsRejectedBeforeAnyReceiptWrite() {
        val files = temporary.newFolder("files")
        val output = prepare(files)
        val target = temporary.newFolder("link-target")
        alias(output, target)
        assertTrue(target.delete()) // Empty fixture directory only, never recursive.
        try {
            assertThrows(Exception::class.java) { prepare(files) }
        } finally { Files.delete(output.toPath()) }
    }

    @Test fun incorrectOwnerCannotBeAdopted() {
        val files = temporary.newFolder("files")
        val output = prepare(files)
        val owner = File(output.parentFile, "owner.json").apply { writeText("wrong-owner") }
        assertThrows(IllegalStateException::class.java) { prepare(files) }
        assertEquals("wrong-owner", owner.readText())
        assertFalse(output.exists())
    }

    @Test fun duplicateOutputIsNotOverwritten() {
        val files = temporary.newFolder("files")
        val output = prepare(files).apply { writeText("keep-receipt") }
        assertThrows(IllegalStateException::class.java) { prepare(files) }
        assertEquals("keep-receipt", output.readText())
    }

    @Test fun failedDirectoryCreationPreservesTheBlockingFile() {
        val files = temporary.newFolder("files")
        val blocker = File(files, "reports").apply { writeText("keep-blocker") }
        assertThrows(IllegalStateException::class.java) { prepare(files) }
        assertEquals("keep-blocker", blocker.readText())
    }

    @Test fun publicationCreatesTheReceiptThroughOneExclusiveHandle() {
        val output = prepare(temporary.newFolder("files"))
        Stage12NativeReceiptFiles.publish(output, "recibo válido", null)
        assertEquals("recibo válido", output.readText(Charsets.UTF_8))
    }

    @Test fun publicationFailurePreservesThePrimaryFailureAndExistingBytes() {
        val output = prepare(temporary.newFolder("files")).apply { writeText("keep") }
        val primary = IllegalArgumentException("body failure")
        val actual = assertThrows(IllegalArgumentException::class.java) {
            Stage12NativeReceiptFiles.publish(output, "replacement", primary)
        }
        assertSame(primary, actual)
        assertEquals(1, actual.suppressed.size)
        assertTrue(actual.suppressed.single() is java.nio.file.FileAlreadyExistsException)
        assertEquals("keep", output.readText())
    }

    @Test fun publicationFailureWithoutAnEarlierFailureIsPropagated() {
        val output = prepare(temporary.newFolder("files")).apply { writeText("keep") }
        assertThrows(java.nio.file.FileAlreadyExistsException::class.java) {
            Stage12NativeReceiptFiles.publish(output, "replacement", null)
        }
        assertEquals("keep", output.readText())
    }

    @Test fun technicalAdmissionReceiptHasItsOwnExplicitName() {
        val files = temporary.newFolder("files")
        val technical = Stage12NativeReceiptFiles.prepareOutput(files, uuid, "exact-owner", "activity-admission")
        val functional = prepare(files)
        assertNotEquals(technical, functional)
        assertEquals("activity-admission.json", technical.name)
    }

    @Test fun summaryObservationReceiptHasItsOwnExplicitNameAndPublishesExclusively() {
        val files = temporary.newFolder("files")
        val summary = Stage12NativeReceiptFiles.prepareOutput(files, uuid, "exact-owner", "summary-observation")
        val functional = prepare(files)
        assertNotEquals(summary, functional)
        assertEquals("summary-observation.json", summary.name)
        Stage12NativeReceiptFiles.publish(summary, "safe-summary", null)
        assertEquals("safe-summary", summary.readText())
        assertThrows(IllegalStateException::class.java) {
            Stage12NativeReceiptFiles.prepareOutput(files, uuid, "exact-owner", "summary-observation")
        }
    }

    @Test fun nativeCompletionReceiptsHaveOnlyTheirOwnExplicitNamesAndPublishExclusively() {
        val files = temporary.newFolder("completion-files")
        for (name in listOf("c090-identical-shifts", "w010-applied-widget", "x015-query-conservation")) {
            val output = Stage12NativeReceiptFiles.prepareOutput(files, uuid, "exact-owner", name)
            assertEquals("$name.json", output.name)
            Stage12NativeReceiptFiles.publish(output, "safe-$name", null)
            assertEquals("safe-$name", output.readText())
            assertThrows(IllegalStateException::class.java) {
                Stage12NativeReceiptFiles.prepareOutput(files, uuid, "exact-owner", name)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            Stage12NativeReceiptFiles.prepareOutput(files, uuid, "exact-owner", "w023-unadmitted-resource")
        }
    }

    private fun alias(link: File, target: File) {
        val root = temporary.root.canonicalFile.toPath()
        check(link.toPath().toAbsolutePath().normalize().startsWith(root))
        check(target.canonicalFile.toPath().startsWith(root))
        if (!System.getProperty("os.name").startsWith("Windows")) {
            Files.createSymbolicLink(link.toPath(), target.toPath())
            return
        }
        fun literal(file: File) = "'" + file.path.replace("'", "''") + "'"
        val command = "New-Item -ItemType Junction -Path ${literal(link)} -Target ${literal(target)} -ErrorAction Stop | Out-Null"
        val encoded = Base64.getEncoder().encodeToString(command.toByteArray(Charsets.UTF_16LE))
        val executable = File(checkNotNull(System.getenv("SystemRoot")), "System32/WindowsPowerShell/v1.0/powershell.exe")
        val process = ProcessBuilder(executable.path, "-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
            .redirectErrorStream(true).start()
        if (!process.waitFor(15, TimeUnit.SECONDS)) { process.destroyForcibly(); error("Owned junction creation timed out") }
        check(process.exitValue() == 0) { "Owned junction creation failed" }
    }
}
