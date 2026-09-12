package com.blackatsystems.miguardia.support

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SupportMenuActionTest {
    @Test fun `callback waits for close completion and simultaneous taps collapse`() = runBlocking {
        val action = SupportMenuAction()
        val closed = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            action.open({ events += "closing"; closed.await(); events += "closed" }, { true }) { events += "open" }
        }
        action.open({ error("Second tap must not close again") }, { true }) { error("Duplicate open") }
        assertEquals(listOf("closing"), events)
        closed.complete(Unit)
        first.join()
        assertEquals(listOf("closing", "closed", "open"), events)
    }

    @Test fun `gate is read after close and never replayed after reopening access`() = runBlocking {
        val action = SupportMenuAction()
        val closed = CompletableDeferred<Unit>()
        var allowed = true
        var opens = 0
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            action.open({ closed.await() }, { allowed }) { opens++ }
        }
        allowed = false
        closed.complete(Unit)
        first.join()
        allowed = true
        assertEquals(0, opens)
        action.open({}, { allowed }) { opens++ }
        assertEquals(1, opens)
    }

    @Test fun `disposing during close cancels without launching or retaining a tap`() = runBlocking {
        val action = SupportMenuAction()
        var opens = 0
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            action.open({ CompletableDeferred<Unit>().await() }, { true }) { opens++ }
        }
        first.cancelAndJoin()
        assertEquals(0, opens)
        action.open({}, { true }) { opens++ }
        assertEquals(1, opens)
    }

    @Test fun `leaving foreground invalidates an in flight tap even when access becomes allowed again`() = runBlocking {
        val action = SupportMenuAction()
        val closed = CompletableDeferred<Unit>()
        var opens = 0
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            action.open({ closed.await() }, { true }) { opens++ }
        }
        action.cancelPendingOpen()
        closed.complete(Unit)
        first.join()
        assertEquals(0, opens)
        action.open({}, { true }) { opens++ }
        assertEquals(1, opens)
    }

}
