package com.blackatsystems.miguardia.backup

import com.blackatsystems.miguardia.core.domain.backup.BackupPreference
import com.blackatsystems.miguardia.core.domain.backup.BackupPreferenceType
import com.blackatsystems.miguardia.core.domain.backup.InvalidBackupException
import com.blackatsystems.miguardia.core.domain.backup.MiGuardiaBackupContract
import com.blackatsystems.miguardia.notifications.NotificationPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

/**
 * Qué preferencias admite una Copia y cómo se completa una Copia anterior a las tres opciones del
 * aviso permanente (etapa 15).
 */
class PortablePreferenceCompletionTest {
    @Test
    fun currentCopyWithTheThreePermanentNotificationChoicesIsReturnedAsItCame() {
        val current = (earlierCopy() + addedChoices(alwaysShowNext = true, lockScreen = false, countdown = false))
            .sortedBy(BackupPreference::key)

        assertEquals(20, current.size)
        assertSame(current, completePortablePreferences(current))
    }

    @Test
    fun earlierCopyWithSeventeenPreferencesIsAcceptedAndCompletedWithFactoryChoices() {
        val earlier = earlierCopy()
        assertEquals(17, earlier.size)

        val completed = completePortablePreferences(earlier)

        assertEquals(20, completed.size)
        assertEquals(completed.map(BackupPreference::key).sorted(), completed.map(BackupPreference::key))
        assertEquals(earlier, completed.filter { it.key !in ADDED_KEYS })
        val factory = NotificationPreferences()
        assertEquals(
            addedChoices(factory.alwaysShowNext, factory.showOnLockScreen, factory.countdownWhileActive),
            completed.filter { it.key in ADDED_KEYS },
        )
        assertEquals(
            addedChoices(alwaysShowNext = false, lockScreen = true, countdown = true),
            completed.filter { it.key in ADDED_KEYS },
        )
    }

    @Test
    fun everySubsetOfTheAddedChoicesIsAcceptedAndKeepsWhatTheCopyCarries() {
        val carried = addedChoices(alwaysShowNext = true, lockScreen = false, countdown = false)
        val factory = addedChoices(alwaysShowNext = false, lockScreen = true, countdown = true)
        for (mask in 0 until 8) {
            val present = carried.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            val incoming = (earlierCopy() + present).sortedBy(BackupPreference::key)

            val completed = completePortablePreferences(incoming)

            assertEquals(20, completed.size)
            val expectedAdded = carried.indices.map { index ->
                if (mask and (1 shl index) != 0) carried[index] else factory[index]
            }
            assertEquals(expectedAdded, completed.filter { it.key in ADDED_KEYS })
            assertEquals(earlierCopy(), completed.filter { it.key !in ADDED_KEYS })
            assertEquals(completed, completePortablePreferences(completed))
        }
    }

    @Test
    fun unknownMissingRepeatedOrUnorderedKeysAreRejected() {
        val earlier = earlierCopy()
        val current = completePortablePreferences(earlier)
        val unknown = BackupPreference("unknown.device_state", BackupPreferenceType.TEXT, listOf("forbidden"))
        val lookalike = BackupPreference(
            "notifications.always_show_next_event",
            BackupPreferenceType.BOOLEAN,
            listOf("true"),
        )
        listOf(
            (earlier + unknown).sortedBy(BackupPreference::key),
            (current + unknown).sortedBy(BackupPreference::key),
            (current + lookalike).sortedBy(BackupPreference::key),
            earlier.drop(1),
            current.filter { it.key != "notifications.enabled" },
            earlier + earlier.last(),
            (current + current.first { it.key in ADDED_KEYS }).sortedBy(BackupPreference::key),
            earlier.reversed(),
            current.reversed(),
            emptyList(),
        ).forEach { invalid ->
            try {
                completePortablePreferences(invalid)
                fail("Debe rechazar ${invalid.map(BackupPreference::key)}")
            } catch (_: InvalidBackupException) {
            }
        }
    }

    /** Las 17 claves que escribe la versión pública, con valores ficticios. */
    private fun earlierCopy(): List<BackupPreference> = listOf(
        preference("profile.display_name", BackupPreferenceType.TEXT, "Persona ficticia"),
        preference("display.theme", BackupPreferenceType.TEXT, "DARK"),
        preference("display.zoom_percent", BackupPreferenceType.LONG, "100"),
        preference("summary.ordered_families", BackupPreferenceType.TEXT_LIST, "HOLIDAYS", "NIGHTS"),
        preference("summary.hidden_families", BackupPreferenceType.TEXT_LIST),
        preference("summary.intro_seen", BackupPreferenceType.BOOLEAN, "true"),
        preference("notifications.enabled", BackupPreferenceType.BOOLEAN, "true"),
        preference("notifications.precise_timing", BackupPreferenceType.BOOLEAN, "false"),
        preference("notifications.reminder_minutes", BackupPreferenceType.TEXT_LIST, "120", "720"),
        preference("notifications.persistent", BackupPreferenceType.BOOLEAN, "true"),
        preference("notifications.privacy", BackupPreferenceType.TEXT, "REDUCED"),
        preference("notifications.attention", BackupPreferenceType.TEXT, "VIBRATION_ONLY"),
        preference(MiGuardiaBackupContract.DISMISSED_EVENT_KEYS_PREFERENCE, BackupPreferenceType.TEXT_LIST),
        preference("weather.enabled", BackupPreferenceType.BOOLEAN, "false"),
        preference("weather.unit", BackupPreferenceType.TEXT, "CELSIUS"),
        preference("weather.include_notifications", BackupPreferenceType.BOOLEAN, "false"),
        preference("weather.explanation_accepted", BackupPreferenceType.BOOLEAN, "false"),
    ).sortedBy(BackupPreference::key)

    /** En el orden en que quedan dentro de la Copia (alfabético por clave). */
    private fun addedChoices(alwaysShowNext: Boolean, lockScreen: Boolean, countdown: Boolean) = listOf(
        preference("notifications.always_show_next", BackupPreferenceType.BOOLEAN, alwaysShowNext.toString()),
        preference("notifications.countdown_while_active", BackupPreferenceType.BOOLEAN, countdown.toString()),
        preference("notifications.show_on_lock_screen", BackupPreferenceType.BOOLEAN, lockScreen.toString()),
    )

    private fun preference(key: String, type: BackupPreferenceType, vararg values: String) =
        BackupPreference(key, type, values.toList())

    private companion object {
        val ADDED_KEYS = setOf(
            "notifications.always_show_next",
            "notifications.countdown_while_active",
            "notifications.show_on_lock_screen",
        )
    }
}
