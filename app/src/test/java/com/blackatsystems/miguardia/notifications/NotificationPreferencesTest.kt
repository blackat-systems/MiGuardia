package com.blackatsystems.miguardia.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPreferencesTest {
    @Test
    fun defaultConfigurationRemainsEssentialForExistingUsers() {
        assertEquals(NotificationRhythm.ESSENTIAL, NotificationPreferences().rhythm())
    }

    @Test
    fun eachPresetAndAnyManualDeviationAreDerivedWithoutPersistingAnotherField() {
        val accompanied = NotificationPreferences(globalReminderLeadMinutes = listOf(120L, 720L))
        val discreet = NotificationPreferences(
            persistentWhileActive = false,
            privacy = NotificationPrivacy.REDUCED,
            attentionMode = NotificationAttentionMode.SILENT,
        )
        val custom = NotificationPreferences(
            globalReminderLeadMinutes = listOf(360L),
            attentionMode = NotificationAttentionMode.VIBRATION_ONLY,
        )

        assertEquals(NotificationRhythm.ACCOMPANIED, accompanied.rhythm())
        assertEquals(NotificationRhythm.DISCREET, discreet.rhythm())
        assertEquals(NotificationRhythm.CUSTOM, custom.rhythm())
    }
}
