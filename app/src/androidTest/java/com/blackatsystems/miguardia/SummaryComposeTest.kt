package com.blackatsystems.miguardia

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryEssentials
import com.blackatsystems.miguardia.core.domain.summary.MonthlySummaryProjection
import com.blackatsystems.miguardia.core.domain.summary.SummaryContribution
import com.blackatsystems.miguardia.core.domain.summary.SummaryContributionKind
import com.blackatsystems.miguardia.core.domain.summary.SummaryMetric
import com.blackatsystems.miguardia.core.domain.summary.SummaryOptionalFamily
import com.blackatsystems.miguardia.core.domain.summary.SummaryOptionalSection
import com.blackatsystems.miguardia.core.domain.summary.SummaryValueUnit
import com.blackatsystems.miguardia.ui.summary.SummaryActions
import com.blackatsystems.miguardia.ui.summary.SummaryLoadState
import com.blackatsystems.miguardia.ui.summary.SummaryPreferences
import com.blackatsystems.miguardia.ui.summary.SummaryScreen
import com.blackatsystems.miguardia.ui.summary.SummarySurface
import com.blackatsystems.miguardia.ui.summary.SummaryUiState
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SummaryComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loadingEmptyAndErrorAreDistinctAndRetryIsExplicit() {
        var state by mutableStateOf(SummaryUiState(MONTH))
        var retries = 0
        setSummary({ state }, SummaryActions(retry = { retries++ }))

        compose.onNodeWithTag("summary-loading").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(loadState = SummaryLoadState.EMPTY) }
        compose.onNodeWithTag("summary-empty").assertIsDisplayed()
        compose.runOnIdle {
            state = state.copy(
                loadState = SummaryLoadState.ERROR,
                errorMessage = "No pudimos cargar este resumen. Reintentá.",
            )
        }
        compose.onNodeWithTag("summary-error").assertIsDisplayed()
        compose.onNodeWithTag("summary-source-warning").assertDoesNotExist()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun introAndThreeDotMenuOpenPersonalizationWithoutChangingProjection() {
        val projection = projection()
        var state by mutableStateOf(
            SummaryUiState(
                MONTH,
                SummaryLoadState.CONTENT,
                projection,
                introVisible = true,
            ),
        )
        val actions = SummaryActions(
            dismissIntro = { state = state.copy(introVisible = false) },
            openPersonalization = { state = state.copy(surface = SummarySurface.PERSONALIZATION) },
            back = { state = state.copy(surface = SummarySurface.OVERVIEW) },
        )
        setSummary({ state }, actions)

        compose.onNodeWithTag("summary-intro").assertIsDisplayed()
        compose.onNodeWithTag("summary-intro-understood").performClick()
        compose.onNodeWithTag("summary-intro").assertDoesNotExist()
        compose.onNodeWithTag("summary-menu").performClick()
        compose.onNodeWithText("Personalizar resumen").performClick()
        compose.onNodeWithTag("summary-personalization").assertIsDisplayed()
        compose.onNodeWithTag("summary-personalization-back").performClick()
        compose.onNodeWithTag("summary-overview").assertIsDisplayed()
        compose.onNodeWithContentDescription("Total trabajado: 2 h. Qué incluye este valor").assertIsDisplayed()
    }

    @Test
    fun visibleSummaryOffersTheReportEntryAndCallsTheDedicatedAction() {
        var openings = 0
        val state = SummaryUiState(MONTH, SummaryLoadState.CONTENT, projection())
        setSummary({ state }, SummaryActions(openReports = { openings++ }))

        compose.onNodeWithTag("summary-generate-report").performScrollTo().performClick()

        compose.runOnIdle { assertEquals(1, openings) }
    }

    @Test
    fun hiddenEmptyAndOrderedFamiliesFollowPreferences() {
        val ordered = listOf(
            SummaryOptionalFamily.HOLIDAYS,
            SummaryOptionalFamily.NIGHTS,
        ) + SummaryOptionalFamily.entries.filterNot {
            it == SummaryOptionalFamily.HOLIDAYS || it == SummaryOptionalFamily.NIGHTS
        }
        val state = SummaryUiState(
            MONTH,
            SummaryLoadState.CONTENT,
            projection = projection(),
            preferences = SummaryPreferences(
                orderedFamilies = ordered,
                hiddenFamilies = setOf(SummaryOptionalFamily.NIGHTS),
                introSeen = true,
            ),
        )
        setSummary({ state })

        compose.onNodeWithTag("summary-family-holidays").assertIsDisplayed()
        compose.onNodeWithTag("summary-family-nights").assertDoesNotExist()
        compose.onNodeWithTag("summary-family-weekends").assertDoesNotExist()
    }

    @Test
    fun metricDetailUsesTheExactRowsAndNeverDisplaysPrivateFields() {
        val projection = projection()
        var state by mutableStateOf(SummaryUiState(MONTH, SummaryLoadState.CONTENT, projection))
        val actions = SummaryActions(
            openMetric = { id -> state = state.copy(surface = SummarySurface.DETAIL, selectedMetricId = id) },
            back = { state = state.copy(surface = SummarySurface.OVERVIEW, selectedMetricId = null) },
        )
        setSummary({ state }, actions)

        compose.onNodeWithTag("summary-metric-essential-total").performClick()
        compose.onNodeWithTag("summary-detail").assertIsDisplayed()
        compose.onNodeWithText("Suma exacta: 2 h").assertIsDisplayed()
        compose.onNodeWithText("Lugar ficticio (LF)", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Dirección privada").assertDoesNotExist()
        compose.onNodeWithText("Motivo médico privado").assertDoesNotExist()
        compose.onNodeWithText("Foto privada").assertDoesNotExist()
    }

    @Test
    fun personalizationHasAccessibleVisibilityAndBoundaryMoveControls() {
        val preferences = SummaryPreferences(introSeen = true)
        var hidden: SummaryOptionalFamily? = null
        var moved: SummaryOptionalFamily? = null
        val state = SummaryUiState(
            MONTH,
            SummaryLoadState.CONTENT,
            projection(),
            preferences,
            surface = SummarySurface.PERSONALIZATION,
        )
        setSummary(
            { state },
            SummaryActions(
                setFamilyVisible = { family, visible -> if (!visible) hidden = family },
                moveFamilyDown = { moved = it },
            ),
        )

        compose.onNodeWithTag("summary-move-up-nights").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Subir Noches").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Bajar Noches").assertIsEnabled()
        compose.onNodeWithTag("summary-move-down-nights").assertIsEnabled().performClick()
        compose.onNodeWithTag("summary-toggle-nights").performClick()
        compose.runOnIdle {
            assertEquals(SummaryOptionalFamily.NIGHTS, hidden)
            assertEquals(SummaryOptionalFamily.NIGHTS, moved)
        }
    }

    @Test
    fun restoredDetailAndPersonalizationWaitForRealDataAndExposeRetryOnFailure() {
        var retries = 0
        var state by mutableStateOf(
            SummaryUiState(
                visibleMonth = MONTH,
                loadState = SummaryLoadState.LOADING,
                surface = SummarySurface.DETAIL,
                selectedMetricId = "essential:total",
            ),
        )
        setSummary({ state }, SummaryActions(retry = { retries++ }))

        compose.onNodeWithTag("summary-detail-loading").assertIsDisplayed()
        compose.onNodeWithText("Esta cifra ya no está disponible", substring = true).assertDoesNotExist()
        compose.runOnIdle {
            state = state.copy(
                loadState = SummaryLoadState.ERROR,
                errorMessage = "No pudimos cargar este resumen. Reintentá.",
            )
        }
        compose.onNodeWithTag("summary-detail-error").assertIsDisplayed()
        compose.onNodeWithTag("summary-detail-source-warning").assertDoesNotExist()
        compose.onNodeWithText("Reintentar").performClick()

        compose.runOnIdle {
            state = state.copy(loadState = SummaryLoadState.LOADING, surface = SummarySurface.PERSONALIZATION)
        }
        compose.onNodeWithTag("summary-personalization-loading").assertIsDisplayed()
        compose.onNodeWithTag("summary-toggle-nights").assertDoesNotExist()
        compose.runOnIdle {
            state = state.copy(
                loadState = SummaryLoadState.ERROR,
                errorMessage = "No pudimos cargar este resumen. Reintentá.",
            )
        }
        compose.onNodeWithTag("summary-personalization-error").assertIsDisplayed()
        compose.onNodeWithTag("summary-personalization-source-warning").assertDoesNotExist()
        compose.onNodeWithTag("summary-toggle-nights").assertDoesNotExist()
        compose.runOnIdle {
            state = state.copy(
                loadState = SummaryLoadState.CONTENT,
                projection = projection(),
                errorMessage = "No pudimos actualizar este resumen. Reintentá.",
            )
        }
        compose.onNodeWithTag("summary-personalization-source-warning").assertIsDisplayed()
        compose.onNodeWithTag("summary-toggle-nights").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun preferenceWriteFailureIsRecoverableWithoutClosingTheSurface() {
        var retries = 0
        val state = SummaryUiState(
            visibleMonth = MONTH,
            loadState = SummaryLoadState.CONTENT,
            projection = projection(),
            surface = SummarySurface.PERSONALIZATION,
            preferenceErrorMessage = "No pudimos guardar este cambio.",
        )
        setSummary(
            { state },
            SummaryActions(retryPreferenceWrite = { retries++ }),
        )

        compose.onNodeWithTag("summary-preference-write-error").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        compose.onNodeWithTag("summary-personalization").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun eachHistoricallySimilarLabelOpensItsOwnMetricDetail() {
        val projection = projection()
        var state by mutableStateOf(SummaryUiState(MONTH, SummaryLoadState.CONTENT, projection))
        val actions = SummaryActions(
            openMetric = { id -> state = state.copy(surface = SummarySurface.DETAIL, selectedMetricId = id) },
            back = { state = state.copy(surface = SummarySurface.OVERVIEW, selectedMetricId = null) },
        )
        setSummary({ state }, actions)

        compose.onNodeWithTag("summary-metric-optional-places-50756573746f2041")
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Puesto A").assertIsDisplayed()
        compose.onNodeWithTag("summary-detail-back").performClick()
        compose.onNodeWithTag("summary-metric-optional-places-50756573746f2d41")
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Puesto-A").assertIsDisplayed()
    }

    @Test
    fun returningFromDetailRestoresTheOverviewScrollPosition() {
        val projection = projection()
        var state by mutableStateOf(SummaryUiState(MONTH, SummaryLoadState.CONTENT, projection))
        val actions = SummaryActions(
            updateOverviewScrollPosition = { position ->
                state = state.copy(overviewScrollPosition = position)
            },
            openMetric = { id -> state = state.copy(surface = SummarySurface.DETAIL, selectedMetricId = id) },
            back = { state = state.copy(surface = SummarySurface.OVERVIEW, selectedMetricId = null) },
        )
        setSummary({ state }, actions, Modifier.height(360.dp))

        val target = compose.onNodeWithTag("summary-metric-optional-places-50756573746f2d41")
        target.performScrollTo()
        var expectedScrollPosition = 0
        compose.runOnIdle {
            expectedScrollPosition = state.overviewScrollPosition
            assertTrue(expectedScrollPosition > 0)
        }
        target.performClick()
        compose.onNodeWithTag("summary-detail-back").performClick()
        target.assertIsDisplayed()
        compose.runOnIdle { assertEquals(expectedScrollPosition, state.overviewScrollPosition) }
    }

    @Test
    fun surfaceRendersAtEveryInternalZoomInLightAndDarkThemes() {
        var zoom by mutableStateOf(AppZoom.STANDARD)
        var dark by mutableStateOf(false)
        compose.setContent {
            MiGuardiaTheme(darkTheme = dark, appZoom = zoom) {
                SummaryScreen(
                    state = SummaryUiState(MONTH, SummaryLoadState.CONTENT, projection()),
                    actions = SummaryActions(),
                    contentPadding = PaddingValues(0.dp),
                )
            }
        }
        AppZoom.entries.forEach { selected ->
            compose.runOnIdle {
                zoom = selected
                dark = !dark
            }
            compose.onNodeWithTag("summary-overview").assertIsDisplayed()
        }
    }

    private fun setSummary(
        state: () -> SummaryUiState,
        actions: SummaryActions = SummaryActions(),
        modifier: Modifier = Modifier,
    ) {
        compose.setContent {
            MiGuardiaTheme {
                SummaryScreen(
                    state = state(),
                    actions = actions,
                    contentPadding = PaddingValues(0.dp),
                    modifier = modifier,
                )
            }
        }
    }

    private fun projection(): MonthlySummaryProjection {
        val totalRows = listOf(
            SummaryContribution(
                id = "row-1",
                sourceId = "shift-1",
                ownerLocalDate = LocalDate.of(2026, 8, 20),
                start = Instant.parse("2026-08-20T08:00:00Z"),
                end = Instant.parse("2026-08-20T10:00:00Z"),
                zoneId = ZoneOffset.UTC,
                value = 120L,
                unit = SummaryValueUnit.MINUTES,
                kind = SummaryContributionKind.REGULAR_WORK,
                sourceLabel = "Jornada con horario real",
                workPlaceLabel = "Lugar ficticio (LF)",
                workTypeLabel = "Tipo ficticio",
                explanation = "Pertenece al mes del inicio real.",
            ),
        )
        val total = SummaryMetric("essential:total", "Total trabajado", 120L, SummaryValueUnit.MINUTES, totalRows)
        val holiday = SummaryMetric(
            "optional:holidays",
            "Feriados",
            60L,
            SummaryValueUnit.MINUTES,
            listOf(
                totalRows.single().copy(
                    id = "holiday-row",
                    end = Instant.parse("2026-08-20T09:00:00Z"),
                    value = 60L,
                    kind = SummaryContributionKind.HOLIDAY,
                ),
            ),
        )
        val night = SummaryMetric(
            "optional:nights",
            "Noches",
            60L,
            SummaryValueUnit.MINUTES,
            listOf(
                totalRows.single().copy(
                    id = "night-row",
                    end = Instant.parse("2026-08-20T09:00:00Z"),
                    value = 60L,
                    kind = SummaryContributionKind.NIGHT,
                ),
            ),
        )
        val spacedPlace = SummaryMetric(
            "optional:places:50756573746f2041",
            "Puesto A",
            30L,
            SummaryValueUnit.MINUTES,
            listOf(
                totalRows.single().copy(
                    id = "place-spaced-row",
                    start = Instant.parse("2026-08-20T10:00:00Z"),
                    end = Instant.parse("2026-08-20T10:30:00Z"),
                    value = 30L,
                ),
            ),
        )
        val dashedPlace = SummaryMetric(
            "optional:places:50756573746f2d41",
            "Puesto-A",
            30L,
            SummaryValueUnit.MINUTES,
            listOf(
                totalRows.single().copy(
                    id = "place-dashed-row",
                    start = Instant.parse("2026-08-20T11:00:00Z"),
                    end = Instant.parse("2026-08-20T11:30:00Z"),
                    value = 30L,
                ),
            ),
        )
        return MonthlySummaryProjection(
            month = MONTH,
            essentials = MonthlySummaryEssentials(total, total.copy(id = "essential:regular", label = "Trabajo habitual"), null, null),
            compliance = emptyList(),
            availability = null,
            optionalSections = listOf(
                SummaryOptionalSection(SummaryOptionalFamily.NIGHTS, listOf(night)),
                SummaryOptionalSection(SummaryOptionalFamily.HOLIDAYS, listOf(holiday)),
                SummaryOptionalSection(
                    SummaryOptionalFamily.WORK_PLACES,
                    listOf(spacedPlace, dashedPlace),
                ),
            ),
            hasContent = true,
        )
    }

    private companion object {
        val MONTH: YearMonth = YearMonth.of(2026, 8)
    }
}
