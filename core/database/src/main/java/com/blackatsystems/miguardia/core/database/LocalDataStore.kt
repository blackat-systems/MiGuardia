package com.blackatsystems.miguardia.core.database

import android.content.Context
import com.blackatsystems.miguardia.core.database.repository.RoomExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.database.repository.RoomMedicalLeaveRepository
import com.blackatsystems.miguardia.core.database.repository.RoomObjectiveRepository
import com.blackatsystems.miguardia.core.database.repository.RoomScheduleCombinationRepository
import com.blackatsystems.miguardia.core.database.repository.RoomShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.ScheduleCombinationRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import java.io.Closeable

class LocalDataStore internal constructor(
    private val database: MiGuardiaDatabase,
) : Closeable {
    val objectives: ObjectiveRepository = RoomObjectiveRepository(database)
    val scheduleCombinations: ScheduleCombinationRepository =
        RoomScheduleCombinationRepository(database.scheduleCombinationDao())
    val shifts: ShiftRepository = RoomShiftRepository(database)
    val explicitDayStatuses: ExplicitDayStatusRepository =
        RoomExplicitDayStatusRepository(database.explicitDayStatusDao())
    val medicalLeaves: MedicalLeaveRepository =
        RoomMedicalLeaveRepository(database.medicalLeaveDao())

    override fun close() = database.close()

    companion object {
        fun create(
            context: Context,
            databaseName: String = MiGuardiaDatabase.DATABASE_NAME,
        ): LocalDataStore = LocalDataStore(
            MiGuardiaDatabase.build(context, databaseName),
        )
    }
}
