package com.blackatsystems.miguardia.core.domain.repository

import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface ShiftNotificationConfigRepository {
    fun observeAll(): Flow<List<ShiftNotificationConfig>>
    fun observeForShift(shiftId: UUID): Flow<ShiftNotificationConfig?>
    suspend fun getForShift(shiftId: UUID): ShiftNotificationConfig?
    suspend fun replace(config: ShiftNotificationConfig)
    suspend fun clear(shiftId: UUID)
}
