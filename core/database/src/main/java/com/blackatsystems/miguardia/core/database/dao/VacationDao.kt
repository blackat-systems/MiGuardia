package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.blackatsystems.miguardia.core.database.entity.VacationEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface VacationDao {
    @Query("SELECT * FROM vacations ORDER BY startDate, endDateInclusive, id")
    suspend fun getAll(): List<VacationEntity>

    @Query(
        """SELECT * FROM vacations
            WHERE startDate <= :endDateInclusive AND endDateInclusive >= :startDateInclusive
            ORDER BY startDate, endDateInclusive, id""",
    )
    fun observeOverlapping(
        startDateInclusive: String,
        endDateInclusive: String,
    ): Flow<List<VacationEntity>>

    @Query(
        """SELECT * FROM vacations
            WHERE endDateInclusive >= :dateInclusive
            ORDER BY startDate, endDateInclusive, id""",
    )
    fun observeEndingOnOrAfter(dateInclusive: String): Flow<List<VacationEntity>>

    @Query("SELECT * FROM vacations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VacationEntity?

    @Query(
        """SELECT * FROM vacations
            WHERE startDate <= :endDateInclusive
              AND endDateInclusive >= :startDateInclusive
              AND (:excludedId IS NULL OR id != :excludedId)
            ORDER BY startDate, endDateInclusive, id
            LIMIT 1""",
    )
    suspend fun findFirstOverlapping(
        startDateInclusive: String,
        endDateInclusive: String,
        excludedId: String?,
    ): VacationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: VacationEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(entity: VacationEntity): Int

    @Query("DELETE FROM vacations WHERE id = :id")
    suspend fun delete(id: String): Int
}
