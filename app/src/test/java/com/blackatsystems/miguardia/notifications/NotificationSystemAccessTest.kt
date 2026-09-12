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
        assertTrue(NotificationSystemAccessState(true, false, true).notificationAccessGranted)
        assertFalse(NotificationSystemAccessState(true, false, false).notificationAccessGranted)
    }

    @Test
    fun `exact scheduling requires both the user preference and Android access`() {
        assertTrue(shouldScheduleExactAlarm(preferExact = true, exactAlarmAccessGranted = true))
        assertFalse(shouldScheduleExactAlarm(preferExact = true, exactAlarmAccessGranted = false))
        assertFalse(shouldScheduleExactAlarm(preferExact = false, exactAlarmAccessGranted = true))
        assertFalse(shouldScheduleExactAlarm(preferExact = false, exactAlarmAccessGranted = false))
    }
}
