package com.blackatsystems.miguardia.upgrade

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.MiGuardiaApplication
import com.blackatsystems.miguardia.backup.BackupCreateOptions
import com.blackatsystems.miguardia.backup.BackupPhotoFiles
import com.blackatsystems.miguardia.backup.LocalBackupCoordinator
import com.blackatsystems.miguardia.backup.PortablePreferencesGateway
import com.blackatsystems.miguardia.backup.RestoreChoice
import com.blackatsystems.miguardia.backup.StagedBackupSource
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.backup.BackupContainer
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupSchemaV6
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupSchemaV8
import com.blackatsystems.miguardia.core.domain.backup.upgradeSupportedBackupSchema
import com.blackatsystems.miguardia.notifications.NotificationPreferencesStore
import com.blackatsystems.miguardia.profile.GuardProfileStore
import com.blackatsystems.miguardia.ui.summary.SummaryPreferencesStore
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in DATA PREPARATION ONLY for MAIN's disposable emulator upgrade rehearsal.
 *
 * Stage 9 uses the same pure versioned constructor as the JVM regression.
 * Produces a synthetic legacy V6 container and a distinct current V8 roundtrip.
 * Historical method name and all opt-in API33/AVD/consent guards are preserved.
 * This does not execute a Room migration or an installed application upgrade.
 * Writes only independent Room databases, stores and synthetic PNG under a new private
 * fixture root. The ordinary backup coordinator exports and restores it in isolation.
 * MAIN then imports the exported backup through the signed origin application's UI,
 * BEFORE taking its baseline. This test never accesses the signed application's sandbox,
 * restores the live QA database, changes system permissions or initializes a beta identity.
 *
 * Ordinary suites skip this method. The ADB serial argument is an operator attestation;
 * runtime also checks independently observed AVD/hardware identity, model, fingerprint,
 * API and QA package. No assertion claims that Android exposes the host's ADB serial.
 */
@RunWith(AndroidJUnit4::class)
class PublicUpgradeBackupFixtureTest {
    @Test
    fun preparesCompleteSyntheticBackupForExplicitDisposableUpgradeRehearsal() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("upgradeFixtureConsent") == CONSENT)
        val base = instrumentation.targetContext.applicationContext
        check(base.packageName == QA_PACKAGE)
        check(instrumentation.context.packageName == "$QA_PACKAGE.test")
        check(Build.VERSION.SDK_INT == 33)
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(arguments.getString("upgradeFixtureAdbSerial") == "emulator-5582")
        check(arguments.getString("upgradeFixtureAvd") == AVD_NAME)
        check(readProperty("ro.boot.qemu.avd_name") == AVD_NAME)
        check(readProperty("ro.kernel.qemu") == "1")
        val hardwareSerial = readProperty("ro.serialno")
        check(hardwareSerial.isNotBlank() && hardwareSerial != "unknown")
        check(arguments.getString("upgradeFixtureHardwareSerial") == hardwareSerial)
        check(arguments.getString("upgradeFixtureModel") == Build.MODEL)
        check(arguments.getString("upgradeFixtureFingerprint") == Build.FINGERPRINT)
        val day = LocalDate.parse(requireNotNull(arguments.getString("upgradeFixtureDate")))
        check(day == LocalDate.now(ZONE)) { "Fixture date must match the emulator's local day" }

        // Hash-only attestation, never open the live SQLite database or its stores.
        // Any unrelated writer makes preparation fail, rather than hiding a mutation.
        val liveBefore = awaitStablePrivateFootprint(base)
        val outputRoot = File(base.filesDir, OUTPUT_DIRECTORY)
        check(!outputRoot.exists()) { "Never overwrite an earlier preparation; MAIN must inspect it first" }
        check(outputRoot.mkdir())
        val root = File(base.cacheDir, "public-upgrade-fixture-${UUID.randomUUID()}")
        check(root.mkdir())
        val scopeJob = SupervisorJob()
        val scope = CoroutineScope(scopeJob + Dispatchers.IO)
        val sourceContext = FixtureContext(base, File(root, "source"), "source-${UUID.randomUUID()}")
        val targetContext = FixtureContext(base, File(root, "roundtrip"), "roundtrip-${UUID.randomUUID()}")
        check(sourceContext.getDatabasePath("fixture.db").canonicalPath.startsWith(root.canonicalPath + File.separator))
        check(targetContext.getDatabasePath("fixture.db").canonicalPath.startsWith(root.canonicalPath + File.separator))
        check(sourceContext.getDatabasePath("fixture.db") != base.getDatabasePath("miguardia-v2.db"))
        var source: LocalDataStore? = null
        var target: LocalDataStore? = null
        var completedEvidence: JSONObject? = null
        try {
            val photo = createSyntheticPhoto(sourceContext)
            val origin = UpgradeFixtureSnapshots.sourceV6(day, photo.length())
            val expected = UpgradeFixtureSnapshots.targetV8(day, photo.length())
            MiGuardiaBackupSchemaV6.requireValid(origin)
            MiGuardiaBackupSchemaV8.requireValid(expected)
            assertEquals(27, origin.tables.size)
            assertEquals(29, expected.tables.size)
            assertTrue(origin.tables.all { it.records.isNotEmpty() })
            assertTrue(expected.table("shift_note_attachments").records.isEmpty())
            assertTrue(expected.table("shift_situations").records.isEmpty())
            assertEquals(expected, upgradeSupportedBackupSchema(origin))
            val sourceStore = LocalDataStore.create(sourceContext, "fixture.db")
            source = sourceStore
            sourceStore.backups.validateCandidate(expected)
            sourceStore.backups.replace(expected)
            sourceStore.backups.verifyLiveAfterReopen(expected)

            val sourcePreferences = preferencesGateway(sourceContext, scope)
            val preferences = sourcePreferences.capture(expected).map { item ->
                when (item.key) {
                    "profile.display_name" -> item.copy(values = listOf("Ensayo ficticio"))
                    "display.zoom_percent" -> item.copy(values = listOf("150"))
                    "summary.intro_seen" -> item.copy(values = listOf("true"))
                    // No weather requests or notification/alarm side effects on import.
                    "weather.enabled", "notifications.enabled", "notifications.precise_timing" ->
                        item.copy(values = listOf("false"))
                    else -> item
                }
            }
            check(preferences.size == 17)
            check(preferences.none { it.key.contains("beta") || it.key.contains("lock") || it.key.contains("onboarding") })
            sourcePreferences.replace(preferences, expected)
            check(preferences == sourcePreferences.capture(expected)) { "Portable preferences did not round-trip" }
            BackupPhotoFiles(sourceContext).verify(expected, mapOf(PHOTO_STORAGE_KEY to sha256(photo)))
            val fixedClock = Clock.fixed(day.atTime(12, 0).atZone(ZONE).toInstant(), ZONE)
            val coordinator = LocalBackupCoordinator(sourceContext, sourceStore, sourcePreferences, clock = fixedClock, zoneId = ZONE)
            val originOutput = File(outputRoot, "MiGuardia_origen_RoomV6_sintetico.miguardia-backup")
            LegacyUpgradeFixtureContainer.write(
                originOutput, LegacyUpgradeFixtureContainer.entries(origin, preferences, photo, fixedClock.millis()),
                fixedClock.millis(),
            )
            val originHash = sha256(originOutput)
            BackupContainer.extract(originOutput, File(root, "verify-origin-v6"), null).use { extracted ->
                assertEquals(6, extracted.manifest.roomVersion)
                assertEquals(origin.tables.associate { it.name to it.records.size }, extracted.manifest.tableCounts)
                assertEquals(expected, extracted.payload.database)
                assertEquals(preferences, extracted.payload.preferences)
                assertEquals(sha256(photo), sha256(extracted.photoFile(PHOTO_STORAGE_KEY)))
            }
            check(originHash == sha256(originOutput))
            val output = File(outputRoot, "MiGuardia_destino_RoomV8_roundtrip.miguardia-backup")
            coordinator.prepareBackup(BackupCreateOptions(includePhotos = true, password = null)).use { prepared ->
                check(!output.exists())
                prepared.file.inputStream().use { input ->
                    output.outputStream().use { sink -> input.copyTo(sink) }
                }
                check(output.length() == prepared.file.length())
                check(sha256(output) == sha256(prepared.file))
            }

            BackupContainer.extract(output, File(root, "verify-container"), null).use { extracted ->
                assertEquals(8, extracted.manifest.roomVersion)
                check(expected == extracted.payload.database) { "Extracted logical database differs" }
                check(preferences == extracted.payload.preferences) { "Extracted portable preferences differ" }
                check(extracted.payload.photos.size == 1)
            }

            // The same coordinator used by the app imports into a SECOND empty sandbox.
            // This is not the installed-upgrade claim: that belongs to MAIN's later UI test.
            val targetStore = LocalDataStore.create(targetContext, "fixture.db")
            target = targetStore
            val targetPreferences = preferencesGateway(targetContext, scope)
            val restore = LocalBackupCoordinator(targetContext, targetStore, targetPreferences, clock = fixedClock, zoneId = ZONE)
            val importFile = File(root, "roundtrip-input.miguardia-backup")
            originOutput.copyTo(importFile)
            val staged = StagedBackupSource(importFile, BackupContainer.readHeader(importFile))
            val beforePreview = targetStore.backups.capture()
            val preferencesBeforePreview = targetPreferences.capture(beforePreview)
            restore.openSource(staged, null).use { session ->
                check(session.preview.destinationEmpty)
                assertEquals(beforePreview, targetStore.backups.capture())
                assertEquals(preferencesBeforePreview, targetPreferences.capture(beforePreview))
                check(!File(targetContext.filesDir, "schedule_photos/$PHOTO_STORAGE_KEY").exists())
                restore.apply(session, RestoreChoice.REPLACE_ALL, emptyList(), LocalBackupCoordinator.REPLACE_CONFIRMATION)
            }
            check(expected == targetStore.backups.capture()) { "Restored logical database differs" }
            check(preferences == targetPreferences.capture(expected)) { "Restored portable preferences differ" }
            targetStore.backups.verifyLiveAfterReopen(expected)
            BackupPhotoFiles(targetContext).verify(expected, mapOf(PHOTO_STORAGE_KEY to sha256(photo)))
            val restoredPhoto = File(targetContext.filesDir, "schedule_photos/$PHOTO_STORAGE_KEY")
            BitmapFactory.decodeFile(restoredPhoto.path).also { bitmap ->
                checkNotNull(bitmap)
                check(bitmap.width == PHOTO_WIDTH && bitmap.height == PHOTO_HEIGHT)
                bitmap.recycle()
            }
            check(originHash == sha256(originOutput)) { "The explicit V6 source changed during restore" }
            val counts = JSONObject()
            expected.tables.forEach { table -> counts.put(table.name, table.records.size) }
            val originCounts = JSONObject()
            origin.tables.forEach { table -> originCounts.put(table.name, table.records.size) }
            completedEvidence = JSONObject()
                .put("format", 2).put("fictitious", true)
                .put("verdict", "SYNTHETIC_V6_BACKUP_ADAPTED_AND_RESTORED_AS_V8_IN_ISOLATION")
                .put("room_migration_executed", false)
                .put("origin_container_encoding", "LEGACY_UNSEALED_V1_TEST_ONLY")
                .put("origin_room_version", origin.roomVersion).put("origin_room_identity", origin.roomIdentityHash)
                .put("destination_room_version", expected.roomVersion).put("destination_room_identity", expected.roomIdentityHash)
                .put("origin_tables", origin.tables.size).put("destination_tables", expected.tables.size)
                .put("origin_table_counts", originCounts).put("preview_without_logical_mutation", true)
                .put("origin_backup_sha256", originHash).put("origin_backup_bytes", originOutput.length())
                .put("installed_upgrade_executed", false)
                .put("adb_serial_operator_attestation", "emulator-5582")
                .put("avd_runtime_verified", AVD_NAME).put("api", Build.VERSION.SDK_INT)
                .put("date", day.toString()).put("room_version", expected.roomVersion)
                .put("tables", counts).put("portable_preferences", preferences.size)
                .put("backup_sha256", sha256(output)).put("backup_bytes", output.length())
                .put("photo_sha256", sha256(photo)).put("photo_width", PHOTO_WIDTH).put("photo_height", PHOTO_HEIGHT)
                .put("notifications_enabled", false).put("weather_enabled", false)
                .put("manual_shift_day", day.minusDays(3).toString())
                .put("automatic_shift_day", day.plusDays(1).toString())
                .put("independent_extra_day", day.minusDays(2).toString())
                .put("availability_day", day.toString())
                .put("explicit_day_off", day.plusDays(2).toString())
                .put("medical_leave_day", day.plusDays(3).toString())
                .put("vacation_day", day.plusDays(4).toString())
                .put("holiday_day", day.plusDays(5).toString())
        } finally {
            source?.close()
            target?.close()
            scopeJob.cancelAndJoin()
            sourceContext.removeOwnSharedPreferences()
            targetContext.removeOwnSharedPreferences()
            check(root.canonicalFile.parentFile == base.cacheDir.canonicalFile)
            check(root.name.startsWith("public-upgrade-fixture-"))
            check(root.deleteRecursively()) { "MAIN must inspect the retained fixture sandbox" }
            // Only outputRoot is intentionally retained. No published/shared document is removed.
        }
        check(liveBefore == privateFootprint(base)) { "Live QA private files changed; do not accept this preparation" }
        val evidence = checkNotNull(completedEvidence).put("qa_private_files_unchanged", true)
        File(outputRoot, "fixture-evidence.json").writeText(evidence.toString(2) + "\n", Charsets.UTF_8)
    }

    private fun readProperty(name: String): String {
        check(name in setOf("ro.boot.qemu.avd_name", "ro.kernel.qemu", "ro.serialno"))
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("getprop $name")
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText().trim() }
    }

    private fun preferencesGateway(context: FixtureContext, scope: CoroutineScope) = PortablePreferencesGateway(
        context,
        GuardProfileStore(File(context.filesDir, "profile.preferences_pb"), scope),
        SummaryPreferencesStore(File(context.filesDir, "summary.preferences_pb"), scope),
        NotificationPreferencesStore(File(context.filesDir, "notifications.preferences_pb"), scope),
        WeatherPreferencesStore(File(context.filesDir, "weather.preferences_pb"), scope),
    )

    private fun createSyntheticPhoto(context: Context): File {
        val photo = File(context.filesDir, "schedule_photos/$PHOTO_STORAGE_KEY")
        check(photo.parentFile!!.mkdirs())
        val bitmap = Bitmap.createBitmap(PHOTO_WIDTH, PHOTO_HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 45, 80); textSize = 36f }
            canvas.drawText("MIGUARDIA - ENSAYO FICTICIO", 35f, 70f, paint)
            canvas.drawText("Conservar esta imagen tras actualizar", 35f, 140f, paint)
            paint.textSize = 28f
            canvas.drawText("Datos de prueba. No contiene personas.", 35f, 230f, paint)
            canvas.drawText("Conservación lógica Room V6 a V8", 35f, 290f, paint)
            paint.color = Color.rgb(30, 120, 90)
            canvas.drawRect(35f, 350f, 765f, 430f, paint)
            photo.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        return photo
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private suspend fun awaitStablePrivateFootprint(base: Context): Map<String, String> = withTimeout(15_000) {
        val application = base as MiGuardiaApplication
        while (!application.startupRecoveryGate.isReady) delay(50)
        var previous = privateFootprint(base)
        var unchanged = 0
        while (unchanged < 8) {
            delay(250)
            val current = privateFootprint(base)
            unchanged = if (current == previous) unchanged + 1 else 0
            previous = current
        }
        // Stability is only a bounded start condition, not a lock. The exact final
        // comparison still rejects any concurrent mutation during preparation.
        previous
    }

    private fun privateFootprint(base: Context): Map<String, String> {
        val roots = listOf(
            base.filesDir,
            base.noBackupFilesDir,
            File(base.applicationInfo.dataDir, "shared_prefs"),
            base.getDatabasePath("miguardia-v2.db").parentFile!!,
        )
        val result = sortedMapOf<String, String>()
        roots.forEachIndexed { index, directory ->
            if (directory.exists()) {
                directory.walkTopDown().onEnter { child ->
                    check(child.canonicalPath == directory.canonicalPath ||
                        child.canonicalPath.startsWith(directory.canonicalPath + File.separator))
                    !(index == 0 && child.parentFile == directory && child.name == OUTPUT_DIRECTORY)
                }.filter { it.isFile }.forEach { file ->
                    check(file.canonicalPath.startsWith(directory.canonicalPath + File.separator))
                    val relative = "$index/" + file.relativeTo(directory).invariantSeparatorsPath
                    val nameHash = MessageDigest.getInstance("SHA-256").digest(relative.toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it.toInt() and 255) }
                    result[nameHash] = sha256(file)
                }
            }
        }
        return result
    }

    private class FixtureContext(base: Context, private val root: File, private val prefix: String) : ContextWrapper(base) {
        private val ownPreferences = mutableSetOf<String>()
        init { check(root.mkdirs()) }
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(root, "files").also { it.mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").also { it.mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").also { it.mkdirs() }
        override fun getDataDir(): File = root
        override fun getDatabasePath(name: String): File {
            check(name.matches(Regex("[A-Za-z0-9._-]+")))
            return File(File(root, "databases").also { it.mkdirs() }, name)
        }
        override fun databaseList(): Array<String> = File(root, "databases").list()?.map { it }.orEmpty().toTypedArray()
        override fun deleteDatabase(name: String): Boolean = SQLiteDatabase.deleteDatabase(getDatabasePath(name))
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            SQLiteDatabase.openDatabase(getDatabasePath(name).path, factory, SQLiteDatabase.CREATE_IF_NECESSARY)
        override fun openOrCreateDatabase(
            name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?,
        ): SQLiteDatabase = SQLiteDatabase.openDatabase(
            getDatabasePath(name).path, factory, SQLiteDatabase.CREATE_IF_NECESSARY, errorHandler,
        )
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val privateName = "public-upgrade-fixture-$prefix-$name"
            ownPreferences += privateName
            return baseContext.getSharedPreferences(privateName, mode)
        }
        fun removeOwnSharedPreferences() {
            ownPreferences.forEach { check(baseContext.deleteSharedPreferences(it)) }
        }
    }

    private companion object {
        const val CONSENT = "CREATE_FICTITIOUS_BACKUP_FOR_UPGRADE_API33"
        const val AVD_NAME = "MiGuardia_Public_Upgrade_API33_20260910"
        const val QA_PACKAGE = "com.blackatsystems.miguardia.qa"
        const val OUTPUT_DIRECTORY = "public-upgrade-fixture-export-v2"
        const val PHOTO_WIDTH = 800
        const val PHOTO_HEIGHT = 480
        val ZONE: ZoneId = ZoneId.of(UpgradeFixtureSnapshots.ZONE_ID)
        const val PHOTO_STORAGE_KEY = UpgradeFixtureSnapshots.PHOTO_STORAGE_KEY
    }
}
