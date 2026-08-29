package com.blackatsystems.miguardia.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.blackatsystems.miguardia.MiGuardiaApplication
import kotlinx.coroutines.launch

class NextEventAppWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        runtime(context).let { widgetRuntime ->
            widgetRuntime.showLoading(appWidgetIds)
            launchReceiverWork(widgetRuntime, appWidgetIds) {
                widgetRuntime.refreshNow(appWidgetIds)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        val widgetRuntime = runtime(context)
        launchReceiverWork(widgetRuntime, intArrayOf(appWidgetId)) {
            widgetRuntime.refreshNow(intArrayOf(appWidgetId))
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val widgetRuntime = runtime(context)
        launchReceiverWork(widgetRuntime, appWidgetIds) {
            widgetRuntime.deleteNow(appWidgetIds)
        }
    }

    override fun onEnabled(context: Context) {
        runtime(context).start()
    }

    override fun onDisabled(context: Context) {
        runtime(context).disabled()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_RESTORED) {
            val oldIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS) ?: intArrayOf()
            val newIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: intArrayOf()
            val widgetRuntime = runtime(context)
            widgetRuntime.registerRestoration(newIds)
            launchReceiverWork(widgetRuntime, newIds) {
                widgetRuntime.restoreNow(oldIds, newIds)
            }
            return
        }
        if (intent.action in RefreshActions) {
            val widgetRuntime = runtime(context)
            launchReceiverWork(widgetRuntime, widgetRuntimeIds(context)) {
                widgetRuntime.refreshNow()
            }
            return
        }
        super.onReceive(context, intent)
    }

    private fun launchReceiverWork(
        widgetRuntime: WidgetRuntime,
        affectedIds: IntArray,
        block: suspend () -> Unit,
    ) {
        val pendingResult = goAsync()
        widgetRuntime.scope.launch {
            val success = runWidgetReceiverWork(
                finish = pendingResult::finish,
                block = block,
            )
            if (!success) widgetRuntime.receiverFailed(affectedIds)
        }
    }

    private fun runtime(context: Context): WidgetRuntime =
        (context.applicationContext as MiGuardiaApplication).widgetRuntime

    private fun widgetRuntimeIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(
            android.content.ComponentName(context, NextEventAppWidgetProvider::class.java),
        )

    companion object {
        const val ACTION_REFRESH_BOUNDARY = "com.blackatsystems.miguardia.action.WIDGET_REFRESH_BOUNDARY"
        private val RefreshActions = setOf(
            ACTION_REFRESH_BOUNDARY,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
