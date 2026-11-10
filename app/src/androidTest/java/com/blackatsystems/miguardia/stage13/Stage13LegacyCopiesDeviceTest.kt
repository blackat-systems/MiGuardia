package com.blackatsystems.miguardia.stage13

import android.graphics.Color
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.blackatsystems.miguardia.core.domain.backup.BackupDatabaseSnapshot
import com.blackatsystems.miguardia.core.domain.backup.BackupEntryManifest
import com.blackatsystems.miguardia.core.domain.backup.BackupManifest
import com.blackatsystems.miguardia.core.domain.backup.BackupPayloadCodec
import com.blackatsystems.miguardia.core.domain.backup.BackupPhotoMetadata
import com.blackatsystems.miguardia.core.domain.backup.BackupPhotoMode
import com.blackatsystems.miguardia.core.domain.backup.BackupRecord
import com.blackatsystems.miguardia.core.domain.backup.BackupTable
import com.blackatsystems.miguardia.core.domain.backup.BackupValue
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupContract
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupSchemaV7
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupSchemaV8
import com.blackatsystems.miguardia.stage13.Stage13Pickers.Kind
import com.blackatsystems.miguardia.upgrade.LegacyUpgradeFixtureContainer
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import com.blackatsystems.miguardia.upgrade.UpgradeFixtureSnapshots as U

/**
 * A188: Copias genuinas de las versiones 5, 6 y 7 (mismas fixtures que `Stage11RestoreInstrumentedTest`) se leen
 * por la pantalla real de Copias y se restauran con reemplazo consciente sobre la base de la corrida. La forma V8
 * esperada se declara aquí, independiente de los adaptadores de lectura.
 */
class Stage13LegacyCopiesDeviceTest : Stage13DeviceBase() {
    @Test fun version5CopyIsReadAndRestoredExactly() = legacy(5)
    @Test fun version6CopyIsReadAndRestoredExactly() = legacy(6)
    @Test fun version7CopyIsReadAndRestoredExactly() = legacy(7)

    private fun legacy(version: Int) {
        val photo = writePng(graph.runDocument(U.PHOTO_STORAGE_KEY), U.PHOTO_WIDTH, U.PHOTO_HEIGHT, Color.BLUE)
        var database = when (version) {
            5 -> U.legacyV5(DAY, photo.length())
            6 -> U.sourceV6(DAY, photo.length())
            else -> U.legacyV7(DAY, photo.length())
        }
        val files = linkedMapOf(U.PHOTO_STORAGE_KEY to photo)
        if (version == 7) {
            val attachment = writePng(graph.runDocument(NOTE_KEY), 2, 2, Color.GREEN)
            files[NOTE_KEY] = attachment
            database = database.rows("shift_note_attachments", listOf(BackupRecord(listOf(t(ATTACHMENT_ID),
                t(U.NOTE_ID), t(NOTE_KEY), t("image/png"), n(attachment.length()), n(2), n(2), n(0), n(NOW), n(NOW)))))
            MiGuardiaBackupSchemaV7.requireValid(database)
        }
        val expectedTables = MiGuardiaBackupSchemaV8.tables.map { spec ->
            val old = database.tables.find { it.name == spec.name }
            if (old == null) BackupTable(spec.name, spec.columns, spec.primaryKey, emptyList())
            else if (version == 5 && spec.name == "objectives") old.copy(columns = spec.columns,
                records = old.records.map { it.copy(values = it.values + listOf(BackupValue.Null, BackupValue.Null)) })
            else old
        }
        val container = graph.runDocument("genuina-v$version.miguardia-backup")
        writeLegacyContainer(container, database, files)

        openDrawerAction("drawer-action-backups")
        waitTag("backup-choose-source")
        pickers.answer(Kind.OPEN_DOCUMENT, graph.contentUri(container))
        compose.onNodeWithTag("backup-choose-source").performScrollTo().performClick()
        waitTag("backup-preview")
        waitText("datos internos Room V$version", hasAnyAncestor(hasTestTag("backup-preview")), substring = true)
        // Otra línea temporal que la de la corrida: combinar bloqueado, reemplazo consciente con frase exacta.
        compose.onNodeWithTag("backup-choose-merge").assertIsNotEnabled()
        compose.onNodeWithTag("backup-choose-replace").performScrollTo().performClick()
        compose.onNodeWithTag("backup-replace-confirmation").performTextReplacement("Reemplazar todo")
        compose.onNodeWithTag("backup-apply-replace").performScrollTo().performClick()
        waitTag("backup-success")
        compose.onNodeWithTag("backup-finish").performScrollTo().performClick()
        waitFor { !exists(hasTestTag("backup-surface")) }

        val restored = runBlocking { graph.store.backups.capture() }
        assertEquals(8, restored.roomVersion)
        expectedTables.forEach { table ->
            val actual = restored.tables.single { it.name == table.name }
            assertEquals("V$version ${table.name}: columnas", table.columns, actual.columns)
            assertEquals("V$version ${table.name}: filas", table.records, actual.records)
        }
        assertEquals("V$version: sin tablas inventadas", expectedTables.map { it.name }.toSet(), restored.tables.map { it.name }.toSet())
        val photos = File(graph.runContext.filesDir, "schedule_photos").listFiles().orEmpty().filter { it.isFile }
            .associate { it.name to sha256(it) }
        assertEquals("V$version: bytes de imágenes", files.mapValues { sha256(it.value) }, photos)
    }

    /** Contenedor heredado como lo arma `Stage11RestoreInstrumentedTest.legacyContainer`. */
    private fun writeLegacyContainer(target: File, database: BackupDatabaseSnapshot, files: Map<String, File>) {
        fun encoded(block: (java.io.OutputStream) -> Unit) = ByteArrayOutputStream().also(block).toByteArray()
        val prefs = runBlocking { graph.portablePreferences.capture(graph.store.backups.capture()) }
        val assets = files.map { (key, file) ->
            BackupPhotoMetadata(if (key == U.PHOTO_STORAGE_KEY) U.PHOTO_ID else ATTACHMENT_ID, key, "image/png", file.length(),
                if (key == U.PHOTO_STORAGE_KEY) U.PHOTO_WIDTH else 2, if (key == U.PHOTO_STORAGE_KEY) U.PHOTO_HEIGHT else 2, sha256(file))
        }
        val entries = linkedMapOf("database.bin" to LegacyUpgradeFixtureContainer.databaseBytes(database),
            "preferences.bin" to encoded { BackupPayloadCodec.writePreferences(prefs, it) },
            "photos.bin" to encoded { BackupPayloadCodec.writePhotos(assets, it) })
        files.forEach { (key, file) -> entries["photos/$key"] = file.readBytes() }
        val manifest = BackupManifest(UUID.randomUUID().toString(), NOW, U.ZONE_ID, database.roomVersion,
            database.timelineId, BackupPhotoMode.INCLUDED, database.tables.associate { it.name to it.records.size },
            entries.map { (name, bytes) -> BackupEntryManifest(name, bytes.size.toLong(), sha256(bytes)) }.sortedBy { it.name })
        entries["manifest.bin"] = encoded { BackupPayloadCodec.writeManifest(manifest, it) }
        LegacyUpgradeFixtureContainer.write(target, entries, NOW)
        check(MiGuardiaBackupContract.FILE_EXTENSION.isNotEmpty())
    }

    private fun BackupDatabaseSnapshot.rows(name: String, records: List<BackupRecord>) =
        copy(tables = tables.map { if (it.name == name) it.copy(records = records) else it })

    private fun t(value: String) = BackupValue.Text(value)
    private fun n(value: Long) = BackupValue.Integer(value)
    private fun n(value: Int) = BackupValue.Integer(value.toLong())

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 23)
        const val NOW = 1792728000000L
        const val ATTACHMENT_ID = "50000000-0000-4000-8000-000000000001"
        const val NOTE_KEY = "note_50000000-0000-4000-8000-000000000001.png"
    }
}
