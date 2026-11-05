package com.blackatsystems.miguardia.stage12

import java.io.File
import com.blackatsystems.miguardia.stage12.Stage12TemporalPreparation.Step as SetupStep

internal class Stage12TemporalPaths private constructor(
    val run: String, val scope: Stage12TemporalScope, val root: File,
    val database: File, val privateRoot: File, val dataRoot: File,
) {
    val name: String get() = scope.databaseName(run)

    /** Rechecks against this admitted identity; never recaptures a different root for the case. */
    fun revalidate(access: Stage12RecurringPathAccess): Stage12TemporalPaths =
        resolve(access, run, scope, null, this)

    fun requireStored(access: Stage12RecurringPathAccess) {
        revalidate(access)
        check(access.regular(database) && !access.link(database)) { "TEMPORAL_OWN_DATABASE_REQUIRED" }
        check(access.directory(privateRoot) && !access.link(privateRoot)) { "TEMPORAL_OWN_PRIVATE_REQUIRED" }
        check(access.directory(dataRoot) && !access.link(dataRoot)) { "TEMPORAL_OWN_DATA_REQUIRED" }
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            val sidecar = access.resolve(root, "databases/$name$suffix")
            if (access.exists(sidecar)) check(access.regular(sidecar) && !access.link(sidecar)) { "TEMPORAL_OWN_SIDECAR_REJECTED" }
        }
    }

    companion object {
        fun admit(access: Stage12RecurringPathAccess, run: String, scope: Stage12TemporalScope,
            journal: Stage12TemporalPreparation): Stage12TemporalPaths = resolve(access, run, scope, journal, null)

        private fun resolve(access: Stage12RecurringPathAccess, run: String, scope: Stage12TemporalScope,
            journal: Stage12TemporalPreparation?, bound: Stage12TemporalPaths?): Stage12TemporalPaths {
            fun <T> call(step: Stage12TemporalPreparation.Step, body: () -> T): T =
                if (journal == null) body() else journal.call(step, body)
            fun guard(step: Stage12TemporalPreparation.Step, body: () -> Boolean) {
                if (journal == null) check(body() == step.expected) { "TEMPORAL_OWN_ANCHOR_REJECTED" }
                else journal.guard(step, body)
            }
            val names = call(SetupStep.NAMES) { scope.databaseName(run) to scope.privateRootName(run) }
            val rawApp = call(SetupStep.APP_ANCHOR) { access.application().absoluteFile }
            fun shape(prefix: File) = prefix.toPath().none { it.toString() in setOf(".", "..") }
            guard(SetupStep.APP_SHAPE) { shape(rawApp) }
            val root = call(SetupStep.APP_IDENTITY) {
                access.canonical(rawApp).also { if (bound != null) check(it == bound.root) { "TEMPORAL_OWN_ANCHOR_CHANGED" } }
            }
            // An external representation of the trusted root is allowed. Re-entering that
            // root through one of its own descendants is an administered alias, not an anchor.
            fun prefixAllowed(prefix: File): Boolean {
                if (!shape(prefix)) return false
                var parent = prefix.parentFile
                while (parent != null) {
                    if (access.canonical(parent).toPath().startsWith(root.toPath())) return false
                    parent = parent.parentFile
                }
                return true
            }
            guard(SetupStep.APP_PREFIX) { prefixAllowed(rawApp) }
            guard(SetupStep.APP_DIRECTORY) { access.directory(root) }
            val databases = File(root, "databases")
            guard(SetupStep.DATABASE_DIRECTORY) { access.directory(databases) }
            guard(SetupStep.DATABASE_DIRECTORY_LINK) { access.link(databases) }
            guard(SetupStep.DATABASE_DIRECTORY_IDENTITY) { access.canonical(databases) == databases }
            val database = call(SetupStep.DATABASE_RESOLUTION) { access.resolve(root, "databases/${names.first}") }
            val contextDatabase = call(SetupStep.CONTEXT_DATABASE) { access.database(names.first).absoluteFile }
            guard(SetupStep.CONTEXT_DATABASE_NAME) { contextDatabase.name == names.first && contextDatabase.parentFile?.name == "databases" }
            guard(SetupStep.CONTEXT_DATABASE_PREFIX) { prefixAllowed(checkNotNull(contextDatabase.parentFile?.parentFile)) }
            guard(SetupStep.CONTEXT_DATABASE_PARENT) { access.canonical(checkNotNull(contextDatabase.parentFile)) == databases }
            guard(SetupStep.CONTEXT_DATABASE_ANCHOR) { access.canonical(checkNotNull(contextDatabase.parentFile?.parentFile)) == root }
            guard(SetupStep.CONTEXT_DATABASE_DIRECTORY_LINK) { access.link(checkNotNull(contextDatabase.parentFile)) }
            guard(SetupStep.CONTEXT_DATABASE_LINK) { access.link(contextDatabase) }
            guard(SetupStep.CONTEXT_DATABASE_IDENTITY) { access.canonical(contextDatabase) == database }
            val files = File(root, "files")
            guard(SetupStep.FILES_DIRECTORY) { access.directory(files) }
            guard(SetupStep.FILES_DIRECTORY_LINK) { access.link(files) }
            guard(SetupStep.FILES_DIRECTORY_IDENTITY) { access.canonical(files) == files }
            val contextFiles = call(SetupStep.CONTEXT_FILES) { access.files().absoluteFile }
            guard(SetupStep.CONTEXT_FILES_NAME) { contextFiles.name == "files" }
            guard(SetupStep.CONTEXT_FILES_PREFIX) { prefixAllowed(checkNotNull(contextFiles.parentFile)) }
            guard(SetupStep.CONTEXT_FILES_ANCHOR) { access.canonical(checkNotNull(contextFiles.parentFile)) == root }
            guard(SetupStep.CONTEXT_FILES_LINK) { access.link(contextFiles) }
            guard(SetupStep.CONTEXT_FILES_IDENTITY) { access.canonical(contextFiles) == files }
            val privateRoot = call(SetupStep.PRIVATE_RESOLUTION) { access.resolve(root, "files/${names.second}") }
            val dataRoot = call(SetupStep.DATA_RESOLUTION) { access.resolve(root, "files/${names.second}/files") }
            guard(SetupStep.APP_STABLE) { access.canonical(rawApp) == root }
            if (bound != null) check(bound.run == run && bound.scope == scope && database == bound.database &&
                privateRoot == bound.privateRoot && dataRoot == bound.dataRoot) { "TEMPORAL_OWN_BINDING_CHANGED" }
            return bound ?: Stage12TemporalPaths(run, scope, root, database, privateRoot, dataRoot)
        }
    }
}
