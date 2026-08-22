package com.blackatsystems.miguardia.core.domain.work

import com.blackatsystems.miguardia.core.domain.model.Objective
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkCatalogValidationTest {
    @Test
    fun workTextUsesNfkcAndCollapsesCommonAndNonBreakingSpaces() {
        assertEquals("Clínica Norte", normalizeRequiredWorkText("  Cli\u0301nica\u00A0  Norte  ", "Nombre"))
        assertNull(normalizeOptionalWorkText(" \u00A0 "))
    }

    @Test
    fun canonicalTypeKeyIsCaseIndependentAndHandlesGermanSharpS() {
        assertEquals(canonicalWorkTypeNameKey("capacitación"), canonicalWorkTypeNameKey("CAPACITACIÓN"))
        assertEquals(canonicalWorkTypeNameKey("straße"), canonicalWorkTypeNameKey("STRASSE"))
        assertEquals(canonicalWorkTypeNameKey("Cli\u0301nica"), canonicalWorkTypeNameKey("CLÍNICA"))
        assertEquals(canonicalWorkTypeNameKey("Guardia\u00A0  habitual"), canonicalWorkTypeNameKey("guardia habitual"))
    }

    @Test
    fun newAbbreviationNeedsThreeToFiveCharactersAndIsUppercase() {
        assertEquals("HNO", normalizeNewWorkPlaceAbbreviation(" hno "))
        assertThrows(IllegalArgumentException::class.java) {
            normalizeNewWorkPlaceAbbreviation("hn")
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeNewWorkPlaceAbbreviation("HOSPIT")
        }
    }

    @Test
    fun historicalTwoCharacterAbbreviationMayRemainButCannotChangeToAnotherTwo() {
        assertEquals("HN", normalizeUpdatedWorkPlaceAbbreviation(" hn ", "HN"))
        assertThrows(IllegalArgumentException::class.java) {
            normalizeUpdatedWorkPlaceAbbreviation("HC", "HN")
        }
        assertEquals("HCN", normalizeUpdatedWorkPlaceAbbreviation("hcn", "HN"))
    }

    @Test
    fun objectiveNormalizationPreservesHistoricalTwoCharacterAbbreviationOnOtherEdits() {
        val previous = objective(abbreviation = "HN", fullName = "Hospital Norte", isActive = false)
        val edited = previous.copy(
            fullName = "  Hospital\u00A0Central  ",
            address = "  Calle 1 ",
            note = "   ",
            isActive = true,
            updatedAt = NOW.plusSeconds(1),
        ).normalizedForV2Update(previous)

        assertEquals("Hospital Central", edited.fullName)
        assertEquals("HN", edited.abbreviation)
        assertEquals("Calle 1", edited.address)
        assertNull(edited.note)
        assertFalse(edited.isActive)
    }

    @Test
    fun visibleTypeNameKeepsChosenWritingWhileCanonicalKeyIsStable() {
        val type = WorkType.create(
            id = TYPE_ID,
            timelineId = TIMELINE_ID,
            sector = WorkSector.MEDICINE,
            rawName = "  Consultorio\u00A0 externo ",
            timestamp = NOW,
        )

        assertEquals("Consultorio externo", type.name)
        assertEquals("CONSULTORIO EXTERNO", type.normalizedNameKey)
        assertEquals(WorkTypeBehavior.ACTIVE_WORK, type.behavior)
    }

    @Test
    fun recentLimitAcceptsOnlyOneToFive() {
        (1..5).forEach(::requireRecentWorkTemplateLimit)
        assertThrows(IllegalArgumentException::class.java) { requireRecentWorkTemplateLimit(0) }
        assertThrows(IllegalArgumentException::class.java) { requireRecentWorkTemplateLimit(6) }
    }

    private fun objective(
        abbreviation: String,
        fullName: String,
        isActive: Boolean = true,
    ) = Objective(
        id = OBJECTIVE_ID,
        fullName = fullName,
        abbreviation = abbreviation,
        address = null,
        note = null,
        isActive = isActive,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-21T12:00:00Z")
        val TIMELINE_ID: UUID = UUID.fromString("81000000-0000-0000-0000-000000000001")
        val OBJECTIVE_ID: UUID = UUID.fromString("81000000-0000-0000-0000-000000000002")
        val TYPE_ID: UUID = UUID.fromString("81000000-0000-0000-0000-000000000003")
    }
}
