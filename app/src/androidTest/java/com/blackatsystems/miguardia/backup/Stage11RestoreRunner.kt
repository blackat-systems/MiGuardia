package com.blackatsystems.miguardia.backup

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/** Explicit opt-in: plain Application, one UUID filesystem, no product startup. */
class Stage11RestoreRunner : AndroidJUnitRunner() {
    private val identity = UUID.randomUUID().toString()
    private lateinit var live: Context
    private lateinit var isolated: Stage11RootContext
    private var before = emptyMap<String, String>()
    private var admitted = false

    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application {
        check(context.packageName == QA)
        check(hash(File(context.applicationInfo.sourceDir)) in setOf(QA_HASH, QA_R1_HASH))
        check(Build.VERSION.SDK_INT == 36 && Build.SUPPORTED_ABIS.contains("x86_64"))
        val properties = Class.forName("android.os.SystemProperties")
        check(properties.getMethod("get", String::class.java).invoke(null, "ro.kernel.qemu") == "1")
        live = context
        before = durableFiles(context)
        isolated = Stage11RootContext(context, identity)
        return super.newApplication(cl, Application::class.java.name, isolated).also {
            check(it.javaClass == Application::class.java)
            isolated.application = it
        }
    }

    override fun callApplicationOnCreate(app: Application) {
        check(app.javaClass == Application::class.java)
        check(app.filesDir.canonicalPath.startsWith(isolated.root.canonicalPath + File.separator))
        super.callApplicationOnCreate(app)
    }

    override fun onCreate(arguments: Bundle?) {
        val args = requireNotNull(arguments)
        check(args.getString("stage11Consent") == "STAGE11_SYNTHETIC_R2_20261023")
        check(UUID.fromString(args.getString("stage11RunId")).toString() == args.getString("stage11RunId"))
        check(hash(File(context.applicationInfo.sourceDir)) == args.getString("stage11RunnerSha256"))
        check(hash(File(targetContext.applicationInfo.sourceDir)) == args.getString("stage11QaSha256"))
        val classes = requireNotNull(args.getString("class")).split(',')
        check(classes.isNotEmpty() && classes.all { it in ALLOWED })
        admitted = true
        super.onCreate(arguments)
    }

    override fun finish(resultCode: Int, results: Bundle?) {
        val bundle = results ?: Bundle()
        try {
            check(admitted)
            val after = durableFiles(live)
            check(before.all { (name, digest) -> after[name] == digest }) { "Protected input changed" }
            val additions = after.keys - before.keys
            val owned = "files/reports/artifacts/stage11-$identity/"
            check(additions.all { it.startsWith(owned) || it.startsWith("shared_prefs/stage11-$identity-") }) {
                "Unowned durable file created"
            }
            val receipt = JSONObject().put("neutral_application", true)
                .put("isolation", "PASS").put("root_id", identity)
                .put("protected_before", JSONObject(before)).put("protected_after", JSONObject(after.filterKeys { it in before }))
                .put("owned_additions", org.json.JSONArray(additions.sorted()))
            val evidence = File(isolated.root, "evidence").also { it.mkdirs() }
            additions.filter { it.startsWith("shared_prefs/stage11-$identity-") }.forEach { relative ->
                val original = File(live.applicationInfo.dataDir, relative)
                original.copyTo(File(evidence, relative).also { it.parentFile!!.mkdirs() }, overwrite = false)
            }
            File(evidence, "isolation.json").writeText(receipt.toString(2))
            val archive = File(live.cacheDir, "stage11-$identity.zip")
            check(!archive.exists())
            ZipOutputStream(archive.outputStream()).use { zip ->
                evidence.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.relativeTo(evidence).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
            bundle.putString("stage11Bundle", archive.name)
            bundle.putString("stage11BundleSha256", hash(archive))
            check(Stage11Preservation.pending.isEmpty()) { "Adverse originals retained: evidence copy incomplete" }
            // Delete only UUID resources explicitly inventoried above, after preserving evidence.
            additions.filter { it.startsWith("shared_prefs/stage11-$identity-") && it.endsWith(".xml") }
                .forEach { live.deleteSharedPreferences(it.substringAfter("shared_prefs/").removeSuffix(".xml")) }
            check(isolated.root.canonicalPath == File(live.filesDir, "reports/artifacts/stage11-$identity").canonicalPath)
            check(isolated.root.deleteRecursively())
            check(durableFiles(live) == before) { "Protected final inventory differs" }
            bundle.putString("stage11Isolation", "PASS")
        } catch (error: Throwable) {
            bundle.putString("stage11Isolation", "FAILED_${error.javaClass.simpleName}")
            super.finish(0, bundle)
            return
        }
        super.finish(resultCode, bundle)
    }

    private fun durableFiles(context: Context): Map<String, String> = buildMap {
        val data = File(context.applicationInfo.dataDir)
        listOf("files", "databases", "shared_prefs", "no_backup").forEach { name ->
            File(data, name).walkTopDown().filter { it.isFile }.forEach { file ->
                put(file.relativeTo(data).invariantSeparatorsPath, hash(file))
            }
        }
    }.toSortedMap()

    companion object {
        const val QA = "com.blackatsystems.miguardia.qa"
        const val QA_HASH = "2e00ab12ef5c5537f9e0b5b2a28172e5ad376ff5845dc3749e47a4be1a3758a3"
        const val QA_R1_HASH = "494a131086380424e45db3fd2af5707ed1be95b6e56397003bf22beec218669a"
        val ALLOWED = setOf(
            "com.blackatsystems.miguardia.backup.Stage11RestoreInstrumentedTest",
            "com.blackatsystems.miguardia.backup.BackupRecoveryInstrumentedTest",
            "com.blackatsystems.miguardia.backup.BackupPhotoFilesInstrumentedTest",
            "com.blackatsystems.miguardia.backup.BackupPhotoMutationGateInstrumentedTest",
            "com.blackatsystems.miguardia.backup.PortablePreferencesGatewayInstrumentedTest",
            "com.blackatsystems.miguardia.Stage4NoteRestoreConcurrencyTest",
        )
        fun hash(file: File): String = file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
    }
}

object Stage11Preservation {
    val pending = mutableSetOf<String>()
}

/** Also isolates the gateway's orphan-candidate sweep, not just the main DB name. */
class Stage11RootContext(base: Context, id: String) : ContextWrapper(base) {
    val root = File(base.filesDir, "reports/artifacts/stage11-$id").also { check(it.mkdirs()) }
    lateinit var application: Application
    private val prefix = "stage11-$id-"
    override fun getApplicationContext(): Context = if (::application.isInitialized) application else this
    override fun getFilesDir(): File = File(root, "files").also { it.mkdirs() }
    override fun getCacheDir(): File = File(root, "cache").also { it.mkdirs() }
    override fun getNoBackupFilesDir(): File = File(root, "no_backup").also { it.mkdirs() }
    override fun getDatabasePath(name: String): File {
        check(!name.contains('/') && !name.contains('\\') && name != ".." && name != "miguardia-v2.db")
        return File(File(root, "databases").also { it.mkdirs() }, name)
    }
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        baseContext.openOrCreateDatabase(getDatabasePath(name).absolutePath, mode, factory)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
        baseContext.openOrCreateDatabase(getDatabasePath(name).absolutePath, mode, factory, errorHandler)
    override fun deleteDatabase(name: String): Boolean = SQLiteDatabase.deleteDatabase(getDatabasePath(name))
    override fun databaseList(): Array<String> = File(root, "databases").list() ?: emptyArray()
    override fun getSharedPreferences(name: String, mode: Int) = baseContext.getSharedPreferences(prefix + name, mode)
    override fun deleteSharedPreferences(name: String): Boolean = baseContext.deleteSharedPreferences(prefix + name)
}
