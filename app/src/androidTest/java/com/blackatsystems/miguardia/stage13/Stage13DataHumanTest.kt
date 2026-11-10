package com.blackatsystems.miguardia.stage13

import android.content.ContentValues
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.blackatsystems.miguardia.stage12.Stage12CalculationFixture as F

/**
 * Sesión humana de la etapa 13: selector de documentos real del Samsung (cancelar, guardar y abrir una
 * Copia), selector de fotos real (Fotos del mes y Notas) y pinza en el visor. Sólo con Joaquín frente al
 * teléfono. Las imágenes que se eligen son sintéticas, propias de la corrida (álbum `MiGuardiaQA_<run>`) y
 * se borran al terminar. La Copia guardada (datos ficticios, cifrada) queda donde Joaquín la guarde.
 */
class Stage13DataHumanTest : Stage13DeviceBase() {
    override val useRealPickers: Boolean = true
    private val run = UUID.randomUUID().toString()
    private val twinId: UUID = UUID.nameUUIDFromBytes("stage13/human-twin".toByteArray())
    private val published = mutableListOf<Pair<Uri, String>>()

    override suspend fun prepare() {
        graph.seedPhotos(1)
        graph.store.v2Shifts.insert(twinOfDayShift(twinId))
        val colors = listOf(Color.rgb(250, 160, 30), Color.rgb(30, 160, 250))
        colors.forEachIndexed { index, color -> published += publishImage("MG_QA_13_${index + 1}_$run.png", color) }
    }

    @After fun removeGalleryImages() {
        val resolver = instrumentation.targetContext.contentResolver
        published.forEach { (uri, _) -> runCatching { check(resolver.delete(uri, null, null) == 1) } }
        published.forEach { (uri, _) ->
            resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { check(it.count == 0) }
        }
    }

    @Test fun realPickersSaveOpenAndImportWithTheHumanHand() {
        val before = runBlocking { graph.digest() }

        // PASO 1: cancelar el selector de documentos.
        openBackups()
        password(PASSWORD)
        step(1, "Se abre el selector de documentos para guardar la copia: tocá Atrás SIN guardar")
        compose.onNodeWithTag("backup-create").performScrollTo().performClick()
        humanWait { exists(hasText("Guardado cancelado.")) }
        assertTrue(stagingIsEmpty())
        ok(1)

        // PASO 2: guardar de verdad.
        password(PASSWORD)
        step(2, "Selector de documentos otra vez: elegí la carpeta Descargas y tocá Guardar")
        compose.onNodeWithTag("backup-create").performScrollTo().performClick()
        humanWait { exists(hasTestTag("backup-success")) }
        compose.onNodeWithTag("backup-finish").performScrollTo().performClick()
        waitFor { !exists(hasTestTag("backup-surface")) }
        ok(2)

        // PASO 3: abrir la misma Copia con el selector real y combinarla (no cambia nada: es idéntica).
        openBackups()
        step(3, "Selector de documentos para abrir: elegí la copia que acabás de guardar (MiGuardia_copia…)")
        compose.onNodeWithTag("backup-choose-source").performScrollTo().performClick()
        humanWait { exists(hasTestTag("backup-password-required")) }
        compose.onNodeWithTag("backup-unlock-password").performTextReplacement(PASSWORD)
        compose.onNodeWithTag("backup-unlock").performScrollTo().performClick()
        waitTag("backup-preview")
        waitText("0 nuevos", hasAnyAncestor(hasTestTag("backup-preview")), substring = true)
        waitText("0 conflictos", hasAnyAncestor(hasTestTag("backup-preview")), substring = true)
        compose.onNodeWithTag("backup-choose-merge").performScrollTo().performClick()
        compose.onNodeWithTag("backup-apply-merge").performScrollTo().performClick()
        waitTag("backup-success")
        compose.onNodeWithTag("backup-finish").performScrollTo().performClick()
        waitFor { !exists(hasTestTag("backup-surface")) }
        assertEquals("Abrir y combinar la misma copia no cambia nada", before, runBlocking { graph.digest() })
        ok(3)

        // PASO 4: Fotos del mes con el selector de fotos real.
        openDrawerAction("drawer-action-calendar-photos")
        waitText("Fotos del cronograma")
        step(4, "Selector de fotos: elegí las DOS imágenes MG_QA_13 (naranja y celeste) y tocá Agregar/Listo")
        tap("Agregar fotos", scroll = true)
        humanWait { runBlocking { graph.monthPhotos() }.size == 3 }
        val hashes = runBlocking { graph.monthPhotos() }.map { sha256(graph.photoStore.file(it.storageKey)) }.toSet()
        published.forEach { (_, hash) -> assertTrue("Bytes exactos de la imagen elegida", hash in hashes) }
        ok(4)

        // PASO 5: pinza en el visor (lo confirma Joaquín a la vista).
        tap("Foto 2 de 3", scroll = true)
        waitText("2 de 3")
        step(5, "Visor: pellizcá para ampliar, arrastrá la foto ampliada y después tocá Volver")
        humanWait { exists(hasText("Agregar fotos")) }
        tap("Cerrar")
        waitGone("Fotos del cronograma")
        ok(5)

        // PASO 6: imagen de Nota con el selector real.
        openDay(F.DAY_SHIFT_DATE)
        compose.onNodeWithTag("shift-notes-$twinId").performScrollTo().performClick()
        waitText("Todavía no hay notas para esta jornada.")
        step(6, "Selector de fotos para la nota: elegí UNA imagen MG_QA_13 y tocá Agregar/Listo")
        tap("Agregar imágenes")
        humanWait { exists(hasTagPrefix("note-draft-image-1")) }
        compose.onNode(hasSetTextAction()).performTextReplacement("Nota con imagen del selector real")
        tap("Guardar nota")
        waitText("Nota con imagen del selector real")
        val saved = runBlocking { graph.store.shiftNotes.observeWithAttachmentsForShift(twinId).first().single() }
        val noteHash = sha256(graph.photoStore.file(saved.attachments.single().storageKey))
        assertTrue("La imagen de la nota es una de las elegidas", published.any { it.second == noteHash })
        ok(6)
    }

    private fun publishImage(name: String, color: Int): Pair<Uri, String> {
        val context = instrumentation.targetContext
        val source = writePng(graph.runDocument(name), 640, 360, color)
        val bytes = source.readBytes()
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/MiGuardiaQA_$run/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = checkNotNull(context.contentResolver.insert(collection, values))
        checkNotNull(context.contentResolver.openOutputStream(uri, "w")).use { it.write(bytes) }
        check(context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1)
        return uri to sha256(bytes)
    }

    private fun openBackups() {
        openDrawerAction("drawer-action-backups")
        waitTag("backup-create-card")
    }

    private fun password(value: String) {
        compose.onNodeWithTag("backup-password").performScrollTo().performTextReplacement(value)
        compose.onNodeWithTag("backup-password-confirmation").performScrollTo().performTextReplacement(value)
    }

    private fun stagingIsEmpty() =
        java.io.File(graph.runContext.filesDir, "backups/staging").walkTopDown().none { it.isFile }

    /**
     * Mientras el selector de Android está encima, MiGuardia queda detenida y no hay árbol de Compose para
     * leer: eso cuenta como «todavía no», no como falla. Al volver, se espera a que la app esté al frente.
     */
    private fun humanWait(condition: () -> Boolean) = compose.waitUntil(HUMAN_WAIT) {
        device.currentPackageName == QA && runCatching(condition).getOrDefault(false)
    }

    private fun step(number: Int, text: String) = Log.i(LOG_TAG, "PASO $number: $text")

    private fun ok(number: Int) = Log.i(LOG_TAG, "PASO $number OK")

    private companion object {
        const val PASSWORD = "clave-humana-13"
        const val QA = "com.blackatsystems.miguardia.qa"
        const val HUMAN_WAIT = 240_000L
    }
}
