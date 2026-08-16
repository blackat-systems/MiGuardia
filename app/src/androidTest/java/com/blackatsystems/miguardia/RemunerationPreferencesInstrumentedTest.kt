package com.blackatsystems.miguardia

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.remuneration.RemunerationPreferencesStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemunerationPreferencesInstrumentedTest {
    @Test
    fun defaultsToZeroAndPersistsOnlyInAnIsolatedDataStore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = File(context.cacheDir, "remuneration-preferences-${UUID.randomUUID()}")
        check(directory.mkdirs())
        val file = File(directory, "preferences.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = RemunerationPreferencesStore(file, firstScope)
        assertEquals(0, first.preferences.first().seniorityYears)
        first.setSeniorityYears(7)

        firstScope.cancel()
        delay(100)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val saved = RemunerationPreferencesStore(file, secondScope)
        assertEquals(7, saved.preferences.first().seniorityYears)
        secondScope.cancel()
        directory.deleteRecursively()
        Unit
    }

    @Test
    fun rejectsSeniorityOutsideTheSupportedRange() {
        val store = RemunerationPreferencesStore(
            ApplicationProvider.getApplicationContext(),
            "remuneration-${UUID.randomUUID()}.preferences_pb",
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.setSeniorityYears(61) }
        }
    }
}
