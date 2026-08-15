package com.blackatsystems.miguardia.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackatsystems.miguardia.core.domain.model.Objective
import com.blackatsystems.miguardia.core.domain.model.SchedulePhoto
import com.blackatsystems.miguardia.core.domain.repository.ConflictingLocalWriteException
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SchedulePhotoPersistenceInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: LocalDataStore
    @Before fun setup() { context.deleteDatabase(DB); store = LocalDataStore.create(context, DB) }
    @After fun cleanup() { store.close(); context.deleteDatabase(DB) }

    @Test fun crudOrderMonthsAndReopen() = runBlocking {
        val second = photo(2, Instant.ofEpochSecond(2)); val first = photo(1, Instant.ofEpochSecond(1))
        store.schedulePhotos.insert(second); store.schedulePhotos.insert(first)
        assertEquals(listOf(first, second), store.schedulePhotos.observeForMonth(MONTH).first())
        store.close(); store = LocalDataStore.create(context, DB)
        assertEquals(first, store.schedulePhotos.getById(first.id))
        store.schedulePhotos.delete(first.id); assertNull(store.schedulePhotos.getById(first.id))
    }

    @Test fun storageKeyIsUnique() = runBlocking {
        val first = photo(1, Instant.EPOCH); store.schedulePhotos.insert(first)
        try { store.schedulePhotos.insert(photo(2, Instant.EPOCH).copy(storageKey = first.storageKey)); throw AssertionError("Expected conflict") }
        catch (_: ConflictingLocalWriteException) { }
        assertEquals(1, store.schedulePhotos.observeForMonth(MONTH).first().size)
    }

    @Test fun photoAndSnapshotsSurviveObjectiveDeletion() = runBlocking {
        val objectiveId = UUID.fromString("20000000-0000-0000-0000-000000000001")
        store.objectives.create(
            Objective(objectiveId, "Objetivo QA", "QA", null, null, true, Instant.EPOCH, Instant.EPOCH),
        )
        val linked = photo(1, Instant.EPOCH).copy(
            objectiveId = objectiveId,
            objectiveNameSnapshot = "Objetivo QA",
            objectiveAbbreviationSnapshot = "QA",
        )
        store.schedulePhotos.insert(linked)

        store.objectives.delete(objectiveId)

        assertNull(store.objectives.getById(objectiveId))
        assertEquals(linked, store.schedulePhotos.getById(linked.id))
    }

    private fun photo(n: Int, at: Instant): SchedulePhoto { val id=UUID.fromString("10000000-0000-0000-0000-${n.toString().padStart(12,'0')}"); return SchedulePhoto(id, MONTH, null, null, null, "$id.jpg", "image/jpeg", 10, 2, 3, at, at) }
    private companion object { const val DB="photo-persistence.db"; val MONTH=YearMonth.of(2026,8) }
}
