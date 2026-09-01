package com.blackatsystems.miguardia.backup

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes the few local mutations that span Room and private files.
 *
 * Room already serializes database-only writers. Photo mutations need this additional gate so a
 * restore cannot swap the photo directory between their file and metadata steps.
 */
class LocalDataMutationGate {
    private val mutex = Mutex()

    suspend fun <T> withExclusiveMutation(block: suspend () -> T): T = mutex.withLock { block() }
}
