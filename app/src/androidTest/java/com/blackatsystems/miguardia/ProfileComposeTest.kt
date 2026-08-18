package com.blackatsystems.miguardia

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.ScheduleCombination
import com.blackatsystems.miguardia.profile.ActiveProfileObjective
import com.blackatsystems.miguardia.ui.MiGuardiaApp
import com.blackatsystems.miguardia.ui.calendar.CalendarLoadState
import com.blackatsystems.miguardia.ui.calendar.CalendarUiState
import com.blackatsystems.miguardia.ui.profile.ProfileActions
import com.blackatsystems.miguardia.ui.profile.ProfileDraft
import com.blackatsystems.miguardia.ui.profile.ProfileSurface
import com.blackatsystems.miguardia.ui.profile.ProfileSurfaceHost
import com.blackatsystems.miguardia.ui.profile.ProfileUiState
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import java.time.Instant
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun profileEditsOptionalNameAndCompanyAndSubmitsOnce() {
        var state by mutableStateOf(
            ProfileUiState(surface = ProfileSurface.EDITOR, isLoading = false),
        )
        var saves = 0
        compose.setContent {
            MaterialTheme {
                ProfileSurfaceHost(
                    state = state,
                    actions = ProfileActions(
                        updateDisplayName = { state = state.copy(draft = state.draft.copy(displayName = it)) },
                        updateCompany = { state = state.copy(draft = state.draft.copy(company = it)) },
                        save = { saves++ },
                    ),
                )
            }
        }

        compose.onNodeWithText("Vigilancia y seguridad").assertExists()
        compose.onNodeWithTag("profile-display-name").performTextReplacement("Persona ficticia")
        compose.onNodeWithTag("profile-company").performTextReplacement("Empresa ficticia")
        compose.onNodeWithText("Guardar perfil").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("Persona ficticia", state.draft.displayName)
            assertEquals("Empresa ficticia", state.draft.company)
            assertEquals(1, saves)
        }
    }

    @Test
    fun blankCompanyShowsValidationAndCannotSubmit() {
        compose.setContent {
            MaterialTheme {
                ProfileSurfaceHost(
                    state = ProfileUiState(
                        surface = ProfileSurface.EDITOR,
                        draft = ProfileDraft(company = ""),
                        isLoading = false,
                    ),
                    actions = ProfileActions(),
                )
            }
        }

        compose.onNodeWithText("La empresa es obligatoria.").assertExists()
        compose.onNodeWithText("Guardar perfil").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun profileShowsLocalDefaultsAndEmptyStateOpensRealManagement() {
        var opened = 0
        compose.setContent {
            MaterialTheme {
                ProfileSurfaceHost(
                    state = ProfileUiState(
                        surface = ProfileSurface.EDITOR,
                        isLoading = false,
                    ),
                    actions = ProfileActions(openObjectives = { opened++ }),
                )
            }
        }

        compose.onNodeWithText("Este perfil es local.", substring = true).assertExists()
        compose.onNodeWithText("Nombre o apodo (opcional)").assertExists()
        compose.onNodeWithText("Vigilancia y seguridad").assertExists()
        compose.onNodeWithText("Inforce").assertExists()
        compose.onNodeWithText("Todavía no hay objetivos activos").performScrollTo().assertExists()
        compose.onNodeWithText("Ir a Objetivos y horarios").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun activeProjectionIsReadOnlyAndUsesTheRealManagementEntry() {
        var opened = 0
        compose.setContent {
            MaterialTheme {
                ProfileSurfaceHost(
                    state = ProfileUiState(
                        surface = ProfileSurface.EDITOR,
                        activeObjectives = listOf(ActiveProfileObjective(OBJECTIVE, listOf(SCHEDULE))),
                        isLoading = false,
                    ),
                    actions = ProfileActions(openObjectives = { opened++ }),
                )
            }
        }

        compose.onNodeWithText("Objetivo ficticio (FIC)").performScrollTo().assertExists()
        compose.onNodeWithText("19:00–07:00").assertExists()
        compose.onNodeWithText("Editar objetivo").assertDoesNotExist()
        compose.onNodeWithText("Administrar Objetivos y horarios").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun discardConfirmationProtectsTheDraft() {
        var discarded = 0
        var continued = 0
        compose.setContent {
            MaterialTheme {
                ProfileSurfaceHost(
                    state = ProfileUiState(
                        surface = ProfileSurface.EDITOR,
                        showDiscardConfirmation = true,
                        isLoading = false,
                    ),
                    actions = ProfileActions(
                        confirmDiscard = { discarded++ },
                        dismissDiscard = { continued++ },
                    ),
                )
            }
        }

        compose.onNodeWithText("Los cambios del perfil todavía no fueron guardados.", substring = true).assertExists()
        compose.onNodeWithText("Seguir editando").performClick()
        compose.onNodeWithText("Descartar").performClick()
        compose.runOnIdle {
            assertEquals(1, continued)
            assertEquals(1, discarded)
        }
    }

    @Test
    fun retryIsOnlyOfferedForReadFailures() {
        var state by mutableStateOf(
            ProfileUiState(
                surface = ProfileSurface.EDITOR,
                isLoading = false,
                errorMessage = "No pudimos guardar el perfil. Intentá nuevamente.",
            ),
        )
        compose.setContent {
            MaterialTheme {
                ProfileSurfaceHost(state = state, actions = ProfileActions())
            }
        }

        compose.onNodeWithText("Reintentar").assertDoesNotExist()
        compose.runOnUiThread { state = state.copy(canRetryLoad = true) }
        compose.onNodeWithText("Reintentar").assertExists()
    }

    @Test
    fun profileControlsRemainReachableAcrossThemesAndInternalZooms() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        var zoom by mutableStateOf(AppZoom.STANDARD)
        var darkTheme by mutableStateOf(false)
        try {
            device.setOrientationLeft()
            device.waitForIdle()
            compose.setContent {
                MiGuardiaTheme(darkTheme = darkTheme, appZoom = zoom) {
                    ProfileSurfaceHost(
                        state = ProfileUiState(
                            surface = ProfileSurface.EDITOR,
                            isLoading = false,
                        ),
                        actions = ProfileActions(),
                    )
                }
            }

            for (theme in listOf(false, true)) {
                for (option in AppZoom.entries) {
                    compose.runOnUiThread {
                        darkTheme = theme
                        zoom = option
                    }
                    compose.waitForIdle()
                    compose.onNodeWithTag("profile-display-name").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithTag("profile-company").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText("Guardar perfil").performScrollTo().assertIsDisplayed()
                }
            }
        } finally {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    @Test
    fun settingsExposeOneProfileEntryAtTwoHundredPercent() {
        var opened = 0
        compose.setContent {
            MiGuardiaTheme(appZoom = AppZoom.EXTRA_LARGE) {
                MiGuardiaApp(
                    calendarState = CalendarUiState(
                        visibleMonth = YearMonth.of(2026, 8),
                        referenceInstant = Instant.parse("2026-08-18T12:00:00Z"),
                        loadState = CalendarLoadState.CONTENT,
                    ),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onSelectDate = {},
                    onDismissDate = {},
                    onRetry = {},
                    profileActions = ProfileActions(open = { opened++ }),
                    appZoom = AppZoom.EXTRA_LARGE,
                )
            }
        }

        compose.onNodeWithContentDescription("Configuración").performClick()
        compose.onAllNodesWithText("Perfil laboral").assertCountEquals(1)
        listOf(
            "Objetivos y horarios",
            "Feriados",
            "Vacaciones",
            "Notificaciones",
            "Clima",
        ).forEach { entry ->
            compose.onAllNodesWithText(entry).assertCountEquals(1)
        }
        compose.onNodeWithText("Seguir el sistema").performScrollTo().assertIsDisplayed()
        listOf("100 %", "150 %", "200 %").forEach { option ->
            compose.onNodeWithText(option).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithContentDescription(
            "Perfil laboral. Nombre opcional, profesión y empresa actual.",
        ).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    private companion object {
        val OBJECTIVE = Objective(
            id = UUID.fromString("00000000-0000-0000-0000-000000001321"),
            fullName = "Objetivo ficticio",
            abbreviation = "FIC",
            address = null,
            note = null,
            isActive = true,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val SCHEDULE = ScheduleCombination(
            id = UUID.fromString("00000000-0000-0000-0000-000000001322"),
            objectiveId = OBJECTIVE.id,
            startTime = LocalTime.of(19, 0),
            endTime = LocalTime.of(7, 0),
            colorArgb = 0xff336699.toInt(),
            isActive = true,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}
