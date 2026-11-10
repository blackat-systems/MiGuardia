package com.blackatsystems.miguardia.stage14

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.backup.BackupCreateOptions
import com.blackatsystems.miguardia.backup.BackupRestoreJournal
import com.blackatsystems.miguardia.backup.LocalBackupCoordinator
import com.blackatsystems.miguardia.backup.PortablePreferencesGateway
import com.blackatsystems.miguardia.backup.RestoreChoice
import com.blackatsystems.miguardia.backup.RestoreJournalPhase
import com.blackatsystems.miguardia.backup.StagedBackupSource
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.backup.BackupContainer
import com.blackatsystems.miguardia.core.domain.backup.BackupDatabaseSnapshot
import com.blackatsystems.miguardia.core.domain.backup.BackupPhotoMode
import com.blackatsystems.miguardia.core.domain.backup.BackupPreference
import com.blackatsystems.miguardia.core.domain.backup.BackupPreferenceType
import com.blackatsystems.miguardia.core.domain.backup.BackupRecord
import com.blackatsystems.miguardia.core.domain.backup.BackupValue
import com.blackatsystems.miguardia.core.domain.backup.InvalidBackupException
import com.blackatsystems.miguardia.notifications.NotificationPreferences
import com.blackatsystems.miguardia.notifications.NotificationPreferencesStore
import com.blackatsystems.miguardia.profile.GuardProfileStore
import com.blackatsystems.miguardia.ui.summary.SummaryPreferencesStore
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import java.io.Closeable
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Las tres opciones del aviso permanente viajan en las Copias (etapa 15): «Mostrar siempre la próxima
 * jornada», «Mostrar en la pantalla bloqueada» y «Contar hasta el final durante la jornada».
 *
 * Cada prueba trabaja en una carpeta temporal propia, con su base, sus preferencias y su registro de
 * recuperación: no abre la base ni las preferencias de la app QA, y borra todo al terminar.
 */
@RunWith(AndroidJUnit4::class)
class Stage14PermanentNotificationCopiesTest {
    @Test
    fun newCopyCarriesTheThreeChoicesAndRestoringItBringsThemBack() = runBlocking<Unit> {
        Sandbox("origen").use { source ->
            Sandbox("destino").use { target ->
                source.store.backups.replace(source.store.backups.capture().withObjective("Copia nueva"))
                source.notifications.setAlwaysShowNext(true)
                source.notifications.setShowOnLockScreen(false)
                source.notifications.setCountdownWhileActive(false)
                val database = source.store.backups.capture()
                val sourcePreferences = source.gateway.capture(database)
                assertEquals(20, sourcePreferences.size)
                assertEquals(listOf("true", "false", "false"), sourcePreferences.addedChoices())

                val copy = File(target.root, "copia-nueva.miguardia-backup")
                source.coordinator().prepareBackup(BackupCreateOptions(includePhotos = false, password = null))
                    .use { prepared -> prepared.file.copyTo(copy) }
                BackupContainer.extract(copy, File(target.root, "leer-copia"), null).use { extracted ->
                    assertEquals(sourcePreferences, extracted.payload.preferences)
                }

                // El destino tiene las tres opciones de fábrica: restaurar tiene que cambiarlas.
                assertEquals(FACTORY_CHOICES, target.gateway.capture(target.store.backups.capture()).addedChoices())
                val coordinator = target.coordinator()
                coordinator.openSource(StagedBackupSource(copy, BackupContainer.readHeader(copy)), null).use { session ->
                    assertEquals(20, session.preview.incomingPreferenceCount)
                    coordinator.apply(
                        session,
                        RestoreChoice.REPLACE_ALL,
                        emptyList(),
                        LocalBackupCoordinator.REPLACE_CONFIRMATION,
                    )
                }

                val restored = target.notifications.current()
                assertTrue(restored.alwaysShowNext)
                assertFalse(restored.showOnLockScreen)
                assertFalse(restored.countdownWhileActive)
                assertEquals(database, target.store.backups.capture())
                assertEquals(sourcePreferences, target.gateway.capture(database))
                assertFalse(target.journalDirectory.exists())
            }
        }
    }

    @Test
    fun earlierCopyOfSeventeenPreferencesIsRestoredAndLeavesTheThreeChoicesAtFactoryValue() = runBlocking<Unit> {
        Sandbox("anterior").use { phone ->
            // En este teléfono la persona ya había cambiado las tres opciones.
            phone.notifications.setAlwaysShowNext(true)
            phone.notifications.setShowOnLockScreen(false)
            phone.notifications.setCountdownWhileActive(false)
            val database = phone.store.backups.capture().withObjective("Copia anterior")
            val earlier = phone.gateway.capture(database).withoutAddedChoices()
            assertEquals(17, earlier.size)
            val copy = phone.writeCopy("copia-anterior", database, earlier)
            BackupContainer.extract(copy, File(phone.root, "leer-copia"), null).use { extracted ->
                assertEquals(earlier, extracted.payload.preferences)
            }

            val coordinator = phone.coordinator()
            coordinator.openSource(StagedBackupSource(copy, BackupContainer.readHeader(copy)), null).use { session ->
                // La comparación y la vista previa trabajan con la lista completa.
                assertEquals(20, session.incomingPreferences.size)
                assertEquals(FACTORY_CHOICES, session.incomingPreferences.addedChoices())
                assertEquals(20, session.preview.incomingPreferenceCount)
                assertEquals(3, session.preview.currentPreferencesRemovedOrReplaced)
                coordinator.apply(
                    session,
                    RestoreChoice.REPLACE_ALL,
                    emptyList(),
                    LocalBackupCoordinator.REPLACE_CONFIRMATION,
                )
            }

            val restored = phone.notifications.current()
            val factory = NotificationPreferences()
            assertEquals(factory.alwaysShowNext, restored.alwaysShowNext)
            assertEquals(factory.showOnLockScreen, restored.showOnLockScreen)
            assertEquals(factory.countdownWhileActive, restored.countdownWhileActive)
            assertFalse(restored.alwaysShowNext)
            assertTrue(restored.showOnLockScreen)
            assertTrue(restored.countdownWhileActive)
            assertEquals(database, phone.store.backups.capture())
            assertEquals(earlier.withFactoryChoices(), phone.gateway.capture(database))
            assertFalse(phone.journalDirectory.exists())
        }
    }

    @Test
    fun copyWithAnUnknownPreferenceIsRejectedWithoutWritingAnything() = runBlocking<Unit> {
        Sandbox("desconocida").use { phone ->
            phone.notifications.setAlwaysShowNext(true)
            phone.notifications.setShowOnLockScreen(false)
            phone.notifications.setCountdownWhileActive(false)
            val databaseBefore = phone.store.backups.capture()
            val preferencesBefore = phone.gateway.capture(databaseBefore)
            val incomingDatabase = databaseBefore.withObjective("No debe guardarse")
            val unknown = BackupPreference("unknown.device_state", BackupPreferenceType.TEXT, listOf("forbidden"))
            val coordinator = phone.coordinator()

            listOf(
                "actual-con-desconocida" to preferencesBefore + unknown,
                "anterior-con-desconocida" to preferencesBefore.withoutAddedChoices() + unknown,
            ).forEach { (name, preferences) ->
                val copy = phone.writeCopy(name, incomingDatabase, preferences.sortedBy(BackupPreference::key))
                val source = StagedBackupSource(copy, BackupContainer.readHeader(copy))
                val failure = try {
                    coordinator.openSource(source, null).close()
                    null
                } catch (error: InvalidBackupException) {
                    error
                } finally {
                    source.close()
                }

                assertTrue("La copia $name tenía que rechazarse", failure != null)
                assertEquals(databaseBefore, phone.store.backups.capture())
                assertEquals(preferencesBefore, phone.gateway.capture(databaseBefore))
                assertFalse(phone.journalDirectory.exists())
            }
        }
    }

    @Test
    fun restoreInterruptedBeforeTheChoicesExistedIsRolledBackAfterUpdating() = runBlocking<Unit> {
        Sandbox("interrumpida").use { phone ->
            phone.store.backups.replace(phone.store.backups.capture().withObjective("Antes de restaurar"))
            val previousDatabase = phone.store.backups.capture()
            val desiredDatabase = previousDatabase.withObjective("Copia a medio restaurar")
            // La versión anterior de la app escribió su registro de recuperación con 17 preferencias.
            val previous = phone.gateway.capture(previousDatabase).withoutAddedChoices()
            val desired = previous.withZoom(150)
            BackupRestoreJournal(phone.context).apply {
                prepare(
                    oldDatabase = previousDatabase,
                    oldPreferences = previous,
                    oldPhotos = emptyList(),
                    newDatabase = desiredDatabase,
                    newPreferences = desired,
                    newPhotos = emptyList(),
                    createdAtEpochMillis = CREATED_AT,
                    zoneId = ZONE,
                )
                writePhase(RestoreJournalPhase.SWAPPED)
            }
            // Se cortó con la base ya cambiada; después se actualizó la app y se volvió a abrir.
            phone.store.backups.replace(desiredDatabase)
            phone.reopenStore()

            assertTrue(phone.coordinator().recoverAtStartup())

            assertEquals(previousDatabase, phone.store.backups.capture())
            assertEquals(previous.withFactoryChoices(), phone.gateway.capture(previousDatabase))
            assertFalse(phone.journalDirectory.exists())
        }
    }

    @Test
    fun restoreVerifiedBeforeTheChoicesExistedIsConfirmedAfterUpdating() = runBlocking<Unit> {
        Sandbox("verificada").use { phone ->
            phone.store.backups.replace(phone.store.backups.capture().withObjective("Antes de restaurar"))
            val previousDatabase = phone.store.backups.capture()
            val desiredDatabase = previousDatabase.withObjective("Copia ya verificada")
            val previous = phone.gateway.capture(previousDatabase).withoutAddedChoices()
            val desired = previous.withZoom(150)
            BackupRestoreJournal(phone.context).apply {
                prepare(
                    oldDatabase = previousDatabase,
                    oldPreferences = previous,
                    oldPhotos = emptyList(),
                    newDatabase = desiredDatabase,
                    newPreferences = desired,
                    newPhotos = emptyList(),
                    createdAtEpochMillis = CREATED_AT,
                    zoneId = ZONE,
                )
                writePhase(RestoreJournalPhase.VERIFIED)
            }
            // La versión anterior ya había dejado la base y las preferencias de la copia.
            phone.store.backups.replace(desiredDatabase)
            phone.gateway.replace(desired, desiredDatabase)
            phone.reopenStore()

            assertTrue(phone.coordinator().recoverAtStartup())

            assertEquals(desiredDatabase, phone.store.backups.capture())
            assertEquals(desired.withFactoryChoices(), phone.gateway.capture(desiredDatabase))
            assertFalse(phone.journalDirectory.exists())
        }
    }

    private fun List<BackupPreference>.addedChoices(): List<String> =
        ADDED_KEYS.map { key -> single { it.key == key }.values.single() }

    private fun List<BackupPreference>.withoutAddedChoices(): List<BackupPreference> =
        filterNot { it.key in ADDED_KEYS }

    private fun List<BackupPreference>.withFactoryChoices(): List<BackupPreference> =
        (
            withoutAddedChoices() + ADDED_KEYS.zip(FACTORY_CHOICES).map { (key, value) ->
                BackupPreference(key, BackupPreferenceType.BOOLEAN, listOf(value))
            }
            ).sortedBy(BackupPreference::key)

    private fun List<BackupPreference>.withZoom(percent: Int): List<BackupPreference> = map { preference ->
        if (preference.key == "display.zoom_percent") preference.copy(values = listOf(percent.toString())) else preference
    }

    private fun BackupDatabaseSnapshot.withObjective(name: String): BackupDatabaseSnapshot = copy(
        tables = tables.map { table ->
            if (table.name == "objectives") table.copy(records = listOf(objectiveRecord(name))) else table
        },
    )

    private fun objectiveRecord(name: String): BackupRecord = BackupRecord(
        listOf(
            BackupValue.Text(OBJECTIVE_ID),
            BackupValue.Text(name),
            BackupValue.Text("FIC"),
            BackupValue.Null,
            BackupValue.Null,
            BackupValue.Integer(1),
            BackupValue.Integer(CREATED_AT),
            BackupValue.Integer(CREATED_AT),
            BackupValue.Null,
            BackupValue.Null,
        ),
    )

    /** Un «teléfono» de prueba: carpeta, base, preferencias y registro de recuperación propios. */
    private class Sandbox(label: String) : Closeable {
        private val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "stage14-copies-$label-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val context = SandboxContext(base, root, "stage14-copies-${UUID.randomUUID()}")
        private val job = SupervisorJob()
        private val scope = CoroutineScope(job + Dispatchers.IO)
        val notifications = NotificationPreferencesStore(File(root, "notifications.preferences_pb"), scope)
        val gateway = PortablePreferencesGateway(
            context = context,
            guardProfile = GuardProfileStore(File(root, "profile.preferences_pb"), scope),
            summaryStore = SummaryPreferencesStore(File(root, "summary.preferences_pb"), scope),
            notificationStore = notifications,
            weatherStore = WeatherPreferencesStore(File(root, "weather.preferences_pb"), scope),
        )
        private val databaseName = "stage14-copies-${UUID.randomUUID()}.db"
        var store: LocalDataStore = try {
            LocalDataStore.create(context, databaseName)
        } catch (error: Throwable) {
            // Sin base no hay prueba ni `use` que cierre: la carpeta temporal no puede quedar.
            job.cancel()
            context.removeOwnSharedPreferences()
            root.deleteRecursively()
            throw error
        }
            private set
        val journalDirectory: File get() = File(context.noBackupFilesDir, "miguardia_backup_restore")

        fun coordinator() = LocalBackupCoordinator(
            context = context,
            localDataStore = store,
            preferences = gateway,
            clock = CLOCK,
            zoneId = ZONE,
        )

        /** Como al volver a abrir la app después de un corte. */
        fun reopenStore() {
            store.close()
            store = LocalDataStore.create(context, databaseName)
        }

        fun writeCopy(name: String, database: BackupDatabaseSnapshot, preferences: List<BackupPreference>): File {
            val copy = File(root, "$name.miguardia-backup")
            BackupContainer.create(
                target = copy,
                workingDirectory = File(root, "armar-$name"),
                backupId = UUID.randomUUID(),
                createdAtEpochMillis = CREATED_AT,
                zoneId = ZONE,
                database = database,
                preferences = preferences,
                photoMode = BackupPhotoMode.INCLUDED,
                photoAssets = emptyList(),
                password = null,
            )
            return copy
        }

        override fun close() {
            store.close()
            runBlocking { job.cancelAndJoin() }
            context.removeOwnSharedPreferences()
            // La base y su bloqueo viven dentro de la carpeta temporal. Si Android los hubiera puesto
            // en las carpetas de la app, se borran por su nombre propio de esta prueba.
            base.deleteDatabase(databaseName)
            File(base.cacheDir, "$databaseName.lck").delete()
            check(root.deleteRecursively()) { "Quedaron archivos de la prueba en ${root.name}" }
        }
    }

    /** Todo lo que la app escribe por este contexto queda dentro de la carpeta temporal. */
    private class SandboxContext(
        base: Context,
        private val root: File,
        private val prefix: String,
    ) : ContextWrapper(base) {
        private val ownPreferences = mutableSetOf<String>()

        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(root, "files").also { it.mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no-backup").also { it.mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").also { it.mkdirs() }
        override fun getDatabasePath(name: String): File = File(File(root, "databases").also { it.mkdirs() }, name)

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val ownName = "$prefix-$name"
            ownPreferences += ownName
            return baseContext.getSharedPreferences(ownName, mode)
        }

        /** Las preferencias de apariencia son el único archivo fuera de la carpeta: se borra entero. */
        fun removeOwnSharedPreferences() {
            ownPreferences.forEach { baseContext.deleteSharedPreferences(it) }
        }
    }

    private companion object {
        const val CREATED_AT = 1_788_131_400_000L
        const val OBJECTIVE_ID = "11111111-1111-4111-8111-111111111111"
        val ZONE: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
        val CLOCK: Clock = Clock.fixed(Instant.ofEpochMilli(CREATED_AT), ZONE)

        /** En el orden en que quedan dentro de la Copia (alfabético por clave). */
        val ADDED_KEYS = listOf(
            "notifications.always_show_next",
            "notifications.countdown_while_active",
            "notifications.show_on_lock_screen",
        )

        /** Valores de una instalación nueva, en el mismo orden. */
        val FACTORY_CHOICES = listOf("false", "true", "true")
    }
}
