package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.blackatsystems.miguardia.core.database.entity.AvailabilityWindowEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface AvailabilityWindowDao {
    @Query(
        """SELECT * FROM availability_windows
            WHERE timelineId = :timelineId AND sector = :sector
            ORDER BY startEpochMillis, id""",
    )
    fun observeAll(timelineId: String, sector: String): Flow<List<AvailabilityWindowEntity>>

    @Query(
        """SELECT * FROM availability_windows
            WHERE timelineId = :timelineId AND sector = :sector AND ownerLocalDate = :ownerLocalDate
            ORDER BY startEpochMillis, id""",
    )
    fun observeOn(
        timelineId: String,
        sector: String,
        ownerLocalDate: String,
    ): Flow<List<AvailabilityWindowEntity>>

    @Query("SELECT * FROM availability_windows WHERE id = :id")
    suspend fun getById(id: String): AvailabilityWindowEntity?

    @Query("SELECT * FROM availability_windows ORDER BY timelineId, sector, startEpochMillis, id")
    suspend fun getAll(): List<AvailabilityWindowEntity>

    @Query(
        """SELECT * FROM availability_windows
            WHERE timelineId = :timelineId
              AND sector = :sector
              AND id != :excludedId
              AND startEpochMillis < :windowEndEpochMillis
              AND endEpochMillis > :windowStartEpochMillis
            ORDER BY startEpochMillis, id""",
    )
    suspend fun getOverlapping(
        timelineId: String,
        sector: String,
        excludedId: String,
        windowStartEpochMillis: Long,
        windowEndEpochMillis: Long,
    ): List<AvailabilityWindowEntity>

    @Query(INVALID_AVAILABILITY_COUNT_QUERY)
    fun observeInvalidRowCount(): Flow<Int>

    @Query(INVALID_AVAILABILITY_COUNT_QUERY)
    suspend fun getInvalidRowCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AvailabilityWindowEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(entity: AvailabilityWindowEntity): Int

    @Query("DELETE FROM availability_windows WHERE id = :id")
    suspend fun delete(id: String): Int
}

private const val INVALID_AVAILABILITY_COUNT_QUERY: String = """
    SELECT COUNT(*)
    FROM availability_windows AS availability
    LEFT JOIN work_configuration_roots AS root
        ON root.timelineId = availability.timelineId
    LEFT JOIN work_configuration_revisions AS revision
        ON revision.id = availability.configurationRevisionId
       AND revision.timelineId = availability.timelineId
       AND revision.sector = availability.sector
    WHERE root.timelineId IS NULL
       OR revision.id IS NULL
       OR availability.sector NOT IN ('PRIVATE_SECURITY', 'POLICE', 'NURSING', 'MEDICINE')
       OR availability.labelSnapshot NOT IN ('Guardia pasiva', 'Disponible para llamado', 'Retén')
       OR availability.startEpochMillis >= availability.endEpochMillis
       OR availability.startEpochMillis % 60000 != 0
       OR availability.endEpochMillis % 60000 != 0
       OR availability.createdAtEpochMillis > availability.updatedAtEpochMillis
"""
