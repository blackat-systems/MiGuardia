package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.blackatsystems.miguardia.core.database.entity.ExplicitDayStatusEntity
import com.blackatsystems.miguardia.core.database.entity.IndependentExtraWorkRecordEntity
import com.blackatsystems.miguardia.core.database.entity.MedicalLeaveEntity
import com.blackatsystems.miguardia.core.database.entity.VacationEntity
import kotlinx.coroutines.flow.Flow

internal data class IndependentShiftOccupancyRow(
    val shiftId: String,
    val zoneId: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val status: String,
    val shiftUpdatedAtEpochMillis: Long,
    val actualUpdatedAtEpochMillis: Long?,
)

@Dao
internal interface IndependentExtraWorkDao {
    @Query(
        """SELECT * FROM independent_extra_work_records
            WHERE timelineId = :timelineId AND sector = :sector
            ORDER BY startEpochMillis, id""",
    )
    fun observeAll(timelineId: String, sector: String): Flow<List<IndependentExtraWorkRecordEntity>>

    @Query(
        """SELECT * FROM independent_extra_work_records
            WHERE timelineId = :timelineId
              AND sector = :sector
              AND ownerLocalDate = :ownerLocalDate
            ORDER BY startEpochMillis, id""",
    )
    fun observeOn(
        timelineId: String,
        sector: String,
        ownerLocalDate: String,
    ): Flow<List<IndependentExtraWorkRecordEntity>>

    @Query("SELECT * FROM independent_extra_work_records WHERE id = :id")
    suspend fun getById(id: String): IndependentExtraWorkRecordEntity?

    @Query("SELECT * FROM independent_extra_work_records ORDER BY timelineId, sector, startEpochMillis, id")
    suspend fun getAll(): List<IndependentExtraWorkRecordEntity>

    @Query(
        """SELECT * FROM independent_extra_work_records
            WHERE timelineId = :timelineId
              AND sector = :sector
              AND id != :excludedId
              AND startEpochMillis < :windowEndEpochMillis
              AND endEpochMillis > :windowStartEpochMillis
            ORDER BY startEpochMillis, id""",
    )
    suspend fun getOverlappingExtras(
        timelineId: String,
        sector: String,
        excludedId: String,
        windowStartEpochMillis: Long,
        windowEndEpochMillis: Long,
    ): List<IndependentExtraWorkRecordEntity>

    @Query(
        """SELECT shifts.id AS shiftId,
                   shifts.zoneId AS zoneId,
                   COALESCE(actual.actualStartEpochMillis, shifts.startEpochMillis) AS startEpochMillis,
                   COALESCE(actual.actualEndEpochMillis, shifts.endEpochMillis) AS endEpochMillis,
                   shifts.status AS status,
                   shifts.updatedAtEpochMillis AS shiftUpdatedAtEpochMillis,
                   actual.updatedAtEpochMillis AS actualUpdatedAtEpochMillis
            FROM shifts
            JOIN shift_work_snapshots ON shift_work_snapshots.shiftId = shifts.id
            LEFT JOIN shift_actual_records AS actual
                ON actual.shiftId = shifts.id
               AND actual.timelineId = shift_work_snapshots.timelineId
               AND actual.sector = shift_work_snapshots.sector
            WHERE shift_work_snapshots.timelineId = :timelineId
              AND shift_work_snapshots.sector = :sector
              AND shifts.status = 'PLANNED'
              AND COALESCE(actual.actualStartEpochMillis, shifts.startEpochMillis) < :windowEndEpochMillis
              AND COALESCE(actual.actualEndEpochMillis, shifts.endEpochMillis) > :windowStartEpochMillis
            ORDER BY startEpochMillis, shifts.id""",
    )
    suspend fun getOverlappingShifts(
        timelineId: String,
        sector: String,
        windowStartEpochMillis: Long,
        windowEndEpochMillis: Long,
    ): List<IndependentShiftOccupancyRow>

    @Query(
        """SELECT * FROM medical_leaves
            WHERE startDate <= :endDateInclusive AND endDateInclusive >= :startDateInclusive
            ORDER BY startDate, id""",
    )
    suspend fun getMedicalLeaves(
        startDateInclusive: String,
        endDateInclusive: String,
    ): List<MedicalLeaveEntity>

    @Query(
        """SELECT * FROM vacations
            WHERE startDate <= :endDateInclusive AND endDateInclusive >= :startDateInclusive
            ORDER BY startDate, id""",
    )
    suspend fun getVacations(
        startDateInclusive: String,
        endDateInclusive: String,
    ): List<VacationEntity>

    @Query(
        """SELECT * FROM explicit_day_statuses
            WHERE localDate BETWEEN :startDateInclusive AND :endDateInclusive
            ORDER BY localDate""",
    )
    suspend fun getExplicitDayStatuses(
        startDateInclusive: String,
        endDateInclusive: String,
    ): List<ExplicitDayStatusEntity>

    @Query(INVALID_INDEPENDENT_EXTRA_COUNT_QUERY)
    fun observeInvalidRowCount(): Flow<Int>

    @Query(INVALID_INDEPENDENT_EXTRA_COUNT_QUERY)
    suspend fun getInvalidRowCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: IndependentExtraWorkRecordEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(entity: IndependentExtraWorkRecordEntity): Int

    @Query("DELETE FROM independent_extra_work_records WHERE id = :id")
    suspend fun delete(id: String): Int
}

private const val INVALID_INDEPENDENT_EXTRA_COUNT_QUERY: String = """
    SELECT COUNT(*)
    FROM independent_extra_work_records AS extra
    LEFT JOIN work_configuration_roots AS root
        ON root.timelineId = extra.timelineId
    LEFT JOIN work_configuration_revisions AS revision
        ON revision.id = extra.configurationRevisionId
       AND revision.timelineId = extra.timelineId
       AND revision.sector = extra.sector
    LEFT JOIN work_places AS place
        ON place.id = extra.workPlaceId
       AND place.timelineId = extra.timelineId
       AND place.sector = extra.sector
       AND place.objectiveId = extra.objectiveId
    LEFT JOIN objectives AS objective ON objective.id = extra.objectiveId
    LEFT JOIN work_types AS work_type
        ON work_type.id = extra.workTypeId
       AND work_type.timelineId = extra.timelineId
       AND work_type.sector = extra.sector
    LEFT JOIN work_templates AS template
        ON template.id = extra.templateId
       AND template.timelineId = extra.timelineId
       AND template.sector = extra.sector
       AND template.workPlaceId = extra.workPlaceId
       AND template.objectiveId = extra.objectiveId
       AND template.workTypeId = extra.workTypeId
    LEFT JOIN extra_work_classes AS extra_class
        ON extra_class.id = extra.extraWorkClassId
       AND extra_class.timelineId = extra.timelineId
       AND extra_class.sector = extra.sector
    WHERE root.timelineId IS NULL
       OR revision.id IS NULL
       OR place.id IS NULL
       OR objective.id IS NULL
       OR work_type.id IS NULL
       OR (extra.templateId IS NOT NULL AND template.id IS NULL)
       OR extra_class.id IS NULL
       OR extra.sector NOT IN ('PRIVATE_SECURITY', 'POLICE', 'NURSING', 'MEDICINE')
       OR work_type.behavior != 'ACTIVE_WORK'
       OR extra.workTypeBehaviorSnapshot != 'ACTIVE_WORK'
       OR extra.startEpochMillis >= extra.endEpochMillis
       OR extra.startEpochMillis % 60000 != 0
       OR extra.endEpochMillis % 60000 != 0
       OR extra.createdAtEpochMillis > extra.updatedAtEpochMillis
       OR extra.helpsMeetHoursReferenceSnapshot NOT IN (0, 1)
       OR extra.showDedicatedSummarySnapshot NOT IN (0, 1)
"""
