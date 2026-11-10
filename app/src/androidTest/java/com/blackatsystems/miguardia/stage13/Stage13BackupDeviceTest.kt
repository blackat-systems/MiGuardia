package com.blackatsystems.miguardia.stage13

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.blackatsystems.miguardia.backup.RestoreCheckpoint
import com.blackatsystems.miguardia.core.domain.backup.BackupPhotoMode
import com.blackatsystems.miguardia.core.domain.model.V2ShiftLookup
import com.blackatsystems.miguardia.stage13.Stage13Pickers.Kind
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.blackatsystems.miguardia.stage12.Stage12CalculationFixture as F

/**
 * Copias y restauración (A166–A187) con la pantalla real y el coordinador de Copias DE LA CORRIDA: base
 * `stage13-<id>.db`, carpeta y preferencias propias. Los documentos viven en la carpeta de la corrida y se
 * entregan por el FileProvider real como `content://`, igual que un selector de documentos.
 */
class Stage13BackupDeviceTest : Stage13DeviceBase() {
    override suspend fun prepare() {
        graph.seedPhotos(2)
        graph.seedNoteImage()
    }

    /** A166–A173: abrir, fotos sí/no, cifrado por defecto, contraseña 8–256, mostrar/ocultar, sin cifrado con advertencia, cancelar. */
    @Test fun createEncryptedAndPlainCopiesAndCancelTheDestination() {
        val encrypted = graph.runDocument("cifrada.miguardia-backup")
        val plain = graph.runDocument("sin-cifrar.miguardia-backup")
        val before = runBlocking { graph.digest() }
        openBackups()

        // A167/A168: incluye fotos y cifra por defecto.
        compose.onNodeWithTag("backup-include-photos").assertIsOn()
        compose.onNodeWithTag("backup-encryption").assertIsOn()

        // A169: 7 caracteres o confirmación distinta no habilitan crear; 256 iguales sí.
        password("corta12", "corta12")
        compose.onNodeWithTag("backup-create").assertIsNotEnabled()
        password("contraseña-ok", "contraseña-no")
        compose.onNodeWithTag("backup-create").assertIsNotEnabled()
        password(LONG_PASSWORD, LONG_PASSWORD)
        compose.onNodeWithTag("backup-create").assertIsEnabled()

        // A170: mostrar y ocultar sólo cambia cómo se ve el borrador.
        assertNotEquals(LONG_PASSWORD, editable("backup-password"))
        compose.onNodeWithTag("backup-password-visibility").performScrollTo().performClick()
        waitText("Ocultar contraseñas")
        assertEquals(LONG_PASSWORD, editable("backup-password"))
        compose.onNodeWithTag("backup-password-visibility").performScrollTo().performClick()
        waitText("Mostrar contraseñas")
        assertNotEquals(LONG_PASSWORD, editable("backup-password"))

        // A173: cancelar el selector de destino no deja archivos ni cambia datos.
        pickers.answer(Kind.CREATE_DOCUMENT, null)
        compose.onNodeWithTag("backup-create").performScrollTo().performClick()
        waitText("Guardado cancelado.")
        assertTrue(stagingFiles().isEmpty())
        assertFalse(encrypted.exists())

        // A172: instantánea coherente al destino elegido (cifrada).
        password(LONG_PASSWORD, LONG_PASSWORD)
        pickers.answer(Kind.CREATE_DOCUMENT, graph.contentUri(encrypted))
        compose.onNodeWithTag("backup-create").performScrollTo().performClick()
        waitTag("backup-success")
        waitText("Copia guardada.")
        assertTrue(encrypted.length() > 0)
        compose.onNodeWithTag("backup-finish").performScrollTo().performClick()
        waitFor { !exists(hasTestTag("backup-surface")) }

        // A171: sin cifrado sólo tras la advertencia expresa; además sin fotos (A167).
        openBackups()
        compose.onNodeWithTag("backup-include-photos").performScrollTo().performClick()
        compose.onNodeWithTag("backup-include-photos").assertIsOff()
        compose.onNodeWithTag("backup-encryption").performScrollTo().performClick()
        compose.onNodeWithTag("backup-unencrypted-confirmation").assertIsOff()
        compose.onNodeWithTag("backup-create").assertIsNotEnabled()
        compose.onNodeWithTag("backup-unencrypted-confirmation").performScrollTo().performClick()
        compose.onNodeWithTag("backup-create").assertIsEnabled()
        pickers.answer(Kind.CREATE_DOCUMENT, graph.contentUri(plain))
        compose.onNodeWithTag("backup-create").performScrollTo().performClick()
        waitText("Copia guardada.")
        compose.onNodeWithTag("backup-finish").performScrollTo().performClick()
        waitFor { !exists(hasTestTag("backup-surface")) }

        // Lo creado es coherente con los datos y la contraseña nunca quedó guardada.
        runBlocking {
            val sealed = graph.backupCoordinator.stageSource(graph.contentUri(encrypted))
            try { assertTrue("La copia cifrada declara cifrado", sealed.header.encrypted) } finally { sealed.close() }
            val open = graph.backupCoordinator.stageSource(graph.contentUri(plain))
            assertFalse(open.header.encrypted)
            graph.backupCoordinator.openSource(open, null).use { session ->
                assertEquals(BackupPhotoMode.OMITTED, session.preview.manifest.photoMode)
                assertEquals(0, session.preview.newRecords)
                assertTrue(session.conflicts.isEmpty())
            }
            val withPhotos = graph.backupCoordinator.stageSource(graph.contentUri(encrypted))
            graph.backupCoordinator.openSource(withPhotos, LONG_PASSWORD).use { session ->
                assertEquals(BackupPhotoMode.INCLUDED, session.preview.manifest.photoMode)
                assertEquals(3, session.preview.photosInBackup)
                assertTrue(session.conflicts.isEmpty())
            }
        }
        assertEquals(before, runBlocking { graph.digest() })
        assertEquals(emptyList<String>(), graph.filesContaining(LONG_PASSWORD, instrumentation.targetContext.dataDir))
    }

    /** A174–A177: abrir una Copia, contraseña incorrecta, dañada, truncada y no admitida; revisar antes de escribir. */
    @Test fun openingRejectsWrongPasswordAndDamagedFilesWithoutWriting() {
        val good = graph.runDocument("buena.miguardia-backup")
        runBlocking { graph.writeBackup(good, PASSWORD) }
        val plainGood = graph.runDocument("buena-sin-cifrar.miguardia-backup")
        runBlocking { graph.writeBackup(plainGood, null) }
        val bytes = plainGood.readBytes()
        val tampered = graph.runDocument("alterada.miguardia-backup").also { file ->
            file.writeBytes(bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x5a).toByte() })
        }
        val truncated = graph.runDocument("truncada.miguardia-backup").also { it.writeBytes(bytes.copyOf(bytes.size / 2)) }
        val unsupported = graph.runDocument("no-es-copia.miguardia-backup").also { it.writeText("Esto no es una copia de MiGuardia") }
        val before = runBlocking { graph.digest() }
        openBackups()

        // Selección cancelada.
        pickers.answer(Kind.OPEN_DOCUMENT, null)
        chooseSource()
        waitText("Selección cancelada.")

        // A175/A176: contraseña incorrecta se rechaza sin leer el contenido.
        pickers.answer(Kind.OPEN_DOCUMENT, graph.contentUri(good))
        chooseSource()
        waitTag("backup-password-required")
        compose.onNodeWithTag("backup-unlock-password").performTextReplacement("contraseña-incorrecta")
        compose.onNodeWithTag("backup-unlock").performScrollTo().performClick()
        waitFor { exists(hasTestTag("backup-password-required")) && hasFieldError("backup-unlock-password") }
        assertFalse(exists(hasTestTag("backup-preview")))
        assertEquals(before, runBlocking { graph.digest() })

        // A177: con la contraseña correcta se revisa todo antes de escribir.
        compose.onNodeWithTag("backup-unlock-password").performTextReplacement(PASSWORD)
        compose.onNodeWithTag("backup-unlock").performScrollTo().performClick()
        waitTag("backup-preview")
        val preview = hasAnyAncestor(hasTestTag("backup-preview"))
        waitText("datos internos Room V8", preview, substring = true)
        waitText("3 imágenes incluidas", preview)
        waitText("0 nuevos", preview, substring = true)
        waitText("0 conflictos", preview, substring = true)
        assertEquals(before, runBlocking { graph.digest() })
        closeBackups()

        // A176: alterada, truncada y no admitida terminan en error visible, sin escribir nada.
        listOf(tampered, truncated, unsupported).forEach { file ->
            openBackups()
            pickers.answer(Kind.OPEN_DOCUMENT, graph.contentUri(file))
            chooseSource()
            waitTag("backup-error")
            assertFalse("${file.name}: no hay vista previa", exists(hasTestTag("backup-preview")))
            assertEquals("${file.name}: datos intactos", before, runBlocking { graph.digest() })
            closeBackups()
        }
        assertEquals(emptyList<String>(), graph.filesContaining(PASSWORD, instrumentation.targetContext.dataDir)
            .filterNot { it.endsWith(".miguardia-backup") })
    }

    /** A178–A181, A186: combinar con conflictos resueltos como actual, de la Copia y ambas; vuelve al Calendario. */
    @Test fun mergeAppliesEachConsciousResolution() {
        val copy = graph.runDocument("para-combinar.miguardia-backup")
        runBlocking { graph.writeBackup(copy, null) }
        val atCopy = runBlocking { graph.digest() }
        val overlapId = UUID.nameUUIDFromBytes("stage13/overlap-night".toByteArray())
        val photo = runBlocking { graph.monthPhotos().first() }
        runBlocking {
            val note = checkNotNull(graph.store.shiftNotes.getById(F.SHIFT_NOTE_ID))
            graph.store.shiftNotes.update(note.copy(body = CURRENT_NOTE, updatedAt = note.updatedAt.plusSeconds(60)))
            graph.store.schedulePhotos.update(photo.copy(objectiveId = null, objectiveNameSnapshot = null,
                objectiveAbbreviationSnapshot = null, updatedAt = photo.updatedAt.plusSeconds(60)))
            graph.store.v2Shifts.deleteShift(F.nightShift)
            graph.store.v2Shifts.insert(overlapOfNightShift(overlapId))
        }
        openBackups()
        pickers.answer(Kind.OPEN_DOCUMENT, graph.contentUri(copy))
        chooseSource()
        waitTag("backup-preview")
        compose.onNodeWithTag("backup-choose-merge").performScrollTo().performClick()
        waitTag("backup-conflict-1")
        val tables = conflictTables()
        android.util.Log.i(LOG_TAG, "conflicts=$tables")
        tables.forEachIndexed { index, table ->
            val label = when (table) {
                "shift_notes" -> "Conservar actual"
                "schedule_photos" -> "Usar copia"
                "shifts" -> "Conservar ambos"
                else -> "Conservar actual"
            }
            tap(label, hasAnyAncestor(hasTestTag("backup-conflict-${index + 1}")), scroll = true)
        }
        assertTrue("Hay conflicto de nota, de foto y de jornada superpuesta: $tables",
            tables.containsAll(listOf("shift_notes", "schedule_photos", "shifts")))
        compose.onNodeWithTag("backup-confirm-resolutions").performScrollTo().performClick()
        compose.onNodeWithTag("backup-apply-merge").performScrollTo().performClick()
        waitTag("backup-success")
        waitText("Restauración verificada. Tus datos ya están listos.")
        compose.onNodeWithTag("backup-finish").performScrollTo().performClick()
        waitFor { !exists(hasTestTag("backup-surface")) }
        waitTag("month-grid")

        runBlocking {
            assertEquals("A179: nota actual", CURRENT_NOTE, graph.store.shiftNotes.getById(F.SHIFT_NOTE_ID)?.body)
            val restored = checkNotNull(graph.store.schedulePhotos.getById(photo.id))
            assertEquals("A180: foto de la Copia", F.OBJECTIVE_ID, restored.objectiveId)
            assertTrue("A181: la nocturna de la Copia vuelve", graph.store.v2Shifts.getShift(F.NIGHT_SHIFT_ID) is V2ShiftLookup.V2)
            assertTrue("A181: la superpuesta actual sigue", graph.store.v2Shifts.getShift(overlapId) is V2ShiftLookup.V2)
            assertEquals("Los bytes de las imágenes no cambian al combinar", atCopy.files, graph.digest().files)
        }
    }

    /** A182, A186, A187: reemplazar todo con frase exacta devuelve EXACTAMENTE la Copia, bytes de imagen incluidos. */
    @Test fun replaceAllNeedsTheExactPhraseAndRestoresTheCopyExactly() {
        val copy = graph.runDocument("para-reemplazar.miguardia-backup")
        runBlocking { graph.writeBackup(copy, PASSWORD) }
        val atCopy = runBlocking { graph.digest() }
        val twinId = UUID.nameUUIDFromBytes("stage13/after-copy".toByteArray())
        val photo = runBlocking { graph.monthPhotos().last() }
        val photoFile = graph.photoStore.file(photo.storageKey)
        runBlocking {
            graph.store.v2Shifts.insert(twinOfDayShift(twinId))
            graph.setProfileDisplayName("Apodo posterior a la copia")
        }
        // A187: mismos metadatos (tamaño, medidas, fila) pero otros bytes.
        photoFile.writeBytes(photoFile.readBytes().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x01).toByte() })
        assertNotEquals(atCopy.files["schedule_photos/${photo.storageKey}"], sha256(photoFile))
        assertEquals(photo, runBlocking { graph.store.schedulePhotos.getById(photo.id) })

        openBackups()
        pickers.answer(Kind.OPEN_DOCUMENT, graph.contentUri(copy))
        chooseSource()
        waitTag("backup-password-required")
        compose.onNodeWithTag("backup-unlock-password").performTextReplacement(PASSWORD)
        compose.onNodeWithTag("backup-unlock").performScrollTo().performClick()
        waitTag("backup-preview")
        compose.onNodeWithTag("backup-choose-replace").performScrollTo().performClick()
        waitText("Para continuar, escribí exactamente: Reemplazar todo")
        waitText("Datos actuales que desaparecerán o serán reemplazados", substring = true)
        compose.onNodeWithTag("backup-apply-replace").assertIsNotEnabled()
        compose.onNodeWithTag("backup-replace-confirmation").performTextReplacement("reemplazar todo")
        compose.onNodeWithTag("backup-apply-replace").assertIsNotEnabled()
        compose.onNodeWithTag("backup-replace-confirmation").performTextReplacement("Reemplazar")
        compose.onNodeWithTag("backup-apply-replace").assertIsNotEnabled()
        compose.onNodeWithTag("backup-replace-confirmation").performTextReplacement("Reemplazar todo")
        compose.onNodeWithTag("backup-apply-replace").assertIsEnabled().performScrollTo().performClick()
        waitTag("backup-success")
        compose.onNodeWithTag("backup-finish").performScrollTo().performClick()
        waitFor { !exists(hasTestTag("backup-surface")) }

        assertEquals("Base, preferencias y bytes iguales a la Copia", atCopy, runBlocking { graph.digest() })
        // A186: el Calendario ya muestra los datos restaurados (sin la jornada agregada después).
        openDay(F.DAY_SHIFT_DATE)
        waitTag("shift-notes-${F.DAY_SHIFT_ID}")
        assertFalse(exists(hasTestTag("shift-notes-$twinId")))
    }

    /** A183: otra línea temporal bloquea combinar y deja reemplazar disponible; salir no escribe. */
    @Test fun otherTimelineBlocksMergeButKeepsConsciousReplacement() {
        val foreign = graph.runDocument("otra-linea.miguardia-backup")
        runBlocking { graph.prepareForeignTimelineBackup(foreign) }
        val before = runBlocking { graph.digest() }
        openBackups()
        pickers.answer(Kind.OPEN_DOCUMENT, graph.contentUri(foreign))
        chooseSource()
        waitTag("backup-preview")
        waitText("La línea temporal es distinta: combinar está bloqueado.")
        compose.onNodeWithTag("backup-choose-merge").assertIsNotEnabled()
        compose.onNodeWithTag("backup-choose-replace").assertIsEnabled().performScrollTo().performClick()
        waitTag("backup-replace-confirmation")
        closeBackups()
        assertEquals(before, runBlocking { graph.digest() })
    }

    /** A184, A185: la aplicación no se puede abandonar; una recuperación pendiente no finge éxito y se reintenta. */
    @Test fun applyingCannotBeLeftAndPendingRecoveryNeedsARealRetry() {
        val copy = graph.runDocument("interrumpida.miguardia-backup")
        runBlocking { graph.writeBackup(copy, null) }
        val atCopy = runBlocking { graph.digest() }
        runBlocking { graph.store.v2Shifts.insert(twinOfDayShift(UUID.nameUUIDFromBytes("stage13/pending".toByteArray()))) }
        val hold = CountDownLatch(1)
        val reached = CountDownLatch(1)
        graph.restoreFault = RestoreCheckpoint.AFTER_PREPARED to {
            // Después de este punto, una falla al confirmar deja una recuperación pendiente.
            graph.restoreFault = RestoreCheckpoint.AFTER_COMMITTED to { throw IOException("STAGE13_COMMIT_INTERRUPTED") }
            reached.countDown()
            check(hold.await(60, TimeUnit.SECONDS)) { "STAGE13_HOLD_TIMEOUT" }
        }
        openBackups()
        pickers.answer(Kind.OPEN_DOCUMENT, graph.contentUri(copy))
        chooseSource()
        waitTag("backup-preview")
        compose.onNodeWithTag("backup-choose-replace").performScrollTo().performClick()
        compose.onNodeWithTag("backup-replace-confirmation").performTextReplacement("Reemplazar todo")
        compose.onNodeWithTag("backup-apply-replace").performScrollTo().performClick()
        try {
            assertTrue(reached.await(WAIT, TimeUnit.MILLISECONDS))
            // A184: durante la aplicación no hay cancelar, «Volver» está deshabilitado y Atrás no sale.
            waitTag("backup-applying")
            assertFalse(exists(hasTestTag("backup-applying-cancel")))
            clickable("Volver").assertIsNotEnabled()
            device.pressBack()
            compose.waitForIdle()
            assertTrue(exists(hasTestTag("backup-applying")))
        } finally {
            hold.countDown()
        }

        // A185: la confirmación falló: se muestra el error con Reintentar, nunca éxito.
        waitTag("backup-error")
        assertFalse(exists(hasTestTag("backup-success")))
        // Atrás del sistema acá cierra la Activity (la app pasa a segundo plano), no vuelve a los datos:
        // se comprueba que la pantalla no ofrece salir hacia el Calendario.
        clickable("Volver").assertIsNotEnabled()
        assertTrue(exists(hasTestTag("backup-surface")))

        // Un primer reintento que también falla sigue sin fingir éxito.
        graph.failNextRecoveryCleanup = true
        tap("Reintentar", hasAnyAncestor(hasTestTag("backup-error")))
        waitFor { exists(hasTestTag("backup-error")) && !exists(hasTestTag("backup-recovering")) }
        assertFalse(exists(hasTestTag("backup-success")))
        waitText("La recuperación pendiente todavía no pudo completarse.", substring = true)

        // El segundo reintento termina de verdad y vuelve al Calendario con los datos de la Copia.
        tap("Reintentar", hasAnyAncestor(hasTestTag("backup-error")))
        waitTag("backup-success")
        waitText("La recuperación pendiente terminó correctamente.")
        compose.onNodeWithTag("backup-finish").performScrollTo().performClick()
        waitFor { !exists(hasTestTag("backup-surface")) }
        waitTag("month-grid")
        assertEquals(atCopy, runBlocking { graph.digest() })
    }

    /** Hallazgo de la etapa 13: un `.lck` de candidata huérfano (de una versión anterior) se retira al abrir una base. */
    @Test fun orphanCandidateLockIsSweptByTheNextStore() {
        val context = instrumentation.targetContext
        val orphan = File(context.cacheDir, "miguardia-backup-candidate-${UUID.randomUUID()}.db.lck")
        val name = "stage13-sweep-${UUID.randomUUID()}.db"
        check(orphan.createNewFile())
        val sweeper = com.blackatsystems.miguardia.core.database.LocalDataStore.create(context, databaseName = name)
        try {
            runBlocking { sweeper.backups.capture() }
            assertFalse("El bloqueo huérfano se retira", orphan.exists())
        } finally {
            sweeper.close()
            context.deleteDatabase(name)
            File(context.cacheDir, "$name.lck").delete()
            orphan.delete()
        }
    }

    private fun openBackups() {
        openDrawerAction("drawer-action-backups")
        waitTag("backup-surface")
        waitTag("backup-create-card")
    }

    private fun closeBackups() {
        tap("Volver", hasAnyAncestor(hasTestTag("backup-surface")))
        waitFor { !exists(hasTestTag("backup-surface")) }
    }

    private fun chooseSource() {
        waitTag("backup-choose-source")
        compose.onNodeWithTag("backup-choose-source").performScrollTo().performClick()
    }

    private fun password(value: String, confirmation: String) {
        compose.onNodeWithTag("backup-password").performScrollTo().performTextReplacement(value)
        compose.onNodeWithTag("backup-password-confirmation").performScrollTo().performTextReplacement(confirmation)
    }

    private fun hasFieldError(tag: String): Boolean = compose.onAllNodesWithTag(tag).fetchSemanticsNodes()
        .any { it.config.getOrElseNullable(SemanticsProperties.Error) { null } != null }

    private fun editable(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.EditableText) { null }?.text.orEmpty()

    private fun stagingFiles(): List<File> =
        File(graph.runContext.filesDir, "backups/staging").walkTopDown().filter { it.isFile }.toList()

    private fun conflictTables(): List<String> = (1..50).mapNotNull { number ->
        val matcher = hasText("Conflicto $number · ", substring = true)
        compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { node ->
            node.config.getOrElseNullable(SemanticsProperties.Text) { null }?.firstOrNull { it.text.startsWith("Conflicto $number · ") }
                ?.text?.substringAfter(" · ")
        }
    }

    private companion object {
        const val PASSWORD = "clave-de-prueba-13"
        val LONG_PASSWORD: String = "Q".repeat(250) + "-13-ok"
        const val CURRENT_NOTE = "Nota cambiada después de la copia"
    }
}
