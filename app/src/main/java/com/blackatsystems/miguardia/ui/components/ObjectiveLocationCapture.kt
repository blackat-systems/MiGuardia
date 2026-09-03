package com.blackatsystems.miguardia.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.net.toUri
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Composable
fun ObjectiveLocationCapture(
    address: String,
    hasSavedLocation: Boolean,
    enabled: Boolean = true,
    onLocationCaptured: (latitude: Double, longitude: Double) -> Boolean,
    onLocationRemoved: () -> Boolean = { false },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val requestGuard = remember { ObjectiveLocationRequestGuard() }
    var isLocating by remember { mutableStateOf(false) }
    var isResolvingAddress by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var cancellationSignal by remember { mutableStateOf<CancellationSignal?>(null) }
    var addressLookupJob by remember { mutableStateOf<Job?>(null) }
    var pendingAddressResult by remember { mutableStateOf<GeocodedObjectiveLocation?>(null) }
    var confirmRemoval by remember { mutableStateOf(false) }
    var permissionWasDenied by rememberSaveable { mutableStateOf(false) }
    var pendingPermissionRequestToken by remember { mutableStateOf<Long?>(null) }
    val isBusy = isLocating || isResolvingAddress

    LaunchedEffect(address.trim()) {
        requestGuard.supersede()
        cancellationSignal?.cancel()
        addressLookupJob?.cancel()
        pendingPermissionRequestToken = null
        pendingAddressResult = null
        isLocating = false
        isResolvingAddress = false
        message = null
    }

    fun capture() {
        if (isLocating) return
        val requestToken = requestGuard.begin()
        isLocating = true
        message = "Buscando tu ubicación actual…"
        cancellationSignal?.cancel()
        cancellationSignal = requestCurrentLocation(
            context = context,
            onResult = { latitude, longitude ->
                if (!requestGuard.finish(requestToken)) return@requestCurrentLocation
                isLocating = false
                message = if (onLocationCaptured(latitude, longitude)) {
                    "Guardando ubicación…"
                } else {
                    "Ya se está guardando otra ubicación. Esperá un momento y volvé a intentarlo."
                }
            },
            onFailure = { failure ->
                if (!requestGuard.finish(requestToken)) return@requestCurrentLocation
                isLocating = false
                message = failure
            },
        )
    }

    fun resolveAddress() {
        val query = address.trim()
        if (query.isEmpty() || isBusy) return
        message = "Buscando la dirección…"
        isResolvingAddress = true
        addressLookupJob?.cancel()
        val requestToken = requestGuard.begin()
        addressLookupJob = scope.launch {
            try {
                val result = geocodeObjectiveAddress(context, query)
                if (!requestGuard.finish(requestToken)) return@launch
                isResolvingAddress = false
                if (result == null) {
                    message = "No encontramos esa dirección. Completala con localidad y provincia o usá tu ciudad actual para el clima."
                } else {
                    pendingAddressResult = result
                    message = null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (requestGuard.finish(requestToken)) {
                    isResolvingAddress = false
                    message = "No pudimos buscar la dirección. Revisá tu conexión o usá tu ciudad actual para el clima."
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val requestToken = pendingPermissionRequestToken
        if (requestToken != null && requestGuard.finish(requestToken)) {
            pendingPermissionRequestToken = null
            if (granted) {
                permissionWasDenied = false
                capture()
            } else {
                permissionWasDenied = true
                message = "El permiso fue rechazado. Podés usar una dirección, continuar sin clima o habilitarlo desde Ajustes."
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            requestGuard.invalidate()
            cancellationSignal?.cancel()
            addressLookupJob?.cancel()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (address.isNotBlank()) {
            OutlinedButton(
                onClick = ::resolveAddress,
                enabled = enabled && !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("objective-address-geocode"),
            ) {
                if (isResolvingAddress) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text("Usar esta dirección para el clima")
                }
            }
            Text("Android buscará una ubicación aproximada. Vas a poder revisarla antes de guardarla.")
        }
        OutlinedButton(
            onClick = {
                message = null
                if (
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    permissionWasDenied = false
                    capture()
                } else {
                    pendingPermissionRequestToken = requestGuard.begin()
                    permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            },
            enabled = enabled && !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("objective-location-capture"),
        ) {
            if (isLocating) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                Text(
                    when {
                        hasSavedLocation -> "Actualizar con mi ubicación actual"
                        address.isBlank() -> "Usar mi ciudad actual para el clima"
                        else -> "Usar mi ubicación actual en su lugar"
                    },
                )
            }
        }
        if (permissionWasDenied) {
            TextButton(
                onClick = {
                    val settingsIntent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:${context.packageName}".toUri(),
                    )
                    runCatching { context.startActivity(settingsIntent) }
                        .onFailure { message = "No pudimos abrir Ajustes. Buscá MiGuardia en Ajustes > Aplicaciones > Permisos." }
                },
                enabled = enabled && !isBusy,
                modifier = Modifier.testTag("objective-location-open-settings"),
            ) {
                Text("Abrir permisos de MiGuardia")
            }
        }
        Text(
            visibleLocationCaptureMessage(message, hasSavedLocation, enabled) ?: if (hasSavedLocation) {
                "Ubicación guardada. Sólo se usará para el clima de este objetivo."
            } else if (address.isBlank()) {
                "MiGuardia pedirá tu ubicación aproximada sólo cuando toques el botón. No hará seguimiento."
            } else {
                "Podés usar la dirección ahora o guardar el objetivo y configurar el clima más adelante."
            },
        )
        if (hasSavedLocation) {
            TextButton(
                onClick = { confirmRemoval = true },
                enabled = enabled && !isBusy,
                modifier = Modifier.testTag("objective-location-remove"),
            ) {
                Text("Quitar ubicación guardada")
            }
        }
    }
    pendingAddressResult?.let { result ->
        AlertDialog(
            onDismissRequest = { pendingAddressResult = null },
            title = { Text("¿Usar esta ubicación?") },
            text = {
                Text(
                    "Android encontró: ${result.displayName}. Puede ser aproximada; usala sólo si coincide con tu objetivo.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAddressResult = null
                        message = if (onLocationCaptured(result.latitude, result.longitude)) {
                            "Guardando ubicación…"
                        } else {
                            "Ya se está guardando otra ubicación. Esperá un momento y volvé a intentarlo."
                        }
                    },
                ) { Text("Usar para el clima") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAddressResult = null }) { Text("Corregir dirección") }
            },
        )
    }
    if (confirmRemoval) {
        AlertDialog(
            onDismissRequest = { confirmRemoval = false },
            title = { Text("¿Quitar la ubicación?") },
            text = {
                Text(
                    "Este objetivo dejará de tener clima. Las copias de seguridad que ya hayas creado no se modifican.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoval = false
                        message = if (onLocationRemoved()) {
                            null
                        } else {
                            "Ya se está guardando otra ubicación. Esperá un momento y volvé a intentarlo."
                        }
                    },
                ) { Text("Quitar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoval = false }) { Text("Conservar") }
            },
        )
    }
}

internal fun visibleLocationCaptureMessage(
    message: String?,
    hasSavedLocation: Boolean,
    enabled: Boolean,
): String? = if (message == "Guardando ubicación…" && hasSavedLocation && enabled) null else message

internal class ObjectiveLocationRequestGuard {
    private var generation = 0L
    private var active = true

    fun begin(): Long {
        if (!active) return INVALID_TOKEN
        generation += 1
        return generation
    }

    fun finish(token: Long): Boolean {
        if (!active || token == INVALID_TOKEN || token != generation) return false
        generation += 1
        return true
    }

    fun invalidate() {
        active = false
        generation += 1
    }

    fun supersede() {
        if (active) generation += 1
    }

    private companion object {
        const val INVALID_TOKEN = 0L
    }
}

internal data class GeocodedObjectiveLocation(
    val latitude: Double,
    val longitude: Double,
    val displayName: String,
)

internal suspend fun geocodeObjectiveAddress(
    context: Context,
    rawAddress: String,
): GeocodedObjectiveLocation? {
    val query = rawAddress.trim()
    require(query.isNotEmpty())
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context.applicationContext, Locale.forLanguageTag("es-AR"))
    val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { continuation ->
            try {
                geocoder.getFromLocationName(
                    query,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: List<Address>) {
                            if (continuation.isActive) continuation.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IOException(errorMessage ?: "El servicio de direcciones no respondió."),
                                )
                            }
                        }
                    },
                )
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    } else {
        @Suppress("DEPRECATION")
        withContext(Dispatchers.IO) { geocoder.getFromLocationName(query, 1).orEmpty() }
    }
    return addresses.firstNotNullOfOrNull(Address::toGeocodedObjectiveLocation)
}

private fun Address.toGeocodedObjectiveLocation(): GeocodedObjectiveLocation? {
    if (!hasLatitude() || !hasLongitude()) return null
    val latitude = latitude
    val longitude = longitude
    if (!latitude.isFinite() || latitude !in -90.0..90.0) return null
    if (!longitude.isFinite() || longitude !in -180.0..180.0) return null
    val visibleName = getAddressLine(0)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: listOfNotNull(thoroughfare, locality, adminArea, countryName)
            .joinToString(", ")
            .ifBlank { "la dirección ingresada" }
    return GeocodedObjectiveLocation(latitude, longitude, visibleName.take(240))
}

private fun requestCurrentLocation(
    context: Context,
    onResult: (Double, Double) -> Unit,
    onFailure: (String) -> Unit,
): CancellationSignal? {
    val manager = ContextCompat.getSystemService(context, LocationManager::class.java)
    if (manager == null) {
        onFailure("Este teléfono no ofrece un servicio de ubicación disponible.")
        return null
    }
    val enabledProviders = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
    val provider = LocationManager.NETWORK_PROVIDER.takeIf { it in enabledProviders }
    if (provider == null) {
        onFailure("No hay una ubicación aproximada de tu ciudad disponible. Podés usar la dirección o intentar más tarde.")
        return null
    }
    val cancellation = CancellationSignal()
    try {
        LocationManagerCompat.getCurrentLocation(
            manager,
            provider,
            cancellation,
            ContextCompat.getMainExecutor(context),
        ) { location ->
            if (location == null) {
                onFailure("No pudimos obtener la ubicación aproximada de tu ciudad. Intentá de nuevo más tarde.")
            } else {
                val latitude = location.latitude
                val longitude = location.longitude
                if (
                    latitude.isFinite() && latitude in -90.0..90.0 &&
                    longitude.isFinite() && longitude in -180.0..180.0
                ) {
                    onResult(latitude, longitude)
                } else {
                    onFailure("El teléfono devolvió una ubicación inválida. Intentá de nuevo.")
                }
            }
        }
    } catch (_: SecurityException) {
        onFailure("Falta el permiso de ubicación. Podés continuar sin clima.")
    } catch (_: IllegalArgumentException) {
        onFailure("La ubicación del teléfono no está disponible en este momento.")
    }
    return cancellation
}
