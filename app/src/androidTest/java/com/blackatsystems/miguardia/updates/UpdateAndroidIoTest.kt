package com.blackatsystems.miguardia.updates

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.beta.*
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Synthetic local IO only. Fixture transfer and all instrumentation require a separate device authorization. */
class UpdateAndroidIoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root by lazy { File(context.cacheDir, "updates28-test-${UUID.randomUUID()}").apply { check(mkdir()) } }
    @After fun cleanup() { if (root.exists()) root.deleteRecursively() }

    @Test fun createDocumentIsSaveOnlyAndNeverLaunchesAnInstaller() {
        val intent = CreateUpdateDocument().createIntent(context, "MiGuardia-1.0.1.apk")
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action); assertEquals(APK_MIME, intent.type)
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
        assertEquals("MiGuardia-1.0.1.apk", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertNull(intent.data); assertNull(intent.clipData); assertNull(intent.component); assertNull(intent.`package`)
    }
    @Test fun realAndroidParserReadsSignedApkEvenBeforePartRenameAndRejectsSplitContainers() {
        val relative = InstrumentationRegistry.getArguments().getString("updates28FixtureDirectory")
        assumeTrue("MAIN must explicitly authorize and supply the documented signed QA fixtures", relative == "updates28-fixtures")
        val fixtures = File(context.cacheDir, "updates28-fixtures")
        check(context.packageName == "com.blackatsystems.miguardia.qa")
        val inspector = AndroidUpdateApkInspector(context)
        val installed = inspector.installed()
        // Read-only inspection fixtures are never installed as an upgrade base.
        val valid = File(fixtures, "valid.apk"); check(valid.isFile)
        val part = valid.copyTo(File(root, "candidate.part"))
        val parsed = inspector.inspect(part)
        assertEquals("com.blackatsystems.miguardia.qa", parsed.packageName)
        assertEquals(2L, parsed.versionCode)
        assertEquals("1.0.1", parsed.versionName)
        assertEquals(installed.identity.certificateSha256, parsed.certificates.single())
        for (name in listOf("required.apk", "uses-split.apk", "split.apk", "splittypes.apk", "unsigned.apk", "broken.apk")) {
            val file = File(fixtures, name); check(file.isFile)
            assertTrue(name, runCatching { inspector.inspect(file) }.isFailure)
        }
    }

    private fun artifact(): VerifiedUpdate {
        val bytes = "Synthetic APK for provider streaming test".toByteArray()
        val file = File(root, "source.apk").apply { writeBytes(bytes) }
        val installed = InstalledUpdateApp(InstalledAppIdentity(PUBLIC_PACKAGE, PUBLIC_CERTIFICATE, 1), "1.0.0", 26)
        val release = UpdateRelease("updates28_provider", "1.0.1", 2, installed.identity.packageName, 26,
            bytes.size.toLong(), BetaCrypto.hash(bytes), installed.identity.certificateSha256, Instant.EPOCH)
        // Writer-only fixture. These bytes never pass AndroidUpdateApkInspector or install.
        return VerifiedUpdate(UpdateSelection(release, installed, this, 0), file)
    }
    private class Provider(val file: File) : DocumentsProvider() {
        var denyRead = false
        var corruptRead = false
        var deletes = 0
        override fun onCreate() = true
        override fun queryRoots(projection: Array<out String>?) = MatrixCursor(projection ?: arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID))
        override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = MatrixCursor(projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
        override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) = queryDocument(parentDocumentId, projection)
        override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
            if (mode == "r") {
                if (denyRead) throw SecurityException("Synthetic write-only provider")
                if (corruptRead) file.writeBytes(byteArrayOf(1))
            }
            return ParcelFileDescriptor.open(file, if (mode == "r") ParcelFileDescriptor.MODE_READ_ONLY else ParcelFileDescriptor.MODE_READ_WRITE)
        }
        override fun deleteDocument(documentId: String) { deletes++; check(file.delete()) }
        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
            if (method != "android:deleteDocument") return super.call(method, arg, extras)
            // Local wrap(provider) has no registered authority/URI grants. Exercise the exact
            // DocumentsContract deletion message without inventing a manifest permission.
            @Suppress("DEPRECATION") val uri = checkNotNull(extras?.getParcelable<Uri>("uri"))
            require(uri.authority == "updates28.test.provider")
            deleteDocument(DocumentsContract.getDocumentId(uri))
            return Bundle()
        }
    }
    @SdkSuppress(minSdkVersion = 29)
    @Test fun documentsProviderStreamsReReadsAndNeverDeletesOnFailure() = runBlocking {
        val artifact = artifact()
        for (mode in listOf("readback", "writeonly", "corrupt", "preexisting", "blocked")) {
            val target = File(root, "$mode.apk").apply { if (mode == "preexisting") writeText("keep") else createNewFile() }
            val provider = Provider(target).apply {
                denyRead = mode == "writeonly"; corruptRead = mode == "corrupt"
                attachInfo(context, ProviderInfo().apply { authority = "updates28.test.provider"; exported = true;
                    grantUriPermissions = true; readPermission = "android.permission.MANAGE_DOCUMENTS"; writePermission = readPermission })
            }
            val uri = DocumentsContract.buildDocumentUri("updates28.test.provider", mode)
            // Local ContentProvider transport; the package manager check is injected because no test manifest component is added.
            val destination = AndroidUpdateDocument(context, uri, ContentResolver.wrap(provider)) { it == uri }
            val result = runCatching { UpdateDocumentWriter().save(artifact, destination) { mode != "blocked" } }
            when (mode) {
                "readback" -> { assertEquals(UpdateSaveVerification.READ_BACK, result.getOrThrow()); assertArrayEquals(artifact.file.readBytes(), target.readBytes()) }
                "writeonly" -> assertEquals(UpdateSaveVerification.WRITE_ONLY, result.getOrThrow())
                "corrupt" -> { assertTrue(result.isFailure); assertEquals(0, provider.deletes); assertTrue(target.exists()) }
                "preexisting" -> { assertTrue(result.isFailure); assertEquals("keep", target.readText()); assertEquals(0, provider.deletes) }
                "blocked" -> { assertTrue(result.isFailure); assertEquals(0, provider.deletes); assertEquals(0L, target.length()) }
            }
            provider.shutdown()
        }
    }
    @Test fun foreignAndNonDocumentUrisAreNeverOpenedOrDeleted() {
        val target = File(root, "keep.apk").apply { writeText("keep") }
        for (uri in listOf(Uri.fromFile(target), Uri.parse("content://foreign.test/not-document"))) {
            val document = AndroidUpdateDocument(context, uri)
            assertTrue(runCatching { document.openEmptyOutput() }.isFailure)
            document.discardCreated(); assertEquals("keep", target.readText())
        }
    }
}
