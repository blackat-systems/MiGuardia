package com.blackatsystems.miguardia

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.notifications.NotificationSystemAccess
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsViewModel
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasViewModel
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadViewModel
import com.blackatsystems.miguardia.ui.management.V2RecurringPlanViewModel
import com.blackatsystems.miguardia.ui.management.V2ShiftEditViewModel
import com.blackatsystems.miguardia.ui.management.V2ShiftActualViewModel
import com.blackatsystems.miguardia.ui.nextevent.NextEventViewModel
import com.blackatsystems.miguardia.ui.notifications.NotificationViewModel
import com.blackatsystems.miguardia.ui.photos.PhotosViewModel
import com.blackatsystems.miguardia.ui.photos.SchedulePhotoFileStore
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.theme.vigiliaSystemBarStyle
import com.blackatsystems.miguardia.ui.vacation.VacationViewModel
import com.blackatsystems.miguardia.ui.weather.WeatherViewModel
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupViewModel
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var calendarNavigationRequest by mutableIntStateOf(0)

    private val notificationViewModel: NotificationViewModel by viewModels {
        val application = application as MiGuardiaApplication
        NotificationViewModel.Factory(
            preferencesStore = application.notificationPreferences,
            configs = application.localDataStore.shiftNotificationConfigs,
            systemAccess = NotificationSystemAccess(application),
            runtime = application.notificationRuntime,
        )
    }
    private val weatherViewModel: WeatherViewModel by viewModels {
        val application = application as MiGuardiaApplication
        WeatherViewModel.Factory(
            runtime = application.weatherRuntime,
            shifts = application.localDataStore.shifts,
            vacations = application.localDataStore.vacations,
        )
    }
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

    private val v2ManualShiftLoadViewModel: V2ManualShiftLoadViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        V2ManualShiftLoadViewModel.Factory(
            configurationRepository = dataStore.workConfiguration,
            catalogRepository = dataStore.workCatalog,
            objectiveRepository = dataStore.objectives,
            shiftRepository = dataStore.shifts,
            medicalLeaveRepository = dataStore.medicalLeaves,
            v2ShiftRepository = dataStore.v2Shifts,
            shiftActualRepository = dataStore.shiftActuals,
        )
    }

    private val v2ShiftEditViewModel: V2ShiftEditViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        V2ShiftEditViewModel.Factory(
            configurationRepository = dataStore.workConfiguration,
            catalogRepository = dataStore.workCatalog,
            objectiveRepository = dataStore.objectives,
            shiftRepository = dataStore.shifts,
            medicalLeaveRepository = dataStore.medicalLeaves,
            v2ShiftRepository = dataStore.v2Shifts,
            shiftActualRepository = dataStore.shiftActuals,
            recurringPlanRepository = dataStore.recurringPlans,
        )
    }

    private val v2ShiftActualViewModel: V2ShiftActualViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        V2ShiftActualViewModel.Factory(
            repository = dataStore.shiftActuals,
            clock = Clock.systemUTC(),
        )
    }

    private val v2RecurringPlanViewModel: V2RecurringPlanViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        V2RecurringPlanViewModel.Factory(
            configurationRepository = dataStore.workConfiguration,
            catalogRepository = dataStore.workCatalog,
            objectiveRepository = dataStore.objectives,
            shiftRepository = dataStore.shifts,
            medicalLeaveRepository = dataStore.medicalLeaves,
            recurringPlanRepository = dataStore.recurringPlans,
            recurringShiftRepository = dataStore.recurringShiftWriter,
        )
    }

    private val workSetupViewModel: WorkSetupViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        WorkSetupViewModel.Factory(
            configurationRepository = dataStore.workConfiguration,
            catalogRepository = dataStore.workCatalog,
            objectiveRepository = dataStore.objectives,
            clock = Clock.system(AppDefaults.zoneId()),
        )
    }

    private val hoursAndExtrasViewModel: HoursAndExtrasViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        HoursAndExtrasViewModel.Factory(
            configurationRepository = dataStore.workConfiguration,
            catalogRepository = dataStore.workCatalog,
            objectiveRepository = dataStore.objectives,
            shiftRepository = dataStore.v2Shifts,
            shiftActualRepository = dataStore.shiftActuals,
            independentExtraRepository = dataStore.independentExtraWork,
            medicalLeaveRepository = dataStore.medicalLeaves,
            vacationRepository = dataStore.vacations,
            clock = Clock.systemUTC(),
            zoneId = AppDefaults.zoneId(),
        )
    }

    private val exceptionsViewModel: ExceptionsViewModel by viewModels {
        val dataStore = (application as MiGuardiaApplication).localDataStore
        ExceptionsViewModel.Factory(
            holidays = dataStore.holidays,
            notes = dataStore.shiftNotes,
            shifts = dataStore.shifts,
        )
    }

    @Suppress("DEPRECATION")
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
            var appThemeMode by remember {
                mutableStateOf(
                    AppThemeMode.fromStorage(preferences.getString(APP_THEME_MODE, null)),
                )
            }
            val useDarkTheme = appThemeMode.resolve(isSystemInDarkTheme())
            val systemBarStyle = vigiliaSystemBarStyle(useDarkTheme)
            SideEffect {
                window.statusBarColor = systemBarStyle.backgroundArgb
                window.navigationBarColor = systemBarStyle.backgroundArgb
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = systemBarStyle.useDarkIcons
                    isAppearanceLightNavigationBars = systemBarStyle.useDarkIcons
                }
            }
            MiGuardiaTheme(
                darkTheme = useDarkTheme,
                appZoom = appZoom,
            ) {
                MiGuardiaApp(
                    calendarViewModel = calendarViewModel,
                    nextEventViewModel = nextEventViewModel,
                    v2ManualShiftLoadViewModel = v2ManualShiftLoadViewModel,
                    v2ShiftEditViewModel = v2ShiftEditViewModel,
                    v2ShiftActualViewModel = v2ShiftActualViewModel,
                    v2RecurringPlanViewModel = v2RecurringPlanViewModel,
                    exceptionsViewModel = exceptionsViewModel,
                    vacationViewModel = vacationViewModel,
                    photosViewModel = photosViewModel,
                    notificationViewModel = notificationViewModel,
                    weatherViewModel = weatherViewModel,
                    workSetupViewModel = workSetupViewModel,
                    hoursAndExtrasViewModel = hoursAndExtrasViewModel,
                    calendarNavigationRequest = calendarNavigationRequest,
                    appZoom = appZoom,
                    onAppZoomChange = { selected ->
                        appZoom = selected
                        preferences.edit { putInt(APP_ZOOM_PERCENT, selected.percent) }
                    },
                    appThemeMode = appThemeMode,
                    onAppThemeModeChange = { selected ->
                        appThemeMode = selected
                        preferences.edit { putString(APP_THEME_MODE, selected.name) }
                    },
                )
            }
        }
        handleNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        workSetupViewModel.refreshReferenceDate()
        hoursAndExtrasViewModel.refresh()
        notificationViewModel.refreshSystemAccess()
        weatherViewModel.onResume()
        (application as MiGuardiaApplication).notificationRuntime.reconcile()
    }

    private fun handleNotificationIntent(source: Intent?) {
        val action = source?.action ?: return
        if (action !in NotificationActions) return
        val shiftId = source.getStringExtra(EXTRA_SHIFT_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return
        lifecycleScope.launch {
            val shift = (application as MiGuardiaApplication).localDataStore.shifts.getById(shiftId)
            if (shift == null) {
                Toast.makeText(this@MainActivity, "La guardia ya no está disponible.", Toast.LENGTH_LONG).show()
                return@launch
            }
            when (action) {
                ACTION_VIEW_SHIFT -> {
                    calendarViewModel.openDate(shift.localStartDate)
                    calendarNavigationRequest++
                }
                ACTION_DIRECTIONS -> {
                    val address = shift.objectiveAddressSnapshot?.takeIf(String::isNotBlank)
                    val opened = address?.let {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(it)}".toUri()))
                            true
                        } catch (_: ActivityNotFoundException) {
                            false
                        }
                    } == true
                    if (!opened) {
                        calendarViewModel.openDate(shift.localStartDate)
                        calendarNavigationRequest++
                        Toast.makeText(
                            this@MainActivity,
                            if (address == null) "Esta guardia no tiene una dirección guardada." else "No hay una aplicación compatible para abrir la dirección.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_VIEW_SHIFT = "com.blackatsystems.miguardia.action.VIEW_SHIFT"
        const val ACTION_DIRECTIONS = "com.blackatsystems.miguardia.action.DIRECTIONS"
        const val EXTRA_SHIFT_ID = "shift_id"
        const val DISPLAY_PREFERENCES = "miguardia_display_preferences"
        const val APP_ZOOM_PERCENT = "app_zoom_percent"
        const val APP_THEME_MODE = "app_theme_mode"
        private val NotificationActions = setOf(ACTION_VIEW_SHIFT, ACTION_DIRECTIONS)
    }
}
