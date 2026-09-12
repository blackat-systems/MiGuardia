package com.blackatsystems.miguardia.beta

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.material3.Text
import androidx.test.espresso.Espresso
import androidx.test.core.app.ApplicationProvider
import com.blackatsystems.miguardia.ui.theme.AppZoom
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme
import java.io.File
import java.security.KeyStore
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BetaComposeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun overdueOpinionKeepsPendingOperationHostThroughSavedStateRestoreUntilSafePoint() {
        var pending by mutableStateOf(false)
        var beta by mutableStateOf(BetaState(BetaPhase.ACTIVE, activationValid = true))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            var safe by rememberBetaSafePoint()
            MiGuardiaTheme {
                if (beta.allowsContentAt(safe)) {
                    SideEffect { safe = !pending }
                    Text(if (pending) "Operación pendiente" else "Punto seguro")
                } else Text("Opinión vencida")
            }
        }
        compose.runOnIdle { pending = true }
        compose.onNodeWithText("Operación pendiente").assertIsDisplayed()
        compose.runOnIdle { beta = beta.copy(phase = BetaPhase.OPINION, due = true) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Operación pendiente").assertIsDisplayed()
        compose.onNodeWithText("Opinión vencida").assertDoesNotExist()
        compose.runOnIdle { pending = false }
        compose.onNodeWithText("Opinión vencida").assertIsDisplayed()
    }

    @Test fun sharedMainAndWidgetBackHandlerCancelsCanjeBeforeClosing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val alias = "miguardia.beta.test.${UUID.randomUUID()}"
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val returned = CompletableDeferred<Unit>()
        var record = BetaRecord()
        var closed = false
        try {
            val installed = installedBetaApp(context)
            val issuer = BetaDeviceIssuer(installed)
            val store = object : BetaStorage {
                override suspend fun read() = record
                override suspend fun write(value: BetaRecord) { record = value }
            }
            val coordinator = BetaCoordinator(store, KeystoreBetaIdentity(alias), { installed },
                { BetaEnvironment(issuer.keys, BetaTransport { path, bytes ->
                    withContext(NonCancellable) {
                        entered.complete(Unit); release.await()
                        issuer.transport.post(path, bytes).also { returned.complete(Unit) }
                    }
                }) }, scope)
            coordinator.initialize()
            compose.setContent {
                MiGuardiaTheme {
                    BetaExitBackHandler(true, coordinator) { closed = true }
                    Text("Acceso de prueba")
                }
            }
            coordinator.activate("MG" + "2345".repeat(6))
            withTimeout(5000) { entered.await() }
            Espresso.pressBackUnconditionally()
            compose.runOnIdle { assertTrue(closed) }
            release.complete(Unit)
            withTimeout(5000) { returned.await() }
            delay(80)
            assertFalse(coordinator.state.value.activationValid)
            assertNull(record.activation)
        } finally {
            release.complete(Unit)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }
    @Test fun activationOpinionConsentQueueAndReceiptHaveDistinctAccessibleStatesAcrossInternalZooms() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(context.noBackupFilesDir, "beta-compose-${UUID.randomUUID()}.preferences_pb")
        val alias = "miguardia.beta.test.${UUID.randomUUID()}"
        try {
            val clock = MutableBetaDeviceClock(Instant.parse("2026-09-03T12:00:00.000Z"))
            val issuer = BetaDeviceIssuer(installedBetaApp(context), clock.value)
            val coordinator = BetaCoordinator(BetaStore(file, scope), KeystoreBetaIdentity(alias), { installedBetaApp(context) },
                { BetaEnvironment(issuer.keys, issuer.transport) }, scope, clock)
            coordinator.initialize()
            val draft = BetaDraftViewModel()
            var zoom by mutableStateOf(AppZoom.STANDARD)
            var dark by mutableStateOf(false)
            compose.setContent {
                val state by coordinator.state.collectAsState()
                MiGuardiaTheme(darkTheme = dark, appZoom = zoom) { BetaScreen(state, draft, coordinator) {} }
            }
            compose.onNodeWithTag("beta-activate").assertIsNotEnabled()
            compose.onNodeWithTag("beta-code").performTextInput("MG" + "2345".repeat(6))
            compose.onNodeWithTag("beta-activate").performScrollTo().performClick()
            awaitBeta { coordinator.state.value.activationValid }
            compose.onNodeWithTag("beta-status").assertTextContains("Esta instalación está activada.", substring = true)
            clock.value = clock.value.plusMillis(FEEDBACK_DELAY_MILLIS); coordinator.onForeground(); awaitBeta { coordinator.state.value.due }
            compose.onNodeWithText("Una idea").performScrollTo().performClick()
            compose.onNodeWithText("Calendario").performScrollTo().performClick()
            compose.onNodeWithTag("beta-message").performScrollTo().performTextInput("Una mejora ficticia")
            compose.onNodeWithTag("beta-confirm").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("Acepto enviar esta opinión a Blackat Systems para mejorar MiGuardia.").performScrollTo().performClick()
            for (newZoom in AppZoom.entries) for (newDark in listOf(false, true)) {
                compose.runOnIdle { zoom = newZoom; dark = newDark }
                compose.onNodeWithTag("beta-message").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("beta-confirm").performScrollTo().assertIsDisplayed().assertIsEnabled()
            }
            issuer.offline = true
            compose.onNodeWithTag("beta-confirm").performClick()
            awaitBeta { coordinator.state.value.confirmationSaved && !coordinator.state.value.busy }
            compose.onNodeWithTag("beta-status").performScrollTo().assertTextContains("Recepción pendiente.", substring = true)
            assertTrue(coordinator.state.value.allowsContent); assertFalse(coordinator.state.value.received)
            issuer.offline = false; clock.value = clock.value.plusSeconds(31)
            // Civil and monotonic backoff both apply; reconstructing a coordinator is covered separately.
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            check(!file.exists() || file.delete())
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }
}
