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
    fun initialScreenShowsCalendarAndDrawerEntryWithoutPermanentBottomDestinations() {
        val (context, device) = launchApp()

        listOf(
            R.string.app_name,
            R.string.next_guard,
        ).forEach { expectedText ->
            device.assertTextVisible(context.getString(expectedText))
        }
        device.assertTextGone(context.getString(R.string.summary))
        device.assertTextGone(context.getString(R.string.appearance))
        device.tapDescription(context.getString(R.string.open_menu))
        listOf(
            R.string.calendar,
            R.string.summary,
            R.string.profile,
            R.string.objectives_and_schedules,
        ).forEach { destination ->
            device.assertTextVisible(context.getString(destination))
        }
    }

    @Test
    fun mainDestinationsOpenTheirEmptyStates() {
        val (context, device) = launchApp()

        device.openDestination(context, R.string.summary)
        device.assertTextVisible(context.getString(R.string.summary_hours_title))
        device.assertTextVisible(context.getString(R.string.summary_planned))

        device.openDestination(context, R.string.appearance)
        device.assertTextVisible(context.getString(R.string.appearance_intro))

        device.openDestination(context, R.string.calendar)
        device.assertTextVisible(context.getString(R.string.next_guard))
    }

    @Test
    fun backClosesDrawerBeforeReturningSummaryAndAppearanceToCalendar() {
        val (context, device) = launchApp()

        device.openDestination(context, R.string.summary)
        device.assertTextVisible(context.getString(R.string.summary_hours_title))
        device.tapDescription(context.getString(R.string.open_menu))
        device.pressBack()
        device.assertTextGone(context.getString(R.string.calendar))
        device.assertTextVisible(context.getString(R.string.summary_hours_title))

        device.pressBack()
        device.assertTextVisible(context.getString(R.string.next_guard))

        device.openDestination(context, R.string.appearance)
        device.assertTextVisible(context.getString(R.string.appearance_intro))
        device.pressBack()
        device.assertTextVisible(context.getString(R.string.next_guard))
    }

    @Test
    fun summaryMonthSurvivesActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        prepareDevice(instrumentation, context, device)
        val currentMonth = YearMonth.now(AppDefaults.zoneId())

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(
                "MiGuardia no se hizo visible dentro del tiempo esperado.",
                device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), WAIT_TIMEOUT_MILLIS),
            )
            device.openDestination(context, R.string.summary)
            device.tapDescription(context.getString(R.string.summary_previous_month))
            device.assertTextVisible(currentMonth.minusMonths(1).displayName())
            device.tapDescription(context.getString(R.string.open_menu))
            device.assertTextVisible(context.getString(R.string.calendar))

            scenario.recreate()

            device.assertTextVisible(currentMonth.minusMonths(1).displayName())
            device.assertTextVisible(context.getString(R.string.summary_hours_title))
            device.pressBack()
            device.assertTextVisible(context.getString(R.string.next_guard))
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
        device.tapText("Ir a hoy")
        device.assertTextVisible(currentMonth.displayName())
    }

    @Test
    fun selectedMonthSurvivesActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        val device = UiDevice.getInstance(instrumentation)
        prepareDevice(instrumentation, context, device)
        assertFalse(
            "El dispositivo debe estar desbloqueado para probar la interfaz.",
            keyguardManager.isKeyguardLocked,
        )
        val currentMonth = YearMonth.now(AppDefaults.zoneId())

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(
                "MiGuardia no se hizo visible dentro del tiempo esperado.",
                device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), WAIT_TIMEOUT_MILLIS),
            )
            device.tapDescription(context.getString(R.string.previous_month))
            device.assertTextVisible(currentMonth.minusMonths(1).displayName())

            scenario.recreate()

            device.assertTextVisible(currentMonth.minusMonths(1).displayName())
        }
    }

    @Test
    fun calendarEditModeSurvivesActivityRecreationAndFinishesSafely() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        prepareDevice(instrumentation, context, device)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(
                "MiGuardia no se hizo visible dentro del tiempo esperado.",
                device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), WAIT_TIMEOUT_MILLIS),
            )
            val loadData = "Cargar datos"
            val editCalendar = "Editar calendario"
            val entry = when {
                device.scrollUntilText(loadData) -> loadData
                device.scrollUntilText(editCalendar) -> editCalendar
                else -> throw AssertionError("No apareció una entrada consciente a la edición del calendario.")
            }
            device.tapText(entry)
            if (entry == loadData) {
                assertTrue(
                    "La primera carga no mostró su preparación sin abrir una guardia.",
                    device.scrollUntilText("Primero: prepará objetivos y horarios"),
                )
                device.assertTextGone("Cargar mi primera guardia")
                scenario.recreate()
                assertTrue(
                    "La preparación de datos no sobrevivió la recreación.",
                    device.scrollUntilText("Primero: prepará objetivos y horarios"),
                )
                assertTrue(
                    "No apareció Salir por ahora después de recrear la preparación.",
                    device.scrollUntilText("Salir por ahora"),
                )
                device.tapText("Salir por ahora")
                assertTrue("La salida no volvió al calendario vacío.", device.scrollUntilText(loadData))
            } else {
                assertTrue("No apareció Salir de edición.", device.scrollUntilText("Salir de edición"))
                scenario.recreate()
                assertTrue("El modo edición no sobrevivió la recreación.", device.scrollUntilText("Salir de edición"))

                device.tapText("Salir de edición")
                assertTrue(
                    "Salir de edición no regresó al modo consulta.",
                    device.scrollUntilText(loadData) || device.scrollUntilText(editCalendar),
                )
            }
        }
    }

    private fun launchApp(): Pair<Context, UiDevice> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        prepareDevice(instrumentation, context, device)
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        assertFalse(
            "El dispositivo debe estar desbloqueado para probar la interfaz.",
            keyguardManager.isKeyguardLocked,
        )

        val launchIntent = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launchIntent)

        assertTrue(
            "MiGuardia no se hizo visible dentro del tiempo esperado.",
            device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), WAIT_TIMEOUT_MILLIS),
        )
        return context to device
    }

    private fun prepareDevice(
        instrumentation: android.app.Instrumentation,
        context: Context,
        device: UiDevice,
    ) {
        device.wakeUp()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
        device.waitForIdle()
        assertFalse(
            "El dispositivo debe estar desbloqueado para probar la interfaz.",
            context.getSystemService(KeyguardManager::class.java).isKeyguardLocked,
        )
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
        waitForIdle()
    }

    private fun UiDevice.openDestination(context: Context, destination: Int) {
        tapDescription(context.getString(R.string.open_menu))
        tapText(context.getString(destination))
    }

    private fun UiDevice.assertTextGone(text: String) {
        assertTrue(
            "El texto debía dejar de estar visible: $text",
            wait(Until.gone(By.text(text)), WAIT_TIMEOUT_MILLIS),
        )
    }

    private fun UiDevice.scrollUntilText(text: String): Boolean {
        repeat(8) {
            if (hasObject(By.text(text))) return true
            swipe(displayWidth / 2, displayHeight * 3 / 4, displayWidth / 2, displayHeight / 4, 30)
            waitForIdle()
        }
        return hasObject(By.text(text))
    }

    private fun YearMonth.displayName(): String {
        val locale = Locale.forLanguageTag("es-AR")
        val monthName = month.getDisplayName(TextStyle.FULL, locale)
            .replaceFirstChar { it.titlecase(locale) }
        return "$monthName de $year"
    }

    private companion object {
        const val WAIT_TIMEOUT_MILLIS = 5_000L
    }
}
