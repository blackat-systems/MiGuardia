package com.blackatsystems.miguardia

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.MissingWorkSetupRequirement
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkSetupState
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import com.blackatsystems.miguardia.ui.worksetup.WorkPlaceDraft
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupActions
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupStep
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurface
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupSurfaceHost
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupUiState
import com.blackatsystems.miguardia.ui.worksetup.WorkTemplateDraft
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class RgbColorPickerComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pickerChangesBothAxesAndPropagatesArgbToTheVisibleWorkDraftAtTwoHundredPercent() {
        var confirmed: Int? = null
        var state by mutableStateOf(workSetupState())
        compose.setContent {
            MiGuardiaTheme(darkTheme = true, appZoom = AppZoom.EXTRA_LARGE) {
                WorkSetupSurfaceHost(
                    state = state,
                    actions = WorkSetupActions(
                        updateTemplateDraft = { transform ->
                            state = state.copy(templateDraft = transform(state.templateDraft))
                            confirmed = state.templateDraft.colorArgb
                        },
                    ),
                )
            }
        }

        compose.onNodeWithTag("work-template-color").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Área de saturación y luminosidad")
            .performScrollTo()
            .assertIsDisplayed()
            .performTouchInput { click(center) }
        compose.onNodeWithContentDescription("Barra arcoíris de tono")
            .performScrollTo()
            .assertIsDisplayed()
            .performTouchInput { click(center) }
        compose.onNodeWithText("RGB: 64, 128, 128").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("HEX: #408080").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Usar color").performClick()

        compose.runOnIdle {
            assertNotNull(confirmed)
            assertNotEquals(INITIAL_COLOR, confirmed)
            assertEquals(EXPECTED_COLOR, confirmed)
            assertEquals(EXPECTED_COLOR, state.templateDraft.colorArgb)
        }
        compose.onNodeWithText("Selector de color").assertDoesNotExist()
        compose.onNodeWithText("Cambiar color").performScrollTo().assertIsDisplayed()
    }

    private fun workSetupState(): WorkSetupUiState {
        val timelineId = UUID(0L, 1L)
        val sector = WorkSector.NURSING
        return WorkSetupUiState(
            rootState = WorkSetupState.V2NeedsFirstSet(
                timelineId = timelineId,
                configurationRevision = EffectiveRevision(
                    id = UUID(0L, 2L),
                    effectiveFrom = LocalDate.of(2026, 8, 23),
                    value = WorkConfiguration(sector, HoursReference.PendingSetup, null),
                ),
                missing = MissingWorkSetupRequirement.entries.toSet(),
            ),
            selectedSector = sector,
            surface = WorkSetupSurface.FIRST_WORK_SET,
            step = WorkSetupStep.TYPE_AND_TEMPLATE,
            placeDraft = WorkPlaceDraft(name = "Hospital ficticio", abbreviation = "HFI"),
            templateDraft = WorkTemplateDraft(
                typeName = "Turno habitual",
                startTime = "08:00",
                endTime = "16:00",
                colorArgb = INITIAL_COLOR,
            ),
        )
    }

    private companion object {
        const val INITIAL_COLOR: Int = 0xFF1565C0.toInt()
        val EXPECTED_COLOR: Int = android.graphics.Color.HSVToColor(floatArrayOf(180f, 0.5f, 0.5f))
    }
}
