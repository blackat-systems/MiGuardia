package com.blackatsystems.miguardia.stage135

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.domain.model.AvailabilityBreakdown
import com.blackatsystems.miguardia.core.domain.model.AvailabilityTemporalState
import com.blackatsystems.miguardia.core.domain.model.AvailabilityWindowRecord
import com.blackatsystems.miguardia.core.domain.model.sumAvailabilityBreakdowns
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveDateTimeline
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.HoursReference
import com.blackatsystems.miguardia.core.domain.work.PerPeriodHoursValues
import com.blackatsystems.miguardia.core.domain.work.WorkConfiguration
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationHistory
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import com.blackatsystems.miguardia.testsupport.AndroidFontScaleTheme
import com.blackatsystems.miguardia.testsupport.TestFontScale
import com.blackatsystems.miguardia.ui.availability.AvailabilityActions
import com.blackatsystems.miguardia.ui.availability.AvailabilityConfigurationDraft
import com.blackatsystems.miguardia.ui.availability.AvailabilityLoadState
import com.blackatsystems.miguardia.ui.availability.AvailabilitySource
import com.blackatsystems.miguardia.ui.availability.AvailabilitySurface
import com.blackatsystems.miguardia.ui.availability.AvailabilitySurfaceHost
import com.blackatsystems.miguardia.ui.availability.AvailabilityUiState
import com.blackatsystems.miguardia.ui.availability.AvailabilityWindowDraftState
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Rule
import org.junit.Test

/**
 * Stage 13.5 before/after captures of Disponibilidad with synthetic data only (no Room,
 * no QA data). PNGs go to the external cache of the app
 * under test: Android/data/<qa>/cache/stage13-5-captures/disponibilidad/, which
 * each run empties first; pull it right after running. The window review needs a domain-built expectation; it is captured in the app flow instead.
 */
class Stage135AvailabilityCaptureTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun captureAvailabilitySurfaces() {
        // External cache of the app under test: outside its private data (databases, files,
        // preferences), so captures never mix with QA data. Pulled with adb after each run.
        val folder = File(
            requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir),
            "stage13-5-captures/disponibilidad",
        ).apply { deleteRecursively(); mkdirs() }
        var dark by mutableStateOf(true)
        var scale by mutableStateOf(TestFontScale.STANDARD)
        var state by mutableStateOf(surfaceState(AvailabilitySurface.OVERVIEW))
        compose.setContent {
            key(dark, scale) {
                AndroidFontScaleTheme(darkTheme = dark, fontScale = scale) {
                    Box(Modifier.requiredSize(width = 360.dp, height = 800.dp)) {
                        AvailabilitySurfaceHost(state, AvailabilityActions())
                    }
                }
            }
        }
        val surfaces = listOf(
            AvailabilitySurface.OVERVIEW,
            AvailabilitySurface.CONFIG_EDITOR,
            AvailabilitySurface.CONFIG_REVIEW,
            AvailabilitySurface.WINDOW_EDITOR,
            AvailabilitySurface.DELETE_CONFIRMATION,
        )
        for (theme in listOf(false, true)) for (fontScale in listOf(TestFontScale.STANDARD, TestFontScale.EXTRA_LARGE)) {
            compose.runOnIdle { dark = theme; scale = fontScale }
            val config = "${if (theme) "oscuro" else "claro"}-letra${fontScale.percent}"
            surfaces.forEach { surface ->
                compose.runOnIdle { state = surfaceState(surface) }
                compose.waitForIdle()
                val node = if (surface == AvailabilitySurface.DELETE_CONFIRMATION) {
                    compose.onNode(isDialog())
                } else {
                    compose.onRoot()
                }
                save(node, File(folder, "${surface.name.lowercase()}-$config.png"))
            }
            // Pickers exist only in the polished build; the base build simply has no button.
            compose.runOnIdle { state = surfaceState(AvailabilitySurface.WINDOW_EDITOR) }
            listOf("availability-start-picker" to "selector-hora", "availability-end-date-picker" to "selector-fecha")
                .forEach { (tag, name) ->
                    if (compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) {
                        compose.onNodeWithTag(tag).performScrollTo().performClick()
                        compose.waitForIdle()
                        save(compose.onNode(isDialog()), File(folder, "$name-$config.png"))
                        compose.onNodeWithTag("native-picker-cancel").performClick()
                        compose.waitForIdle()
                    }
                }
        }
    }

    private fun save(node: SemanticsNodeInteraction, file: File) {
        val bitmap = node.captureToImage().asAndroidBitmap()
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    private fun surfaceState(surface: AvailabilitySurface): AvailabilityUiState {
        val record = record()
        val base = AvailabilityUiState(loadState = AvailabilityLoadState.CONTENT, source = source(record))
        return when (surface) {
            AvailabilitySurface.CONFIG_EDITOR, AvailabilitySurface.CONFIG_REVIEW -> base.copy(
                surface = surface,
                configurationDraft = AvailabilityConfigurationDraft(AvailabilityLabel.PASSIVE_GUARD, "2026-08-01"),
            )
            AvailabilitySurface.WINDOW_EDITOR -> base.copy(
                surface = surface,
                windowDraft = AvailabilityWindowDraftState(
                    ownerDate = OWNER,
                    startTime = "22:00",
                    endDate = OWNER.plusDays(1).toString(),
                    endTime = "06:00",
                ),
            )
            AvailabilitySurface.DELETE_CONFIRMATION -> base.copy(surface = surface, deletingRecord = record)
            else -> base.copy(surface = surface)
        }
    }

    private fun source(record: AvailabilityWindowRecord): AvailabilitySource {
        val value = WorkConfiguration(WorkSector.PRIVATE_SECURITY, HoursReference.PendingSetup, AvailabilityLabel.PASSIVE_GUARD)
        val history = WorkConfigurationHistory(
            EffectiveDateTimeline(TIMELINE, listOf(EffectiveRevision(REVISION, LocalDate.of(2026, 1, 1), value))),
            PerPeriodHoursValues(emptyList()),
        )
        val breakdowns = mapOf(record.id to BREAKDOWN)
        return AvailabilitySource(
            history = history,
            windows = listOf(record),
            breakdowns = breakdowns,
            totals = sumAvailabilityBreakdowns(breakdowns.values),
            protectedWindowIds = emptySet(),
            activeWork = emptyList(),
            protectedRanges = emptyList(),
            today = OWNER,
        )
    }

    private fun record() = AvailabilityWindowRecord(
        id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
        timelineId = TIMELINE,
        sector = WorkSector.PRIVATE_SECURITY,
        configurationRevisionId = REVISION,
        ownerLocalDate = OWNER,
        zoneId = ZoneOffset.UTC,
        start = Instant.parse("2026-08-27T22:00:00Z"),
        end = Instant.parse("2026-08-28T06:00:00Z"),
        labelSnapshot = "Guardia pasiva",
        createdAt = Instant.parse("2026-08-27T20:00:00Z"),
        updatedAt = Instant.parse("2026-08-27T20:00:00Z"),
    )

    private companion object {
        val OWNER: LocalDate = LocalDate.of(2026, 8, 27)
        val TIMELINE: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val REVISION: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val BREAKDOWN = AvailabilityBreakdown(AvailabilityTemporalState.COMPLETED, 480, 480, 0, 0, 480, 0)
    }
}
