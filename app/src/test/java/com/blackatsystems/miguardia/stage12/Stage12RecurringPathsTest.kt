package com.blackatsystems.miguardia.stage12

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import com.blackatsystems.miguardia.stage12.Stage12RecurringPreparation.Step

/** Actual helper with stipulated canonicalizer projections; no Android or filesystem alias creation. */
class Stage12RecurringPathsTest {
    private val run = "80523c48-cad8-48b7-ae90-7c11d8d0e86a"
    private val ownCase = Stage12RecurringCase.LIFECYCLE
    private val root = File("C:/stage12-public/identity").absoluteFile
    private class Model(val root: File, var app: File, var dbAnchor: File, var filesAnchor: File) : Stage12RecurringPathAccess {
        val calls = mutableListOf<String>()
        val aliases = mutableMapOf<File, File>()
        val links = mutableSetOf<File>()
        val nonDirectories = mutableSetOf<File>()
        var throwAt = -1
        var databaseOverride: File? = null
        var filesOverride: File? = null
        val thrown = IOException("SYNTHETIC_PRIVATE_MESSAGE_MUST_NOT_BE_PUBLISHED")
        var operation: (() -> Unit)? = null
        private fun touch(name: String) { calls += name; operation?.invoke(); if (calls.size == throwAt) throw thrown }
        override fun application(): File { touch("application"); return app }
        override fun database(name: String): File { touch("database"); return databaseOverride ?: File(dbAnchor, "databases/$name") }
        override fun files(): File { touch("files"); return filesOverride ?: File(filesAnchor, "files") }
        override fun canonical(file: File): File {
            touch("canonical")
            aliases[file]?.let { return it }
            for (anchor in listOf(app, dbAnchor, filesAnchor).distinct()) {
                if (file.toPath().startsWith(anchor.toPath())) return root.toPath().resolve(anchor.toPath().relativize(file.toPath())).toFile()
            }
            return file
        }
        override fun directory(file: File): Boolean { touch("directory"); return file !in nonDirectories }
        override fun link(file: File): Boolean { touch("link"); return file in links }
        override fun regular(file: File): Boolean { touch("regular"); return file !in nonDirectories }
        override fun exists(file: File): Boolean { touch("exists"); return file in links }
        override fun resolve(root: File, relative: String): File {
            touch("resolve")
            check(relative.split('/').all { it.isNotEmpty() && it != "." && it != ".." })
            val candidate = File(root, relative)
            check(canonical(candidate) == candidate && candidate.toPath().startsWith(root.toPath()))
            return candidate
        }
    }
    private fun model(distinct: Boolean = false): Model = if (distinct)
        Model(root, File("C:/stage12-public/framework-a").absoluteFile, File("C:/stage12-public/framework-b").absoluteFile, File("C:/stage12-public/framework-c").absoluteFile)
        else Model(root, root, root, root)
    private fun journal(errors: MutableList<Throwable> = mutableListOf()) = Stage12RecurringPreparation { errors += it }
        .also { it.call(Step.CASE_REGISTERED) {} }
    private fun bind(model: Model, journal: Stage12RecurringPreparation = journal()) = Stage12RecurringPaths.admit(model, run, ownCase, journal)
    private fun rejected(model: Model, body: () -> Unit) {
        try { body(); fail("Expected scoped rejection") } catch (expected: IllegalStateException) { }
    }

    @Test fun validBindingUsesOnlyCanonicalOwnedDestinationsAcrossAllConsumers() {
        val model = model(); val paths = bind(model)
        assertEquals(root, paths.root); assertEquals(File(root, "databases/${ownCase.databaseName(run)}"), paths.database)
        assertEquals(File(root, "files/${ownCase.privateRootName(run)}"), paths.privateRoot)
        assertEquals(File(paths.privateRoot, "files"), paths.dataRoot)
        // The real shared guards invoked by history, readback and cleanup, not four independent recaptures.
        repeat(3) { paths.requireStored(model); assertSame(paths, paths.revalidate(model)) }
    }
    @Test fun distinctTrustedFrameworkRepresentationsWithSameIdentityAreAccepted() {
        val model = model(true); val paths = bind(model)
        assertNotEquals(model.app, paths.root); assertEquals(root, paths.root)
        paths.requireStored(model); assertSame(paths, paths.revalidate(model))
    }
    @Test fun differentApplicationIdentityBetweenCaptureAndUseIsNotReadmitted() {
        val model = model(true); val paths = bind(model)
        model.aliases[model.app] = File("C:/stage12-public/foreign").absoluteFile
        rejected(model) { paths.requireStored(model) }
        assertEquals("canonical", model.calls.last())
    }
    @Test fun wrongFrameworkDatabaseAndFilesAnchorsRejectBeforeDependentOperations() {
        for (isDatabase in listOf(true,false)) {
            val model=model(true)
            val anchor=if (isDatabase) model.dbAnchor else model.filesAnchor
            model.aliases[anchor]=File("C:/stage12-public/foreign").absoluteFile
            val j=journal();rejected(model) { bind(model,j) }
            assertEquals(if (isDatabase) Step.CONTEXT_DATABASE_ANCHOR.name else Step.CONTEXT_FILES_ANCHOR.name,j.failedStep?.name)
            assertEquals("canonical",model.calls.last())
        }
    }
    @Test fun managedDirectoryAndDatabaseLinksAreRejectedIncludingBrokenLinks() {
        val name=ownCase.databaseName(run)
        for ((path,step) in listOf(File(root,"databases") to Step.DATABASE_DIRECTORY_LINK,
            File(root,"files") to Step.FILES_DIRECTORY_LINK,File(root,"databases/$name") to Step.CONTEXT_DATABASE_LINK)) {
            val model=model();model.links+=path;val j=journal()
            rejected(model) { bind(model,j) };assertEquals(step,j.failedStep);assertEquals("link",model.calls.last())
        }
    }
    @Test fun managedAliasOrEscapeIsRejectedByTheResolver() {
        for (relative in listOf("databases/${ownCase.databaseName(run)}","files/${ownCase.privateRootName(run)}","files/${ownCase.privateRootName(run)}/files")) {
            val model=model();model.aliases[File(root,relative)]=File("C:/stage12-public/outside").absoluteFile
            rejected(model) { bind(model) }
            assertEquals("canonical",model.calls.last())
        }
    }
    @Test fun managedRootOrDataChangedAfterAdmissionStopsStoredConsumers() {
        for (data in listOf(false,true)) {
            val model=model();val paths=bind(model)
            model.aliases[if(data) paths.dataRoot else paths.privateRoot]=File("C:/stage12-public/outside").absoluteFile
            rejected(model) { paths.requireStored(model) }
            assertEquals("canonical",model.calls.last())
        }
    }
    @Test fun sidecarAliasIsRejectedBeforeItCanBeUsed() {
        val model=model();val paths=bind(model)
        model.aliases[File(paths.database.path+"-wal")]=File("C:/stage12-public/outside").absoluteFile
        rejected(model) { paths.requireStored(model) }
        assertEquals("canonical",model.calls.last())
    }
    @Test fun anchorChangeDuringAdmissionFailsItsStableCheck() {
        val model=model(true);val app=model.app;var appReads=0
        model.operation={ if(model.calls.last()=="canonical") {
            // The final canonical(rawApp), after all managed destinations have been resolved.
            if(model.calls.count { it=="resolve" }==3) { appReads++;model.aliases[app]=File("C:/stage12-public/foreign").absoluteFile }
        } }
        val j=journal();rejected(model) { bind(model,j) };assertEquals(Step.APP_STABLE,j.failedStep);assertTrue(appReads>0)
    }
    @Test fun everyAccessExceptionKeepsOriginalAndBlocksAllDependentOperations() {
        val baseline=model();bind(baseline)
        for (at in 1..baseline.calls.size) {
            val model=model();model.throwAt=at;val errors=mutableListOf<Throwable>();val j=journal(errors)
            try { bind(model,j);fail("Expected original access error") } catch(error:IOException) { assertSame(model.thrown,error) }
            val count=model.calls.size;assertEquals(at,count);assertSame(model.thrown,errors.single())
            var dependent=0
            try { j.call(Step.ACTIVITY_MOUNT) { dependent++ };fail("Must remain failed") } catch(error:IOException) { assertSame(model.thrown,error) }
            assertEquals(0,dependent);assertEquals(count,model.calls.size)
            val wire=j.projection().toString();assertFalse(wire.contains("SYNTHETIC_PRIVATE"));assertFalse(wire.contains("stage12-public"))
        }
    }
    @Test fun allBooleanGuardFailuresStopAtTheMeasuredOperand() {
        for (path in listOf(root,File(root,"databases"),File(root,"files"))) {
            val model=model();model.nonDirectories+=path;val j=journal()
            rejected(model) { bind(model,j) };assertEquals("directory",model.calls.last())
            @Suppress("UNCHECKED_CAST") val events=j.projection()["events"] as List<Map<String,Any>>
            assertEquals(false,events.last()["result"]);assertEquals("GUARD_REJECTED",events.last()["error"])
        }
    }
    @Test fun invalidRunStopsBeforeAnyFrameworkAccess() {
        for((value,errorType) in listOf("../received" to IllegalArgumentException::class.java,run.uppercase() to IllegalStateException::class.java)) {
            val model=model();val j=journal()
            assertThrows(errorType) { Stage12RecurringPaths.admit(model,value,ownCase,j) }
            assertTrue(model.calls.isEmpty());assertEquals(Step.NAMES,j.failedStep)
        }
    }
    @Test fun receivedWrongRunWrongCaseAndWrongParentCannotBecomeOwnedDatabase() {
        val expected=ownCase.databaseName(run)
        for(path in listOf(File(root,"databases/miguardia-v2.db"),
            File(root,"databases/${ownCase.databaseName("8e672fbf-783f-47e2-a870-a424f341206d")}"),
            File(root,"databases/${Stage12RecurringCase.ROLLBACK.databaseName(run)}"),File(root,"outside/$expected"))) {
            val model=model();model.databaseOverride=path;val j=journal()
            rejected(model) { bind(model,j) };assertEquals(Step.CONTEXT_DATABASE_NAME,j.failedStep)
        }
        val model=model();model.filesOverride=File(root,"outside");val j=journal()
        rejected(model) { bind(model,j) };assertEquals(Step.CONTEXT_FILES_NAME,j.failedStep)
    }
    @Test fun managedDirectoryCanonicalChangesStopAtTheOriginalIdentityOperand() {
        for ((path,step) in listOf(File(root,"databases") to Step.DATABASE_DIRECTORY_IDENTITY,File(root,"files") to Step.FILES_DIRECTORY_IDENTITY)) {
            val model=model();model.aliases[path]=File("C:/stage12-public/elsewhere").absoluteFile;val j=journal()
            rejected(model) { bind(model,j) };assertEquals(step,j.failedStep);assertEquals("canonical",model.calls.last())
        }
    }
    @Test fun frameworkPrefixesCannotReenterTheRootThroughManagedAliases() {
        for(kind in listOf("application","database","files")) {
            val model=model(true);val back=File(root,"files/back")
            model.aliases[back]=root
            // Stipulated canonical projection for the alias path, without creating a link.
            if(kind=="application") model.app=back
            if(kind=="database") { model.dbAnchor=back;model.aliases[File(back,"databases")]=File(root,"databases");model.aliases[File(back,"databases/${ownCase.databaseName(run)}")]=File(root,"databases/${ownCase.databaseName(run)}") }
            if(kind=="files") { model.filesAnchor=back;model.aliases[File(back,"files")]=File(root,"files") }
            val j=journal();rejected(model) { bind(model,j) }
            assertEquals(when(kind){"application"->Step.APP_PREFIX;"database"->Step.CONTEXT_DATABASE_PREFIX;else->Step.CONTEXT_FILES_PREFIX},j.failedStep)
        }
    }
    @Test fun dotDotFrameworkPrefixesNeverPassAsCanonicalAnchors() {
        val model=model();model.app=File(root,"outside/..");val j=journal()
        rejected(model) { bind(model,j) };assertEquals(Step.APP_SHAPE,j.failedStep)
        assertEquals(listOf("application"),model.calls)
    }
    @Test fun externalEntryIntoManagedFilesCannotHideTheReturnAlias() {
        for(kind in listOf("application","database","files")) {
            val model=model(true);val entry=File("C:/stage12-public/external/entry").absoluteFile;val back=File(entry,"back")
            model.aliases[entry]=File(root,"files");model.aliases[back]=root
            if(kind=="application") model.app=back
            if(kind=="database") model.dbAnchor=back
            if(kind=="files") model.filesAnchor=back
            val j=journal();rejected(model) { bind(model,j) }
            assertEquals(when(kind){"application"->Step.APP_PREFIX;"database"->Step.CONTEXT_DATABASE_PREFIX;else->Step.CONTEXT_FILES_PREFIX},j.failedStep)
        }
    }
}
