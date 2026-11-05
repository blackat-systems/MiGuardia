package com.blackatsystems.miguardia.stage12

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.blackatsystems.miguardia.core.domain.model.RecurringPattern
import com.blackatsystems.miguardia.core.domain.model.RecurringPlanAggregate
import java.io.File
import java.nio.file.Files
import java.util.UUID

/** SETUP ONLY: inserts an explicit synthetic history, never an operation under test. */
internal object Stage12RecurringHistoryProducer {
    /** Explicit fault fixture in the separate negative case; never removed/retried to make it pass. */
    fun installRollback(context: Context, binding: Stage12RecurringPaths, target: UUID): String {
        check(context.packageName == "com.blackatsystems.miguardia.qa")
        check(binding.ownCase == Stage12RecurringCase.ROLLBACK)
        binding.requireStored(stage12RecurringPathAccess(context))
        val file = binding.database
        val sql = "CREATE TRIGGER stage12_recurring_expected_rollback BEFORE INSERT ON shifts WHEN NEW.id = '$target' BEGIN SELECT RAISE(ABORT, 'STAGE12_OWN_RECURRING_ROLLBACK'); END"
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.rawQuery("SELECT name FROM sqlite_master WHERE name = 'stage12_recurring_expected_rollback'", null).use { check(!it.moveToFirst()) }
            db.execSQL(sql)
        }
        return sql
    }

    fun insert(context: Context, binding: Stage12RecurringPaths, plans: List<RecurringPlanAggregate>) {
        check(context.packageName == "com.blackatsystems.miguardia.qa")
        binding.requireStored(stage12RecurringPathAccess(context))
        val file = binding.database
        check(plans.isNotEmpty() && plans.map { it.plan.id }.distinct().size == plans.size)
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        Stage12RecurringHistoryClose.run(body = {
            db.setForeignKeyConstraintsEnabled(true)
            db.beginTransaction()
            fun insert(table: String, fields: Map<String, Any?>) {
                check(table in setOf("recurring_plans", "recurring_plan_revisions", "recurring_occurrences"))
                val columns = db.rawQuery("PRAGMA table_info(\"$table\")", null).use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
                }
                check(columns == fields.keys) { "RECURRING_SETUP_SCHEMA_DIFFERS" }
                val values = ContentValues()
                fields.forEach { (key, value) -> when (value) {
                    null -> values.putNull(key)
                    is String -> values.put(key, value)
                    is Long -> values.put(key, value)
                    is Int -> values.put(key, value)
                    else -> error("RECURRING_SETUP_VALUE_TYPE")
                } }
                check(db.insertOrThrow(table, null, values) != -1L)
            }
            plans.forEach { aggregate ->
                val p = aggregate.plan
                insert("recurring_plans", mapOf("id" to p.id.toString(), "timelineId" to p.timelineId.toString(),
                    "sector" to p.sector.name, "createdAtEpochMillis" to p.createdAt.toEpochMilli()))
                aggregate.revisions.forEach { r ->
                    val pattern = r.pattern
                    insert("recurring_plan_revisions", mapOf(
                        "id" to r.id.toString(), "planId" to r.planId.toString(), "revisionNumber" to r.revisionNumber,
                        "effectiveFrom" to r.effectiveFrom.toString(), "kind" to r.kind.name,
                        "endDateInclusive" to r.endDateInclusive.toString(),
                        "patternKind" to when (pattern) {
                            is RecurringPattern.Weekdays -> "WEEKDAYS"
                            is RecurringPattern.EveryNDays -> "EVERY_N_DAYS"
                            is RecurringPattern.EveryNWeeks -> "EVERY_N_WEEKS"
                            is RecurringPattern.Monthly -> "MONTHLY"
                        },
                        "weekdaysMask" to (pattern as? RecurringPattern.Weekdays)?.days?.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) },
                        "intervalCount" to when (pattern) { is RecurringPattern.EveryNDays -> pattern.intervalCount; is RecurringPattern.EveryNWeeks -> pattern.intervalCount; else -> null },
                        "monthlyOrdinal" to (pattern as? RecurringPattern.Monthly)?.ordinal?.name,
                        "monthlyDayOfWeek" to (pattern as? RecurringPattern.Monthly)?.dayOfWeek?.value,
                        "templateId" to r.templateId.toString(), "workPlaceId" to r.workPlaceId.toString(),
                        "objectiveId" to r.objectiveId.toString(), "workTypeId" to r.workTypeId.toString(),
                        "objectiveNameSnapshot" to r.objectiveNameSnapshot, "objectiveAbbreviationSnapshot" to r.objectiveAbbreviationSnapshot,
                        "objectiveAddressSnapshot" to r.objectiveAddressSnapshot, "workTypeNameSnapshot" to r.workTypeNameSnapshot,
                        "workTypeBehaviorSnapshot" to r.workTypeBehaviorSnapshot.name, "startTimeSnapshot" to r.startTimeSnapshot.toString(),
                        "endTimeSnapshot" to r.endTimeSnapshot.toString(), "colorArgbSnapshot" to r.colorArgbSnapshot,
                        "positionSnapshot" to r.positionSnapshot, "zoneId" to r.zoneId.id, "createdAtEpochMillis" to r.createdAt.toEpochMilli()))
                }
                aggregate.occurrences.forEach { o ->
                    insert("recurring_occurrences", mapOf("planId" to o.planId.toString(), "localDate" to o.localDate.toString(),
                        "revisionId" to o.revisionId.toString(), "shiftId" to o.shiftId?.toString(), "state" to o.state.name,
                        "createdAtEpochMillis" to o.createdAt.toEpochMilli(), "updatedAtEpochMillis" to o.updatedAt.toEpochMilli()))
                }
            }
            db.rawQuery("PRAGMA foreign_key_check", null).use { check(!it.moveToFirst()) { "RECURRING_SETUP_FOREIGN_KEYS" } }
            db.rawQuery("PRAGMA integrity_check", null).use { check(it.moveToFirst() && it.getString(0) == "ok" && !it.moveToNext()) { "RECURRING_SETUP_INTEGRITY" } }
            db.setTransactionSuccessful()
        }, finishTransaction = { if (db.inTransaction()) db.endTransaction() }, close = { db.close() })
    }
}
