package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.blackatsystems.miguardia.core.database.dao.ExplicitDayStatusDao
import com.blackatsystems.miguardia.core.database.dao.MedicalLeaveDao
import com.blackatsystems.miguardia.core.database.dao.ObjectiveDao
import com.blackatsystems.miguardia.core.database.dao.ScheduleCombinationDao
import com.blackatsystems.miguardia.core.database.dao.ShiftDao
import com.blackatsystems.miguardia.core.database.entity.ExplicitDayStatusEntity
import com.blackatsystems.miguardia.core.database.entity.MedicalLeaveEntity
import com.blackatsystems.miguardia.core.database.entity.ObjectiveEntity
import com.blackatsystems.miguardia.core.database.entity.ScheduleCombinationEntity
import com.blackatsystems.miguardia.core.database.entity.ShiftEntity

@Database(
    entities = [
        ObjectiveEntity::class,
        ScheduleCombinationEntity::class,
        ShiftEntity::class,
        ExplicitDayStatusEntity::class,
        MedicalLeaveEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
internal abstract class MiGuardiaDatabase : RoomDatabase() {
    internal abstract fun objectiveDao(): ObjectiveDao
    internal abstract fun scheduleCombinationDao(): ScheduleCombinationDao
    internal abstract fun shiftDao(): ShiftDao
    internal abstract fun explicitDayStatusDao(): ExplicitDayStatusDao
    internal abstract fun medicalLeaveDao(): MedicalLeaveDao

    companion object {
        const val DATABASE_NAME: String = "miguardia.db"

        fun build(
            context: Context,
            databaseName: String = DATABASE_NAME,
        ): MiGuardiaDatabase = Room.databaseBuilder(
            context.applicationContext,
            MiGuardiaDatabase::class.java,
            databaseName,
        ).build()
    }
}
