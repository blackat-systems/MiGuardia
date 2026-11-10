package com.blackatsystems.miguardia.stage13

import android.graphics.Color
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.exifinterface.media.ExifInterface
import com.blackatsystems.miguardia.stage13.Stage13Pickers.Kind
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoBitmapDecoder
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.blackatsystems.miguardia.stage12.Stage12CalculationFixture as F

/** Fotos del mes (A198–A207, C095) sobre la app real y una base propia de la corrida. */
class Stage13PhotosDeviceTest : Stage13DeviceBase() {
    override suspend fun prepare() {
        graph.seedPhotos(2)
    }

    /** A198, A199, C095: se abre en el mes visible del Calendario, cambia de mes y vuelve al mismo contexto. */
    @Test fun photosOpenOnTheVisibleMonthChangeMonthAndReturnToTheCalendar() {
        assertCalendarMonth("Septiembre de 2026")
        tap("Mes anterior")
        assertCalendarMonth("Agosto de 2026")
        openDrawerAction("drawer-action-calendar-photos")
        waitText("Fotos del cronograma")
        waitText("Agosto de 2026")
        waitText("Sin fotos para este mes")
        tap("Mes siguiente de fotos")
        waitText("Septiembre de 2026")
        waitText("Foto 1 de 2")
        waitText("Foto 2 de 2")
        tap("Mes siguiente de fotos")
        waitText("Octubre de 2026")
        waitText("Sin fotos para este mes")
        tap("Cerrar")
        waitGone("Fotos del cronograma")
        // C095: el Calendario sigue en el mes desde el que se abrió.
        assertCalendarMonth("Agosto de 2026")
    }

    /** A200, A202–A206: importar, asociar y quitar objetivo, reemplazar, eliminar con confirmación y EXIF. */
    @Test fun importAssociateReplaceAndDeleteKeepBytesAndOrientation() {
        val rotated = writeJpegWithOrientation(graph.runDocument("apaisada-rotada.jpg"), 80, 40,
            Color.rgb(200, 40, 40), ExifInterface.ORIENTATION_ROTATE_90)
        val plain = writePng(graph.runDocument("simple.png"), 30, 20, Color.rgb(40, 200, 40))
        val replacement = writeJpegWithOrientation(graph.runDocument("reemplazo.jpg"), 60, 20,
            Color.rgb(40, 40, 200), ExifInterface.ORIENTATION_ROTATE_270)
        val seeded = runBlocking { graph.monthPhotos() }
        openPhotos()
        waitText("Foto 1 de 2")

        // A200: varias imágenes por el selector de fotos.
        pickers.answer(Kind.PICK_IMAGES, listOf(graph.contentUri(rotated), graph.contentUri(plain)))
        tap("Agregar fotos", scroll = true)
        waitFor { runBlocking { graph.monthPhotos() }.size == 4 }
        revealPhoto(4, 4)
        val all = runBlocking { graph.monthPhotos() }
        val imported = all.filter { photo -> seeded.none { it.id == photo.id } }
        assertEquals(2, imported.size)
        val byHash = imported.associateBy { sha256(graph.photoStore.file(it.storageKey)) }
        val rotatedPhoto = checkNotNull(byHash[sha256(rotated)]) { "La JPEG importada no conserva sus bytes" }
        checkNotNull(byHash[sha256(plain)]) { "La PNG importada no conserva sus bytes" }
        imported.forEach { assertNull("Importada sin objetivo", it.objectiveId) }
        // A206: la orientación EXIF se respeta al mostrarla (80×40 girada se ve 40×80).
        assertVisualSize(graph.photoStore.file(rotatedPhoto.storageKey), 40, 80)

        // A202: asociar al objetivo activo.
        actionsOf(rotatedPhoto.id)
        tap("Asociar: ${F.PLACE_ABBREVIATION} · ${F.PLACE_NAME}")
        waitFor { runBlocking { graph.store.schedulePhotos.getById(rotatedPhoto.id) }?.objectiveId == F.OBJECTIVE_ID }
        val associated = checkNotNull(runBlocking { graph.store.schedulePhotos.getById(rotatedPhoto.id) })
        assertEquals(F.PLACE_NAME, associated.objectiveNameSnapshot)
        assertEquals(F.PLACE_ABBREVIATION, associated.objectiveAbbreviationSnapshot)

        // A203: quitar el objetivo no borra la foto.
        actionsOf(rotatedPhoto.id)
        tap("Quitar objetivo")
        waitFor { runBlocking { graph.store.schedulePhotos.getById(rotatedPhoto.id) }?.objectiveId == null }
        assertTrue(graph.photoStore.file(rotatedPhoto.storageKey).isFile)
        assertEquals(sha256(rotated), sha256(graph.photoStore.file(rotatedPhoto.storageKey)))

        // A204: reemplazar conscientemente conserva la identidad y toma los bytes nuevos.
        pickers.answer(Kind.PICK_ONE_IMAGE, graph.contentUri(replacement))
        actionsOf(rotatedPhoto.id)
        tap("Reemplazar")
        waitFor { runBlocking { graph.store.schedulePhotos.getById(rotatedPhoto.id) }?.storageKey != rotatedPhoto.storageKey }
        val replaced = checkNotNull(runBlocking { graph.store.schedulePhotos.getById(rotatedPhoto.id) })
        assertEquals(sha256(replacement), sha256(graph.photoStore.file(replaced.storageKey)))
        assertFalse("El archivo anterior se retira", graph.photoStore.file(rotatedPhoto.storageKey).exists())
        // A206 en el reemplazo: 60×20 con ROTATE_270 se ve 20×60.
        assertVisualSize(graph.photoStore.file(replaced.storageKey), 20, 60)

        // Visor: abre la foto elegida y vuelve al listado (la pinza real se prueba con la persona).
        val index = positionOf(rotatedPhoto.id)
        revealPhoto(index, 4)
        tap("Foto $index de 4", scroll = true)
        waitText("$index de 4")
        tap("Volver")
        waitText("Agregar fotos")

        // A205: eliminar una foto exige confirmación; cancelar no borra.
        actionsOf(rotatedPhoto.id)
        tap("Eliminar")
        waitText("La foto se quitará de MiGuardia. Esta acción no se puede deshacer.")
        clickInDialog("Cancelar")
        assertEquals(4, runBlocking { graph.monthPhotos() }.size)
        actionsOf(rotatedPhoto.id)
        tap("Eliminar")
        clickInDialog("Eliminar foto")
        waitFor { runBlocking { graph.monthPhotos() }.size == 3 }; revealPhoto(3, 3)
        val remaining = runBlocking { graph.monthPhotos() }
        assertEquals(3, remaining.size)
        assertNull(runBlocking { graph.store.schedulePhotos.getById(rotatedPhoto.id) })
        assertFalse(graph.photoStore.file(replaced.storageKey).exists())
        remaining.forEach { assertTrue("Las demás siguen con su archivo", graph.photoStore.file(it.storageKey).isFile) }
    }

    /** A207: una foto cuyo archivo falta muestra el error y nunca la reemplaza otra; al reabrir se reintenta. */
    @Test fun missingPhotoShowsItsOwnErrorAndRetryNeverSubstitutesAnother() {
        val photos = runBlocking { graph.monthPhotos() }
        val missing = photos.first()
        val file = graph.photoStore.file(missing.storageKey)
        val bytes = file.readBytes()
        val parked = File(graph.runDocument("ausente.bin").path).also { file.copyTo(it) }
        assertTrue(file.delete())
        openPhotos()
        waitText("Foto 2 de 2")
        // La lista muestra un error en la tarjeta que falta y la otra foto sigue visible.
        waitText("No se pudo mostrar la foto")
        assertEquals(1, compose.onAllNodes(hasText("No se pudo mostrar la foto")).fetchSemanticsNodes().size)
        tap("Foto 1 de 2", scroll = true)
        waitText("1 de 2")
        waitText("No se pudo mostrar la foto")
        assertFalse("El visor no muestra otra foto en su lugar",
            exists(hasContentDescription("Foto del cronograma", substring = true) and hasAnyAncestor(hasText("1 de 2"))))
        tap("Volver")
        waitText("Agregar fotos")

        // Reintento: vuelve el archivo y al reabrir la MISMA foto se lee de nuevo.
        parked.copyTo(file)
        assertEquals(sha256(bytes), sha256(file))
        tap("Foto 1 de 2", scroll = true)
        waitText("1 de 2")
        waitFor { compose.onAllNodes(hasText("No se pudo mostrar la foto")).fetchSemanticsNodes().isEmpty() }
        assertEquals(missing.id, runBlocking { graph.monthPhotos() }.first().id)
        assertNotEquals(photos[1].storageKey, missing.storageKey)
    }

    private fun openPhotos() {
        openDrawerAction("drawer-action-calendar-photos")
        waitText("Fotos del cronograma")
        waitText("Septiembre de 2026")
    }

    private fun assertVisualSize(file: File, width: Int, height: Int) {
        val decoded = checkNotNull(SchedulePhotoBitmapDecoder().decode(file, 2048)) { "No se pudo decodificar ${file.name}" }
        try {
            assertEquals("Ancho visual", width, decoded.bitmap.width)
            assertEquals("Alto visual", height, decoded.bitmap.height)
        } finally {
            decoded.bitmap.recycle()
        }
    }

    /** Posición (1…n) de la foto en el listado, en el mismo orden que observa la pantalla. */
    private fun positionOf(id: java.util.UUID): Int =
        runBlocking { graph.monthPhotos() }.indexOfFirst { it.id == id }.also { check(it >= 0) } + 1

    /** El listado sólo arma las tarjetas visibles: desplazarse hasta la tarjeta antes de buscarla. */
    private fun revealPhoto(index: Int, total: Int) {
        val list = androidx.compose.ui.test.hasScrollToNodeAction() and
            androidx.compose.ui.test.hasAnyDescendant(hasText("Acciones"))
        // Al importar o borrar el listado se rearma: reintentar hasta que el desplazamiento llegue a la tarjeta.
        waitFor {
            exists(list) && runCatching {
                compose.onNode(list).performScrollToNode(hasContentDescription("Foto $index de $total", substring = true))
            }.isSuccess && exists(hasText("Foto $index de $total"))
        }
    }

    private fun actionsOf(id: java.util.UUID) {
        val total = runBlocking { graph.monthPhotos() }.size
        revealPhoto(positionOf(id), total)
        tap("Acciones", hasAnyAncestor(hasContentDescription("Foto ${positionOf(id)} de $total", substring = true)), scroll = true)
    }
}
