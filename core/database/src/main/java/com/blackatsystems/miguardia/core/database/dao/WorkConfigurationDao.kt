package com.blackatsystems.miguardia.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.blackatsystems.miguardia.core.database.entity.PerPeriodHoursDefinitionEntity
import com.blackatsystems.miguardia.core.database.entity.PerPeriodHoursValueEntity
import com.blackatsystems.miguardia.core.database.entity.WorkConfigurationRevisionEntity
import com.blackatsystems.miguardia.core.database.entity.WorkConfigurationRootEntity
import kotlinx.coroutines.flow.Flow

internal data class PerPeriodHoursDefinitionWithValues(
    @Embedded val definition: PerPeriodHoursDefinitionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "definitionId",
    )
    val values: List<PerPeriodHoursValueEntity>,
)

internal data class WorkConfigurationRootWithRelations(
    @Embedded val root: WorkConfigurationRootEntity,
    @Relation(
        parentColumn = "timelineId",
        entityColumn = "timelineId",
    )
    val revisions: List<WorkConfigurationRevisionEntity>,
    @Relation(
        entity = PerPeriodHoursDefinitionEntity::class,
        parentColumn = "timelineId",
        entityColumn = "timelineId",
    )
    val perPeriodDefinitions: List<PerPeriodHoursDefinitionWithValues>,
)

@Dao
internal interface WorkConfigurationDao {
    @Transaction
    @Query("SELECT * FROM work_configuration_roots ORDER BY singletonSlot, timelineId")
    fun observeRoots(): Flow<List<WorkConfigurationRootWithRelations>>

    @Transaction
    @Query("SELECT * FROM work_configuration_roots ORDER BY singletonSlot, timelineId")
    suspend fun getRoots(): List<WorkConfigurationRootWithRelations>

    @Query(
        """SELECT
            (SELECT COUNT(*)
                FROM per_period_hours_definitions AS definition
                LEFT JOIN work_configuration_roots AS root
                    ON root.timelineId = definition.timelineId
                WHERE root.timelineId IS NULL) +
            (SELECT COUNT(*)
                FROM work_configuration_revisions AS revision
                LEFT JOIN work_configuration_roots AS root
                    ON root.timelineId = revision.timelineId
                WHERE root.timelineId IS NULL) +
            (SELECT COUNT(*)
                FROM work_configuration_revisions AS revision
                LEFT JOIN per_period_hours_definitions AS definition
                    ON definition.id = revision.perPeriodDefinitionId
                WHERE revision.perPeriodDefinitionId IS NOT NULL
                    AND definition.id IS NULL) +
            (SELECT COUNT(*)
                FROM per_period_hours_values AS value
                LEFT JOIN per_period_hours_definitions AS definition
                    ON definition.id = value.definitionId
                WHERE definition.id IS NULL)""",
    )
    fun observeOrphanRowCount(): Flow<Int>

    @Query(
        """SELECT
            (SELECT COUNT(*)
                FROM per_period_hours_definitions AS definition
                LEFT JOIN work_configuration_roots AS root
                    ON root.timelineId = definition.timelineId
                WHERE root.timelineId IS NULL) +
            (SELECT COUNT(*)
                FROM work_configuration_revisions AS revision
                LEFT JOIN work_configuration_roots AS root
                    ON root.timelineId = revision.timelineId
                WHERE root.timelineId IS NULL) +
            (SELECT COUNT(*)
                FROM work_configuration_revisions AS revision
                LEFT JOIN per_period_hours_definitions AS definition
                    ON definition.id = revision.perPeriodDefinitionId
                WHERE revision.perPeriodDefinitionId IS NOT NULL
                    AND definition.id IS NULL) +
            (SELECT COUNT(*)
                FROM per_period_hours_values AS value
                LEFT JOIN per_period_hours_definitions AS definition
                    ON definition.id = value.definitionId
                WHERE definition.id IS NULL)""",
    )
    suspend fun getOrphanRowCount(): Int

    @Query("SELECT * FROM per_period_hours_definitions WHERE id = :id")
    suspend fun getDefinitionById(id: String): PerPeriodHoursDefinitionEntity?

    @Query("SELECT * FROM work_configuration_revisions WHERE id = :id")
    suspend fun getRevisionById(id: String): WorkConfigurationRevisionEntity?

    @Query(
        """SELECT id FROM work_configuration_revisions
            WHERE timelineId = :timelineId AND effectiveFrom = :effectiveFrom
            LIMIT 1""",
    )
    suspend fun findRevisionIdByEffectiveDate(timelineId: String, effectiveFrom: String): String?

    @Query("SELECT * FROM per_period_hours_values WHERE id = :id")
    suspend fun getValueById(id: String): PerPeriodHoursValueEntity?

    @Query(
        """SELECT id FROM per_period_hours_values
            WHERE definitionId = :definitionId AND windowStartInclusive = :windowStartInclusive
            LIMIT 1""",
    )
    suspend fun findValueIdByWindow(
        definitionId: String,
        windowStartInclusive: String,
    ): String?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRoot(entity: WorkConfigurationRootEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDefinition(entity: PerPeriodHoursDefinitionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevision(entity: WorkConfigurationRevisionEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateRevision(entity: WorkConfigurationRevisionEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertValue(entity: PerPeriodHoursValueEntity)

    @Query("DELETE FROM per_period_hours_values WHERE definitionId = :definitionId")
    suspend fun deleteValuesForDefinition(definitionId: String): Int

    @Query(
        """DELETE FROM per_period_hours_definitions
            WHERE id = :definitionId
              AND NOT EXISTS (
                  SELECT 1 FROM work_configuration_revisions
                  WHERE perPeriodDefinitionId = :definitionId
              )""",
    )
    suspend fun deleteDefinitionIfUnused(definitionId: String): Int

    @Query(
        """UPDATE per_period_hours_values
            SET requiredMinutes = :requiredMinutes
            WHERE id = :id
              AND definitionId = :definitionId
              AND windowStartInclusive = :windowStartInclusive
              AND windowEndExclusive = :windowEndExclusive""",
    )
    suspend fun updateValueMinutes(
        id: String,
        definitionId: String,
        windowStartInclusive: String,
        windowEndExclusive: String,
        requiredMinutes: Long,
    ): Int
}
