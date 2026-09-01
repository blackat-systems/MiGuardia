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
        if (!canUseData(context)) return
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
        if (!canUseData(context)) return
        val widgetRuntime = runtime(context)
        launchReceiverWork(widgetRuntime, intArrayOf(appWidgetId)) {
            widgetRuntime.refreshNow(intArrayOf(appWidgetId))
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val application = application(context) ?: return
        if (runCatching { application.widgetDeferredActions.enqueueDeletion(appWidgetIds) }.isFailure) return
        if (!application.startupRecoveryGate.isReady) return
        val widgetRuntime = application.widgetRuntime
        if (widgetRuntime.isPausedForRestore) return
        launchReceiverWork(widgetRuntime, appWidgetIds) {
            application.widgetDeferredActions.replay(widgetRuntime)
        }
    }

    override fun onEnabled(context: Context) {
        if (!canUseData(context)) return
        runtime(context).start()
    }

    override fun onDisabled(context: Context) {
        if (!canUseData(context)) return
        runtime(context).disabled()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_RESTORED) {
            val oldIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS) ?: intArrayOf()
            val newIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: intArrayOf()
            val application = application(context) ?: return
            if (runCatching {
                    application.widgetDeferredActions.enqueueRestoration(oldIds, newIds)
                }.isFailure
            ) return
            if (!application.startupRecoveryGate.isReady) return
            val widgetRuntime = application.widgetRuntime
            if (widgetRuntime.isPausedForRestore) return
            launchReceiverWork(widgetRuntime, newIds) {
                application.widgetDeferredActions.replay(widgetRuntime)
            }
            return
        }
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_DELETED) {
            super.onReceive(context, intent)
            return
        }
        if (!canUseData(context)) return
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

    private fun application(context: Context): MiGuardiaApplication? =
        context.applicationContext as? MiGuardiaApplication

    private fun canUseData(context: Context): Boolean =
        (context.applicationContext as? MiGuardiaApplication)?.startupRecoveryGate?.isReady == true

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
