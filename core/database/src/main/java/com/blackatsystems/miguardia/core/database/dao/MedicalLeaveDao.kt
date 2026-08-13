package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.blackatsystems.miguardia.core.database.entity.MedicalLeaveEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface MedicalLeaveDao {
    @Query(
        """SELECT * FROM medical_leaves
            WHERE startDate <= :endDateInclusive AND endDateInclusive >= :startDateInclusive
            ORDER BY startDate, endDateInclusive, id""",
    )
    fun observeIntersecting(
        startDateInclusive: String,
        endDateInclusive: String,
    ): Flow<List<MedicalLeaveEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MedicalLeaveEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(entity: MedicalLeaveEntity): Int

    @Query("DELETE FROM medical_leaves WHERE id = :id")
    suspend fun delete(id: String): Int
}
