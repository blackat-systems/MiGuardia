package com.blackatsystems.miguardia.beta

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

internal interface BetaIdentity {
    fun publicKey(allowCreate: Boolean): ByteArray
    fun sign(bytes: ByteArray): ByteArray
}
internal class KeystoreBetaIdentity(private val alias: String = "miguardia.beta.installation.v1") : BetaIdentity {
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    @Synchronized override fun publicKey(allowCreate: Boolean): ByteArray {
        val store = store()
        if (!store.containsAlias(alias)) {
            check(allowCreate) { "Installation identity unavailable" }
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256).setUserAuthenticationRequired(false).build())
            }.generateKeyPair()
        }
        return store().getCertificate(alias).publicKey.encoded.also { BetaCrypto.publicKey(it) }
    }
    override fun sign(bytes: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        val entry = store().getEntry(alias, null) as KeyStore.PrivateKeyEntry
        initSign(entry.privateKey); update(bytes); BetaCrypto.derToP1363(sign())
    }
}
@Suppress("DEPRECATION")
internal fun installedBetaApp(context: Context): InstalledBetaApp {
    val info = context.packageManager.getPackageInfo(context.packageName,
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
    val certificates = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
    check(certificates?.size == 1)
    return InstalledBetaApp(context.packageName, BetaCrypto.hash(certificates.single().toByteArray()),
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong())
}
