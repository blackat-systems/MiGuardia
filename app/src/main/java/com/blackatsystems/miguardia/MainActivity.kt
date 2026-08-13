package com.blackatsystems.miguardia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.management.ManagementViewModel
import com.blackatsystems.miguardia.ui.summary.SummaryViewModel
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme

class MainActivity : ComponentActivity() {
    private val calendarViewModel: CalendarViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        CalendarViewModel.Factory(
            shiftRepository = dataStore.shifts,
            explicitDayStatusRepository = dataStore.explicitDayStatuses,
            medicalLeaveRepository = dataStore.medicalLeaves,
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

    private val summaryViewModel: SummaryViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        SummaryViewModel.Factory(
            shiftRepository = dataStore.shifts,
            explicitDayStatusRepository = dataStore.explicitDayStatuses,
            medicalLeaveRepository = dataStore.medicalLeaves,
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
                )
            }
        }
    }
}
