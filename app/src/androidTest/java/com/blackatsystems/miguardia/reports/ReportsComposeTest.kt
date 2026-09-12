package com.blackatsystems.miguardia.reports

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import com.blackatsystems.miguardia.core.domain.report.ReportFormat
import com.blackatsystems.miguardia.core.domain.report.ReportPrivacySelection
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReportsComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyPrivateInclusionStartsOffAndXlsxExplainsThatPhotosArePdfOnly() {
        var state by mutableStateOf(contentState(displayNameAvailable = true))
        val actions = ReportsActions(
            setFormat = { format -> state = state.copy(format = format) },
            setDisplayNameIncluded = { included ->
                state = state.copy(privacy = state.privacy.copy(includeDisplayName = included))
            },
        )
        setReports({ state }, actions)

        listOf(
            "reports-include-name",
            "reports-include-position",
            "reports-include-shift-notes",
            "reports-include-medical-notes",
            "reports-include-photos",
        ).forEach { tag -> compose.onNodeWithTag(tag).performScrollTo().assertIsOff() }
        compose.onNodeWithTag("reports-include-name").assertIsEnabled().performClick()
        compose.onNodeWithTag("reports-include-name").assertIsOn()
        compose.onNodeWithTag("reports-include-name")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        compose.onNodeWithTag("reports-include-name-switch", useUnmergedTree = true)
            .assertHasNoClickAction()

        compose.onNodeWithTag("reports-format-xlsx").performScrollTo().performClick()
        compose.onNodeWithTag("reports-format-xlsx")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onNodeWithTag("reports-format-xlsx-radio", useUnmergedTree = true)
            .assertHasNoClickAction()
        compose.onNodeWithTag("reports-xlsx-photo-explanation").assertIsDisplayed()
        compose.runOnIdle { assertEquals(ReportFormat.XLSX, state.format) }
    }

    @Test
    fun medicalNoteRequiresASecondExplicitConfirmation() {
        var state by mutableStateOf(contentState())
        val actions = ReportsActions(
            requestMedicalNotes = { included ->
                assertTrue(included)
                state = state.copy(medicalConfirmationPending = true)
            },
            confirmMedicalNotes = {
                state = state.copy(
                    medicalConfirmationPending = false,
                    privacy = state.privacy.copy(includeMedicalNotes = true),
                )
            },
        )
        setReports({ state }, actions)

        compose.onNodeWithTag("reports-include-medical-notes").performScrollTo().performClick()
        compose.onNodeWithText("Incluir una nota médica privada").assertIsDisplayed()
        compose.onNodeWithTag("reports-include-medical-notes").assertIsOff()
        compose.onNodeWithTag("reports-confirm-medical-note").performClick()
        compose.onNodeWithTag("reports-include-medical-notes").performScrollTo().assertIsOn()
    }

    @Test
    fun loadingEmptyGeneratingSavingSharingAndErrorRemainDistinctAndActionable() {
        var retries = 0
        var state by mutableStateOf(
            ReportsUiState(
                isOpen = true,
                month = MONTH,
                stage = ReportsStage.LOADING,
            ),
        )
        setReports({ state }, ReportsActions(retry = { retries++ }))

        compose.onNodeWithTag("reports-loading").assertIsDisplayed()
        compose.runOnIdle {
            state = contentState().copy(
                stage = ReportsStage.EMPTY,
                preview = reportProjectionFixture().copy(situations = emptyList()),
            )
        }
        compose.onNodeWithText("Sin actividad registrada").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reports-generate").performScrollTo().assertIsEnabled()
        compose.runOnIdle { state = state.copy(stage = ReportsStage.GENERATING) }
        compose.onNodeWithTag("reports-generating").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(stage = ReportsStage.SAVING) }
        compose.onNodeWithTag("reports-saving").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(stage = ReportsStage.SHARING) }
        compose.onNodeWithTag("reports-sharing").assertIsDisplayed()
        compose.runOnIdle {
            state = state.copy(stage = ReportsStage.ERROR, errorMessage = "Error verificable de archivo")
        }
        compose.onNodeWithTag("reports-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun surfaceRemainsReachableInLightDarkAndEveryInternalZoom() {
        var dark by mutableStateOf(false)
        var zoom by mutableStateOf(AppZoom.STANDARD)
        val state = contentState()
        compose.setContent {
            MiGuardiaTheme(darkTheme = dark, appZoom = zoom) {
                ReportsSurfaceHost(state, ReportsActions())
            }
        }

        AppZoom.entries.forEach { candidate ->
            compose.runOnIdle {
                dark = candidate != AppZoom.STANDARD
                zoom = candidate
            }
            compose.onNodeWithTag("reports-surface").assertIsDisplayed()
            compose.onNodeWithTag("reports-back-summary").performScrollTo().assertIsDisplayed()
            if (candidate == AppZoom.EXTRA_LARGE) {
                val titleBounds = compose.onNode(
                    hasText("Informes locales") and hasAnyAncestor(hasTestTag("reports-header")),
                    useUnmergedTree = true,
                ).fetchSemanticsNode().boundsInRoot
                val navigationBounds = compose.onNode(
                    hasText("Volver al Resumen") and hasAnyAncestor(hasTestTag("reports-header")),
                    useUnmergedTree = true,
                ).fetchSemanticsNode().boundsInRoot
                compose.runOnIdle {
                    assertTrue(titleBounds.bottom <= navigationBounds.top)
                }
            }
        }
    }

    private fun setReports(
        state: () -> ReportsUiState,
        actions: ReportsActions,
    ) {
        compose.setContent {
            MiGuardiaTheme {
                ReportsSurfaceHost(state(), actions)
            }
        }
    }

    private fun contentState(displayNameAvailable: Boolean = false): ReportsUiState = ReportsUiState(
        isOpen = true,
        month = MONTH,
        format = ReportFormat.PDF,
        privacy = ReportPrivacySelection(),
        stage = ReportsStage.CONTENT,
        preview = reportProjectionFixture(),
        displayNameAvailable = displayNameAvailable,
    )

    private companion object {
        val MONTH: YearMonth = YearMonth.of(2026, 8)
    }
}
