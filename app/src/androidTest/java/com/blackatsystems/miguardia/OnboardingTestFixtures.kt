package com.blackatsystems.miguardia

import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.ui.help.OnboardingPreferencesStore
import kotlinx.coroutines.runBlocking

internal fun preparePublicRootAndMarkOnboardingCompletedForTest() = runBlocking {
    val application = InstrumentationRegistry.getInstrumentation()
        .targetContext
        .applicationContext as MiGuardiaApplication
    com.blackatsystems.miguardia.beta.PublicQaRootFixture.awaitReady(application)
    application.onboardingPreferences.completeAtLeast(OnboardingPreferencesStore.CURRENT_VERSION)
}

internal fun preparePublicRootAndResetOnboardingForTest() = runBlocking {
    val application = InstrumentationRegistry.getInstrumentation()
        .targetContext
        .applicationContext as MiGuardiaApplication
    com.blackatsystems.miguardia.beta.PublicQaRootFixture.awaitReady(application)
    application.onboardingPreferences.resetForInstrumentation()
}
