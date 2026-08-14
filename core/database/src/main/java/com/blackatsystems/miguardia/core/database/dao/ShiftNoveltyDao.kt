package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.blackatsystems.miguardia.core.database.entity.FormalShiftChangeEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftNoveltyEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ShiftNoveltyDao {
    @Query("SELECT * FROM shift_novelties WHERE shiftId = :shiftId ORDER BY createdAtEpochMillis, id")
    fun observeForShift(shiftId: String): Flow<List<ShiftNoveltyEntity>>

    @Query("SELECT * FROM shift_novelties WHERE id = :id")
    suspend fun getById(id: String): ShiftNoveltyEntity?

    @Query("SELECT * FROM shift_novelties WHERE shiftId = :shiftId AND type IN ('ABSENCE','CANCELLATION') LIMIT 1")
    suspend fun getStateController(shiftId: String): ShiftNoveltyEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ShiftNoveltyEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(entity: ShiftNoveltyEntity): Int

    @Query("DELETE FROM shift_novelties WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM shift_novelties WHERE shiftId = :shiftId AND type IN ('ABSENCE','CANCELLATION')")
    suspend fun deleteStateControllers(shiftId: String): Int

    @Query("DELETE FROM shift_novelties WHERE relatedShiftId = :shiftId")
    suspend fun deleteLinksToShift(shiftId: String): Int

    @Query("SELECT * FROM formal_shift_changes WHERE shiftId = :shiftId LIMIT 1")
    fun observeFormalChange(shiftId: String): Flow<FormalShiftChangeEntity?>

    @Query("SELECT * FROM formal_shift_changes WHERE shiftId = :shiftId LIMIT 1")
    suspend fun getFormalChange(shiftId: String): FormalShiftChangeEntity?

    @Upsert
    suspend fun upsertFormalChange(entity: FormalShiftChangeEntity)

    @Query("DELETE FROM formal_shift_changes WHERE shiftId = :shiftId")
    suspend fun deleteFormalChange(shiftId: String): Int
}
