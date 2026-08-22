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

internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `work_places` (`id` TEXT NOT NULL, `timelineId` TEXT NOT NULL, `sector` TEXT NOT NULL, `objectiveId` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`timelineId`) REFERENCES `work_configuration_roots`(`timelineId`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`objectiveId`) REFERENCES `objectives`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )""",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_work_places_timelineId_sector_objectiveId` ON `work_places` (`timelineId`, `sector`, `objectiveId`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_work_places_id_timelineId_sector_objectiveId` ON `work_places` (`id`, `timelineId`, `sector`, `objectiveId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_work_places_objectiveId` ON `work_places` (`objectiveId`)",
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `work_types` (`id` TEXT NOT NULL, `timelineId` TEXT NOT NULL, `sector` TEXT NOT NULL, `name` TEXT NOT NULL, `normalizedNameKey` TEXT NOT NULL, `behavior` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`timelineId`) REFERENCES `work_configuration_roots`(`timelineId`) ON UPDATE NO ACTION ON DELETE RESTRICT )""",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_work_types_timelineId_sector_normalizedNameKey` ON `work_types` (`timelineId`, `sector`, `normalizedNameKey`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_work_types_id_timelineId_sector` ON `work_types` (`id`, `timelineId`, `sector`)",
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `work_templates` (`id` TEXT NOT NULL, `timelineId` TEXT NOT NULL, `sector` TEXT NOT NULL, `workPlaceId` TEXT NOT NULL, `objectiveId` TEXT NOT NULL, `workTypeId` TEXT NOT NULL, `startTime` TEXT NOT NULL, `endTime` TEXT NOT NULL, `colorArgb` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `legacyScheduleCombinationId` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`timelineId`) REFERENCES `work_configuration_roots`(`timelineId`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`workPlaceId`, `timelineId`, `sector`, `objectiveId`) REFERENCES `work_places`(`id`, `timelineId`, `sector`, `objectiveId`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`workTypeId`, `timelineId`, `sector`) REFERENCES `work_types`(`id`, `timelineId`, `sector`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`legacyScheduleCombinationId`) REFERENCES `schedule_combinations`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_work_templates_timelineId` ON `work_templates` (`timelineId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_work_templates_workPlaceId_timelineId_sector_objectiveId` ON `work_templates` (`workPlaceId`, `timelineId`, `sector`, `objectiveId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_work_templates_workTypeId_timelineId_sector` ON `work_templates` (`workTypeId`, `timelineId`, `sector`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_work_templates_legacyScheduleCombinationId` ON `work_templates` (`legacyScheduleCombinationId`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_work_templates_workPlaceId_workTypeId_startTime_endTime` ON `work_templates` (`workPlaceId`, `workTypeId`, `startTime`, `endTime`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_work_templates_id_timelineId_sector_workPlaceId_objectiveId_workTypeId` ON `work_templates` (`id`, `timelineId`, `sector`, `workPlaceId`, `objectiveId`, `workTypeId`)",
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `workplace_rule_revisions` (`id` TEXT NOT NULL, `timelineId` TEXT NOT NULL, `sector` TEXT NOT NULL, `workPlaceId` TEXT NOT NULL, `objectiveId` TEXT NOT NULL, `effectiveFrom` TEXT NOT NULL, `nightRuleCode` TEXT NOT NULL, `nightStartTime` TEXT, `nightEndTime` TEXT, `nightDifferentTreatment` INTEGER, `nightShowDedicatedSummary` INTEGER, `weekendRuleCode` TEXT NOT NULL, `weekendDifferentTreatment` INTEGER, `weekendShowDedicatedSummary` INTEGER, `holidayDifferentTreatment` INTEGER NOT NULL, `holidayShowDedicatedSummary` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`timelineId`) REFERENCES `work_configuration_roots`(`timelineId`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`workPlaceId`, `timelineId`, `sector`, `objectiveId`) REFERENCES `work_places`(`id`, `timelineId`, `sector`, `objectiveId`) ON UPDATE NO ACTION ON DELETE RESTRICT )""",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_workplace_rule_revisions_workPlaceId_effectiveFrom` ON `workplace_rule_revisions` (`workPlaceId`, `effectiveFrom`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workplace_rule_revisions_timelineId` ON `workplace_rule_revisions` (`timelineId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workplace_rule_revisions_workPlaceId_timelineId_sector_objectiveId` ON `workplace_rule_revisions` (`workPlaceId`, `timelineId`, `sector`, `objectiveId`)",
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `shift_work_snapshots` (`shiftId` TEXT NOT NULL, `timelineId` TEXT NOT NULL, `sector` TEXT NOT NULL, `configurationRevisionId` TEXT NOT NULL, `workPlaceId` TEXT NOT NULL, `objectiveId` TEXT NOT NULL, `templateId` TEXT NOT NULL, `workTypeId` TEXT NOT NULL, `workTypeNameSnapshot` TEXT NOT NULL, `workTypeBehaviorSnapshot` TEXT NOT NULL, PRIMARY KEY(`shiftId`), FOREIGN KEY(`shiftId`) REFERENCES `shifts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`timelineId`) REFERENCES `work_configuration_roots`(`timelineId`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`configurationRevisionId`) REFERENCES `work_configuration_revisions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`workPlaceId`, `timelineId`, `sector`, `objectiveId`) REFERENCES `work_places`(`id`, `timelineId`, `sector`, `objectiveId`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`workTypeId`, `timelineId`, `sector`) REFERENCES `work_types`(`id`, `timelineId`, `sector`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`templateId`, `timelineId`, `sector`, `workPlaceId`, `objectiveId`, `workTypeId`) REFERENCES `work_templates`(`id`, `timelineId`, `sector`, `workPlaceId`, `objectiveId`, `workTypeId`) ON UPDATE NO ACTION ON DELETE RESTRICT )""",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shift_work_snapshots_timelineId` ON `shift_work_snapshots` (`timelineId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shift_work_snapshots_configurationRevisionId` ON `shift_work_snapshots` (`configurationRevisionId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shift_work_snapshots_workPlaceId_timelineId_sector_objectiveId` ON `shift_work_snapshots` (`workPlaceId`, `timelineId`, `sector`, `objectiveId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shift_work_snapshots_workTypeId_timelineId_sector` ON `shift_work_snapshots` (`workTypeId`, `timelineId`, `sector`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shift_work_snapshots_templateId_timelineId_sector_workPlaceId_objectiveId_workTypeId` ON `shift_work_snapshots` (`templateId`, `timelineId`, `sector`, `workPlaceId`, `objectiveId`, `workTypeId`)",
        )
    }
}
