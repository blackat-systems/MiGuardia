package com.blackatsystems.miguardia.beta

import java.security.PublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class BetaEnvironment(val keys: Map<String, PublicKey> = emptyMap(), val transport: BetaTransport = BetaHttpTransport())
internal enum class BetaPhase { LOADING, UNAVAILABLE, ACCESS, REPAIR, ACTIVE, OPINION, QUEUED, SENDING, RECEIVED, SEND_ERROR }
internal data class BetaState(
    val phase: BetaPhase = BetaPhase.LOADING,
    val activationValid: Boolean = false,
    val due: Boolean = false,
    val confirmationSaved: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val dueAt: Instant? = null,
) {
    val allowsContent: Boolean get() = activationValid && (!due || confirmationSaved)
    val received: Boolean get() = phase == BetaPhase.RECEIVED
}

/** Sole writer. Network operations never hold the storage mutex; a confirmed operation is immutable. */
internal class BetaCoordinator(
    private val storage: BetaStorage,
    private val identity: BetaIdentity,
    private val installed: () -> InstalledBetaApp,
    private val environment: () -> BetaEnvironment,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    private val monotonic: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val mutex = Mutex()
    private val activationDecision = Any()
    private val mutableState = MutableStateFlow(BetaState())
    val state: StateFlow<BetaState> = mutableState.asStateFlow()
    private var record = BetaRecord()
    @Volatile private var activation: BetaActivation? = null
    private var timer: Job? = null
    private var deferredRetry: Job? = null
    @Volatile private var foreground = false
    private var deadlineMono: Long? = null
    private var retryMono: Long = 0
    private val generation = AtomicLong()
    private var sendingRequest: String? = null
    private var networkJob: Job? = null
    @Volatile private var initialized = false
    private var dueNeedsSaving = false
    private val confirming = AtomicBoolean()

    private val updateOwner = Any()
    /** Read and narrowly sign only; this boundary never initializes or writes BetaStore. */
    internal val updateAccess: BetaUpdateAccess = object : BetaUpdateAccess {
        override suspend fun capture(releaseId: String): BetaUpdateContext = withContext(Dispatchers.IO) {
            mutex.withLock {
                BetaReceiptVerifier.identifier(releaseId)
                val (verified, app, hash) = verifiedUpdateIdentity()
                BetaUpdateContext(updateOwner, generation.get(), hash, verified.id, verified.envelope, app, releaseId)
            }
        }

        override suspend fun validate(context: BetaUpdateContext) = withContext(Dispatchers.IO) {
            mutex.withLock { validateUpdateContext(context) }
        }

        override suspend fun sign(context: BetaUpdateContext, challenge: BetaDownloadChallenge): String =
            withContext(Dispatchers.IO) { mutex.withLock {
                validateUpdateContext(context)
                challenge.requireFor(context)
                val bytes = challenge.proof(context.activationId).toByteArray(Charsets.UTF_8)
                val signature = identity.sign(bytes)
                validateUpdateContext(context)
                challenge.requireFor(context)
                check(BetaCrypto.verify(BetaCrypto.publicKey(identity.publicKey(false)), bytes, signature))
                BetaCrypto.encode(signature)
            } }
    }

    private fun verifiedUpdateIdentity(): Triple<BetaActivation, InstalledBetaApp, String> {
        check(initialized && activation != null && mutableState.value.activationValid)
        val hash = BetaCrypto.hash(identity.publicKey(false))
        check(hash == record.installationHash)
        val app = installed()
        val verified = BetaReceiptVerifier(environment().keys).activation(checkNotNull(record.activation), app, hash)
        check(verified == activation)
        return Triple(verified, app, hash)
    }

    private fun validateUpdateContext(context: BetaUpdateContext) {
        check(context.owner === updateOwner && context.generation == generation.get())
        val (verified, app, hash) = verifiedUpdateIdentity()
        check(context.receipt == verified.envelope && context.activationId == verified.id &&
            context.installed == app && context.installationHash == hash)
    }

    suspend fun initialize() = withContext(Dispatchers.IO) { mutex.withLock {
        if (initialized) return@withLock
        generation.incrementAndGet()
        try {
            record = storage.read()
            if (environment().keys.isEmpty()) {
                mutableState.value = BetaState(BetaPhase.UNAVAILABLE, message = "El acceso a la beta todavía no está disponible en esta versión.")
                return@withLock
            }
            val spki = identity.publicKey(allowCreate = record.installationHash == null && record.activation == null)
            val fingerprint = BetaCrypto.hash(spki)
            check(record.installationHash == null || record.installationHash == fingerprint)
            if (record.installationHash == null) save(record.copy(installationHash = fingerprint))
            val verifier = BetaReceiptVerifier(environment().keys)
            activation = record.activation?.let { verifier.activation(it, installed(), fingerprint) }
            record.pending?.let { pending ->
                check(BetaCrypto.verify(BetaCrypto.publicKey(spki), feedbackProof(checkNotNull(activation).id,
                    pending.requestId, pending.opinion.hash).toByteArray(Charsets.UTF_8), BetaCrypto.decode(pending.signature)))
            }
            record.feedbackReceipt?.let { verifier.feedback(it, checkNotNull(activation).id,
                checkNotNull(record.feedbackRequestId), checkNotNull(record.feedbackHash)) }
            initialized = true
            val dueSaved = runCatching { observeDue() }.isSuccess
            publish(if (dueSaved) null else "No pudimos guardar el plazo. Reintentá.")
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            activation = null
            mutableState.value = BetaState(BetaPhase.REPAIR, message = "No pudimos comprobar el acceso guardado. Reintentá; tus datos de trabajo siguen en el teléfono. Si falta la identidad de esta instalación, necesitás una reasignación de la beta.")
        }
    } }

    fun retryRead() { scope.launch { initialize(); onForeground() } }
    fun onForeground() {
        foreground = true
        scope.launch(Dispatchers.IO) {
            initialize()
            mutex.withLock {
                if (initialized) {
                    try { observeDue(); publish() } catch (_: Exception) { publish("No pudimos guardar el plazo. Reintentá.") }
                    scheduleDeadline()
                }
            }
            retryFeedback()
        }
    }
    fun onBackground() {
        foreground = false
        timer?.cancel(); timer = null
        scope.launch(Dispatchers.IO) { mutex.withLock {
            if (!foreground) { deferredRetry?.cancel(); deferredRetry = null }
        } }
    }
    fun connectionRecovered() { if (foreground) retryFeedback() }

    fun activate(code: String) {
        if (mutableState.value.busy || networkJob?.isActive == true || activation != null || !initialized) return
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        val token = generation.get()
        networkJob = scope.launch(Dispatchers.IO) {
            try {
                check(clock.instant() >= (record.retryAt ?: Instant.MIN) && monotonic() >= retryMono)
                val spki = identity.publicKey(false)
                val envelope = BetaClient(environment().transport, clock::instant).activate(code, spki)
                val verified = BetaReceiptVerifier(environment().keys).activation(envelope, installed(), BetaCrypto.hash(spki))
                mutex.withLock {
                    if (generation.get() != token || activation != null) return@withLock
                    // Once a durable commit begins, cancellation must not leave an unread receipt
                    // behind an initialized ACCESS state. A later explicit reread verifies it again.
                    initialized = false
                    withContext(NonCancellable) { save(record.copy(activation = envelope, retryAt = null)) }
                    val accepted = synchronized(activationDecision) {
                        if (generation.get() != token) false else {
                            activation = verified
                            initialized = true
                            true
                        }
                    }
                    if (!accepted) return@withLock
                    runCatching { observeDue() }; publish(); scheduleDeadline()
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                mutex.withLock {
                    if (generation.get() == token && activation == null) {
                        initialized = record.activation == null
                        if (error is BetaServiceFailure && error.retryAt != null) {
                            retryMono = monotonic() + Duration.between(clock.instant(), error.retryAt).toMillis().coerceAtLeast(0)
                            runCatching { save(record.copy(retryAt = error.retryAt)) }
                        }
                        mutableState.value = BetaState(BetaPhase.ACCESS, message = when {
                            error is BetaServiceFailure && error.status == 403 -> "No se pudo validar el acceso. Revisá el código recibido y reintentá."
                            error is BetaServiceFailure && error.status == 429 -> "Hay demasiados intentos. Esperá unos minutos antes de reintentar."
                            else -> "No pudimos activar esta instalación. Revisá la conexión y reintentá con el mismo código."
                        })
                    }
                }
            } finally {
                if (generation.get() == token) mutableState.value = mutableState.value.copy(busy = false)
            }
        }
    }

    fun cancelActivation() = synchronized(activationDecision) {
        if (activation != null) return@synchronized
        generation.incrementAndGet()
        networkJob?.cancel()
        mutableState.value = mutableState.value.copy(busy = false)
    }

    fun confirm(opinion: BetaOpinion) {
        if (mutableState.value.busy || !mutableState.value.activationValid || mutableState.value.confirmationSaved) return
        if (!confirming.compareAndSet(false, true)) return
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    if (record.pending != null || record.feedbackReceipt != null) return@withLock
                    check(BetaOpinion.create(opinion.category, opinion.area, opinion.message, true) == opinion)
                    val active = checkNotNull(activation)
                    val requestId = UUID.randomUUID().toString()
                    val signature = BetaCrypto.encode(identity.sign(feedbackProof(active.id, requestId, opinion.hash).toByteArray(Charsets.UTF_8)))
                    save(record.copy(pending = BetaPending(requestId, opinion, signature), terminalError = false, retryAt = null))
                    publish()
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { publish("No pudimos guardar tu opinión. Todavía no está a salvo; reintentá.") }
                finally { confirming.set(false); mutableState.value = mutableState.value.copy(busy = false) }
            }
            retryFeedback()
        }
    }

    fun retryFeedback(explicit: Boolean = false) = launchFeedback(explicit, foregroundOnly = false)

    private fun launchFeedback(explicit: Boolean, foregroundOnly: Boolean) {
        scope.launch(Dispatchers.IO) {
            val sending = mutex.withLock {
                if (foregroundOnly && !foreground) return@launch
                val pending = record.pending ?: return@launch
                val active = activation ?: return@launch
                if (sendingRequest != null || (!explicit && record.terminalError)) return@launch
                if (retryWaitMillis() > 0) {
                    deferFeedbackRetry(explicit)
                    return@launch
                }
                deferredRetry?.cancel(); deferredRetry = null
                sendingRequest = pending.requestId
                mutableState.value = mutableState.value.copy(phase = BetaPhase.SENDING, busy = true, message = null)
                Triple(pending, active, generation.get())
            }
            try {
                val (pending, active, token) = sending
                val receipt = BetaClient(environment().transport, clock::instant).feedback(active.envelope, pending)
                BetaReceiptVerifier(environment().keys).feedback(receipt, active.id, pending.requestId, pending.opinion.hash)
                mutex.withLock {
                    if (token != generation.get() || record.pending != pending) return@withLock
                    save(record.copy(pending = null, feedbackReceipt = receipt, feedbackRequestId = pending.requestId,
                        feedbackHash = pending.opinion.hash, retryAt = null, terminalError = false))
                    sendingRequest = null
                    publish()
                }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { mutex.withLock { sendingRequest = null; publish() } }
                throw cancelled
            } catch (error: Exception) {
                mutex.withLock {
                    if (record.pending != sending.first || generation.get() != sending.third) return@withLock
                    sendingRequest = null
                    val terminal = error !is java.io.IOException && error !is BetaServiceFailure ||
                        (error is BetaServiceFailure && !error.retryable)
                    val retryAt = (error as? BetaServiceFailure)?.retryAt ?: clock.instant().plusSeconds(30)
                    retryMono = monotonic() + Duration.between(clock.instant(), retryAt).toMillis().coerceAtLeast(0)
                    record = record.copy(retryAt = retryAt, terminalError = terminal)
                    runCatching { save(record) }
                    publish(if (terminal) "Tu opinión está guardada, pero el servicio no confirmó su recepción. Podés seguir usando esta versión y reintentar; si persiste, pedí ayuda para resolver el acceso."
                        else "Tu opinión está guardada en este teléfono. Podés continuar; el envío sigue pendiente.")
                }
            }
        }
    }
    private fun retryWaitMillis(): Long = maxOf(
        record.retryAt?.let { Duration.between(clock.instant(), it).toMillis().coerceAtLeast(0) } ?: 0,
        (retryMono - monotonic()).coerceAtLeast(0),
    )

    /** Consumes one legitimate trigger after cooldown; a failed send never schedules itself. */
    private fun deferFeedbackRetry(explicit: Boolean) {
        if (!foreground || deferredRetry?.isActive == true) return
        val token = generation.get()
        deferredRetry = scope.launch(Dispatchers.IO) {
            while (true) {
                val wait = mutex.withLock {
                    if (!foreground || generation.get() != token || record.pending == null ||
                        (record.terminalError && !explicit)) return@launch
                    retryWaitMillis()
                }
                if (wait == 0L) break
                delay(wait)
            }
            mutex.withLock {
                deferredRetry = null
                if (foreground && generation.get() == token) launchFeedback(explicit, foregroundOnly = true)
            }
        }
    }
    private suspend fun save(value: BetaRecord) {
        storage.write(value)
        record = value
        if (value.dueObserved) dueNeedsSaving = false
    }
    private suspend fun observeDue() {
        val active = activation ?: return
        if (deadlineMono == null) deadlineMono = monotonic() + Duration.between(clock.instant(), active.dueAt).toMillis().coerceAtLeast(0)
        if (!record.dueObserved && (clock.instant() >= active.dueAt || monotonic() >= checkNotNull(deadlineMono))) {
            // Latch in memory first: an IO failure or backwards civil clock must not reopen the gate.
            record = record.copy(dueObserved = true)
            dueNeedsSaving = true
        }
        if (dueNeedsSaving) save(record)
    }
    private fun scheduleDeadline() {
        timer?.cancel()
        if (!foreground || activation == null || record.dueObserved) return
        val wait = (checkNotNull(deadlineMono) - monotonic()).coerceAtLeast(0)
        timer = scope.launch(Dispatchers.IO) {
            delay(wait)
            mutex.withLock {
                runCatching { observeDue() }
                publish()
            }
        }
    }
    private fun publish(message: String? = null) {
        val active = activation
        val phase = when {
            active == null -> BetaPhase.ACCESS
            record.feedbackReceipt != null -> BetaPhase.RECEIVED
            record.pending != null && sendingRequest == record.pending?.requestId -> BetaPhase.SENDING
            record.pending != null && record.terminalError -> BetaPhase.SEND_ERROR
            record.pending != null -> BetaPhase.QUEUED
            record.dueObserved -> BetaPhase.OPINION
            else -> BetaPhase.ACTIVE
        }
        mutableState.value = BetaState(phase, active != null, record.dueObserved,
            record.pending != null || record.feedbackReceipt != null,
            busy = phase == BetaPhase.SENDING || confirming.get() || (active == null && networkJob?.isActive == true),
            message = message, dueAt = active?.dueAt)
    }
}
