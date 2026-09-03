package com.blackatsystems.miguardia.core.domain.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupMemoryBudgetTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun operationalBudgetUsesOneQuarterOfHeapUpToTheStableLogicalLimit() {
        assertEquals(
            16L * 1024L * 1024L,
            BackupMemoryBudget.operationalHeapBytes(64L * 1024L * 1024L),
        )
        assertEquals(
            MiGuardiaBackupContract.MAX_LOGICAL_BYTES,
            BackupMemoryBudget.operationalHeapBytes(2L * 1024L * 1024L * 1024L),
        )
    }

    @Test
    fun sharedEstimatorMatchesDecoderAtTheExactBoundaryAndRejectsOneByteLess() {
        val snapshot = snapshotWithDayStatus()
        val encoded = ByteArrayOutputStream().also { output ->
            BackupPayloadCodec.writeDatabase(snapshot, output)
        }.toByteArray()
        val estimate = estimateDecodedDatabaseBytes(snapshot)

        assertEquals(
            snapshot,
            BackupPayloadCodec.readDatabase(
                ByteArrayInputStream(encoded),
                decodedMemoryLimitBytes = estimate,
            ),
        )
        assertFails<InvalidBackupException> {
            BackupPayloadCodec.readDatabase(
                ByteArrayInputStream(encoded),
                decodedMemoryLimitBytes = estimate - 1L,
            )
        }
        assertEquals(estimate, BackupMemoryBudget.requireSnapshotFits(snapshot, estimate))
        assertFails<InvalidBackupException> {
            BackupMemoryBudget.requireSnapshotFits(snapshot, estimate - 1L)
        }
    }

    @Test
    fun containerExtractPropagatesTheCallerDecodedMemoryLimit() {
        val snapshot = snapshotWithDayStatus()
        val target = temporary.newFile("memory-limit.miguardia-backup")
        val work = temporary.newFolder("memory-limit-work")
        BackupContainer.create(
            target = target,
            workingDirectory = work,
            backupId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
            createdAtEpochMillis = 1_788_131_400_000L,
            zoneId = ZoneId.of("America/Argentina/Buenos_Aires"),
            database = snapshot,
            preferences = emptyList(),
            photoMode = BackupPhotoMode.OMITTED,
            photoAssets = emptyList(),
            password = null,
        )
        val estimate = estimateDecodedDatabaseBytes(snapshot)

        assertFails<InvalidBackupException> {
            BackupContainer.extract(target, work, null, decodedMemoryLimitBytes = estimate - 1L)
        }
        BackupContainer.extract(target, work, null, decodedMemoryLimitBytes = estimate).use { extracted ->
            assertEquals(snapshot, extracted.payload.database)
        }
    }

    @Test
    fun peakBudgetAcceptsTheExactSixSnapshotBoundaryAndRejectsOneByteLess() {
        val snapshot = emptySnapshot()
        val estimate = estimateDecodedDatabaseBytes(snapshot)
        val exactPeak = Math.multiplyExact(
            estimate,
            BackupMemoryBudget.RECOMMENDED_PEAK_FACTOR.toLong(),
        )

        assertEquals(
            exactPeak,
            BackupMemoryBudget.requirePeakFits(
                current = snapshot,
                incoming = snapshot,
                merged = snapshot,
                operationalBytes = exactPeak,
            ),
        )
        assertFails<InvalidBackupException> {
            BackupMemoryBudget.requirePeakFits(
                current = snapshot,
                incoming = snapshot,
                merged = snapshot,
                operationalBytes = exactPeak - 1L,
            )
        }
        assertEquals(
            estimate,
            BackupMemoryBudget.perSnapshotBytes(
                operationalBytes = exactPeak,
                peakFactor = BackupMemoryBudget.RECOMMENDED_PEAK_FACTOR,
            ),
        )
    }

    @Test
    fun potentialMergeIsRejectedBeforeAllocationAtTheFirstBytePastItsSnapshotBudget() {
        val current = emptySnapshot()
        val incoming = snapshotWithDayStatus()
        val exactUpperBound = Math.addExact(
            estimateDecodedDatabaseBytes(current),
            estimateDecodedDatabaseBytes(incoming),
        )

        assertEquals(
            exactUpperBound,
            BackupMemoryBudget.requirePotentialMergeFits(current, incoming, exactUpperBound),
        )
        assertFails<InvalidBackupException> {
            BackupMemoryBudget.requirePotentialMergeFits(current, incoming, exactUpperBound - 1L)
        }
    }

    @Test
    fun boundedOutputStopsBeforeWritingTheFirstBytePastTheLimit() {
        val target = ByteArrayOutputStream()
        val bounded = BoundedOutputStream(target, maximumBytes = 5L, section = "prueba")

        bounded.write(byteArrayOf(1, 2, 3, 4, 5))
        assertEquals(5L, bounded.writtenBytes)
        assertFails<InvalidBackupException> { bounded.write(6) }
        assertEquals(5, target.size())
    }

    @Test
    fun databaseAndPreferencesWritersAcceptTheirExactEncodedSizeAndRejectOneByteLess() {
        val snapshot = emptySnapshot()
        val databaseBytes = ByteArrayOutputStream().also { output ->
            BackupPayloadCodec.writeDatabase(snapshot, output)
        }.toByteArray()
        val preferences = listOf(
            BackupPreference("theme.mode", BackupPreferenceType.TEXT, listOf("DARK")),
        )
        val preferenceBytes = ByteArrayOutputStream().also { output ->
            BackupPayloadCodec.writePreferences(preferences, output)
        }.toByteArray()

        assertEquals(
            databaseBytes.size,
            ByteArrayOutputStream().also { output ->
                BackupPayloadCodec.writeDatabase(snapshot, output, databaseBytes.size.toLong())
            }.size(),
        )
        assertFails<InvalidBackupException> {
            BackupPayloadCodec.writeDatabase(
                snapshot,
                ByteArrayOutputStream(),
                databaseBytes.size.toLong() - 1L,
            )
        }
        assertEquals(
            preferenceBytes.size,
            ByteArrayOutputStream().also { output ->
                BackupPayloadCodec.writePreferences(preferences, output, preferenceBytes.size.toLong())
            }.size(),
        )
        assertFails<InvalidBackupException> {
            BackupPayloadCodec.writePreferences(
                preferences,
                ByteArrayOutputStream(),
                preferenceBytes.size.toLong() - 1L,
            )
        }
        assertTrue(databaseBytes.isNotEmpty())
        assertTrue(preferenceBytes.isNotEmpty())
    }

    private fun emptySnapshot(): BackupDatabaseSnapshot = BackupDatabaseSnapshot(
        timelineId = null,
        tables = MiGuardiaBackupSchemaV6.tables.map { spec ->
            BackupTable(spec.name, spec.columns, spec.primaryKey, emptyList())
        },
    )

    private fun snapshotWithDayStatus(): BackupDatabaseSnapshot = emptySnapshot().let { empty ->
        empty.copy(
            tables = empty.tables.map { table ->
                if (table.name == "explicit_day_statuses") {
                    table.copy(
                        records = listOf(
                            BackupRecord(
                                listOf(
                                    BackupValue.Text("2026-09-01"),
                                    BackupValue.Text("DAY_OFF"),
                                ),
                            ),
                        ),
                    )
                } else {
                    table
                }
            },
        )
    }

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError("Se esperaba ${T::class.java.simpleName}, llegó ${error::class.java.simpleName}", error)
        }
        throw AssertionError("Se esperaba ${T::class.java.simpleName}")
    }
}
