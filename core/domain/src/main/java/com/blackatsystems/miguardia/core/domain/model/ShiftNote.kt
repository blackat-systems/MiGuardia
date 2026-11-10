package com.blackatsystems.miguardia.core.domain.model

import java.time.Instant
import java.util.UUID

data class ShiftNote(
    val id: UUID,
    val shiftId: UUID,
    val body: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ShiftNoteAttachment(
    val id: UUID,
    val noteId: UUID,
    val storageKey: String,
    val mimeType: String,
    val byteSize: Long,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val position: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(storageKey.matches(STORAGE_KEY_PATTERN)) { "La referencia del adjunto es inválida" }
        require(mimeType in ALLOWED_IMAGE_MIME_TYPES) { "El tipo del adjunto no es compatible" }
        require(byteSize > 0 && pixelWidth > 0 && pixelHeight > 0) { "Las dimensiones del adjunto son inválidas" }
        require(position in 0 until MAX_ATTACHMENTS) { "La posición del adjunto es inválida" }
        require(!updatedAt.isBefore(createdAt)) { "La actualización del adjunto no puede anteceder a su creación" }
    }

    private companion object {
        const val MAX_ATTACHMENTS = 30
        val ALLOWED_IMAGE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        val STORAGE_KEY_PATTERN = Regex(
            "note_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" +
                "(?:_[0-9a-f]{8})?\\.(?:jpg|jpeg|png|webp)",
        )
    }
}

data class ShiftNoteWithAttachments(
    val note: ShiftNote,
    val attachments: List<ShiftNoteAttachment>,
) {
    init {
        require(note.body.isNotBlank() || attachments.isNotEmpty()) { "Una nota no puede quedar vacía" }
        require(attachments.size <= 30) { "Una nota admite hasta 30 imágenes" }
        require(attachments.all { it.noteId == note.id }) { "Los adjuntos deben pertenecer a la nota" }
        require(attachments.map(ShiftNoteAttachment::position) == attachments.indices.toList()) {
            "Los adjuntos deben conservar un orden continuo"
        }
        require(attachments.map(ShiftNoteAttachment::id).distinct().size == attachments.size)
        require(attachments.map(ShiftNoteAttachment::storageKey).distinct().size == attachments.size)
    }
}
