package com.blackatsystems.miguardia.stage12

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.widget.WidgetConfigurationActivity
import java.time.LocalDate
import java.util.UUID

/** Own virtual rendering identities only. Token existence is never alarm scheduling/delivery proof. */
internal class Stage12OwnedWidgetIntents(context: Context, namespace: UUID,
    shifts: Set<UUID>, dates: Set<LocalDate>) : AutoCloseable {
    private val context = context.applicationContext
    val widgetId: Int = -((namespace.hashCode() and 0x3fffffff) + 1)
    private data class Identity(val code: Int, val intent: Intent)
    private val identities: List<Identity>
    private val created = linkedMapOf<Int, PendingIntent>()
    init {
        check(context.packageName == "com.blackatsystems.miguardia.qa")
        fun uri(action: String, target: String) = Uri.Builder().scheme("miguardia").authority("widget-action")
            .appendPath(widgetId.toString()).appendPath(action.substringAfterLast('.'))
            .appendQueryParameter("target", target).build()
        fun activity(action: String, target: String) = Identity("$widgetId|$action|$target".hashCode(),
            Intent(context, MainActivity::class.java).setAction(action).setData(uri(action, target))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        identities = buildList {
            add(activity(MainActivity.ACTION_OPEN_CALENDAR, "calendar"))
            add(activity(MainActivity.ACTION_OPEN_WEATHER, "weather"))
            shifts.sortedBy(UUID::toString).forEach { add(activity(MainActivity.ACTION_VIEW_SHIFT, it.toString())) }
            dates.sorted().forEach { add(activity(MainActivity.ACTION_VIEW_DATE, it.toString())) }
            add(Identity("configure|$widgetId".hashCode(), Intent(context, WidgetConfigurationActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setData(uri("configure", widgetId.toString()))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)))
        }
        check(identities.none { it.code == 29_082_026 }) { "OWN_WIDGET_RESERVED_REQUEST_CODE" }
        check(identities.map { it.code }.distinct().size == identities.size) { "OWN_WIDGET_REQUEST_CODE_COLLISION" }
        check(identities.all { existing(it) == null }) { "OWN_WIDGET_IDENTITY_ALREADY_EXISTS" }
    }
    private fun existing(identity: Identity): PendingIntent? = PendingIntent.getActivity(context, identity.code,
        identity.intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
    fun retainCreated() {
        identities.forEachIndexed { index, identity -> existing(identity)?.let { token ->
            check(token.creatorPackage == context.packageName && token.creatorUid == context.applicationInfo.uid && token.isImmutable) {
                "OWN_WIDGET_TOKEN_OWNER_MISMATCH"
            }
            val previous = created[index]
            check(previous == null || previous == token) { "OWN_WIDGET_TOKEN_REPLACED" }
            created[index] = token
        } }
    }
    /** Observes the already retained original capability; never constructs or sends a replacement. */
    fun requireRetainedDate(date: LocalDate) {
        val index = identities.indices.single { index ->
            identities[index].intent.action == MainActivity.ACTION_VIEW_DATE &&
                identities[index].intent.data?.getQueryParameter("target") == date.toString()
        }
        val original = checkNotNull(created[index]) { "OWN_WIDGET_DATE_TOKEN_NOT_RETAINED" }
        check(existing(identities[index]) == original) { "OWN_WIDGET_DATE_TOKEN_REPLACED" }
    }
    override fun close() {
        var primary: Throwable? = null
        created.forEach { (index, token) ->
            try {
                check(existing(identities[index]) == token) { "OWN_WIDGET_TOKEN_DISAPPEARED_OR_REPLACED" }
                token.cancel()
                check(existing(identities[index]) == null) { "OWN_WIDGET_TOKEN_NOT_CLOSED" }
            } catch (error: Throwable) { primary = Stage12W010Oracle.retain(primary, error) }
        }
        primary?.let { throw it }
    }
}
