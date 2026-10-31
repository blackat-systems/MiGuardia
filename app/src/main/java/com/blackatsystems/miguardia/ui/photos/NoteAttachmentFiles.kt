package com.blackatsystems.miguardia.ui.photos

import android.net.Uri
import java.util.UUID

/** Note-specific private IO boundary; the application uses the real file-store adapter below. */
internal interface NoteAttachmentFiles {
    suspend fun import(uri: Uri, id: UUID): StoredPhoto
    suspend fun discardUnreferenced(storageKey: String)
    suspend fun reconcileNoteAttachments(expectedStorageKeys: Set<String>)
    fun exists(storageKey: String): Boolean
}

internal fun SchedulePhotoFileStore.noteAttachments(): NoteAttachmentFiles {
    val store = this
    return object : NoteAttachmentFiles {
        override suspend fun import(uri: Uri, id: UUID) =
            store.import(uri, id, namespace = SchedulePhotoFileStore.NOTE_NAMESPACE)
        override suspend fun discardUnreferenced(storageKey: String) = store.discardUnreferenced(storageKey)
        override suspend fun reconcileNoteAttachments(expectedStorageKeys: Set<String>) =
            store.reconcileNoteAttachments(expectedStorageKeys)
        override fun exists(storageKey: String) = store.file(storageKey).isFile
    }
}
