package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query(
        """SELECT * FROM explicit_day_statuses
            WHERE localDate >= :startDateInclusive
            ORDER BY localDate""",
    )
    fun observeFrom(startDateInclusive: String): Flow<List<ExplicitDayStatusEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: ExplicitDayStatusEntity)

    @Transaction
    suspend fun setAll(entities: List<ExplicitDayStatusEntity>) {
        entities.forEach { set(it) }
    }

    @Query("DELETE FROM explicit_day_statuses WHERE localDate = :localDate")
    suspend fun clear(localDate: String): Int
}
