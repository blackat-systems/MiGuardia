package com.blackatsystems.miguardia.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.core.domain.nextevent.NextEventResult
import com.blackatsystems.miguardia.core.domain.widget.WidgetEventKind
import com.blackatsystems.miguardia.core.domain.widget.WidgetNavigation
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.core.domain.widget.WidgetProjection
import com.blackatsystems.miguardia.core.domain.widget.WidgetProjectionConfig
import com.blackatsystems.miguardia.core.domain.widget.WidgetSize
import com.blackatsystems.miguardia.core.domain.widget.projectWidget
import com.blackatsystems.miguardia.core.domain.widget.widgetChronometerBase
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.weather.WeatherCredits

private const val COMPACT_WIDTH_DP = 180f
private const val COMPACT_HEIGHT_DP = 96f
private const val EXPANDED_WIDTH_DP = 250f
private const val EXPANDED_HEIGHT_DP = 360f

/**
 * Lo que el motor del Widget necesita de quien dibuja: saber qué instancias hay y publicarles una vista.
 * Producción usa [WidgetRemoteViewsRenderer]; las pruebas pueden observar el motor sin un Widget instalado.
 */
interface WidgetSurface {
    fun installedIds(): IntArray
    fun isExpandedLayout(appWidgetId: Int): Boolean
    fun renderRecovery(appWidgetIds: IntArray? = null)
    fun renderLoading(appWidgetIds: IntArray)
    fun renderRecoverableError(appWidgetIds: IntArray)
    fun render(
        appWidgetId: Int,
        result: NextEventResult,
        preferences: WidgetInstancePreferences,
        weatherText: String?,
    )
}

class WidgetRemoteViewsRenderer(
    private val context: Context,
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(context),
) : WidgetSurface {
    override fun installedIds(): IntArray {
        com.blackatsystems.miguardia.QaInstrumentationScope.requireExternalAction(context, "widget.installed-query")
        return manager.getAppWidgetIds(providerComponent())
    }

    override fun renderRecovery(appWidgetIds: IntArray?) {
        if (com.blackatsystems.miguardia.QaInstrumentationScope.suppress(context, "widget.recovery")) return
        (appWidgetIds ?: installedIds()).filterValidIds().forEach { id ->
            manager.updateAppWidget(id, recoveryViews(id))
        }
    }

    internal fun recoveryViews(id: Int): RemoteViews =
        statusViews(id, "MIGUARDIA", "Abrí MiGuardia para terminar la recuperación de tus datos.")

    override fun renderLoading(appWidgetIds: IntArray) {
        if (com.blackatsystems.miguardia.QaInstrumentationScope.suppress(context, "widget.render-loading")) return
        appWidgetIds.filterValidIds().forEach { id ->
            manager.updateAppWidget(id, statusViews(id, "CARGANDO", "Actualizando el próximo evento…"))
        }
    }

    override fun renderRecoverableError(appWidgetIds: IntArray) {
        if (com.blackatsystems.miguardia.QaInstrumentationScope.suppress(context, "widget.render-error")) return
        appWidgetIds.filterValidIds().forEach { id ->
            manager.updateAppWidget(
                id,
                statusViews(id, "MIGUARDIA", "No pudimos actualizar. Tocá para abrir y reintentar."),
            )
        }
    }

    override fun render(
        appWidgetId: Int,
        result: NextEventResult,
        preferences: WidgetInstancePreferences,
        weatherText: String?,
    ) {
        com.blackatsystems.miguardia.QaInstrumentationScope.requireExternalAction(context, "widget.render")
        if (appWidgetId <= 0) return
        if (!com.blackatsystems.miguardia.localAllowsPublication(context)) {
            renderRecovery(intArrayOf(appWidgetId))
            return
        }
        val compact = projectWidget(
            result,
            WidgetProjectionConfig(
                mode = preferences.mode,
                privacy = preferences.privacy,
                size = WidgetSize.COMPACT,
                configured = preferences.configured,
            ),
        )
        val expanded = projectWidget(
            result,
            WidgetProjectionConfig(
                mode = preferences.mode,
                privacy = preferences.privacy,
                size = WidgetSize.EXPANDED,
                configured = preferences.configured,
            ),
        )
        val palette = currentPalette()
        val compactViews = createViews(compact, appWidgetId, null, palette)
        val expandedWeather = weatherText.takeIf {
            preferences.includeWeather &&
                preferences.privacy == WidgetPrivacy.COMPLETE &&
                expanded.events.firstOrNull()?.details?.kind == WidgetEventKind.SHIFT
        }
        val expandedViews = createViews(expanded, appWidgetId, expandedWeather, palette)
        val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RemoteViews(
                mapOf(
                    SizeF(COMPACT_WIDTH_DP, COMPACT_HEIGHT_DP) to compactViews,
                    SizeF(EXPANDED_WIDTH_DP, EXPANDED_HEIGHT_DP) to expandedViews,
                ),
            )
        } else if (isExpandedLayout(appWidgetId)) {
            expandedViews
        } else {
            compactViews
        }
        if (com.blackatsystems.miguardia.localAllowsPublication(context)) {
            manager.updateAppWidget(appWidgetId, views)
        } else renderRecovery(intArrayOf(appWidgetId))
    }

    internal fun navigationPendingIntentForTest(
        appWidgetId: Int,
        navigation: WidgetNavigation,
    ): PendingIntent = navigationIntent(appWidgetId, navigation)

    internal fun configurationPendingIntentForTest(appWidgetId: Int): PendingIntent =
        configurationIntent(appWidgetId)

    internal fun weatherPendingIntentForTest(appWidgetId: Int): PendingIntent =
        weatherIntent(appWidgetId)

    internal fun createViews(
        projection: WidgetProjection,
        appWidgetId: Int,
        weatherText: String?,
        palette: WidgetPalette = currentPalette(),
        elapsedRealtime: Long = SystemClock.elapsedRealtime(),
    ): RemoteViews {
        val expanded = projection.size == WidgetSize.EXPANDED
        val layout = if (expanded) R.layout.widget_next_event_expanded else R.layout.widget_next_event_compact
        val text = buildWidgetRenderText(projection)
        return RemoteViews(context.packageName, layout).apply {
            setInt(
                R.id.widget_root,
                "setBackgroundResource",
                if (palette.background == DARK_BACKGROUND) {
                    R.drawable.widget_background_dark
                } else {
                    R.drawable.widget_background_light
                },
            )
            val accent = projection.events.firstOrNull()?.details?.colorArgb ?: palette.defaultAccent
            setInt(R.id.widget_accent, "setBackgroundColor", accent)
            setTextViewText(R.id.widget_title, text.title)
            setTextViewText(R.id.widget_primary, text.primary)
            setTextColor(R.id.widget_title, palette.secondaryText)
            setTextColor(R.id.widget_primary, palette.primaryText)
            setOptionalText(R.id.widget_schedule, text.schedule, palette.secondaryText)
            configureCountdown(projection, text, elapsedRealtime, palette.action)
            setOptionalText(R.id.widget_simultaneous, text.simultaneous, palette.action)
            setOnClickPendingIntent(R.id.widget_root, navigationIntent(appWidgetId, projection.navigation))
            setContentDescription(
                R.id.widget_root,
                listOfNotNull(text.title, text.primary, text.schedule, text.simultaneous).joinToString(". "),
            )
            if (expanded) {
                configureRows(projection, text.rows, appWidgetId, palette)
                val renderedWeather = weatherText
                    ?.takeIf {
                        projection.privacy == WidgetPrivacy.COMPLETE &&
                            projection.events.firstOrNull()?.details?.kind == WidgetEventKind.SHIFT
                    }
                    ?.takeIf(String::isNotBlank)
                setOptionalText(R.id.widget_weather, renderedWeather, palette.secondaryText)
                val credit = WeatherCredits.forDisplayedWeather(renderedWeather)
                setOptionalText(R.id.widget_weather_credit, credit, palette.action)
                setViewVisibility(R.id.widget_weather_area, if (credit != null) View.VISIBLE else View.GONE)
                setOnClickPendingIntent(R.id.widget_weather_area, null)
                setContentDescription(R.id.widget_weather_area, null)
                if (renderedWeather != null) {
                    setOnClickPendingIntent(R.id.widget_weather_area, weatherIntent(appWidgetId))
                    setContentDescription(R.id.widget_weather_area, "$renderedWeather. $credit. ${WeatherCredits.OPEN}.")
                }
                setTextColor(R.id.widget_configure, palette.action)
                setOnClickPendingIntent(R.id.widget_configure, configurationIntent(appWidgetId))
                setContentDescription(R.id.widget_configure, "Reconfigurar este Widget de inicio")
            }
        }
    }

    private fun RemoteViews.configureRows(
        projection: WidgetProjection,
        rows: List<String>,
        appWidgetId: Int,
        palette: WidgetPalette,
    ) {
        RowIds.forEachIndexed { index, viewId ->
            val row = rows.drop(1).getOrNull(index)
            setOptionalText(viewId, row, palette.primaryText)
            setOnClickPendingIntent(viewId, null)
            setContentDescription(viewId, null)
            val event = projection.events.drop(1).getOrNull(index)
            if (row != null && event != null) {
                setOnClickPendingIntent(viewId, navigationIntent(appWidgetId, event.navigation))
                setContentDescription(viewId, "$row. Abrir día en MiGuardia.")
            }
        }
    }

    private fun RemoteViews.configureCountdown(
        projection: WidgetProjection,
        text: WidgetRenderText,
        elapsedRealtime: Long,
        color: Int,
    ) {
        val countdown = projection.countdown
        val format = text.countdownFormat
        val base = countdown?.let {
            widgetChronometerBase(elapsedRealtime, projection.referenceInstant, it.target)
        }
        val visible = base != null && format != null
        setViewVisibility(R.id.widget_countdown, if (visible) View.VISIBLE else View.GONE)
        setTextColor(R.id.widget_countdown, color)
        setContentDescription(R.id.widget_countdown, null)
        if (visible) {
            setChronometer(R.id.widget_countdown, requireNotNull(base), format, true)
            setChronometerCountDown(R.id.widget_countdown, true)
        } else {
            setChronometer(R.id.widget_countdown, 0L, null, false)
            setChronometerCountDown(R.id.widget_countdown, false)
            setTextViewText(R.id.widget_countdown, "")
        }
    }

    private fun RemoteViews.setOptionalText(viewId: Int, value: String?, color: Int) {
        setViewVisibility(viewId, if (value == null) View.GONE else View.VISIBLE)
        setTextViewText(viewId, value ?: "")
        setTextColor(viewId, color)
        setContentDescription(viewId, null)
    }

    private fun statusViews(appWidgetId: Int, title: String, primary: String): RemoteViews {
        val palette = currentPalette()
        return RemoteViews(context.packageName, R.layout.widget_next_event_compact).apply {
            setInt(
                R.id.widget_root,
                "setBackgroundResource",
                if (palette.background == DARK_BACKGROUND) R.drawable.widget_background_dark
                else R.drawable.widget_background_light,
            )
            setInt(R.id.widget_accent, "setBackgroundColor", palette.defaultAccent)
            setTextViewText(R.id.widget_title, title)
            setTextViewText(R.id.widget_primary, primary)
            setTextColor(R.id.widget_title, palette.secondaryText)
            setTextColor(R.id.widget_primary, palette.primaryText)
            setOptionalText(R.id.widget_schedule, null, palette.secondaryText)
            setOptionalText(R.id.widget_simultaneous, null, palette.action)
            setViewVisibility(R.id.widget_countdown, View.GONE)
            setChronometer(R.id.widget_countdown, 0L, null, false)
            setChronometerCountDown(R.id.widget_countdown, false)
            setTextViewText(R.id.widget_countdown, "")
            setTextColor(R.id.widget_countdown, palette.action)
            setContentDescription(R.id.widget_countdown, null)
            setOnClickPendingIntent(R.id.widget_root, calendarIntent(appWidgetId))
            setContentDescription(R.id.widget_root, "$title. $primary")
        }
    }

    private fun navigationIntent(appWidgetId: Int, navigation: WidgetNavigation): PendingIntent =
        when (navigation) {
            is WidgetNavigation.Shift -> activityIntent(
                appWidgetId = appWidgetId,
                action = MainActivity.ACTION_VIEW_SHIFT,
                discriminator = navigation.shiftId.toString(),
                extras = { intent ->
                    intent.putExtra(MainActivity.EXTRA_SHIFT_ID, navigation.shiftId.toString())
                },
            )
            is WidgetNavigation.Date -> activityIntent(
                appWidgetId = appWidgetId,
                action = MainActivity.ACTION_VIEW_DATE,
                discriminator = navigation.ownerLocalDate.toString(),
                extras = { intent ->
                    intent.putExtra(MainActivity.EXTRA_OWNER_LOCAL_DATE, navigation.ownerLocalDate.toString())
                },
            )
            WidgetNavigation.Calendar -> calendarIntent(appWidgetId)
            WidgetNavigation.Configure -> configurationIntent(appWidgetId)
        }

    private fun calendarIntent(appWidgetId: Int): PendingIntent = activityIntent(
        appWidgetId = appWidgetId,
        action = MainActivity.ACTION_OPEN_CALENDAR,
        discriminator = "calendar",
    )

    private fun weatherIntent(appWidgetId: Int): PendingIntent = activityIntent(
        appWidgetId = appWidgetId,
        action = MainActivity.ACTION_OPEN_WEATHER,
        discriminator = "weather",
    )

    private fun activityIntent(
        appWidgetId: Int,
        action: String,
        discriminator: String,
        extras: (Intent) -> Unit = {},
    ): PendingIntent {
        com.blackatsystems.miguardia.QaInstrumentationScope.requireExternalAction(context, "widget.activity-identity")
        val intent = Intent(context, MainActivity::class.java)
            .setAction(action)
            .setData(widgetUri(appWidgetId, action, discriminator))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        extras(intent)
        return PendingIntent.getActivity(
            context,
            "$appWidgetId|$action|$discriminator".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun configurationIntent(appWidgetId: Int): PendingIntent {
        com.blackatsystems.miguardia.QaInstrumentationScope.requireExternalAction(context, "widget.configuration-identity")
        val intent = Intent(context, WidgetConfigurationActivity::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .setData(widgetUri(appWidgetId, "configure", appWidgetId.toString()))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        return PendingIntent.getActivity(
            context,
            "configure|$appWidgetId".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun widgetUri(appWidgetId: Int, action: String, discriminator: String): Uri = Uri.Builder()
        .scheme("miguardia")
        .authority("widget-action")
        .appendPath(appWidgetId.toString())
        .appendPath(action.substringAfterLast('.'))
        .appendQueryParameter("target", discriminator)
        .build()

    override fun isExpandedLayout(appWidgetId: Int): Boolean {
        val options = manager.getAppWidgetOptions(appWidgetId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val supportedSizes = options.widgetSizes(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            if (supportedSizes.isNotEmpty()) {
                return supportedSizes.any { size -> isExpandedWidgetSize(size.width, size.height) }
            }
            val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            return isExpandedWidgetSize(maxWidth.toFloat(), maxHeight.toFloat())
        }
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        return isExpandedWidgetSize(minWidth.toFloat(), minHeight.toFloat())
    }

    @Suppress("DEPRECATION")
    private fun Bundle.widgetSizes(optionKey: String): List<SizeF> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayList(optionKey, SizeF::class.java).orEmpty()
        } else {
            getParcelableArrayList<SizeF>(optionKey).orEmpty()
        }

    private fun currentPalette(): WidgetPalette {
        val preferences = context.getSharedPreferences(MainActivity.DISPLAY_PREFERENCES, Context.MODE_PRIVATE)
        val theme = AppThemeMode.fromStorage(preferences.getString(MainActivity.APP_THEME_MODE, null))
        val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        return resolveWidgetPalette(theme, systemDark)
    }

    private fun providerComponent() = ComponentName(context, NextEventAppWidgetProvider::class.java)

    private fun IntArray.filterValidIds() = filter { it > 0 }

    private companion object {
        const val DARK_BACKGROUND = 0xFF15151E.toInt()
        val RowIds = intArrayOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3)
    }
}

internal fun isExpandedWidgetSize(widthDp: Float, heightDp: Float): Boolean =
    widthDp >= EXPANDED_WIDTH_DP && heightDp >= EXPANDED_HEIGHT_DP
