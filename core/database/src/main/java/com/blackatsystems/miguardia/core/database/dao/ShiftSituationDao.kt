package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.blackatsystems.miguardia.core.database.entity.ShiftSituationEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ShiftSituationDao {
    @Query("SELECT * FROM shift_situations WHERE shiftId = :shiftId")
    fun observe(shiftId: String): Flow<ShiftSituationEntity?>

    @Query("SELECT * FROM shift_situations WHERE shiftId = :shiftId")
    suspend fun get(shiftId: String): ShiftSituationEntity?

    @Query("SELECT * FROM shift_situations ORDER BY shiftId")
    suspend fun getAll(): List<ShiftSituationEntity>

    @Upsert
    suspend fun replace(entity: ShiftSituationEntity)

    @Query("DELETE FROM shift_situations WHERE shiftId = :shiftId")
    suspend fun delete(shiftId: String): Int
}
