package com.blackatsystems.miguardia.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.MiGuardiaApplication
import com.blackatsystems.miguardia.StartupRecoveryState
import com.blackatsystems.miguardia.core.domain.widget.WidgetMode
import com.blackatsystems.miguardia.core.domain.widget.WidgetPrivacy
import com.blackatsystems.miguardia.ui.components.ScreenHeading
import com.blackatsystems.miguardia.ui.components.SectionCard
import com.blackatsystems.miguardia.ui.theme.AppThemeMode
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

data class WidgetConfigurationDraft(
    val mode: WidgetMode,
    val privacy: WidgetPrivacy,
    val includeWeather: Boolean,
)

class WidgetConfigurationActivity : ComponentActivity() {
    private val widgetId: Int by lazy {
        intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        if (!isInstalledWidget(widgetId)) {
            finish()
            return
        }
        val application = application as MiGuardiaApplication
        setContent {
            val recoveryState by application.startupRecoveryGate.state.collectAsStateWithLifecycle()
            when (val recovery = recoveryState) {
                StartupRecoveryState.Ready -> ReadyWidgetConfiguration(application)
                StartupRecoveryState.Recovering -> MiGuardiaTheme {
                    WidgetRecoveryGateScreen(errorMessage = null, onCancel = ::finish)
                }
                is StartupRecoveryState.Failed -> MiGuardiaTheme {
                    WidgetRecoveryGateScreen(errorMessage = recovery.message, onCancel = ::finish)
                }
            }
        }
    }

    @Composable
    private fun ReadyWidgetConfiguration(application: MiGuardiaApplication) {
        val displayPreferences = remember {
            getSharedPreferences(MainActivity.DISPLAY_PREFERENCES, MODE_PRIVATE)
        }
        val zoom = AppZoom.fromPercent(
            displayPreferences.getInt(MainActivity.APP_ZOOM_PERCENT, AppZoom.STANDARD.percent),
        )
        val theme = AppThemeMode.fromStorage(
            displayPreferences.getString(MainActivity.APP_THEME_MODE, null),
        )
        val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
        MiGuardiaTheme(darkTheme = theme.resolve(systemDark), appZoom = zoom) {
            WidgetConfigurationContent(application)
        }
    }

    @Composable
    private fun WidgetConfigurationContent(application: MiGuardiaApplication) {
        var loaded by remember { mutableStateOf<WidgetInstancePreferences?>(null) }
        var loadFailed by remember { mutableStateOf(false) }
        var saveFailed by rememberSaveable { mutableStateOf(false) }
        var saving by rememberWidgetConfigurationSavingState()
        var modeName by rememberSaveable { mutableStateOf<String?>(null) }
        var privacyName by rememberSaveable { mutableStateOf<String?>(null) }
        var includeWeather by rememberSaveable { mutableStateOf<Boolean?>(null) }
        var loadAttempt by rememberSaveable { mutableIntStateOf(0) }

        LaunchedEffect(loadAttempt) {
            loadFailed = false
            runCatching { application.widgetPreferences.current(widgetId) }
                .onSuccess { preferences ->
                    loaded = preferences
                    if (modeName == null) modeName = preferences.mode.name
                    if (privacyName == null) privacyName = preferences.privacy.name
                    if (includeWeather == null) includeWeather = preferences.includeWeather
                }
                .onFailure { loadFailed = true }
        }
        val existing = loaded
        if (existing == null || modeName == null || privacyName == null || includeWeather == null) {
            LoadingConfiguration(
                failed = loadFailed,
                onRetry = { loadAttempt++ },
                onCancel = ::finish,
            )
            return
        }
        val draft = WidgetConfigurationDraft(
            mode = runCatching { WidgetMode.valueOf(requireNotNull(modeName)) }.getOrDefault(WidgetMode.AUTOMATIC),
            privacy = runCatching { WidgetPrivacy.valueOf(requireNotNull(privacyName)) }.getOrDefault(WidgetPrivacy.HIDDEN),
            includeWeather = requireNotNull(includeWeather),
        )
        WidgetConfigurationScreen(
            draft = draft,
            isReconfiguration = existing.configured,
            saving = saving,
            errorMessage = "No pudimos guardar la configuración. Probá nuevamente."
                .takeIf { saveFailed },
            onDraftChange = { changed ->
                saveFailed = false
                modeName = changed.mode.name
                privacyName = changed.privacy.name
                includeWeather = changed.includeWeather
            },
            onCancel = ::finish,
            onSave = {
                if (!saving) {
                    saving = true
                    saveFailed = false
                    lifecycleScope.launch {
                        val saved = runCatching {
                            check(isInstalledWidget(widgetId)) { "El Widget ya no está instalado." }
                            application.widgetPreferences.save(
                                widgetId,
                                WidgetInstancePreferences(
                                    mode = draft.mode,
                                    privacy = draft.privacy,
                                    includeWeather = draft.includeWeather,
                                    configured = true,
                                ),
                            )
                        }.isSuccess
                        if (saved) {
                            application.widgetRuntime.refresh(widgetId)
                            setResult(
                                Activity.RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                            )
                            finish()
                        } else {
                            saving = false
                            saveFailed = true
                        }
                    }
                }
            },
        )
    }

    private fun isInstalledWidget(id: Int): Boolean {
        val manager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, NextEventAppWidgetProvider::class.java)
        return belongsToWidgetProvider(id, manager.getAppWidgetIds(provider))
    }
}

@Composable
private fun WidgetRecoveryGateScreen(errorMessage: String?, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag("widget-recovery-gate"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (errorMessage == null) {
            CircularProgressIndicator()
            Text("Protegiendo tus datos antes de configurar el Widget…", modifier = Modifier.padding(top = 12.dp))
        } else {
            Text(errorMessage, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            Text(
                "Abrí MiGuardia para terminar la recuperación antes de configurar el Widget.",
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text("Cancelar")
        }
    }
}

@Composable
internal fun rememberWidgetConfigurationSavingState(): MutableState<Boolean> =
    remember { mutableStateOf(false) }

internal fun belongsToWidgetProvider(appWidgetId: Int, providerIds: IntArray): Boolean =
    appWidgetId > 0 && providerIds.any { it == appWidgetId }

@Composable
fun WidgetConfigurationScreen(
    draft: WidgetConfigurationDraft,
    isReconfiguration: Boolean,
    saving: Boolean,
    errorMessage: String? = null,
    onDraftChange: (WidgetConfigurationDraft) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .testTag("widget-configuration-screen"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeading(
                title = if (isReconfiguration) "Reconfigurar Widget" else "Configurar Widget",
                supportingText = "Elegí qué querés ver en esta instancia. Cada Widget conserva su propia opción.",
            )
            SectionCard("Contenido") {
                WidgetMode.entries.forEach { mode ->
                    ConfigurationChoice(
                        label = mode.label(),
                        description = mode.description(),
                        selected = draft.mode == mode,
                        onSelect = { onDraftChange(draft.copy(mode = mode)) },
                        testTag = "widget-mode-${mode.name.lowercase()}",
                    )
                }
            }
            SectionCard("Privacidad") {
                WidgetPrivacy.entries.forEach { privacy ->
                    ConfigurationChoice(
                        label = privacy.label(),
                        description = privacy.description(),
                        selected = draft.privacy == privacy,
                        onSelect = { onDraftChange(draft.copy(privacy = privacy)) },
                        testTag = "widget-privacy-${privacy.name.lowercase()}",
                    )
                }
            }
            SectionCard(
                title = "Clima opcional",
                supportingText = "Sólo aparece en tamaño ampliado, privacidad Completa y una jornada con caché fresca y cobertura total.",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Incluir Clima cuando esté disponible", modifier = Modifier.weight(1f))
                    Switch(
                        checked = draft.includeWeather,
                        onCheckedChange = { onDraftChange(draft.copy(includeWeather = it)) },
                        modifier = Modifier.testTag("widget-weather-toggle"),
                    )
                }
                Text(
                    "El evento local se muestra primero. Si no hay red o el pronóstico no sirve, el Widget sigue funcionando sin Clima.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("widget-save-error"),
                )
            }
            Button(
                onClick = onSave,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().testTag("widget-save"),
            ) {
                Text(
                    if (saving) "Guardando…"
                    else if (isReconfiguration) "Guardar cambios"
                    else "Continuar",
                )
            }
            OutlinedButton(
                onClick = onCancel,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().testTag("widget-cancel"),
            ) {
                Text("Cancelar")
            }
        }
    }
}

@Composable
private fun LoadingConfiguration(failed: Boolean, onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (failed) {
            Text("No pudimos leer la configuración del Widget.", fontWeight = FontWeight.SemiBold)
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Reintentar") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Cancelar") }
        } else {
            CircularProgressIndicator()
            Text("Cargando configuración…", modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun ConfigurationChoice(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun WidgetMode.label(): String = when (this) {
    WidgetMode.NEXT_SHIFT -> "Próxima jornada"
    WidgetMode.NEXT_DAY_OFF -> "Próximo franco"
    WidgetMode.AUTOMATIC -> "Automático"
}

private fun WidgetMode.description(): String = when (this) {
    WidgetMode.NEXT_SHIFT -> "Primera jornada futura; una jornada activa queda excluida."
    WidgetMode.NEXT_DAY_OFF -> "Sólo el próximo franco marcado explícitamente."
    WidgetMode.AUTOMATIC -> "Prioridad compartida entre jornada, disponibilidad, franco y vacío."
}

private fun WidgetPrivacy.label(): String = when (this) {
    WidgetPrivacy.COMPLETE -> "Completa"
    WidgetPrivacy.REDUCED -> "Reducida"
    WidgetPrivacy.HIDDEN -> "Oculta"
}

private fun WidgetPrivacy.description(): String = when (this) {
    WidgetPrivacy.COMPLETE -> "Muestra tipo, lugar, horario y contexto permitido."
    WidgetPrivacy.REDUCED -> "Muestra estado genérico, fecha y horario."
    WidgetPrivacy.HIDDEN -> "No muestra tipo, fecha, horario, lugar, color ni cuenta regresiva."
}
