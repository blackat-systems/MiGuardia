package com.blackatsystems.miguardia

import android.app.Application
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.notifications.NotificationPreferencesStore
import com.blackatsystems.miguardia.notifications.NotificationRuntime
import com.blackatsystems.miguardia.profile.GuardProfileStore
import com.blackatsystems.miguardia.reports.LocalReportGenerator
import com.blackatsystems.miguardia.reports.ReportArtifactStore
import com.blackatsystems.miguardia.reports.ReportPhotoStager
import com.blackatsystems.miguardia.core.domain.AppDefaults
import com.blackatsystems.miguardia.ui.summary.SummaryPreferencesStore
import com.blackatsystems.miguardia.weather.WeatherPreferencesStore
import com.blackatsystems.miguardia.weather.WeatherRuntime
import com.blackatsystems.miguardia.widget.WidgetPreferencesStore
import com.blackatsystems.miguardia.widget.WidgetRuntime
import java.time.Clock

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
    val guardProfile: GuardProfileStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GuardProfileStore(this)
    }
    val reportArtifactStore: ReportArtifactStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ReportArtifactStore(this)
    }
    val reportGenerator: LocalReportGenerator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LocalReportGenerator(
            snapshots = localDataStore.monthlyReportSnapshots,
            profiles = guardProfile,
            photoStager = ReportPhotoStager(this),
            artifactStore = reportArtifactStore,
            clock = Clock.systemUTC(),
            zoneId = AppDefaults.zoneId(),
        )
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
