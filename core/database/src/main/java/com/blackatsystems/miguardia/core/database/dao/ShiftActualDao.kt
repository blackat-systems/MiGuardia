package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.blackatsystems.miguardia.core.database.entity.ExtraWorkClassEntity
import com.blackatsystems.miguardia.core.database.entity.ExplicitDayStatusEntity
import com.blackatsystems.miguardia.core.database.entity.HolidayEntity
import com.blackatsystems.miguardia.core.database.entity.MedicalLeaveEntity
import com.blackatsystems.miguardia.core.database.entity.RecurringOccurrenceEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftActualRecordEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftExtraIntervalEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftNoteEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftNotificationConfigEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftNotificationReminderEntity
import com.blackatsystems.miguardia.core.database.entity.VacationEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ShiftActualDao {
    @Query(
        """SELECT
            (SELECT COUNT(*) FROM shift_actual_records WHERE timelineId = :timelineId AND sector = :sector) +
            (SELECT COUNT(*) FROM shift_extra_intervals WHERE timelineId = :timelineId AND sector = :sector) +
            (SELECT COUNT(*) FROM extra_work_classes WHERE timelineId = :timelineId AND sector = :sector)""",
    )
    fun observeContextToken(timelineId: String, sector: String): Flow<Long>

    @Query(
        """SELECT
            (SELECT COUNT(*) FROM extra_work_classes c
                LEFT JOIN work_configuration_roots r ON r.timelineId = c.timelineId
                WHERE r.timelineId IS NULL OR c.helpsMeetHoursReference NOT IN (0, 1)
                    OR c.showDedicatedSummary NOT IN (0, 1) OR c.isActive NOT IN (0, 1)) +
            (SELECT COUNT(*) FROM shift_actual_records a
                LEFT JOIN shift_work_snapshots s
                    ON s.shiftId = a.shiftId AND s.timelineId = a.timelineId AND s.sector = a.sector
                WHERE s.shiftId IS NULL) +
            (SELECT COUNT(*) FROM shift_extra_intervals i
                LEFT JOIN shift_actual_records a
                    ON a.shiftId = i.shiftId AND a.timelineId = i.timelineId AND a.sector = i.sector
                LEFT JOIN extra_work_classes c
                    ON c.id = i.extraWorkClassId AND c.timelineId = i.timelineId AND c.sector = i.sector
                WHERE a.shiftId IS NULL OR c.id IS NULL
                    OR i.helpsMeetHoursReferenceSnapshot NOT IN (0, 1)
                    OR i.showDedicatedSummarySnapshot NOT IN (0, 1))""",
    )
    suspend fun getInvalidReferenceOrBooleanCount(): Int

    @Query(
        """SELECT COUNT(*) +
            (SELECT COUNT(*) FROM shifts WHERE id = :shiftId) +
            (SELECT COUNT(*) FROM shift_work_snapshots WHERE shiftId = :shiftId) +
            (SELECT COUNT(*) FROM shift_extra_intervals WHERE shiftId = :shiftId) +
            (SELECT COUNT(*) FROM extra_work_classes
                WHERE id IN (SELECT extraWorkClassId FROM shift_extra_intervals WHERE shiftId = :shiftId)) +
            (SELECT COUNT(*) FROM shift_notes WHERE shiftId = :shiftId) +
            (SELECT COUNT(*) FROM shift_notification_configs WHERE shiftId = :shiftId) +
            (SELECT COUNT(*) FROM shift_notification_reminders WHERE shiftId = :shiftId) +
            (SELECT COUNT(*) FROM recurring_occurrences WHERE shiftId = :shiftId) +
            (SELECT COUNT(*) FROM explicit_day_statuses
                WHERE localDate = (SELECT localStartDate FROM shifts WHERE id = :shiftId)) +
            (SELECT COUNT(*) FROM medical_leaves
                WHERE startDate <= (SELECT localStartDate FROM shifts WHERE id = :shiftId)
                AND endDateInclusive >= (SELECT localStartDate FROM shifts WHERE id = :shiftId)) +
            (SELECT COUNT(*) FROM vacations
                WHERE startDate <= (SELECT localStartDate FROM shifts WHERE id = :shiftId)
                AND endDateInclusive >= (SELECT localStartDate FROM shifts WHERE id = :shiftId)) +
            (SELECT COUNT(*) FROM holidays
                WHERE localDate = (SELECT localStartDate FROM shifts WHERE id = :shiftId))
            FROM shift_actual_records WHERE shiftId = :shiftId""",
    )
    fun observeExpectationToken(shiftId: String): Flow<Long>

    @Query(
        """SELECT * FROM extra_work_classes
            WHERE timelineId = :timelineId AND sector = :sector
            ORDER BY isActive DESC, normalizedNameKey, id""",
    )
    fun observeClasses(timelineId: String, sector: String): Flow<List<ExtraWorkClassEntity>>

    @Query(
        """SELECT * FROM extra_work_classes
            WHERE timelineId = :timelineId AND sector = :sector
            ORDER BY normalizedNameKey, id""",
    )
    suspend fun getClasses(timelineId: String, sector: String): List<ExtraWorkClassEntity>

    @Query("SELECT * FROM extra_work_classes ORDER BY timelineId, sector, normalizedNameKey, id")
    suspend fun getAllClasses(): List<ExtraWorkClassEntity>

    @Query("SELECT * FROM extra_work_classes WHERE id = :id")
    suspend fun getClass(id: String): ExtraWorkClassEntity?

    @Query("SELECT * FROM shift_actual_records WHERE shiftId = :shiftId")
    suspend fun getRecord(shiftId: String): ShiftActualRecordEntity?

    @Query("SELECT * FROM shift_actual_records ORDER BY shiftId")
    suspend fun getAllRecords(): List<ShiftActualRecordEntity>

    @Query("SELECT * FROM shift_actual_records WHERE shiftId IN (:shiftIds) ORDER BY shiftId")
    suspend fun getRecords(shiftIds: List<String>): List<ShiftActualRecordEntity>

    @Query(
        """SELECT * FROM shift_extra_intervals
            WHERE shiftId = :shiftId
            ORDER BY startEpochMillis, endEpochMillis, id""",
    )
    suspend fun getIntervals(shiftId: String): List<ShiftExtraIntervalEntity>

    @Query("SELECT * FROM shift_extra_intervals ORDER BY shiftId, startEpochMillis, endEpochMillis, id")
    suspend fun getAllIntervals(): List<ShiftExtraIntervalEntity>

    @Query("SELECT * FROM recurring_occurrences WHERE shiftId = :shiftId")
    suspend fun getOccurrence(shiftId: String): RecurringOccurrenceEntity?

    @Query("SELECT * FROM shift_notes WHERE shiftId = :shiftId ORDER BY id")
    suspend fun getNotes(shiftId: String): List<ShiftNoteEntity>

    @Query("SELECT * FROM shift_notification_configs WHERE shiftId = :shiftId")
    suspend fun getNotificationConfig(shiftId: String): ShiftNotificationConfigEntity?

    @Query("SELECT * FROM shift_notification_reminders WHERE shiftId = :shiftId ORDER BY leadMinutes")
    suspend fun getNotificationReminders(shiftId: String): List<ShiftNotificationReminderEntity>

    @Query("SELECT * FROM explicit_day_statuses WHERE localDate = :localDate")
    suspend fun getExplicitDayStatus(localDate: String): ExplicitDayStatusEntity?

    @Query("SELECT * FROM medical_leaves WHERE startDate <= :localDate AND endDateInclusive >= :localDate ORDER BY id")
    suspend fun getMedicalLeaves(localDate: String): List<MedicalLeaveEntity>

    @Query("SELECT * FROM vacations WHERE startDate <= :localDate AND endDateInclusive >= :localDate ORDER BY id")
    suspend fun getVacations(localDate: String): List<VacationEntity>

    @Query("SELECT * FROM holidays WHERE localDate = :localDate ORDER BY id")
    suspend fun getHolidays(localDate: String): List<HolidayEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertClass(entity: ExtraWorkClassEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateClass(entity: ExtraWorkClassEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecord(entity: ShiftActualRecordEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateRecord(entity: ShiftActualRecordEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIntervals(entities: List<ShiftExtraIntervalEntity>)

    @Query("DELETE FROM shift_extra_intervals WHERE shiftId = :shiftId")
    suspend fun deleteIntervals(shiftId: String): Int

    @Query("DELETE FROM shift_actual_records WHERE shiftId = :shiftId")
    suspend fun deleteRecord(shiftId: String): Int
}
