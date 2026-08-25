package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.blackatsystems.miguardia.core.database.entity.RecurringOccurrenceEntity
import com.blackatsystems.miguardia.core.database.entity.RecurringPlanEntity
import com.blackatsystems.miguardia.core.database.entity.RecurringPlanRevisionEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftNoteEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftNotificationConfigEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftNotificationReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface RecurringPlanDao {
    @Query(
        """SELECT * FROM recurring_plans
            WHERE timelineId = :timelineId AND sector = :sector
            ORDER BY createdAtEpochMillis, id""",
    )
    fun observePlans(timelineId: String, sector: String): Flow<List<RecurringPlanEntity>>

    @Query("SELECT COUNT(*) FROM recurring_plan_revisions")
    fun observeRevisionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM recurring_occurrences")
    fun observeOccurrenceCount(): Flow<Int>

    @Query("SELECT * FROM recurring_plans WHERE id = :planId")
    suspend fun getPlan(planId: String): RecurringPlanEntity?

    @Query("SELECT * FROM recurring_plans WHERE id IN (:planIds) ORDER BY id")
    suspend fun getPlans(planIds: List<String>): List<RecurringPlanEntity>

    @Query("SELECT * FROM recurring_plans ORDER BY id")
    suspend fun getAllPlans(): List<RecurringPlanEntity>

    @Query(
        """SELECT * FROM recurring_plan_revisions
            WHERE planId = :planId
            ORDER BY revisionNumber""",
    )
    suspend fun getRevisions(planId: String): List<RecurringPlanRevisionEntity>

    @Query("SELECT * FROM recurring_plan_revisions ORDER BY planId, revisionNumber")
    suspend fun getAllRevisions(): List<RecurringPlanRevisionEntity>

    @Query(
        """SELECT * FROM recurring_occurrences
            WHERE planId = :planId
            ORDER BY localDate""",
    )
    suspend fun getOccurrences(planId: String): List<RecurringOccurrenceEntity>

    @Query("SELECT * FROM recurring_occurrences ORDER BY planId, localDate")
    suspend fun getAllOccurrences(): List<RecurringOccurrenceEntity>

    @Query("SELECT * FROM recurring_occurrences WHERE shiftId = :shiftId")
    suspend fun getOccurrenceForShift(shiftId: String): RecurringOccurrenceEntity?

    @Query("SELECT * FROM shifts WHERE id IN (:shiftIds) ORDER BY id")
    suspend fun getShifts(shiftIds: List<String>): List<ShiftEntity>

    @Query("SELECT * FROM shift_notes WHERE shiftId IN (:shiftIds) ORDER BY shiftId, id")
    suspend fun getNotes(shiftIds: List<String>): List<ShiftNoteEntity>

    @Query("SELECT * FROM shift_notification_configs WHERE shiftId IN (:shiftIds) ORDER BY shiftId")
    suspend fun getNotificationConfigs(shiftIds: List<String>): List<ShiftNotificationConfigEntity>

    @Query(
        """SELECT * FROM shift_notification_reminders
            WHERE shiftId IN (:shiftIds)
            ORDER BY shiftId, leadMinutes""",
    )
    suspend fun getNotificationReminders(shiftIds: List<String>): List<ShiftNotificationReminderEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlan(entity: RecurringPlanEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevision(entity: RecurringPlanRevisionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOccurrences(entities: List<RecurringOccurrenceEntity>)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateOccurrence(entity: RecurringOccurrenceEntity): Int
}
