package com.blackatsystems.miguardia

import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.ui.help.OnboardingPreferencesStore
import kotlinx.coroutines.runBlocking

internal fun markOnboardingCompletedForTest() = runBlocking {
    val application = InstrumentationRegistry.getInstrumentation()
        .targetContext
        .applicationContext as MiGuardiaApplication
    application.onboardingPreferences.completeAtLeast(OnboardingPreferencesStore.CURRENT_VERSION)
}

internal fun resetOnboardingForTest() = runBlocking {
    val application = InstrumentationRegistry.getInstrumentation()
        .targetContext
        .applicationContext as MiGuardiaApplication
    application.onboardingPreferences.resetForInstrumentation()
}
