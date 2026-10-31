package com.blackatsystems.miguardia.upgrade

import com.blackatsystems.miguardia.core.domain.backup.*
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class Stage10EmulatorContractTest {
    private val started = 1_792_713_600_000L
    private val scenario = Stage10EmulatorContract.Scenario("3cbd51f6-3091-4272-a1a2-a2efbe488aee", started, started + 900_000L, started + 172_800_000L)
    private val assets = File("src/androidTest/assets/stage10")
    private fun base() = assets.resolve("source-v6.bin").inputStream().use(Stage10UpgradeContract::fixture)
    private fun wanted() = Stage10EmulatorContract.preferences(assets.resolve("preferences.bin").inputStream().use(Stage10UpgradeContract::preferences))
    private fun rejected(block: () -> Unit) { try { block(); fail("Must reject") } catch (_: IllegalArgumentException) { } }
    private fun values(keys: List<String>) = wanted().map { if (it.key == Stage10UpgradeContract.DISMISSED_KEY) it.copy(values = keys) else it }

    @Test fun fortyRowsKeepEveryOriginalTypedRowAndAddTwoCompleteManualShifts() {
        val before = base()
        val after = Stage10EmulatorContract.fixture(before, scenario)
        assertEquals(27, after.tables.size); assertEquals(40, after.totalRows)
        before.tables.zip(after.tables).forEach { (a, b) -> assertTrue(b.records.containsAll(a.records)) }
        val shifts = after.table("shifts")
        listOf(Stage10EmulatorContract.SHORT_ID to scenario.shortEnd, Stage10EmulatorContract.LONG_ID to scenario.longEnd).forEach { (id, end) ->
            val row = shifts.records.single { it.values[0] == BackupValue.Text(id) }
            assertEquals(BackupValue.Integer(end), row.values[shifts.columns.indexOf("endEpochMillis")])
            listOf("shift_work_snapshots", "shift_notification_configs", "shift_notification_reminders").forEach { name ->
                assertEquals(1, after.table(name).records.count { it.values[0] == BackupValue.Text(id) })
            }
        }
        MiGuardiaBackupSchemaV6.requireValid(after)
    }
    @Test fun v8CaptureUsesLegacyFixtureEncodingAndMatchesTheSealedR2Source() {
        val originalR2 = Stage10EmulatorContract.Scenario("a711836c-78a0-483c-83c2-6143018c7377",
            1792721040000, 1792722840000, 1792893840000, Stage10EmulatorContract.CONSENT_R2)
        val source = Stage10EmulatorContract.fixture(base(), originalR2)
        try {
            BackupPayloadCodec.writeDatabase(source, ByteArrayOutputStream())
            fail("Current product writer must continue rejecting a V6 source")
        } catch (_: InvalidBackupException) { }
        val bytes = LegacyUpgradeFixtureContainer.databaseBytes(source)
        assertEquals(source, BackupPayloadCodec.readDatabase(bytes.inputStream()))
        assertEquals("2194ca0e64218e1936318a9bdeb6b95eb6c99dc0f7022294dc8ced8bc0d34764",
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) })
    }
    @Test fun onlyDismissalIdentitiesDifferFromPreviouslySafeSeventeenPreferences() {
        val original = assets.resolve("preferences.bin").inputStream().use(Stage10UpgradeContract::preferences)
        assertEquals(listOf(Stage10UpgradeContract.DISMISSED_KEY), original.zip(wanted()).filter { it.first != it.second }.map { it.first.key })
        assertTrue(wanted().filter { it.key in Stage10UpgradeContract.off }.all { it.values == listOf("false") })
    }
    @Test fun liveDiscardCannotBeLostEvenWhenShortDiscardExpires() {
        rejected { Stage10EmulatorContract.requirePreferences(values(emptyList()), wanted(), scenario, scenario.shortEnd, true) }
        rejected { Stage10EmulatorContract.requirePreferences(values(listOf(Stage10EmulatorContract.SHORT_KEY)), wanted(), scenario, scenario.shortEnd, true) }
        rejected { Stage10EmulatorContract.requirePreferences(values(listOf(Stage10EmulatorContract.LONG_KEY)), wanted(), scenario, scenario.shortEnd - 1, true) }
        Stage10EmulatorContract.requirePreferences(values(listOf(Stage10EmulatorContract.LONG_KEY)), wanted(), scenario, scenario.shortEnd, true)
        rejected { Stage10EmulatorContract.requirePreferences(values(listOf(Stage10EmulatorContract.LONG_KEY)), wanted(), scenario, scenario.longEnd, true) }
    }
    @Test fun everyOtherPreferenceAndTypeMustSurvive() {
        wanted().filter { it.key != Stage10UpgradeContract.DISMISSED_KEY }.forEach { item ->
            rejected { Stage10EmulatorContract.requirePreferences(wanted().map { if (it.key == item.key) it.copy(values = listOf("changed")) else it }, wanted(), scenario, scenario.shortEnd, true) }
        }
        rejected { Stage10EmulatorContract.requirePreferences(wanted().dropLast(1), wanted(), scenario, scenario.shortEnd, true) }
        rejected { Stage10EmulatorContract.requirePreferences(values(Stage10EmulatorContract.keys + "shift:unknown"), wanted(), scenario, scenario.shortEnd, true) }
    }
    @Test fun baselineAndCaptureWindowsFailAtTheirBoundaries() {
        scenario.requireTime("seed-v6", started)
        rejected { scenario.requireTime("seed-v6", started + 60_000L) }
        scenario.requireTime("capture-v6", scenario.shortEnd - 60_001L)
        rejected { scenario.requireTime("capture-v6", scenario.shortEnd - 60_000L) }
        rejected { scenario.requireTime("capture-v8", scenario.shortEnd - 1L) }
        scenario.requireTime("capture-v8", scenario.shortEnd)
        rejected { scenario.requireTime("capture-v8", scenario.longEnd) }
    }
    @Test fun secondScenarioHasThirtyMinutesWithoutReinterpretingFirstScenario() {
        val second = scenario.copy(shortEnd = started + 1_800_000L, consent = Stage10EmulatorContract.CONSENT_R2)
        assertEquals(Stage10EmulatorContract.CONSENT_R2, second.marker().lineSequence().first())
        assertEquals(Stage10EmulatorContract.CONSENT, scenario.marker().lineSequence().first())
        assertEquals(started + 900_000L, scenario.shortEnd)
        rejected { scenario.copy(shortEnd = second.shortEnd) }
        rejected { second.copy(shortEnd = scenario.shortEnd) }
        rejected { second.copy(consent = "UNKNOWN") }
        second.requireTime("capture-v6", second.shortEnd - 60_001L)
        rejected { second.requireTime("capture-v6", second.shortEnd - 60_000L) }
        rejected { second.requireTime("capture-v8", second.shortEnd - 1L) }
        second.requireTime("capture-v8", second.shortEnd)
        rejected { second.requireTime("capture-v8", second.longEnd) }
        val fixture = Stage10EmulatorContract.fixture(base(), second)
        assertEquals(40, fixture.totalRows)
        assertTrue(fixture.table("shifts").records.any { row ->
            row.values[0] == BackupValue.Text(Stage10EmulatorContract.SHORT_ID) &&
                row.values[fixture.table("shifts").columns.indexOf("endEpochMillis")] == BackupValue.Integer(second.shortEnd)
        })
    }
    @Test fun secondRequestBindsConsentToExactWindow() {
        val args = mapOf("stage10EmulatorOperation" to "seed-v6", "stage10Consent" to Stage10EmulatorContract.CONSENT_R2,
            "stage10ScenarioId" to scenario.id, "stage10StartedAt" to started.toString(), "stage10ShortEnd" to (started + 1_800_000L).toString(),
            "stage10LongEnd" to scenario.longEnd.toString(), "stage10CaptureId" to "4793f38c-b729-4127-8a04-278bda9e4cdf")
        fun request(a: Map<String,String>) = Stage10EmulatorContract.request(a, Stage10UpgradeContract.QA,
            "${Stage10UpgradeContract.QA}.test", Stage10UpgradeContract.CERT, Stage10UpgradeContract.APK6)
        assertEquals(6, request(args).room)
        rejected { request(args + ("stage10Consent" to Stage10EmulatorContract.CONSENT)) }
        rejected { request(args + ("stage10ShortEnd" to scenario.shortEnd.toString())) }
    }
    @Test fun profileAnnexRequiresInstalledIdentityAndExactLayout() {
        fun bytes(updated: Long = started, code: Int = 2) = ByteBuffer.allocate(24).putInt(1).putInt(code).putLong(updated).putLong(0L).array()
        Stage10EmulatorContract.requireAnnex("files/profileInstalled", bytes(), started)
        rejected { Stage10EmulatorContract.requireAnnex("files/profileInstalled", bytes(updated = started + 1), started) }
        rejected { Stage10EmulatorContract.requireAnnex("files/profileInstalled", bytes(code = 999), started) }
        rejected { Stage10EmulatorContract.requireAnnex("files/profileInstalled", bytes() + 0, started) }
    }
    @Test fun inheritedProfileMustBeExactlyBoundAndAllOtherAnnexRulesRemain() {
        val before = started - 1000L
        fun bytes(code: Int = 2, stamp: Long = before, size: Long = 0) =
            ByteBuffer.allocate(24).putInt(1).putInt(code).putLong(stamp).putLong(size).array()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
        val baseline = Stage10EmulatorContract.ProfileBaseline("a".repeat(64), hash, before, before, started + 1000L)
        val updated = started + 1_800_000L
        Stage10EmulatorContract.requireAnnex("files/profileInstalled", bytes(), updated, baseline)
        Stage10EmulatorContract.requireAnnex("files/profileInstalled", bytes(stamp = updated), updated, baseline)
        rejected { Stage10EmulatorContract.requireAnnex("files/profileInstalled", bytes(), updated) }
        rejected { Stage10EmulatorContract.requireAnnex("files/profileInstalled", bytes(code = 3), updated, baseline) }
        rejected { Stage10EmulatorContract.requireAnnex("files/profileInstalled", bytes(stamp = before - 1), updated, baseline) }
        rejected { Stage10EmulatorContract.requireAnnex("files/profileInstalled", bytes(size = 1), updated, baseline) }
        rejected { Stage10EmulatorContract.requireAnnex("files/profileInstalled", bytes(), updated, baseline.copy(profileSha256 = "b".repeat(64))) }
        rejected { Stage10EmulatorContract.requireAnnex("files/profileInstalled", bytes(code = 999, stamp = updated), updated, baseline) }
        rejected { Stage10EmulatorContract.requireAnnex("files/unknown", bytes(), updated, baseline) }
    }
    @Test fun profileBindingRequiresOriginalInstallPostGuideR2AndCompleteExplicitArguments() {
        val second = scenario.copy(shortEnd = started + 1_800_000L, consent = Stage10EmulatorContract.CONSENT_R2)
        val before = started - 1000L
        val baseline = Stage10EmulatorContract.ProfileBaseline("a".repeat(64), "b".repeat(64), before, before, started + 1000L)
        baseline.requireInstalled(second, before, second.shortEnd)
        rejected { baseline.requireInstalled(second, before + 1, second.shortEnd) }
        rejected { baseline.requireInstalled(second, before, second.shortEnd - 1) }
        rejected { baseline.requireScenario(scenario) }
        rejected { baseline.copy(capturedAt = second.shortEnd - 60_000L).requireScenario(second) }
        val args = mapOf("stage10EmulatorOperation" to "capture-v8", "stage10Consent" to second.consent,
            "stage10ScenarioId" to second.id, "stage10StartedAt" to started.toString(), "stage10ShortEnd" to second.shortEnd.toString(),
            "stage10LongEnd" to second.longEnd.toString(), "stage10CaptureId" to "4793f38c-b729-4127-8a04-278bda9e4cdf",
            "stage10BaselineBundleSha256" to baseline.bundleSha256, "stage10BaselineProfileSha256" to baseline.profileSha256,
            "stage10BaselineFirstInstallTime" to before.toString(), "stage10BaselineLastUpdateTime" to before.toString(),
            "stage10BaselineCapturedAt" to baseline.capturedAt.toString())
        fun request(a: Map<String,String>) = Stage10EmulatorContract.request(a, Stage10UpgradeContract.QA,
            "${Stage10UpgradeContract.QA}.test", Stage10UpgradeContract.CERT, Stage10UpgradeContract.APK8)
        assertEquals(8, request(args).room)
        assertEquals(baseline, Stage10EmulatorContract.profileBaseline(args))
        rejected { request(args - "stage10BaselineBundleSha256") }
        rejected { request(args + ("stage10EmulatorOperation" to "seed-v6")) }
        rejected { request(args + ("unexpected" to "value")) }
        rejected { request(args + ("stage10BaselineProfileSha256" to "invalid")) }
    }
    @Test fun extraFileAndMissingOnboardingNeverBecomeAnIgnoredDirectory() {
        val selected = setOf(Stage10EmulatorContract.ONBOARDING, "files/synthetic.png")
        Stage10EmulatorContract.requireSelection(selected, selected)
        rejected { Stage10EmulatorContract.requireSelection(selected - Stage10EmulatorContract.ONBOARDING, selected) }
        rejected { Stage10EmulatorContract.requireSelection(selected + "files/datastore/unexpected.preferences_pb", selected) }
        rejected { Stage10EmulatorContract.requireAnnex("files/arbitrary", byteArrayOf(1), started) }
    }
    @Test fun platformCounterHasOnlyItsExactSingleField() {
        val valid = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <int name=\"IDSCount\" value=\"2\" />\n</map>\n".toByteArray()
        Stage10EmulatorContract.requireAnnex(Stage10UpgradeContract.PLATFORM_IDS_PATH, valid, started)
        rejected { Stage10EmulatorContract.requireAnnex(Stage10UpgradeContract.PLATFORM_IDS_PATH, valid + 0, started) }
        rejected { Stage10EmulatorContract.requireAnnex(Stage10UpgradeContract.PLATFORM_IDS_PATH, String(valid).replace("IDSCount", "private").toByteArray(), started) }
    }
    @Test fun separateRequestRejectsPublicDestinationWrongArtifactAndExtraArguments() {
        val args = mapOf("stage10EmulatorOperation" to "seed-v6", "stage10Consent" to Stage10EmulatorContract.CONSENT,
            "stage10ScenarioId" to scenario.id, "stage10StartedAt" to started.toString(), "stage10ShortEnd" to scenario.shortEnd.toString(),
            "stage10LongEnd" to scenario.longEnd.toString(), "stage10CaptureId" to "4793f38c-b729-4127-8a04-278bda9e4cdf")
        fun request(a: Map<String,String> = args, pkg: String = Stage10UpgradeContract.QA, apk: String = Stage10UpgradeContract.APK6) =
            Stage10EmulatorContract.request(a, pkg, "${Stage10UpgradeContract.QA}.test", Stage10UpgradeContract.CERT, apk)
        assertEquals(6, request().room)
        rejected { request(pkg = "com.blackatsystems.miguardia") }
        rejected { request(apk = Stage10UpgradeContract.APK8) }
        rejected { request(args + ("stage10Operation" to "seed-v6")) }
        rejected { request(args + ("stage10EmulatorOperation" to "clear")) }
        rejected { request(args + ("stage10Consent" to Stage10UpgradeContract.CONSENT)) }
    }
}
