package com.blackatsystems.miguardia.ui.management

import android.content.Context
import android.net.Uri
import com.blackatsystems.miguardia.core.domain.model.*
import com.blackatsystems.miguardia.core.domain.repository.ShiftNoteRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftSituationRepository
import com.blackatsystems.miguardia.backup.LocalDataMutationGate
import com.blackatsystems.miguardia.core.domain.repository.CalendarBatchPreview
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import com.blackatsystems.miguardia.ui.photos.StoredPhoto
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

/** Serializes promotion + SQL confirmation with reconciliation (which must read references inside). */
internal object NoteFileCommitGate { val mutex = Mutex() }

class ManualLoadNotes(context: Context, private val notes: ShiftNoteRepository,
    private val situations: ShiftSituationRepository? = null,
    private val mutationGate: LocalDataMutationGate = LocalDataMutationGate()) {
    private val drafts = SchedulePhotoFileStore(context, draftOnly = true)
    private val stored = SchedulePhotoFileStore(context)
    private val draftMutex = Mutex()

    suspend fun import(uri: Uri, operation: UUID, position: Int, now: Instant): ShiftNoteAttachment = draftMutex.withLock {
        val id = UUID.randomUUID()
        val image = drafts.import(uri, id, namespace = "note")
        ShiftNoteAttachment(id, operation, image.storageKey, image.mimeType, image.byteSize,
            image.width, image.height, position, now, now)
    }

    suspend fun discard(attachments: List<ShiftNoteAttachment>) = draftMutex.withLock {
        attachments.forEach { runCatching { drafts.discardUnreferenced(it.storageKey) } }
    }

    suspend fun recoverDraft(attachments: List<ShiftNoteAttachment>) = draftMutex.withLock {
        runCatching { drafts.reconcileNoteAttachments(attachments.mapTo(linkedSetOf()) { it.storageKey }) }
        Unit
    }

    suspend fun matchesCommitted(pair: V2ShiftWrite, body: String, images: List<ShiftNoteAttachment>, operation: UUID): Boolean {
        if (body.isBlank() && images.isEmpty()) return true
        val id = UUID.nameUUIDFromBytes("$operation:note:${pair.shift.id}".toByteArray())
        val saved = notes.getWithAttachments(id) ?: return false
        if (saved.note != ShiftNote(id, pair.shift.id, body.trim(), pair.shift.createdAt, pair.shift.createdAt)) return false
        val expected = images.mapIndexed { index, image ->
            val attachmentId = UUID.nameUUIDFromBytes("$operation:${pair.shift.id}:${image.id}".toByteArray())
            val extension = when (image.mimeType) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
            image.copy(id = attachmentId, noteId = id, storageKey = "note_$attachmentId.$extension", position = index,
                createdAt = pair.shift.createdAt, updatedAt = pair.shift.createdAt)
        }
        return saved.attachments == expected && expected.all { stored.file(it.storageKey).isFile }
    }

    suspend fun cleanRemoved(noteKeys: Set<String>, evidenceKeys: Set<String> = emptySet()) = NoteFileCommitGate.mutex.withLock {
        runCatching {
            val retained = notes.allAttachmentStorageKeys()
            noteKeys.filterNot { it in retained }.forEach { runCatching { stored.discardUnreferenced(it) } }
            situations?.let { repository ->
                val evidence = repository.evidenceStorageKeys()
                evidenceKeys.filterNot { it in evidence }.forEach { runCatching { stored.discardUnreferenced(it) } }
            }
        }
        Unit
    }

    suspend fun applyCalendarBatch(preview: CalendarBatchPreview, write: suspend () -> Unit) =
        mutationGate.withExclusiveMutation {
            write()
            withContext(NonCancellable) {
                cleanRemoved(preview.removedNoteAttachmentStorageKeys, preview.removedSituationEvidenceStorageKeys)
            }
        }

    suspend fun commit(
        mutation: V2ShiftBatchMutation,
        body: String,
        attachments: List<ShiftNoteAttachment>,
        operation: UUID,
        write: suspend (V2ShiftBatchMutation) -> Unit,
    ) = mutationGate.withExclusiveMutation { NoteFileCommitGate.mutex.withLock {
        val created = mutableListOf<String>()
        try {
            val rows = if (body.isBlank() && attachments.isEmpty()) emptyList() else mutation.shiftsToInsert.map { pair ->
                val id = UUID.nameUUIDFromBytes("$operation:note:${pair.shift.id}".toByteArray())
                val now = pair.shift.createdAt
                val copies = attachments.mapIndexed { index, attachment ->
                    val attachmentId = UUID.nameUUIDFromBytes("$operation:${pair.shift.id}:${attachment.id}".toByteArray())
                    val extension = when (attachment.mimeType) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
                    // Register the deterministic destination before IO: a cancellation may
                    // discard copyNote's return value after its file has already been created.
                    created += "note_$attachmentId.$extension"
                    val image = stored.copyNote(drafts.file(attachment.storageKey),
                        StoredPhoto(attachment.storageKey, attachment.mimeType, attachment.byteSize,
                            attachment.pixelWidth, attachment.pixelHeight), attachmentId)
                    attachment.copy(id = attachmentId, noteId = id, storageKey = image.storageKey,
                        position = index, createdAt = now, updatedAt = now)
                }
                ShiftNoteWithAttachments(ShiftNote(id, pair.shift.id, body.trim(), now, now), copies)
            }
            write(mutation.copy(notesToInsert = rows))
        } finally {
            // Also handles cancellation or an uncertain SQL return: never delete a referenced file.
            withContext(NonCancellable) {
                runCatching {
                    val retained = notes.allAttachmentStorageKeys()
                    (created + mutation.deletionExpectations.values.flatMap { it.attachmentStorageKeys })
                        .filterNot { it in retained }.forEach { key -> runCatching { stored.discardUnreferenced(key) } }
                    situations?.let { repository ->
                        val evidence = repository.evidenceStorageKeys()
                        mutation.deletionExpectations.values.mapNotNull { it.situationEvidenceStorageKey }
                            .filterNot { it in evidence }.forEach { key -> runCatching { stored.discardUnreferenced(key) } }
                    }
                }
            }
        }
    } }
}

internal fun ShiftNoteAttachment.encodeManualDraft(): String = listOf(id, noteId, storageKey, mimeType,
    byteSize, pixelWidth, pixelHeight, position, createdAt, updatedAt).joinToString("|")

internal fun decodeManualDraft(raw: String): ShiftNoteAttachment? = runCatching {
    val f = raw.split('|'); require(f.size == 10)
    ShiftNoteAttachment(UUID.fromString(f[0]), UUID.fromString(f[1]), f[2], f[3], f[4].toLong(),
        f[5].toInt(), f[6].toInt(), f[7].toInt(), Instant.parse(f[8]), Instant.parse(f[9]))
}.getOrNull()
