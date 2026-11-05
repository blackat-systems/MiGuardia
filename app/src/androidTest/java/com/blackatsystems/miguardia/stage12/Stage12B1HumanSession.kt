package com.blackatsystems.miguardia.stage12

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.blackatsystems.miguardia.QaExplicitHumanActionContext
import com.blackatsystems.miguardia.QaExplicitHumanActionSession
import com.blackatsystems.miguardia.QaInstrumentationScope
import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Six finite human actions. No coordinates, provider text, addresses or returned URIs in projection. */
internal class Stage12B1HumanSession(override val runId: String, private val runner: Stage12NormalQaRunner,
    private val retain: (Throwable) -> Unit) : QaExplicitHumanActionSession {
    enum class HumanStep { MAP_AND_RETURN, ADDRESS_FOR_WEATHER, APPROXIMATE_CITY, PICK_OWN_IMAGE, CANCEL_SECOND_PICKER, WEATHER_AFTER_LOCATION }
    data class LocationCall(val objective: UUID, val latitude: Double, val longitude: Double,
        val before: Instant, var returned: Boolean? = null, var after: Instant? = null)
    companion object {
        const val SELECTOR = Stage12B1HumanContract.SELECTOR
        const val SLOT = Stage12B1HumanContract.SLOT
        const val ADDRESS = "Plaza del Congreso, Ciudad Autónoma de Buenos Aires, Argentina"
        const val ADDRESS_OBJECTIVE = "Objetivo QA Dirección"
        const val CITY_OBJECTIVE = "Objetivo QA Ciudad"
        const val MAX_STEP_MILLIS = 180_000L
    }
    private var activity: ComponentActivity? = null
    override val ownerInstance: Activity get() = checkNotNull(activity)
    private var root: Stage12RecurringPaths? = null
    private var activeStep: HumanStep? = null
    private var stepStarted = 0L
    private var nextOrdinal = 0
    private var failed: Throwable? = null
    private val allowedContexts = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<QaExplicitHumanActionContext, Boolean>())
    private val counts = mutableMapOf<String, Int>()
    private var pauseCount = 0; private var resumeCount = 0
    private var stepPause = 0; private var stepResume = 0
    private var mapReturned = 0
    private var mapCandidate = 0
    private var mapActivityEntries = 0
    private var admittedMapIntent: Intent? = null
    private var weatherConsentEntered = false
    private var expectedPicker: Intent? = null
    private var pickerRequest: Int? = null
    private var pickerReturned = false
    private var resultObserved = false; private var resultForwarded = false
    private var imageValidator: ((Uri) -> Unit)? = null
    private var permissionBefore: Pair<Boolean, Boolean>? = null
    private var addressId: UUID? = null; private var cityId: UUID? = null
    private val calls = mutableListOf<LocationCall>()
    private val a042 = JSONObject().put("schema","stage12-a042-approximate-guard-v1")
        .put("status","NOT_STARTED").put("phase","NONE")
        .put("permission_gate",JSONObject.NULL).put("capture_gate",JSONObject.NULL)
        .put("request",JSONObject.NULL).put("callback",JSONObject.NULL)
        .put("request_count",0).put("callback_count",0).put("callback_admitted",false)
        .put("callback_after_failure",false).put("first_code",JSONObject.NULL).put("failure_step_count",0)
    val projection = JSONObject().put("schema", "stage12-b1-human-session-v1").put("steps", JSONArray())
        .put("capability_entries", JSONArray()).put("owner_closures", 0).put("coordinates_recorded", false)
        .put("external_provider_closure_proven", false).put("a042_guard",a042)
    private val observer = LifecycleEventObserver { _, event -> synchronized(this) {
        if (event == Lifecycle.Event.ON_PAUSE) pauseCount++
        if (event == Lifecycle.Event.ON_RESUME) resumeCount++
    } }
    private fun reject(code: String): Nothing {
        val original = failed ?: IllegalStateException(code).also { failed = it; retain(it) }
        throw original
    }
    @Synchronized override fun retainFailure(error: Throwable) { if (failed == null) failed = error; retain(error) }
    @Synchronized fun requireActive() {
        failed?.let { throw it }
        if (activeStep != null && SystemClock.elapsedRealtime() - stepStarted > MAX_STEP_MILLIS) reject("HUMAN_STEP_DEADLINE")
    }
    @Synchronized fun requireWaitingStep() { requireActive(); check(activeStep != null) }
    @Synchronized fun configureObjectives(address: UUID, city: UUID) {
        check(address != city && addressId == null && cityId == null); addressId = address; cityId = city
    }
    @Synchronized fun attach(owner: ComponentActivity, binding: Stage12RecurringPaths) {
        requireActive(); check(activity == null && activeStep == null && binding.run == runId)
        activity = owner; root = binding; owner.lifecycle.addObserver(observer)
        runner.registerB1HumanSession(owner,this)
    }
    @Synchronized fun contextCreated(value: QaExplicitHumanActionContext, binding: Stage12RecurringPaths) {
        requireActive(); check(value.explicitHumanSession === this && value.humanOwnerInstance === activity && root === binding)
        allowedContexts += value
    }
    @Synchronized override fun requireAction(context: QaExplicitHumanActionContext, operation: String) {
        requireActive()
        if (context !in allowedContexts || context.humanOwnerInstance !== activity) reject("HUMAN_CONTEXT_FOREIGN")
        val step = activeStep ?: reject("HUMAN_OPERATION_WITHOUT_ACTIVE_STEP")
        val permitted = when (step) {
            HumanStep.MAP_AND_RETURN -> setOf("maps.launch")
            HumanStep.ADDRESS_FOR_WEATHER -> setOf("location.geocoder")
            HumanStep.APPROXIMATE_CITY -> setOf("location.permission", "location.capture")
            HumanStep.PICK_OWN_IMAGE, HumanStep.CANCEL_SECOND_PICKER -> emptySet()
            HumanStep.WEATHER_AFTER_LOCATION -> setOf("weather.network")
        }
        if (operation !in permitted) reject("HUMAN_OPERATION_NOT_ADMITTED")
        if (step == HumanStep.APPROXIMATE_CITY && operation in setOf("location.permission","location.capture")) {
            a042.put("phase",if(operation=="location.permission") "PERMISSION" else "CAPTURE")
            val grants=permissionState(context as Context)
            a042.put(if(operation=="location.permission") "permission_gate" else "capture_gate",grantProof(grants))
            if(grants.second) rejectA042("A042_FINE_PERMISSION_GRANTED")
            if(operation=="location.capture" && !grants.first) rejectA042("A042_COARSE_PERMISSION_MISSING")
        }
        if (operation == "weather.network" && !weatherConsentEntered) reject("HUMAN_WEATHER_WITHOUT_CONSENT")
        val count = (counts[operation] ?: 0) + 1
        if (count > when(operation) { "maps.launch" -> 3; "weather.network" -> 16; else -> 1 }) reject("HUMAN_OPERATION_REPEATED")
        counts[operation] = count
        projection.getJSONArray("capability_entries").put(JSONObject().put("step", step.name).put("operation", operation)
            .put("ordinal", count).put("guard_passed", true))
    }
    @Synchronized fun begin(step: HumanStep) {
        requireActive(); check(activeStep == null && step.ordinal == nextOrdinal && activity != null)
        check(checkNotNull(activity).lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        activeStep = step; stepStarted = SystemClock.elapsedRealtime(); stepPause = pauseCount; stepResume = resumeCount
        counts.clear(); mapReturned = 0; mapCandidate = 0; mapActivityEntries = 0; admittedMapIntent = null
        pickerRequest = null; pickerReturned = false; resultObserved = false; resultForwarded = false
        expectedPicker = if (step in setOf(HumanStep.PICK_OWN_IMAGE, HumanStep.CANCEL_SECOND_PICKER))
            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia().createIntent(checkNotNull(activity),
                androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) else null
        if (step == HumanStep.APPROXIMATE_CITY) {
            permissionBefore = permissionState(checkNotNull(activity));a042.put("status","NOT_REQUESTED")
        }
        projection.getJSONArray("steps").put(JSONObject().put("step", step.name).put("status", "ENTERED"))
        runner.publishB1HumanStep(step.name, step.ordinal + 1)
    }
    @Synchronized fun requireMapIntent(context: QaExplicitHumanActionContext, intent: Intent): Int {
        QaInstrumentationScope.requireExternalAction(context as Context, "maps.launch")
        val encoded = Uri.encode(ADDRESS)
        val candidates = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).setPackage("com.google.android.apps.maps"),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded")))
        if (mapCandidate >= candidates.size || !intent.filterEquals(candidates[mapCandidate]) ||
            intent.flags != 0 || intent.extras != null || intent.clipData != null || intent.selector != null)
            reject("HUMAN_MAP_INTENT_FOREIGN")
        admittedMapIntent = Intent(intent)
        return mapCandidate++
    }
    @Synchronized fun mapReturned(candidate: Int, error: Throwable?) {
        requireActive(); check(activeStep == HumanStep.MAP_AND_RETURN && candidate == mapCandidate - 1)
        if (error == null) mapReturned++ else if (error !is ActivityNotFoundException) reject("HUMAN_MAP_LAUNCH_FAILED")
    }

    @Synchronized override fun requireMapLaunch(owner: Activity, intent: Intent, requestCode: Int) {
        requireActive(); check(activeStep == HumanStep.MAP_AND_RETURN && owner === activity && requestCode == -1)
        val admitted = checkNotNull(admittedMapIntent)
        check(mapCandidate == mapActivityEntries+1 && intent.filterEquals(admitted) && intent.flags == admitted.flags &&
            intent.extras == null && intent.clipData == null && intent.selector == null)
        mapActivityEntries++
    }
    @Synchronized override fun requirePickerLaunch(owner: Activity, intent: Intent, requestCode: Int) {
        requireActive(); check(owner === activity && activeStep in setOf(HumanStep.PICK_OWN_IMAGE, HumanStep.CANCEL_SECOND_PICKER))
        val expected = checkNotNull(expectedPicker)
        check(pickerRequest == null && requestCode >= 0 && intent.action == android.provider.MediaStore.ACTION_PICK_IMAGES &&
            intent.type == "image/*" && intent.filterEquals(expected) && intent.flags == expected.flags &&
            intent.clipData == null && intent.selector == null)
        val left = intent.extras; val right = expected.extras
        check(left?.keySet().orEmpty() == right?.keySet().orEmpty())
        for (key in left?.keySet().orEmpty()) {
            @Suppress("DEPRECATION") val a = left?.get(key)
            @Suppress("DEPRECATION") val b = right?.get(key)
            check((a is Int || a is Long || a is Boolean || a is String) && a == b)
        }
        pickerRequest = requestCode; counts["image.pick"] = 1
        projection.getJSONArray("capability_entries").put(JSONObject().put("step", checkNotNull(activeStep).name)
            .put("operation", "image.pick").put("ordinal", 1).put("guard_passed", true))
    }
    @Synchronized fun bindImage(validator: (Uri) -> Unit) { check(imageValidator == null && activeStep == null); imageValidator = validator }
    @Synchronized override fun pickerResult(owner: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        requireActive(); check(owner === activity && pickerRequest == requestCode && pickerReturned && !resultObserved)
        when(activeStep) {
            HumanStep.PICK_OWN_IMAGE -> {
                check(resultCode == Activity.RESULT_OK)
                val uri = checkNotNull(data?.data)
                data.clipData?.let { check(it.itemCount == 1 && it.getItemAt(0).uri == uri) }
                checkNotNull(imageValidator).invoke(uri)
            }
            HumanStep.CANCEL_SECOND_PICKER -> check(resultCode == Activity.RESULT_CANCELED && data?.data == null && data?.clipData == null)
            else -> reject("HUMAN_PICKER_RETURN_OUTSIDE_STEP")
        }
        resultObserved = true
        projection.getJSONArray("steps").getJSONObject(checkNotNull(activeStep).ordinal)
            .put("result_observed", true).put("result_kind", if(resultCode==Activity.RESULT_OK) "OWN_URI" else "CANCELLED")
    }
    @Synchronized override fun pickerResultForwarded(owner: Activity, requestCode: Int) {
        requireActive(); check(owner === activity && pickerRequest == requestCode && resultObserved && !resultForwarded)
        resultForwarded = true
        projection.getJSONArray("steps").getJSONObject(checkNotNull(activeStep).ordinal).put("result_forwarded", true)
    }
    @Synchronized override fun pickerLaunchReturned(owner: Activity, requestCode: Int, error: Throwable?) {
        requireActive(); check(owner === activity && pickerRequest == requestCode && !pickerReturned)
        if (error != null) { retainFailure(error); throw error }
        pickerReturned = true
    }

    @Synchronized fun mapReturnObserved(): Boolean = mapReturned == 1 && pauseCount > stepPause && resumeCount > stepResume &&
        activity?.lifecycle?.currentState == Lifecycle.State.RESUMED
    @Synchronized fun pickerReturnObserved(): Boolean = pickerReturned && resultObserved && resultForwarded && counts["image.pick"] == 1 && pauseCount > stepPause && resumeCount > stepResume &&
        activity?.lifecycle?.currentState == Lifecycle.State.RESUMED
    @Synchronized fun pickerSequenceObserved(): Boolean {
        val steps = projection.getJSONArray("steps")
        return listOf(HumanStep.PICK_OWN_IMAGE,HumanStep.CANCEL_SECOND_PICKER).all { step ->
            val item = steps.optJSONObject(step.ordinal)
            item?.optString("status") == "RETURNED" && item.optBoolean("result_observed") && item.optBoolean("result_forwarded")
        }
    }
    @Synchronized fun operationCount(operation: String): Int = counts[operation] ?: 0
    @Synchronized fun weatherConsent() { requireActive(); check(activeStep == HumanStep.WEATHER_AFTER_LOCATION && !weatherConsentEntered); weatherConsentEntered = true; projection.put("weather_enable_callback_entered", true) }
    private fun grantProof(value:Pair<Boolean,Boolean>)=JSONObject().put("coarse",value.first).put("fine",value.second)
    private fun rejectA042(code:String):Nothing {
        if(a042.isNull("first_code"))a042.put("first_code",code).put("failure_step_count",projection.getJSONArray("steps").length())
        a042.put("status","BLOCKED");reject(code)
    }
    @Synchronized override fun approximateLocationRequest(context:QaExplicitHumanActionContext,request:()->Unit) {
        requireActive()
        check(context in allowedContexts && context.humanOwnerInstance === activity && activeStep == HumanStep.APPROXIMATE_CITY)
        a042.put("phase","REQUEST")
        if(a042.getInt("request_count")!=0)rejectA042("A042_REQUEST_REPEATED")
        check(counts["location.capture"]==1)
        // Last grant observation before the one real SDK call; no location is read here.
        val grants=permissionState(context as Context);a042.put("request",grantProof(grants))
        if(grants.second)rejectA042("A042_FINE_PERMISSION_GRANTED")
        if(!grants.first)rejectA042("A042_COARSE_PERMISSION_MISSING")
        a042.put("request_count",1).put("status","REQUESTED")
        request()
    }
    @Synchronized override fun approximateLocationResult(context:QaExplicitHumanActionContext) {
        failed?.let{a042.put("callback_after_failure",true);throw it}
        requireActive();a042.put("phase","CALLBACK")
        val seen=a042.getInt("callback_count");a042.put("callback_count",minOf(2,seen+1))
        if(seen!=0)rejectA042("A042_CALLBACK_REPEATED")
        if(a042.getInt("request_count")!=1)rejectA042("A042_CALLBACK_WITHOUT_REQUEST")
        if(context !in allowedContexts || context.humanOwnerInstance !== activity)rejectA042("A042_CALLBACK_OWNER_CLOSED")
        if(activeStep!=HumanStep.APPROXIMATE_CITY)rejectA042("A042_CALLBACK_OUTSIDE_STEP")
        // The callback calls this BEFORE Location.latitude/longitude or any persistence.
        val grants=permissionState(context as Context);a042.put("callback",grantProof(grants))
        if(grants.second)rejectA042("A042_FINE_PERMISSION_GRANTED")
        if(!grants.first)rejectA042("A042_COARSE_PERMISSION_MISSING")
        a042.put("callback_admitted",true).put("status","CALLBACK_ALLOWED")
    }
    @Synchronized fun locationCallCount() = calls.size
    @Synchronized fun locationCalls(): List<LocationCall> = calls.map { it.copy() }
    fun location(objective: UUID, latitude: Double, longitude: Double, delegate: () -> Boolean): Boolean {
        val call = synchronized(this) {
            requireActive()
            val expected = when (activeStep) { HumanStep.ADDRESS_FOR_WEATHER -> addressId; HumanStep.APPROXIMATE_CITY -> cityId; else -> null }
            if (expected == null || objective != expected || !latitude.isFinite() || !longitude.isFinite() ||
                latitude !in -90.0..90.0 || longitude !in -180.0..180.0) reject("HUMAN_LOCATION_TARGET_OR_VALUE")
            if(activeStep==HumanStep.APPROXIMATE_CITY)check(a042.getBoolean("callback_admitted") && a042.getInt("request_count")==1)
            check(calls.none { it.objective == objective })
            LocationCall(objective, latitude, longitude, Instant.now()).also { calls += it }
        }
        return try { delegate().also { synchronized(this) { call.returned = it; call.after = Instant.now() } } }
        catch (error: Throwable) { retain(error); synchronized(this) { if (failed == null) failed = error }; throw error }
    }
    @Synchronized fun complete(outcome: String, measured: JSONObject = JSONObject()) {
        requireActive(); val step = checkNotNull(activeStep)
        val allowed = when(step) {
            HumanStep.MAP_AND_RETURN -> setOf("RETURNED_SAME_CONTEXT")
            HumanStep.ADDRESS_FOR_WEATHER -> setOf("SAVED_FOR_OBJECTIVE", "NO_RESULT_OR_SERVICE_FAILURE")
            HumanStep.APPROXIMATE_CITY -> setOf("SAVED_FOR_OBJECTIVE", "PERMISSION_DENIED", "LOCATION_UNAVAILABLE")
            HumanStep.PICK_OWN_IMAGE -> setOf("OWN_IMAGE_SELECTED")
            HumanStep.CANCEL_SECOND_PICKER -> setOf("PENDING_IMAGE_PRESERVED")
            HumanStep.WEATHER_AFTER_LOCATION -> setOf("PROVIDER_RESPONSE", "PROVIDER_ERROR", "LOCATION_UNAVAILABLE_NOT_RUN")
        }
        check(outcome in allowed)
        if(step == HumanStep.APPROXIMATE_CITY) {
            val before = checkNotNull(permissionBefore); val after = permissionState(checkNotNull(activity))
            measured.put("coarse_before", before.first).put("fine_before", before.second)
                .put("coarse_after", after.first).put("fine_after", after.second)
                .put("permission_gate_entries", counts["location.permission"] ?: 0)
                .put("capture_gate_entries", counts["location.capture"] ?: 0)
                .put("permission_dialog_proven_by_host", false)
        }
        val item = projection.getJSONArray("steps").getJSONObject(step.ordinal)
        item.put("status", "RETURNED").put("outcome", outcome).put("measured", measured)
            .put("owner_pause_delta", pauseCount-stepPause).put("owner_resume_delta", resumeCount-stepResume)
        activeStep = null; nextOrdinal++
    }
    @Synchronized fun beforeClose() { if (failed != null) activeStep = null }
    @Synchronized fun detached(owner: ComponentActivity, ownClosure: Boolean) {
        check(activity === owner && owner.isDestroyed && ownClosure)
        runner.releaseB1HumanSession(owner,this,ownClosure)
        owner.lifecycle.removeObserver(observer); allowedContexts.clear(); activity = null; root = null
        projection.put("owner_closures", projection.getInt("owner_closures")+1)
    }
    private fun permissionState(context: Context) =
        (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) to
        (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
}
