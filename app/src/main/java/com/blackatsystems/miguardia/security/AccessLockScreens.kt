package com.blackatsystems.miguardia.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackatsystems.miguardia.R
import com.blackatsystems.miguardia.ui.components.ScreenHeading
import com.blackatsystems.miguardia.ui.components.SectionCard
import androidx.compose.ui.res.stringResource

@Composable
internal fun AccessLockContentGate(
    state: AccessLockState,
    capability: DeviceAuthenticationCapability,
    onUnlock: () -> Unit,
    onRetryStore: () -> Unit,
    onRepairStore: () -> Unit,
    onRetryDeviceSecurity: () -> Unit,
    onOpenDeviceSecurity: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sensitiveStateHolder = rememberSaveableStateHolder()
    if (state.allowsSensitiveContent) {
        sensitiveStateHolder.SaveableStateProvider("miguardia-sensitive-content") {
            content()
        }
    } else {
        AccessLockGate(
            state = state,
            capability = capability,
            onUnlock = onUnlock,
            onRetryStore = onRetryStore,
            onRepairStore = onRepairStore,
            onRetryDeviceSecurity = onRetryDeviceSecurity,
            onOpenDeviceSecurity = onOpenDeviceSecurity,
        )
    }
}

@Composable
internal fun AccessLockGate(
    state: AccessLockState,
    capability: DeviceAuthenticationCapability,
    onUnlock: () -> Unit,
    onRetryStore: () -> Unit,
    onRepairStore: () -> Unit,
    onRetryDeviceSecurity: () -> Unit,
    onOpenDeviceSecurity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("access-lock-gate"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(20.dp))
        when (state.phase) {
            AccessLockPhase.WAITING_FOR_RECOVERY,
            AccessLockPhase.LOADING,
            -> {
                CircularProgressIndicator(modifier = Modifier.testTag("access-lock-loading"))
                Text(
                    text = stringResource(R.string.access_lock_protecting),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            AccessLockPhase.STORE_ERROR -> {
                Text(
                    text = stringResource(R.string.access_lock_store_error_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.access_lock_store_error_body),
                    modifier = Modifier.padding(top = 12.dp),
                )
                AccessLockMessage(state.message)
                Button(
                    onClick = onRetryStore,
                    enabled = !state.authenticationInProgress && !state.persistenceInProgress,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp).testTag("access-lock-retry-store"),
                ) { Text(stringResource(R.string.retry)) }
                OutlinedButton(
                    onClick = onRepairStore,
                    enabled = !state.authenticationInProgress && !state.persistenceInProgress &&
                        capability == DeviceAuthenticationCapability.AVAILABLE,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("access-lock-repair-store"),
                ) { Text(stringResource(R.string.access_lock_repair)) }
                DeviceCapabilityMessage(capability, onRetryDeviceSecurity, onOpenDeviceSecurity)
            }

            AccessLockPhase.READY -> {
                if (!state.locked) {
                    CircularProgressIndicator(modifier = Modifier.testTag("access-lock-privacy-cover"))
                    Text(
                        text = stringResource(R.string.access_lock_protecting),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.access_lock_gate_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.access_lock_gate_body),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    AccessLockMessage(state.message)
                    Button(
                        onClick = onUnlock,
                        enabled = !state.authenticationInProgress &&
                            capability == DeviceAuthenticationCapability.AVAILABLE,
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).testTag("access-lock-unlock"),
                    ) {
                        if (state.authenticationInProgress) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp))
                        } else {
                            Text(stringResource(R.string.access_lock_unlock))
                        }
                    }
                    DeviceCapabilityMessage(capability, onRetryDeviceSecurity, onOpenDeviceSecurity)
                }
            }
        }
    }
}

@Composable
internal fun AccessLockSettingsScreen(
    state: AccessLockState,
    capability: DeviceAuthenticationCapability,
    contentPadding: PaddingValues,
    onActivate: (AccessLockTimeout) -> Unit,
    onChangeTimeout: (AccessLockTimeout) -> Unit,
    onDisable: () -> Unit,
    onLockNow: () -> Unit,
    onRetryDeviceSecurity: () -> Unit,
    onOpenDeviceSecurity: () -> Unit,
) {
    val configuration = state.configuration ?: AccessLockConfiguration()
    var disabledDraftName by rememberSaveable { mutableStateOf(configuration.timeout.name) }
    LaunchedEffect(configuration.enabled, configuration.timeout) {
        disabledDraftName = configuration.timeout.name
    }
    val disabledDraft = AccessLockTimeout.entries
        .firstOrNull { it.name == disabledDraftName }
        ?: AccessLockTimeout.IMMEDIATE
    val selected = if (configuration.enabled) configuration.timeout else disabledDraft
    val busy = state.authenticationInProgress || state.persistenceInProgress

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("access-lock-settings"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeading(
            title = stringResource(R.string.access_lock),
            supportingText = stringResource(R.string.access_lock_intro),
        )
        SectionCard(stringResource(R.string.access_lock)) {
            Text(
                text = if (configuration.enabled) {
                    stringResource(R.string.access_lock_status_enabled)
                } else {
                    stringResource(R.string.access_lock_status_disabled)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("access-lock-status"),
            )
            DeviceCapabilityMessage(capability, onRetryDeviceSecurity, onOpenDeviceSecurity)
            AccessLockMessage(state.message)
            Button(
                onClick = {
                    if (configuration.enabled) onDisable() else onActivate(disabledDraft)
                },
                enabled = !busy && capability == DeviceAuthenticationCapability.AVAILABLE,
                modifier = Modifier.fillMaxWidth().testTag("access-lock-enabled-control"),
            ) {
                Text(
                    if (configuration.enabled) stringResource(R.string.access_lock_disable)
                    else stringResource(R.string.access_lock_enable),
                )
            }
            if (configuration.enabled) {
                OutlinedButton(
                    onClick = onLockNow,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("access-lock-now"),
                ) { Text(stringResource(R.string.access_lock_now)) }
            }
        }
        SectionCard(stringResource(R.string.access_lock_timeout_title)) {
            AccessLockTimeout.entries.forEach { timeout ->
                AccessLockTimeoutChoice(
                    timeout = timeout,
                    selected = selected == timeout,
                    enabled = !busy,
                    onClick = {
                        if (configuration.enabled) onChangeTimeout(timeout)
                        else disabledDraftName = timeout.name
                    },
                )
            }
        }
        SectionCard(stringResource(R.string.access_lock_scope_title)) {
            Text(stringResource(R.string.access_lock_scope_body))
            Text(
                text = stringResource(R.string.access_lock_not_encryption),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AccessLockTimeoutChoice(
    timeout: AccessLockTimeout,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 8.dp)
            .testTag("access-lock-timeout-${timeout.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(timeout.label(), modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun AccessLockTimeout.label(): String = when (this) {
    AccessLockTimeout.IMMEDIATE -> stringResource(R.string.access_lock_timeout_immediate)
    AccessLockTimeout.ONE_MINUTE -> stringResource(R.string.access_lock_timeout_one_minute)
    AccessLockTimeout.FIVE_MINUTES -> stringResource(R.string.access_lock_timeout_five_minutes)
    AccessLockTimeout.FIFTEEN_MINUTES -> stringResource(R.string.access_lock_timeout_fifteen_minutes)
}

@Composable
private fun DeviceCapabilityMessage(
    capability: DeviceAuthenticationCapability,
    onRetryDeviceSecurity: () -> Unit,
    onOpenDeviceSecurity: () -> Unit,
) {
    Text(
        text = when (capability) {
            DeviceAuthenticationCapability.AVAILABLE -> stringResource(R.string.access_lock_device_ready)
            DeviceAuthenticationCapability.NO_SECURE_CREDENTIAL -> stringResource(R.string.access_lock_device_not_ready)
            DeviceAuthenticationCapability.TEMPORARILY_UNAVAILABLE ->
                stringResource(R.string.access_lock_device_temporarily_unavailable)
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (capability == DeviceAuthenticationCapability.AVAILABLE) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.error
        },
        modifier = Modifier.testTag("access-lock-device-capability"),
    )
    if (capability != DeviceAuthenticationCapability.AVAILABLE) {
        OutlinedButton(
            onClick = onRetryDeviceSecurity,
            modifier = Modifier.fillMaxWidth().testTag("access-lock-retry-device-security"),
        ) { Text(stringResource(R.string.access_lock_retry_security)) }
        OutlinedButton(
            onClick = onOpenDeviceSecurity,
            modifier = Modifier.fillMaxWidth().testTag("access-lock-open-device-security"),
        ) { Text(stringResource(R.string.access_lock_open_security)) }
    }
}

@Composable
private fun AccessLockMessage(message: AccessLockMessage?) {
    if (message == null) return
    val text = when (message) {
        AccessLockMessage.USER_CANCELLED -> stringResource(R.string.access_lock_auth_cancelled)
        AccessLockMessage.SYSTEM_CANCELLED -> stringResource(R.string.access_lock_auth_system_cancelled)
        AccessLockMessage.LOCKOUT -> stringResource(R.string.access_lock_auth_lockout)
        AccessLockMessage.HARDWARE_UNAVAILABLE -> stringResource(R.string.access_lock_auth_hardware)
        AccessLockMessage.BIOMETRIC_NOT_ENROLLED -> stringResource(R.string.access_lock_auth_not_enrolled)
        AccessLockMessage.NO_SECURE_CREDENTIAL -> stringResource(R.string.access_lock_device_not_ready)
        AccessLockMessage.UNSUPPORTED -> stringResource(R.string.access_lock_auth_unsupported)
        AccessLockMessage.RECOVERABLE_ERROR,
        AccessLockMessage.FINAL_ERROR,
        -> stringResource(R.string.access_lock_auth_error)
        AccessLockMessage.STORE_WRITE_ERROR -> stringResource(R.string.access_lock_store_write_error)
    }
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp).testTag("access-lock-message"),
    )
}
