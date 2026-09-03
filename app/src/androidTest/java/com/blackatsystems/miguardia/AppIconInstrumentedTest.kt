package com.blackatsystems.miguardia

import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIconInstrumentedTest {
    @Test
    fun launcherAndRoundIconsResolveOnEverySupportedApi() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageManager = context.packageManager
        val applicationInfo = packageManager.getApplicationInfo(context.packageName, 0)
        val resources = packageManager.getResourcesForApplication(applicationInfo)

        assertNotEquals(0, applicationInfo.icon)
        val roundIconRes = resources.getIdentifier(
            "ic_launcher_round",
            "mipmap",
            context.packageName,
        )
        assertNotEquals(0, roundIconRes)

        val launcherIcon = resources.getDrawable(applicationInfo.icon, context.theme)
        val roundIcon = resources.getDrawable(roundIconRes, context.theme)

        assertNotNull(launcherIcon)
        assertNotNull(roundIcon)
        assertTrue(launcherIcon is AdaptiveIconDrawable)
        assertTrue(roundIcon is AdaptiveIconDrawable)
    }
}
