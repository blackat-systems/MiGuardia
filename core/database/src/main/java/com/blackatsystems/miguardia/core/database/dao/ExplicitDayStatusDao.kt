package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.blackatsystems.miguardia.core.database.entity.ExplicitDayStatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ExplicitDayStatusDao {
    @Query(
        """SELECT * FROM explicit_day_statuses
            WHERE localDate BETWEEN :startDateInclusive AND :endDateInclusive
            ORDER BY localDate""",
    )
    fun observeBetween(
        startDateInclusive: String,
        endDateInclusive: String,
    ): Flow<List<ExplicitDayStatusEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: ExplicitDayStatusEntity)

    @Query("DELETE FROM explicit_day_statuses WHERE localDate = :localDate")
    suspend fun clear(localDate: String): Int
}
