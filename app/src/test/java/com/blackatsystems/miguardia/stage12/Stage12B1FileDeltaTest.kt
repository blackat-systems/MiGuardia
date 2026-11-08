package com.blackatsystems.miguardia.stage12

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class Stage12B1FileDeltaTest {
    private val run = "b7a1f779-b389-488f-ae3e-0c2d369a3dc3"
    private val old = Stage12FileSnapshot(1, "a".repeat(64))
    private val next = Stage12FileSnapshot(2, "b".repeat(64))

    @Test fun recordsAddRemoveModifyWithBothOriginalReadbacksAndOpaqueIdentity() {
        val secret = "files/PRIVATE_B1_FILENAME"
        val before = mapOf(secret to old, "cache/removed" to old, "files/unchanged" to old)
        val after = mapOf(secret to next, "code_cache/new" to next, "files/unchanged" to old)
        val result = Stage12B1FileDelta.project(before, after, run)
        assertEquals(3, result.total); assertEquals(1, result.added); assertEquals(1, result.removed); assertEquals(1, result.modified)
        assertEquals(2, result.receivedChanged); assertFalse(result.truncated)
        val token = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val entry = result.entries.single { it.pathToken == token }
        assertEquals(old, entry.before); assertEquals(next, entry.after)
        assertFalse(result.toString().contains(secret)); assertFalse(result.toString().contains("PRIVATE_B1_FILENAME"))
    }
    @Test fun onlyTheExactProfileInstallerMarkerIsNotAReceivedChange() {
        val marker = Stage12B1FileDelta.project(mapOf("files/profileInstalled" to old), mapOf("files/profileInstalled" to next), run)
        assertEquals(Stage12B1FileDelta.Role.PROFILE_INSTALLER_MARKER, marker.entries.single().role)
        assertEquals(1, marker.modified); assertEquals(0, marker.receivedChanged)
        for (path in listOf("files/profileInstalled.bak", "files/x/profileInstalled", "no_backup/profileInstalled")) {
            val other = Stage12B1FileDelta.project(mapOf(path to old), mapOf(path to next), run)
            assertEquals(Stage12B1FileDelta.Role.APP_STATE, other.entries.single().role)
            assertEquals(1, other.receivedChanged)
        }
    }
    @Test fun rolesDescribeCurrentSourcePathsWithoutWaivingReceivedChanges() {
        val roles = mapOf("cache/x" to Stage12B1FileDelta.Role.APP_CACHE, "code_cache/x" to Stage12B1FileDelta.Role.CODE_CACHE,
            "shared_prefs/p.xml" to Stage12B1FileDelta.Role.PREFERENCE_PRIMARY,
            "shared_prefs/p.xml.bak" to Stage12B1FileDelta.Role.PREFERENCE_BACKUP,
            "files/datastore/p.preferences_pb" to Stage12B1FileDelta.Role.PREFERENCE_PRIMARY,
            "files/datastore/p.preferences_pb.bak" to Stage12B1FileDelta.Role.PREFERENCE_BACKUP,
            "files/reports/artifacts/stage12-normal-$run/a" to Stage12B1FileDelta.Role.OWN_REPORT,
            "files/reports/artifacts/stage12-normal-${run}extra/a" to Stage12B1FileDelta.Role.APP_STATE,
            "other/private" to Stage12B1FileDelta.Role.UNKNOWN)
        roles.forEach { (path, role) ->
            val result = Stage12B1FileDelta.project(mapOf(path to old), mapOf(path to next), run)
            assertEquals(role, result.entries.single().role)
            assertEquals(1, result.receivedChanged)
            assertEquals(if (role == Stage12B1FileDelta.Role.UNKNOWN) 1 else 0, result.unknown)
        }
    }
    @Test fun boundedDetailsKeepFullCountsAndExplicitTruncation() {
        val before = (1..3).associate { "files/$it" to old }
        val result = Stage12B1FileDelta.project(before, emptyMap(), run, 1)
        assertEquals(3, result.total); assertEquals(3, result.receivedChanged); assertEquals(3, result.removed)
        assertEquals(1, result.entries.size); assertTrue(result.truncated)
    }
    @Test fun refusesUnboundRunAndNoncanonicalRelativeKeysBeforeProjection() {
        for (path in listOf("/absolute", "../escape", "files/../escape", "files//x", "files\\x")) {
            assertThrows(IllegalArgumentException::class.java) { Stage12B1FileDelta.project(mapOf(path to old), emptyMap(), run) }
        }
        assertThrows(IllegalArgumentException::class.java) { Stage12B1FileDelta.project(emptyMap(), emptyMap(), "not-a-run") }
        assertThrows(IllegalArgumentException::class.java) { Stage12B1FileDelta.project(emptyMap(), emptyMap(), run, 0) }
    }
    @Test fun unchangedFilesAndDirectoriesProduceNoDifference() {
        val before = mapOf("files/x" to old, "files" to Stage12FileSnapshot(0, "a".repeat(64), Stage12PrivateNodeKind.DIRECTORY))
        assertEquals(0, Stage12B1FileDelta.project(before, before.toMap(), run).total)
    }
}
