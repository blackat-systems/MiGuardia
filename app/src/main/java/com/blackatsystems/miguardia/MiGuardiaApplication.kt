package com.blackatsystems.miguardia

import android.app.Application
import com.blackatsystems.miguardia.core.database.LocalDataStore
import com.blackatsystems.miguardia.notifications.NotificationPreferencesStore
import com.blackatsystems.miguardia.notifications.NotificationRuntime

class MiGuardiaApplication : Application() {
    val localDataStore: LocalDataStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LocalDataStore.create(this)
    }
    val notificationPreferences: NotificationPreferencesStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NotificationPreferencesStore(this)
    }
    val notificationRuntime: NotificationRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NotificationRuntime(this, localDataStore, notificationPreferences)
    }

    override fun onCreate() {
        super.onCreate()
        notificationRuntime.start()
    }
}
