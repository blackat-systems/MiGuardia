package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.blackatsystems.miguardia.core.database.entity.ScheduleCombinationEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ScheduleCombinationDao {
    @Query("SELECT * FROM schedule_combinations WHERE objectiveId = :objectiveId ORDER BY startTime, endTime, id")
    fun observeByObjective(objectiveId: String): Flow<List<ScheduleCombinationEntity>>

    @Query(
        """
        SELECT
            o.id AS objective_id,
            o.fullName AS objective_fullName,
            o.abbreviation AS objective_abbreviation,
            o.address AS objective_address,
            o.note AS objective_note,
            o.isActive AS objective_isActive,
            o.createdAtEpochMillis AS objective_createdAtEpochMillis,
            o.updatedAtEpochMillis AS objective_updatedAtEpochMillis,
            sc.id AS combination_id,
            sc.objectiveId AS combination_objectiveId,
            sc.startTime AS combination_startTime,
            sc.endTime AS combination_endTime,
            sc.colorArgb AS combination_colorArgb,
            sc.isActive AS combination_isActive,
            sc.createdAtEpochMillis AS combination_createdAtEpochMillis,
            sc.updatedAtEpochMillis AS combination_updatedAtEpochMillis,
            MAX(s.createdAtEpochMillis) AS lastUsedAtEpochMillis
        FROM schedule_combinations sc
        JOIN objectives o ON o.id = sc.objectiveId
        JOIN shifts s ON s.sourceScheduleCombinationId = sc.id
        WHERE o.isActive = 1 AND sc.isActive = 1
        GROUP BY sc.id
        ORDER BY lastUsedAtEpochMillis DESC, sc.id
        LIMIT :limit
        """,
    )
    fun observeRecentlyUsed(limit: Int): Flow<List<RecentScheduleCombinationRow>>

    @Query("SELECT * FROM schedule_combinations WHERE id = :id")
    suspend fun getById(id: String): ScheduleCombinationEntity?

    @Query(
        """SELECT id FROM schedule_combinations
            WHERE objectiveId = :objectiveId AND startTime = :startTime AND endTime = :endTime
            LIMIT 1""",
    )
    suspend fun findExactId(objectiveId: String, startTime: String, endTime: String): String?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ScheduleCombinationEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(entity: ScheduleCombinationEntity): Int

    @Query("UPDATE schedule_combinations SET isActive = 0, updatedAtEpochMillis = :updatedAtEpochMillis WHERE id = :id")
    suspend fun hide(id: String, updatedAtEpochMillis: Long): Int

    @Query("DELETE FROM schedule_combinations WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM schedule_combinations WHERE objectiveId = :objectiveId")
    suspend fun deleteByObjective(objectiveId: String): Int
}
