package com.blackatsystems.miguardia.backup

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.MiGuardiaApplication
import com.blackatsystems.miguardia.core.domain.backup.BackupContainer
import com.blackatsystems.miguardia.core.domain.backup.InvalidBackupException
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupDocumentPublicationInstrumentedTest {
    private val application = ApplicationProvider.getApplicationContext<Context>() as MiGuardiaApplication

    @Test
    fun completeDocumentIsPublishedByteForByteOnlyAfterItsPayloadIsDurable() = runBlocking {
        val artifact = application.backupCoordinator.prepareBackup(
            BackupCreateOptions(includePhotos = false, password = "clave-ficticia-segura"),
        )
        val destination = destinationFile("complete")
        try {
            application.backupCoordinator.copyToDocument(artifact, uri(destination))

            assertArrayEquals(artifact.file.readBytes(), destination.readBytes())
            assertTrue(BackupContainer.readHeader(destination).encrypted)
        } finally {
            artifact.close()
            destination.delete()
        }
    }

    @Test
    fun completePasswordlessDocumentOpensWithoutPasswordButItsPayloadIsSealed() = runBlocking {
        val artifact = application.backupCoordinator.prepareBackup(
            BackupCreateOptions(includePhotos = false, password = null),
        )
        val destination = destinationFile("complete-passwordless")
        try {
            application.backupCoordinator.copyToDocument(artifact, uri(destination))

            assertArrayEquals(artifact.file.readBytes(), destination.readBytes())
            assertTrue(!BackupContainer.readHeader(destination).encrypted)
            BackupContainer.extract(destination, File(application.cacheDir, "passwordless-read"), null).use {
                assertEquals(27, it.payload.database.tables.size)
            }
        } finally {
            artifact.close()
            destination.delete()
        }
    }

    @Test
    fun processDeathBeforeFinalHeaderLeavesOnlyAnInvalidSealedPayloadWithoutPassword() = runBlocking {
        val artifact = application.backupCoordinator.prepareBackup(
            BackupCreateOptions(includePhotos = false, password = null),
        )
        val destination = destinationFile("interrupted")
        val coordinator = LocalBackupCoordinator(
            context = application,
            localDataStore = application.localDataStore,
            preferences = application.portablePreferences,
            afterDocumentPayloadStaged = { throw SimulatedProcessDeath() },
        )
        try {
            assertThrows(SimulatedProcessDeath::class.java) {
                runBlocking { coordinator.copyToDocument(artifact, uri(destination)) }
            }

            assertTrue(destination.length() > BackupContainer.HEADER_BYTES)
            assertEquals(
                List(BackupContainer.HEADER_BYTES) { 0.toByte() },
                destination.inputStream().use { input ->
                    ByteArray(BackupContainer.HEADER_BYTES).also { input.read(it) }.toList()
                },
            )
            assertThrows(InvalidBackupException::class.java) {
                BackupContainer.readHeader(destination)
            }
            val payloadPrefix = destination.inputStream().use { input ->
                input.skip(BackupContainer.HEADER_BYTES.toLong())
                ByteArray(2).also { input.read(it) }
            }
            assertTrue(!payloadPrefix.contentEquals(byteArrayOf(0x50, 0x4B)))
        } finally {
            artifact.close()
            destination.delete()
        }
    }

    @Test
    fun ordinaryPublicationFailureDeletesOrDurablyEmptiesTheIncompleteDocument() = runBlocking {
        val artifact = application.backupCoordinator.prepareBackup(
            BackupCreateOptions(includePhotos = false, password = null),
        )
        val destination = destinationFile("ordinary-failure")
        val coordinator = LocalBackupCoordinator(
            context = application,
            localDataStore = application.localDataStore,
            preferences = application.portablePreferences,
            afterDocumentPayloadStaged = { throw IOException("fallo ficticio de publicación") },
        )
        try {
            assertThrows(IOException::class.java) {
                runBlocking { coordinator.copyToDocument(artifact, uri(destination)) }
            }

            assertTrue(!destination.exists() || destination.length() == 0L)
        } finally {
            artifact.close()
            destination.delete()
        }
    }

    private fun destinationFile(label: String): File = File(
        application.filesDir,
        "reports/artifacts/backup-publication-$label-${UUID.randomUUID()}.miguardia-backup",
    ).also {
        it.parentFile?.mkdirs()
        it.createNewFile()
    }

    private fun uri(file: File) = FileProvider.getUriForFile(
        application,
        "${application.packageName}.fileprovider",
        file,
    )

    private class SimulatedProcessDeath : Error()
}
