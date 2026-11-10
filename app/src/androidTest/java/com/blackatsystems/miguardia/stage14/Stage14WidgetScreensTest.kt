package com.blackatsystems.miguardia.stage14

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.ui.widget.InstalledWidgetUi
import com.blackatsystems.miguardia.ui.widget.WidgetActions
import com.blackatsystems.miguardia.ui.widget.WidgetSurface
import com.blackatsystems.miguardia.ui.widget.WidgetSurfaceHost
import com.blackatsystems.miguardia.ui.widget.WidgetUiState
import com.blackatsystems.miguardia.widget.WidgetConfigurationDraft
import com.blackatsystems.miguardia.widget.WidgetConfigurationScreen
import com.blackatsystems.miguardia.widget.WidgetInstancePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Etapa 14 · Widget de inicio: pantallas reales de gestión y configuración con estado ficticio. */
class Stage14WidgetScreensTest {
    @get:Rule
    val compose = createComposeRule()

    // W001
    @Test
    fun emptyManagementExplainsHowToAddFromAndroidAndOffersNoOwnAddButton() {
        var closes = 0
        compose.setContent {
            AndroidFontScaleTheme {
                WidgetSurfaceHost(
                    state = WidgetUiState(surface = WidgetSurface.GLOBAL),
                    actions = WidgetActions(close = { closes++ }),
                )
            }
        }

        compose.onNodeWithText("Widget de inicio").assertIsDisplayed()
        compose.onNodeWithText("Gestioná las instancias que agregaste a la pantalla de inicio de Android.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Todavía no hay Widgets").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "Mantené presionada la pantalla de inicio de Android, elegí Widgets y buscá MiGuardia.",
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("La configuración inicial es obligatoria y empieza en privacidad Oculta.")
            .performScrollTo().assertIsDisplayed()

        // El alta es de Android: en la pantalla vacía lo único que se puede tocar es «Cerrar».
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithTag("widget-management-close").assertTextEquals("Cerrar").performClick()
        compose.runOnIdle { assertEquals(1, closes) }
        listOf("Agregar", "Añadir", "Fijar", "Configurar").forEach { ownAction ->
            compose.onAllNodesWithText(ownAction, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }

    // W002
    @Test
    fun managementListsInstancesInOrderWithModePrivacyWeatherAndTheMatchingAction() {
        val selected = mutableListOf<Int>()
        val instances = listOf(
            InstalledWidgetUi(
                appWidgetId = 7001,
                position = 1,
                preferences = WidgetInstancePreferences(
                    mode = WidgetMode.NEXT_SHIFT,
                    privacy = WidgetPrivacy.COMPLETE,
                    includeWeather = true,
                    configured = true,
                ),
            ),
            InstalledWidgetUi(
                appWidgetId = 7002,
                position = 2,
                preferences = WidgetInstancePreferences(
                    mode = WidgetMode.NEXT_DAY_OFF,
                    privacy = WidgetPrivacy.REDUCED,
                    includeWeather = false,
                    configured = true,
                ),
            ),
            InstalledWidgetUi(
                appWidgetId = 7003,
                position = 3,
                preferences = WidgetInstancePreferences(
                    mode = WidgetMode.AUTOMATIC,
                    privacy = WidgetPrivacy.HIDDEN,
                    includeWeather = false,
                    configured = true,
                ),
            ),
            InstalledWidgetUi(appWidgetId = 7004, position = 4, preferences = WidgetInstancePreferences()),
        )
        compose.setContent {
            AndroidFontScaleTheme {
                WidgetSurfaceHost(
                    state = WidgetUiState(surface = WidgetSurface.GLOBAL, instances = instances),
                    actions = WidgetActions(reconfigure = { selected += it }),
                )
            }
        }

        val summaries = listOf(
            "Próxima jornada · Completa",
            "Próximo franco · Reducida",
            "Automático · Oculta",
            "Configuración pendiente · Oculta",
        )
        summaries.forEachIndexed { index, summary ->
            compose.onNodeWithText("Widget ${index + 1}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(summary).performScrollTo().assertIsDisplayed()
        }
        compose.onAllNodesWithText("Clima opcional activado para esta instancia.").assertCountEquals(1)
        compose.onAllNodesWithText("Clima opcional desactivado para esta instancia.").assertCountEquals(3)

        // Orden visible: cada tarjeta queda debajo de la anterior.
        val titleTops = (1..4).map { position ->
            compose.onNodeWithText("Widget $position").fetchSemanticsNode().positionInRoot.y
        }
        assertEquals(titleTops.sorted(), titleTops)
        assertEquals(4, titleTops.distinct().size)
        // El Clima activado pertenece a la primera tarjeta, no a otra.
        val weatherOnTop = compose.onNodeWithText("Clima opcional activado para esta instancia.")
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(weatherOnTop > titleTops[0] && weatherOnTop < titleTops[1])

        listOf("Reconfigurar", "Reconfigurar", "Reconfigurar", "Configurar").forEachIndexed { index, action ->
            compose.onNodeWithTag("widget-reconfigure-${index + 1}")
                .performScrollTo()
                .assertTextEquals(action)
                .performClick()
        }
        compose.runOnIdle { assertEquals(listOf(7001, 7002, 7003, 7004), selected) }
    }

    // W004
    @Test
    fun firstConfigurationOpensWithAutomaticHiddenAndWeatherOffSelected() {
        // El mismo recorrido de la Activity: el borrador nace de las preferencias de una instancia nueva.
        val initial = WidgetInstancePreferences()
        compose.setContent {
            AndroidFontScaleTheme {
                WidgetConfigurationScreen(
                    draft = WidgetConfigurationDraft(
                        mode = initial.mode,
                        privacy = initial.privacy,
                        includeWeather = initial.includeWeather,
                    ),
                    isReconfiguration = initial.configured,
                    saving = false,
                    onDraftChange = {},
                    onCancel = {},
                    onSave = {},
                )
            }
        }

        compose.onNodeWithText("Configurar Widget").assertIsDisplayed()
        compose.onNodeWithTag("widget-mode-automatic").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("widget-mode-next_shift").performScrollTo().assertIsNotSelected()
        compose.onNodeWithTag("widget-mode-next_day_off").performScrollTo().assertIsNotSelected()
        compose.onNodeWithTag("widget-privacy-hidden").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("widget-privacy-complete").performScrollTo().assertIsNotSelected()
        compose.onNodeWithTag("widget-privacy-reduced").performScrollTo().assertIsNotSelected()
        compose.onNodeWithTag("widget-weather-toggle").performScrollTo().assertIsOff()
        compose.onNodeWithTag("widget-save").performScrollTo().assertIsEnabled().assertTextEquals("Continuar")
    }

    // W012
    @Test
    fun activeSaveLocksSaveAndCancelSoASecondTapDoesNothing() {
        var saves = 0
        var cancels = 0
        var saving by mutableStateOf(false)
        compose.setContent {
            AndroidFontScaleTheme {
                WidgetConfigurationScreen(
                    draft = WidgetConfigurationDraft(WidgetMode.NEXT_SHIFT, WidgetPrivacy.REDUCED, includeWeather = false),
                    isReconfiguration = true,
                    saving = saving,
                    onDraftChange = {},
                    onCancel = { cancels++ },
                    onSave = { saves++ },
                )
            }
        }

        compose.onNodeWithTag("widget-save").performScrollTo().assertIsEnabled()
            .assertTextEquals("Guardar cambios").performClick()
        compose.runOnIdle {
            assertEquals(1, saves)
            saving = true
        }

        compose.onNodeWithTag("widget-save").performScrollTo().assertIsNotEnabled()
            .assertTextEquals("Guardando…").performClick()
        compose.onNodeWithTag("widget-cancel").performScrollTo().assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, saves)
            assertEquals(0, cancels)
        }
    }

    // W012, con letra de Android al 200 %
    @Test
    fun failedSaveShowsTheErrorAndLetsThePersonRetryOrCancel() {
        var saves = 0
        var cancels = 0
        var saving by mutableStateOf(true)
        var error by mutableStateOf<String?>(null)
        compose.setContent {
            AndroidFontScaleTheme(fontScale = TestFontScale.EXTRA_LARGE) {
                WidgetConfigurationScreen(
                    draft = WidgetConfigurationDraft(WidgetMode.AUTOMATIC, WidgetPrivacy.HIDDEN, includeWeather = false),
                    isReconfiguration = false,
                    saving = saving,
                    errorMessage = error,
                    onDraftChange = {},
                    onCancel = { cancels++ },
                    onSave = { saves++ },
                )
            }
        }

        compose.onNodeWithTag("widget-save-error").assertDoesNotExist()
        compose.runOnIdle {
            // Lo que hace la Activity cuando el guardado falla (WidgetConfigurationActivity.kt:370-373).
            saving = false
            error = "No pudimos guardar la configuración. Probá nuevamente."
        }

        compose.onNodeWithTag("widget-save-error").performScrollTo().assertIsDisplayed()
            .assertTextEquals("No pudimos guardar la configuración. Probá nuevamente.")
        compose.onNodeWithTag("widget-save").performScrollTo().assertIsDisplayed().assertIsEnabled()
            .assertTextEquals("Continuar").performClick()
        compose.onNodeWithTag("widget-cancel").performScrollTo().assertIsDisplayed().assertIsEnabled()
            .performClick()
        compose.runOnIdle {
            assertEquals(1, saves)
            assertEquals(1, cancels)
        }
    }
}
