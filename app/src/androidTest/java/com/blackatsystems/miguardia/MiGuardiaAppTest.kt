package com.blackatsystems.miguardia

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiGuardiaAppTest {
    @Test
    fun freshV2InstallShowsExactlyTheFourAuthorizedSectors() {
        val (context, device, scenario) = launchFreshV2App()
        scenario.use {
            device.assertTextVisible("¿En qué rubro trabajás?")
            listOf("Vigilancia privada", "Policía", "Enfermería", "Medicina").forEach {
                device.assertTextVisible(it)
            }
            listOf("Salud", "Otro", "Resumen", "Perfil laboral", "Objetivos y horarios", "Cargar datos")
                .forEach { text -> device.assertTextGone(text) }
            device.assertTextGone(context.getString(R.string.next_guard))
        }
    }

    @Test
    fun freshV2SelectionRemainsBlockingAfterActivityRecreation() {
        val (_, device, scenario) = launchFreshV2App()
        scenario.use {
            device.assertTextVisible("¿En qué rubro trabajás?")
            it.recreate()
            device.assertTextVisible("¿En qué rubro trabajás?")
            device.assertTextGone("Calendario")
        }
    }

    private fun launchFreshV2App(): Triple<Context, UiDevice, ActivityScenario<MainActivity>> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == QA_APPLICATION_ID) {
            "La prueba de primera apertura V2 sólo puede ejecutarse contra el paquete QA."
        }
        val store = (context.applicationContext as MiGuardiaApplication).localDataStore
        store.clearAllDataForInstrumentation()
        assertNull(
            "La preparación QA debe dejar una primera apertura sin configuración.",
            runBlocking { store.workConfiguration.get() },
        )
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
        device.waitForIdle()
        assertFalse(
            "El dispositivo debe estar desbloqueado para probar la interfaz.",
            context.getSystemService(KeyguardManager::class.java).isKeyguardLocked,
        )

        val launchIntent = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val scenario = ActivityScenario.launch<MainActivity>(launchIntent)
        assertTrue(
            "MiGuardia no se hizo visible dentro del tiempo esperado.",
            device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), WAIT_TIMEOUT_MILLIS),
        )
        return Triple(context, device, scenario)
    }

    private fun UiDevice.assertTextVisible(text: String) {
        assertTrue(
            "No se encontró el texto esperado: $text",
            wait(Until.hasObject(By.text(text)), WAIT_TIMEOUT_MILLIS),
        )
    }

    private fun UiDevice.assertTextGone(text: String) {
        assertTrue(
            "El texto no debía estar visible: $text",
            wait(Until.gone(By.text(text)), WAIT_TIMEOUT_MILLIS),
        )
    }

    private companion object {
        const val QA_APPLICATION_ID: String = "com.blackatsystems.miguardia.qa"
        const val WAIT_TIMEOUT_MILLIS: Long = 5_000L
    }
}
