package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.blackatsystems.miguardia.core.database.entity.ShiftEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftWorkSnapshotEntity
import kotlinx.coroutines.flow.Flow

internal data class ShiftWithWorkSnapshotRow(
    @Embedded val shift: ShiftEntity,
    @Embedded val snapshot: ShiftWorkSnapshotEntity,
)

@Dao
internal interface V2ShiftDao {
    @Transaction
    @Query(
        """SELECT shifts.*, shift_work_snapshots.*
            FROM shift_work_snapshots
            JOIN shifts ON shifts.id = shift_work_snapshots.shiftId
            WHERE shift_work_snapshots.timelineId = :timelineId
              AND shift_work_snapshots.sector = :sector
            ORDER BY shifts.startEpochMillis, shifts.id""",
    )
    fun observeAll(
        timelineId: String,
        sector: String,
    ): Flow<List<ShiftWithWorkSnapshotRow>>

    @Query("SELECT * FROM shift_work_snapshots WHERE shiftId = :shiftId")
    fun observeSnapshot(shiftId: String): Flow<ShiftWorkSnapshotEntity?>

    @Query("SELECT * FROM shift_work_snapshots WHERE shiftId = :shiftId")
    suspend fun getSnapshot(shiftId: String): ShiftWorkSnapshotEntity?

    @Transaction
    @Query(
        """SELECT shifts.*, shift_work_snapshots.*
            FROM shifts
            JOIN shift_work_snapshots ON shift_work_snapshots.shiftId = shifts.id
            WHERE shifts.id = :shiftId""",
    )
    suspend fun getShiftWithSnapshot(shiftId: String): ShiftWithWorkSnapshotRow?

    @Transaction
    @Query(
        """SELECT shifts.*, shift_work_snapshots.*
            FROM shift_work_snapshots
            JOIN shifts ON shifts.id = shift_work_snapshots.shiftId
            ORDER BY shifts.startEpochMillis, shifts.id""",
    )
    suspend fun getAllShiftsWithSnapshots(): List<ShiftWithWorkSnapshotRow>

    @Transaction
    @Query(
        """SELECT shifts.*, shift_work_snapshots.*
            FROM shift_work_snapshots
            JOIN shifts ON shifts.id = shift_work_snapshots.shiftId
            WHERE shift_work_snapshots.workPlaceId = :workPlaceId
            ORDER BY shifts.startEpochMillis, shifts.id""",
    )
    suspend fun getShiftsWithSnapshotsForWorkPlace(
        workPlaceId: String,
    ): List<ShiftWithWorkSnapshotRow>

    @Query("SELECT COUNT(*) FROM shift_work_snapshots")
    fun observeSnapshotCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM shift_work_snapshots")
    suspend fun getSnapshotCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM shift_work_snapshots WHERE shiftId = :shiftId)")
    suspend fun hasSnapshot(shiftId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertShift(entity: ShiftEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSnapshot(entity: ShiftWorkSnapshotEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateShift(entity: ShiftEntity): Int

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateSnapshot(entity: ShiftWorkSnapshotEntity): Int

    @Query("DELETE FROM shifts WHERE id = :shiftId")
    suspend fun deleteShiftAndOwnedSnapshot(shiftId: String): Int

    @Query("DELETE FROM shifts WHERE id IN (:shiftIds)")
    suspend fun deleteShiftsAndOwnedSnapshots(shiftIds: List<String>): Int

    @Transaction
    suspend fun insertPair(
        shift: ShiftEntity,
        snapshot: ShiftWorkSnapshotEntity,
    ) {
        insertShift(shift)
        insertSnapshot(snapshot)
    }

    @Transaction
    suspend fun updatePair(
        shift: ShiftEntity,
        snapshot: ShiftWorkSnapshotEntity,
    ): Pair<Int, Int> = updateShift(shift) to updateSnapshot(snapshot)
}
