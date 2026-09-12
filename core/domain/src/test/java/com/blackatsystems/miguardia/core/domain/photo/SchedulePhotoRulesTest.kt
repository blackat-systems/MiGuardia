package com.blackatsystems.miguardia.core.domain.photo

import com.blackatsystems.miguardia.core.domain.model.SchedulePhoto
import com.blackatsystems.miguardia.core.domain.repository.InvalidLocalDataException
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SchedulePhotoRulesTest {
    @Test fun validPhotoKeepsHistoricalObjectiveSnapshots() {
        val photo = photo(objectiveId = UUID.randomUUID(), name = "Objetivo QA", abbreviation = "QA")
        assertEquals("Objetivo QA", photo.validated().objectiveNameSnapshot)
    }

    @Test fun invalidStorageKeyIsRejected() {
        assertThrows(InvalidLocalDataException::class.java) { photo().copy(storageKey = "../foto.jpg").validated() }
        assertThrows(InvalidLocalDataException::class.java) {
            photo().copy(storageKey = "------------------------------------.jpg").validated()
        }
    }

    @Test fun versionedReplacementStorageKeyIsAccepted() {
        val key = "10000000-0000-0000-0000-000000000001_a1b2c3d4.webp"
        assertEquals(key, photo().copy(storageKey = key).validated().storageKey)
    }

    @Test fun invalidDimensionsAndSizeAreRejected() {
        assertThrows(InvalidLocalDataException::class.java) { photo().copy(byteSize = 0).validated() }
        assertThrows(InvalidLocalDataException::class.java) { photo().copy(pixelWidth = 0).validated() }
    }

    @Test fun objectiveAssociationRequiresBothSnapshots() {
        assertThrows(InvalidLocalDataException::class.java) { photo(objectiveId = UUID.randomUUID()).validated() }
    }

    private fun photo(objectiveId: UUID? = null, name: String? = null, abbreviation: String? = null) = SchedulePhoto(
        UUID.fromString("10000000-0000-0000-0000-000000000001"), YearMonth.of(2026, 8), objectiveId,
        name, abbreviation, "10000000-0000-0000-0000-000000000001.jpg", "image/jpeg", 10, 2, 3,
        Instant.EPOCH, Instant.EPOCH,
    )
}
