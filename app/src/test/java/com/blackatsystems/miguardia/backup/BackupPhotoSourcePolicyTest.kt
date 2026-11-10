package com.blackatsystems.miguardia.backup

import org.junit.Assert.*
import org.junit.Test

class BackupPhotoSourcePolicyTest {
    @Test fun replaceAllAndJournalNeverFallBackToCurrentBytes() {
        val policy = BackupPhotoSourcePolicy.INCOMING_ONLY
        assertEquals("backup", policy.select("current", "backup", true))
        assertEquals("backup", policy.select("current", "backup", false))
        assertNull(policy.select("current", null, true))
        assertNull(policy.select("current", null, false))
    }

    @Test fun mergePreservesResolvedCurrentAndIncomingOrigins() {
        val policy = BackupPhotoSourcePolicy.MERGE_BY_RECORD
        assertEquals("current", policy.select("current", "backup", true))
        assertEquals("backup", policy.select(null, "backup", true))
        assertEquals("current", policy.select("current", null, true))
        assertEquals("backup", policy.select("current", "backup", false))
    }
}
