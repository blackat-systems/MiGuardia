package com.blackatsystems.miguardia.ui.photos

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** One whole selection is accepted or rejected before any private file is imported. */
internal object NoteImageSelection {
    const val MAX_IMAGES = 30

    fun rejectionMessage(existingCount: Int, selectedCount: Int): String? {
        if (selectedCount == 0) return null // Cancelling the picker leaves the draft untouched.
        val available = (MAX_IMAGES - existingCount).coerceAtLeast(0)
        if (selectedCount <= available) return null
        return if (available == 0) {
            "La nota ya tiene 30 imágenes. Quitá alguna antes de agregar otras. No se agregó ninguna imagen."
        } else {
            "Podés agregar ${if (available == 1) "1 imagen" else "$available imágenes"} más. " +
                "Elegí menos imágenes y volvé a intentar. No se agregó ninguna imagen."
        }
    }

    suspend fun <Source, Image> importBatch(
        selection: List<Source>,
        existingCount: Int,
        importImage: suspend (Source, Int) -> Image,
        discard: suspend (List<Image>) -> Unit,
    ): List<Image> {
        rejectionMessage(existingCount, selection.size)?.let { throw IllegalArgumentException(it) }
        val imported = mutableListOf<Image>()
        try {
            selection.forEach { imported += importImage(it, existingCount + imported.size) }
            return imported
        } catch (error: Exception) {
            // A failed/cancelled selection owns only these new files, never previous images.
            withContext(NonCancellable) { discard(imported) }
            throw error
        }
    }
}
