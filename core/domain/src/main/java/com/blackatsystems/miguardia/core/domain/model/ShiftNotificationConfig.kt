package com.blackatsystems.miguardia.core.domain.model

import java.time.Duration
import java.util.UUID

const val MAX_SHIFT_NOTIFICATION_REMINDERS = 5

data class ShiftNotificationConfig(
    val shiftId: UUID,
    val reminderLeadMinutes: List<Long>,
) {
    init {
        validateReminderLeadMinutes(reminderLeadMinutes)
    }
}
fun validateReminderLeadMinutes(values: Collection<Long>): List<Long> {
    require(values.size <= MAX_SHIFT_NOTIFICATION_REMINDERS) {
        "Se admiten hasta cinco avisos por jornada."
    }
    require(values.all { it > 0L }) {
        "Cada aviso debe anticiparse una cantidad positiva de minutos."
    }
    require(values.distinct().size == values.size) {
        "Los avisos duplicados no están permitidos."
    }
    require(values.all { runCatching { Duration.ofMinutes(it) }.isSuccess }) {
        "La anticipacion del aviso es demasiado grande."
    }
    return values.sorted()
}
