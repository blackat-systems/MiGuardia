package com.blackatsystems.miguardia

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.blackatsystems.miguardia.core.domain.work.WorkConfigurationOrigin
import kotlinx.coroutines.runBlocking

/**
 * Gives activity-level V1 regression tests the same root produced by MIGRATION_5_6.
 * Fresh-install behavior is covered separately and must keep using a database with no root.
 */
internal fun ensureMigratedV1ActivityFixture(context: Context) {
    check(context.packageName == QA_APPLICATION_ID) {
        "El fixture MIGRATED_V1 sólo puede ejecutarse contra el paquete QA."
    }
    val application = context.applicationContext as MiGuardiaApplication
    val store = application.localDataStore
    val current = runBlocking { store.workConfiguration.get() }

    if (current == null) {
        val path = context.getDatabasePath(DATABASE_NAME)
        check(path.isFile) { "Room no creó la base QA antes de preparar el fixture V1." }
        SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            check(database.version == EXPECTED_SCHEMA_VERSION) {
                "El fixture V1 esperaba Room v$EXPECTED_SCHEMA_VERSION y encontró v${database.version}."
            }
            val values = ContentValues().apply {
                put("timelineId", MIGRATED_TIMELINE_ID)
                put("singletonSlot", 1)
                put("origin", WorkConfigurationOrigin.MIGRATED_V1.name)
            }
            database.insertOrThrow("work_configuration_roots", null, values)
        }
    }

    val prepared = runBlocking { store.workConfiguration.get() }
    check(prepared?.origin == WorkConfigurationOrigin.MIGRATED_V1) {
        "La prueba V1 encontró una configuración que no proviene de la migración."
    }
    check(prepared.timeline.revisions.isEmpty()) {
        "La prueba V1 requiere una raíz migrada sin activación V2."
    }
}

private const val DATABASE_NAME = "miguardia.db"
private const val QA_APPLICATION_ID = "com.blackatsystems.miguardia.qa"
private const val EXPECTED_SCHEMA_VERSION = 7
private const val MIGRATED_TIMELINE_ID = "00000000-0000-0000-0000-000000000100"
