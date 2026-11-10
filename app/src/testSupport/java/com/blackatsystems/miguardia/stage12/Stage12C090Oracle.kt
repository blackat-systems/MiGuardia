package com.blackatsystems.miguardia.stage12

/** Exact canonical rows stay in memory. Failures must never print either map. */
internal object Stage12C090Oracle {
    data class Table(val columns: List<String>, val rows: List<List<String>>)
    data class Removal(val table: String, val column: String, val values: Set<String>)

    val assertionKeys = listOf(
        "C090-contrato-completo.ordinal", "C090-contrato-completo.target",
        "C090-contrato-completo.cancel", "C090-contrato-completo.confirm",
        "C090-contrato-completo.first-files", "C090-contrato-completo.cas",
    )

    fun afterRemoving(before: Map<String, Table>, removals: List<Removal>): Map<String, Table> {
        removals.forEach { removal ->
            val table = checkNotNull(before[removal.table]) { "C090_EXPECTED_TABLE_MISSING" }
            check(removal.column in table.columns) { "C090_EXPECTED_COLUMN_MISSING" }
            check(removal.values.isNotEmpty()) { "C090_EMPTY_REMOVAL_IDENTITY" }
        }
        return before.mapValues { (name, table) ->
            val allowed = removals.filter { it.table == name }.map { table.columns.indexOf(it.column) to it.values }
            check(table.rows.all { it.size == table.columns.size }) { "C090_INVALID_CANONICAL_ROW" }
            table.copy(rows = table.rows.filterNot { row -> allowed.any { (column, values) -> row[column] in values } })
        }
    }

    /** Only an explicitly owned removed file may disappear. No additions or modified bytes. */
    fun <T> sameFilesExceptOwnedRemoval(before: Map<String, T>, after: Map<String, T>, removable: Set<String>): Boolean =
        after.keys.all { it in before } && before.all { (path, value) ->
            if (path in removable) path !in after || after[path] == value else after[path] == value
        }

    fun retain(primary: Throwable?, secondary: Throwable): Throwable {
        if (primary == null) return secondary
        if (primary !== secondary && primary.suppressed.none { it === secondary }) primary.addSuppressed(secondary)
        return primary
    }
}
