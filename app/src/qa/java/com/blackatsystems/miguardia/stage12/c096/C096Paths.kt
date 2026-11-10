package com.blackatsystems.miguardia.stage12.c096

import android.content.*
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import java.io.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS

/** Framework anchor may have Android's trusted alias; every managed component must be canonical. */
internal class C096Paths(val framework: Context, val variant: C096Variant) {
    private val app = File(framework.applicationInfo.dataDir).canonicalFile
    private val anchor = framework.filesDir.canonicalFile
    val privateRoot = File(anchor, "stage12-c096-" + variant.run)
    val files = File(privateRoot, "files")
    val cache = File(privateRoot, "cache")
    val noBackup = File(privateRoot, "no_backup")
    val name = "stage12-c096-" + variant.run + ".db"
    val database = File(File(app, "databases"), name)
    val reportRoot = File(anchor, "reports/artifacts/stage12-c096-" + variant.run)
    val source = File(files, "source.png")
    private fun own(f: File): File {
        check(f.absoluteFile == f.canonicalFile && !Files.isSymbolicLink(f.toPath())) { "C096_PATH_ALIAS" }
        return f
    }
    fun revalidate() {
        check(framework.packageName == C096Recipe.QA && File(framework.applicationInfo.dataDir).canonicalFile == app)
        check(framework.filesDir.canonicalFile == anchor && anchor.parentFile == app && anchor.name == "files")
        own(anchor); own(database.parentFile!!); own(privateRoot); own(reportRoot.parentFile!!)
        listOf("", "-wal", "-shm", "-journal").forEach { own(File(database.path + it)) }
        check(database.parentFile == File(app, "databases") && privateRoot.parentFile == anchor)
    }
    fun create() {
        revalidate()
        check(!Files.exists(privateRoot.toPath(), NOFOLLOW_LINKS) && !Files.exists(reportRoot.toPath(), NOFOLLOW_LINKS))
        listOf("", "-wal", "-shm", "-journal").forEach { check(!Files.exists(File(database.path + it).toPath(), NOFOLLOW_LINKS)) }
        check(privateRoot.mkdir())
        for (f in listOf(files, cache, noBackup, File(privateRoot,"shared_prefs"), File(files,"datastore"), File(files,"schedule_photos"), File(files,"manual_load_drafts"))) { own(f); check(f.mkdir()) }
        check(reportRoot.mkdir())
        Files.write(source.toPath(), C096Recipe.png, java.nio.file.StandardOpenOption.CREATE_NEW)
        Files.write(File(privateRoot,"shared_prefs/c096.xml").toPath(), "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><string name='fixture'>C096</string><int name='sequence' value='1'/></map>".toByteArray(), java.nio.file.StandardOpenOption.CREATE_NEW)
        Files.write(File(files,"datastore/c096.preferences_pb").toPath(), byteArrayOf(), java.nio.file.StandardOpenOption.CREATE_NEW)
    }
    fun requireStored() {
        revalidate()
        check(database.isFile && privateRoot.isDirectory && files.isDirectory && reportRoot.isDirectory && source.isFile)
        check(source.readBytes().contentEquals(C096Recipe.png))
    }
    fun file(relative: String): File = C096PathPolicy(privateRoot).resolve(relative)
    fun cleanupAfterClosure() {
        requireStored()
        fun remove(f: File) { own(f); check(f.toPath().startsWith(privateRoot.toPath())); if(f.isDirectory) checkNotNull(f.listFiles()).forEach(::remove); check(f.delete()) }
        remove(privateRoot)
        listOf("", "-wal", "-shm", "-journal").forEach { val f=own(File(database.path+it)); if(Files.exists(f.toPath(),NOFOLLOW_LINKS)){check(f.isFile);check(f.delete())} }
    }
}

/** Only the fixture's storage changes; framework resources and event services remain real. */
internal class C096Context(base: Context, private val paths: C096Paths) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = paths.files.also { paths.revalidate() }
    override fun getCacheDir(): File = paths.cache.also { paths.revalidate() }
    override fun getCodeCacheDir(): File = cacheDir
    override fun getNoBackupFilesDir(): File = paths.noBackup.also { paths.revalidate() }
    override fun getDatabasePath(name: String): File { check(name==paths.name);paths.revalidate();return paths.database }
    override fun openOrCreateDatabase(name:String,mode:Int,factory:SQLiteDatabase.CursorFactory?):SQLiteDatabase { check(mode==MODE_PRIVATE);return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory) }
    override fun openOrCreateDatabase(name:String,mode:Int,factory:SQLiteDatabase.CursorFactory?,handler:DatabaseErrorHandler?):SQLiteDatabase { check(mode==MODE_PRIVATE);return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path,factory,handler) }
    override fun databaseList(): Array<String> = arrayOf(paths.name)
    override fun deleteDatabase(name:String):Boolean = deny()
    override fun getSharedPreferences(name:String,mode:Int):SharedPreferences = deny()
    override fun moveSharedPreferencesFrom(sourceContext:Context,name:String):Boolean = deny()
    override fun deleteSharedPreferences(name:String):Boolean = deny()
    override fun openFileInput(name:String):FileInputStream { check('/' !in name && '\\' !in name);return FileInputStream(paths.file("files/"+name)) }
    override fun openFileOutput(name:String,mode:Int):FileOutputStream { check(mode==MODE_PRIVATE && '/' !in name && '\\' !in name);return FileOutputStream(paths.file("files/"+name)) }
    override fun getFileStreamPath(name:String):File { check('/' !in name && '\\' !in name);return paths.file("files/"+name) }
    override fun deleteFile(name:String):Boolean = deny()
    override fun fileList():Array<String> = checkNotNull(filesDir.list())
    override fun getDir(name:String,mode:Int):File = deny()
    override fun getExternalFilesDir(type:String?):File? = deny()
    override fun getExternalFilesDirs(type:String?):Array<File> = deny()
    override fun getExternalCacheDir():File? = deny()
    override fun getExternalCacheDirs():Array<File> = deny()
    override fun getObbDir():File = deny()
    override fun getObbDirs():Array<File> = deny()
    override fun createDeviceProtectedStorageContext():Context = deny()
    override fun createPackageContext(packageName:String,flags:Int):Context = deny()
    override fun createConfigurationContext(configuration:android.content.res.Configuration):Context = C096Context(baseContext.createConfigurationContext(configuration),paths)
    override fun createDisplayContext(display:android.view.Display):Context = deny()
    override fun createWindowContext(type:Int,options:android.os.Bundle?):Context = deny()
    override fun createWindowContext(display:android.view.Display,type:Int,options:android.os.Bundle?):Context = deny()
    override fun createContext(params:ContextParams):Context = deny()
    override fun createDeviceContext(deviceId:Int):Context = deny()
    override fun startActivity(intent:Intent):Unit = deny()
    override fun startActivity(intent:Intent,options:android.os.Bundle?):Unit = deny()
    override fun startService(service:Intent):ComponentName? = deny()
    override fun startForegroundService(service:Intent):ComponentName? = deny()
    override fun bindService(service:Intent,conn:ServiceConnection,flags:Int):Boolean = deny()
    override fun sendBroadcast(intent:Intent):Unit = deny()
    override fun sendBroadcast(intent:Intent,receiverPermission:String?):Unit = deny()
    private fun deny():Nothing = throw SecurityException("C096_EXTERNAL_EFFECT_EXCLUDED")
}
