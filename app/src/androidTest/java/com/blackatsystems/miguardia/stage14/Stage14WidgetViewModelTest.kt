package com.blackatsystems.miguardia.stage14

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.ui.widget.WidgetSurface
import com.blackatsystems.miguardia.ui.widget.WidgetViewModel
import com.blackatsystems.miguardia.widget.WidgetInstancePreferences
import com.blackatsystems.miguardia.widget.WidgetPreferencesStore
import com.blackatsystems.miguardia.widget.WidgetRemoteViewsRenderer
import com.blackatsystems.miguardia.widget.WidgetRuntime
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Etapa 14 · Widget de inicio: `WidgetViewModel` con dobles. Necesita Android porque recibe un
 * `AppWidgetManager` (clase del sistema sin constructor público), así que no puede ser prueba de PC.
 * No usa la base, las preferencias ni el motor de la app: todo es propio de la prueba.
 */
@RunWith(AndroidJUnit4::class)
class Stage14WidgetViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scheduler = Stage14WidgetRecordingScheduler()
    private val weatherClient = Stage14WidgetOfflineWeatherClient()
    private val storeOpens = AtomicInteger()
    private lateinit var storeScope: CoroutineScope
    private lateinit var preferences: WidgetPreferencesStore
    private lateinit var runtime: WidgetRuntime
    private var viewModel: WidgetViewModel? = null

    @Before
    fun setUp() {
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val folder = temporaryFolder.newFolder("stage14-widget-view-model")
        preferences = WidgetPreferencesStore(File(folder, "stage14-widget.preferences_pb"), storeScope)
        runtime = WidgetRuntime(
            context = context,
            localDataStore = {
                storeOpens.incrementAndGet()
                error("La prueba no debe abrir ninguna base")
            },
            preferences = preferences,
            weatherRuntime = stage14WidgetWeatherRuntime(context, folder, storeScope, CLOCK, weatherClient),
            clock = CLOCK,
            renderer = WidgetRemoteViewsRenderer(context),
            scheduler = scheduler,
        )
    }

    @After
    fun tearDown() {
        viewModel?.viewModelScope?.cancel()
        runtime.scope.cancel()
        storeScope.cancel()
    }

    // W002 / W003: sin Widgets puestos, abrir deja la lista vacía y sin error, y le pide al motor que redibuje.
    @Test
    fun openWithNoInstalledWidgetEndsEmptyWithoutErrorAndAsksTheEngineToRefresh() = runBlocking<Unit> {
        // Sin esta guarda, el motor de la prueba dibujaría sobre un Widget real del teléfono.
        assumeTrue(
            "Hay un Widget real en la pantalla de inicio: esta prueba aislada no corre.",
            stage14InstalledWidgetIds(context).isEmpty(),
        )
        assumeTrue("La Application no terminó su arranque.", stage14WidgetStartupIsReady(context))
        preferences.save(
            8101,
            WidgetInstancePreferences(WidgetMode.NEXT_SHIFT, WidgetPrivacy.COMPLETE, includeWeather = true, configured = true),
        )
        val model = WidgetViewModel(
            appWidgetManager = AppWidgetManager.getInstance(context),
            provider = ComponentName(context.packageName, ABSENT_PROVIDER_CLASS),
            preferences = preferences,
            runtime = runtime,
        ).also { viewModel = it }
        assertEquals(WidgetSurface.NONE, model.uiState.value.surface)

        model.open()

        assertEquals(WidgetSurface.GLOBAL, model.uiState.value.surface)
        // `runtime.refreshAll()` es lo último que hace `refresh()`; sin instancias, el motor cancela su alarma.
        assertTrue(
            "El ViewModel no llegó a pedirle al motor que redibuje",
            stage14WidgetWaitUntil { scheduler.cancels.get() >= 1 },
        )
        val loaded = withTimeout(5_000) { model.uiState.first { !it.isLoading } }
        // Una preferencia guardada sin instancia puesta no inventa una fila.
        assertTrue(loaded.instances.isEmpty())
        assertNull(loaded.errorMessage)
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(0, storeOpens.get())
        assertEquals(0, weatherClient.calls.get())

        model.close()

        assertEquals(WidgetSurface.NONE, model.uiState.value.surface)
    }

    // W003: si Android rechaza la lectura de instancias, queda el error y «Cerrar» lo limpia.
    @Test
    fun refreshFailureShowsTheReadErrorWithoutInstancesAndCloseClearsIt() = runBlocking<Unit> {
        // Doble de fallo: Android rechaza con SecurityException consultar un proveedor de otro paquete.
        val model = WidgetViewModel(
            appWidgetManager = AppWidgetManager.getInstance(context),
            provider = ComponentName(FOREIGN_PACKAGE, "$FOREIGN_PACKAGE.ProveedorFicticio"),
            preferences = preferences,
            runtime = runtime,
        ).also { viewModel = it }

        model.open()

        val failed = withTimeout(5_000) { model.uiState.first { it.errorMessage != null } }
        assertEquals("No pudimos leer los Widgets instalados.", failed.errorMessage)
        assertTrue(failed.instances.isEmpty())
        assertFalse(failed.isLoading)
        assertEquals(WidgetSurface.GLOBAL, failed.surface)
        // El fallo corta antes de avisarle al motor.
        assertEquals(0, scheduler.cancels.get())
        assertTrue(scheduler.scheduled.isEmpty())

        model.close()

        assertNull(model.uiState.value.errorMessage)
        assertEquals(WidgetSurface.NONE, model.uiState.value.surface)
    }

    private companion object {
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-08-29T15:00:00Z"), ZoneOffset.UTC)
        const val ABSENT_PROVIDER_CLASS = "com.blackatsystems.miguardia.stage14.Stage14ProveedorInexistente"
        const val FOREIGN_PACKAGE = "stage14.paquete.ajeno.ficticio"
    }
}
