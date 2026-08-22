package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.blackatsystems.miguardia.core.database.entity.ObjectiveEntity
import com.blackatsystems.miguardia.core.database.entity.WorkPlaceEntity
import com.blackatsystems.miguardia.core.database.entity.WorkTemplateEntity
import com.blackatsystems.miguardia.core.database.entity.WorkTypeEntity
import com.blackatsystems.miguardia.core.database.entity.WorkplaceRuleRevisionEntity
import kotlinx.coroutines.flow.Flow

internal data class RecentWorkTemplateRow(
    @Embedded(prefix = "objective_") val objective: ObjectiveEntity,
    @Embedded(prefix = "workPlace_") val workPlace: WorkPlaceEntity,
    @Embedded(prefix = "workType_") val workType: WorkTypeEntity,
    @Embedded(prefix = "template_") val template: WorkTemplateEntity,
    val lastUsedAtEpochMillis: Long,
)

@Dao
internal interface WorkCatalogDao {
    @Query(
        """SELECT * FROM work_places
            WHERE timelineId = :timelineId AND sector = :sector
            ORDER BY createdAtEpochMillis, id""",
    )
    fun observeWorkPlaces(timelineId: String, sector: String): Flow<List<WorkPlaceEntity>>

    @Query(
        """SELECT * FROM work_types
            WHERE timelineId = :timelineId AND sector = :sector
            ORDER BY normalizedNameKey, id""",
    )
    fun observeWorkTypes(timelineId: String, sector: String): Flow<List<WorkTypeEntity>>

    @Query(
        """SELECT * FROM work_templates
            WHERE timelineId = :timelineId AND sector = :sector
            ORDER BY workPlaceId, startTime, endTime, id""",
    )
    fun observeWorkTemplates(timelineId: String, sector: String): Flow<List<WorkTemplateEntity>>

    @Query(
        """SELECT * FROM workplace_rule_revisions
            WHERE timelineId = :timelineId AND sector = :sector
            ORDER BY workPlaceId, effectiveFrom, id""",
    )
    fun observeWorkplaceRuleRevisions(
        timelineId: String,
        sector: String,
    ): Flow<List<WorkplaceRuleRevisionEntity>>

    @Query(
        """SELECT * FROM work_places
            WHERE timelineId = :timelineId AND sector = :sector
            ORDER BY createdAtEpochMillis, id""",
    )
    suspend fun getWorkPlaces(timelineId: String, sector: String): List<WorkPlaceEntity>

    @Query(
        """SELECT * FROM work_types
            WHERE timelineId = :timelineId AND sector = :sector
            ORDER BY normalizedNameKey, id""",
    )
    suspend fun getWorkTypes(timelineId: String, sector: String): List<WorkTypeEntity>

    @Query(
        """SELECT * FROM work_templates
            WHERE timelineId = :timelineId AND sector = :sector
            ORDER BY workPlaceId, startTime, endTime, id""",
    )
    suspend fun getWorkTemplates(timelineId: String, sector: String): List<WorkTemplateEntity>

    @Query(
        """SELECT * FROM workplace_rule_revisions
            WHERE timelineId = :timelineId AND sector = :sector
            ORDER BY workPlaceId, effectiveFrom, id""",
    )
    suspend fun getWorkplaceRuleRevisions(
        timelineId: String,
        sector: String,
    ): List<WorkplaceRuleRevisionEntity>

    @Query("SELECT * FROM work_places ORDER BY timelineId, sector, createdAtEpochMillis, id")
    suspend fun getAllWorkPlaces(): List<WorkPlaceEntity>

    @Query("SELECT * FROM work_types ORDER BY timelineId, sector, normalizedNameKey, id")
    suspend fun getAllWorkTypes(): List<WorkTypeEntity>

    @Query(
        """SELECT * FROM work_templates
            ORDER BY timelineId, sector, workPlaceId, startTime, endTime, id""",
    )
    suspend fun getAllWorkTemplates(): List<WorkTemplateEntity>

    @Query(
        """SELECT * FROM workplace_rule_revisions
            ORDER BY timelineId, sector, workPlaceId, effectiveFrom, id""",
    )
    suspend fun getAllWorkplaceRuleRevisions(): List<WorkplaceRuleRevisionEntity>

    @Query("SELECT * FROM work_places WHERE id = :id")
    suspend fun getWorkPlaceById(id: String): WorkPlaceEntity?

    @Query(
        """SELECT * FROM work_places
            WHERE timelineId = :timelineId AND sector = :sector AND objectiveId = :objectiveId
            LIMIT 1""",
    )
    suspend fun getWorkPlaceByContext(
        timelineId: String,
        sector: String,
        objectiveId: String,
    ): WorkPlaceEntity?

    @Query("SELECT COUNT(*) FROM work_places WHERE objectiveId = :objectiveId")
    suspend fun countWorkPlacesForObjective(objectiveId: String): Int

    @Query("SELECT * FROM work_types WHERE id = :id")
    suspend fun getWorkTypeById(id: String): WorkTypeEntity?

    @Query(
        """SELECT id FROM work_types
            WHERE timelineId = :timelineId
              AND sector = :sector
              AND normalizedNameKey = :normalizedNameKey
            LIMIT 1""",
    )
    suspend fun findWorkTypeIdByNameKey(
        timelineId: String,
        sector: String,
        normalizedNameKey: String,
    ): String?

    @Query("SELECT * FROM work_templates WHERE id = :id")
    suspend fun getWorkTemplateById(id: String): WorkTemplateEntity?

    @Query(
        """SELECT id FROM work_templates
            WHERE workPlaceId = :workPlaceId
              AND workTypeId = :workTypeId
              AND startTime = :startTime
              AND endTime = :endTime
            LIMIT 1""",
    )
    suspend fun findExactWorkTemplateId(
        workPlaceId: String,
        workTypeId: String,
        startTime: String,
        endTime: String,
    ): String?

    @Query(
        """SELECT * FROM workplace_rule_revisions
            WHERE workPlaceId = :workPlaceId
            ORDER BY effectiveFrom, id""",
    )
    suspend fun getRuleRevisionsForWorkPlace(workPlaceId: String): List<WorkplaceRuleRevisionEntity>

    @Query(
        """SELECT * FROM workplace_rule_revisions
            WHERE workPlaceId = :workPlaceId AND effectiveFrom <= :localDate
            ORDER BY effectiveFrom DESC, id DESC
            LIMIT 1""",
    )
    suspend fun getRuleRevisionAt(
        workPlaceId: String,
        localDate: String,
    ): WorkplaceRuleRevisionEntity?

    @Query(
        """SELECT id FROM workplace_rule_revisions
            WHERE workPlaceId = :workPlaceId AND effectiveFrom = :effectiveFrom
            LIMIT 1""",
    )
    suspend fun findRuleRevisionIdByEffectiveDate(
        workPlaceId: String,
        effectiveFrom: String,
    ): String?

    @Query(
        """WITH recent_usage AS (
                SELECT snapshot.templateId AS templateId,
                       MAX(shift.createdAtEpochMillis) AS lastUsedAtEpochMillis
                FROM shift_work_snapshots AS snapshot
                JOIN shifts AS shift ON shift.id = snapshot.shiftId
                GROUP BY snapshot.templateId
            )
            SELECT
                objective.id AS objective_id,
                objective.fullName AS objective_fullName,
                objective.abbreviation AS objective_abbreviation,
                objective.address AS objective_address,
                objective.note AS objective_note,
                objective.isActive AS objective_isActive,
                objective.createdAtEpochMillis AS objective_createdAtEpochMillis,
                objective.updatedAtEpochMillis AS objective_updatedAtEpochMillis,
                work_place.id AS workPlace_id,
                work_place.timelineId AS workPlace_timelineId,
                work_place.sector AS workPlace_sector,
                work_place.objectiveId AS workPlace_objectiveId,
                work_place.isActive AS workPlace_isActive,
                work_place.createdAtEpochMillis AS workPlace_createdAtEpochMillis,
                work_place.updatedAtEpochMillis AS workPlace_updatedAtEpochMillis,
                work_type.id AS workType_id,
                work_type.timelineId AS workType_timelineId,
                work_type.sector AS workType_sector,
                work_type.name AS workType_name,
                work_type.normalizedNameKey AS workType_normalizedNameKey,
                work_type.behavior AS workType_behavior,
                work_type.isActive AS workType_isActive,
                work_type.createdAtEpochMillis AS workType_createdAtEpochMillis,
                work_type.updatedAtEpochMillis AS workType_updatedAtEpochMillis,
                template.id AS template_id,
                template.timelineId AS template_timelineId,
                template.sector AS template_sector,
                template.workPlaceId AS template_workPlaceId,
                template.objectiveId AS template_objectiveId,
                template.workTypeId AS template_workTypeId,
                template.startTime AS template_startTime,
                template.endTime AS template_endTime,
                template.colorArgb AS template_colorArgb,
                template.isActive AS template_isActive,
                template.legacyScheduleCombinationId AS template_legacyScheduleCombinationId,
                template.createdAtEpochMillis AS template_createdAtEpochMillis,
                template.updatedAtEpochMillis AS template_updatedAtEpochMillis,
                recent_usage.lastUsedAtEpochMillis AS lastUsedAtEpochMillis
            FROM recent_usage
            JOIN work_templates AS template ON template.id = recent_usage.templateId
            JOIN work_places AS work_place ON work_place.id = template.workPlaceId
            JOIN objectives AS objective ON objective.id = work_place.objectiveId
            JOIN work_types AS work_type ON work_type.id = template.workTypeId
            WHERE template.timelineId = :timelineId
              AND template.sector = :sector
              AND template.isActive = 1
              AND work_place.isActive = 1
              AND work_type.isActive = 1
            ORDER BY recent_usage.lastUsedAtEpochMillis DESC, template.id
            LIMIT :limit""",
    )
    fun observeRecentlyUsed(
        timelineId: String,
        sector: String,
        limit: Int,
    ): Flow<List<RecentWorkTemplateRow>>

    @Query(INVALID_V2_ROW_COUNT_QUERY)
    fun observeInvalidV2RowCount(): Flow<Int>

    @Query(INVALID_V2_ROW_COUNT_QUERY)
    suspend fun getInvalidV2RowCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkPlace(entity: WorkPlaceEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateWorkPlace(entity: WorkPlaceEntity): Int

    @Query(
        """UPDATE work_places
            SET isActive = :isActive, updatedAtEpochMillis = :updatedAtEpochMillis
            WHERE id = :id""",
    )
    suspend fun setWorkPlaceActive(
        id: String,
        isActive: Boolean,
        updatedAtEpochMillis: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkType(entity: WorkTypeEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateWorkType(entity: WorkTypeEntity): Int

    @Query(
        """UPDATE work_types
            SET isActive = :isActive, updatedAtEpochMillis = :updatedAtEpochMillis
            WHERE id = :id""",
    )
    suspend fun setWorkTypeActive(
        id: String,
        isActive: Boolean,
        updatedAtEpochMillis: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkTemplate(entity: WorkTemplateEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateWorkTemplate(entity: WorkTemplateEntity): Int

    @Query(
        """UPDATE work_templates
            SET isActive = :isActive, updatedAtEpochMillis = :updatedAtEpochMillis
            WHERE id = :id""",
    )
    suspend fun setWorkTemplateActive(
        id: String,
        isActive: Boolean,
        updatedAtEpochMillis: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkplaceRuleRevision(entity: WorkplaceRuleRevisionEntity)
}

private const val INVALID_V2_ROW_COUNT_QUERY: String = """
    SELECT
        (SELECT COUNT(*)
            FROM work_places AS place
            LEFT JOIN work_configuration_roots AS root
                ON root.timelineId = place.timelineId
            LEFT JOIN objectives AS objective
                ON objective.id = place.objectiveId
            WHERE root.timelineId IS NULL
               OR objective.id IS NULL
               OR place.sector NOT IN ('PRIVATE_SECURITY', 'POLICE', 'NURSING', 'MEDICINE')
               OR place.isActive NOT IN (0, 1)) +
        (SELECT COUNT(*)
            FROM work_types AS work_type
            LEFT JOIN work_configuration_roots AS root
                ON root.timelineId = work_type.timelineId
            WHERE root.timelineId IS NULL
               OR work_type.sector NOT IN ('PRIVATE_SECURITY', 'POLICE', 'NURSING', 'MEDICINE')
               OR work_type.behavior != 'ACTIVE_WORK'
               OR work_type.isActive NOT IN (0, 1)) +
        (SELECT COUNT(*)
            FROM work_templates AS template
            LEFT JOIN work_configuration_roots AS root
                ON root.timelineId = template.timelineId
            LEFT JOIN work_places AS place
                ON place.id = template.workPlaceId
               AND place.timelineId = template.timelineId
               AND place.sector = template.sector
               AND place.objectiveId = template.objectiveId
            LEFT JOIN work_types AS work_type
                ON work_type.id = template.workTypeId
               AND work_type.timelineId = template.timelineId
               AND work_type.sector = template.sector
            LEFT JOIN schedule_combinations AS legacy_schedule
                ON legacy_schedule.id = template.legacyScheduleCombinationId
            WHERE root.timelineId IS NULL
               OR place.id IS NULL
               OR work_type.id IS NULL
               OR (template.legacyScheduleCombinationId IS NOT NULL AND legacy_schedule.id IS NULL)
               OR template.isActive NOT IN (0, 1)) +
        (SELECT COUNT(*)
            FROM workplace_rule_revisions AS rule
            LEFT JOIN work_configuration_roots AS root
                ON root.timelineId = rule.timelineId
            LEFT JOIN work_places AS place
                ON place.id = rule.workPlaceId
               AND place.timelineId = rule.timelineId
               AND place.sector = rule.sector
               AND place.objectiveId = rule.objectiveId
            WHERE root.timelineId IS NULL
               OR place.id IS NULL
               OR rule.nightRuleCode NOT IN ('DISABLED', 'DEFINED')
               OR (rule.nightRuleCode = 'DISABLED' AND (
                    rule.nightStartTime IS NOT NULL
                    OR rule.nightEndTime IS NOT NULL
                    OR rule.nightDifferentTreatment IS NOT NULL
                    OR rule.nightShowDedicatedSummary IS NOT NULL))
               OR (rule.nightRuleCode = 'DEFINED' AND (
                    rule.nightStartTime IS NULL
                    OR rule.nightEndTime IS NULL
                    OR rule.nightStartTime = rule.nightEndTime
                    OR rule.nightDifferentTreatment IS NULL
                    OR rule.nightDifferentTreatment NOT IN (0, 1)
                    OR rule.nightShowDedicatedSummary IS NULL
                    OR rule.nightShowDedicatedSummary NOT IN (0, 1)))
               OR rule.weekendRuleCode NOT IN ('NONE', 'SATURDAY', 'SUNDAY', 'SATURDAY_AND_SUNDAY')
               OR (rule.weekendRuleCode = 'NONE' AND (
                    rule.weekendDifferentTreatment IS NOT NULL
                    OR rule.weekendShowDedicatedSummary IS NOT NULL))
               OR (rule.weekendRuleCode != 'NONE' AND (
                    rule.weekendDifferentTreatment IS NULL
                    OR rule.weekendDifferentTreatment NOT IN (0, 1)
                    OR rule.weekendShowDedicatedSummary IS NULL
                    OR rule.weekendShowDedicatedSummary NOT IN (0, 1)))
               OR rule.holidayDifferentTreatment NOT IN (0, 1)
               OR rule.holidayShowDedicatedSummary NOT IN (0, 1)) +
        (SELECT COUNT(*)
            FROM shift_work_snapshots AS snapshot
            LEFT JOIN shifts AS shift ON shift.id = snapshot.shiftId
            LEFT JOIN work_configuration_roots AS root
                ON root.timelineId = snapshot.timelineId
            LEFT JOIN work_configuration_revisions AS configuration_revision
                ON configuration_revision.id = snapshot.configurationRevisionId
            LEFT JOIN work_places AS place
                ON place.id = snapshot.workPlaceId
               AND place.timelineId = snapshot.timelineId
               AND place.sector = snapshot.sector
               AND place.objectiveId = snapshot.objectiveId
            LEFT JOIN work_types AS work_type
                ON work_type.id = snapshot.workTypeId
               AND work_type.timelineId = snapshot.timelineId
               AND work_type.sector = snapshot.sector
            LEFT JOIN work_templates AS template
                ON template.id = snapshot.templateId
               AND template.timelineId = snapshot.timelineId
               AND template.sector = snapshot.sector
               AND template.workPlaceId = snapshot.workPlaceId
               AND template.objectiveId = snapshot.objectiveId
               AND template.workTypeId = snapshot.workTypeId
            WHERE shift.id IS NULL
               OR root.timelineId IS NULL
               OR configuration_revision.id IS NULL
               OR configuration_revision.timelineId != snapshot.timelineId
               OR configuration_revision.sector != snapshot.sector
               OR place.id IS NULL
               OR work_type.id IS NULL
               OR template.id IS NULL
               OR snapshot.workTypeBehaviorSnapshot != 'ACTIVE_WORK') +
        (SELECT COUNT(*)
            FROM work_places AS place
            WHERE NOT EXISTS (
                SELECT 1
                FROM workplace_rule_revisions AS rule
                WHERE rule.workPlaceId = place.id
                  AND rule.timelineId = place.timelineId
                  AND rule.sector = place.sector
                  AND rule.objectiveId = place.objectiveId
            ))
"""
