package com.blackatsystems.miguardia.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.time.Instant

interface WidgetBoundaryScheduler {
    fun schedule(triggerAt: Instant)
    fun cancel()
}

class AndroidWidgetBoundaryScheduler(
    context: Context,
) : WidgetBoundaryScheduler {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)

    override fun schedule(triggerAt: Instant) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt.toEpochMilli(),
            requireNotNull(boundaryIntent(PendingIntent.FLAG_UPDATE_CURRENT)),
        )
    }

    override fun cancel() {
        boundaryIntent(PendingIntent.FLAG_NO_CREATE)?.let { pendingIntent ->
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    internal fun existingPendingIntent(): PendingIntent? = boundaryIntent(PendingIntent.FLAG_NO_CREATE)

    private fun boundaryIntent(mode: Int): PendingIntent? {
        val intent = Intent(applicationContext, NextEventAppWidgetProvider::class.java)
            .setAction(NextEventAppWidgetProvider.ACTION_REFRESH_BOUNDARY)
            .setData(
                Uri.Builder()
                    .scheme("miguardia")
                    .authority("widget-boundary")
                    .appendPath("next")
                    .build(),
            )
        return PendingIntent.getBroadcast(
            applicationContext,
            REQUEST_CODE,
            intent,
            mode or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_CODE = 29_082_026
    }
}
