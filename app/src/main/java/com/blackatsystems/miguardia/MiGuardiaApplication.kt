package com.blackatsystems.miguardia

import android.app.Application
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.notifications.NotificationPreferencesStore
import com.blackatsystems.miguardia.notifications.NotificationRuntime
import com.blackatsystems.miguardia.ui.summary.SummaryPreferencesStore
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import com.blackatsystems.miguardia.weather.WeatherRuntime
import com.blackatsystems.miguardia.widget.WidgetPreferencesStore
import com.blackatsystems.miguardia.widget.WidgetRuntime

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
    val summaryPreferences: SummaryPreferencesStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SummaryPreferencesStore(this)
    }
    val widgetPreferences: WidgetPreferencesStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WidgetPreferencesStore(this)
    }
    val weatherRuntime: WeatherRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WeatherRuntime(this, weatherPreferences)
    }
    val notificationRuntime: NotificationRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NotificationRuntime(this, localDataStore, notificationPreferences, weatherRuntime)
    }
    val widgetRuntime: WidgetRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WidgetRuntime(
            context = this,
            localDataStore = { localDataStore },
            preferences = widgetPreferences,
            weatherRuntime = weatherRuntime,
        )
    }

    override fun onCreate() {
        super.onCreate()
        notificationRuntime.start()
        widgetRuntime.start()
    }
}
