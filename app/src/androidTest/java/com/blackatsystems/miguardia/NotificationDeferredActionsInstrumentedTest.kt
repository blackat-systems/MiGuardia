package com.blackatsystems.miguardia

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.notifications.ShiftAlarmReceiver
import com.blackatsystems.miguardia.notifications.processQueuedDismissalsUnderMutationGate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
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
                processQueuedDismissalsUnderMutationGate(application)
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

    private companion object {
        const val EVENT_KEY = "shift:11111111-1111-4111-8111-111111111111"
    }
}
