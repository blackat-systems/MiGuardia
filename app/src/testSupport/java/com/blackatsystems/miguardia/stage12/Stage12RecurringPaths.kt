package com.blackatsystems.miguardia.stage12

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import com.blackatsystems.miguardia.stage12.Stage12RecurringPreparation.Step as SetupStep

/** Only the three QA framework anchors are supplied. Managed descendants never become trusted anchors. */
internal interface Stage12RecurringPathAccess {
    fun application(): File
    fun database(name: String): File
    fun files(): File
    fun canonical(file: File): File = file.canonicalFile
    fun directory(file: File): Boolean = file.isDirectory
    fun link(file: File): Boolean = Files.isSymbolicLink(file.toPath())
    fun exists(file: File): Boolean = Files.exists(file.toPath(), NOFOLLOW_LINKS)
    fun regular(file: File): Boolean = Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)
    fun resolve(root: File, relative: String): File = Stage12PathPolicy(root).resolve(relative)
}

internal class Stage12RecurringPaths private constructor(
    val run: String, val ownCase: Stage12RecurringCase, val root: File,
    val database: File, val privateRoot: File, val dataRoot: File,
) {
    val name: String get() = ownCase.databaseName(run)

    /** Rechecks against this admitted identity; never recaptures a different root for the case. */
    fun revalidate(access: Stage12RecurringPathAccess): Stage12RecurringPaths =
        resolve(access, run, ownCase, null, this)

    fun requireStored(access: Stage12RecurringPathAccess) {
        revalidate(access)
        check(access.regular(database) && !access.link(database)) { "RECURRING_OWN_DATABASE_REQUIRED" }
        check(access.directory(privateRoot) && !access.link(privateRoot)) { "RECURRING_OWN_PRIVATE_REQUIRED" }
        check(access.directory(dataRoot) && !access.link(dataRoot)) { "RECURRING_OWN_DATA_REQUIRED" }
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            val sidecar = access.resolve(root, "databases/$name$suffix")
            if (access.exists(sidecar)) check(access.regular(sidecar) && !access.link(sidecar)) { "RECURRING_OWN_SIDECAR_REJECTED" }
        }
    }

    companion object {
        fun admit(access: Stage12RecurringPathAccess, run: String, ownCase: Stage12RecurringCase,
            journal: Stage12RecurringPreparation): Stage12RecurringPaths = resolve(access, run, ownCase, journal, null)

        private fun resolve(access: Stage12RecurringPathAccess, run: String, ownCase: Stage12RecurringCase,
            journal: Stage12RecurringPreparation?, bound: Stage12RecurringPaths?): Stage12RecurringPaths {
            fun <T> call(step: Stage12RecurringPreparation.Step, body: () -> T): T =
                if (journal == null) body() else journal.call(step, body)
            fun guard(step: Stage12RecurringPreparation.Step, body: () -> Boolean) {
                if (journal == null) check(body() == step.expected) { "RECURRING_OWN_ANCHOR_REJECTED" }
                else journal.guard(step, body)
            }
            val names = call(SetupStep.NAMES) { ownCase.databaseName(run) to ownCase.privateRootName(run) }
            val rawApp = call(SetupStep.APP_ANCHOR) { access.application().absoluteFile }
            fun shape(prefix: File) = prefix.toPath().none { it.toString() in setOf(".", "..") }
            guard(SetupStep.APP_SHAPE) { shape(rawApp) }
            val root = call(SetupStep.APP_IDENTITY) {
                access.canonical(rawApp).also { if (bound != null) check(it == bound.root) { "RECURRING_OWN_ANCHOR_CHANGED" } }
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
            if (bound != null) check(bound.run == run && bound.ownCase == ownCase && database == bound.database &&
                privateRoot == bound.privateRoot && dataRoot == bound.dataRoot) { "RECURRING_OWN_BINDING_CHANGED" }
            return bound ?: Stage12RecurringPaths(run, ownCase, root, database, privateRoot, dataRoot)
        }
    }
}
