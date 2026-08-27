package com.blackatsystems.miguardia

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.work.AvailabilityLabel
import com.blackatsystems.miguardia.core.domain.work.EffectiveRevision
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationAvailabilityMutation
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationAvailabilityWriteResult
import com.blackatsystems.miguardia.core.domain.work.WorkSector
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Recorrido sintético; sólo se ejecuta contra QA con autorización expresa. */
class AvailabilityActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var ownerDate: LocalDate

    @Before
    fun prepareSyntheticV2Configuration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == QA_APPLICATION_ID) {
            "La prueba integral de disponibilidad sólo puede ejecutarse contra el paquete QA."
        }
        ownerDate = LocalDate.now(AppDefaults.zoneId())
        val start = ZonedDateTime.of(ownerDate, LocalTime.of(8, 0), AppDefaults.zoneId())
        val timestamp = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(1, ChronoUnit.DAYS)
        val shift = Shift(
            id = UUID.fromString("f2300000-0000-0000-0000-000000000001"),
            startAt = start.toInstant(),
            endAt = start.plusHours(8).toInstant(),
            zoneId = AppDefaults.zoneId(),
            localStartDate = ownerDate,
            objectiveNameSnapshot = "Lugar ficticio disponibilidad",
            objectiveAbbreviationSnapshot = "LFD",
            objectiveAddressSnapshot = null,
            startTimeSnapshot = LocalTime.of(8, 0),
            endTimeSnapshot = LocalTime.of(16, 0),
            colorArgbSnapshot = 0xFF336699.toInt(),
            position = null,
            status = ShiftStatus.PLANNED,
            sourceObjectiveId = V2AppTestFixture.PLACEHOLDER_OBJECTIVE_ID,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        val store = (context.applicationContext as MiGuardiaApplication).localDataStore
        store.clearAllDataForInstrumentation()
        runBlocking {
            V2AppTestFixture.writeFor(store, shift, ownerDate)
            val history = requireNotNull(store.workConfiguration.get())
            val previous = requireNotNull(history.timeline.revisionAt(ownerDate))
            val result = store.workConfiguration.applyAvailabilityMutation(
                WorkConfigurationAvailabilityMutation(
                    history,
                    EffectiveRevision(
                        id = UUID.fromString("f2300000-0000-0000-0000-000000000002"),
                        effectiveFrom = ownerDate,
                        value = previous.value.copy(availabilityLabel = AvailabilityLabel.PASSIVE_GUARD),
                    ),
                ),
            )
            assertTrue(result is WorkConfigurationAvailabilityWriteResult.Saved)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        selectOwnerDay()
        waitForTag("availability-add-$ownerDate")
    }

    @After
    fun closeActivity() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun createRecreateCorrectAndDeleteReturnsToSameCalendarDetail() {
        compose.onNodeWithTag("availability-add-$ownerDate").performScrollTo().performClick()
        waitForTag("availability-start")
        requireNotNull(scenario).recreate()
        waitForTag("availability-start")
        compose.onNodeWithTag("availability-start").assertTextContains("08:00")
        compose.onNodeWithTag("availability-window-review").performScrollTo().performClick()
        waitForTag("availability-window-save")
        requireNotNull(scenario).recreate()
        waitForTag("availability-window-save")
        compose.onNodeWithTag("availability-window-save").performScrollTo().performClick()

        compose.waitUntil(WAIT_MILLIS) { runBlocking { stored().size == 1 } }
        val created = runBlocking { stored().single() }
        waitForTag("availability-window-${created.id}")
        compose.onNodeWithTag("availability-correct-${created.id}").performScrollTo().performClick()
        waitForTag("availability-start")
        compose.onNodeWithTag("availability-start").performTextClearance()
        compose.onNodeWithTag("availability-start").performTextInput("09:00")
        compose.onNodeWithTag("availability-window-review").performScrollTo().performClick()
        waitForTag("availability-window-save")
        requireNotNull(scenario).recreate()
        waitForTag("availability-window-save")
        compose.onNodeWithTag("availability-window-save").performScrollTo().performClick()
        compose.waitUntil(WAIT_MILLIS) {
            runBlocking { stored().singleOrNull()?.start?.atZone(AppDefaults.zoneId())?.hour == 9 }
        }

        waitForTag("availability-delete-${created.id}")
        compose.onNodeWithTag("availability-delete-${created.id}").performScrollTo().performClick()
        waitForTag("availability-delete-confirm")
        requireNotNull(scenario).recreate()
        waitForTag("availability-delete-confirm")
        compose.onNodeWithTag("availability-delete-confirm").performClick()
        compose.waitUntil(WAIT_MILLIS) { runBlocking { stored().isEmpty() } }
        assertEquals(null, runBlocking { qaStore().availabilityWindows.get(created.id) })
        waitForTag("availability-add-$ownerDate")
    }

    @Test
    fun configurationDraftAndReviewSurviveRecreationWithoutChangingHoursReference() {
        val before = runBlocking {
            requireNotNull(qaStore().workConfiguration.get()).timeline.revisionAt(ownerDate)?.value
        }
        requireNotNull(before)

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply {
            pressBack()
            waitForIdle()
        }
        waitForTag("main-menu-button")
        compose.onNodeWithTag("main-menu-button").performClick()
        waitForTag("drawer-action-work-setup")
        compose.onNodeWithTag("drawer-action-work-setup").performScrollTo().performClick()
        waitForTag("work-setup-availability")
        compose.onNodeWithTag("work-setup-availability").performScrollTo().performClick()
        waitForTag("availability-configure")
        compose.onNodeWithTag("availability-configure").performScrollTo().performClick()
        waitForTag("availability-label-ON_CALL_RETAINER")
        compose.onNodeWithTag("availability-label-ON_CALL_RETAINER").performScrollTo().performClick()

        requireNotNull(scenario).recreate()
        waitForTag("availability-config-review")
        compose.onNodeWithTag("availability-config-review").performScrollTo().performClick()
        waitForTag("availability-config-save")

        requireNotNull(scenario).recreate()
        waitForTag("availability-config-save")
        compose.onNodeWithTag("availability-config-save").performScrollTo().performClick()
        compose.waitUntil(WAIT_MILLIS) {
            runBlocking {
                qaStore().workConfiguration.get()?.timeline?.valueAt(ownerDate)?.availabilityLabel ==
                    AvailabilityLabel.ON_CALL_RETAINER
            }
        }

        val after = runBlocking {
            requireNotNull(qaStore().workConfiguration.get()).timeline.revisionAt(ownerDate)?.value
        }
        requireNotNull(after)
        assertEquals(before.sector, after.sector)
        assertEquals(before.hoursReference, after.hoursReference)
        assertEquals(before.hoursReferenceStartedOn, after.hoursReferenceStartedOn)
        assertEquals(AvailabilityLabel.ON_CALL_RETAINER, after.availabilityLabel)
    }

    private fun selectOwnerDay() {
        waitForTag("month-grid")
        val regular = "day-$ownerDate"
        val completed = "completed-day-$ownerDate"
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag(regular).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag(completed).fetchSemanticsNodes().isNotEmpty()
        }
        val tag = if (compose.onAllNodesWithTag(regular).fetchSemanticsNodes().isNotEmpty()) regular else completed
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private suspend fun stored() = qaStore().availabilityWindows.observeOn(
        V2AppTestFixture.TIMELINE_ID,
        WorkSector.NURSING,
        ownerDate,
    ).first()

    private fun waitForTag(tag: String) {
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun qaStore() = (
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MiGuardiaApplication
        ).localDataStore

    private companion object {
        const val QA_APPLICATION_ID = "com.blackatsystems.miguardia.qa"
        const val WAIT_MILLIS = 15_000L
    }
}
