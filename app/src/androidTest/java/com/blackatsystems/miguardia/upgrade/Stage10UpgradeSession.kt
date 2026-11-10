package com.blackatsystems.miguardia.upgrade

import android.content.Context
import android.graphics.BitmapFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.blackatsystems.miguardia.backup.PortablePreferencesGateway
import com.blackatsystems.miguardia.core.domain.backup.*
import com.blackatsystems.miguardia.notifications.NotificationPreferencesStore
import com.blackatsystems.miguardia.profile.GuardProfileStore
import com.blackatsystems.miguardia.ui.summary.SummaryPreferencesStore
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** Framework/ABI common to V6 and V8. Live target classes supply Room and preference code. */
class Stage10UpgradeSession(private val target: Context, private val test: Context,
    private val emulator: Stage10EmulatorContract.Scenario? = null,
    private val profileBaseline: Stage10EmulatorContract.ProfileBaseline? = null) {
    private var captureV8 = false
    private var expiryCriterion = false
    private var capturedAt = 0L
    private var seedCapture = false
    private val databaseFile = target.getDatabasePath("miguardia-v2.db")
    private val storeNames = linkedMapOf("profile" to "guard_profile.preferences_pb", "summary" to "summary_preferences.preferences_pb",
        "notifications" to "notification_preferences.preferences_pb", "weather" to "weather_preferences.preferences_pb")
    private val marker = File(target.noBackupFilesDir, if (emulator == null) Stage10UpgradeContract.MARKER else Stage10EmulatorContract.MARKER)
    private val photo = File(target.filesDir, "schedule_photos/${Stage10UpgradeContract.PHOTO_KEY}")
    private val display = File(target.applicationInfo.dataDir, "shared_prefs/miguardia_display_preferences.xml")
    private val selected: Map<String, File> get() = buildMap {
        storeNames.forEach { (_, name) -> put("files/" + target.preferencesDataStoreFile(name).relativeTo(target.filesDir).invariantSeparatorsPath, target.preferencesDataStoreFile(name)) }
        put("files/schedule_photos/${photo.name}", photo)
        put("shared_prefs/${display.name}", display)
        put("no_backup/${marker.name}", marker)
        if (emulator != null && !seedCapture) put(Stage10EmulatorContract.ONBOARDING, File(target.applicationInfo.dataDir, Stage10EmulatorContract.ONBOARDING))
    }
    private fun asset(name: String, expected: String): ByteArray = test.assets.open("stage10/$name").use { it.readBytes() }.also { check(digest(it) == expected) }

    suspend fun run(request: Stage10UpgradeContract.Request): File {
        captureV8 = request.room == 8
        expiryCriterion = request.operation == Stage10UpgradeContract.EXPIRY_OPERATION
        capturedAt = System.currentTimeMillis()
        seedCapture = request.operation == "seed-v6"
        emulator?.requireTime(request.operation, capturedAt)
        profileBaseline?.let { baseline ->
            require(request.operation == "capture-v8")
            val info = target.packageManager.getPackageInfo(target.packageName, 0)
            baseline.requireInstalled(requireNotNull(emulator), info.firstInstallTime, info.lastUpdateTime)
        }
        val sourceBytes = asset("source-v6.bin", Stage10UpgradeContract.SOURCE)
        val preferenceBytes = asset("preferences.bin", Stage10UpgradeContract.PREFERENCES)
        val png = asset("synthetic.png", Stage10UpgradeContract.PHOTO)
        val base = sourceBytes.inputStream().use(Stage10UpgradeContract::fixture)
        val source = if (emulator == null) base else Stage10EmulatorContract.fixture(base, emulator)
        val originalPreferences = preferenceBytes.inputStream().use(Stage10UpgradeContract::preferences)
        val wanted = if (emulator == null) originalPreferences else Stage10EmulatorContract.preferences(originalPreferences)
        requireSingleTargetProcess()
        // Instrumentation runs with target UID: use its cache, not another UID's
        // test-package data directory. Cache is explicitly outside selected data.
        val output = File(target.cacheDir, "stage10-captures/${request.captureId}")
        val bundle = File(target.cacheDir, "stage10-${request.captureId}.zip")
        check(!output.exists() && !bundle.exists()) { "Never overwrite a capture" }
        if (request.operation == "seed-v6") seed(source, wanted, png)
        check(marker.isFile && marker.readText() == (emulator?.marker() ?: Stage10UpgradeContract.marker()))
        if (emulator != null && !seedCapture) requireOnboarding()
        requireSelection()
        val before = footprint()
        val room = TargetRoom(target)
        val actualPreferences: List<BackupPreference>
        var checkpoint: List<Int> = emptyList()
        try {
            val db = room.database
            db.beginTransaction()
            try {
            check(db.version == request.room)
            val identity = db.query("SELECT identity_hash FROM room_master_table WHERE id=42").use { c -> check(c.moveToFirst()); c.getString(0) }
            check(identity == if (request.room == 6) Stage10UpgradeContract.ID6 else Stage10UpgradeContract.ID8)
            val actual = readOriginalTables(db, source).copy(roomVersion = request.room, roomIdentityHash = identity)
            // No personal/unexpected row is exported; compare actual reads, never substitute expected rows.
            check(actual.copy(roomVersion = 6, roomIdentityHash = Stage10UpgradeContract.ID6) == source)
            if (request.room == 8) listOf("shift_note_attachments", "shift_situations").forEach { table ->
                check(db.query("SELECT COUNT(*) FROM $table").use { c -> c.moveToFirst(); c.getLong(0) } == 0L)
            }
            check(db.query("PRAGMA foreign_key_check").use { !it.moveToFirst() })
            check(db.query("PRAGMA integrity_check").use { c -> c.moveToFirst(); c.getString(0) } == "ok")
            actualPreferences = preferences(actual, wanted, seed = false)
            db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            checkpoint = db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { c -> check(c.moveToFirst()); (0..2).map(c::getInt) }
            check(checkpoint[0] == 0)
        } finally { room.close() }
        check(!File(databaseFile.path + "-wal").exists() || File(databaseFile.path + "-wal").length() == 0L)
        requireSelection()
        requireSingleTargetProcess()
        check(footprint() == before) { "Private files changed during capture" }
        check(hash(photo) == Stage10UpgradeContract.PHOTO)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(photo.path))
        try { check(bitmap.width == 800 && bitmap.height == 480) } finally { bitmap.recycle() }
        check(output.mkdirs())
        val databaseCopy = File(output, "database.db")
        databaseFile.copyTo(databaseCopy)
        check(hash(databaseCopy) == hash(databaseFile))
        selected.forEach { (relative, file) -> File(output, "private/$relative").also { it.parentFile!!.mkdirs(); file.copyTo(it); check(hash(it) == hash(file)) } }
        // Serialize values obtained from the real target gateway, not wanted/fixture JSON.
        val portable = JSONArray().also { array -> actualPreferences.forEach { p -> array.put(JSONObject().put("key", p.key).put("type", p.type.name).put("values", JSONArray(p.values))) } }
        File(output, "portable-preferences.json").writeText(portable.toString(2) + "\n")
        if (emulator != null) {
            // The live V8 product writer correctly rejects a V6 source. Preserve
            // the synthetic V6 wire image with the existing legacy test encoder.
            val fixtureFile = File(output, "fixture-source.bin")
            if (captureV8) fixtureFile.writeBytes(LegacyUpgradeFixtureContainer.databaseBytes(source))
            else fixtureFile.outputStream().use { BackupPayloadCodec.writeDatabase(source, it) }
            (technicalFiles() + platformFiles()).forEach { (relative, file) ->
                File(output, "annex/$relative").also { it.parentFile!!.mkdirs(); file.copyTo(it); check(hash(it) == hash(file)) }
            }
        }
        val files = JSONObject()
        output.walkTopDown().filter { it.isFile }.forEach { f -> files.put(f.relativeTo(output).invariantSeparatorsPath, JSONObject().put("bytes", f.length()).put("sha256", hash(f))) }
        val receipt = JSONObject().put("format", "stage10-android-capture/1").put("operation", request.operation)
            .put("captured_at_epoch_millis", capturedAt)
            .put("capture_id", request.captureId).put("room_version", request.room).put("fixture_day", Stage10UpgradeContract.DAY)
            .put("target_package", target.packageName).put("target_apk_sha256", hash(File(target.applicationInfo.sourceDir)))
            .put("test_package", test.packageName).put("runner_apk_sha256", hash(File(test.applicationInfo.sourceDir)))
            .put("certificate_sha256", Stage10UpgradeContract.CERT).put("source_fixture_sha256", Stage10UpgradeContract.SOURCE)
            .put("neutral_application", true).put("portable_source", "TARGET_GATEWAY_AND_EXPLICIT_PERSISTED_KEYS")
            .put("single_target_process_verified", true).put("export_owner", "TARGET_QA_CACHE")
            .put("technical_files_not_portable", JSONObject(technicalFiles().mapValues { hash(it.value) }))
            .put("wal_checkpoint", JSONArray(checkpoint)).put("database_closed_before_copy", true).put("files", files)
        if (expiryCriterion) receipt.put("criterion", Stage10UpgradeContract.EXPIRY_CRITERION)
        if (expiryCriterion) receipt.put("platform_runtime_files", JSONObject(platformFiles().mapValues { hash(it.value) }))
        if (emulator != null) {
            val info = target.packageManager.getPackageInfo(target.packageName, 0)
            receipt.put("format", Stage10EmulatorContract.FORMAT).put("criterion", emulator.consent)
                .put("scenario_id", emulator.id).put("started_at", emulator.startedAt).put("short_end", emulator.shortEnd).put("long_end", emulator.longEnd)
                .put("source_fixture_sha256", hash(File(output, "fixture-source.bin")))
                .put("first_install_time", info.firstInstallTime).put("last_update_time", info.lastUpdateTime)
                .put("onboarding_completed", !seedCapture).put("platform_runtime_files", JSONObject(platformFiles().mapValues { hash(it.value) }))
                .put("api", android.os.Build.VERSION.SDK_INT).put("fingerprint", android.os.Build.FINGERPRINT)
            profileBaseline?.let { receipt.put("profile_baseline", JSONObject(it.receipt())) }
        }
        File(output, "receipt.json").writeText(receipt.toString(2) + "\n")
        check(footprint() == before)
        check(hash(databaseCopy) == hash(databaseFile))
        ZipOutputStream(bundle.outputStream()).use { zip ->
            output.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(output).invariantSeparatorsPath }.forEach { f ->
                zip.putNextEntry(ZipEntry(f.relativeTo(output).invariantSeparatorsPath)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
        emulator?.requireTime(request.operation, System.currentTimeMillis())
        return bundle
    }

    private suspend fun seed(source: BackupDatabaseSnapshot, wanted: List<BackupPreference>, png: ByteArray) {
        // No reset/clear/deletion. Existing QA, even an apparently empty DB, rejects.
        listOf(target.filesDir, target.noBackupFilesDir, File(target.applicationInfo.dataDir, "shared_prefs"), databaseFile.parentFile!!).forEach {
            check(regularFiles(it).isEmpty()) { "Initial QA must be empty; review whole-QA disposition" }
        }
        val room = TargetRoom(target)
        try {
            val db = room.database
            check(db.version == 6)
            source.tables.forEach { table -> check(db.query("SELECT COUNT(*) FROM ${q(table.name)}").use { c -> c.moveToFirst(); c.getLong(0) } == 0L) }
            db.beginTransaction()
            try {
                db.execSQL("PRAGMA defer_foreign_keys=ON")
                source.tables.forEach { table ->
                    val sql = "INSERT INTO ${q(table.name)} (${table.columns.joinToString(",", transform = ::q)}) VALUES (${table.columns.joinToString(",") { "?" }})"
                    table.records.forEach { row -> db.execSQL(sql, row.values.map(::binding).toTypedArray()) }
                }
                check(readOriginalTables(db, source) == source)
                check(db.query("PRAGMA foreign_key_check").use { !it.moveToFirst() })
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            preferences(source, wanted, seed = true)
        } finally { room.close() }
        check(photo.parentFile!!.mkdirs())
        photo.writeBytes(png)
        marker.parentFile!!.mkdirs()
        check(!marker.exists()); marker.writeText(emulator?.marker() ?: Stage10UpgradeContract.marker())
    }

    private suspend fun preferences(database: BackupDatabaseSnapshot, wanted: List<BackupPreference>, seed: Boolean): List<BackupPreference> {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        val stores: Map<String, DataStore<Preferences>> = storeNames.mapValues { (_, name) -> PreferenceDataStoreFactory.create(scope = scope, produceFile = { target.preferencesDataStoreFile(name) }) }
        try {
            val gateway = PortablePreferencesGateway(target, GuardProfileStore(stores.getValue("profile")), SummaryPreferencesStore(stores.getValue("summary")),
                NotificationPreferencesStore(stores.getValue("notifications")), WeatherPreferencesStore(stores.getValue("weather")))
            if (seed) gateway.replace(wanted, database)
            val persisted = stores.mapValues { (_, store) -> store.data.first().asMap().keys.map { it.name }.toSet() }.toMutableMap()
            val displayPrefs = target.getSharedPreferences("miguardia_display_preferences", Context.MODE_PRIVATE)
            persisted["display"] = displayPrefs.all.keys
            Stage10UpgradeContract.requirePersistedKeys(persisted)
            return gateway.capture(database).also {
                if (emulator == null) Stage10UpgradeContract.requireCapturedPreferences(it, wanted, database, capturedAt, expiryCriterion && !seed)
                else Stage10EmulatorContract.requirePreferences(it, wanted, emulator, capturedAt, captureV8 && !seed)
            }
        } finally { job.cancelAndJoin() }
    }

    private fun readOriginalTables(db: TargetDatabase, shape: BackupDatabaseSnapshot): BackupDatabaseSnapshot = shape.copy(
        timelineId = db.query("SELECT timelineId FROM work_configuration_roots WHERE singletonSlot=1").use { c -> check(c.moveToFirst()); c.getString(0).also { check(!c.moveToNext()) } },
        tables = shape.tables.map { table -> table.copy(records = db.query("SELECT ${table.columns.joinToString(",", transform = ::q)} FROM ${q(table.name)} ORDER BY ${table.primaryKey.joinToString(",", transform = ::q)}").use { cursor ->
            buildList { while (cursor.moveToNext()) add(BackupRecord(table.columns.indices.map { index -> when (cursor.getType(index)) {
                android.database.Cursor.FIELD_TYPE_NULL -> BackupValue.Null
                android.database.Cursor.FIELD_TYPE_INTEGER -> BackupValue.Integer(cursor.getLong(index))
                android.database.Cursor.FIELD_TYPE_FLOAT -> BackupValue.Real(cursor.getDouble(index))
                android.database.Cursor.FIELD_TYPE_STRING -> BackupValue.Text(cursor.getString(index))
                android.database.Cursor.FIELD_TYPE_BLOB -> BackupValue.Binary(cursor.getBlob(index))
                else -> error("Unexpected SQLite type")
            } })) }
        }) },
    )
    private fun requireSelection() {
        val roots = listOf(target.filesDir, target.noBackupFilesDir, File(target.applicationInfo.dataDir, "shared_prefs"))
        val actual = roots.flatMap(::regularFiles).map { it.relativeTo(File(target.applicationInfo.dataDir)).invariantSeparatorsPath }.toSet()
        if (emulator == null) Stage10UpgradeContract.requirePrivateSelection(actual, selected.keys, captureV8, expiryCriterion)
        else Stage10EmulatorContract.requireSelection(actual, selected.keys)
        technicalFiles()
        platformFiles()
        check(selected.values.all { it.isFile })
        check(regularFiles(databaseFile.parentFile!!).all { it.name in setOf("miguardia-v2.db", "miguardia-v2.db-wal", "miguardia-v2.db-shm") })
    }
    private fun regularFiles(root: File): List<File> {
        check(!Files.isSymbolicLink(root.toPath()))
        if (!root.exists()) return emptyList()
        return root.walkTopDown().onEnter { check(!Files.isSymbolicLink(it.toPath())); check(it.canonicalPath.startsWith(root.canonicalPath)); true }
            .filter { check(!Files.isSymbolicLink(it.toPath())); it.isFile }.toList()
    }
    private fun requireSingleTargetProcess() {
        check(android.os.Process.myUid() == target.applicationInfo.uid)
        val manager = target.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val own = requireNotNull(manager.runningAppProcesses).filter { it.uid == target.applicationInfo.uid }
        check(own.size == 1 && own.single().pid == android.os.Process.myPid() && own.single().processName == target.packageName)
    }
    private fun technicalFiles(): Map<String, File> = Stage10UpgradeContract.technicalPaths.mapNotNull { path ->
        val file = File(target.applicationInfo.dataDir, path)
        if (!file.exists()) null else {
            check(captureV8 || emulator != null)
            check(file.isFile && !Files.isSymbolicLink(file.toPath()))
            val updated = target.packageManager.getPackageInfo(target.packageName, 0).lastUpdateTime
            if (emulator == null) Stage10UpgradeContract.requireTechnicalFile(path, file.readBytes(), updated)
            else Stage10EmulatorContract.requireAnnex(path, file.readBytes(), updated, profileBaseline)
            path to file
        }
    }.toMap()
    private fun platformFiles(): Map<String, File> {
        val file = File(target.applicationInfo.dataDir, Stage10UpgradeContract.PLATFORM_IDS_PATH)
        if (!file.exists()) return emptyMap()
        check((expiryCriterion || emulator != null) && file.isFile && !Files.isSymbolicLink(file.toPath()))
        if (emulator == null) Stage10UpgradeContract.requirePlatformIds(file.readBytes())
        else Stage10EmulatorContract.requireAnnex(Stage10UpgradeContract.PLATFORM_IDS_PATH, file.readBytes(), target.packageManager.getPackageInfo(target.packageName, 0).lastUpdateTime)
        return mapOf(Stage10UpgradeContract.PLATFORM_IDS_PATH to file)
    }
    private fun footprint(): Map<String,String> = (selected + technicalFiles() + platformFiles()).mapValues { hash(it.value) }
    private suspend fun requireOnboarding() {
        val file = File(target.applicationInfo.dataDir, Stage10EmulatorContract.ONBOARDING)
        check(file.isFile)
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
            val values = store.data.first().asMap()
            check(values.size == 1 && values.entries.single().key.name == "completed_version" && values.entries.single().value == 1)
        } finally { job.cancelAndJoin() }
    }
    private fun binding(value: BackupValue): Any? = when (value) {
        BackupValue.Null -> null
        is BackupValue.Integer -> value.value
        is BackupValue.Real -> value.value
        is BackupValue.Text -> value.value
        is BackupValue.Binary -> value.value
    }
    private fun q(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
    private fun hash(file: File): String = digest(file.readBytes())
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    // The database module is intentionally internal and not on app's test compile API.
    // Resolve ONLY the pinned target's existing public JVM ABI. No Room/schema copy,
    // extra dependency, source visibility change or V8 implementation in the runner.
    private class TargetRoom(context: Context) {
        private val loader = context.classLoader
        private val type = Class.forName("com.blackatsystems.miguardia.core.database.MiGuardiaV2Database", true, loader)
        private val companion = type.getField("Companion").get(null)
        private val room = companion.javaClass.getMethod("build", Context::class.java, String::class.java).invoke(companion, context, "miguardia-v2.db")
        private val roomApi = Class.forName("androidx.room.RoomDatabase", true, loader)
        val database: TargetDatabase get() {
            val helper = roomApi.getMethod("getOpenHelper").invoke(room)
            val helperApi = Class.forName("androidx.sqlite.db.SupportSQLiteOpenHelper", true, loader)
            return TargetDatabase(helperApi.getMethod("getWritableDatabase").invoke(helper), loader)
        }
        fun close() { roomApi.getMethod("close").invoke(room) }
    }
    private class TargetDatabase(private val value: Any, loader: ClassLoader) {
        private val api = Class.forName("androidx.sqlite.db.SupportSQLiteDatabase", true, loader)
        val version: Int get() = api.getMethod("getVersion").invoke(value) as Int
        fun query(sql: String): android.database.Cursor = api.getMethod("query", String::class.java).invoke(value, sql) as android.database.Cursor
        fun execSQL(sql: String) { api.getMethod("execSQL", String::class.java).invoke(value, sql) }
        fun execSQL(sql: String, args: Array<Any?>) { api.getMethod("execSQL", String::class.java, Array<Any>::class.java).invoke(value, sql, args as Any) }
        fun beginTransaction() { api.getMethod("beginTransaction").invoke(value) }
        fun setTransactionSuccessful() { api.getMethod("setTransactionSuccessful").invoke(value) }
        fun endTransaction() { api.getMethod("endTransaction").invoke(value) }
    }
}
