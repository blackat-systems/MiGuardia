package com.blackatsystems.miguardia

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.notification.NotificationBoundaryIdentity
import com.blackatsystems.miguardia.core.domain.notification.NotificationBoundaryType
import com.blackatsystems.miguardia.notifications.ShiftAlarmReceiver
import com.blackatsystems.miguardia.notifications.processQueuedNotificationActionsUnderMutationGate
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDeferredActionsInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val application: MiGuardiaApplication
        get() = context.applicationContext as MiGuardiaApplication

    @Before
    fun setUp() = runBlocking {
        application.notificationRuntime.pauseForRestore()
        assertTrue(
            context.getSharedPreferences("notification_deferred_actions", Context.MODE_PRIVATE)
                .edit().clear().commit(),
        )
        application.notificationPreferences.setDismissedEventKeys(emptySet())
        application.startupRecoveryGate.recovering()
    }

    @After
    fun tearDown() = runBlocking {
        application.startupRecoveryGate.ready()
        application.notificationPreferences.setDismissedEventKeys(emptySet())
        assertTrue(
            context.getSharedPreferences("notification_deferred_actions", Context.MODE_PRIVATE)
                .edit().clear().commit(),
        )
        application.notificationRuntime.resumeAfterRestore()
    }

    @Test
    fun dismissalDuringStartupRecoveryIsReplayedExactlyOnce() = runBlocking {
        ShiftAlarmReceiver().onReceive(
            context,
            Intent(ShiftAlarmReceiver.ACTION_NOTIFICATION_DISMISSED).setData(
                Uri.Builder()
                    .scheme("miguardia")
                    .authority("notification-dismissed")
                    .appendQueryParameter("event", EVENT_KEY)
                    .build(),
            ),
        )

        assertTrue(application.notificationDeferredActions.hasPendingActions())
        assertFalse(EVENT_KEY in application.notificationPreferences.dismissedEventKeys())

        application.notificationDeferredActions.replay(application.notificationPreferences)
        application.notificationDeferredActions.replay(application.notificationPreferences)

        assertFalse(application.notificationDeferredActions.hasPendingActions())
        assertTrue(EVENT_KEY in application.notificationPreferences.dismissedEventKeys())
    }

    @Test
    fun dismissalReplayWaitsForRestoreMutationAndCannotBeOverwritten() = runBlocking {
        application.startupRecoveryGate.ready()
        lateinit var receiverWork: Deferred<Unit>

        application.localDataMutationGate.withExclusiveMutation {
            application.notificationDeferredActions.enqueueDismissal(EVENT_KEY)
            receiverWork = async(start = CoroutineStart.UNDISPATCHED) {
                processQueuedNotificationActionsUnderMutationGate(application)
            }

            assertTrue(application.notificationDeferredActions.hasPendingActions())
            assertFalse(EVENT_KEY in application.notificationPreferences.dismissedEventKeys())

            // Simulates the preference swap followed by the replay that restore performs while
            // it still owns the same non-reentrant mutation gate.
            application.notificationPreferences.setDismissedEventKeys(emptySet())
            application.notificationDeferredActions.replay(application.notificationPreferences)
            assertTrue(EVENT_KEY in application.notificationPreferences.dismissedEventKeys())
            assertFalse(application.notificationDeferredActions.hasPendingActions())
        }

        withTimeout(5_000L) { receiverWork.await() }
        application.notificationDeferredActions.replay(application.notificationPreferences)

        assertTrue(EVENT_KEY in application.notificationPreferences.dismissedEventKeys())
        assertFalse(application.notificationDeferredActions.hasPendingActions())
    }

    @Test
    fun deliveryDuringStartupRecoveryIsQueuedAndReplayedExactlyOnce() = runBlocking {
        val identity = pendingDeliveryIdentity()

        ShiftAlarmReceiver().onReceive(context, boundaryIntent(identity))

        assertTrue(application.notificationDeferredActions.hasPendingActions())
        assertEquals(
            setOf(identity.opaqueKey),
            context.getSharedPreferences("notification_deferred_actions", Context.MODE_PRIVATE)
                .getStringSet("pending_deliveries", emptySet()),
        )
        val delivered = mutableListOf<NotificationBoundaryIdentity>()
        assertTrue(
            application.notificationDeferredActions.replayDeliveries { delivered += it },
        )
        assertTrue(
            application.notificationDeferredActions.replayDeliveries { delivered += it },
        )

        assertEquals(listOf(identity), delivered)
        assertFalse(application.notificationDeferredActions.hasPendingActions())
    }

    @Test
    fun transientDeliveryFailureRemainsQueuedUntilAReplaySucceeds() = runBlocking {
        val identity = pendingDeliveryIdentity()
        application.notificationDeferredActions.enqueueDelivery(identity.opaqueKey)
        var attempts = 0

        assertFalse(
            application.notificationDeferredActions.replayDeliveries {
                attempts += 1
                throw IOException("fallo transitorio ficticio")
            },
        )
        assertTrue(application.notificationDeferredActions.hasPendingActions())
        assertTrue(
            application.notificationDeferredActions.replayDeliveries {
                attempts += 1
                assertEquals(identity, it)
            },
        )

        assertEquals(2, attempts)
        assertFalse(application.notificationDeferredActions.hasPendingActions())
    }

    @Test
    fun boundedReplayRetriesATransientFailureAndClearsTheQueue() = runBlocking {
        val identity = pendingDeliveryIdentity()
        application.notificationDeferredActions.enqueueDelivery(identity.opaqueKey)
        var attempts = 0

        assertTrue(
            application.notificationDeferredActions.replayDeliveriesWithRetry(
                maxAttempts = 2,
                retryDelayMillis = 0L,
            ) {
                attempts += 1
                if (attempts == 1) throw IOException("fallo transitorio ficticio")
                assertEquals(identity, it)
            },
        )

        assertEquals(2, attempts)
        assertFalse(application.notificationDeferredActions.hasPendingActions())
    }

    private fun pendingDeliveryIdentity() = NotificationBoundaryIdentity(
        shiftId = DELIVERY_SHIFT_ID,
        type = NotificationBoundaryType.START,
        triggerAt = Instant.parse("2026-09-03T12:00:00Z"),
    )

    private fun boundaryIntent(identity: NotificationBoundaryIdentity): Intent =
        Intent(ShiftAlarmReceiver.ACTION_DELIVER_BOUNDARY).setData(
            Uri.Builder()
                .scheme("miguardia")
                .authority("shift-alarm")
                .appendQueryParameter("boundary", identity.opaqueKey)
                .build(),
        )

    private companion object {
        const val EVENT_KEY = "shift:11111111-1111-4111-8111-111111111111"
        val DELIVERY_SHIFT_ID = java.util.UUID.fromString("22222222-2222-4222-8222-222222222222")
    }
}
