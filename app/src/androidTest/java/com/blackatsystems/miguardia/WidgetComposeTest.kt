package com.blackatsystems.miguardia

import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.widget.InstalledWidgetUi
import com.blackatsystems.miguardia.ui.widget.WidgetActions
import com.blackatsystems.miguardia.ui.widget.WidgetSurface
import com.blackatsystems.miguardia.ui.widget.WidgetSurfaceHost
import com.blackatsystems.miguardia.ui.widget.WidgetUiState
import com.blackatsystems.miguardia.widget.WidgetConfigurationDraft
import com.blackatsystems.miguardia.widget.WidgetConfigurationScreen
import com.blackatsystems.miguardia.widget.WidgetInstancePreferences
import com.blackatsystems.miguardia.widget.rememberWidgetConfigurationSavingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class WidgetComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun configurationSelectsAllFieldsAndKeepsSaveSeparateFromCancel() {
        var draft by mutableStateOf(
            WidgetConfigurationDraft(
                WidgetMode.AUTOMATIC,
                WidgetPrivacy.HIDDEN,
                includeWeather = false,
            ),
        )
        var saved: WidgetConfigurationDraft? = null
        var cancelled = 0
        compose.setContent {
            MiGuardiaTheme {
                WidgetConfigurationScreen(
                    draft = draft,
                    isReconfiguration = true,
                    saving = false,
                    onDraftChange = { draft = it },
                    onCancel = { cancelled++ },
                    onSave = { saved = draft },
                )
            }
        }

        compose.onNodeWithTag("widget-mode-next_shift").performScrollTo().performClick()
        compose.onNodeWithTag("widget-privacy-complete").performScrollTo().performClick()
        compose.onNodeWithTag("widget-weather-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("widget-mode-next_shift").assertIsSelected()
        compose.onNodeWithTag("widget-privacy-complete").assertIsSelected()
        compose.onNodeWithTag("widget-cancel").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, cancelled)
            assertNull(saved)
        }
        compose.onNodeWithTag("widget-save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(
                WidgetConfigurationDraft(WidgetMode.NEXT_SHIFT, WidgetPrivacy.COMPLETE, true),
                saved,
            )
        }
    }

    @Test
    fun configurationRemainsUsableAtEveryInternalZoom() {
        var zoom by mutableStateOf(AppZoom.STANDARD)
        compose.setContent {
            MiGuardiaTheme(appZoom = zoom) {
                WidgetConfigurationScreen(
                    draft = WidgetConfigurationDraft(
                        WidgetMode.NEXT_DAY_OFF,
                        WidgetPrivacy.REDUCED,
                        includeWeather = false,
                    ),
                    isReconfiguration = false,
                    saving = false,
                    onDraftChange = {},
                    onCancel = {},
                    onSave = {},
                )
            }
        }

        AppZoom.entries.forEach { option ->
            compose.runOnIdle { zoom = option }
            compose.onNodeWithTag("widget-configuration-screen").assertIsDisplayed()
            compose.onNodeWithTag("widget-save").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("widget-cancel").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun configurationDraftSurvivesActivityStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            var modeName by rememberSaveable { mutableStateOf(WidgetMode.AUTOMATIC.name) }
            var privacyName by rememberSaveable { mutableStateOf(WidgetPrivacy.HIDDEN.name) }
            var includeWeather by rememberSaveable { mutableStateOf(false) }
            val draft = WidgetConfigurationDraft(
                mode = WidgetMode.valueOf(modeName),
                privacy = WidgetPrivacy.valueOf(privacyName),
                includeWeather = includeWeather,
            )
            MiGuardiaTheme {
                WidgetConfigurationScreen(
                    draft = draft,
                    isReconfiguration = false,
                    saving = false,
                    onDraftChange = { changed ->
                        modeName = changed.mode.name
                        privacyName = changed.privacy.name
                        includeWeather = changed.includeWeather
                    },
                    onCancel = {},
                    onSave = {},
                )
            }
        }

        compose.onNodeWithTag("widget-mode-next_shift").performScrollTo().performClick()
        compose.onNodeWithTag("widget-privacy-complete").performScrollTo().performClick()
        compose.onNodeWithTag("widget-weather-toggle").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag("widget-mode-next_shift").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("widget-privacy-complete").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("widget-weather-toggle").performScrollTo().assertIsOn()
    }

    @Test
    fun inFlightSaveDoesNotSurviveActivityStateRestoration() {
        val restoration = StateRestorationTester(compose)
        lateinit var savingState: MutableState<Boolean>
        restoration.setContent {
            savingState = rememberWidgetConfigurationSavingState()
            MiGuardiaTheme {
                WidgetConfigurationScreen(
                    draft = WidgetConfigurationDraft(
                        WidgetMode.AUTOMATIC,
                        WidgetPrivacy.HIDDEN,
                        includeWeather = false,
                    ),
                    isReconfiguration = false,
                    saving = savingState.value,
                    onDraftChange = {},
                    onCancel = {},
                    onSave = {},
                )
            }
        }

        compose.runOnIdle { savingState.value = true }
        compose.onNodeWithText("Guardando…").performScrollTo().assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        compose.runOnIdle { assertFalse(savingState.value) }
        compose.onNodeWithText("Continuar").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun internalManagementNamesInstancesWithoutShowingTechnicalIds() {
        var selected: Int? = null
        compose.setContent {
            MiGuardiaTheme {
                WidgetSurfaceHost(
                    state = WidgetUiState(
                        surface = WidgetSurface.GLOBAL,
                        instances = listOf(
                            InstalledWidgetUi(
                                appWidgetId = 4815,
                                position = 1,
                                preferences = WidgetInstancePreferences(
                                    WidgetMode.AUTOMATIC,
                                    WidgetPrivacy.REDUCED,
                                    includeWeather = false,
                                    configured = true,
                                ),
                            ),
                        ),
                    ),
                    actions = WidgetActions(reconfigure = { selected = it }),
                )
            }
        }

        compose.onNodeWithText("Widget 1").assertIsDisplayed()
        compose.onNodeWithText("4815").assertDoesNotExist()
        compose.onNodeWithTag("widget-reconfigure-1").performClick()
        compose.runOnIdle { assertEquals(4815, selected) }
    }

    @Test
    fun internalManagementExposesLoadingErrorRetryAndEmptyStates() {
        var state by mutableStateOf(
            WidgetUiState(
                surface = WidgetSurface.GLOBAL,
                isLoading = true,
            ),
        )
        var retries = 0
        compose.setContent {
            MiGuardiaTheme {
                WidgetSurfaceHost(
                    state = state,
                    actions = WidgetActions(refresh = { retries++ }),
                )
            }
        }

        compose.onNodeWithTag("widget-loading").assertIsDisplayed()
        compose.runOnIdle {
            state = state.copy(
                isLoading = false,
                errorMessage = "No pudimos leer los Widgets instalados.",
            )
        }
        compose.onNodeWithText("No pudimos leer los Widgets instalados.").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            state = state.copy(errorMessage = null)
        }
        compose.onNodeWithText("Todavía no hay Widgets").assertIsDisplayed()
    }
}
