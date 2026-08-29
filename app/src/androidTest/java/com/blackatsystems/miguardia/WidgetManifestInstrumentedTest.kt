package com.blackatsystems.miguardia

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.widget.NextEventAppWidgetProvider
import com.blackatsystems.miguardia.widget.WidgetConfigurationActivity
import com.blackatsystems.miguardia.widget.WidgetConfigurationChangeReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetManifestInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    @Suppress("DEPRECATION")
    fun providerAndConfigurationActivityHaveTheMinimumExpectedExposure() {
        val packageManager = context.packageManager
        val providerComponent = ComponentName(context, NextEventAppWidgetProvider::class.java)
        val provider = packageManager.getReceiverInfo(
            providerComponent,
            PackageManager.GET_META_DATA,
        )
        val configuration = packageManager.getActivityInfo(
            ComponentName(context, WidgetConfigurationActivity::class.java),
            0,
        )

        assertFalse(provider.exported)
        assertEquals(R.xml.widget_next_event_info, provider.metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER))
        assertTrue(configuration.exported)
        assertEquals("Widget de inicio", configuration.loadLabel(packageManager).toString())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val widgetInfo = AppWidgetManager.getInstance(context).installedProviders.single { info ->
                info.provider == providerComponent
            }
            assertEquals(3, widgetInfo.targetCellWidth)
            assertEquals(2, widgetInfo.targetCellHeight)
        }
    }

    @Test
    fun configurationChangeUsesTheRuntimeReceiverAndNotTheManifest() {
        val manifestReceivers = context.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_CONFIGURATION_CHANGED).setPackage(context.packageName),
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        assertTrue(
            manifestReceivers.none {
                it.activityInfo.name == NextEventAppWidgetProvider::class.java.name
            },
        )

        var refreshes = 0
        val receiver = WidgetConfigurationChangeReceiver { refreshes++ }
        receiver.onReceive(context, Intent("com.blackatsystems.miguardia.UNRELATED"))
        receiver.onReceive(context, Intent(Intent.ACTION_CONFIGURATION_CHANGED))

        assertEquals(1, refreshes)
    }
}
