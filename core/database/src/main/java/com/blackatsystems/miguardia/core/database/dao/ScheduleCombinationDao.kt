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
