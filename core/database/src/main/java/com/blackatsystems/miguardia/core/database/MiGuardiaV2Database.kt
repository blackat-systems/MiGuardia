package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.blackatsystems.miguardia.core.database.dao.ExplicitDayStatusDao
import com.blackatsystems.miguardia.core.database.dao.HolidayDao
import com.blackatsystems.miguardia.core.database.dao.MedicalLeaveDao
import com.blackatsystems.miguardia.core.database.dao.ObjectiveDao
import com.blackatsystems.miguardia.core.database.dao.SchedulePhotoDao
import com.blackatsystems.miguardia.core.database.dao.ShiftDao
import com.blackatsystems.miguardia.core.database.dao.ShiftNoteDao
import com.blackatsystems.miguardia.core.database.dao.ShiftNotificationConfigDao
import com.blackatsystems.miguardia.core.database.dao.V2ShiftDao
import com.blackatsystems.miguardia.core.database.dao.VacationDao
import com.blackatsystems.miguardia.core.database.dao.WorkCatalogDao
import com.blackatsystems.miguardia.core.database.dao.WorkConfigurationDao
import com.blackatsystems.miguardia.core.database.entity.ExplicitDayStatusEntity
import com.blackatsystems.miguardia.core.database.entity.HolidayEntity
import com.blackatsystems.miguardia.core.database.entity.MedicalLeaveEntity
import com.blackatsystems.miguardia.core.database.entity.ObjectiveEntity
import com.blackatsystems.miguardia.core.database.entity.PerPeriodHoursDefinitionEntity
import com.blackatsystems.miguardia.core.database.entity.PerPeriodHoursValueEntity
import com.blackatsystems.miguardia.core.database.entity.SchedulePhotoEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftEntity
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
    ],
    version = 1,
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

    companion object {
        const val DATABASE_NAME: String = "miguardia-v2.db"

        fun build(
            context: Context,
            databaseName: String = DATABASE_NAME,
        ): MiGuardiaV2Database = Room.databaseBuilder(
            context.applicationContext,
            MiGuardiaV2Database::class.java,
            databaseName,
        ).build()
    }
}
