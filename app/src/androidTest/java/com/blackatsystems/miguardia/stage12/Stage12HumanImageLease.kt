package com.blackatsystems.miguardia.stage12

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject

/** One synthetic row, never a media query/enumeration. Creation uncertainty is retained, not inferred absent. */
internal class Stage12HumanImageLease(private val context: Context, private val run: String, private val bytes: ByteArray) {
    companion object { const val SIZE = 5410; const val SHA = "05c1cfded8072ea5c25029036a7c18638d9dcf3ad0ad132615be581d3fa852e8" }
    private val resolver = context.contentResolver
    private val name = "MG_QA_C074_" + run + ".png"
    private val directory = "Pictures/MiGuardiaQA_" + run + "/"
    private val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private var uri: Uri? = null
    private var attempted = false
    val projection = JSONObject().put("schema", "stage12-human-image-lease-v1").put("source_sha256", SHA)
        .put("source_bytes", SIZE).put("width", 640).put("height", 360).put("create_attempted", false)
        .put("create_returned", false).put("published", false).put("delete_returned", false).put("absence_observed", false)
    fun publish() {
        check(context.packageName == "com.blackatsystems.miguardia.qa" && UUID.fromString(run).toString() == run)
        check(!attempted && bytes.size == SIZE && hash(bytes) == SHA); attempted = true
        projection.put("create_attempted", true)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, directory); put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val created = checkNotNull(resolver.insert(collection, values)); uri = created
        check(created.scheme == "content" && created.authority == "media" &&
            created.toString().startsWith(collection.toString()+"/") && created.lastPathSegment?.toLongOrNull() != null)
        projection.put("create_returned", true).put("uri_sha256", hash(created.toString().toByteArray()))
        checkNotNull(resolver.openOutputStream(created, "w")).use { it.write(bytes); it.flush() }
        verify(1)
        check(resolver.update(created, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1)
        verify(0); projection.put("published", true)
    }
    fun verify(pending: Int = 0) {
        val own = checkNotNull(uri)
        val columns = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.IS_PENDING, MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        checkNotNull(resolver.query(own, columns, null, null, null)).use { row ->
            check(row.count == 1 && row.moveToFirst())
            check(row.getString(0) == name && row.getString(1) == "image/png" && row.getString(2) == directory &&
                row.getInt(3) == pending && (pending == 1 || row.getLong(4) == SIZE.toLong()) && row.getString(5) == context.packageName)
        }
        check(readExact(own).contentEquals(bytes))
    }
    fun requireSelected(selected: Uri) {
        check(selected.scheme == "content")
        val own = checkNotNull(uri)
        val mapped = if (selected == own) selected else MediaStore.getMediaUri(context, selected)
        check(mapped == own) { "HUMAN_SELECTED_MEDIA_ID_NOT_PROVEN" }
        check(resolver.getType(selected) == "image/png")
        check(readExact(selected).contentEquals(bytes)); verify()
        projection.put("selected_bytes_exact", true).put("selected_uri_sha256", hash(selected.toString().toByteArray()))
    }
    private fun readExact(value: Uri): ByteArray = checkNotNull(resolver.openInputStream(value)).use { input ->
        val output = java.io.ByteArrayOutputStream(SIZE); val buffer = ByteArray(4096)
        while (true) { val count = input.read(buffer); if (count < 0) break; check(output.size()+count <= SIZE); output.write(buffer,0,count) }
        output.toByteArray().also { check(it.size == SIZE && hash(it) == SHA) }
    }
    fun close() {
        check(projection.getBoolean("published")); verify()
        check(resolver.delete(checkNotNull(uri), null, null) == 1); projection.put("delete_returned", true)
        checkNotNull(resolver.query(checkNotNull(uri), arrayOf(MediaStore.MediaColumns._ID), null, null, null)).use { check(it.count == 0) }
        projection.put("absence_observed", true)
    }
    private fun hash(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it.toInt() and 255) }
}
