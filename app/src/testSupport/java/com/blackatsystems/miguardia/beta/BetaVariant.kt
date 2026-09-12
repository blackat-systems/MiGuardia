package com.blackatsystems.miguardia.beta
/** Historical protocol test support, excluded from every application APK. */
internal object BetaVariant {
    @Volatile var environmentForTests: BetaEnvironment = BetaEnvironment()
    fun environment(): BetaEnvironment = environmentForTests
}
