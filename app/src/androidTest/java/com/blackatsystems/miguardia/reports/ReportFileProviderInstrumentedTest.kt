package com.blackatsystems.miguardia.reports

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.report.ReportFormat
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportFileProviderInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val reportRoot = File(context.filesDir, ReportArtifactStore.ARTIFACT_DIRECTORY).apply { mkdirs() }
    private val authority = "${context.packageName}.fileprovider"

    @After
    fun cleanUp() {
        reportRoot.deleteRecursively()
        File(context.filesDir, "reports/staging-test").deleteRecursively()
        File(context.filesDir, "provider-forbidden.db").delete()
    }

    @Test
    fun providerIsPrivateGrantsTemporaryReadAndSharesOnlyTheExactReportMime() {
        val provider = context.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA)
        assertNotNull(provider)
        requireNotNull(provider)
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)

        listOf(ReportFormat.PDF, ReportFormat.XLSX).forEach { format ->
            val file = File(reportRoot, "${UUID.randomUUID().toString().replace("-", "")}.${format.extension}")
            val bytes = if (format == ReportFormat.PDF) {
                "%PDF-1.4\nfixture".toByteArray(Charsets.US_ASCII)
            } else {
                byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x01)
            }
            file.writeBytes(bytes)
            val artifact = ReportArtifact(file, format, "MiGuardia_2026-08_informe_parcial.${format.extension}", file.length())

            val chooser = ReportShareIntentFactory.createChooser(context, artifact)
            val send = requireNotNull(
                IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java),
            )
            val stream = requireNotNull(
                IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java),
            )

            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals(format.mimeType, send.type)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals("content", stream.scheme)
            assertEquals(authority, stream.authority)
            assertEquals(stream, send.clipData?.getItemAt(0)?.uri)
        }
    }

    @Test
    fun providerRejectsDatabasePhotoStagingAndEveryOtherPrivatePath() {
        val database = File(context.filesDir, "provider-forbidden.db").apply { writeText("private") }
        val staging = File(context.filesDir, "reports/staging-test/original.jpg").apply {
            parentFile?.mkdirs()
            writeText("private-photo")
        }

        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(context, authority, database)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(context, authority, staging)
        }
    }
}
