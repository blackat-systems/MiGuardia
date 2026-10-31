package com.blackatsystems.miguardia.upgrade

import com.blackatsystems.miguardia.core.domain.backup.*
import java.io.DataInputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Separate test-only profile. The historical Samsung contract remains unchanged. */
object Stage10EmulatorContract {
    const val CONSENT = "STAGE10_ONE_DEDICATED_AVD_20261022"
    const val CONSENT_R2 = "STAGE10_SECOND_DEDICATED_AVD_20261022_R2"
    const val FORMAT = "stage10-emulator-capture/1"
    const val MARKER = "stage10-emulator-fixture-v1.txt"
    const val ONBOARDING = "files/datastore/onboarding.preferences_pb"
    const val SHORT_ID = "30000000-0000-4000-8000-000000000023"
    const val LONG_ID = "30000000-0000-4000-8000-000000000024"
    const val SHORT_KEY = "shift:$SHORT_ID"
    const val LONG_KEY = "shift:$LONG_ID"
    val keys = listOf(SHORT_KEY, LONG_KEY)
    val annexPaths = Stage10UpgradeContract.technicalPaths + Stage10UpgradeContract.PLATFORM_IDS_PATH
    private val requestKeys = setOf("stage10EmulatorOperation", "stage10Consent", "stage10ScenarioId",
        "stage10StartedAt", "stage10ShortEnd", "stage10LongEnd", "stage10CaptureId")
    private val baselineKeys = setOf("stage10BaselineBundleSha256", "stage10BaselineProfileSha256",
        "stage10BaselineFirstInstallTime", "stage10BaselineLastUpdateTime", "stage10BaselineCapturedAt")

    data class ProfileBaseline(val bundleSha256: String, val profileSha256: String,
        val firstInstallTime: Long, val lastUpdateTime: Long, val capturedAt: Long) {
        init {
            require(bundleSha256.matches(Regex("[a-f0-9]{64}")) && profileSha256.matches(Regex("[a-f0-9]{64}")))
            require(firstInstallTime > 0 && firstInstallTime <= lastUpdateTime && lastUpdateTime <= capturedAt)
        }
        fun requireScenario(scenario: Scenario) {
            require(scenario.consent == CONSENT_R2)
            require(capturedAt >= scenario.startedAt && capturedAt < scenario.shortEnd - 60_000L)
        }
        fun requireInstalled(scenario: Scenario, first: Long, updated: Long) {
            requireScenario(scenario)
            require(first == firstInstallTime && updated >= scenario.shortEnd && updated > lastUpdateTime)
        }
        fun receipt() = mapOf("bundle_sha256" to bundleSha256, "profile_sha256" to profileSha256,
            "first_install_time" to firstInstallTime, "last_update_time" to lastUpdateTime,
            "captured_at_epoch_millis" to capturedAt)
    }

    fun profileBaseline(args: Map<String, String>): ProfileBaseline? {
        if (args.keys.none { it in baselineKeys }) return null
        require(args.keys.containsAll(baselineKeys))
        require(args["stage10EmulatorOperation"] == "capture-v8")
        return ProfileBaseline(args.getValue("stage10BaselineBundleSha256"), args.getValue("stage10BaselineProfileSha256"),
            args.getValue("stage10BaselineFirstInstallTime").toLong(), args.getValue("stage10BaselineLastUpdateTime").toLong(),
            args.getValue("stage10BaselineCapturedAt").toLong()).also { it.requireScenario(scenario(args)) }
    }

    data class Scenario(val id: String, val startedAt: Long, val shortEnd: Long, val longEnd: Long, val consent: String = CONSENT) {
        init {
            require(UUID.fromString(id).toString() == id)
            require(startedAt > 0 && startedAt % 60_000L == 0L)
            require(consent in setOf(CONSENT, CONSENT_R2))
            require(shortEnd == Math.addExact(startedAt, (if (consent == CONSENT_R2) 30 else 15) * 60_000L))
            require(longEnd == Math.addExact(startedAt, 48 * 3_600_000L))
        }
        fun marker() = listOf(consent, id, startedAt, shortEnd, longEnd,
            Stage10UpgradeContract.SOURCE, Stage10UpgradeContract.PREFERENCES, Stage10UpgradeContract.PHOTO)
            .joinToString("\n", postfix = "\n")
        fun requireTime(operation: String, now: Long) {
            require(now >= startedAt && now < longEnd) { "Scenario clock window missed" }
            when (operation) {
                "seed-v6" -> require(now < startedAt + 60_000L)
                "capture-v6" -> require(now < shortEnd - 60_000L)
                "capture-v8" -> require(now >= shortEnd)
                else -> error("Unknown emulator operation")
            }
        }
    }

    fun scenario(args: Map<String, String>) = Scenario(
        requireNotNull(args["stage10ScenarioId"]), requireNotNull(args["stage10StartedAt"]).toLong(),
        requireNotNull(args["stage10ShortEnd"]).toLong(), requireNotNull(args["stage10LongEnd"]).toLong(),
        requireNotNull(args["stage10Consent"]),
    )

    fun request(args: Map<String, String>, pkg: String, testPkg: String, cert: String, apk: String): Stage10UpgradeContract.Request {
        require(args.keys == requestKeys || args.keys == requestKeys + baselineKeys)
        require(args["stage10Consent"] in setOf(CONSENT, CONSENT_R2))
        require(pkg == Stage10UpgradeContract.QA && testPkg == "$pkg.test" && cert == Stage10UpgradeContract.CERT)
        scenario(args)
        profileBaseline(args)
        val operation = requireNotNull(args["stage10EmulatorOperation"])
        require(operation in setOf("seed-v6", "capture-v6", "capture-v8"))
        val room = if (operation == "capture-v8") 8 else 6
        require(apk == if (room == 6) Stage10UpgradeContract.APK6 else Stage10UpgradeContract.APK8)
        val id = requireNotNull(args["stage10CaptureId"])
        require(UUID.fromString(id).toString() == id)
        return Stage10UpgradeContract.Request(operation, room, id)
    }

    /** Keep all 32 original rows; add two manual shifts and their three associations. */
    fun fixture(base: BackupDatabaseSnapshot, scenario: Scenario): BackupDatabaseSnapshot {
        require(base.totalRows == 32 && base.tables.size == 27 && base.roomVersion == 6)
        val additions = mapOf(SHORT_ID to scenario.shortEnd, LONG_ID to scenario.longEnd)
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
        return base.copy(tables = base.tables.map { table ->
            if (table.name !in setOf("shifts", "shift_work_snapshots", "shift_notification_configs", "shift_notification_reminders")) table
            else {
                val idColumn = if (table.name == "shifts") "id" else "shiftId"
                val original = table.records.single { (it.values[table.columns.indexOf(idColumn)] as BackupValue.Text).value ==
                    Stage10UpgradeContract.KNOWN_DISMISSED.removePrefix("shift:") }
                val rows = additions.map { (id, end) ->
                    val start = end - 8 * 3_600_000L
                    val startDate = Instant.ofEpochMilli(start).atZone(zone)
                    val endDate = Instant.ofEpochMilli(end).atZone(zone)
                    val replacements = mutableMapOf<String, BackupValue>(idColumn to BackupValue.Text(id))
                    if (table.name == "shifts") replacements.putAll(mapOf(
                        "startEpochMillis" to BackupValue.Integer(start), "endEpochMillis" to BackupValue.Integer(end),
                        "zoneId" to BackupValue.Text(zone.id), "localStartDate" to BackupValue.Text(startDate.toLocalDate().toString()),
                        "startTimeSnapshot" to BackupValue.Text(startDate.format(clockFormat)),
                        "endTimeSnapshot" to BackupValue.Text(endDate.format(clockFormat)),
                        "position" to BackupValue.Text("Ensayo ficticio de conservación"),
                        "createdAtEpochMillis" to BackupValue.Integer(scenario.startedAt),
                        "updatedAtEpochMillis" to BackupValue.Integer(scenario.startedAt),
                    ))
                    original.copy(values = original.values.mapIndexed { i, value -> replacements[table.columns[i]] ?: value })
                }
                val spec = MiGuardiaBackupSchemaV6.tables.single { it.name == table.name }
                table.copy(records = (table.records + rows).sortedBy { it.backupKey(spec).stableText() })
            }
        }).also { MiGuardiaBackupSchemaV6.requireValid(it); require(it.totalRows == 40) }
    }

    fun preferences(original: List<BackupPreference>) = original.map {
        if (it.key == Stage10UpgradeContract.DISMISSED_KEY) it.copy(values = keys) else it
    }

    fun requirePreferences(actual: List<BackupPreference>, wanted: List<BackupPreference>, scenario: Scenario, now: Long, v8: Boolean) {
        require(now < scenario.longEnd)
        require(actual.size == 17 && actual.map { it.key } == wanted.map { it.key })
        actual.zip(wanted).forEach { (value, before) ->
            require(value.type == before.type)
            if (value.key != Stage10UpgradeContract.DISMISSED_KEY) require(value == before)
            else {
                require(before.values == keys && value.values.distinct() == value.values)
                require(value.values == keys || (v8 && now >= scenario.shortEnd && value.values == listOf(LONG_KEY)))
            }
        }
    }

    fun requireSelection(actual: Set<String>, selected: Set<String>) {
        require(actual.containsAll(selected) && (actual - selected).all { it in annexPaths })
    }

    fun requireAnnex(path: String, bytes: ByteArray, packageLastUpdate: Long, baseline: ProfileBaseline? = null) {
        when (path) {
            "files/profileInstalled" -> {
                require(bytes.size == 24)
                DataInputStream(bytes.inputStream()).use {
                    require(it.readInt() == 1)
                    // ProfileVerifier 1.4.0 result constants, fixed cache layout.
                    require(it.readInt() in setOf(0, 1, 2, 3, 65536, 131072, 196608, 262144, 327680))
                    val cachedUpdate = it.readLong()
                    val exactBaseline = baseline != null && cachedUpdate == baseline.lastUpdateTime &&
                        packageLastUpdate > baseline.lastUpdateTime && MessageDigest.getInstance("SHA-256").digest(bytes)
                            .joinToString("") { value -> "%02x".format(value.toInt() and 255) } == baseline.profileSha256
                    require(packageLastUpdate > 0 && (cachedUpdate == packageLastUpdate || exactBaseline))
                    require(it.readLong() >= 0L)
                }
            }
            "shared_prefs/notification_deferred_actions.xml" -> Stage10UpgradeContract.requireTechnicalFile(path, bytes, packageLastUpdate)
            Stage10UpgradeContract.PLATFORM_IDS_PATH -> {
                val text = bytes.toString(Charsets.UTF_8)
                require(bytes.size <= 128 && text.matches(Regex("<\\?xml version='1.0' encoding='utf-8' standalone='yes' \\?>\\n<map>\\n    <int name=\"IDSCount\" value=\"[1-9][0-9]{0,8}\" />\\n</map>\\n")))
            }
            else -> throw IllegalArgumentException("Unknown emulator annex")
        }
    }
}
