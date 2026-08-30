package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.blackatsystems.miguardia.core.database.entity.ShiftNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ShiftNoteDao {
    @Query("SELECT * FROM shift_notes ORDER BY shiftId, createdAtEpochMillis, id")
    suspend fun getAll(): List<ShiftNoteEntity>

    @Query("SELECT * FROM shift_notes WHERE shiftId = :shiftId ORDER BY createdAtEpochMillis, id")
    fun observeForShift(shiftId: String): Flow<List<ShiftNoteEntity>>

    @Query("SELECT * FROM shift_notes WHERE id = :id")
    suspend fun getById(id: String): ShiftNoteEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ShiftNoteEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(entity: ShiftNoteEntity): Int

    @Query("DELETE FROM shift_notes WHERE id = :id")
    suspend fun delete(id: String): Int
}
