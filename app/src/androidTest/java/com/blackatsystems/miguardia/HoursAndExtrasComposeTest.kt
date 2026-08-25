package com.blackatsystems.miguardia

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkRecord
import com.blackatsystems.miguardia.core.domain.model.IndependentExtraWorkSnapshot
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursPeriod
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursEntry
import com.blackatsystems.miguardia.core.domain.work.PositiveMinutes
import com.blackatsystems.miguardia.core.domain.work.WorkCatalog
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.core.domain.work.WorkTypeBehavior
import com.blackatsystems.miguardia.core.domain.work.calculateHoursProgress
import com.blackatsystems.miguardia.core.domain.work.resolveHoursReferenceSegment
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasActions
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasLoadState
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasSource
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasSurface
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasSurfaceHost
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasUiState
import com.blackatsystems.miguardia.ui.hours.HoursReferenceDraft
import com.blackatsystems.miguardia.ui.hours.HoursReferenceReview
import com.blackatsystems.miguardia.ui.hours.HoursPeriodChoice
import com.blackatsystems.miguardia.ui.hours.HoursReferenceChoice
import com.blackatsystems.miguardia.ui.hours.EditorStage
import com.blackatsystems.miguardia.ui.hours.IndependentExtraDetailCard
import com.blackatsystems.miguardia.ui.hours.PerPeriodValueDraft
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HoursAndExtrasComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun progressDistinguishesLoadingErrorAndKnownContentWithoutFalseZero() {
        var retries = 0
        var state by mutableStateOf(
            HoursAndExtrasUiState(surface = HoursAndExtrasSurface.PROGRESS),
        )
        compose.setContent {
            MiGuardiaTheme {
                HoursAndExtrasSurfaceHost(
                    state,
                    HoursAndExtrasActions(retry = { retries++ }),
                )
            }
        }

        compose.onNodeWithText("Calculando tu avance…").assertIsDisplayed()
        compose.runOnIdle {
            state = state.copy(
                loadState = HoursAndExtrasLoadState.ERROR,
                message = "Fallo ficticio de lectura",
            )
        }
        compose.onNodeWithTag("hours-progress-error").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            state = contentState()
        }
        compose.onNodeWithTag("hours-progress-content").assertIsDisplayed()
        compose.onNodeWithText("Meta: 100 h").assertIsDisplayed()
        compose.onNodeWithText("Avance: 0,0 %").assertIsDisplayed()
        compose.onNodeWithText("Faltan").assertIsDisplayed()
    }

    @Test
    fun referenceEditorExposesEveryExplicitChoiceAndNoSectorDefault() {
        var state by mutableStateOf(
            contentState().copy(
                surface = HoursAndExtrasSurface.REFERENCE_EDITOR,
                referenceDraft = HoursReferenceDraft(),
            ),
        )
        compose.setContent {
            MiGuardiaTheme {
                HoursAndExtrasSurfaceHost(
                    state,
                    HoursAndExtrasActions(
                        updateReferenceDraft = { transform ->
                            state = state.copy(referenceDraft = state.referenceDraft?.let(transform))
                        },
                    ),
                )
            }
        }

        listOf(
            "Todavía no la configuré",
            "No uso una referencia de horas",
            "Tengo una referencia, pero no sé cuántas horas",
            "La cantidad es fija",
            "La cantidad cambia en cada período",
        ).forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithTag("hours-reference-fixed").performClick()
        compose.onNodeWithText("Mes calendario").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mes calendario").performClick()
        compose.onNodeWithTag("hours-reference-next-period")
            .performScrollTo()
            .assertTextContains("1 de septiembre de 2026", substring = true)
        compose.onNodeWithText("MiGuardia no propone un valor por sector.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun exactReferenceReviewShowsPeriodAmountAndConfirmationsAfterReview() {
        val startedOn = LocalDate.of(2026, 8, 10)
        val reference = HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(6_000))
        val state = contentState().copy(
            surface = HoursAndExtrasSurface.REFERENCE_EDITOR,
            referenceDraft = HoursReferenceDraft(
                choice = HoursReferenceChoice.FIXED,
                periodChoice = HoursPeriodChoice.MONTHLY,
                requiredMinutes = "6000",
                stage = EditorStage.REVIEW,
                confirmPastRecalculation = false,
                confirmShortFirstSegment = false,
            ),
            referenceReview = HoursReferenceReview(
                reference = reference,
                startedOn = startedOn,
                previousSegmentEndInclusive = startedOn.minusDays(1),
                naturalWindowStart = startedOn.withDayOfMonth(1),
                naturalWindowEndExclusive = startedOn.withDayOfMonth(1).plusMonths(1),
                recalculationEndExclusive = LocalDate.MAX,
                isPast = true,
                isShortFirstSegment = true,
                initialValue = null,
            ),
        )
        compose.setContent {
            MiGuardiaTheme {
                HoursAndExtrasSurfaceHost(state, HoursAndExtrasActions())
            }
        }

        compose.onNodeWithText("Período: Mes calendario").assertIsDisplayed()
        compose.onNodeWithText("Cantidad: 100 h").assertIsDisplayed()
        compose.onNodeWithText("Confirmo recalcular los tramos", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Confirmo usar la meta completa", substring = true).assertIsDisplayed()
    }

    @Test
    fun independentExtraCardIsTextualAndKeepsCorrectionAndDeletionSeparate() {
        var corrected = 0
        var deleted = 0
        compose.setContent {
            MiGuardiaTheme {
                IndependentExtraDetailCard(
                    record = EXTRA,
                    onCorrect = { corrected++ },
                    onDelete = { deleted++ },
                )
            }
        }

        compose.onNodeWithText("Trabajo extra independiente").assertIsDisplayed()
        compose.onNodeWithText("Clase: Refuerzo especial").assertIsDisplayed()
        compose.onNodeWithText("No ayuda a la referencia").assertIsDisplayed()
        compose.onNodeWithText("Corregir trabajo extra").performClick()
        compose.onNodeWithText("Eliminar trabajo extra").performClick()
        compose.runOnIdle {
            assertEquals(1, corrected)
            assertEquals(1, deleted)
        }
    }

    @Test
    fun perPeriodMissingValueHasAnExplicitEditorAndCorrectionHasAConfirmation() {
        var state by mutableStateOf(contentState(PER_PERIOD_HISTORY))
        compose.setContent {
            MiGuardiaTheme {
                HoursAndExtrasSurfaceHost(
                    state,
                    HoursAndExtrasActions(
                        openPerPeriodValueEditor = {
                            state = state.copy(
                                surface = HoursAndExtrasSurface.PERIOD_VALUE_EDITOR,
                                periodValueDraft = PerPeriodValueDraft(valueId = PERIOD_VALUE_ID),
                            )
                        },
                    ),
                )
            }
        }

        compose.onNodeWithText("Meta: Falta informar").assertIsDisplayed()
        compose.onNodeWithText("Informar meta de este período").performScrollTo().performClick()
        compose.onNodeWithTag("hours-period-value-minutes").assertIsDisplayed()
        compose.onNodeWithText("Debe ser un entero positivo; no se prorratea.", substring = true)
            .assertIsDisplayed()

        compose.runOnIdle {
            state = contentState(PER_PERIOD_DEFINED_HISTORY).copy(
                surface = HoursAndExtrasSurface.PERIOD_VALUE_EDITOR,
                periodValueDraft = PerPeriodValueDraft(
                    requiredMinutes = "8400",
                    valueId = PERIOD_VALUE_ID,
                ),
            )
        }
        compose.onNodeWithText("Valor actual: 140 h").assertIsDisplayed()
        compose.onNodeWithText(
            "Confirmo corregir sólo este período sin cambiar su identidad.",
        ).assertIsDisplayed()
    }

    @Test
    fun progressPrimaryActionStaysReachableInBothThemesAndEveryInternalZoom() {
        var dark by mutableStateOf(false)
        var zoom by mutableStateOf(AppZoom.STANDARD)
        compose.setContent {
            MiGuardiaTheme(darkTheme = dark, appZoom = zoom) {
                HoursAndExtrasSurfaceHost(contentState(), HoursAndExtrasActions())
            }
        }

        listOf(false, true).forEach { darkTheme ->
            AppZoom.entries.forEach { appZoom ->
                compose.runOnIdle {
                    dark = darkTheme
                    zoom = appZoom
                }
                compose.onNodeWithTag("hours-reference-configure")
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        }
    }

    private fun contentState(
        history: WorkConfigurationHistory = HISTORY,
    ): HoursAndExtrasUiState {
        val segment = requireNotNull(resolveHoursReferenceSegment(history, DATE))
        return HoursAndExtrasUiState(
            loadState = HoursAndExtrasLoadState.CONTENT,
            source = HoursAndExtrasSource(
                history = history,
                catalog = WorkCatalog(
                    history.timeline.id,
                    WorkSector.NURSING,
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                ),
                objectives = emptyList(),
                extraClasses = emptyList(),
                independentExtras = emptyList(),
                segment = segment,
                progress = calculateHoursProgress(
                    segment,
                    emptyList(),
                    emptyList(),
                    CLOCK,
                    ZoneOffset.UTC,
                ),
                today = DATE,
            ),
            surface = HoursAndExtrasSurface.PROGRESS,
        )
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 25)
        val NOW: Instant = Instant.parse("2026-08-25T12:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val TIMELINE_ID: UUID = uuid(1)
        val REVISION_ID: UUID = uuid(2)
        val EXTRA_ID: UUID = uuid(3)
        val PERIOD_DEFINITION_ID: UUID = uuid(8)
        val PERIOD_VALUE_ID: UUID = uuid(9)
        val HISTORY = WorkConfigurationHistory(
            EffectiveDateTimeline(
                TIMELINE_ID,
                listOf(
                    EffectiveRevision(
                        REVISION_ID,
                        LocalDate.of(2026, 8, 1),
                        WorkConfiguration(
                            WorkSector.NURSING,
                            HoursReference.Fixed(HoursPeriod.Monthly, PositiveMinutes(6_000)),
                            null,
                            LocalDate.of(2026, 8, 1),
                        ),
                    ),
                ),
            ),
            PerPeriodHoursValues(emptyList()),
        )
        val PER_PERIOD_REFERENCE = HoursReference.PerPeriod(
            PERIOD_DEFINITION_ID,
            HoursPeriod.Monthly,
        )
        val PER_PERIOD_REVISION = EffectiveRevision(
            REVISION_ID,
            LocalDate.of(2026, 8, 1),
            WorkConfiguration(
                WorkSector.NURSING,
                PER_PERIOD_REFERENCE,
                null,
                LocalDate.of(2026, 8, 1),
            ),
        )
        val PER_PERIOD_HISTORY = WorkConfigurationHistory(
            EffectiveDateTimeline(TIMELINE_ID, listOf(PER_PERIOD_REVISION)),
            PerPeriodHoursValues(emptyList()),
        )
        val PERIOD_ENTRY = PerPeriodHoursEntry(
            PERIOD_VALUE_ID,
            PER_PERIOD_REFERENCE.keyContaining(DATE),
            PositiveMinutes(8_400),
        )
        val PER_PERIOD_DEFINED_HISTORY = WorkConfigurationHistory(
            EffectiveDateTimeline(TIMELINE_ID, listOf(PER_PERIOD_REVISION)),
            PerPeriodHoursValues(listOf(PERIOD_ENTRY)),
        )
        val EXTRA = IndependentExtraWorkRecord(
            id = EXTRA_ID,
            timelineId = TIMELINE_ID,
            sector = WorkSector.NURSING,
            configurationRevisionId = REVISION_ID,
            workPlaceId = uuid(4),
            objectiveId = uuid(5),
            workTypeId = uuid(6),
            templateId = null,
            extraWorkClassId = uuid(7),
            ownerLocalDate = DATE,
            zoneId = ZoneOffset.UTC,
            start = NOW.minusSeconds(7_200),
            end = NOW.minusSeconds(3_600),
            snapshot = IndependentExtraWorkSnapshot(
                workPlaceName = "Hospital ficticio",
                workPlaceAbbreviation = "HFI",
                workPlaceAddress = null,
                workTypeName = "Refuerzo",
                workTypeBehavior = WorkTypeBehavior.ACTIVE_WORK,
                colorArgb = 0xFF336699.toInt(),
                position = "Puesto 1",
                className = "Refuerzo especial",
                helpsMeetHoursReference = false,
                showDedicatedSummary = true,
            ),
            createdAt = NOW.minusSeconds(86_400),
            updatedAt = NOW.minusSeconds(86_400),
        )

        fun uuid(value: Int): UUID = UUID.fromString(
            "94000000-0000-0000-0000-${value.toString().padStart(12, '0')}",
        )
    }
}
