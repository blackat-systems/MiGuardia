package com.blackatsystems.miguardia.beta

import android.content.Intent
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.blackatsystems.miguardia.MainActivity
import com.blackatsystems.miguardia.MiGuardiaApplication
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Manual two-process fixture only. Each state needs a fresh disposable QA sandbox.
 * Prepare one method, explicitly force-stop QA, then verify in a separate invocation.
 * Never installs, clears data, creates a Keystore identity, or activates the Application.
 */
class PublicColdRootTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<MiGuardiaApplication>()
    private val evidence get() = File(app.cacheDir,"public-cold-root")
    private fun authorized() {
        check(app.packageName == "com.blackatsystems.miguardia.qa")
        check(InstrumentationRegistry.getArguments().getString("publicColdRoot") == "authorized")
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun residualHash(): String = hash(app.noBackupFilesDir.walkTopDown().filter {it.isFile && it.name.startsWith("beta")}
        .map {it.relativeTo(app.noBackupFilesDir).path + ":" + hash(it.readBytes())}.sorted().joinToString("\n").toByteArray())
    private fun aliasesHash(): String {
        val store = KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        return hash(java.util.Collections.list(store.aliases()).sorted().joinToString("\n").toByteArray())
    }
    private fun prepare(state: String): Unit = runBlocking {
        authorized(); PublicQaRootFixture.awaitReady(app)
        check(!evidence.exists() && !File(app.noBackupFilesDir,"beta-v1.preferences_pb").exists()) {"Use a fresh disposable QA sandbox; never overwrite a prior session"}
        if (state == "corrupt") File(app.noBackupFilesDir,"beta-v1.preferences_pb").writeBytes(byteArrayOf(-1, 0, -1))
        else if (state != "absent") {
            val identity = KeyPairGenerator.getInstance("EC").apply {initialize(ECGenParameterSpec("secp256r1"))}.generateKeyPair()
            val installed = installedBetaApp(app).let {if(state == "incompatible") it.copy(packageName="different.package") else it}
            val date = if(state in listOf("expired","queued")) Instant.parse("2020-01-01T00:00:00Z") else Instant.now()
            val issuer = BetaDeviceIssuer(installed,date)
            val envelope = BetaClient(issuer.transport,Instant::now).activate("MG"+"2345".repeat(6),identity.public.encoded)
            val pending = if(state == "queued") BetaPending("public_cold_request",BetaOpinion.create(BetaCategory.OTHER,BetaArea.OTHER,"Fictitious residual",true),BetaCrypto.encode(ByteArray(64) {1})) else null
            val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
            try { BetaStore(File(app.noBackupFilesDir,"beta-v1.preferences_pb"),scope).write(BetaRecord(BetaCrypto.hash(identity.public.encoded),envelope,state in listOf("expired","queued"),pending)) }
            finally {scope.coroutineContext[Job]!!.cancelAndJoin()}
        }
        check(evidence.mkdir())
        File(evidence,"state").writeText(state)
        File(evidence,"pid").writeText(Process.myPid().toString())
        File(evidence,"residual-sha256").writeText(residualHash())
        File(evidence,"aliases-sha256").writeText(aliasesHash())
    }
    @Test fun prepareAbsent() = prepare("absent")
    @Test fun prepareValidTestReceipt() = prepare("valid")
    @Test fun prepareCorrupt() = prepare("corrupt")
    @Test fun prepareIncompatible() = prepare("incompatible")
    @Test fun prepareExpired() = prepare("expired")
    @Test fun prepareQueued() = prepare("queued")
    @Test fun verifyAfterExplicitProcessDeath() {
        authorized()
        assertNotEquals(File(evidence,"pid").readText(),Process.myPid().toString())
        fun unchanged() {
            assertEquals(File(evidence,"residual-sha256").readText(),residualHash())
            assertEquals(File(evidence,"aliases-sha256").readText(),aliasesHash())
        }
        unchanged() // Snapshot was captured in the prior process, before this Application.onCreate.
        ActivityScenario.launch<MainActivity>(Intent(app,MainActivity::class.java)).use {scenario ->
            val tags = listOf("access-lock-gate","work-sector-selection","work-setup-calendar-guide","work-setup-overview","help-introduction-1","month-grid")
            repeat(2) {
                compose.waitUntil(15000) {tags.any {compose.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty()}}
                compose.onNodeWithTag("beta-screen").assertDoesNotExist()
                unchanged()
                if(it == 0) scenario.recreate()
            }
        }
        unchanged()
        // Preserve residue and hashes for operator review; no automatic deletion.
    }
}
