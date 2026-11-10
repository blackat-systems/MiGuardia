package com.blackatsystems.miguardia.updates

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etapa 14 · A283 (ADR 0040): MiGuardia guarda el APK verificado y la persona lo instala a mano.
 * Guarda de fuentes: falla si el manifiesto o el código de la app piden permiso para instalar
 * paquetes o llaman al instalador de Android. Sólo lee archivos del proyecto.
 */
class Stage14NoInstallerGuardTest {
    // Gradle runs module unit tests from `app/` (as Stage10UpgradeContractTest relies on); the
    // fallback covers a run started from the repository root.
    private val main: File = listOf(File("src/main"), File("app/src/main"), File(System.getProperty("user.dir"), "src/main"),
        File(System.getProperty("user.dir"), "app/src/main")).firstOrNull { File(it, "AndroidManifest.xml").isFile }
        ?: error("app/src/main not found from ${File("").absolutePath}")
    private val manifest get() = File(main, "AndroidManifest.xml").readText()
    private val sources: List<File> get() = File(main, "java").walkTopDown()
        .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()
    private val apkMime = "application/vnd.android.package-archive"

    private val installerTokens = listOf(
        "REQUEST_INSTALL_PACKAGES",
        "INSTALL_PACKAGES",
        "ACTION_INSTALL_PACKAGE",
        "android.intent.action.INSTALL_PACKAGE",
        "PackageInstaller",
    )

    // A283
    @Test fun scanReallyCoversTheAppManifestAndTheUpdateSources() {
        assertTrue(manifest.contains("<manifest"))
        assertTrue("The manifest scan must see the declared permissions", manifest.contains("android.permission.INTERNET"))
        val names = sources.map { it.name }.toSet()
        assertTrue("Too few sources scanned: ${names.size}", names.size > 50)
        for (expected in listOf("MainActivity.kt", "UpdatesScreen.kt", "UpdatesViewModel.kt", "UpdateDocumentWriter.kt", "UpdateProtocol.kt"))
            assertTrue("$expected was not scanned", expected in names)
        // The constant the other checks follow is still the real APK type.
        assertEquals(apkMime, APK_MIME)
    }

    // A283
    @Test fun manifestNeverRequestsToInstallPackagesNorHandlesApkFiles() {
        for (token in installerTokens) assertFalse("AndroidManifest.xml must not mention $token", manifest.contains(token))
        assertFalse("AndroidManifest.xml must not declare the APK type", manifest.contains(apkMime))
    }

    // A283
    @Test fun appSourcesNeverCallTheAndroidInstaller() {
        val found = sources.flatMap { file -> installerTokens.filter { file.readText().contains(it) }.map { "${file.name}: $it" } }
        assertEquals("Installer API referenced from app sources", emptyList<String>(), found)
    }

    // A283
    @Test fun apkTypeIsOnlyUsedToDownloadCheckAndSaveNeverToOpenTheFile() {
        // Reviewed uses: constant, Accept header, response type check, and the system "create document" selector.
        val reviewed = setOf("UpdateProtocol.kt", "UpdateTransport.kt", "UpdateService.kt", "UpdateDocumentWriter.kt")
        val users = sources.filter { file -> file.readText().let { it.contains("APK_MIME") || it.contains(apkMime) } }
        assertEquals("A new use of the APK type needs review against ADR 0040", reviewed, users.map { it.name }.toSet())
        val openers = listOf("ACTION_VIEW", "ACTION_SEND", "setDataAndType", "startActivity(", "FileProvider")
        val offending = users.flatMap { file -> openers.filter { file.readText().contains(it) }.map { "${file.name}: $it" } }
        assertEquals("A file that knows the APK type must not open or hand the APK to another app", emptyList<String>(), offending)
        // And no single statement anywhere pairs a view intent with the APK type.
        val paired = sources.flatMap { file ->
            file.readLines().filter { it.contains("ACTION_VIEW") && (it.contains("APK_MIME") || it.contains("package-archive")) }
                .map { "${file.name}: ${it.trim()}" }
        }
        assertEquals(emptyList<String>(), paired)
    }
}
