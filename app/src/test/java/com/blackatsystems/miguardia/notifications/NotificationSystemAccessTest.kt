package com.blackatsystems.miguardia.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSystemAccessTest {
    @Test
    fun `requires runtime permission and unblocked app notifications`() {
        assertTrue(notificationAccessGranted(runtimePermissionGranted = true, appNotificationsEnabled = true))
        assertFalse(notificationAccessGranted(runtimePermissionGranted = false, appNotificationsEnabled = true))
        assertFalse(notificationAccessGranted(runtimePermissionGranted = true, appNotificationsEnabled = false))
        assertFalse(notificationAccessGranted(runtimePermissionGranted = false, appNotificationsEnabled = false))
    }
}
