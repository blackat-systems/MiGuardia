package com.blackatsystems.miguardia

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.calendar.projectCalendarMonth
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsSurface
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsActions
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsUiState
import com.blackatsystems.miguardia.ui.management.ManagementActions
import com.blackatsystems.miguardia.ui.management.ManagementSurface
import com.blackatsystems.miguardia.ui.management.ManagementUiState
import com.blackatsystems.miguardia.ui.notifications.NotificationSurface
import com.blackatsystems.miguardia.ui.notifications.NotificationActions
import com.blackatsystems.miguardia.ui.notifications.NotificationUiState
import com.blackatsystems.miguardia.ui.photos.PhotosSurface
import com.blackatsystems.miguardia.ui.photos.PhotosUiState
import com.blackatsystems.miguardia.ui.profile.ProfileSurface
import com.blackatsystems.miguardia.ui.profile.ProfileActions
import com.blackatsystems.miguardia.ui.profile.ProfileUiState
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.vacation.VacationSurface
import com.blackatsystems.miguardia.ui.vacation.VacationActions
import com.blackatsystems.miguardia.ui.vacation.VacationUiState
import com.blackatsystems.miguardia.ui.weather.WeatherSurface
import com.blackatsystems.miguardia.ui.weather.WeatherActions
import com.blackatsystems.miguardia.ui.weather.WeatherUiState
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupActions
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NavigationDrawerComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun bottomBarIsAbsentAndDrawerContainsTheFullUniqueHierarchyWithSelectionState() {
        setAppContent()

        compose.onNodeWithContentDescription("Abrir menú").assertIsDisplayed()
        compose.onNodeWithTag("main-destination-summary").assertIsNotDisplayed()
        compose.onNodeWithTag("main-destination-appearance").assertIsNotDisplayed()

        compose.onNodeWithContentDescription("Abrir menú").performClick()

        listOf(
            "Calendario",
            "Resumen",
            "Mi forma de trabajar",
            "Perfil laboral",
            "Objetivos y horarios",
            "Feriados",
            "Vacaciones",
            "Notificaciones",
            "Clima",
            "Apariencia",
        ).forEach { label ->
            compose.onAllNodes(
                hasText(label) and hasAnyAncestor(hasTestTag("main-navigation-drawer")),
            ).assertCountEquals(1)
        }
        listOf("Tu trabajo", "Avisos y contexto", "Aplicación").forEach { section ->
            compose.onAllNodes(
                hasText(section, ignoreCase = true) and
                    hasAnyAncestor(hasTestTag("main-navigation-drawer")),
            ).assertCountEquals(1)
        }
        compose.onNodeWithText("Configuración").assertDoesNotExist()
        compose.onNodeWithTag("main-destination-calendar").assertIsSelected()
        compose.onNodeWithTag("main-destination-summary").assertIsNotSelected()
        compose.onNodeWithTag("main-destination-appearance").assertIsNotSelected()
    }

    @Test
    fun selectingEachMainDestinationClosesDrawerAndShowsItsScreen() {
        setAppContent()

        openDestination(
            "main-destination-summary",
            "Horas y eventos del mes seleccionado.",
        )
        compose.onNodeWithText("Horas y eventos del mes seleccionado.").assertExists()
        compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()

        openDestination("main-destination-appearance", "Tema de MiGuardia")
        listOf(
            "Tema de MiGuardia",
            "Zoom de MiGuardia",
        ).forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()

        openDestination("main-destination-calendar", "Próximo evento")
        compose.onNodeWithText("Próximo evento").assertExists()
        compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
    }

    @Test
    fun everyDirectEntryClosesDrawerBeforeInvokingItsExistingOwner() {
        val opened = mutableListOf<String>()
        val expectedMonth = YearMonth.of(2026, 8)
        compose.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = calendarState(expectedMonth),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    workSetupActions = WorkSetupActions(openOverview = { opened += "work-setup" }),
                    profileActions = ProfileActions(open = { opened += "profile" }),
                    managementActions = ManagementActions(openSettings = { opened += "objectives" }),
                    exceptionsActions = ExceptionsActions(
                        openHolidays = { month -> opened += "holidays:$month" },
                    ),
                    vacationActions = VacationActions(
                        openList = { month -> opened += "vacations:$month" },
                    ),
                    notificationActions = NotificationActions(openGlobal = { opened += "notifications" }),
                    weatherActions = WeatherActions(openGlobal = { opened += "weather" }),
                )
            }
        }

        listOf(
            "drawer-action-work-setup",
            "drawer-action-profile",
            "drawer-action-objectives",
            "drawer-action-holidays",
            "drawer-action-vacations",
            "drawer-action-notifications",
            "drawer-action-weather",
        ).forEach(::openAction)

        compose.runOnIdle {
            assertEquals(
                listOf(
                    "work-setup",
                    "profile",
                    "objectives",
                    "holidays:$expectedMonth",
                    "vacations:$expectedMonth",
                    "notifications",
                    "weather",
                ),
                opened,
            )
        }
    }

    @Test
    fun horizontalMonthGestureChangesMonthWithoutOpeningDrawer() {
        var nextMonthRequests = 0
        setAppContent(onNextMonth = { nextMonthRequests++ })

        compose.onNodeWithTag("month-grid").performTouchInput { swipeLeft() }

        compose.runOnIdle { assertEquals(1, nextMonthRequests) }
        compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
    }

    @Test
    fun calendarNavigationRequestClosesDrawerAndShowsRequestedDay() {
        val month = YearMonth.of(2026, 8)
        val selectedDate = LocalDate.of(2026, 8, 14)
        var request by mutableIntStateOf(0)
        var calendarState by mutableStateOf(calendarState(month).copy(detailDate = null))
        compose.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = calendarState,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    calendarNavigationRequest = request,
                )
            }
        }
        openDestination(
            "main-destination-summary",
            "Horas y eventos del mes seleccionado.",
        )
        compose.onNodeWithContentDescription("Abrir menú").performClick()

        compose.runOnIdle {
            calendarState = calendarState.copy(detailDate = selectedDate)
            request++
        }

        compose.onNodeWithText("Viernes 14 de agosto de 2026").assertExists()
        compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
        compose.onNodeWithTag("main-menu-button").assertIsNotEnabled()
    }

    @Test
    fun everyBlockingSurfaceAndDraftKeepsDrawerClosed() {
        val month = YearMonth.of(2026, 8)
        var management by mutableStateOf(ManagementUiState())
        var exceptions by mutableStateOf(ExceptionsUiState(holidayMonth = month))
        var vacations by mutableStateOf(VacationUiState(visibleMonth = month))
        var photos by mutableStateOf(PhotosUiState(month = month))
        var notifications by mutableStateOf(NotificationUiState())
        var weather by mutableStateOf(WeatherUiState())
        var profile by mutableStateOf(ProfileUiState())
        compose.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = calendarState(month),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    managementState = management,
                    exceptionsState = exceptions,
                    vacationState = vacations,
                    photosState = photos,
                    notificationState = notifications,
                    weatherState = weather,
                    profileState = profile,
                )
            }
        }
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.runOnIdle { management = ManagementUiState(surface = ManagementSurface.OBJECTIVE_FORM) }
        assertMenuBlocked()

        compose.runOnIdle {
            management = ManagementUiState()
            exceptions = exceptions.copy(surface = ExceptionsSurface.HOLIDAYS)
        }
        assertMenuBlocked()
        compose.runOnIdle {
            exceptions = exceptions.copy(surface = ExceptionsSurface.NONE)
            vacations = vacations.copy(surface = VacationSurface.LIST)
        }
        assertMenuBlocked()
        compose.runOnIdle {
            vacations = vacations.copy(surface = VacationSurface.NONE)
            photos = photos.copy(surface = PhotosSurface.LIST)
        }
        assertMenuBlocked()
        compose.runOnIdle {
            photos = photos.copy(surface = PhotosSurface.NONE)
            notifications = notifications.copy(surface = NotificationSurface.GLOBAL)
        }
        assertMenuBlocked()
        compose.runOnIdle {
            notifications = notifications.copy(surface = NotificationSurface.NONE)
            weather = weather.copy(surface = WeatherSurface.GLOBAL)
        }
        assertMenuBlocked()
        compose.runOnIdle {
            weather = weather.copy(surface = WeatherSurface.NONE)
            profile = profile.copy(surface = ProfileSurface.EDITOR)
        }
        assertMenuBlocked()
    }

    @Test
    fun drawerRemainsUsableAcrossThemesZoomsPortraitAndLandscape() {
        var themeMode by mutableStateOf(AppThemeMode.SYSTEM)
        var zoom by mutableStateOf(AppZoom.STANDARD)
        var landscape by mutableStateOf(false)
        compose.setContent {
            MiGuardiaTheme(
                darkTheme = themeMode == AppThemeMode.DARK,
                appZoom = zoom,
            ) {
                Box(
                    Modifier.size(
                        width = if (landscape) 600.dp else 320.dp,
                        height = if (landscape) 320.dp else 600.dp,
                    ),
                ) {
                    MiGuardiaApp(
                        calendarState = calendarState(),
                        onPreviousMonth = {},
                        onNextMonth = {},
                        onToday = {},
                        onSelectDate = {},
                        onDismissDate = {},
                        onRetry = {},
                        appZoom = zoom,
                        appThemeMode = themeMode,
                    )
                }
            }
        }

        AppThemeMode.entries.forEach { mode ->
            AppZoom.entries.forEach { option ->
                compose.runOnIdle {
                    themeMode = mode
                    zoom = option
                }
                compose.onNodeWithContentDescription("Abrir menú").performClick()
                compose.onNodeWithTag("main-destination-calendar").assertIsDisplayed().performClick()
            }
        }
        compose.runOnIdle { landscape = true }
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithTag("main-destination-calendar").assertIsDisplayed()
        compose.onNodeWithTag("main-destination-summary").assertIsDisplayed()
        compose.onNodeWithTag("main-destination-appearance").performScrollTo().assertIsDisplayed()
    }

    private fun setAppContent(onNextMonth: () -> Unit = {}) {
        compose.setContent {
            MiGuardiaTheme {
                MiGuardiaApp(
                    calendarState = calendarState(),
                    onPreviousMonth = {},
                    onNextMonth = onNextMonth,
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                )
            }
        }
    }

    private fun openDestination(tag: String, expectedText: String) {
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitUntil(5_000L) {
            compose.onAllNodesWithText(expectedText).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openAction(tag: String) {
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
    }

    private fun assertMenuBlocked() {
        compose.waitForIdle()
        compose.onNodeWithTag("main-navigation-drawer").assertIsNotDisplayed()
        compose.onNodeWithTag("main-menu-button").assertIsNotEnabled()
    }

    private fun calendarState(month: YearMonth = YearMonth.of(2026, 8)): CalendarUiState {
        val now = Instant.parse("2026-08-13T12:00:00Z")
        return CalendarUiState(
            visibleMonth = month,
            referenceInstant = now,
            days = projectCalendarMonth(
                month = month,
                shifts = emptyList(),
                explicitDayStatuses = emptyList(),
                medicalLeaves = emptyList(),
                now = now,
            ),
            loadState = CalendarLoadState.CONTENT,
        )
    }
}
