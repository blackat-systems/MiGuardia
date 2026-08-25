package com.blackatsystems.miguardia.core.database

import android.content.Context
import com.blackatsystems.miguardia.core.database.repository.RoomExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.database.repository.RoomHolidayRepository
import com.blackatsystems.miguardia.core.database.repository.RoomMedicalLeaveRepository
import com.blackatsystems.miguardia.core.database.repository.RoomObjectiveRepository
import com.blackatsystems.miguardia.core.database.repository.RoomSchedulePhotoRepository
import com.blackatsystems.miguardia.core.database.repository.RoomShiftRepository
import com.blackatsystems.miguardia.core.database.repository.RoomShiftActualRepository
import com.blackatsystems.miguardia.core.database.repository.RoomShiftNoteRepository
import com.blackatsystems.miguardia.core.database.repository.RoomShiftNotificationConfigRepository
import com.blackatsystems.miguardia.core.database.repository.RoomVacationRepository
import com.blackatsystems.miguardia.core.database.repository.RoomV2ShiftRepository
import com.blackatsystems.miguardia.core.database.repository.RoomWorkCatalogRepository
import com.blackatsystems.miguardia.core.database.repository.RoomWorkConfigurationRepository
import com.blackatsystems.miguardia.core.domain.repository.ExplicitDayStatusRepository
import com.blackatsystems.miguardia.core.domain.repository.HolidayRepository
import com.blackatsystems.miguardia.core.domain.repository.MedicalLeaveRepository
import com.blackatsystems.miguardia.core.domain.repository.ObjectiveRepository
import com.blackatsystems.miguardia.core.domain.repository.RecurringPlanRepository
import com.blackatsystems.miguardia.core.domain.repository.SchedulePhotoRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftActualRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftNoteRepository
import com.blackatsystems.miguardia.core.domain.repository.ShiftNotificationConfigRepository
import com.blackatsystems.miguardia.core.domain.repository.VacationRepository
import com.blackatsystems.miguardia.core.domain.repository.V2ShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.V2RecurringShiftRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkCatalogRepository
import com.blackatsystems.miguardia.core.domain.repository.WorkConfigurationRepository
import java.io.Closeable
import java.time.Clock

class LocalDataStore internal constructor(
    private val database: MiGuardiaV2Database,
    private val instrumentationResetAllowed: Boolean = false,
    recurringClock: Clock = Clock.systemUTC(),
) : Closeable {
    private val roomV2Shifts = RoomV2ShiftRepository(database, recurringClock)
    private val roomShiftActual = RoomShiftActualRepository(database)
    val objectives: ObjectiveRepository = RoomObjectiveRepository(database)
    val shifts: ShiftRepository = RoomShiftRepository(database)
    val explicitDayStatuses: ExplicitDayStatusRepository =
        RoomExplicitDayStatusRepository(database.explicitDayStatusDao())
    val medicalLeaves: MedicalLeaveRepository =
        RoomMedicalLeaveRepository(database)
    val holidays: HolidayRepository = RoomHolidayRepository(database)
    val shiftNotes: ShiftNoteRepository = RoomShiftNoteRepository(database.shiftNoteDao())
    val vacations: VacationRepository = RoomVacationRepository(database)
    val schedulePhotos: SchedulePhotoRepository = RoomSchedulePhotoRepository(database.schedulePhotoDao())
    val shiftNotificationConfigs: ShiftNotificationConfigRepository =
        RoomShiftNotificationConfigRepository(database)
    val workConfiguration: WorkConfigurationRepository =
        RoomWorkConfigurationRepository(database)
    val workCatalog: WorkCatalogRepository = RoomWorkCatalogRepository(database)
    val v2Shifts: V2ShiftRepository = roomV2Shifts
    val shiftActuals: ShiftActualRepository = roomShiftActual
    val recurringShiftWriter: V2RecurringShiftRepository = roomV2Shifts
    val recurringPlans: RecurringPlanRepository = roomV2Shifts

    /** Clears the isolated QA database between instrumentation scenarios. */
    fun clearAllDataForInstrumentation() {
        check(instrumentationResetAllowed) {
            "La limpieza instrumentada sólo está disponible para la base QA aislada."
        }
        database.clearAllTables()
    }

    override fun close() = database.close()

    companion object {
        fun create(
            context: Context,
            databaseName: String = MiGuardiaV2Database.DATABASE_NAME,
        ): LocalDataStore = LocalDataStore(
            database = MiGuardiaV2Database.build(context, databaseName),
            instrumentationResetAllowed =
                context.packageName == QA_APPLICATION_ID && databaseName == MiGuardiaV2Database.DATABASE_NAME,
        )

        private const val QA_APPLICATION_ID: String = "com.blackatsystems.miguardia.qa"
    }
}
