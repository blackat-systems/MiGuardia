package com.blackatsystems.miguardia.core.database

import android.content.Context
import com.blackatsystems.miguardia.core.database.repository.RoomExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.database.repository.RoomHolidayRepository
import com.blackatsystems.miguardia.core.database.repository.RoomMedicalLeaveRepository
import com.blackatsystems.miguardia.core.database.repository.RoomObjectiveRepository
import com.blackatsystems.miguardia.core.database.repository.RoomScheduleCombinationRepository
import com.blackatsystems.miguardia.core.database.repository.RoomSchedulePhotoRepository
import com.blackatsystems.miguardia.core.database.repository.RoomShiftRepository
import com.blackatsystems.miguardia.core.database.repository.RoomShiftNoteRepository
import com.blackatsystems.miguardia.core.database.repository.RoomShiftNoveltyRepository
import com.blackatsystems.miguardia.core.database.repository.RoomShiftNotificationConfigRepository
import com.blackatsystems.miguardia.core.database.repository.RoomVacationRepository
import com.blackatsystems.miguardia.core.database.repository.RoomWorkConfigurationRepository
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.HolidayRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.ScheduleCombinationRepository
import com.blackatsystems.miguardia.core.domain.repository.SchedulePhotoRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftNoteRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftNoveltyRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftNotificationConfigRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
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
        RoomMedicalLeaveRepository(database)
    val holidays: HolidayRepository = RoomHolidayRepository(database)
    val shiftNotes: ShiftNoteRepository = RoomShiftNoteRepository(database.shiftNoteDao())
    val shiftNovelties: ShiftNoveltyRepository = RoomShiftNoveltyRepository(database)
    val vacations: VacationRepository = RoomVacationRepository(database)
    val schedulePhotos: SchedulePhotoRepository = RoomSchedulePhotoRepository(database.schedulePhotoDao())
    val shiftNotificationConfigs: ShiftNotificationConfigRepository =
        RoomShiftNotificationConfigRepository(database)
    val workConfiguration: WorkConfigurationRepository =
        RoomWorkConfigurationRepository(database)

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
