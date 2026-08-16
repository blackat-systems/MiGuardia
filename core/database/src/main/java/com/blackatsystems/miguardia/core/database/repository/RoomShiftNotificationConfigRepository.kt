package com.blackatsystems.miguardia.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.blackatsystems.miguardia.core.database.MiGuardiaDatabase
import com.blackatsystems.miguardia.core.database.dao.ShiftNotificationConfigRow
import com.blackatsystems.miguardia.core.database.entity.ShiftNotificationConfigEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftNotificationReminderEntity
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.validateReminderLeadMinutes
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import com.blackatsystems.miguardia.core.domain.repository.ShiftNotificationConfigRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomShiftNotificationConfigRepository(
    private val database: MiGuardiaDatabase,
) : ShiftNotificationConfigRepository {
    private val dao = database.shiftNotificationConfigDao()

    override fun observeAll(): Flow<List<ShiftNotificationConfig>> =
        dao.observeAll().map { rows -> rows.map(ShiftNotificationConfigRow::toDomain) }

    override fun observeForShift(shiftId: UUID): Flow<ShiftNotificationConfig?> =
        dao.observeForShift(shiftId.toString()).map { it?.toDomain() }

    override suspend fun getForShift(shiftId: UUID): ShiftNotificationConfig? =
        dao.getForShift(shiftId.toString())?.toDomain()

    override suspend fun replace(config: ShiftNotificationConfig) {
        val leads = validateReminderLeadMinutes(config.reminderLeadMinutes)
        val shiftId = config.shiftId.toString()
        try {
            database.withTransaction {
                if (database.shiftDao().getById(shiftId) == null) {
                    throw InvalidLocalDataException("No existe la guardia solicitada.")
                }
                dao.upsertConfig(ShiftNotificationConfigEntity(shiftId))
                dao.deleteReminders(shiftId)
                if (leads.isNotEmpty()) {
                    dao.insertReminders(
                        leads.map { ShiftNotificationReminderEntity(shiftId, it) },
                    )
                }
            }
        } catch (error: SQLiteConstraintException) {
            throw InvalidLocalDataException("No se pudo guardar la configuración de avisos.", error)
        }
    }

    override suspend fun clear(shiftId: UUID) {
        dao.deleteConfig(shiftId.toString())
    }
}

private fun ShiftNotificationConfigRow.toDomain(): ShiftNotificationConfig =
    ShiftNotificationConfig(
        shiftId = UUID.fromString(config.shiftId),
        reminderLeadMinutes = reminders.map { it.leadMinutes }.sorted(),
    )
