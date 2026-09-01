package com.blackatsystems.miguardia.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.blackatsystems.miguardia.core.domain.backup.BackupConflict
import com.blackatsystems.miguardia.core.domain.backup.BackupConflictResolution
import com.blackatsystems.miguardia.core.domain.backup.BackupManifest
import com.blackatsystems.miguardia.core.domain.backup.BackupPhotoMode
import com.blackatsystems.miguardia.core.domain.backup.BackupPreview
import com.blackatsystems.miguardia.core.domain.backup.BackupRecordClassification
import com.blackatsystems.miguardia.core.domain.backup.BackupRecordKey
import com.blackatsystems.miguardia.core.domain.backup.BackupValue
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BackupComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun creationStartsEncryptedAndRequiresMatchingPasswords() {
        var state by mutableStateOf(BackupUiState(isOpen = true))
        val actions = BackupActions(
            setPassword = { state = state.copy(password = it) },
            setPasswordConfirmation = { state = state.copy(passwordConfirmation = it) },
        )
        setBackup({ state }, actions)

        compose.onNodeWithTag("backup-encryption").assertIsOn()
        compose.onNodeWithTag("backup-create").assertIsNotEnabled()
        compose.onNodeWithTag("backup-password").performTextInput("contraseña segura")
        compose.onNodeWithTag("backup-password-confirmation").performTextInput("contraseña segura")
        compose.onNodeWithTag("backup-create").assertIsEnabled()
        compose.onNodeWithText(
            "Guardá esta contraseña en un lugar seguro: si la olvidás, nadie puede recuperar el contenido de la copia.",
        ).assertIsDisplayed()
    }

    @Test
    fun unencryptedCreationNeedsAnExplicitReadableFileWarning() {
        var state by mutableStateOf(BackupUiState(isOpen = true, encryptionEnabled = false))
        val actions = BackupActions(
            setUnencryptedWarningAccepted = {
                state = state.copy(unencryptedWarningAccepted = it)
            },
        )
        setBackup({ state }, actions)

        compose.onNodeWithText("Cualquiera con acceso al archivo podrá leer su contenido.")
            .assertIsDisplayed()
        compose.onNodeWithTag("backup-create").assertIsNotEnabled()
        compose.onNodeWithTag("backup-unencrypted-confirmation").performClick()
        compose.onNodeWithTag("backup-create").assertIsEnabled()
    }

    @Test
    fun differentTimelineBlocksMergeButKeepsConsciousReplacementAvailable() {
        val state = BackupUiState(
            isOpen = true,
            stage = BackupStage.PREVIEW,
            formatVersion = 1,
            preview = preview(timelineCompatible = false),
        )
        setBackup({ state }, BackupActions())

        compose.onNodeWithTag("backup-choose-merge").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("backup-choose-replace").assertIsEnabled()
        compose.onNodeWithText("La línea temporal es distinta: combinar está bloqueado.").assertIsDisplayed()
        compose.onNodeWithText("Formato de copia V1 · datos internos Room V5").assertIsDisplayed()
    }

    @Test
    fun excessiveMergeComplexityKeepsOnlyConsciousReplacementAvailable() {
        val reason = "Hay demasiados conflictos para combinarlos de forma segura."
        val state = BackupUiState(
            isOpen = true,
            stage = BackupStage.PREVIEW,
            formatVersion = 1,
            preview = preview(mergeBlockedReason = reason),
        )
        setBackup({ state }, BackupActions())

        compose.onNodeWithTag("backup-choose-merge").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("backup-choose-replace").assertIsEnabled()
        compose.onNodeWithText(reason).assertIsDisplayed()
    }

    @Test
    fun conflictDefaultsToKeepingCurrentAndCannotInventKeepBoth() {
        val conflict = conflict()
        var state by mutableStateOf(
            BackupUiState(
                isOpen = true,
                stage = BackupStage.RESOLVING_CONFLICTS,
                preview = preview(),
                restoreChoice = RestoreChoice.MERGE,
                conflicts = listOf(conflict),
                resolutions = mapOf(conflict.id to BackupConflictResolution.KEEP_CURRENT),
            ),
        )
        val actions = BackupActions(
            resolve = { id, resolution -> state = state.copy(resolutions = state.resolutions + (id to resolution)) },
        )
        setBackup({ state }, actions)

        compose.onNodeWithText("Conservar actual").assertIsDisplayed()
        compose.onNodeWithText("Usar copia").assertIsDisplayed()
        compose.onNodeWithText("Conservar ambos").assertDoesNotExist()
        compose.onNodeWithText("Usar copia").performClick()
        compose.runOnIdle {
            assertEquals(BackupConflictResolution.USE_BACKUP, state.resolutions[conflict.id])
        }
    }

    @Test
    fun compatibleDistinctIdentitiesOfferAnExplicitKeepBothChoice() {
        val conflict = conflict(keepBothAllowed = true)
        var state by mutableStateOf(
            BackupUiState(
                isOpen = true,
                stage = BackupStage.RESOLVING_CONFLICTS,
                preview = preview(),
                restoreChoice = RestoreChoice.MERGE,
                conflicts = listOf(conflict),
                resolutions = mapOf(conflict.id to BackupConflictResolution.KEEP_CURRENT),
            ),
        )
        val actions = BackupActions(
            resolve = { id, resolution -> state = state.copy(resolutions = state.resolutions + (id to resolution)) },
        )
        setBackup({ state }, actions)

        compose.onNodeWithText("Conservar ambos").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(BackupConflictResolution.KEEP_BOTH, state.resolutions[conflict.id])
        }
    }

    @Test
    fun replaceNeedsTheExactSecondConfirmation() {
        var state by mutableStateOf(
            BackupUiState(
                isOpen = true,
                stage = BackupStage.READY_TO_APPLY,
            preview = preview(
                photosMissing = 3,
                currentRecordsRemovedOrReplaced = 2,
                currentPreferencesRemovedOrReplaced = 2,
                incomingPreferenceCount = 17,
            ),
                restoreChoice = RestoreChoice.REPLACE_ALL,
            ),
        )
        val actions = BackupActions(
            setReplaceConfirmation = { state = state.copy(replaceConfirmation = it) },
        )
        setBackup({ state }, actions)

        compose.onNodeWithTag("backup-apply-replace").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("backup-replace-confirmation").performTextInput("Reemplazar todo")
        compose.onNodeWithTag("backup-apply-replace").assertIsEnabled()
        compose.onNodeWithText("También desaparecerán 3 fotos: la copia fue creada sin fotos.")
            .assertIsDisplayed()
        compose.onNodeWithText(
            "Datos actuales que desaparecerán o serán reemplazados: 2 registros y 2 ajustes. " +
                "La copia recuperará 1 registro y 17 ajustes.",
        ).assertIsDisplayed()
    }

    @Test
    fun replacementDescribesNetLossInsteadOfCountingIdenticalRowsAsDeleted() {
        val state = BackupUiState(
            isOpen = true,
            stage = BackupStage.READY_TO_APPLY,
            preview = preview(
                currentRecords = 5,
                incomingRecords = 5,
                identicalRecords = 99,
                currentRecordsRemovedOrReplaced = 1,
                currentPreferencesRemovedOrReplaced = 0,
                incomingPreferenceCount = 17,
            ),
            restoreChoice = RestoreChoice.REPLACE_ALL,
        )
        setBackup({ state }, BackupActions())

        compose.onNodeWithText(
            "Datos actuales que desaparecerán o serán reemplazados: 1 registro y 0 ajustes. " +
                "La copia recuperará 5 registros y 17 ajustes.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pendingRecoveryBlocksLeavingTheProtectiveSurface() {
        val state = BackupUiState(
            isOpen = true,
            stage = BackupStage.ERROR,
            recoveryRequired = true,
            errorMessage = "MiGuardia necesita terminar una recuperación pendiente.",
        )
        setBackup({ state }, BackupActions())

        compose.onNodeWithText("Volver").assertIsNotEnabled()
        compose.onNodeWithText("Reintentar").assertIsDisplayed()
    }

    @Test
    fun safePreparationCanBeCancelledButApplyingCannot() {
        var cancellations = 0
        var state by mutableStateOf(
            BackupUiState(isOpen = true, stage = BackupStage.CAPTURING),
        )
        setBackup(
            { state },
            BackupActions(cancelOperation = { cancellations++ }),
        )

        compose.onNodeWithTag("backup-capturing-cancel").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, cancellations) }

        compose.runOnIdle { state = state.copy(stage = BackupStage.APPLYING) }
        compose.onNodeWithTag("backup-applying-cancel").assertDoesNotExist()
        compose.onNodeWithText("Volver").assertIsNotEnabled()
    }

    private fun setBackup(state: () -> BackupUiState, actions: BackupActions) {
        compose.setContent {
            MiGuardiaTheme { BackupSurfaceHost(state(), actions) }
        }
    }

    private fun preview(
        timelineCompatible: Boolean = true,
        photosMissing: Int = 0,
        mergeBlockedReason: String? = null,
        currentRecords: Int = 2,
        incomingRecords: Int = 1,
        identicalRecords: Int = 0,
        currentRecordsRemovedOrReplaced: Int = 0,
        currentPreferencesRemovedOrReplaced: Int = 0,
        incomingPreferenceCount: Int = 0,
    ) = BackupPreview(
        manifest = BackupManifest(
            backupId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            createdAtEpochMillis = 1_788_131_400_000L,
            zoneId = "America/Argentina/Buenos_Aires",
            roomVersion = 5,
            timelineId = null,
            photoMode = BackupPhotoMode.OMITTED,
            tableCounts = mapOf("objectives" to 1),
            entries = emptyList(),
        ),
        historicalSectors = listOf(WorkSector.PRIVATE_SECURITY.name),
        currentCounts = mapOf("objectives" to currentRecords),
        incomingCounts = mapOf("objectives" to incomingRecords),
        newRecords = 1,
        identicalRecords = identicalRecords,
        conflicts = emptyList(),
        photosInBackup = 0,
        photosMissingFromBackup = photosMissing,
        timelineCompatible = timelineCompatible,
        destinationEmpty = false,
        mergeBlockedReason = mergeBlockedReason,
        currentRecordsRemovedOrReplaced = currentRecordsRemovedOrReplaced,
        currentPreferencesRemovedOrReplaced = currentPreferencesRemovedOrReplaced,
        incomingPreferenceCount = incomingPreferenceCount,
    )

    private fun conflict(keepBothAllowed: Boolean = false): BackupConflict {
        val table = if (keepBothAllowed) "shifts" else "objectives"
        val currentKey = BackupRecordKey(
            table,
            listOf(BackupValue.Text("11111111-1111-4111-8111-111111111111")),
        )
        val incomingKey = if (keepBothAllowed) {
            BackupRecordKey(
                table,
                listOf(BackupValue.Text("22222222-2222-4222-8222-222222222222")),
            )
        } else {
            currentKey
        }
        return BackupConflict(
            id = "identity-test",
            classification = if (keepBothAllowed) {
                BackupRecordClassification.SIGNIFICANT_OVERLAP
            } else {
                BackupRecordClassification.CONFLICT
            },
            table = table,
            currentKey = currentKey,
            incomingKey = incomingKey,
            keepBothAllowed = keepBothAllowed,
            summary = "El mismo registro tiene contenidos diferentes.",
        )
    }
}
