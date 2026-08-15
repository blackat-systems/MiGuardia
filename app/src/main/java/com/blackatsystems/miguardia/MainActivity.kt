package com.blackatsystems.miguardia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.management.ManagementViewModel
import com.blackatsystems.miguardia.ui.nextevent.NextEventViewModel
import com.blackatsystems.miguardia.ui.photos.PhotosViewModel
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsViewModel
import com.blackatsystems.miguardia.ui.summary.SummaryViewModel
import com.blackatsystems.miguardia.ui.vacation.VacationViewModel
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.theme.AppZoom

class MainActivity : ComponentActivity() {
    private val nextEventViewModel: NextEventViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        NextEventViewModel.Factory(
            shifts = dataStore.shifts,
            explicitDayStatuses = dataStore.explicitDayStatuses,
            vacations = dataStore.vacations,
        )
    }
    private val photosViewModel: PhotosViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        PhotosViewModel.Factory(dataStore.schedulePhotos, dataStore.objectives, SchedulePhotoFileStore(applicationContext))
    }
    private val calendarViewModel: CalendarViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        CalendarViewModel.Factory(
            shiftRepository = dataStore.shifts,
            explicitDayStatusRepository = dataStore.explicitDayStatuses,
            medicalLeaveRepository = dataStore.medicalLeaves,
            holidayRepository = dataStore.holidays,
            vacationRepository = dataStore.vacations,
        )
    }

    private val vacationViewModel: VacationViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        VacationViewModel.Factory(dataStore.vacations)
    }

    private val managementViewModel: ManagementViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        ManagementViewModel.Factory(
            objectiveRepository = dataStore.objectives,
            scheduleRepository = dataStore.scheduleCombinations,
            shiftRepository = dataStore.shifts,
            explicitDayStatusRepository = dataStore.explicitDayStatuses,
            medicalLeaveRepository = dataStore.medicalLeaves,
        )
    }

    private val exceptionsViewModel: ExceptionsViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        ExceptionsViewModel.Factory(
            holidays = dataStore.holidays,
            notes = dataStore.shiftNotes,
            novelties = dataStore.shiftNovelties,
            shifts = dataStore.shifts,
            objectives = dataStore.objectives,
            schedules = dataStore.scheduleCombinations,
        )
    }

    private val summaryViewModel: SummaryViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        SummaryViewModel.Factory(
            shiftRepository = dataStore.shifts,
            explicitDayStatusRepository = dataStore.explicitDayStatuses,
            medicalLeaveRepository = dataStore.medicalLeaves,
            holidayRepository = dataStore.holidays,
            vacationRepository = dataStore.vacations,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferences = remember {
                getSharedPreferences(DISPLAY_PREFERENCES, MODE_PRIVATE)
            }
            var appZoom by remember {
                mutableStateOf(
                    AppZoom.fromPercent(
                        preferences.getInt(APP_ZOOM_PERCENT, AppZoom.STANDARD.percent),
                    ),
                )
            }
            MiGuardiaTheme(appZoom = appZoom) {
                MiGuardiaApp(
                    calendarViewModel = calendarViewModel,
                    nextEventViewModel = nextEventViewModel,
                    managementViewModel = managementViewModel,
                    summaryViewModel = summaryViewModel,
                    exceptionsViewModel = exceptionsViewModel,
                    vacationViewModel = vacationViewModel,
                    photosViewModel = photosViewModel,
                    appZoom = appZoom,
                    onAppZoomChange = { selected ->
                        appZoom = selected
                        preferences.edit { putInt(APP_ZOOM_PERCENT, selected.percent) }
                    },
                )
            }
        }
    }

    private companion object {
        const val DISPLAY_PREFERENCES = "miguardia_display_preferences"
        const val APP_ZOOM_PERCENT = "app_zoom_percent"
    }
}
