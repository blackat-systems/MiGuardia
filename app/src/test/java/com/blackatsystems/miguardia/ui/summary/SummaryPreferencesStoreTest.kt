package com.blackatsystems.miguardia.ui.summary

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.blackatsystems.miguardia.core.domain.summary.SummaryOptionalFamily
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryPreferencesStoreTest {
    @Test
    fun invariantRejectsAnyRepeatedFamilyEvenWhenTheSetLooksComplete() {
        assertThrows(IllegalArgumentException::class.java) {
            SummaryPreferences(
                orderedFamilies = SummaryOptionalFamily.entries + SummaryOptionalFamily.NIGHTS,
            )
        }
    }

    @Test
    fun defaultsAreStableAndFirstVisitExplanationStartsPending() {
        val value = normalizeSummaryPreferences(null, null, null)

        assertEquals(SummaryOptionalFamily.entries, value.orderedFamilies)
        assertTrue(value.hiddenFamilies.isEmpty())
        assertFalse(value.introSeen)
    }

    @Test
    fun missingDuplicateUnknownAndFutureValuesNormalizeWithoutLoss() {
        val value = normalizeSummaryPreferences(
            orderStorage = "HOLIDAYS|UNKNOWN_FUTURE|HOLIDAYS|NIGHTS",
            hiddenStorage = setOf("WEEKENDS", "UNKNOWN_FUTURE"),
            introSeen = true,
        )

        assertEquals(SummaryOptionalFamily.HOLIDAYS, value.orderedFamilies[0])
        assertEquals(SummaryOptionalFamily.NIGHTS, value.orderedFamilies[1])
        assertEquals(SummaryOptionalFamily.entries.toSet(), value.orderedFamilies.toSet())
        assertEquals(value.orderedFamilies.size, value.orderedFamilies.distinct().size)
        assertEquals(setOf(SummaryOptionalFamily.WEEKENDS), value.hiddenFamilies)
        assertTrue(value.introSeen)
    }

    @Test
    fun visibilityOrderIntroAndTwoConsecutiveWritesPersistAfterReopen() = runBlocking {
        val root = Files.createTempDirectory("summary-preferences-").toFile()
        val file = root.resolve("qa-summary.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = SummaryPreferencesStore(file, firstScope)

        first.setVisible(SummaryOptionalFamily.NIGHTS, false)
        first.setVisible(SummaryOptionalFamily.NIGHTS, true)
        first.setVisible(SummaryOptionalFamily.HOLIDAYS, false)
        first.move(SummaryOptionalFamily.SITUATIONS, -1)
        first.move(SummaryOptionalFamily.SITUATIONS, -1)
        first.markIntroSeen()
        val beforeClose = first.current()

        firstScope.coroutineContext[Job]?.cancelAndJoin()
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val reopened = SummaryPreferencesStore(file, secondScope)
        val afterReopen = reopened.current()

        assertEquals(beforeClose, afterReopen)
        assertTrue(afterReopen.isVisible(SummaryOptionalFamily.NIGHTS))
        assertFalse(afterReopen.isVisible(SummaryOptionalFamily.HOLIDAYS))
        assertEquals(SummaryOptionalFamily.SITUATIONS, afterReopen.orderedFamilies[5])
        assertTrue(afterReopen.introSeen)
        assertEquals(SummaryOptionalFamily.entries.size, afterReopen.orderedFamilies.distinct().size)
        secondScope.coroutineContext[Job]?.cancelAndJoin()
        Unit
    }

    @Test
    fun movingAtEitherBoundaryNeverDuplicatesOrDropsFamilies() = runBlocking {
        val root = Files.createTempDirectory("summary-boundaries-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = SummaryPreferencesStore(root.resolve("isolated.preferences_pb"), scope)

        store.move(SummaryOptionalFamily.NIGHTS, -1)
        store.move(SummaryOptionalFamily.SITUATIONS, 1)
        val result = store.current()

        assertEquals(SummaryOptionalFamily.entries, result.orderedFamilies)
        assertEquals(SummaryOptionalFamily.entries.size, result.orderedFamilies.toSet().size)
        scope.coroutineContext[Job]?.cancelAndJoin()
        Unit
    }

    @Test
    fun recoverableReadFailureEmitsSafeDefaultsWithoutWritingAnything() = runBlocking {
        var updateAttempts = 0
        val failing = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("fallo recuperable ficticio") }

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                updateAttempts++
                return transform(emptyPreferences())
            }
        }

        val result = SummaryPreferencesStore(failing).current()

        assertEquals(SummaryPreferences(), result)
        assertEquals(0, updateAttempts)
    }
}
