package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.blackatsystems.miguardia.core.database.dao.ExplicitDayStatusDao
import com.blackatsystems.miguardia.core.database.dao.HolidayDao
import com.blackatsystems.miguardia.core.database.dao.MedicalLeaveDao
import com.blackatsystems.miguardia.core.database.dao.ObjectiveDao
import com.blackatsystems.miguardia.core.database.dao.RecurringPlanDao
import com.blackatsystems.miguardia.core.database.dao.SchedulePhotoDao
import com.blackatsystems.miguardia.core.database.dao.ShiftActualDao
import com.blackatsystems.miguardia.core.database.dao.ShiftDao
import com.blackatsystems.miguardia.core.database.dao.ShiftNoteDao
import com.blackatsystems.miguardia.core.database.dao.ShiftNotificationConfigDao
import com.blackatsystems.miguardia.core.database.dao.V2ShiftDao
import com.blackatsystems.miguardia.core.database.dao.VacationDao
import com.blackatsystems.miguardia.core.database.dao.WorkCatalogDao
import com.blackatsystems.miguardia.core.database.dao.WorkConfigurationDao
import com.blackatsystems.miguardia.core.database.entity.ExplicitDayStatusEntity
import com.blackatsystems.miguardia.core.database.entity.ExtraWorkClassEntity
import com.blackatsystems.miguardia.core.database.entity.HolidayEntity
import com.blackatsystems.miguardia.core.database.entity.MedicalLeaveEntity
import com.blackatsystems.miguardia.core.database.entity.ObjectiveEntity
import com.blackatsystems.miguardia.core.database.entity.PerPeriodHoursDefinitionEntity
import com.blackatsystems.miguardia.core.database.entity.PerPeriodHoursValueEntity
import com.blackatsystems.miguardia.core.database.entity.RecurringOccurrenceEntity
import com.blackatsystems.miguardia.core.database.entity.RecurringPlanEntity
import com.blackatsystems.miguardia.core.database.entity.RecurringPlanRevisionEntity
import com.blackatsystems.miguardia.core.database.entity.SchedulePhotoEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftActualRecordEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftExtraIntervalEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftNoteEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftNotificationConfigEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftNotificationReminderEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftWorkSnapshotEntity
import com.blackatsystems.miguardia.core.database.entity.VacationEntity
import com.blackatsystems.miguardia.core.database.entity.WorkConfigurationRevisionEntity
import com.blackatsystems.miguardia.core.database.entity.WorkConfigurationRootEntity
import com.blackatsystems.miguardia.core.database.entity.WorkPlaceEntity
import com.blackatsystems.miguardia.core.database.entity.WorkTemplateEntity
import com.blackatsystems.miguardia.core.database.entity.WorkTypeEntity
import com.blackatsystems.miguardia.core.database.entity.WorkplaceRuleRevisionEntity

@Database(
    entities = [
        ObjectiveEntity::class,
        ShiftEntity::class,
        ShiftWorkSnapshotEntity::class,
        ExplicitDayStatusEntity::class,
        MedicalLeaveEntity::class,
        HolidayEntity::class,
        ShiftNoteEntity::class,
        VacationEntity::class,
        SchedulePhotoEntity::class,
        ShiftNotificationConfigEntity::class,
        ShiftNotificationReminderEntity::class,
        WorkConfigurationRootEntity::class,
        PerPeriodHoursDefinitionEntity::class,
        WorkConfigurationRevisionEntity::class,
        PerPeriodHoursValueEntity::class,
        WorkPlaceEntity::class,
        WorkTypeEntity::class,
        WorkTemplateEntity::class,
        WorkplaceRuleRevisionEntity::class,
        RecurringPlanEntity::class,
        RecurringPlanRevisionEntity::class,
        RecurringOccurrenceEntity::class,
        ExtraWorkClassEntity::class,
        ShiftActualRecordEntity::class,
        ShiftExtraIntervalEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
internal abstract class MiGuardiaV2Database : RoomDatabase() {
    internal abstract fun objectiveDao(): ObjectiveDao
    internal abstract fun shiftDao(): ShiftDao
    internal abstract fun explicitDayStatusDao(): ExplicitDayStatusDao
    internal abstract fun medicalLeaveDao(): MedicalLeaveDao
    internal abstract fun holidayDao(): HolidayDao
    internal abstract fun shiftNoteDao(): ShiftNoteDao
    internal abstract fun vacationDao(): VacationDao
    internal abstract fun schedulePhotoDao(): SchedulePhotoDao
    internal abstract fun shiftNotificationConfigDao(): ShiftNotificationConfigDao
    internal abstract fun workConfigurationDao(): WorkConfigurationDao
    internal abstract fun workCatalogDao(): WorkCatalogDao
    internal abstract fun v2ShiftDao(): V2ShiftDao
    internal abstract fun recurringPlanDao(): RecurringPlanDao
    internal abstract fun shiftActualDao(): ShiftActualDao

    companion object {
        const val DATABASE_NAME: String = "miguardia-v2.db"

        fun build(
            context: Context,
            databaseName: String = DATABASE_NAME,
        ): MiGuardiaV2Database = Room.databaseBuilder(
            context.applicationContext,
            MiGuardiaV2Database::class.java,
            databaseName,
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `recurring_plans` (
                        `id` TEXT NOT NULL,
                        `timelineId` TEXT NOT NULL,
                        `sector` TEXT NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`timelineId`) REFERENCES `work_configuration_roots`(`timelineId`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )""".trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recurring_plans_timelineId_sector` " +
                        "ON `recurring_plans` (`timelineId`, `sector`)",
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `recurring_plan_revisions` (
                        `id` TEXT NOT NULL,
                        `planId` TEXT NOT NULL,
                        `revisionNumber` INTEGER NOT NULL,
                        `effectiveFrom` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `endDateInclusive` TEXT NOT NULL,
                        `patternKind` TEXT NOT NULL,
                        `weekdaysMask` INTEGER,
                        `intervalCount` INTEGER,
                        `monthlyOrdinal` TEXT,
                        `monthlyDayOfWeek` INTEGER,
                        `templateId` TEXT NOT NULL,
                        `workPlaceId` TEXT NOT NULL,
                        `objectiveId` TEXT NOT NULL,
                        `workTypeId` TEXT NOT NULL,
                        `objectiveNameSnapshot` TEXT NOT NULL,
                        `objectiveAbbreviationSnapshot` TEXT NOT NULL,
                        `objectiveAddressSnapshot` TEXT,
                        `workTypeNameSnapshot` TEXT NOT NULL,
                        `workTypeBehaviorSnapshot` TEXT NOT NULL,
                        `startTimeSnapshot` TEXT NOT NULL,
                        `endTimeSnapshot` TEXT NOT NULL,
                        `colorArgbSnapshot` INTEGER NOT NULL,
                        `positionSnapshot` TEXT,
                        `zoneId` TEXT NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`planId`) REFERENCES `recurring_plans`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`templateId`) REFERENCES `work_templates`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )""".trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_plan_revisions_planId_revisionNumber` " +
                        "ON `recurring_plan_revisions` (`planId`, `revisionNumber`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_plan_revisions_id_planId` " +
                        "ON `recurring_plan_revisions` (`id`, `planId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recurring_plan_revisions_planId_effectiveFrom_revisionNumber` " +
                        "ON `recurring_plan_revisions` (`planId`, `effectiveFrom`, `revisionNumber`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recurring_plan_revisions_templateId` " +
                        "ON `recurring_plan_revisions` (`templateId`)",
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `recurring_occurrences` (
                        `planId` TEXT NOT NULL,
                        `localDate` TEXT NOT NULL,
                        `revisionId` TEXT NOT NULL,
                        `shiftId` TEXT,
                        `state` TEXT NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`planId`, `localDate`),
                        FOREIGN KEY(`planId`) REFERENCES `recurring_plans`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`revisionId`, `planId`)
                            REFERENCES `recurring_plan_revisions`(`id`, `planId`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`shiftId`) REFERENCES `shifts`(`id`)
                            ON UPDATE NO ACTION ON DELETE SET NULL
                    )""".trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recurring_occurrences_revisionId_planId` " +
                        "ON `recurring_occurrences` (`revisionId`, `planId`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_occurrences_shiftId` " +
                        "ON `recurring_occurrences` (`shiftId`)",
                )
            }
        }

        internal val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_shift_work_snapshots_shiftId_timelineId_sector` " +
                        "ON `shift_work_snapshots` (`shiftId`, `timelineId`, `sector`)",
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `extra_work_classes` (
                        `id` TEXT NOT NULL,
                        `timelineId` TEXT NOT NULL,
                        `sector` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `normalizedNameKey` TEXT NOT NULL,
                        `helpsMeetHoursReference` INTEGER NOT NULL,
                        `showDedicatedSummary` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`timelineId`) REFERENCES `work_configuration_roots`(`timelineId`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )""".trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_extra_work_classes_timelineId` " +
                        "ON `extra_work_classes` (`timelineId`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_extra_work_classes_timelineId_sector_normalizedNameKey` " +
                        "ON `extra_work_classes` (`timelineId`, `sector`, `normalizedNameKey`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_extra_work_classes_id_timelineId_sector` " +
                        "ON `extra_work_classes` (`id`, `timelineId`, `sector`)",
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `shift_actual_records` (
                        `shiftId` TEXT NOT NULL,
                        `timelineId` TEXT NOT NULL,
                        `sector` TEXT NOT NULL,
                        `actualStartEpochMillis` INTEGER NOT NULL,
                        `actualEndEpochMillis` INTEGER NOT NULL,
                        `differenceReason` TEXT NOT NULL,
                        `explanation` TEXT,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`shiftId`),
                        FOREIGN KEY(`shiftId`, `timelineId`, `sector`)
                            REFERENCES `shift_work_snapshots`(`shiftId`, `timelineId`, `sector`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )""".trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_shift_actual_records_shiftId_timelineId_sector` " +
                        "ON `shift_actual_records` (`shiftId`, `timelineId`, `sector`)",
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `shift_extra_intervals` (
                        `id` TEXT NOT NULL,
                        `shiftId` TEXT NOT NULL,
                        `timelineId` TEXT NOT NULL,
                        `sector` TEXT NOT NULL,
                        `extraWorkClassId` TEXT NOT NULL,
                        `startEpochMillis` INTEGER NOT NULL,
                        `endEpochMillis` INTEGER NOT NULL,
                        `classNameSnapshot` TEXT NOT NULL,
                        `helpsMeetHoursReferenceSnapshot` INTEGER NOT NULL,
                        `showDedicatedSummarySnapshot` INTEGER NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`shiftId`, `timelineId`, `sector`)
                            REFERENCES `shift_actual_records`(`shiftId`, `timelineId`, `sector`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`extraWorkClassId`, `timelineId`, `sector`)
                            REFERENCES `extra_work_classes`(`id`, `timelineId`, `sector`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )""".trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_shift_extra_intervals_shiftId_timelineId_sector` " +
                        "ON `shift_extra_intervals` (`shiftId`, `timelineId`, `sector`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_shift_extra_intervals_extraWorkClassId_timelineId_sector` " +
                        "ON `shift_extra_intervals` (`extraWorkClassId`, `timelineId`, `sector`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_shift_extra_intervals_shiftId_startEpochMillis_endEpochMillis` " +
                        "ON `shift_extra_intervals` (`shiftId`, `startEpochMillis`, `endEpochMillis`)",
                )
            }
        }
    }
}
