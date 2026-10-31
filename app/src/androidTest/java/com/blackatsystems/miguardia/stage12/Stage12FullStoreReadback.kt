package com.blackatsystems.miguardia.stage12

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Base64
import java.io.File
import org.w3c.dom.Element
import org.w3c.dom.Node

internal data class Stage12TableSnapshot(val columns: List<String>, val rows: List<List<String>>)
internal data class Stage12StoreSnapshot(
    val tables: Map<String, Stage12TableSnapshot>,
    val preferences: Map<String, String>,
    val files: Map<String, Stage12FileSnapshot>,
)

/** Independent, read-only QA observation. Values remain in memory; receipts use only digests/counts. */
internal object Stage12FullStoreReadback {
    private const val QA = "com.blackatsystems.miguardia.qa"
    private const val DATABASE = "miguardia-v2.db"

    fun text(value: String): String = "T:" + base64(value.toByteArray(Charsets.UTF_8))
    fun integer(value: Long): String = "I:$value"

    fun capture(context: Context): Stage12StoreSnapshot = try {
        captureChecked(context)
    } catch (error: Exception) {
        // JUnit prints exception causes/suppressed entries. Keep paths, SQL and preference
        // values private even when Android/NIO/parser errors contain them in messages.
        val code = error.message?.takeIf { it.matches(Regex("READBACK_[A-Z0-9_]+")) }
            ?: "READBACK_CAPTURE_FAILED"
        throw IllegalStateException(code)
    }

    private fun captureChecked(context: Context): Stage12StoreSnapshot {
        check(context.packageName == QA) { "READBACK_CONTEXT_NOT_QA" }
        val root = File(context.applicationInfo.dataDir).canonicalFile
        val inventory = Stage12PrivateInventory(root, context.applicationInfo.nativeLibraryDir?.let(::File))
        val database = inventory.databaseFile()
        check(context.getDatabasePath(DATABASE).canonicalFile == database) { "READBACK_DATABASE_NOT_EXISTING" }
        val first = inventory.capture()
        val preferences = readPreferences(inventory, first)
        val tables = readDatabase(database)
        inventory.databaseFile()
        val second = inventory.capture()
        check(first == second) { "READBACK_FILES_CHANGED_DURING_SNAPSHOT" }
        return Stage12StoreSnapshot(tables, preferences, second)
    }

    private fun readDatabase(file: File): Map<String, Stage12TableSnapshot> {
        val db = SQLiteDatabase.openDatabase(file.path, null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        check(db.isReadOnly) { "READBACK_DATABASE_NOT_READONLY" }
        var primary: Throwable? = null
        try {
            // API 35+ exposes a real read-only DEFERRED transaction. execSQL("BEGIN ...")
            // may be intercepted by SQLiteSession with different transaction semantics.
            check(Build.VERSION.SDK_INT >= 35) { "READBACK_READONLY_TRANSACTION_API_UNAVAILABLE" }
            db.beginTransactionReadOnly()
            val names = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            check(names.isNotEmpty() && "shifts" in names && "shift_work_snapshots" in names) { "READBACK_WRONG_DATABASE_SCHEMA" }
            val result = linkedMapOf<String, Stage12TableSnapshot>()
            (names + "sqlite_master").distinct().sorted().forEach { name ->
                val escaped = name.replace("\"", "\"\"")
                result[name] = db.rawQuery("SELECT * FROM \"$escaped\"", null).use { cursor ->
                    val rows = buildList {
                        while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cell(cursor, it) })
                    }.sortedBy { row -> row.joinToString("\u0000") }
                    Stage12TableSnapshot(cursor.columnNames.toList(), rows)
                }
            }
            db.setTransactionSuccessful()
            db.endTransaction()
            return result
        } catch (error: Throwable) {
            primary = error
            try { if (db.inTransaction()) db.endTransaction() } catch (secondary: Throwable) { retain(error, secondary) }
            throw error
        } finally {
            try { db.close() } catch (secondary: Throwable) {
                if (primary == null) throw secondary else retain(primary, secondary)
            }
        }
    }

    private fun cell(cursor: Cursor, column: Int): String = when (cursor.getType(column)) {
        Cursor.FIELD_TYPE_NULL -> "N"
        Cursor.FIELD_TYPE_INTEGER -> integer(cursor.getLong(column))
        Cursor.FIELD_TYPE_FLOAT -> "R:" + java.lang.Double.doubleToRawLongBits(cursor.getDouble(column)).toString(16)
        Cursor.FIELD_TYPE_STRING -> text(cursor.getString(column))
        Cursor.FIELD_TYPE_BLOB -> "B:" + base64(cursor.getBlob(column))
        else -> error("READBACK_UNKNOWN_SQLITE_TYPE")
    }

    private fun readPreferences(inventory: Stage12PrivateInventory, files: Map<String, Stage12FileSnapshot>): Map<String, String> =
        linkedMapOf<String, String>().apply {
            files.keys.filter { files.getValue(it).kind == Stage12PrivateNodeKind.FILE &&
                (it.startsWith("shared_prefs/") && it.endsWith(".xml") ||
                it.startsWith("files/datastore/") && it.endsWith(".preferences_pb")) }.sorted().forEach { relative ->
                val bytes = inventory.readVerifiedBytes(relative, files.getValue(relative))
                put("$relative#@file", "BYTES:${bytes.size}:${files.getValue(relative).sha256}")
                if (relative.endsWith(".preferences_pb")) {
                    // Generated protobuf is an implementation dependency, not a public compile API.
                    // Read its complete runtime message without opening a second DataStore for an app-owned file.
                    val messageClass = Class.forName("androidx.datastore.preferences.PreferencesProto\$PreferenceMap", false,
                        Stage12FullStoreReadback::class.java.classLoader)
                    val message = messageClass.getMethod("parseFrom", ByteArray::class.java).invoke(null, bytes)
                    val entries = messageClass.getMethod("getPreferencesMap").invoke(message) as? Map<*, *>
                        ?: error("READBACK_PREFERENCE_MAP_UNAVAILABLE")
                    check(entries.keys.all { it is String }) { "READBACK_PREFERENCE_KEY_TYPE" }
                    entries.entries.sortedBy { it.key as String }.forEach { entry ->
                        val key = entry.key as String
                        val value = checkNotNull(entry.value) { "READBACK_NULL_PREFERENCE" }
                        val kind = value.javaClass.getMethod("getValueCase").invoke(value) as? Enum<*>
                            ?: error("READBACK_PREFERENCE_TYPE_UNAVAILABLE")
                        val encoded = value.javaClass.getMethod("toByteArray").invoke(value) as? ByteArray
                            ?: error("READBACK_PREFERENCE_BYTES_UNAVAILABLE")
                        check(kind.name != "VALUE_NOT_SET") { "READBACK_UNTYPED_PREFERENCE" }
                        // The complete typed value, not the portable-settings projection.
                        put("$relative#${text(key)}", kind.name + ":" + base64(encoded))
                    }
                } else {
                    val document = Stage12PrivatePreferencesXml.parse(bytes)
                    val children = document.documentElement.childNodes
                    for (index in 0 until children.length) {
                        val element = children.item(index) as? Element ?: continue
                        val key = element.getAttribute("name")
                        check(key.isNotEmpty() && "$relative#${text(key)}" !in this) { "READBACK_DUPLICATE_SHARED_PREFERENCE" }
                        put("$relative#${text(key)}", xmlValue(element))
                    }
                }
            }
        }.toSortedMap()

    private fun xmlValue(element: Element): String {
        val attributes = (0 until element.attributes.length).map { element.attributes.item(it) }
            .filter { it.nodeName != "name" }.sortedBy(Node::getNodeName)
            .joinToString(";") { text(it.nodeName) + "=" + text(it.nodeValue) }
        val parts = (0 until element.childNodes.length).map { element.childNodes.item(it) }.mapNotNull { child ->
            when (child.nodeType) {
                Node.ELEMENT_NODE -> xmlValue(child as Element)
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> child.nodeValue.takeUnless(String::isBlank)?.let(::text)
                else -> null
            }
        }.let { if (element.tagName == "set") it.sorted() else it }
        return element.tagName + "[$attributes]" + parts.joinToString("|")
    }

    private fun base64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun retain(primary: Throwable, secondary: Throwable) {
        if (primary !== secondary && primary.suppressed.none { it === secondary }) primary.addSuppressed(secondary)
    }
}
