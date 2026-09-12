package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Query
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

}
