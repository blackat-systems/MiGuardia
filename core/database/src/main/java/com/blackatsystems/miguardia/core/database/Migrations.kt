package com.blackatsystems.miguardia.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `holidays` (`id` TEXT NOT NULL, `localDate` TEXT NOT NULL, `name` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_holidays_localDate` ON `holidays` (`localDate`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `shift_notes` (`id` TEXT NOT NULL, `shiftId` TEXT NOT NULL, `body` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`shiftId`) REFERENCES `shifts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shift_notes_shiftId` ON `shift_notes` (`shiftId`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `shift_novelties` (`id` TEXT NOT NULL, `shiftId` TEXT NOT NULL, `type` TEXT NOT NULL, `description` TEXT, `relatedShiftId` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`shiftId`) REFERENCES `shifts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`relatedShiftId`) REFERENCES `shifts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shift_novelties_shiftId` ON `shift_novelties` (`shiftId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shift_novelties_relatedShiftId` ON `shift_novelties` (`relatedShiftId`)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `formal_shift_changes` (`id` TEXT NOT NULL, `shiftId` TEXT NOT NULL, `scheduleChanged` INTEGER NOT NULL, `objectiveChanged` INTEGER NOT NULL, `description` TEXT, `original_startEpochMillis` INTEGER NOT NULL, `original_endEpochMillis` INTEGER NOT NULL, `original_zoneId` TEXT NOT NULL, `original_localStartDate` TEXT NOT NULL, `original_objectiveName` TEXT NOT NULL, `original_objectiveAbbreviation` TEXT NOT NULL, `original_objectiveAddress` TEXT, `original_startTime` TEXT NOT NULL, `original_endTime` TEXT NOT NULL, `original_colorArgb` INTEGER NOT NULL, `original_position` TEXT, `original_status` TEXT NOT NULL, `original_sourceObjectiveId` TEXT, `original_sourceScheduleCombinationId` TEXT, `final_startEpochMillis` INTEGER NOT NULL, `final_endEpochMillis` INTEGER NOT NULL, `final_zoneId` TEXT NOT NULL, `final_localStartDate` TEXT NOT NULL, `final_objectiveName` TEXT NOT NULL, `final_objectiveAbbreviation` TEXT NOT NULL, `final_objectiveAddress` TEXT, `final_startTime` TEXT NOT NULL, `final_endTime` TEXT NOT NULL, `final_colorArgb` INTEGER NOT NULL, `final_position` TEXT, `final_status` TEXT NOT NULL, `final_sourceObjectiveId` TEXT, `final_sourceScheduleCombinationId` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`shiftId`) REFERENCES `shifts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_formal_shift_changes_shiftId` ON `formal_shift_changes` (`shiftId`)")
    }
}

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `vacations` (`id` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDateInclusive` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_vacations_startDate` ON `vacations` (`startDate`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_vacations_endDateInclusive` ON `vacations` (`endDateInclusive`)",
        )
    }
}

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `schedule_photos` (`id` TEXT NOT NULL, `month` TEXT NOT NULL, `objectiveId` TEXT, `objectiveNameSnapshot` TEXT, `objectiveAbbreviationSnapshot` TEXT, `storageKey` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `byteSize` INTEGER NOT NULL, `pixelWidth` INTEGER NOT NULL, `pixelHeight` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_photos_month` ON `schedule_photos` (`month`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_schedule_photos_storageKey` ON `schedule_photos` (`storageKey`)")
    }
}

internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `shift_notification_configs` (`shiftId` TEXT NOT NULL, PRIMARY KEY(`shiftId`), FOREIGN KEY(`shiftId`) REFERENCES `shifts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `shift_notification_reminders` (`shiftId` TEXT NOT NULL, `leadMinutes` INTEGER NOT NULL, PRIMARY KEY(`shiftId`, `leadMinutes`), FOREIGN KEY(`shiftId`) REFERENCES `shift_notification_configs`(`shiftId`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
        )
    }
}

internal const val MIGRATED_V1_WORK_CONFIGURATION_TIMELINE_ID: String =
    "00000000-0000-0000-0000-000000000100"

internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `work_configuration_roots` (`timelineId` TEXT NOT NULL, `singletonSlot` INTEGER NOT NULL, `origin` TEXT NOT NULL, PRIMARY KEY(`timelineId`))""",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_work_configuration_roots_singletonSlot` ON `work_configuration_roots` (`singletonSlot`)",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `per_period_hours_definitions` (`id` TEXT NOT NULL, `timelineId` TEXT NOT NULL, `periodKind` TEXT NOT NULL, `weeklyFirstDayIso` INTEGER, `cycleAnchorDate` TEXT, `cycleLengthDays` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`timelineId`) REFERENCES `work_configuration_roots`(`timelineId`) ON UPDATE NO ACTION ON DELETE RESTRICT )""",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_per_period_hours_definitions_timelineId` ON `per_period_hours_definitions` (`timelineId`)",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `work_configuration_revisions` (`id` TEXT NOT NULL, `timelineId` TEXT NOT NULL, `effectiveFrom` TEXT NOT NULL, `sector` TEXT NOT NULL, `availabilityLabel` TEXT, `hoursReferenceKind` TEXT NOT NULL, `periodKind` TEXT, `weeklyFirstDayIso` INTEGER, `cycleAnchorDate` TEXT, `cycleLengthDays` INTEGER, `requiredMinutes` INTEGER, `perPeriodDefinitionId` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`timelineId`) REFERENCES `work_configuration_roots`(`timelineId`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`perPeriodDefinitionId`) REFERENCES `per_period_hours_definitions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )""",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_work_configuration_revisions_timelineId_effectiveFrom` ON `work_configuration_revisions` (`timelineId`, `effectiveFrom`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_work_configuration_revisions_perPeriodDefinitionId` ON `work_configuration_revisions` (`perPeriodDefinitionId`)",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `per_period_hours_values` (`id` TEXT NOT NULL, `definitionId` TEXT NOT NULL, `windowStartInclusive` TEXT NOT NULL, `windowEndExclusive` TEXT NOT NULL, `requiredMinutes` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`definitionId`) REFERENCES `per_period_hours_definitions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )""",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_per_period_hours_values_definitionId_windowStartInclusive` ON `per_period_hours_values` (`definitionId`, `windowStartInclusive`)",
        )
        db.execSQL(
            """INSERT INTO `work_configuration_roots` (`timelineId`, `singletonSlot`, `origin`) VALUES ('$MIGRATED_V1_WORK_CONFIGURATION_TIMELINE_ID', 1, 'MIGRATED_V1')""",
        )
    }
}
