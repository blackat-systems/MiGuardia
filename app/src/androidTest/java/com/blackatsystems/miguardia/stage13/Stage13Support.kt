package com.blackatsystems.miguardia.stage13

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.exifinterface.media.ExifInterface
import com.blackatsystems.miguardia.backup.BackupCreateOptions
import com.blackatsystems.miguardia.core.domain.backup.BackupDatabaseSnapshot
import com.blackatsystems.miguardia.core.domain.backup.BackupPreference
import com.blackatsystems.miguardia.core.domain.model.SchedulePhoto
import com.blackatsystems.miguardia.core.domain.model.ShiftNote
import com.blackatsystems.miguardia.core.domain.model.ShiftNoteAttachment
import com.blackatsystems.miguardia.core.domain.model.V2ShiftWrite
import com.blackatsystems.miguardia.stage12.Stage12CalculationStore
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.flow.first
import com.blackatsystems.miguardia.stage12.Stage12CalculationFixture as F

/**
 * Selectores controlados por la prueba: cada lanzamiento consume la respuesta preparada con [answer].
 * Nunca abre la galería ni el selector de documentos reales. Un lanzamiento sin respuesta preparada se
 * registra en [unexpected] y se contesta como cancelado (la prueba lo comprueba al final).
 */
internal class Stage13Pickers : ActivityResultRegistry(), ActivityResultRegistryOwner {
    enum class Kind { CREATE_DOCUMENT, OPEN_DOCUMENT, PICK_ONE_IMAGE, PICK_IMAGES }

    override val activityResultRegistry: ActivityResultRegistry get() = this
    private val answers = Collections.synchronizedList(mutableListOf<Pair<Kind, Any?>>())
    val launched: MutableList<Kind> = Collections.synchronizedList(mutableListOf())
    val unexpected: MutableList<Kind> = Collections.synchronizedList(mutableListOf())

    /** Prepara la próxima respuesta: `null` (o lista vacía en [Kind.PICK_IMAGES]) equivale a cancelar. */
    fun answer(kind: Kind, result: Any?) {
        answers += kind to result
    }

    val pending: Int get() = answers.size

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        val kind = when (contract) {
            is ActivityResultContracts.CreateDocument -> Kind.CREATE_DOCUMENT
            is ActivityResultContracts.OpenDocument -> Kind.OPEN_DOCUMENT
            is ActivityResultContracts.PickMultipleVisualMedia -> Kind.PICK_IMAGES
            is ActivityResultContracts.PickVisualMedia -> Kind.PICK_ONE_IMAGE
            else -> null
        }
        val next = synchronized(answers) { answers.firstOrNull()?.takeIf { it.first == kind }?.also { answers.removeAt(0) } }
        if (kind != null) launched += kind
        val result: Any? = if (next == null) {
            unexpected += kind ?: Kind.OPEN_DOCUMENT
            if (kind == Kind.PICK_IMAGES) emptyList<Uri>() else null
        } else next.second
        // Fuera del efecto que lanzó, como hace el sistema al volver de un selector.
        Handler(Looper.getMainLooper()).post { dispatchResult(requestCode, result) }
    }
}

/** Foto completa de los datos de la corrida: base lógica, preferencias portables y archivos privados. */
internal data class Stage13Digest(
    val database: BackupDatabaseSnapshot,
    val preferences: List<BackupPreference>,
    val files: Map<String, String>,
)

internal suspend fun Stage12CalculationStore.digest(): Stage13Digest {
    val database = store.backups.capture()
    val files = File(runContext.filesDir, "schedule_photos").walkTopDown().filter { it.isFile }
        .associate { it.relativeTo(runContext.filesDir).path to sha256(it) }
    return Stage13Digest(database, portablePreferences.capture(database), files)
}

internal fun sha256(file: File): String = sha256(file.readBytes())

internal fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** PNG sintético de un solo color (sin metadatos). */
internal fun writePng(target: File, width: Int, height: Int, color: Int): File {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        bitmap.eraseColor(color)
        FileOutputStream(target).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    } finally {
        bitmap.recycle()
    }
    return target
}

/** JPEG sintético apaisado con la orientación EXIF pedida (p. ej. ROTATE_90: se ve vertical). */
internal fun writeJpegWithOrientation(target: File, width: Int, height: Int, color: Int, orientation: Int): File {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        bitmap.eraseColor(color)
        FileOutputStream(target).use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
    } finally {
        bitmap.recycle()
    }
    ExifInterface(target.path).apply {
        setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        saveAttributes()
    }
    check(ExifInterface(target.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0) == orientation)
    return target
}

/** Jornada visualmente igual a la diurna de la fixture (misma fecha, horario y objetivo), con otro UUID. */
internal fun twinOfDayShift(id: UUID): V2ShiftWrite = F.dayShift.copy(
    shift = F.dayShift.shift.copy(id = id),
    snapshot = F.dayShift.snapshot.copy(shiftId = id),
)

/** Jornada distinta que se superpone exactamente con la nocturna de la fixture. */
internal fun overlapOfNightShift(id: UUID): V2ShiftWrite = F.nightShift.copy(
    shift = F.nightShift.shift.copy(id = id),
    snapshot = F.nightShift.snapshot.copy(shiftId = id),
)

/** Agrega a la nota canaria de la jornada diurna una imagen privada real (archivo + fila). */
internal suspend fun Stage12CalculationStore.seedNoteImage(color: Int = Color.MAGENTA): ShiftNoteAttachment {
    val attachmentId = UUID.nameUUIDFromBytes("stage13/note-image".toByteArray())
    val key = "note_$attachmentId.png"
    val file = photoStore.file(key)
    check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
    writePng(file, 6, 4, color)
    val note: ShiftNote = checkNotNull(store.shiftNotes.getById(F.SHIFT_NOTE_ID))
    val attachment = ShiftNoteAttachment(attachmentId, note.id, key, "image/png", file.length(), 6, 4, 0,
        F.CREATED_AT, F.CREATED_AT)
    store.shiftNotes.saveWithAttachments(note, listOf(attachment))
    check(store.shiftNotes.getWithAttachments(note.id)?.attachments == listOf(attachment)) { "STAGE13_NOTE_IMAGE" }
    return attachment
}

/** Copia preparada por el coordinador de la corrida (el mismo que usa la pantalla), guardada en [target]. */
internal suspend fun Stage12CalculationStore.writeBackup(target: File, password: String?, includePhotos: Boolean = true) {
    check(!target.exists())
    backupCoordinator.prepareBackup(BackupCreateOptions(includePhotos, password)).use { it.file.copyTo(target) }
}

internal suspend fun Stage12CalculationStore.monthPhotos(): List<SchedulePhoto> =
    store.schedulePhotos.observeForMonth(F.MONTH).first()

/** Busca bytes de [secret] en cualquier archivo de la corrida y en sus preferencias (no debe aparecer). */
internal fun Stage12CalculationStore.filesContaining(secret: String, realDataDir: File): List<String> {
    val needle = secret.toByteArray(Charsets.UTF_8)
    val runPreferences = File(realDataDir, "shared_prefs").listFiles().orEmpty()
        .filter { it.name.startsWith("stage13-") } + File(realDataDir, "databases").listFiles().orEmpty()
        .filter { it.name.startsWith("stage13-") }
    return (privateRoot.walkTopDown().filter { it.isFile }.toList() + runPreferences)
        .filter { file -> file.readBytes().let { bytes -> indexOf(bytes, needle) >= 0 } }
        .map { it.name }
}

private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
    if (needle.isEmpty() || haystack.size < needle.size) return -1
    outer@ for (i in 0..haystack.size - needle.size) {
        for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
        return i
    }
    return -1
}
