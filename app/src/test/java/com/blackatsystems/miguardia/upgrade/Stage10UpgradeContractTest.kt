package com.blackatsystems.miguardia.upgrade

import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Stage10UpgradeContractTest {
    private val assets = File("src/androidTest/assets/stage10")
    private val valid = mapOf("stage10Operation" to "seed-v6", "stage10Consent" to Stage10UpgradeContract.CONSENT,
        "stage10FixtureDay" to Stage10UpgradeContract.DAY, "stage10CaptureId" to "412b9a42-a80e-437d-b3bf-0f9a50721aa1")
    private fun request(args: Map<String,String> = valid, apk: String = Stage10UpgradeContract.APK6,
        pkg: String = Stage10UpgradeContract.QA, cert: String = Stage10UpgradeContract.CERT) =
        Stage10UpgradeContract.request(args, pkg, "${Stage10UpgradeContract.QA}.test", cert, apk)
    private fun rejected(block: () -> Unit) { try { block(); fail("Must reject") } catch (_: IllegalArgumentException) { } }

    @Test fun fixtureIsTheUnchangedStage9ConstructorWithThirtyTwoTypedRows() {
        val actual = assets.resolve("source-v6.bin").inputStream().use(Stage10UpgradeContract::fixture)
        assertEquals(UpgradeFixtureSnapshots.sourceV6(LocalDate.parse(Stage10UpgradeContract.DAY), assets.resolve("synthetic.png").length()), actual)
    }
    @Test fun exactlyFourSideEffectTogglesChangeBeforeBaseline() {
        val before = assets.resolve("preferences.bin").inputStream().use(com.blackatsystems.miguardia.core.domain.backup.BackupPayloadCodec::readPreferences)
        val after = assets.resolve("preferences.bin").inputStream().use(Stage10UpgradeContract::preferences)
        assertEquals(Stage10UpgradeContract.off, before.zip(after).filter { it.first != it.second }.map { it.first.key }.toSet())
        assertTrue(after.filter { it.key in Stage10UpgradeContract.off }.all { it.values == listOf("false") })
    }
    @Test fun sourceAndDestinationOperationsHaveDistinctArtifactGuards() {
        assertEquals(6, request().room)
        assertEquals(8, request(valid + ("stage10Operation" to "capture-v8"), Stage10UpgradeContract.APK8).room)
        rejected { request(valid + ("stage10Operation" to "capture-v8")) }
        rejected { request(apk = Stage10UpgradeContract.APK8) }
    }
    @Test fun publicPackageWrongSignerAndUnknownOperationsAreRejected() {
        rejected { request(pkg = "com.blackatsystems.miguardia") }
        rejected { request(cert = "0".repeat(64)) }
        rejected { request(valid + ("stage10Operation" to "clear")) }
    }
    @Test fun consentDateNonceAndUnexpectedArgumentsFailClosed() {
        listOf("stage10Consent", "stage10FixtureDay", "stage10CaptureId").forEach { key -> rejected { request(valid + (key to "invalid")) } }
        rejected { request(valid + ("class" to "arbitrary")) }
        rejected { request(valid - "stage10Consent") }
    }
    @Test fun everyPortablePhysicalKeyMustExistEvenIfTheDefaultWouldMatch() {
        Stage10UpgradeContract.requirePersistedKeys(Stage10UpgradeContract.requiredKeys)
        Stage10UpgradeContract.requiredKeys.forEach { (store, keys) ->
            keys.forEach { key -> rejected { Stage10UpgradeContract.requirePersistedKeys(Stage10UpgradeContract.requiredKeys + (store to (keys - key))) } }
        }
    }
    @Test fun onlyTwoExactTechnicalNamesMayAccompanyEverySelectedRawFile() {
        val selected = setOf("files/datastore/guard_profile.preferences_pb.preferences_pb", "files/schedule_photos/fixture.png")
        Stage10UpgradeContract.requirePrivateSelection(selected, selected)
        Stage10UpgradeContract.requirePrivateSelection(selected + Stage10UpgradeContract.technicalPaths, selected, captureV8 = true)
        rejected { Stage10UpgradeContract.requirePrivateSelection(selected + Stage10UpgradeContract.technicalPaths, selected) }
        rejected { Stage10UpgradeContract.requirePrivateSelection(selected + "files/profileInstalled", selected, captureV8 = true) }
        rejected { Stage10UpgradeContract.requirePrivateSelection(selected.drop(1).toSet() + Stage10UpgradeContract.technicalPaths, selected, captureV8 = true) }
        rejected { Stage10UpgradeContract.requirePrivateSelection(selected + "shared_prefs/other.xml", selected, captureV8 = true) }
        rejected { Stage10UpgradeContract.requirePrivateSelection(selected + "files/profileInstalled.bak", selected, captureV8 = true) }
    }
    @Test fun profileVerifierCacheMustMatchItsExactFormatAndInstalledUpdateTime() {
        val observedUpdate = 1792705648157L
        fun cache(schema: Int = 1, result: Int = 327680, updated: Long = observedUpdate, size: Long = 0L) =
            java.nio.ByteBuffer.allocate(24).putInt(schema).putInt(result).putLong(updated).putLong(size).array()
        val path = "files/profileInstalled"
        Stage10UpgradeContract.requireTechnicalFile(path, cache(), observedUpdate)
        listOf(cache(schema = 2), cache(result = 0), cache(updated = 124L), cache(size = 1L), cache() + byteArrayOf(0)).forEach {
            rejected { Stage10UpgradeContract.requireTechnicalFile(path, it, observedUpdate) }
        }
        rejected { Stage10UpgradeContract.requireTechnicalFile(path, cache(), observedUpdate + 1L) }
    }
    @Test fun deferredQueueMustBeExactEmptyRawXmlAndUnknownFilesFail() {
        val empty = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map />\n".toByteArray()
        Stage10UpgradeContract.requireTechnicalFile("shared_prefs/notification_deferred_actions.xml", empty, 123L)
        rejected { Stage10UpgradeContract.requireTechnicalFile("shared_prefs/notification_deferred_actions.xml", "<map><long name='pending_rebuild' value='1'/></map>".toByteArray(), 123L) }
        rejected { Stage10UpgradeContract.requireTechnicalFile("files/unknown", empty, 123L) }
    }
    private fun source() = assets.resolve("source-v6.bin").inputStream().use(Stage10UpgradeContract::fixture)
    private fun wanted() = assets.resolve("preferences.bin").inputStream().use(Stage10UpgradeContract::preferences)
    private fun withoutDismissal() = wanted().map { if (it.key == Stage10UpgradeContract.DISMISSED_KEY) it.copy(values = emptyList()) else it }
    @Test fun approvedExpiryOperationIsBoundToV8AndDoesNotChangeStrictCapture() {
        val operation = valid + ("stage10Operation" to Stage10UpgradeContract.EXPIRY_OPERATION)
        assertEquals(8, request(operation, Stage10UpgradeContract.APK8).room)
        rejected { request(operation, Stage10UpgradeContract.APK6) }
        rejected { Stage10UpgradeContract.requireCapturedPreferences(withoutDismissal(), wanted(), source(), Long.MAX_VALUE, false) }
    }
    @Test fun liveDismissalSurvivesUntilExactEffectiveEnd() {
        val source = source()
        val end = Stage10UpgradeContract.dismissalEnd(source, Stage10UpgradeContract.KNOWN_DISMISSED)
        val actual = source.table("shift_actual_records")
        assertEquals((actual.records.single().values[actual.columns.indexOf("actualEndEpochMillis")] as com.blackatsystems.miguardia.core.domain.backup.BackupValue.Integer).value, end)
        rejected { Stage10UpgradeContract.requireCapturedPreferences(withoutDismissal(), wanted(), source, end - 1, true) }
        assertEquals(setOf(Stage10UpgradeContract.KNOWN_DISMISSED), Stage10UpgradeContract.requireCapturedPreferences(withoutDismissal(), wanted(), source, end, true))
        assertEquals(emptySet<String>(), Stage10UpgradeContract.requireCapturedPreferences(wanted(), wanted(), source, end - 1, true))
    }
    @Test fun expiryCannotMaskDurablePreferenceLossNewIdsOrWrongTypes() {
        val source = source()
        val actual = withoutDismissal()
        actual.filter { it.key != Stage10UpgradeContract.DISMISSED_KEY }.forEach { item ->
            rejected { Stage10UpgradeContract.requireCapturedPreferences(actual.map { if (it.key == item.key) it.copy(values = listOf("CHANGED")) else it }, wanted(), source, Long.MAX_VALUE, true) }
        }
        rejected { Stage10UpgradeContract.requireCapturedPreferences(actual.drop(1), wanted(), source, Long.MAX_VALUE, true) }
        rejected { Stage10UpgradeContract.requireCapturedPreferences(actual.map { if (it.key == Stage10UpgradeContract.DISMISSED_KEY) it.copy(values = listOf("shift:11111111-1111-4111-8111-111111111111")) else it }, wanted(), source, Long.MAX_VALUE, true) }
        rejected { Stage10UpgradeContract.requireCapturedPreferences(actual.map { if (it.key == Stage10UpgradeContract.DISMISSED_KEY) it.copy(type = com.blackatsystems.miguardia.core.domain.backup.BackupPreferenceType.TEXT) else it }, wanted(), source, Long.MAX_VALUE, true) }
    }
    @Test fun observedPlatformCounterIsOnlyAnExplicitExpiryCaptureAnnex() {
        val bytes = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <int name=\"IDSCount\" value=\"1\" />\n</map>\n".toByteArray()
        Stage10UpgradeContract.requirePlatformIds(bytes)
        rejected { Stage10UpgradeContract.requirePlatformIds(String(bytes).replace("value=\"1\"", "value=\"2\"").toByteArray()) }
        rejected { Stage10UpgradeContract.requirePlatformIds(bytes + byteArrayOf(0)) }
        val selected = setOf("files/selected")
        val actual = selected + Stage10UpgradeContract.technicalPaths + Stage10UpgradeContract.PLATFORM_IDS_PATH
        Stage10UpgradeContract.requirePrivateSelection(actual, selected, captureV8 = true, allowPlatformIds = true)
        rejected { Stage10UpgradeContract.requirePrivateSelection(actual, selected, captureV8 = true) }
        rejected { Stage10UpgradeContract.requirePrivateSelection(actual + "shared_prefs/another.xml", selected, captureV8 = true, allowPlatformIds = true) }
    }
}
