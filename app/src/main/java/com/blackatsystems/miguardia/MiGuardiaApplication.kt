package com.blackatsystems.miguardia

import android.app.Application
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.notifications.NotificationPreferencesStore
import com.blackatsystems.miguardia.notifications.NotificationRuntime
import com.blackatsystems.miguardia.profile.GuardProfileStore
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import com.blackatsystems.miguardia.weather.WeatherRuntime

class MiGuardiaApplication : Application() {
    val localDataStore: LocalDataStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LocalDataStore.create(this)
    }
    val notificationPreferences: NotificationPreferencesStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NotificationPreferencesStore(this)
    }
    val weatherPreferences: WeatherPreferencesStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WeatherPreferencesStore(this)
    }
    val weatherRuntime: WeatherRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WeatherRuntime(this, weatherPreferences)
    }
    val guardProfile: GuardProfileStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GuardProfileStore(this)
    }
    val notificationRuntime: NotificationRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NotificationRuntime(this, localDataStore, notificationPreferences, weatherRuntime)
    }

    override fun onCreate() {
        super.onCreate()
        notificationRuntime.start()
    }
}
