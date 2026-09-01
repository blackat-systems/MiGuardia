package com.blackatsystems.miguardia.backup

import com.blackatsystems.miguardia.StartupRecoveryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupUiStateTest {
    @Test
    fun `pending startup recovery overrides a saved closed surface`() {
        val recovering = initialBackupUiState(
            recoveryState = StartupRecoveryState.Recovering,
            savedOpen = false,
            includePhotos = true,
            encryptionEnabled = true,
        )
        val failed = initialBackupUiState(
            recoveryState = StartupRecoveryState.Failed("Recuperación obligatoria"),
            savedOpen = false,
            includePhotos = true,
            encryptionEnabled = true,
        )

        assertTrue(recovering.isOpen)
        assertTrue(recovering.recoveryRequired)
        assertEquals(BackupStage.RECOVERING, recovering.stage)
        assertTrue(failed.isOpen)
        assertTrue(failed.recoveryRequired)
        assertEquals(BackupStage.ERROR, failed.stage)
        assertEquals("Recuperación obligatoria", failed.errorMessage)
    }

    @Test
    fun `only read-only preparation phases can be cancelled`() {
        listOf(
            BackupStage.CAPTURING,
            BackupStage.COPYING_OUT,
            BackupStage.READING,
            BackupStage.VALIDATING,
        ).forEach { stage ->
            assertTrue(stage.name, BackupUiState(stage = stage).canCancel)
        }

        listOf(
            BackupStage.APPLYING,
            BackupStage.RECOVERING,
            BackupStage.CANCELLING,
            BackupStage.READY_TO_APPLY,
            BackupStage.SUCCESS,
        ).forEach { stage ->
            assertFalse(stage.name, BackupUiState(stage = stage).canCancel)
        }
    }

    @Test
    fun `creation validation cannot bypass the unencrypted warning`() {
        val rejected = BackupUiState(
            isOpen = true,
            encryptionEnabled = false,
            unencryptedWarningAccepted = false,
        )
        val accepted = rejected.copy(unencryptedWarningAccepted = true)

        assertEquals(
            "Confirmá que entendés que la copia sin contraseña podrá ser leída por cualquiera con acceso al archivo.",
            backupCreationValidationError(rejected),
        )
        assertEquals(null, backupCreationValidationError(accepted))
    }
}
