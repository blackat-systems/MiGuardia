package com.blackatsystems.miguardia

import android.content.Context
import androidx.compose.runtime.CompositionContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.ui.availability.AvailabilityViewModel
import com.blackatsystems.miguardia.ui.calendar.CalendarViewModel
import com.blackatsystems.miguardia.ui.exceptions.ExceptionsViewModel
import com.blackatsystems.miguardia.ui.help.HelpViewModel
import com.blackatsystems.miguardia.ui.hours.HoursAndExtrasViewModel
import com.blackatsystems.miguardia.ui.management.V2ManualShiftLoadViewModel
import com.blackatsystems.miguardia.ui.management.V2RecurringPlanViewModel
import com.blackatsystems.miguardia.ui.management.V2ShiftActualViewModel
import com.blackatsystems.miguardia.ui.management.V2ShiftEditViewModel
import com.blackatsystems.miguardia.ui.nextevent.NextEventViewModel
import com.blackatsystems.miguardia.ui.notifications.NotificationViewModel
import com.blackatsystems.miguardia.ui.photos.PhotosViewModel
import com.blackatsystems.miguardia.ui.summary.SummaryViewModel
import com.blackatsystems.miguardia.ui.vacation.VacationViewModel
import com.blackatsystems.miguardia.ui.weather.WeatherViewModel
import com.blackatsystems.miguardia.ui.worksetup.WorkSetupViewModel
import com.blackatsystems.miguardia.reports.ReportsViewModel
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import java.util.IdentityHashMap
import java.util.UUID
import kotlinx.coroutines.Job

/** A private file Context can only reject external effects; it cannot install a business graph. */
internal interface QaMainActivityFileContext {
    fun rejectFixtureExternalEffect(operation: String): Nothing
}

/** One real owned composition; a completed probe child is not proof that all effects stopped. */
internal interface QaMainActivityComposition {
    fun parent(activity: MainActivity): CompositionContext
    fun contentInstalled(activity: MainActivity)
    fun ownerDestroyed(activity: MainActivity)
    val jobs: List<Job>
    val closureProven: Boolean
}

/** Concrete, complete business dependencies for one admitted QA receiver; never an Application override. */
internal class QaMainActivityGraph(
    run: String,
    owner: Any,
    generation: UUID,
    val store: LocalDataStore,
    val filesContext: Context,
    val capturedThemeMode: AppThemeMode,
    val factories: Factories,
    val composition: QaMainActivityComposition,
    private val wrap: (Context) -> Context,
    private val observe: (Event) -> Unit,
    retain: (Throwable) -> Unit,
    private val incoming: (MainActivity, IncomingDestination) -> Unit,
    private val published: (MainActivity) -> Unit,
) {
    data class IncomingDestination(
        val action: String?, val componentPackage: String?, val componentClass: String?,
        val dataUri: String?, val flags: Int, val ownerDatePresent: Boolean, val ownerDate: String?,
        val shiftIdPresent: Boolean, val extraKeys: Set<String>,
    )
    data class Factories(
        val calendar: CalendarViewModel.Factory,
        val nextEvent: NextEventViewModel.Factory,
        val manual: V2ManualShiftLoadViewModel.Factory,
        val edit: V2ShiftEditViewModel.Factory,
        val actual: V2ShiftActualViewModel.Factory,
        val recurring: V2RecurringPlanViewModel.Factory,
        val work: WorkSetupViewModel.Factory,
        val hours: HoursAndExtrasViewModel.Factory,
        val availability: AvailabilityViewModel.Factory,
        val summary: SummaryViewModel.Factory,
        val reports: ReportsViewModel.Factory,
        val photos: PhotosViewModel.Factory,
        val vacation: VacationViewModel.Factory,
        val exceptions: ExceptionsViewModel.Factory,
        val weather: WeatherViewModel.Factory,
        val notifications: NotificationViewModel.Factory,
        val help: HelpViewModel.Factory,
    )
    enum class Event { PUBLISHED, WRAPPED, BEFORE_CREATE, STORE_WITNESS, FACTORY_CREATED, NEW_INTENT,
        NOTIFICATION_REFRESH_EXCLUDED, DESTROYED }
    val lease = QaMainActivityLease(run, owner, generation, this, retain)
    val pendingDestinations = PendingMainDestinationCoordinator()
    private val models = IdentityHashMap<ViewModel, Job?>()
    private val unreturnedModels = java.util.Collections.newSetFromMap(IdentityHashMap<ViewModel, Boolean>())
    private val savedStates = IdentityHashMap<ViewModel, SavedStateHandle>()
    private val compositionJobs = java.util.Collections.newSetFromMap(IdentityHashMap<Job, Boolean>())
    @Volatile private var constructorFailureUnproven = false
    @Volatile private var destroyedStoreCleared = false
    private class Witness(val graph: QaMainActivityGraph) : ViewModel()

    fun event(event: Event) {
        try { observe(event) } catch (failure: Throwable) { lease.reject(failure) }
    }
    /** Retain the original receiver before attach can fail; this callback grants no business authority. */
    fun publishedReceiver(activity: MainActivity) {
        try { published(activity); event(Event.PUBLISHED) } catch (failure: Throwable) { lease.reject(failure) }
    }
    fun incomingDestination(activity: MainActivity, snapshot: IncomingDestination) {
        QaInstrumentationScope.requireMainActivityGraph(activity, this)
        requireStoreWitness(activity)
        try { incoming(activity, snapshot) } catch (failure: Throwable) { lease.reject(failure) }
    }

    fun wrapActivity(base: Context): Context {
        try {
            val result = wrap(base)
            if (result.packageName != base.packageName || result.applicationContext !== filesContext ||
                result.filesDir.canonicalFile != filesContext.filesDir.canonicalFile ||
                result.cacheDir.canonicalFile != filesContext.cacheDir.canonicalFile)
                lease.reject(IllegalStateException("QA_MAIN_FILE_CONTEXT_FOREIGN"))
            event(Event.WRAPPED)
            return result
        } catch (failure: Throwable) { lease.reject(failure) }
    }

    /** Called before super.onCreate, before FragmentManager installs its own framework ViewModel. */
    @android.annotation.SuppressLint("RestrictedApi")
    fun installStoreWitness(activity: MainActivity) {
        val store = activity.viewModelStore
        val old = store[WITNESS]
        if (old == null) {
            if (store.keys().isNotEmpty())
                lease.reject(IllegalStateException("QA_MAIN_PREEXISTING_VIEWMODEL_STORE"))
            store.put(WITNESS, Witness(this))
        }
        requireStoreWitness(activity)
        event(Event.STORE_WITNESS)
    }

    @android.annotation.SuppressLint("RestrictedApi")
    fun requireStoreWitness(activity: MainActivity) {
        val witness = activity.viewModelStore[WITNESS] as? Witness
        lease.requireStoreWitness(activity, witness?.graph)
    }

    fun guardedFactory(activity: MainActivity, factory: ViewModelProvider.Factory): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                var constructorEntered = false
                var constructorReturned = false
                try {
                    QaInstrumentationScope.requireMainActivityGraph(activity, this@QaMainActivityGraph)
                    requireStoreWitness(activity)
                    // The product factory requests this same cached handle with these exact CreationExtras.
                    val savedState = if (modelClass == AvailabilityViewModel::class.java) extras.createSavedStateHandle() else null
                    constructorEntered = true
                    val model = factory.create(modelClass, extras)
                    constructorReturned = true
                    synchronized(models) {
                        models[model] = null
                        unreturnedModels += model
                        if (savedState != null) savedStates[model] = savedState
                    }
                    val job = checkNotNull(model.viewModelScope.coroutineContext[Job])
                    synchronized(models) { models[model] = job }
                    event(Event.FACTORY_CREATED)
                    synchronized(models) { unreturnedModels.remove(model) }
                    return model
                } catch (failure: Throwable) {
                    if (constructorEntered && !constructorReturned) constructorFailureUnproven = true
                    lease.reject(failure)
                }
            }
        }

    fun compositionParent(activity: MainActivity): CompositionContext {
        QaInstrumentationScope.requireMainActivityGraph(activity, this)
        requireStoreWitness(activity)
        return try { composition.parent(activity) } catch (failure: Throwable) { lease.reject(failure) }
    }
    fun contentInstalled(activity: MainActivity) {
        QaInstrumentationScope.requireMainActivityGraph(activity, this)
        try { composition.contentInstalled(activity) } catch (failure: Throwable) { lease.reject(failure) }
    }

    fun <T : ViewModel> existingModel(activity: MainActivity, type: Class<T>): T {
        QaInstrumentationScope.requireMainActivityGraph(activity, this)
        requireStoreWitness(activity)
        val found = synchronized(models) { models.keys.filter { type.isInstance(it) } }
        if (found.size != 1) lease.reject(IllegalStateException("QA_MAIN_EXISTING_MODEL_REQUIRED"))
        return type.cast(found.single())
    }

    /** Both owned and ordinary Activity-scoped models must finish before the owned store closes. */
    fun activityJobs(): List<Job> = synchronized(models) { models.values.filterNotNull() + compositionJobs + composition.jobs }

    fun observeCompositionJob(activity: MainActivity, job: Job) {
        synchronized(models) { compositionJobs += job }
        QaInstrumentationScope.requireMainActivityGraph(activity, this)
    }

    /** Read-only observation of the real framework handle; no reflection or restored-state replacement. */
    fun availabilitySavedState(activity: MainActivity, model: AvailabilityViewModel): SavedStateHandle {
        QaInstrumentationScope.requireMainActivityGraph(activity, this)
        requireStoreWitness(activity)
        return synchronized(models) {
            savedStates[model] ?: lease.reject(IllegalStateException("QA_MAIN_AVAILABILITY_HANDLE_NOT_CAPTURED"))
        }
    }

    @android.annotation.SuppressLint("RestrictedApi")
    fun observeDestroyedStore(activity: MainActivity) {
        try {
            if (activity.viewModelStore.keys().isNotEmpty())
                lease.reject(IllegalStateException("QA_MAIN_DESTROYED_STORE_NOT_CLEARED"))
            destroyedStoreCleared = true
            composition.ownerDestroyed(activity)
        } catch (failure: Throwable) { lease.reject(failure) }
    }

    /** An unreturned model or throwing constructor requires HOLD; cancellation alone is not resource closure. */
    fun modelClosureProven(): Boolean = synchronized(models) {
        destroyedStoreCleared && composition.closureProven && !constructorFailureUnproven && unreturnedModels.isEmpty() &&
            models.values.all { it != null && it.isCompleted } && compositionJobs.all { it.isCompleted }
    }

    fun forgetCompletedModels() = synchronized(models) {
        if (!modelClosureProven()) lease.reject(IllegalStateException("QA_MAIN_MODEL_CLOSURE_UNPROVEN"))
        models.clear()
        savedStates.clear()
        compositionJobs.clear()
    }

    fun rejectExternal(operation: String): Nothing = lease.reject(SecurityException("QA_MAIN_EXTERNAL_EFFECT_EXCLUDED:$operation"))

    companion object {
        private const val WITNESS = "com.blackatsystems.miguardia.QaMainActivityGraph.Witness"
    }
}
