package com.blackatsystems.miguardia.stage14

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.model.Shift
import com.blackatsystems.miguardia.core.domain.model.ShiftNotificationConfig
import com.blackatsystems.miguardia.core.domain.model.ShiftStatus
import com.blackatsystems.miguardia.core.domain.repository.ShiftNotificationConfigRepository
import com.blackatsystems.miguardia.notifications.NotificationAttentionMode
import com.blackatsystems.miguardia.notifications.NotificationPreferences
import com.blackatsystems.miguardia.notifications.NotificationPreferencesStore
import com.blackatsystems.miguardia.notifications.NotificationPrivacy
import com.blackatsystems.miguardia.notifications.NotificationRhythm
import com.blackatsystems.miguardia.notifications.NotificationRuntime
import com.blackatsystems.miguardia.notifications.NotificationSystemAccess
import com.blackatsystems.miguardia.ui.notifications.NotificationSurface
import com.blackatsystems.miguardia.ui.notifications.NotificationUiState
import com.blackatsystems.miguardia.ui.notifications.NotificationViewModel
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import com.blackatsystems.miguardia.weather.WeatherRuntime
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Etapa 14.1 · Modelo de la pantalla de Avisos (`NotificationViewModel`).
 *
 * Va instrumentada porque el modelo recibe clases concretas de Android que no admiten dobles
 * (`NotificationSystemAccess`, `NotificationRuntime`). Aislamiento:
 * - preferencias de avisos en un archivo de `TemporaryFolder` (o un almacén en memoria);
 * - avisos por jornada en un repositorio de mentira en memoria;
 * - el permiso de avisos se simula con un `ContextWrapper` (sólo Android 13 o superior);
 * - el `NotificationRuntime` se construye sobre un contexto inerte y queda **pausado** antes de
 *   entregarlo: en ese estado no publica avisos, no programa alarmas y no restaura nada. Su base
 *   lleva otro nombre, apunta a la carpeta temporal y nunca se consulta.
 * No toca `miguardia-v2.db`, `application.localDataStore` ni preferencias reales.
 */
@RunWith(AndroidJUnit4::class)
class Stage14NotificationViewModelTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fixtures = mutableListOf<Fixture>()

    @After
    fun releaseFixtures() {
        fixtures.forEach(Fixture::release)
        ioScope.cancel()
    }

    // A208
    @Test
    fun startsClosedAndLoadsTheStoredDefaults() {
        val fixture = fixture()
        val viewModel = fixture.viewModel()

        val state = viewModel.await("carga inicial") { !it.isLoading }

        assertEquals(NotificationSurface.NONE, state.surface)
        assertEquals(NotificationPreferences(), state.preferences)
        assertFalse(state.preferences.enabled)
        assertTrue(state.restorableEvents.isEmpty())
        assertNull(state.selectedShift)
        assertNull(state.errorMessage)
        assertNull(state.infoMessage)
        assertFalse(state.isSaving)
    }

    // A209, A217, A218, A219, A220, A221, A222, A223
    @Test
    fun everyPreferenceWriteReachesTheStoreAndReportsSaved() {
        val fixture = fixture()
        val viewModel = fixture.viewModel()
        viewModel.await("carga inicial") { !it.isLoading }

        viewModel.setEnabled(true)
        viewModel.awaitSaved("activar") { it.preferences.enabled }

        viewModel.clearMessage()
        viewModel.setGlobalReminders(listOf(1_440L, 360L))
        viewModel.awaitSaved("anticipos") { it.preferences.globalReminderLeadMinutes == listOf(360L, 1_440L) }

        viewModel.clearMessage()
        viewModel.setPersistent(false)
        viewModel.awaitSaved("descartable") { !it.preferences.persistentWhileActive }

        viewModel.clearMessage()
        viewModel.setPrivacy(NotificationPrivacy.HIDDEN)
        viewModel.awaitSaved("privacidad") { it.preferences.privacy == NotificationPrivacy.HIDDEN }

        viewModel.clearMessage()
        viewModel.setAttentionMode(NotificationAttentionMode.VIBRATION_ONLY)
        viewModel.awaitSaved("atención") {
            it.preferences.attentionMode == NotificationAttentionMode.VIBRATION_ONLY
        }

        viewModel.clearMessage()
        viewModel.setPreciseTiming(true)
        viewModel.awaitSaved("puntualidad") { it.preferences.preciseTiming }

        viewModel.clearMessage()
        viewModel.setSound(FAKE_SOUND)
        viewModel.awaitSaved("sonido elegido") { it.preferences.soundUri == FAKE_SOUND }

        viewModel.clearMessage()
        viewModel.setSound(null)
        val beforeRhythm = viewModel.awaitSaved("sonido predeterminado") { it.preferences.soundUri == null }
        assertEquals(NotificationRhythm.CUSTOM, beforeRhythm.preferences.rhythm())
        assertEquals(beforeRhythm.preferences, runBlocking { fixture.store.current() })

        viewModel.clearMessage()
        viewModel.applyRhythm(NotificationRhythm.DISCREET)
        val discreet = viewModel.awaitSaved("ritmo discreto") {
            it.preferences.rhythm() == NotificationRhythm.DISCREET
        }
        assertEquals(listOf(720L), discreet.preferences.globalReminderLeadMinutes)
        assertFalse(discreet.preferences.persistentWhileActive)
        assertEquals(NotificationPrivacy.REDUCED, discreet.preferences.privacy)
        assertEquals(NotificationAttentionMode.SILENT, discreet.preferences.attentionMode)
        // El ritmo no cambia lo que no le pertenece.
        assertTrue(discreet.preferences.enabled)
        assertTrue(discreet.preferences.preciseTiming)

        viewModel.clearMessage()
        viewModel.setEnabled(false)
        val off = viewModel.awaitSaved("desactivar") { !it.preferences.enabled }
        assertEquals(off.preferences, runBlocking { fixture.store.current() })
    }

    // A218 (bordes: seis avisos, cero minutos, ritmo «Personalizado»)
    @Test
    fun rejectedWritesShowTheReasonAndLeaveTheStoredValueUntouched() {
        val fixture = fixture()
        val viewModel = fixture.viewModel()
        viewModel.await("carga inicial") { !it.isLoading }

        viewModel.setGlobalReminders(listOf(10L, 20L, 30L, 40L, 50L, 60L))
        val tooMany = viewModel.await("más de cinco") {
            !it.isSaving && it.errorMessage == "Se admiten hasta cinco avisos por jornada."
        }
        assertNull(tooMany.infoMessage)

        viewModel.clearMessage()
        assertNull(viewModel.uiState.value.errorMessage)

        viewModel.setGlobalReminders(listOf(0L))
        viewModel.await("cero minutos") {
            !it.isSaving && it.errorMessage == "Cada aviso debe anticiparse una cantidad positiva de minutos."
        }

        viewModel.applyRhythm(NotificationRhythm.CUSTOM)
        viewModel.await("ritmo personalizado") {
            !it.isSaving && it.errorMessage == "El ritmo personalizado no es un preset aplicable."
        }

        assertEquals(NotificationPreferences(), runBlocking { fixture.store.current() })
        assertEquals(NotificationPreferences(), viewModel.uiState.value.preferences)

        // Un guardado válido posterior limpia el error.
        viewModel.setGlobalReminders(listOf(480L))
        val saved = viewModel.awaitSaved("guardado válido") {
            it.preferences.globalReminderLeadMinutes == listOf(480L)
        }
        assertNull(saved.errorMessage)
    }

    // A214 (guarda de permiso)
    @Test
    fun testNotificationWithoutPermissionOnlyExplainsWhatIsMissing() {
        assumeTrue("El permiso de avisos sólo se puede simular desde Android 13.", Build.VERSION.SDK_INT >= 33)
        val fixture = fixture()
        fixture.permission.postNotifications = PackageManager.PERMISSION_DENIED
        val viewModel = fixture.viewModel()
        viewModel.await("carga inicial") { !it.isLoading }
        assertFalse(viewModel.uiState.value.systemAccess.notificationAccessGranted)

        viewModel.sendTestNotification()

        val state = viewModel.await("mensaje de permiso") {
            !it.isSaving && it.infoMessage == "Primero permití las notificaciones en Android."
        }
        assertNull(state.errorMessage)
    }

    // A214 (camino del modelo; el runtime está pausado y no publica nada)
    @Test
    fun testNotificationWithPermissionReportsItWasSent() {
        val fixture = fixture()
        fixture.permission.postNotifications = PackageManager.PERMISSION_GRANTED
        assumeTrue(
            "Las notificaciones de la aplicación bajo prueba están bloqueadas en este equipo.",
            NotificationSystemAccess(fixture.permission).read().notificationAccessGranted,
        )
        val viewModel = fixture.viewModel()
        viewModel.await("carga inicial") { !it.isLoading }

        viewModel.sendTestNotification()

        val state = viewModel.await("mensaje de envío") {
            !it.isSaving && it.infoMessage == "Prueba enviada. Se elimina sola en un minuto."
        }
        assertNull(state.errorMessage)
    }

    // A212 (refresco al volver de Ajustes), A211
    @Test
    fun refreshOpenGlobalAndRetryReadAndroidAccessAgain() {
        assumeTrue("El permiso de avisos sólo se puede simular desde Android 13.", Build.VERSION.SDK_INT >= 33)
        val fixture = fixture()
        fixture.permission.postNotifications = PackageManager.PERMISSION_DENIED
        val viewModel = fixture.viewModel()
        viewModel.await("carga inicial") { !it.isLoading }
        assertFalse(viewModel.uiState.value.systemAccess.notificationPermissionGranted)

        fixture.permission.postNotifications = PackageManager.PERMISSION_GRANTED
        assertFalse(viewModel.uiState.value.systemAccess.notificationPermissionGranted)
        viewModel.refreshSystemAccess()
        assertTrue(viewModel.uiState.value.systemAccess.notificationPermissionGranted)

        fixture.permission.postNotifications = PackageManager.PERMISSION_DENIED
        viewModel.openGlobal()
        val opened = viewModel.uiState.value
        assertEquals(NotificationSurface.GLOBAL, opened.surface)
        assertFalse(opened.systemAccess.notificationPermissionGranted)
        assertFalse(opened.systemAccess.notificationAccessGranted)

        fixture.permission.postNotifications = PackageManager.PERMISSION_GRANTED
        viewModel.retry()
        val retried = viewModel.await("reintento") {
            !it.isLoading && it.systemAccess.notificationPermissionGranted
        }
        assertEquals(NotificationSurface.GLOBAL, retried.surface)
        assertNull(retried.errorMessage)

        viewModel.close()
        assertEquals(NotificationSurface.NONE, viewModel.uiState.value.surface)
    }

    // A225, A226 (lo que se puede sin publicar: nada oculto y evento que ya no admite el aviso)
    @Test
    fun restoreAllWithNothingHiddenIsIgnoredAndAStaleEventIsExplained() {
        val fixture = fixture()
        val viewModel = fixture.viewModel()
        viewModel.await("carga inicial") { !it.isLoading }
        assertTrue(viewModel.uiState.value.restorableEvents.isEmpty())

        viewModel.restoreAllNotifications()
        val untouched = viewModel.uiState.value
        assertNull(untouched.infoMessage)
        assertNull(untouched.errorMessage)
        assertFalse(untouched.isSaving)

        viewModel.restoreNotification("shift:$SHIFT_A")
        val state = viewModel.await("evento vencido") {
            !it.isSaving && it.infoMessage == "El evento ya no podía mostrar esa notificación."
        }
        assertNull(state.errorMessage)
    }

    // C080, A227, A228, A229
    @Test
    fun shiftOverrideFollowsCustomizeDisableAndBackToGeneral() {
        val fixture = fixture()
        val viewModel = fixture.viewModel()
        viewModel.await("carga inicial") { !it.isLoading }
        val shift = shift(SHIFT_A)

        viewModel.openShift(shift)
        val opened = viewModel.await("jornada abierta") {
            it.surface == NotificationSurface.SHIFT && it.selectedShift == shift && !it.isLoading
        }
        assertNull(opened.shiftOverride)
        assertNull(opened.errorMessage)

        // A228: anticipos propios.
        viewModel.setShiftReminders(listOf(720L, 30L))
        val own = ShiftNotificationConfig(SHIFT_A, listOf(720L, 30L))
        viewModel.awaitSaved("anticipos propios") { it.shiftOverride == own }
        assertEquals(listOf(own), fixture.configs.replaced.toList())

        // A227: desactivar sólo esta jornada.
        viewModel.clearMessage()
        viewModel.disableShift()
        val silenced = ShiftNotificationConfig(SHIFT_A, emptyList())
        viewModel.awaitSaved("jornada desactivada") { it.shiftOverride == silenced }
        assertEquals(listOf(own, silenced), fixture.configs.replaced.toList())
        assertTrue(fixture.configs.cleared.isEmpty())

        // A229: volver a la general borra la configuración propia.
        viewModel.clearMessage()
        viewModel.useGlobalForShift()
        viewModel.awaitSaved("vuelta a la general") { it.shiftOverride == null }
        assertEquals(listOf(SHIFT_A), fixture.configs.cleared.toList())
        assertNull(runBlocking { fixture.configs.getForShift(SHIFT_A) })

        // Las preferencias generales no cambiaron por tocar una jornada.
        assertEquals(NotificationPreferences(), runBlocking { fixture.store.current() })
    }

    // C080 (bordes: otra jornada, pantalla cerrada)
    @Test
    fun eachShiftShowsOnlyItsOwnOverrideAndAClosedSurfaceWritesNothing() {
        val fixture = fixture()
        val stored = ShiftNotificationConfig(SHIFT_A, listOf(45L))
        fixture.configs.stored.value = mapOf(SHIFT_A to stored)
        val viewModel = fixture.viewModel()
        viewModel.await("carga inicial") { !it.isLoading }

        // Sin jornada abierta, ninguna de las tres acciones escribe.
        viewModel.setShiftReminders(listOf(60L))
        viewModel.disableShift()
        viewModel.useGlobalForShift()
        assertTrue(fixture.configs.replaced.isEmpty())
        assertTrue(fixture.configs.cleared.isEmpty())
        assertFalse(viewModel.uiState.value.isSaving)

        viewModel.openShift(shift(SHIFT_A))
        viewModel.await("jornada A") { it.selectedShift?.id == SHIFT_A && !it.isLoading && it.shiftOverride == stored }

        viewModel.openShift(shift(SHIFT_B))
        val second = viewModel.await("jornada B") { it.selectedShift?.id == SHIFT_B && !it.isLoading }
        assertNull(second.shiftOverride)

        viewModel.close()
        val closed = viewModel.uiState.value
        assertEquals(NotificationSurface.NONE, closed.surface)
        assertNull(closed.selectedShift)
        assertNull(closed.shiftOverride)
        viewModel.disableShift()
        assertTrue(fixture.configs.replaced.isEmpty())
        assertEquals(mapOf(SHIFT_A to stored), fixture.configs.stored.value)
    }

    // C080 (error de lectura con reintento)
    @Test
    fun shiftReadFailureIsReportedAndRetryLoadsTheOverride() {
        val fixture = fixture()
        val stored = ShiftNotificationConfig(SHIFT_A, listOf(90L))
        fixture.configs.stored.value = mapOf(SHIFT_A to stored)
        fixture.configs.observeFailure = IllegalStateException("Fallo ficticio de lectura")
        val viewModel = fixture.viewModel()
        viewModel.await("carga inicial") { !it.isLoading }
        val shift = shift(SHIFT_A)

        viewModel.openShift(shift)
        val failed = viewModel.await("error de lectura") {
            !it.isLoading && it.errorMessage == "No pudimos leer los avisos de esta jornada."
        }
        assertEquals(NotificationSurface.SHIFT, failed.surface)
        assertNull(failed.shiftOverride)

        fixture.configs.observeFailure = null
        viewModel.retry()
        val recovered = viewModel.await("reintento") {
            !it.isLoading && it.errorMessage == null && it.shiftOverride == stored
        }
        assertEquals(NotificationSurface.SHIFT, recovered.surface)
        assertEquals(shift, recovered.selectedShift)
    }

    // C080, A228 (errores de guardado)
    @Test
    fun shiftWriteFailuresAreReportedAndNothingIsStored() {
        val fixture = fixture()
        val viewModel = fixture.viewModel()
        viewModel.await("carga inicial") { !it.isLoading }
        viewModel.openShift(shift(SHIFT_A))
        viewModel.await("jornada abierta") { it.selectedShift?.id == SHIFT_A && !it.isLoading }

        fixture.configs.writeFailure = IllegalStateException("Fallo ficticio de escritura")
        viewModel.setShiftReminders(listOf(60L))
        viewModel.await("error con mensaje") {
            !it.isSaving && it.errorMessage == "Fallo ficticio de escritura"
        }

        fixture.configs.writeFailure = IllegalStateException()
        viewModel.useGlobalForShift()
        viewModel.await("error sin mensaje") {
            !it.isSaving && it.errorMessage == "No pudimos guardar la configuración."
        }

        fixture.configs.writeFailure = null
        viewModel.setShiftReminders(listOf(10L, 20L, 30L, 40L, 50L, 60L))
        val tooMany = viewModel.await("más de cinco") {
            !it.isSaving && it.errorMessage == "Se admiten hasta cinco avisos por jornada."
        }

        assertNull(tooMany.infoMessage)
        assertNull(tooMany.shiftOverride)
        assertTrue(fixture.configs.replaced.isEmpty())
        assertTrue(fixture.configs.cleared.isEmpty())
        assertTrue(fixture.configs.stored.value.isEmpty())
    }

    // A208 (error de lectura de preferencias con reintento)
    @Test
    fun preferenceReadFailureIsReportedAndRetryLoadsTheStoredValues() {
        val failing = FailingPreferences()
        val fixture = fixture(failing)
        val viewModel = fixture.viewModel()

        val failed = viewModel.await("error de lectura") {
            !it.isLoading && it.errorMessage == "No pudimos leer la configuración de avisos."
        }
        assertEquals(NotificationPreferences(), failed.preferences)

        failing.failing = false
        runBlocking {
            fixture.store.setEnabled(true)
            fixture.store.setPrivacy(NotificationPrivacy.REDUCED)
        }
        viewModel.retry()

        val recovered = viewModel.await("reintento") {
            !it.isLoading && it.errorMessage == null && it.preferences.enabled
        }
        assertEquals(NotificationPrivacy.REDUCED, recovered.preferences.privacy)
    }

    private fun fixture(dataStore: DataStore<Preferences>? = null): Fixture =
        Fixture(temporaryFolder.newFolder(), dataStore).also { fixtures += it }

    private inner class Fixture(folder: File, dataStore: DataStore<Preferences>?) {
        private val application: Context = ApplicationProvider.getApplicationContext()
        private val inertContext = InertContext(application, folder)
        private val localStore: LocalDataStore = LocalDataStore.create(inertContext, UNUSED_DATABASE_NAME)
        private val models = mutableListOf<NotificationViewModel>()

        val permission = PermissionContext(application)
        val configs = FakeShiftConfigs()
        val store: NotificationPreferencesStore = if (dataStore != null) {
            NotificationPreferencesStore(dataStore)
        } else {
            NotificationPreferencesStore(File(folder, "stage14-notification.preferences_pb"), ioScope)
        }
        private val runtime = NotificationRuntime(
            inertContext,
            localStore,
            store,
            WeatherRuntime(
                context = inertContext,
                preferences = WeatherPreferencesStore(File(folder, "stage14-weather.preferences_pb"), ioScope),
                objectives = localStore.objectives,
            ),
        )

        init {
            // Pausado, el runtime corta antes de publicar, programar o restaurar cualquier cosa.
            runBlocking { runtime.pauseForRestore() }
            check(runtime.isPausedForRestore)
        }

        fun viewModel(): NotificationViewModel = NotificationViewModel(
            preferencesStore = store,
            configs = configs,
            systemAccess = NotificationSystemAccess(permission),
            runtime = runtime,
        ).also { models += it }

        fun release() {
            models.forEach { it.viewModelScope.cancel() }
            runtime.scope.cancel()
            localStore.close()
        }
    }

    private fun NotificationViewModel.await(
        what: String,
        predicate: (NotificationUiState) -> Boolean,
    ): NotificationUiState = runBlocking {
        withTimeoutOrNull(TIMEOUT_MILLIS) { uiState.first(predicate) }
            ?: throw AssertionError("No se llegó a «$what». Estado final: ${uiState.value}")
    }

    private fun NotificationViewModel.awaitSaved(
        what: String,
        predicate: (NotificationUiState) -> Boolean,
    ): NotificationUiState = await(what) {
        !it.isSaving && it.errorMessage == null && it.infoMessage == "Configuración guardada." && predicate(it)
    }

    private fun shift(id: UUID): Shift = Shift(
        id = id,
        startAt = Instant.parse("2026-09-01T22:00:00Z"),
        endAt = Instant.parse("2026-09-02T10:00:00Z"),
        zoneId = ZoneId.of("America/Argentina/Cordoba"),
        localStartDate = LocalDate.of(2026, 9, 1),
        objectiveNameSnapshot = "Objetivo ficticio",
        objectiveAbbreviationSnapshot = "QA",
        objectiveAddressSnapshot = null,
        startTimeSnapshot = LocalTime.of(19, 0),
        endTimeSnapshot = LocalTime.of(7, 0),
        colorArgbSnapshot = 0xff336699.toInt(),
        position = null,
        status = ShiftStatus.PLANNED,
        sourceObjectiveId = UUID(0L, 1_402L),
        createdAt = Instant.parse("2026-08-15T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-15T00:00:00Z"),
    )

    /** Sólo cambia la respuesta del permiso de avisos; el resto llega al contexto real. */
    private class PermissionContext(base: Context) : ContextWrapper(base) {
        @Volatile var postNotifications: Int = PackageManager.PERMISSION_DENIED

        override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
            if (permission == Manifest.permission.POST_NOTIFICATIONS) {
                postNotifications
            } else {
                super.checkPermission(permission, pid, uid)
            }
    }

    /**
     * No es la aplicación: así la publicación local nunca queda habilitada y la lista de avisos
     * ocultos sale vacía sin consultar la base. Cualquier archivo iría a la carpeta temporal.
     */
    private class InertContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = File(root, name)
    }

    private class FakeShiftConfigs : ShiftNotificationConfigRepository {
        val stored = MutableStateFlow<Map<UUID, ShiftNotificationConfig>>(emptyMap())
        val replaced = CopyOnWriteArrayList<ShiftNotificationConfig>()
        val cleared = CopyOnWriteArrayList<UUID>()
        @Volatile var observeFailure: Exception? = null
        @Volatile var writeFailure: Exception? = null

        override fun observeAll(): Flow<List<ShiftNotificationConfig>> = stored.map { it.values.toList() }

        override fun observeForShift(shiftId: UUID): Flow<ShiftNotificationConfig?> = flow {
            observeFailure?.let { throw it }
            emitAll(stored.map { it[shiftId] })
        }

        override suspend fun getForShift(shiftId: UUID): ShiftNotificationConfig? = stored.value[shiftId]

        override suspend fun replace(config: ShiftNotificationConfig) {
            writeFailure?.let { throw it }
            replaced += config
            stored.update { it + (config.shiftId to config) }
        }

        override suspend fun clear(shiftId: UUID) {
            writeFailure?.let { throw it }
            cleared += shiftId
            stored.update { it - shiftId }
        }
    }

    /** Almacén en memoria cuya lectura falla (sin ser un error de disco) hasta que se lo habilita. */
    private class FailingPreferences : DataStore<Preferences> {
        @Volatile var failing = true
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = flow {
            if (failing) throw IllegalStateException("Fallo ficticio de lectura")
            emitAll(state)
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val UNUSED_DATABASE_NAME = "stage14-notification-unused.db"
        val SHIFT_A: UUID = UUID.fromString("00000000-0000-0000-0000-000000001411")
        val SHIFT_B: UUID = UUID.fromString("00000000-0000-0000-0000-000000001412")
        val FAKE_SOUND: Uri = Uri.parse("content://stage14.ficticio/sonido-elegido")
    }
}
