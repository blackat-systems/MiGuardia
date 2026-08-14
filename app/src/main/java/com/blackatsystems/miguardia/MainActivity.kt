package com.blackatsystems.miguardia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.management.ManagementViewModel
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsViewModel
import com.blackatsystems.miguardia.ui.summary.SummaryViewModel
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme

class MainActivity : ComponentActivity() {
    private val calendarViewModel: CalendarViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        CalendarViewModel.Factory(
            shiftRepository = dataStore.shifts,
            explicitDayStatusRepository = dataStore.explicitDayStatuses,
            medicalLeaveRepository = dataStore.medicalLeaves,
            holidayRepository = dataStore.holidays,
        )
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
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarViewModel = calendarViewModel,
                    managementViewModel = managementViewModel,
                    summaryViewModel = summaryViewModel,
                    exceptionsViewModel = exceptionsViewModel,
                )
            }
        }
    }
}
