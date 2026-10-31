package com.blackatsystems.miguardia.stage12

import java.security.MessageDigest
import java.util.UUID

/** Projects the current RAM readback; no path, filename, value or resource capability escapes. */
internal object Stage12B1FileDelta {
    enum class Role { OWN_REPORT, APP_CACHE, CODE_CACHE, PREFERENCE_PRIMARY, PREFERENCE_BACKUP, APP_STATE, UNKNOWN }
    enum class Change { ADDED, REMOVED, MODIFIED }
    data class Entry(val pathToken: String, val role: Role, val change: Change,
        val before: Stage12FileSnapshot?, val after: Stage12FileSnapshot?)
    data class Result(val total: Int, val added: Int, val removed: Int, val modified: Int,
        val receivedChanged: Int, val unknown: Int, val truncated: Boolean, val entries: List<Entry>)

    fun project(before: Map<String, Stage12FileSnapshot>, after: Map<String, Stage12FileSnapshot>, run: String,
                limit: Int = 128): Result {
        require(UUID.fromString(run).toString() == run)
        require(limit in 1..128)
        val paths = (before.keys + after.keys).sorted()
        paths.forEach { path -> require(!path.startsWith('/') && '\\' !in path &&
            path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }) }
        val changes = paths.filter { before[it] != after[it] }.map { path ->
            Entry(digest(path), role(path, run), when {
                before[path] == null -> Change.ADDED
                after[path] == null -> Change.REMOVED
                else -> Change.MODIFIED
            }, before[path], after[path])
        }
        return Result(changes.size, changes.count { it.change == Change.ADDED }, changes.count { it.change == Change.REMOVED },
            changes.count { it.change == Change.MODIFIED }, changes.count { it.before != null },
            changes.count { it.role == Role.UNKNOWN }, changes.size > limit, changes.take(limit))
    }

    private fun role(path: String, run: String): Role {
        val own = "files/reports/artifacts/stage12-normal-$run"
        return when {
            path == own || path.startsWith("$own/") -> Role.OWN_REPORT
            path == "code_cache" || path.startsWith("code_cache/") -> Role.CODE_CACHE
            path == "cache" || path.startsWith("cache/") -> Role.APP_CACHE
            path.startsWith("shared_prefs/") && path.endsWith(".xml.bak") -> Role.PREFERENCE_BACKUP
            path.startsWith("shared_prefs/") && path.endsWith(".xml") -> Role.PREFERENCE_PRIMARY
            path.startsWith("files/datastore/") && path.endsWith(".preferences_pb.bak") -> Role.PREFERENCE_BACKUP
            path.startsWith("files/datastore/") && path.endsWith(".preferences_pb") -> Role.PREFERENCE_PRIMARY
            path == "files" || path.startsWith("files/") || path == "no_backup" || path.startsWith("no_backup/") -> Role.APP_STATE
            else -> Role.UNKNOWN
        }
    }
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
