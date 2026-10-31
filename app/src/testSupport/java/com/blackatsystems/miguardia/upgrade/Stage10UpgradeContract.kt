package com.blackatsystems.miguardia.upgrade

import com.blackatsystems.miguardia.core.domain.backup.BackupDatabaseSnapshot
import com.blackatsystems.miguardia.core.domain.backup.BackupPayloadCodec
import com.blackatsystems.miguardia.core.domain.backup.BackupPreference
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupSchemaV6
import com.blackatsystems.miguardia.core.domain.backup.BackupValue
import java.io.InputStream
import java.io.DataInputStream
import java.security.MessageDigest
import java.util.UUID

/** Test-only, common V6 ABI. Never references V7/V8-only schema/classes. */
object Stage10UpgradeContract {
    const val QA = "com.blackatsystems.miguardia.qa"
    const val CONSENT = "STAGE10_SYNTHETIC_QA_SEED_AND_CAPTURE"
    const val DAY = "2026-10-21"
    const val CERT = "b6910698d7d1e4524feb80ee1a60015dd931efda7a7c1854d432c0e3eb627617"
    const val APK6 = "1c0d0be8f8f66a2dc65e94a2c8d022836984f210cce8f3b822cb5d555f73148a"
    const val APK8 = "2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3"
    const val SOURCE = "8def0ccf622d3aa7e6ea2a855f071aae28505c695ad8d803888ef7f40a226ba5"
    const val PREFERENCES = "906f8a853793dcdedccd16566c8b17af2b73836bc05daa695dc97e2504152d90"
    const val PHOTO = "5aa8334f217a14c229b9ec7b2c6ae02ae39c5ab94328492363e6458cc14e6e26"
    const val ID6 = "7eb39f6fab5a44e69350e206716554be"
    const val ID8 = "ba1016e34e957457e7bec8ab18a98aad"
    const val PHOTO_KEY = "30000000-0000-4000-8000-000000000014.png"
    const val MARKER = "stage10-fixture-v1.txt"
    const val EXPIRY_OPERATION = "capture-v8-expiry-v1"
    const val EXPIRY_CRITERION = "STAGE10_EXPIRED_NOTIFICATION_PRUNING_20261022_V1"
    const val DISMISSED_KEY = "notifications.dismissed_event_keys"
    const val KNOWN_DISMISSED = "shift:30000000-0000-4000-8000-000000000009"
    val off = setOf("notifications.enabled", "notifications.precise_timing", "weather.enabled", "weather.include_notifications")
    val requiredKeys = mapOf(
        "profile" to setOf("display_name"),
        "summary" to setOf("ordered_optional_families", "hidden_optional_families", "personalization_intro_seen"),
        "notifications" to setOf("enabled", "precise_timing", "persistent_while_active", "privacy", "attention_mode", "global_reminder_minutes", "dismissed_shift_ids"),
        "weather" to setOf("enabled", "unit_system", "include_in_notifications", "provider_explanation_accepted"),
        "display" to setOf("app_theme_mode", "app_zoom_percent"),
    )
    data class Request(val operation: String, val room: Int, val captureId: String)

    fun request(args: Map<String, String>, packageName: String, testPackage: String, certificate: String, apk: String): Request {
        require(args.keys == setOf("stage10Operation", "stage10Consent", "stage10FixtureDay", "stage10CaptureId"))
        require(packageName == QA && testPackage == "$QA.test")
        require(args["stage10Consent"] == CONSENT && args["stage10FixtureDay"] == DAY)
        val operation = requireNotNull(args["stage10Operation"])
        require(operation in setOf("seed-v6", "capture-v6", "capture-v8", EXPIRY_OPERATION))
        val room = if (operation in setOf("capture-v8", EXPIRY_OPERATION)) 8 else 6
        require(certificate == CERT && apk == if (room == 6) APK6 else APK8)
        val id = requireNotNull(args["stage10CaptureId"])
        require(UUID.fromString(id).toString() == id)
        return Request(operation, room, id)
    }

    fun fixture(stream: InputStream): BackupDatabaseSnapshot = BackupPayloadCodec.readDatabase(stream).also {
        require(it.roomVersion == 6 && it.roomIdentityHash == ID6 && it.tables.size == 27 && it.totalRows == 32)
        MiGuardiaBackupSchemaV6.requireValid(it)
        require(it.tables.all { table -> table.records.isNotEmpty() })
    }

    fun preferences(stream: InputStream): List<BackupPreference> = BackupPayloadCodec.readPreferences(stream).map {
        if (it.key in off) it.copy(values = listOf("false")) else it
    }.also { values ->
        require(values.size == 17 && values.map { it.key }.distinct().size == 17)
        require(values.filter { it.key in off }.size == 4)
        require(values.single { it.key == "summary.ordered_families" }.values.contains("SITUATIONS"))
    }

    fun requirePersistedKeys(actual: Map<String, Set<String>>) {
        require(actual.keys == requiredKeys.keys)
        requiredKeys.forEach { (store, required) -> require(actual.getValue(store).containsAll(required)) { "Missing explicitly persisted portable key" } }
    }

    fun dismissalEnd(database: BackupDatabaseSnapshot, key: String): Long {
        require(key == KNOWN_DISMISSED)
        val id = key.removePrefix("shift:")
        val shifts = database.table("shifts")
        val planned = shifts.records.single { (it.values[shifts.columns.indexOf("id")] as BackupValue.Text).value == id }
        val actuals = database.table("shift_actual_records")
        val actual = actuals.records.singleOrNull { (it.values[actuals.columns.indexOf("shiftId")] as BackupValue.Text).value == id }
        return if (actual != null) (actual.values[actuals.columns.indexOf("actualEndEpochMillis")] as BackupValue.Integer).value
        else (planned.values[shifts.columns.indexOf("endEpochMillis")] as BackupValue.Integer).value
    }

    fun requireCapturedPreferences(actual: List<BackupPreference>, wanted: List<BackupPreference>, database: BackupDatabaseSnapshot,
                                   capturedAt: Long, allowExpired: Boolean): Set<String> {
        if (!allowExpired) { require(actual == wanted); return emptySet() }
        require(actual.size == 17 && actual.map { it.key } == wanted.map { it.key })
        actual.zip(wanted).forEach { (value, before) ->
            require(value.type == before.type)
            if (value.key != DISMISSED_KEY) require(value == before)
        }
        val before = wanted.single { it.key == DISMISSED_KEY }.values
        val after = actual.single { it.key == DISMISSED_KEY }.values
        require(before.distinct() == before && after.distinct() == after && before.containsAll(after))
        return (before.toSet() - after.toSet()).also { removed ->
            removed.forEach { require(capturedAt >= dismissalEnd(database, it)) { "Live dismissal must survive" } }
        }
    }

    // Observed after MY_PACKAGE_REPLACED, outside the seven portable RAW files.
    // No directory/prefix exclusions: unknown files and missing selected files fail.
    val technicalPaths = setOf("files/profileInstalled", "shared_prefs/notification_deferred_actions.xml")
    const val EMPTY_DEFERRED_SHA256 = "3325d2a819fdd8062c2cdc48a09b995c9b012915bcdf88b1cf9742a7f057c793"
    const val OBSERVED_PROFILE_SHA256 = "e75f27e7b4159e6d5875c738acecc092060912606c435305af8c92c68456ba5e"
    const val PLATFORM_IDS_PATH = "shared_prefs/android.app.ActivityThread.IDS.xml"
    const val PLATFORM_IDS_SHA256 = "dec264c49b5f4f8b1526a8d84d803fc55102be8b02c3b4a7f0ee6ac973ca9ece"
    fun requirePrivateSelection(actual: Set<String>, selected: Set<String>, captureV8: Boolean = false, allowPlatformIds: Boolean = false) {
        val extra = actual - selected
        require(actual.containsAll(selected) && (extra.isEmpty() || (captureV8 &&
            (extra == technicalPaths || (allowPlatformIds && extra == technicalPaths + PLATFORM_IDS_PATH)))))
    }

    fun requirePlatformIds(bytes: ByteArray) {
        require(bytes.size == 108)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        require(digest == PLATFORM_IDS_SHA256)
    }

    fun requireTechnicalFile(path: String, bytes: ByteArray, packageLastUpdateTime: Long) {
        when (path) {
            "files/profileInstalled" -> {
                // AndroidX profileinstaller 1.4.0 ProfileVerifier.Cache, big endian.
                require(bytes.size == 24)
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                require(digest == OBSERVED_PROFILE_SHA256)
                DataInputStream(bytes.inputStream()).use {
                    require(it.readInt() == 1 && it.readInt() == 327680)
                    require(packageLastUpdateTime > 0 && it.readLong() == packageLastUpdateTime)
                    require(it.readLong() == 0L)
                }
            }
            "shared_prefs/notification_deferred_actions.xml" -> {
                // Exact observed empty <map/> bytes, never a pending action queue.
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                require(digest == EMPTY_DEFERRED_SHA256)
            }
            else -> throw IllegalArgumentException("Unknown technical file")
        }
    }

    fun marker(): String = listOf("STAGE10_SYNTHETIC_ONLY_V1", DAY, SOURCE, PREFERENCES, PHOTO).joinToString("\n", postfix = "\n")
}
