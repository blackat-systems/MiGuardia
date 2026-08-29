package com.blackatsystems.miguardia

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.widget.NextEventAppWidgetProvider
import com.blackatsystems.miguardia.widget.WidgetConfigurationActivity
import com.blackatsystems.miguardia.widget.WidgetInstancePreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetConfigurationActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val application: MiGuardiaApplication
        get() = context.applicationContext as MiGuardiaApplication
    private lateinit var host: AppWidgetHost
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var scenario: ActivityScenario<WidgetConfigurationActivity>? = null

    @Before
    fun setUp() {
        host = AppWidgetHost(context, TEST_HOST_ID)
        appWidgetId = host.allocateAppWidgetId()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity()
        try {
            assertTrue(
                AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(
                    appWidgetId,
                    ComponentName(context, NextEventAppWidgetProvider::class.java),
                ),
            )
        } finally {
            automation.dropShellPermissionIdentity()
        }
        runBlocking { application.widgetPreferences.delete(listOf(appWidgetId)) }
    }

    @After
    fun tearDown() {
        scenario?.close()
        runBlocking { application.widgetPreferences.delete(listOf(appWidgetId)) }
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            host.deleteAppWidgetId(appWidgetId)
        }
    }

    @Test
    fun initialConfigurationRestoresTheDraftAndSavesTheExactInstance() {
        scenario = launchConfiguration()

        compose.onNodeWithTag("widget-mode-next_shift").performScrollTo().performClick()
        compose.onNodeWithTag("widget-privacy-complete").performScrollTo().performClick()
        compose.onNodeWithTag("widget-weather-toggle").performScrollTo().performClick()
        scenario?.recreate()

        compose.onNodeWithTag("widget-mode-next_shift").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("widget-privacy-complete").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("widget-weather-toggle").performScrollTo().assertIsOn()
        compose.onNodeWithTag("widget-save").performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 10_000) {
            scenario?.state == Lifecycle.State.DESTROYED
        }
        assertEquals(
            WidgetInstancePreferences(
                mode = WidgetMode.NEXT_SHIFT,
                privacy = WidgetPrivacy.COMPLETE,
                includeWeather = true,
                configured = true,
            ),
            runBlocking { application.widgetPreferences.current(appWidgetId) },
        )
    }

    @Test
    fun cancellingReconfigurationLeavesThePreviousPreferencesUntouched() {
        val previous = WidgetInstancePreferences(
            mode = WidgetMode.NEXT_DAY_OFF,
            privacy = WidgetPrivacy.REDUCED,
            includeWeather = false,
            configured = true,
        )
        runBlocking { application.widgetPreferences.save(appWidgetId, previous) }
        scenario = launchConfiguration()

        compose.onNodeWithTag("widget-configuration-screen").assertIsDisplayed()
        compose.onNodeWithTag("widget-mode-automatic").performScrollTo().performClick()
        compose.onNodeWithTag("widget-privacy-complete").performScrollTo().performClick()
        compose.onNodeWithTag("widget-cancel").performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 10_000) {
            scenario?.state == Lifecycle.State.DESTROYED
        }
        assertEquals(previous, runBlocking { application.widgetPreferences.current(appWidgetId) })
    }

    private fun launchConfiguration(): ActivityScenario<WidgetConfigurationActivity> =
        ActivityScenario.launch(
            Intent(context, WidgetConfigurationActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )

    private companion object {
        const val TEST_HOST_ID = 0x4D47
    }
}
