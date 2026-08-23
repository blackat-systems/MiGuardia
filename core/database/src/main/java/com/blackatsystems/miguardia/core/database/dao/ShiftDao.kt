package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.blackatsystems.miguardia.core.database.entity.ShiftEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ShiftDao {
    @Query("SELECT EXISTS(SELECT 1 FROM shifts LIMIT 1)")
    fun observeHasAny(): Flow<Boolean>

    @Query(
        """SELECT * FROM shifts
            WHERE localStartDate BETWEEN :startDateInclusive AND :endDateInclusive
            ORDER BY startEpochMillis, id""",
    )
    fun observeStartingBetween(
        startDateInclusive: String,
        endDateInclusive: String,
    ): Flow<List<ShiftEntity>>

    @Query(
        """SELECT * FROM shifts
            WHERE localStartDate BETWEEN :startDateInclusive AND :endDateInclusive
            ORDER BY startEpochMillis, id""",
    )
    suspend fun getStartingBetween(
        startDateInclusive: String,
        endDateInclusive: String,
    ): List<ShiftEntity>

    @Query(
        """SELECT * FROM shifts
            WHERE endEpochMillis > :instantEpochMillisExclusive
            ORDER BY startEpochMillis, endEpochMillis, id""",
    )
    fun observeEndingAfter(instantEpochMillisExclusive: Long): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun getById(id: String): ShiftEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ShiftEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<ShiftEntity>)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(entity: ShiftEntity): Int

    @Query("DELETE FROM shifts WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM shifts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>): Int
}
