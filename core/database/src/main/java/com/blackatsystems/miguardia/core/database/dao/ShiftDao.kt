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
    @Query(
        """SELECT * FROM shifts
            WHERE localStartDate BETWEEN :startDateInclusive AND :endDateInclusive
            ORDER BY startEpochMillis, id""",
    )
    fun observeStartingBetween(
        startDateInclusive: String,
        endDateInclusive: String,
    ): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun getById(id: String): ShiftEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ShiftEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(entity: ShiftEntity): Int

    @Query("DELETE FROM shifts WHERE id = :id")
    suspend fun delete(id: String): Int
}
