package com.blackatsystems.miguardia

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.blackatsystems.miguardia.core.domain.AppDefaults
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiGuardiaAppTest {
    @Test
    fun initialScreenShowsCalendarAndMainDestinations() {
        val (context, device) = launchApp()

        listOf(
            R.string.app_name,
            R.string.next_guard,
            R.string.calendar,
            R.string.summary,
            R.string.settings,
        ).forEach { expectedText ->
            device.assertTextVisible(context.getString(expectedText))
        }
    }

    @Test
    fun mainDestinationsOpenTheirEmptyStates() {
        val (context, device) = launchApp()

        device.tapText(context.getString(R.string.summary))
        device.assertTextVisible(context.getString(R.string.summary_hours_title))
        device.assertTextVisible(context.getString(R.string.summary_planned))

        device.tapText(context.getString(R.string.settings))
        device.assertTextVisible(context.getString(R.string.settings_intro))

        device.tapText(context.getString(R.string.calendar))
        device.assertTextVisible(context.getString(R.string.next_guard))
    }

    @Test
    fun summaryMonthSurvivesActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val currentMonth = YearMonth.now(AppDefaults.zoneId())

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(
                "MiGuardia no se hizo visible dentro del tiempo esperado.",
                device.wait(Until.hasObject(By.pkg(APP_PACKAGE).depth(0)), WAIT_TIMEOUT_MILLIS),
            )
            device.tapText(context.getString(R.string.summary))
            device.tapDescription(context.getString(R.string.summary_previous_month))
            device.assertTextVisible(currentMonth.minusMonths(1).displayName())

            scenario.recreate()

            device.assertTextVisible(currentMonth.minusMonths(1).displayName())
            device.assertTextVisible(context.getString(R.string.summary_hours_title))
        }
    }

    @Test
    fun monthControlsMoveAndReturnToCurrentMonth() {
        val (context, device) = launchApp()
        val currentMonth = YearMonth.now(AppDefaults.zoneId())

        device.assertTextVisible(currentMonth.displayName())
        device.tapDescription(context.getString(R.string.previous_month))
        device.assertTextVisible(currentMonth.minusMonths(1).displayName())
        device.tapDescription(context.getString(R.string.next_month))
        device.assertTextVisible(currentMonth.displayName())

        device.tapDescription(context.getString(R.string.previous_month))
        device.tapText(context.getString(R.string.today))
        device.assertTextVisible(currentMonth.displayName())
    }

    @Test
    fun selectedMonthSurvivesActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        assertFalse(
            "El dispositivo debe estar desbloqueado para probar la interfaz.",
            keyguardManager.isKeyguardLocked,
        )
        val device = UiDevice.getInstance(instrumentation)
        val currentMonth = YearMonth.now(AppDefaults.zoneId())

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(
                "MiGuardia no se hizo visible dentro del tiempo esperado.",
                device.wait(Until.hasObject(By.pkg(APP_PACKAGE).depth(0)), WAIT_TIMEOUT_MILLIS),
            )
            device.tapDescription(context.getString(R.string.previous_month))
            device.assertTextVisible(currentMonth.minusMonths(1).displayName())

            scenario.recreate()

            device.assertTextVisible(currentMonth.minusMonths(1).displayName())
        }
    }

    private fun launchApp(): Pair<Context, UiDevice> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        assertFalse(
            "El dispositivo debe estar desbloqueado para probar la interfaz.",
            keyguardManager.isKeyguardLocked,
        )

        val launchIntent = checkNotNull(context.packageManager.getLaunchIntentForPackage(APP_PACKAGE))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launchIntent)

        val device = UiDevice.getInstance(instrumentation)
        assertTrue(
            "MiGuardia no se hizo visible dentro del tiempo esperado.",
            device.wait(Until.hasObject(By.pkg(APP_PACKAGE).depth(0)), WAIT_TIMEOUT_MILLIS),
        )
        return context to device
    }

    private fun UiDevice.assertTextVisible(text: String) {
        assertTrue(
            "No se encontró el texto esperado: $text",
            wait(Until.hasObject(By.text(text)), WAIT_TIMEOUT_MILLIS),
        )
    }

    private fun UiDevice.tapText(text: String) {
        assertTextVisible(text)
        findObject(By.text(text)).click()
    }

    private fun UiDevice.tapDescription(description: String) {
        assertTrue(
            "No se encontró el control esperado: $description",
            wait(Until.hasObject(By.desc(description)), WAIT_TIMEOUT_MILLIS),
        )
        findObject(By.desc(description)).click()
    }

    private fun YearMonth.displayName(): String {
        val locale = Locale.forLanguageTag("es-AR")
        val monthName = month.getDisplayName(TextStyle.FULL, locale)
            .replaceFirstChar { it.titlecase(locale) }
        return "$monthName de $year"
    }

    private companion object {
        const val APP_PACKAGE = "com.blackatsystems.miguardia"
        const val WAIT_TIMEOUT_MILLIS = 5_000L
    }
}
