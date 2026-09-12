package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.blackatsystems.miguardia.core.database.entity.ObjectiveEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ObjectiveDao {
    @Query("SELECT * FROM objectives WHERE isActive = 1 ORDER BY fullName COLLATE NOCASE, id")
    fun observeActive(): Flow<List<ObjectiveEntity>>

    @Query("SELECT * FROM objectives ORDER BY fullName COLLATE NOCASE, id")
    fun observeAll(): Flow<List<ObjectiveEntity>>

    @Query("SELECT * FROM objectives WHERE id = :id")
    suspend fun getById(id: String): ObjectiveEntity?

    @Query("SELECT * FROM objectives ORDER BY id")
    suspend fun getAll(): List<ObjectiveEntity>

    @Query("SELECT COUNT(*) FROM objectives WHERE isActive NOT IN (0, 1)")
    suspend fun getInvalidBooleanCount(): Int

    @Query("SELECT id FROM objectives WHERE abbreviation = :abbreviation COLLATE NOCASE LIMIT 1")
    suspend fun findIdByAbbreviation(abbreviation: String): String?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ObjectiveEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(entity: ObjectiveEntity): Int

    @Query("UPDATE objectives SET isActive = 0, updatedAtEpochMillis = :updatedAtEpochMillis WHERE id = :id")
    suspend fun hide(id: String, updatedAtEpochMillis: Long): Int

    @Query("DELETE FROM objectives WHERE id = :id")
    suspend fun delete(id: String): Int
}
