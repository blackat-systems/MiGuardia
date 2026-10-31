package com.blackatsystems.miguardia.stage12

import android.app.Application
import android.content.*
import android.content.pm.ApplicationInfo
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.view.LayoutInflater
import com.blackatsystems.miguardia.QaInstrumentationContext
import com.blackatsystems.miguardia.QaInstrumentationScope
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/** Constructed before Application.attach; no path is created until admitted onCreate arguments. */
internal class Stage12RootBinding(val live: Context) : QaInstrumentationContext {
    lateinit var application: Application
    private var fence: Stage12PathPolicy? = null
    private var preferenceFence: Stage12PathPolicy? = null
    private var uuid: String? = null
    val suppressedOperations = java.util.Collections.synchronizedList(mutableListOf<String>())
    val root: File get() = checkNotNull(fence) { "Stage12 root is not admitted" }.root
    val runId: String get() = checkNotNull(uuid) { "Stage12 run is not admitted" }
    val preferencePrefix: String get() = "stage12-$runId-"

    fun bind(runId: String) {
        check(fence == null)
        require(UUID.fromString(runId).toString() == runId)
        val liveRoot = File(live.applicationInfo.dataDir).canonicalFile
        val parent = File(liveRoot, "files/reports/artifacts")
        check(parent.canonicalFile == parent) { "Owned parent alias rejected" }
        val preferences = File(liveRoot, "shared_prefs")
        check(preferences.canonicalFile == preferences)
        check(preferences.listFiles().orEmpty().none { it.name.startsWith("stage12-$runId-") }) { "Preference namespace already exists" }
        val owned = File(parent, "stage12-$runId")
        check(!owned.exists()) { "Run root already exists" }
        val policy = Stage12PathPolicy(owned)
        check(policy.root.parentFile == parent)
        uuid = runId
        fence = policy
        preferenceFence = Stage12PathPolicy(preferences)
        check(policy.root.mkdirs()) { "Cannot create owned root" }
    }

    fun path(relative: String): File = checkNotNull(fence) { "Unbound storage" }.resolve(relative)
    fun directory(relative: String): File = path(relative).also { check(it.isDirectory || it.mkdirs()) }
    fun preferenceName(name: String): String {
        require(name.matches(Regex("[A-Za-z0-9_.-]{1,100}")) && name != "." && name != "..")
        val ownedName = preferencePrefix + name
        val policy = checkNotNull(preferenceFence) { "Unbound preferences" }
        policy.segment("$ownedName.xml")
        policy.segment("$ownedName.xml.bak")
        return ownedName
    }
    override fun wrapActivity(base: Context): Context = Stage12RootContext(base, this)
    override fun suppressed(operation: String) { suppressedOperations.add(operation) }
}

internal class Stage12RootContext(base: Context, internal val binding: Stage12RootBinding) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = binding.application
    override fun getApplicationInfo(): ApplicationInfo {
        val source = super.getApplicationInfo()
        // A copy constructor also copies hidden credential paths. Use public safe metadata only.
        return ApplicationInfo().apply {
            packageName = source.packageName; className = source.className; processName = source.processName
            name = source.name; uid = source.uid; flags = source.flags; enabled = source.enabled
            sourceDir = source.sourceDir; publicSourceDir = source.publicSourceDir
            splitSourceDirs = source.splitSourceDirs?.clone(); splitPublicSourceDirs = source.splitPublicSourceDirs?.clone()
            nativeLibraryDir = source.nativeLibraryDir; targetSdkVersion = source.targetSdkVersion; minSdkVersion = source.minSdkVersion
            theme = source.theme; icon = source.icon; labelRes = source.labelRes; nonLocalizedLabel = source.nonLocalizedLabel
            dataDir = binding.root.path
            deviceProtectedDataDir = binding.path("device_protected").path
        }
    }
    override fun getDataDir(): File = binding.root
    override fun getFilesDir(): File = binding.directory("files")
    override fun getCacheDir(): File = binding.directory("cache")
    override fun getCodeCacheDir(): File = binding.directory("code_cache")
    override fun getNoBackupFilesDir(): File = binding.directory("no_backup")
    override fun getDir(name: String, mode: Int): File {
        require(mode == MODE_PRIVATE)
        Stage12PathPolicy(binding.root).segment(name)
        return binding.directory("app_$name")
    }
    override fun getFileStreamPath(name: String): File {
        Stage12PathPolicy(binding.root).segment(name)
        return binding.path("files/$name")
    }
    override fun openFileInput(name: String): FileInputStream = FileInputStream(getFileStreamPath(name))
    override fun openFileOutput(name: String, mode: Int): FileOutputStream {
        require(mode == MODE_PRIVATE || mode == MODE_APPEND)
        val target = getFileStreamPath(name)
        binding.directory("files")
        return FileOutputStream(target, mode == MODE_APPEND)
    }
    override fun deleteFile(name: String): Boolean = getFileStreamPath(name).delete()
    override fun fileList(): Array<String> = filesDir.list().orEmpty().map { it }.toTypedArray()
    override fun getDatabasePath(name: String): File {
        Stage12PathPolicy(binding.root).segment(name)
        val target = binding.path("databases/$name")
        listOf("-wal", "-shm", "-journal").forEach { binding.path("databases/$name$it") }
        return target
    }
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        openOrCreateDatabase(name, mode, factory, null)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase {
        val allowed = MODE_ENABLE_WRITE_AHEAD_LOGGING or MODE_NO_LOCALIZED_COLLATORS
        require(mode and allowed.inv() == 0)
        val target = getDatabasePath(name)
        binding.directory("databases")
        var flags = SQLiteDatabase.CREATE_IF_NECESSARY
        if (mode and MODE_ENABLE_WRITE_AHEAD_LOGGING != 0) flags = flags or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
        if (mode and MODE_NO_LOCALIZED_COLLATORS != 0) flags = flags or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        return SQLiteDatabase.openDatabase(target.path, factory, flags, errorHandler)
    }
    override fun deleteDatabase(name: String): Boolean = SQLiteDatabase.deleteDatabase(getDatabasePath(name))
    override fun databaseList(): Array<String> = binding.path("databases").list().orEmpty().map { it }.toTypedArray()
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        require(mode == MODE_PRIVATE)
        return binding.live.getSharedPreferences(binding.preferenceName(name), mode)
    }
    override fun deleteSharedPreferences(name: String): Boolean = binding.live.deleteSharedPreferences(binding.preferenceName(name))
    override fun moveSharedPreferencesFrom(sourceContext: Context, name: String): Boolean = denied("preferences.import")
    override fun moveDatabaseFrom(sourceContext: Context, name: String): Boolean = denied("database.import")
    override fun getExternalFilesDir(type: String?): File? = denied("external.files")
    override fun getExternalFilesDirs(type: String?): Array<File> = denied("external.files")
    override fun getExternalCacheDir(): File? = denied("external.cache")
    override fun getExternalCacheDirs(): Array<File> = denied("external.cache")
    override fun getExternalMediaDirs(): Array<File> = denied("external.media")
    override fun getObbDir(): File = denied("external.obb")
    override fun getObbDirs(): Array<File> = denied("external.obb")
    override fun createDeviceProtectedStorageContext(): Context = denied("context.device-protected")
    override fun createDeviceContext(deviceId: Int): Context = denied("context.device")
    override fun createPackageContext(packageName: String, flags: Int): Context = denied("context.package")
    override fun createContextForSplit(splitName: String): Context = denied("context.split")
    override fun createDisplayContext(display: android.view.Display): Context = denied("context.display")
    override fun createAttributionContext(attributionTag: String?): Context = denied("context.attribution")
    override fun createWindowContext(type: Int, options: Bundle?): Context = denied("context.window")
    override fun createWindowContext(display: android.view.Display, type: Int, options: Bundle?): Context = denied("context.window")
    override fun createContext(contextParams: ContextParams): Context = denied("context.params")
    override fun createConfigurationContext(overrideConfiguration: android.content.res.Configuration): Context =
        Stage12RootContext(baseContext.createConfigurationContext(overrideConfiguration), binding)
    override fun getContentResolver(): ContentResolver = denied("content.resolver")
    override fun getSystemService(name: String): Any? {
        if (name in setOf(ALARM_SERVICE, NOTIFICATION_SERVICE, APPWIDGET_SERVICE, LOCATION_SERVICE, DOWNLOAD_SERVICE)) denied<Unit>("service.$name")
        val service = super.getSystemService(name)
        return if (service is LayoutInflater) service.cloneInContext(this) else service
    }
    override fun startActivity(intent: Intent) = startActivity(intent, null)
    override fun startActivity(intent: Intent, options: Bundle?) {
        QaInstrumentationScope.requireActivityLaunch(this, intent)
        super.startActivity(intent, options)
    }
    override fun startActivities(intents: Array<out Intent>, options: Bundle?) { intents.forEach { QaInstrumentationScope.requireActivityLaunch(this, it) }; super.startActivities(intents, options) }
    override fun startActivities(intents: Array<out Intent>) = startActivities(intents, null)
    override fun startService(service: Intent): ComponentName? = denied("service.start")
    override fun startForegroundService(service: Intent): ComponentName? = denied("service.foreground")
    override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean = denied("service.bind")
    override fun sendBroadcast(intent: Intent) { denied<Unit>("broadcast.send") }
    override fun sendBroadcast(intent: Intent, receiverPermission: String?) { denied<Unit>("broadcast.send") }
    private fun <T> denied(operation: String): T {
        binding.suppressed(operation)
        throw SecurityException("Excluded Stage12 operation: $operation")
    }
}
