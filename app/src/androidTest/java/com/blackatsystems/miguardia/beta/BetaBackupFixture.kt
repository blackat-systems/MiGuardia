package com.blackatsystems.miguardia.beta

import android.content.Context
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import kotlinx.coroutines.CoroutineScope

/** Receipt and queued body remain local while the isolated backup restores all portable state. */
internal suspend fun seedIsolatedBetaForBackup(context: Context, scope: CoroutineScope): Pair<BetaStore, BetaRecord> {
    val key = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    val issuer = BetaDeviceIssuer(installedBetaApp(context))
    val receipt = BetaClient(issuer.transport, Instant::now).activate("MG" + "2345".repeat(6), key.public.encoded)
    val opinion = BetaOpinion.create(BetaCategory.IDEA, BetaArea.BACKUP, "Prueba aislada", true)
    val signature = Signature.getInstance("SHA256withECDSA").run {
        initSign(key.private); update(feedbackProof("device_activation", "backup_beta_request", opinion.hash).toByteArray()); sign()
    }
    val state = BetaRecord(installationHash = BetaCrypto.hash(key.public.encoded), activation = receipt, dueObserved = true,
        pending = BetaPending("backup_beta_request", opinion, BetaCrypto.encode(BetaCrypto.derToP1363(signature))))
    val store = BetaStore(File(context.noBackupFilesDir, "beta-v1.preferences_pb"), scope)
    store.write(state)
    return store to state
}
