package com.blackatsystems.miguardia.stage13

import android.graphics.Color
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.stage13.Stage13Pickers.Kind
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.blackatsystems.miguardia.stage12.Stage12CalculationFixture as F

/** Notas privadas de la jornada (A189–A197, C078, C079) sobre la app real y una base propia de la corrida. */
class Stage13NotesDeviceTest : Stage13DeviceBase() {
    private val twinId: UUID = UUID.nameUUIDFromBytes("stage13/twin-day-shift".toByteArray())

    override suspend fun prepare() {
        graph.store.v2Shifts.insert(twinOfDayShift(twinId))
    }

    /** A189–A191, A196, A197, C078: por UUID, vacía no se guarda, editar, cancelar, descartar y eliminar. */
    @Test fun notesFollowTheExactShiftAndEveryEditIsConscious() {
        openDay(F.DAY_SHIFT_DATE)
        waitTag("shift-notes-${F.DAY_SHIFT_ID}")
        waitTag("shift-notes-$twinId")
        compose.onNodeWithTag("shift-notes-$twinId").performScrollTo().performClick()
        // A189: la jornada gemela no hereda la nota canaria de la otra jornada igual.
        waitText("Todavía no hay notas para esta jornada.")
        assertFalse(exists(hasText(F.Canary.SHIFT_NOTE)))

        // C078: vacía o con espacios no se puede guardar.
        val save = clickable("Guardar nota")
        save.assertIsNotEnabled()
        noteField().performTextReplacement("   ")
        save.assertIsNotEnabled()

        // A190: crear.
        noteField().performTextReplacement(CREATED)
        save.assertIsEnabled().performClick()
        waitText(CREATED)
        val created = runBlocking { notesOf(twinId).single() }
        assertEquals(CREATED, created.body)
        assertEquals(listOf(F.Canary.SHIFT_NOTE), runBlocking { notesOf(F.DAY_SHIFT_ID).map { it.body } })

        // A191: editar conserva identidad y fecha de creación.
        tap("Editar")
        waitText("Editar nota")
        noteField().performTextReplacement(EDITED)
        tap("Guardar nota")
        waitText(EDITED)
        val edited = runBlocking { notesOf(twinId).single() }
        assertEquals(created.id, edited.id)
        assertEquals(created.createdAt, edited.createdAt)
        assertEquals(EDITED, edited.body)

        // A197: cancelar una edición no escribe.
        tap("Editar")
        noteField().performTextReplacement("Cambio que se cancela")
        tap("Cancelar edición")
        waitText("Agregar nota")
        assertEquals(edited, runBlocking { notesOf(twinId).single() })

        // A197: cerrar con un borrador pide descartar; «Seguir editando» lo conserva.
        noteField().performTextReplacement(DISCARDED)
        tap("Cerrar")
        waitText("Hay una nota sin guardar.")
        clickInDialog("Seguir editando")
        waitText(DISCARDED)
        tap("Cerrar")
        clickInDialog("Descartar")
        waitGone("Notas privadas")
        assertEquals(listOf(edited), runBlocking { notesOf(twinId) })

        // A196: eliminar exige confirmación; cancelar la confirmación no borra.
        openDay(F.DAY_SHIFT_DATE)
        compose.onNodeWithTag("shift-notes-$twinId").performScrollTo().performClick()
        waitText(EDITED)
        tap("Eliminar")
        waitText("La nota privada se eliminará.")
        clickInDialog("Cancelar")
        assertEquals(listOf(edited), runBlocking { notesOf(twinId) })
        tap("Eliminar")
        clickInDialog("Eliminar")
        waitText("Todavía no hay notas para esta jornada.")
        assertTrue(runBlocking { notesOf(twinId) }.isEmpty())
        // La jornada visualmente igual conserva su nota.
        assertEquals(listOf(F.Canary.SHIFT_NOTE), runBlocking { notesOf(F.DAY_SHIFT_ID).map { it.body } })
    }

    /** A192–A195, C079: imágenes elegidas, abrir, quitar del borrador, cancelar selector y bytes exactos al guardar. */
    @Test fun noteImagesAreChosenOpenedRemovedAndSavedByteForByte() {
        val first = writePng(graph.runDocument("nota-uno.png"), 12, 8, Color.rgb(20, 120, 220))
        val second = writePng(graph.runDocument("nota-dos.png"), 8, 12, Color.rgb(220, 120, 20))
        openDay(F.DAY_SHIFT_DATE)
        compose.onNodeWithTag("shift-notes-$twinId").performScrollTo().performClick()
        waitText("Todavía no hay notas para esta jornada.")

        // Selector cancelado: nada cambia.
        pickers.answer(Kind.PICK_IMAGES, emptyList<android.net.Uri>())
        tap("Agregar imágenes")
        waitFor { pickers.launched.size == 1 }
        compose.waitForIdle()
        assertFalse(exists(hasTagPrefix("note-draft-image-")))

        // A192: dos imágenes elegidas conscientemente.
        pickers.answer(Kind.PICK_IMAGES, listOf(graph.contentUri(first), graph.contentUri(second)))
        tap("Agregar imágenes")
        waitTag("note-draft-image-1")
        waitTag("note-draft-image-2")

        // A193: abrir la imagen privada del borrador.
        clickableBy(hasClickLabel("Ver imagen 1")).performClick()
        waitText("Imagen de la nota")
        clickInDialog("Cerrar")
        waitGone("Imagen de la nota")

        // A194: quitar la segunda del borrador (con confirmación); la primera sigue dibujándose.
        tap("Quitar imagen 2")
        waitText("La imagen dejará de formar parte de esta nota.")
        clickInDialog("Eliminar")
        waitFor { !exists(hasTagPrefix("note-draft-image-2")) }
        waitTag("note-draft-image-1")

        // A195: guardar la nota con texto y una imagen; bytes idénticos al archivo elegido.
        noteField().performTextReplacement(WITH_IMAGE)
        tap("Guardar nota")
        waitText(WITH_IMAGE)
        val saved = runBlocking { checkNotNull(graph.store.shiftNotes.observeWithAttachmentsForShift(twinId).first().single()) }
        assertEquals(WITH_IMAGE, saved.note.body)
        val attachment = saved.attachments.single()
        assertEquals(0, attachment.position)
        assertEquals(sha256(first), sha256(graph.photoStore.file(attachment.storageKey)))
        // La imagen quitada no quedó guardada en la carpeta privada.
        val noteFiles = File(graph.runContext.filesDir, "schedule_photos").listFiles().orEmpty()
            .filter { it.name.startsWith("note_") && !it.name.endsWith(".tmp") }.map { it.name }.toSet()
        val expectedFiles = runBlocking { graph.store.shiftNotes.allAttachmentStorageKeys() }
        assertEquals(expectedFiles, noteFiles)
        assertFalse("La segunda imagen no debe quedar en la corrida",
            noteFiles.any { sha256(File(graph.runContext.filesDir, "schedule_photos/$it")) == sha256(second) })

        // A193 sobre la nota guardada: la miniatura abre la imagen.
        clickableBy(hasClickLabel("Ver imagen 1")).performClick()
        waitText("Imagen de la nota")
        clickInDialog("Cerrar")
    }

    /** A195: si la nota desaparece por fuera mientras se edita, no se recrea y el borrador queda para decidir. */
    @Test fun savingANoteRemovedElsewhereNeverRecreatesItAndKeepsTheDraft() {
        val noteId = UUID.nameUUIDFromBytes("stage13/concurrent-note".toByteArray())
        runBlocking {
            graph.store.shiftNotes.saveWithAttachments(ShiftNote(noteId, twinId, "Nota que otro borra",
                F.CREATED_AT, F.CREATED_AT), emptyList())
        }
        openDay(F.DAY_SHIFT_DATE)
        compose.onNodeWithTag("shift-notes-$twinId").performScrollTo().performClick()
        waitText("Nota que otro borra")
        tap("Editar")
        noteField().performTextReplacement("Borrador sobre una nota ya borrada")
        runBlocking { graph.store.shiftNotes.delete(noteId) }
        waitGone("Nota que otro borra")
        tap("Guardar nota")
        waitText("La nota ya no existe.", substring = true)
        assertNull(runBlocking { graph.store.shiftNotes.getById(noteId) })
        assertTrue(runBlocking { notesOf(twinId) }.isEmpty())
        // El borrador sigue en pantalla: la persona decide si lo descarta.
        waitText("Borrador sobre una nota ya borrada")
        assertNotNull(compose.onNode(hasSetTextAction()))
    }

    private fun noteField() = compose.onNode(hasSetTextAction())

    private fun clickableBy(matcher: androidx.compose.ui.test.SemanticsMatcher) =
        waitFor { exists(matcher) }.let { compose.onAllNodes(matcher)[0] }

    private suspend fun notesOf(shiftId: UUID): List<ShiftNote> = graph.store.shiftNotes.observeForShift(shiftId).first()

    private companion object {
        const val CREATED = "Nota creada en la jornada gemela"
        const val EDITED = "Nota editada en la jornada gemela"
        const val DISCARDED = "Borrador que se descarta"
        const val WITH_IMAGE = "Nota con imagen elegida"
    }
}
