package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.blackatsystems.miguardia.core.database.entity.ShiftNotificationConfigEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftNotificationReminderEntity
import kotlinx.coroutines.flow.Flow

internal data class ShiftNotificationConfigRow(
    @Embedded val config: ShiftNotificationConfigEntity,
    @Relation(
        parentColumn = "shiftId",
        entityColumn = "shiftId",
    )
    val reminders: List<ShiftNotificationReminderEntity>,
)

@Dao
internal interface ShiftNotificationConfigDao {
    @Transaction
    @Query("SELECT * FROM shift_notification_configs ORDER BY shiftId")
    fun observeAll(): Flow<List<ShiftNotificationConfigRow>>

    @Transaction
    @Query("SELECT * FROM shift_notification_configs WHERE shiftId = :shiftId")
    fun observeForShift(shiftId: String): Flow<ShiftNotificationConfigRow?>

    @Transaction
    @Query("SELECT * FROM shift_notification_configs WHERE shiftId = :shiftId")
    suspend fun getForShift(shiftId: String): ShiftNotificationConfigRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(entity: ShiftNotificationConfigEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReminders(entities: List<ShiftNotificationReminderEntity>)

    @Query("DELETE FROM shift_notification_reminders WHERE shiftId = :shiftId")
    suspend fun deleteReminders(shiftId: String)

    @Query("DELETE FROM shift_notification_configs WHERE shiftId = :shiftId")
    suspend fun deleteConfig(shiftId: String)
}
