package com.blackatsystems.miguardia.stage12

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.ContextParams
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.view.Display
import com.blackatsystems.miguardia.QaMainActivityFileContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** File capabilities for one temporal fixture. Application startup and its singleton stay ordinary. */
internal class Stage12B1HumanPrivateContext(
    base: Context,
    private val frameworkContext: Context,
    val binding: Stage12RecurringPaths,
    private val fail: (Throwable) -> Nothing,
    private val ownApplicationContext: Context? = null,
    override val explicitHumanSession: Stage12B1HumanSession,
    override val humanOwnerInstance: android.app.Activity,
) : ContextWrapper(base), com.blackatsystems.miguardia.QaExplicitHumanActionContext {
    init { explicitHumanSession.contextCreated(this, binding) }
    private fun reject(operation: String): Nothing = fail(SecurityException("TEMPORAL_EXTERNAL_EFFECT_EXCLUDED:$operation"))
    private fun <T> checked(block: () -> T): T = try { block() } catch (failure: Throwable) { fail(failure) }
    private fun requireFiles() {
        checked {
            check(frameworkContext.packageName == "com.blackatsystems.miguardia.qa")
            binding.requireStored(stage12RecurringPathAccess(frameworkContext))
        }
    }
    override fun getApplicationContext(): Context = ownApplicationContext ?: this
    override fun getFilesDir(): File { requireFiles(); return binding.dataRoot }
    override fun getCacheDir(): File = checked { requireFiles(); Stage12PathPolicy(binding.privateRoot).resolve("cache") }
    override fun getNoBackupFilesDir(): File = checked { requireFiles(); Stage12PathPolicy(binding.privateRoot).resolve("no_backup") }
    override fun getDataDir(): File { requireFiles(); return binding.privateRoot }
    override fun getDatabasePath(name: String): File {
        requireFiles()
        if (name != binding.name) reject("context.foreign-database")
        return binding.database
    }

    // Product consumers in this graph use explicit File APIs and injected DataStores.
    // An unadmitted storage API is a negative, never a received-storage fallback.
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = reject("context.shared-preferences")
    override fun openFileInput(name: String): FileInputStream = reject("context.file-input")
    override fun openFileOutput(name: String, mode: Int): FileOutputStream = reject("context.file-output")
    override fun getFileStreamPath(name: String): File = reject("context.file-stream-path")
    override fun getDir(name: String, mode: Int): File = reject("context.directory")
    override fun deleteFile(name: String): Boolean = reject("context.file-delete")
    override fun deleteDatabase(name: String): Boolean = reject("context.database-delete")
    override fun databaseList(): Array<String> = reject("context.database-list")
    override fun openOrCreateDatabase(name: String?, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        reject("context.database-open")
    override fun openOrCreateDatabase(name: String?, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
        reject("context.database-open-handler")
    override fun moveDatabaseFrom(sourceContext: Context, name: String): Boolean = reject("context.database-move")
    override fun moveSharedPreferencesFrom(sourceContext: Context, name: String): Boolean = reject("context.preferences-move")
    override fun deleteSharedPreferences(name: String): Boolean = reject("context.preferences-delete")
    override fun fileList(): Array<String> = reject("context.file-list")
    override fun getCodeCacheDir(): File = reject("context.code-cache")
    // Compose/system UI reads settings through this ordinary service (for example animation scale).
    // Reports receives a separate deny-only ReportDestination, never this capability for fixture output.
    override fun getContentResolver(): ContentResolver = frameworkContext.contentResolver
    override fun getExternalFilesDir(type: String?): File? = reject("context.external-files")
    override fun getExternalCacheDir(): File? = reject("context.external-cache")
    override fun getExternalFilesDirs(type: String?): Array<File> = reject("context.external-files-list")
    override fun getExternalCacheDirs(): Array<File> = reject("context.external-cache-list")
    override fun getExternalMediaDirs(): Array<File> = reject("context.external-media-list")
    override fun getObbDir(): File = reject("context.obb")
    override fun getObbDirs(): Array<File> = reject("context.obb-list")
    override fun createDeviceProtectedStorageContext(): Context = reject("context.device-protected-storage")
    override fun createPackageContext(packageName: String, flags: Int): Context = reject("context.package-context")
    override fun createAttributionContext(attributionTag: String?): Context = reject("context.attribution-context")
    override fun createContext(contextParams: ContextParams): Context = reject("context.params-context")
    override fun createContextForSplit(splitName: String): Context = reject("context.split-context")
    override fun createDisplayContext(display: Display): Context = reject("context.display-context")
    override fun createDeviceContext(deviceId: Int): Context = reject("context.device-context")
    override fun createWindowContext(type: Int, options: Bundle?): Context = reject("context.window-context")
    override fun createWindowContext(display: Display, type: Int, options: Bundle?): Context = reject("context.display-window-context")

    /** A resource configuration retains the same file capabilities and original application context. */
    override fun createConfigurationContext(overrideConfiguration: Configuration): Context = checked {
        Stage12B1HumanPrivateContext(baseContext.createConfigurationContext(overrideConfiguration), frameworkContext,
            binding, fail, applicationContext, explicitHumanSession, humanOwnerInstance)
    }

    override fun startActivity(intent: Intent) {
        val index = explicitHumanSession.requireMapIntent(this, intent)
        try { baseContext.startActivity(intent); explicitHumanSession.mapReturned(index, null) }
        catch (error: Throwable) { explicitHumanSession.mapReturned(index, error); throw error }
    }
    override fun startActivity(intent: Intent, options: Bundle?) = reject("context.activity-launch-options")
    override fun startActivities(intents: Array<out Intent>) = reject("context.activities-launch")
    override fun startActivities(intents: Array<out Intent>, options: Bundle?) = reject("context.activities-launch-options")

    override fun rejectFixtureExternalEffect(operation: String): Nothing = reject(operation)

    fun forActivity(base: Context): Stage12B1HumanPrivateContext = Stage12B1HumanPrivateContext(
        base, frameworkContext, binding, fail, applicationContext, explicitHumanSession, humanOwnerInstance,
    )
}
