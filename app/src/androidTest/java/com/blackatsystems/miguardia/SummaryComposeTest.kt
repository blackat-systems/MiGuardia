package com.blackatsystems.miguardia

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.stage12.Stage12SummaryObservation
import com.blackatsystems.miguardia.stage12.Stage12SummaryObservationRule
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import android.os.Looper
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class SummaryComposeTest {
    val compose = createComposeRule()
    private val summaryObservation = Stage12SummaryObservationRule()
    @get:Rule
    val observedComposeRule: RuleChain = RuleChain.outerRule(summaryObservation).around(compose)

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

        compose.onNodeWithTag("summary-metric-essential-total").assertIsDisplayed()
        compose.onNodeWithTag("summary-intro").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("summary-intro-understood").performScrollTo().performClick()
        compose.onNodeWithTag("summary-intro").assertDoesNotExist()
        compose.onNodeWithTag("summary-menu").performScrollTo().performClick()
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
    fun monthControlsShareOneRowAndNumbersPrecedeIntroductionAndReport() {
        var previous = 0
        var current = 0
        var next = 0
        val state = SummaryUiState(MONTH, SummaryLoadState.CONTENT, projection(), introVisible = true)
        setSummary(
            { state },
            SummaryActions(previousMonth = { previous++ }, currentMonth = { current++ }, nextMonth = { next++ }),
            Modifier.height(480.dp),
        )
        val previousBounds = compose.onNodeWithTag("summary-previous-month").fetchSemanticsNode().boundsInRoot
        val nextBounds = compose.onNodeWithTag("summary-next-month").fetchSemanticsNode().boundsInRoot
        assertEquals(previousBounds.top, nextBounds.top, 1f)
        compose.onNodeWithTag("summary-metric-essential-total").assertIsDisplayed()
        compose.onNodeWithTag("summary-previous-month").performClick()
        compose.onNodeWithTag("summary-current-month").performClick()
        compose.onNodeWithTag("summary-next-month").performClick()
        compose.runOnIdle {
            assertEquals(1, previous)
            assertEquals(1, current)
            assertEquals(1, next)
        }
        compose.onNodeWithTag("summary-intro").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("summary-generate-report").performScrollTo().assertIsDisplayed()
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
    fun metricDetailUsesTheExactRowsAndNeverDisplaysPrivateFields() = summaryObservation.observeBody(
        { checkNotNull(compose as? AndroidComposeTestRule<*, *>).activity },
    ) { observation ->
        val projection = projection()
        var state by mutableStateOf(SummaryUiState(MONTH, SummaryLoadState.CONTENT, projection))
        val actions = SummaryActions(
            openMetric = { id ->
                observation?.callback(id == "essential:total", Looper.myLooper() == Looper.getMainLooper())
                state = state.copy(surface = SummarySurface.DETAIL, selectedMetricId = id)
            },
            back = { state = state.copy(surface = SummarySurface.OVERVIEW, selectedMetricId = null) },
        )
        observation?.contentRequested()
        setSummary({ state }, actions, observation = observation)
        summaryObservation.checkpoint(Stage12SummaryObservation.Phase.AFTER_SET_CONTENT)
        summaryObservation.overview {
            compose.runWithoutImplicitWait {
                compose.onAllNodesWithTag("summary-overview").fetchSemanticsNodes(atLeastOneRootRequired = false).size
            }
        }
        summaryObservation.checkpoint(Stage12SummaryObservation.Phase.BEFORE_CLICK, action = true)
        observation?.enteringClick()

        compose.onNodeWithTag("summary-metric-essential-total").performClick()
        observation?.returnedClick()
        summaryObservation.checkpoint(Stage12SummaryObservation.Phase.AFTER_CLICK, action = true)
        compose.onNodeWithTag("summary-detail").assertIsDisplayed()
        observation?.detailAsserted()
        compose.onNodeWithText("Suma exacta: 2 h").assertIsDisplayed()
        compose.onNodeWithText("Fecha a la que se cuenta:", substring = true).assertExists()
        compose.onNodeWithText("Fecha dueña:", substring = true).assertDoesNotExist()
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
    fun surfaceRendersAtEveryAndroidFontScaleInLightAndDarkThemes() {
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        var dark by mutableStateOf(false)
        compose.setContent {
            AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
                SummaryScreen(
                    state = SummaryUiState(MONTH, SummaryLoadState.CONTENT, projection()),
                    actions = SummaryActions(),
                    contentPadding = PaddingValues(0.dp),
                )
            }
        }
        for (theme in listOf(false, true)) for (selected in TestFontScale.entries) {
            compose.runOnIdle {
                fontScale = selected
                dark = theme
            }
            compose.onNodeWithTag("summary-overview").assertIsDisplayed()
        }
    }

    @Test
    fun summaryAndContributionKeepLongLabelsAndAmountsWholeAtEveryThemeAndFontScale() {
        var fontScale by mutableStateOf(TestFontScale.STANDARD)
        var dark by mutableStateOf(false)
        var viewport by mutableStateOf(320 to 480)
        var density = 1f
        val sourceProjection = projection()
        val sourceTotal = requireNotNull(sourceProjection.essentials.totalWorked)
        val label = "Total trabajado en el mes y sus jornadas"
        val sourceLabel = "Jornada de institución ficticia con nombre largo"
        fun initial(amount: Long): SummaryUiState {
            val contribution = sourceTotal.contributions.single().copy(
                sourceLabel = sourceLabel,
                value = amount,
                end = sourceTotal.contributions.single().start!!.plusSeconds(amount * 60),
            )
            val total = sourceTotal.copy(label = label, value = amount, contributions = listOf(contribution))
            return SummaryUiState(MONTH, SummaryLoadState.CONTENT, sourceProjection.copy(
                essentials = sourceProjection.essentials.copy(totalWorked = total),
            ))
        }
        var state by mutableStateOf(initial(1_965L))
        val openedMetrics = mutableListOf<String>()
        compose.setContent {
            AndroidFontScaleTheme(fontScale = TestFontScale.STANDARD) {
                Box(Modifier.width(viewport.first.dp).height(viewport.second.dp)) {
                    AndroidFontScaleTheme(darkTheme = dark, fontScale = fontScale) {
                        density = LocalDensity.current.density
                        SummaryScreen(
                            state,
                            SummaryActions(openMetric = { id ->
                                openedMetrics += id
                                state = state.copy(surface = SummarySurface.DETAIL, selectedMetricId = id)
                            }),
                            PaddingValues(0.dp),
                        )
                    }
                }
            }
        }
        var expectedOpens = 0
        for (window in listOf(320 to 480, 360 to 640)) {
            for (theme in listOf(false, true)) for (scale in TestFontScale.entries) {
                for ((amount, expected) in listOf(1_965L to "32 h 45 min", 740_745L to "12345 h 45 min")) {
                    compose.runOnIdle { viewport = window; dark = theme; fontScale = scale; state = initial(amount) }
                    val context = "window=$window theme=$theme fontScale=$scale value=$expected"
                    assertCompleteAmount("summary-metric-essential-total", label, expected, density, context)
                    compose.onNodeWithContentDescription("$label: $expected. Qué incluye este valor").assertExists()
                    compose.onNodeWithTag("summary-metric-essential-total").performClick()
                    expectedOpens++
                    compose.runOnIdle {
                        assertEquals(context, expectedOpens, openedMetrics.size)
                        assertEquals(context, "essential:total", openedMetrics.last())
                    }
                    compose.onNodeWithTag("summary-detail").assertIsDisplayed()
                    assertCompleteAmount("summary-contribution-row-1-amount", sourceLabel, expected, density, context)
                }
            }
        }
    }

    private fun assertCompleteAmount(tag: String, expectedLabel: String, expectedValue: String, density: Float, context: String) {
        val row = compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        val label = compose.onNodeWithTag("$tag-label", useUnmergedTree = true)
        val value = compose.onNodeWithTag("$tag-value", useUnmergedTree = true)
        for ((node, expected) in listOf(label to expectedLabel, value to expectedValue)) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(context, 1, layouts.size)
            val layout = layouts.single()
            val renderedText = layout.layoutInput.text.text
            assertEquals(context, expected, renderedText.replace('\u00a0', ' '))
            assertFalse("$context tag=$tag text=$renderedText size=${layout.size} " +
                "constraints=${layout.layoutInput.constraints} widthOverflow=${layout.didOverflowWidth} " +
                "heightOverflow=${layout.didOverflowHeight} paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height} " +
                "intrinsicWidth=${layout.multiParagraph.intrinsics.maxIntrinsicWidth} " +
                "line0=${layout.getLineLeft(0)}..${layout.getLineRight(0)} " +
                "bounds=${node.fetchSemanticsNode().boundsInRoot}", layout.hasVisualOverflow)
            assertEquals(context, expected.length, layout.getLineEnd(layout.lineCount - 1))
            assertEquals("$context font family", FontFamily.SansSerif, layout.layoutInput.style.fontFamily)
            assertEquals("$context tabular numbers", "tnum", layout.layoutInput.style.fontFeatureSettings)
            if (expected == expectedValue) {
                assertEquals("$context amount font size", 15.sp, layout.layoutInput.style.fontSize)
                assertEquals("$context amount weight", FontWeight.SemiBold, layout.layoutInput.style.fontWeight)
                if (layout.multiParagraph.intrinsics.maxIntrinsicWidth <= layout.size.width + 1) {
                    assertEquals("$context amount must use one line when it fits", 1, layout.lineCount)
                }
                for (quantity in Regex("[−-]?\\d+ (?:h|min)").findAll(expectedValue)) {
                    assertEquals("$context split quantity ${quantity.value}",
                        layout.getLineForOffset(quantity.range.first), layout.getLineForOffset(quantity.range.last))
                }
                if (layout.lineCount > 1) {
                    compose.onNodeWithTag("$tag-navigation", useUnmergedTree = true).assertDoesNotExist()
                }
            }
            for (line in 0 until layout.lineCount) {
                assertFalse(context, layout.isLineEllipsized(line))
                val end = layout.getLineEnd(line, visibleEnd = true)
                if (end > 0 && end < expected.length) {
                    assertTrue("$context word split at $end", expected[end].isWhitespace() || expected[end - 1].isWhitespace())
                }
            }
            val rendered = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$context clipped text width", rendered.width + 1 >= layout.size.width)
            assertTrue("$context clipped text height", rendered.height + 1 >= layout.size.height)
        }
        val labelBounds = label.fetchSemanticsNode().boundsInRoot
        val valueBounds = value.fetchSemanticsNode().boundsInRoot
        val rowBounds = row.fetchSemanticsNode().boundsInRoot
        assertTrue(context, rowBounds.containsRendered(labelBounds) && rowBounds.containsRendered(valueBounds))
        val navigation = compose.onAllNodesWithTag("$tag-navigation", useUnmergedTree = true).fetchSemanticsNodes()
        if (navigation.isNotEmpty()) {
            assertEquals(context, 1, navigation.size)
            val navigationBounds = navigation.single().boundsInRoot
            assertTrue("$context clipped navigation", rowBounds.containsRendered(navigationBounds))
            assertTrue("$context navigation competes with amount", navigationBounds.left - valueBounds.right >= 12 * density - 1)
        }
        assertTrue("$context missing 12 dp separation",
            valueBounds.left - labelBounds.right >= 12 * density - 1 ||
                valueBounds.top - labelBounds.bottom >= 12 * density - 1)
    }

    private fun Rect.containsRendered(other: Rect): Boolean =
        other.left >= left - 1 && other.top >= top - 1 &&
            other.right <= right + 1 && other.bottom <= bottom + 1

    private fun setSummary(
        state: () -> SummaryUiState,
        actions: SummaryActions = SummaryActions(),
        modifier: Modifier = Modifier,
        observation: Stage12SummaryObservation? = null,
    ) {
        compose.setContent {
            if (observation != null) DisposableEffect(observation) {
                observation.contentEntered(Looper.myLooper() == Looper.getMainLooper())
                onDispose { observation.contentDisposed(Looper.myLooper() == Looper.getMainLooper()) }
            }
            AndroidFontScaleTheme {
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
