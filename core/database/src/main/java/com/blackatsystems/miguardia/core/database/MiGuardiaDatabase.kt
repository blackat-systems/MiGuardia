package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.blackatsystems.miguardia.core.database.dao.ExplicitDayStatusDao
import com.blackatsystems.miguardia.core.database.dao.HolidayDao
import com.blackatsystems.miguardia.core.database.dao.MedicalLeaveDao
import com.blackatsystems.miguardia.core.database.dao.ObjectiveDao
import com.blackatsystems.miguardia.core.database.dao.ScheduleCombinationDao
import com.blackatsystems.miguardia.core.database.dao.ShiftDao
import com.blackatsystems.miguardia.core.database.dao.ShiftNoteDao
import com.blackatsystems.miguardia.core.database.dao.ShiftNoveltyDao
import com.blackatsystems.miguardia.core.database.dao.VacationDao
import com.blackatsystems.miguardia.core.database.entity.ExplicitDayStatusEntity
import com.blackatsystems.miguardia.core.database.entity.FormalShiftChangeEntity
import com.blackatsystems.miguardia.core.database.entity.HolidayEntity
import com.blackatsystems.miguardia.core.database.entity.MedicalLeaveEntity
import com.blackatsystems.miguardia.core.database.entity.ObjectiveEntity
import com.blackatsystems.miguardia.core.database.entity.ScheduleCombinationEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftNoteEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftNoveltyEntity
import com.blackatsystems.miguardia.core.database.entity.VacationEntity

@Database(
    entities = [
        ObjectiveEntity::class,
        ScheduleCombinationEntity::class,
        ShiftEntity::class,
        ExplicitDayStatusEntity::class,
        MedicalLeaveEntity::class,
        HolidayEntity::class,
        ShiftNoteEntity::class,
        ShiftNoveltyEntity::class,
        FormalShiftChangeEntity::class,
        VacationEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
internal abstract class MiGuardiaDatabase : RoomDatabase() {
    internal abstract fun objectiveDao(): ObjectiveDao
    internal abstract fun scheduleCombinationDao(): ScheduleCombinationDao
    internal abstract fun shiftDao(): ShiftDao
    internal abstract fun explicitDayStatusDao(): ExplicitDayStatusDao
    internal abstract fun medicalLeaveDao(): MedicalLeaveDao
    internal abstract fun holidayDao(): HolidayDao
    internal abstract fun shiftNoteDao(): ShiftNoteDao
    internal abstract fun shiftNoveltyDao(): ShiftNoveltyDao
    internal abstract fun vacationDao(): VacationDao

    companion object {
        const val DATABASE_NAME: String = "miguardia.db"

        fun build(
            context: Context,
            databaseName: String = DATABASE_NAME,
        ): MiGuardiaDatabase = Room.databaseBuilder(
            context.applicationContext,
            MiGuardiaDatabase::class.java,
            databaseName,
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
