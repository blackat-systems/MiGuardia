package com.blackatsystems.miguardia.core.domain.model

import com.blackatsystems.miguardia.core.domain.repository.EmptyShiftNoteException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException

fun Holiday.normalized(): Holiday = copy(name = name?.trim()?.takeIf(String::isNotEmpty))

fun ShiftNote.normalized(): ShiftNote {
    val normalizedBody = body.trim()
    if (normalizedBody.isEmpty()) throw EmptyShiftNoteException()
    if (updatedAt < createdAt) {
        throw InvalidLocalDataException("La modificación no puede ser anterior a la creación.")
    }
    return copy(body = normalizedBody)
}
