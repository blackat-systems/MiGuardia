package com.blackatsystems.miguardia.stage12

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun stage12Hash(file: File): String = file.inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(65536)
    while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
    digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}

internal data class Stage12Inventory(val entries: Map<String, Pair<Long, String>>) {
    fun json(): JSONObject = JSONObject().put("count", entries.size).put("entries", JSONObject().also { result ->
        entries.forEach { (name, value) -> result.put(name, JSONObject().put("bytes", value.first).put("sha256", value.second)) }
    })
}

internal class Stage12Session(val binding: Stage12RootBinding, val args: Stage12Arguments) {
    companion object { @Volatile lateinit var current: Stage12Session }
    private val liveRoot = File(binding.live.applicationInfo.dataDir).canonicalFile
    private val owned = File(liveRoot, "files/reports/artifacts/stage12-${args.runId}").canonicalFile
    private val prefix = "stage12-${args.runId}-"
    private val filesBefore = inventory(false)
    private val preferencesBefore = inventory(true)
    private val assertions = mutableListOf<JSONObject>()
    private var canaries: Map<String, Pair<Long, String>>? = null
    private var preferenceCanary: Pair<Long, String>? = null
    val evidence: File get() = binding.directory("evidence")

    /** Baselines contain hashes/lengths only. No received bytes are copied or restored. */
    private fun inventory(preferences: Boolean): Stage12Inventory {
        val entries = sortedMapOf<String, Pair<Long, String>>()
        val roots = if (preferences) listOf("shared_prefs") else listOf("files", "databases", "no_backup")
        fun visit(file: File) {
            if (file == owned || (preferences && file.name.startsWith(prefix))) return
            check(file.canonicalFile == file.absoluteFile) { "Received alias cannot be inventoried safely" }
            if (!file.exists()) return
            if (file.isDirectory) file.listFiles().let { checkNotNull(it) }.sortedBy { it.name }.forEach(::visit)
            else {
                check(file.isFile)
                val relative = liveRoot.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                entries[relative] = file.length() to stage12Hash(file)
            }
        }
        roots.forEach { visit(File(liveRoot, it)) }
        return Stage12Inventory(entries)
    }

    fun createCanaries() {
        check(canaries == null)
        val directory = binding.directory("canaries")
        File(directory, "file.canary").apply { check(createNewFile()); writeText("stage12 synthetic canary") }
        val database = File(directory, "database.canary")
        SQLiteDatabase.openOrCreateDatabase(database, null).use { db ->
            db.execSQL("CREATE TABLE sentinel (id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("INSERT INTO sentinel VALUES (1, 'stage12 synthetic canary')")
        }
        check(binding.live.getSharedPreferences(binding.preferenceName("canary"), Context.MODE_PRIVATE).edit().putString("sentinel", "stage12 synthetic canary").commit())
        canaries = directory.listFiles()!!.associate { it.name to (it.length() to stage12Hash(it)) }
        val preference = File(liveRoot, "shared_prefs/${binding.preferenceName("canary")}.xml")
        check(preference.canonicalFile == preference && preference.isFile)
        preferenceCanary = preference.length() to stage12Hash(preference)
    }

    @Synchronized fun record(caseId: String, subcase: String, key: String, level: String, evidenceKind: String) {
        require(caseId in setOf("S12-B03", "S12-B04"))
        require(level in setOf("ACTIVITY_NATIVE", "FILES_ISOLATED"))
        require(subcase.matches(Regex("[a-z0-9-]+")) && key.matches(Regex("[a-z0-9-]+")))
        check(assertions.none { it.getString("assertion_key") == key && it.getString("subcase_id") == subcase })
        assertions.add(JSONObject().put("case_id", caseId).put("subcase_id", subcase).put("round", 0)
            .put("assertion_key", key).put("level", level).put("evidence_kind", evidenceKind).put("requires_tables", false).put("result", "PASS"))
    }

    fun artifact(name: String): File = Stage12PathPolicy(evidence).segment(name).also { check(!it.exists()) }
    fun writeJson(name: String, json: JSONObject): File = artifact(name).apply { check(createNewFile()); writeText(json.toString(2), Charsets.UTF_8) }

    fun complete(counts: Stage12Counts): File {
        check(counts.isSinglePass()) { "JUnit status did not prove one start and one pass" }
        check(assertions.isNotEmpty()) { "No test assertion receipts" }
        val after = inventory(false)
        val prefsAfter = inventory(true)
        check(filesBefore == after && preferencesBefore == prefsAfter) { "Received durable bytes changed" }
        val expectedCanaries = checkNotNull(canaries)
        val actualCanaries = binding.path("canaries").listFiles()!!.associate { it.name to (it.length() to stage12Hash(it)) }
        check(expectedCanaries == actualCanaries) { "Owned canary bytes changed" }
        val preference = File(liveRoot, "shared_prefs/${binding.preferenceName("canary")}.xml")
        check(preference.canonicalFile == preference && preferenceCanary == (preference.length() to stage12Hash(preference))) { "Owned preference canary bytes changed" }
        check(binding.live.getSharedPreferences(binding.preferenceName("canary"), Context.MODE_PRIVATE).getString("sentinel", null) == "stage12 synthetic canary")
        check(binding.suppressedOperations.contains("application.received-startup-omitted")) { "Real Application startup hook not observed" }
        record("S12-B03", "received-conservation", "received-bytes-and-owned-canaries-unchanged", "FILES_ISOLATED", "independent-before-after-hashes")
        writeJson("canaries.json", JSONObject().put("schema", "stage12-canaries-v1").put("preserved", true)
            .put("location", "owned root/canaries; owned preference namespace").put("received_data_restored", false))
        writeJson("isolation.json", JSONObject().put("schema", "stage12-isolation-v1").put("mode", args.mode).put("root_id", args.runId)
            .put("files", JSONObject().put("before", filesBefore.json()).put("after", after.json()))
            .put("preferences", JSONObject().put("before", preferencesBefore.json()).put("after", prefsAfter.json()))
            .put("tables", JSONObject().put("status", "NOT_MEASURED").put("reason", "Received SQLite is never opened; byte inventory does not measure tables"))
            .put("resources", JSONObject().put("scoped", true).put("identities", JSONArray())))
        writeJson("services.json", JSONObject().put("schema", "stage12-services-v1").put("mode", args.mode)
            .put("observed_suppressed_operations", JSONArray(binding.suppressedOperations.toList()))
            .put("startup", "received startup/recovery omitted by real Application hook")
            .put("backup", "not invoked; no recovery or restoration")
            .put("services", "not started; notifications/widgets/reconcile/OEM not tested")
            .put("excluded_owned_root", "files/reports/artifacts/stage12-${args.runId}")
            .put("excluded_owned_preferences_prefix", prefix)
            .put("technical_cache", "not a durable received-data inventory; owned output only"))
        writeJson("receipt.json", JSONObject().put("schema", "stage12-receipt-v1").put("run_id", args.runId).put("assertions", JSONArray(assertions)))
        val members = evidence.listFiles()!!.sortedBy { it.name }
        check(members.all { it.isFile && it.canonicalFile == it.absoluteFile && it.length() <= 8L * 1024 * 1024 })
        check(members.sumOf { it.length() } <= 30L * 1024 * 1024 && members.size < 2048)
        val inventory = JSONArray()
        members.forEach { inventory.put(JSONObject().put("path", it.name).put("bytes", it.length()).put("sha256", stage12Hash(it))) }
        val bundle = writeJson("bundle.json", JSONObject().put("schema", "stage12-evidence-v1").put("run_id", args.runId)
            .put("qa_sha256", args.qaSha).put("runner_sha256", args.runnerSha).put("selector", args.selector)
            .put("expected_methods", JSONArray(listOf(args.selector))).put("counts", counts.json()).put("files", inventory))
        val zip = binding.path("cache/stage12-${args.runId}.zip")
        binding.directory("cache")
        check(zip.createNewFile())
        // STORED avoids excessive expansion ratios for synthetic flat-colour PNG evidence.
        ZipOutputStream(zip.outputStream()).use { output ->
            (members + bundle).forEach { file ->
                val crc = CRC32().apply { file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; update(buffer, 0, n) } } }
                output.putNextEntry(ZipEntry(file.name).apply { method = ZipEntry.STORED; size = file.length(); compressedSize = size; this.crc = crc.value })
                file.inputStream().use { it.copyTo(output) }; output.closeEntry()
            }
        }
        check(zip.length() <= 32L * 1024 * 1024)
        return zip
    }
}
