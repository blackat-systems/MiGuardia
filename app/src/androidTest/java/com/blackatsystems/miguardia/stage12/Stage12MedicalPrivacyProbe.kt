package com.blackatsystems.miguardia.stage12

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.core.domain.model.MedicalLeave
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventIdentity
import com.blackatsystems.miguardia.core.domain.nextevent.projectNextEvent
import com.blackatsystems.miguardia.core.domain.report.ReportFormat
import com.blackatsystems.miguardia.core.domain.report.ReportNoteKind
import com.blackatsystems.miguardia.core.domain.widget.*
import com.blackatsystems.miguardia.profile.GuardProfileStore
import com.blackatsystems.miguardia.reports.*
import com.blackatsystems.miguardia.ui.nextevent.V2WorkEventSourceObserver
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.widget.WidgetRemoteViewsRenderer
import com.blackatsystems.miguardia.widget.resolveWidgetPalette
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/** Real Room consumers and real output surfaces. No installed-widget publication or external export. */
internal class Stage12MedicalPrivacyProbe(private val dataRoot: File, private val clock: Clock, private val zone: ZoneId) {
    private var profileJob: Job? = null
    private var modelJob: Job? = null
    private var tokens: Stage12OwnedWidgetIntents? = null
    private var initialized = false
    private lateinit var fileContext: Context
    lateinit var reports: ReportsViewModel
        private set
    private var widgetView: View? = null
    private var reportsVisible by mutableStateOf(false)
    var closed = false
        private set
    val artifacts = linkedMapOf<String, Stage12FileSnapshot>()

    fun initialize(activity: ComponentActivity, store: LocalDataStore, key: String) {
        check(!initialized && profileJob == null && dataRoot.isDirectory)
        fileContext = object : ContextWrapper(activity) {
            override fun getFilesDir() = dataRoot
            override fun getApplicationContext(): Context = this
        }
        val job = SupervisorJob(); profileJob = job
        val profileFile = File(dataRoot, "c071-profile.preferences_pb")
        check(!profileFile.exists())
        val profiles = GuardProfileStore(profileFile, CoroutineScope(job + Dispatchers.IO))
        val generator = LocalReportGenerator(store.monthlyReportSnapshots, profiles,
            ReportPhotoStager(fileContext), ReportArtifactStore(fileContext, clock), clock = clock, zoneId = zone)
        reports = ViewModelProvider(activity, ReportsViewModel.Factory(generator, ReportDestinationWriter(fileContext.contentResolver),
            store.schedulePhotos, profiles, clock, zone)).get(key, ReportsViewModel::class.java)
        modelJob = checkNotNull(reports.viewModelScope.coroutineContext[Job])
        initialized = true
    }

    suspend fun projectWidgetFromRoom(store: LocalDataStore, leave: MedicalLeave, expectedShift: UUID,
        namespace: UUID, install: ((() -> Unit) -> Unit)): JSONObject {
        check(initialized && tokens == null)
        val now = leave.startDate.atStartOfDay(zone).toInstant()
        val source = withTimeout(30_000) { V2WorkEventSourceObserver(store.v2Shifts, store.availabilityWindows,
            store.shiftActuals, store.independentExtraWork, store.explicitDayStatuses, store.vacations,
            store.medicalLeaves, store.workConfiguration).observe(leave.startDate).first() }
        check(source.medicalLeaves.single { it.id == leave.id } == leave)
        val canary = checkNotNull(leave.privateNote)
        check(canary.isNotEmpty())
        val projection = projectWidget(projectNextEvent(now, zone, source.toInput()),
            WidgetProjectionConfig(WidgetMode.NEXT_SHIFT, WidgetPrivacy.COMPLETE, WidgetSize.EXPANDED, true))
        check(projection.state == WidgetContentState.EVENTS && projection.events.isNotEmpty())
        check(projection.events.first().identity == NextEventIdentity.Shift(expectedShift))
        check(projection.events.first().details?.placeName?.isNotBlank() == true)
        val shifts = projection.events.mapNotNull { (it.identity as? NextEventIdentity.Shift)?.shiftId }.toSet()
        val dates = projection.events.map { it.ownerLocalDate }.toSet()
        install {
            val ledger = Stage12OwnedWidgetIntents(fileContext, namespace, shifts, dates); tokens = ledger
            try {
                val views = WidgetRemoteViewsRenderer(fileContext).createViews(projection, ledger.widgetId, null,
                    resolveWidgetPalette(AppThemeMode.DARK, false))
                widgetView = views.apply(fileContext, FrameLayout(fileContext))
            } finally { ledger.retainCreated() }
        }
        return JSONObject().put("stage", "ROOM_TO_WIDGET_PROJECTED").put("medical_id", leave.id.toString())
            .put("note_sha256", sha(canary.toByteArray())).put("positive_shift_id", expectedShift.toString())
            .put("source_contains_note", true).put("events", projection.events.size)
            .put("reference_instant", now.toString()).put("installed_widget", false)
    }

    @Composable fun Content() {
        CompositionLocalProvider(LocalContext provides fileContext) {
            if (reportsVisible) {
                val state by reports.uiState.collectAsState()
                ReportsSurfaceHost(state, ReportsActions.from(reports).copy(
                    requestSave = { error("C071_EXTERNAL_EXPORT_NOT_ADMITTED") },
                    requestShare = { error("C071_EXTERNAL_EXPORT_NOT_ADMITTED") }))
            } else AndroidView(factory = { checkNotNull(widgetView) }, modifier = Modifier.fillMaxWidth().height(360.dp))
        }
    }

    /** UI-thread observation of the attached real RemoteViews, including hidden cached text and accessibility labels. */
    fun requireWidgetPrivate(leave: MedicalLeave, expectedPlace: String): JSONObject {
        val root = checkNotNull(widgetView)
        check(root.isAttachedToWindow && root.isShown && root.getGlobalVisibleRect(Rect()))
        val primary = checkNotNull(root.findViewById<TextView>(R.id.widget_primary))
        val schedule = checkNotNull(root.findViewById<TextView>(R.id.widget_schedule))
        check(primary.isShown && primary.getGlobalVisibleRect(Rect()) && primary.text.contains(expectedPlace))
        check(schedule.isShown && schedule.getGlobalVisibleRect(Rect()) && schedule.text.isNotBlank())
        val canary = checkNotNull(leave.privateNote)
        var nodes = 0; var texts = 0
        fun visit(view: View) {
            nodes++
            check(view.contentDescription?.contains(canary) != true)
            if (view is TextView) { texts++; check(!view.text.contains(canary)) }
            if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
        }
        visit(root)
        check(nodes > 1 && texts >= 2)
        return JSONObject().put("stage", "WIDGET_APPLIED_PRIVATE").put("nodes", nodes).put("text_nodes", texts)
            .put("positive_visible", true).put("note_absent", true).put("content_descriptions_absent", true)
    }

    fun openReports(month: YearMonth) { reports.open(month); reportsVisible = true }
    fun requireReportArtifact(included: Boolean, canary: String): JSONObject {
        val state = reports.uiState.value
        check(state.stage == ReportsStage.READY && state.format == ReportFormat.XLSX)
        check(state.privacy.includeMedicalNotes == included && !state.medicalConfirmationPending)
        val generated = checkNotNull(state.generated)
        check(generated.projection.workRows.isNotEmpty())
        check(generated.projection.privateInclusions.medicalNotesIncluded == included)
        val noteRows = generated.projection.notes.filter { it.kind == ReportNoteKind.MEDICAL_LEAVE }
        if (included) check(noteRows.any { it.body == canary }) else check(noteRows.isEmpty())
        val artifact = generated.artifact
        val file = artifact.file.canonicalFile
        check(file.parentFile == File(dataRoot, "reports/artifacts").canonicalFile && file.length() == artifact.byteSize)
        check(file.length() in 1L..16_777_216L)
        var contains = false; var count = 0; var bytes = 0L
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            check(entries.size in 1..256 && entries.map { it.name }.distinct().size == entries.size)
            entries.filter { !it.isDirectory && it.name.endsWith(".xml") }.forEach { entry ->
                check(entry.size in 0L..16_777_216L)
                val body = zip.getInputStream(entry).use { input -> input.readBytes() }
                bytes += body.size; check(bytes <= 16_777_216)
                contains = contains || body.toString(Charsets.UTF_8).contains(canary); count++
            }
        }
        check(count > 0 && contains == included)
        val digest = sha(file.readBytes())
        val relative = "files/reports/artifacts/" + file.name
        check(relative !in artifacts)
        artifacts[relative] = Stage12FileSnapshot(file.length(), digest)
        return JSONObject().put("stage", if (included) "REPORT_CONSENT_INCLUDED" else "REPORT_DEFAULT_EXCLUDED")
            .put("format", "XLSX").put("artifact_sha256", digest).put("bytes", file.length())
            .put("xml_entries", count).put("note_present", included).put("positive_work_rows", generated.projection.workRows.size)
    }

    suspend fun closeAfterOwnerDestroyed() {
        // Composition and the real owner have already ended; no native View timer remains attached.
        check(widgetView == null || !checkNotNull(widgetView).isAttachedToWindow)
        var primary: Throwable? = null
        try { tokens?.let { it.retainCreated(); it.close() } } catch (error: Throwable) { primary = error }
        profileJob?.cancel()
        try { withTimeout(30_000) { modelJob?.join(); profileJob?.join() } }
        catch (error: Throwable) { if (primary == null) primary = error else checkNotNull(primary).addSuppressed(error) }
        primary?.let { throw it }
        check(initialized && modelJob?.isCompleted == true && profileJob?.isCompleted == true)
        closed = true
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
