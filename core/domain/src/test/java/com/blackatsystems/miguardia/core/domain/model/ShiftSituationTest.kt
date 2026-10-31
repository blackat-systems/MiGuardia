package com.blackatsystems.miguardia.core.domain.model

import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertThrows
import org.junit.Test

class ShiftSituationTest {
    private val now = Instant.parse("2026-09-14T12:00:00Z")

    @Test fun cancellationRequiresReasonActorAndNotice() {
        assertThrows(IllegalArgumentException::class.java) {
            situation(ShiftStatus.CANCELLED, reason = null, actor = CancellationActor.NOT_REPORTED, notice = now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            situation(ShiftStatus.CANCELLED, reason = "Cambio", actor = null, notice = now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            situation(ShiftStatus.CANCELLED, reason = "Cambio", actor = CancellationActor.EMPLOYER, notice = null)
        }
    }

    @Test fun absenceRejectsCancellationMetadataAndEvidence() {
        assertThrows(IllegalArgumentException::class.java) {
            situation(ShiftStatus.ABSENT, actor = CancellationActor.NOT_REPORTED)
        }
        assertThrows(IllegalArgumentException::class.java) {
            situation(ShiftStatus.ABSENT, evidence = evidence())
        }
    }

    @Test fun evidenceAcceptsTheExactPrivateStoreNamespace() {
        evidence()
        assertThrows(IllegalArgumentException::class.java) {
            evidence().copy(storageKey = "../certificado.jpg")
        }
        assertThrows(IllegalArgumentException::class.java) {
            evidence().copy(byteSize = CancellationEvidence.MAX_BYTES + 1)
        }
    }

    private fun situation(
        status: ShiftStatus,
        reason: String? = null,
        actor: CancellationActor? = null,
        notice: Instant? = null,
        evidence: CancellationEvidence? = null,
    ) = ShiftSituation(
        shiftId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
        status = status,
        reason = reason,
        privateNote = null,
        cancellationActor = actor,
        notifiedAt = notice,
        evidence = evidence,
        createdAt = now,
        updatedAt = now,
    )

    private fun evidence() = CancellationEvidence(
        storageKey = "cancel_11111111-1111-1111-1111-111111111111_ab12cd34.jpg",
        mimeType = "image/jpeg",
        byteSize = 3,
        pixelWidth = 1,
        pixelHeight = 1,
        sha256 = "a".repeat(64),
    )
}
