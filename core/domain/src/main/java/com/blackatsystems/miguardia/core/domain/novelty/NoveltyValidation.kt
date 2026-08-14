package com.blackatsystems.miguardia.core.domain.novelty

import com.blackatsystems.miguardia.core.domain.model.FormalShiftChange
import com.blackatsystems.miguardia.core.domain.model.Holiday
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftNovelty
import com.blackatsystems.miguardia.core.domain.model.ShiftNoveltyType
import com.blackatsystems.miguardia.core.domain.repository.EmptyShiftNoteException
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.MissingNoveltyDescriptionException

fun Holiday.normalized(): Holiday = copy(name = name?.trim()?.takeIf(String::isNotEmpty))

fun ShiftNote.normalized(): ShiftNote {
    val normalizedBody = body.trim()
    if (normalizedBody.isEmpty()) throw EmptyShiftNoteException()
    if (updatedAt < createdAt) throw InvalidLocalDataException("La modificación no puede ser anterior a la creación.")
    return copy(body = normalizedBody)
}
fun ShiftNovelty.normalized(): ShiftNovelty {
    val normalizedDescription = description?.trim()?.takeIf(String::isNotEmpty)
    if (type == ShiftNoveltyType.OTHER && normalizedDescription == null) {
        throw MissingNoveltyDescriptionException()
    }
    if (type == ShiftNoveltyType.SECOND_SHIFT) {
        if (relatedShiftId == null || relatedShiftId == shiftId) {
            throw InvalidLocalDataException("La segunda guardia debe existir y ser distinta de la original.")
        }
    } else if (relatedShiftId != null) {
        throw InvalidLocalDataException("Solo una novedad de segunda guardia puede tener una guardia relacionada.")
    }
    if (updatedAt < createdAt) throw InvalidLocalDataException("La modificación no puede ser anterior a la creación.")
    return copy(description = normalizedDescription)
}

fun FormalShiftChange.normalized(): FormalShiftChange {
    if (!scheduleChanged && !objectiveChanged) {
        throw InvalidLocalDataException("El cambio formal debe modificar horario, objetivo o ambos.")
    }
    if (original.endAt <= original.startAt || final.endAt <= final.startAt) {
        throw InvalidLocalDataException("Las instantáneas del cambio formal contienen un horario inválido.")
    }
    if (updatedAt < createdAt) throw InvalidLocalDataException("La modificación no puede ser anterior a la creación.")
    return copy(description = description?.trim()?.takeIf(String::isNotEmpty))
}
