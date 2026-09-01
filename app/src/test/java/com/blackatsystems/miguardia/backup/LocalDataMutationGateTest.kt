package com.blackatsystems.miguardia.backup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalDataMutationGateTest {
    @Test
    fun multipartPhotoMutationAndRestoreCannotOverlap() = runBlocking {
        val gate = LocalDataMutationGate()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        var restoreEntered = false

        val photoMutation = launch {
            gate.withExclusiveMutation {
                order += "photo-start"
                firstEntered.complete(Unit)
                releaseFirst.await()
                order += "photo-end"
            }
        }
        firstEntered.await()
        val restore = launch {
            gate.withExclusiveMutation {
                restoreEntered = true
                order += "restore"
            }
        }

        yield()
        assertFalse(restoreEntered)
        releaseFirst.complete(Unit)
        photoMutation.join()
        restore.join()

        assertEquals(listOf("photo-start", "photo-end", "restore"), order)
    }
}
