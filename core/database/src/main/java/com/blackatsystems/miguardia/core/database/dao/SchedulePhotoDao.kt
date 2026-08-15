package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.blackatsystems.miguardia.core.database.entity.SchedulePhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface SchedulePhotoDao {
    @Query("SELECT * FROM schedule_photos WHERE month = :month ORDER BY createdAtEpochMillis, id")
    fun observeForMonth(month: String): Flow<List<SchedulePhotoEntity>>

    @Query("SELECT * FROM schedule_photos WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SchedulePhotoEntity?

    @Query("SELECT storageKey FROM schedule_photos")
    suspend fun getAllStorageKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SchedulePhotoEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(entity: SchedulePhotoEntity): Int

    @Query("DELETE FROM schedule_photos WHERE id = :id")
    suspend fun delete(id: String): Int
}
