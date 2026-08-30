package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.blackatsystems.miguardia.core.database.entity.HolidayEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface HolidayDao {
    @Query("SELECT * FROM holidays WHERE localDate BETWEEN :start AND :endInclusive ORDER BY localDate, id")
    fun observeBetween(start: String, endInclusive: String): Flow<List<HolidayEntity>>

    @Query("SELECT * FROM holidays ORDER BY localDate, id")
    suspend fun getAll(): List<HolidayEntity>

    @Query("SELECT * FROM holidays WHERE id = :id")
    suspend fun getById(id: String): HolidayEntity?

    @Query("SELECT * FROM holidays WHERE localDate = :date LIMIT 1")
    suspend fun getByDate(date: String): HolidayEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: HolidayEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(entity: HolidayEntity): Int

    @Upsert
    suspend fun upsertAll(entities: List<HolidayEntity>)

    @Query("DELETE FROM holidays WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM holidays WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>): Int
}
