package com.blackatsystems.miguardia.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.util.zip.ZipFile

internal data class UpdateApkInfo(
    val packageName: String, val versionName: String?, val versionCode: Long,
    val minAndroid: Int, val certificates: List<String>, val split: Boolean,
)
internal interface UpdateApkInspector {
    fun installed(): InstalledUpdateApp
    fun inspect(file: File): UpdateApkInfo
}

/** Android parses package/signing metadata. The Android installer still verifies the final signature. */
internal class AndroidUpdateApkInspector(private val context: Context) : UpdateApkInspector {
    @Suppress("DEPRECATION")
    override fun installed(): InstalledUpdateApp = InstalledUpdateApp(installedIdentity(context),
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Sin nombre", Build.VERSION.SDK_INT)

    @Suppress("DEPRECATION")
    override fun inspect(file: File): UpdateApkInfo {
        // Reject alternate containers, malformed ZIP directories and split-only manifests.
        ZipFile(file).use { zip ->
            require(zip.getEntry("AndroidManifest.xml") != null && zip.getEntry("classes.dex") != null)
            require(zip.getEntry("toc.pb") == null && zip.getEntry("manifest.json") == null)
            val byAbi = mutableMapOf<String, MutableSet<String>>()
            var entries = 0
            val names = hashSetOf<String>()
            for (entry in zip.entries().asSequence()) {
                require(++entries <= 100_000 && names.add(entry.name))
                require(!entry.name.startsWith("/") && ".." !in entry.name.split('/'))
                if (!entry.isDirectory && entry.name.startsWith("lib/")) {
                    val path = entry.name.split('/')
                    require(path.size == 3)
                    byAbi.getOrPut(path[1]) { mutableSetOf() }.add(path[2])
                }
            }
            if (byAbi.isNotEmpty()) {
                require(byAbi.keys == setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"))
                require(byAbi.values.distinct().size == 1)
            }
        }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = checkNotNull(context.packageManager.getPackageArchiveInfo(file.absolutePath, flags))
        val app = checkNotNull(info.applicationInfo)
        val archiveApp = android.content.pm.ApplicationInfo(app).apply {
            sourceDir = file.absolutePath
            publicSourceDir = file.absolutePath
        }
        val resources = context.packageManager.getResourcesForApplication(archiveApp)
        resources.assets.openXmlResourceParser("AndroidManifest.xml").use { xml ->
            while (xml.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    require(xml.name != "uses-split")
                    if (xml.name == "manifest" || xml.name == "application") {
                        for (index in 0 until xml.attributeCount) {
                            val name = xml.getAttributeName(index)
                            val value = xml.getAttributeValue(index)
                            if (name in setOf("split", "configForSplit", "requiredSplitTypes", "splitTypes")) require(value.isNullOrEmpty())
                            if (name in setOf("isFeatureSplit", "isSplitRequired")) {
                                val resource = xml.getAttributeResourceValue(index, 0)
                                require(!(if (resource != 0) resources.getBoolean(resource) else xml.getAttributeBooleanValue(index, false)))
                            }
                        }
                    }
                    if (xml.name == "meta-data") {
                        val namespace = "http://schemas.android.com/apk/res/android"
                        val resource = xml.getAttributeResourceValue(namespace, "name", 0)
                        val name = if (resource == 0) xml.getAttributeValue(namespace, "name") else resources.getString(resource)
                        require(name !in setOf("com.android.vending.splits.required", "com.android.vending.splits"))
                    }
                }
                xml.next()
            }
        }
        val certificates = certificates(info)
        return UpdateApkInfo(info.packageName, info.versionName,
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
            app.minSdkVersion, certificates, !info.splitNames.isNullOrEmpty())
    }

    @Suppress("DEPRECATION")
    private fun certificates(info: PackageInfo): List<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val signing = checkNotNull(info.signingInfo)
            require(!signing.hasMultipleSigners() && signing.signingCertificateHistory?.size == 1)
            signing.apkContentsSigners
        } else info.signatures
        require(signatures?.size == 1)
        return signatures.map { updateHash(it.toByteArray()) }
    }
}

internal fun validateUpdateApk(apk: UpdateApkInfo, release: UpdateRelease, installed: InstalledUpdateApp) {
    require(release.availability(installed) == UpdateAvailability.AVAILABLE)
    require(!apk.split && apk.packageName == release.packageName && apk.packageName == installed.identity.packageName)
    require(apk.versionCode == release.versionCode && apk.versionCode > installed.identity.versionCode)
    require(apk.versionName == release.versionName)
    require(apk.minAndroid == release.minAndroid && apk.minAndroid <= installed.deviceApi)
    require(apk.certificates.size == 1 && apk.certificates.single() == release.certificateSha256 &&
        apk.certificates.single() == installed.identity.certificateSha256)
}

internal fun updateHash(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

@Suppress("DEPRECATION")
internal fun installedIdentity(context: Context): InstalledAppIdentity {
    val info = context.packageManager.getPackageInfo(context.packageName,
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
    val certificates = if (Build.VERSION.SDK_INT >= 28) {
        val signing = checkNotNull(info.signingInfo)
        check(!signing.hasMultipleSigners() && signing.signingCertificateHistory?.size == 1)
        signing.apkContentsSigners
    } else info.signatures
    check(certificates?.size == 1)
    return InstalledAppIdentity(context.packageName, updateHash(certificates.single().toByteArray()),
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong())
}
