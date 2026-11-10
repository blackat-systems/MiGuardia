package com.blackatsystems.miguardia.stage12

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import com.blackatsystems.miguardia.weather.WeatherRuntime
import com.blackatsystems.miguardia.ui.weather.WeatherViewModel
import java.io.File
import java.time.Clock
import kotlinx.coroutines.*

/** One real DataStore and WeatherRuntime, bound to this owner's private filesystem. */
internal class Stage12HumanWeatherOwner(context: Stage12B1HumanPrivateContext,
    owner: ComponentActivity, store: LocalDataStore, clock: Clock, key: String) {
    private val preferenceJob = SupervisorJob()
    private val scope = CoroutineScope(preferenceJob + Dispatchers.IO)
    val file = File(context.filesDir, "datastore/weather.preferences_pb")
    val preferences: WeatherPreferencesStore
    val runtime: WeatherRuntime
    val model: WeatherViewModel
    var closed = false; private set
    init {
        val policy = Stage12PathPolicy(context.binding.privateRoot)
        check(policy.resolve("files/datastore/weather.preferences_pb") == file.absoluteFile)
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdir())
        if (!file.exists()) check(file.createNewFile())
        check(file.isFile && !java.nio.file.Files.isSymbolicLink(file.toPath()))
        preferences = WeatherPreferencesStore(file, scope)
        runtime = WeatherRuntime(context, preferences, store.objectives, clock)
        model = ViewModelProvider(owner, WeatherViewModel.Factory(runtime, store.shifts, store.vacations, store.medicalLeaves))
            .get(key, WeatherViewModel::class.java)
    }
    fun cancelRequests() = runtime.cancelRefresh()
    suspend fun closeAfterModels() {
        check(!closed)
        withTimeout(30_000) { preferenceJob.cancelAndJoin() }
        check(preferenceJob.isCompleted); closed = true
    }
}
