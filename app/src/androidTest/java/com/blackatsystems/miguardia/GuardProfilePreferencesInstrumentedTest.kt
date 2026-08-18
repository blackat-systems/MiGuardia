package com.blackatsystems.miguardia

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.profile.GuardProfileStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuardProfilePreferencesInstrumentedTest {
    @Test
    fun defaultsPersistReopenAndOptionalNameCanBeRemoved() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = File(context.cacheDir, "guard-profile-${UUID.randomUUID()}")
        check(directory.mkdirs())
        val file = File(directory, "profile.preferences_pb")

        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = GuardProfileStore(file, firstScope)
        assertNull(first.current().displayName)
        assertEquals("Inforce", first.current().company)
        first.save("  Persona ficticia  ", "  Empresa ficticia  ")
        firstScope.cancel()

        delay(100)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val second = GuardProfileStore(file, secondScope)
        assertEquals("Persona ficticia", second.current().displayName)
        assertEquals("Empresa ficticia", second.current().company)
        second.save("   ", "Empresa ficticia")
        secondScope.cancel()

        delay(100)
        val thirdScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val third = GuardProfileStore(file, thirdScope)
        assertNull(third.current().displayName)
        assertEquals("Empresa ficticia", third.current().company)
        thirdScope.cancel()
        directory.deleteRecursively()
        Unit
    }
}
