package com.blackatsystems.miguardia.stage12

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import com.blackatsystems.miguardia.stage12.Stage12W010PublicDescription.Evidence
import com.blackatsystems.miguardia.stage12.Stage12W010PublicDescription.Decision

class Stage12W010PublicDescriptionTest {
    private val fixture = Stage12W010Fixture(UUID.fromString("6714cf36-90b2-4dc5-9af9-f423f02a429a"))
    private fun good(field: Stage12W010Observation.Field = Stage12W010Observation.Field.VIEW_DESCRIPTION) =
        Evidence(field, true, true, false, true, true, true, true, true, true, true)
    private fun rejected(text: String, evidence: Evidence?) {
        val failure = assertThrows(IllegalStateException::class.java) {
            Stage12W010PublicDescription.requireText(text, fixture, evidence)
        }
        assertEquals("W010_APPLIED_SECRET_TEXT", failure.message)
        assertFalse(failure.message.orEmpty().contains(text))
    }

    @Test fun oldLiteralDetectorStillRejectsDigitsAndNoEvidenceCannotWaiveThem() {
        assertThrows(IllegalStateException::class.java) { Stage12W010Oracle.requireNoSecretText(listOf("123 segundos"), fixture) }
        rejected("123 segundos", null)
        rejected("123 segundos", good().copy(cleanControlKnown = false))
    }

    @Test fun exactAttestationIsSpecificToEachDescriptionProperty() {
        for (field in listOf(Stage12W010Observation.Field.VIEW_DESCRIPTION, Stage12W010Observation.Field.NODE_DESCRIPTION)) {
            val e = good(field)
            assertEquals(Decision.VERIFIED_PUBLIC_CONTROL, Stage12W010PublicDescription.decide(e))
            assertTrue(Stage12W010PublicDescription.requireText("123 segundos", fixture, e))
        }
    }

    @Test fun actualPrivateLiteralIsRejectedEvenIfAnAttestationFalselyClaimsItIsAbsent() {
        rejected(fixture.institution, good())
        rejected("123 segundos " + fixture.sourceCanary, good())
        assertEquals(Decision.REJECT_PRIVATE_LITERAL, Stage12W010PublicDescription.decide(good().copy(forbiddenLiteral = true)))
    }

    @Test fun noOtherTextOrAccessibilityFieldGetsANumericExemption() {
        for (field in Stage12W010Observation.Field.entries.filter { it !in setOf(
            Stage12W010Observation.Field.VIEW_DESCRIPTION, Stage12W010Observation.Field.NODE_DESCRIPTION) }) {
            val e = good(field)
            assertEquals(Decision.REJECT_SCOPE_OR_STATE, Stage12W010PublicDescription.decide(e))
            rejected("123 segundos", e)
        }
    }

    @Test fun wrongClassOrAnyNonNeutralCandidateStateRejects() {
        for (e in listOf(good().copy(exactCountdownClass = false), good().copy(hiddenNeutralState = false))) {
            assertEquals(Decision.REJECT_SCOPE_OR_STATE, Stage12W010PublicDescription.decide(e))
            rejected("123 segundos", e)
        }
    }

    @Test fun missingControlMarkerOrOldDetectorWitnessStaysUnknown() {
        for (e in listOf(good().copy(cleanControlKnown = false), good().copy(oldDetectorRejectsCleanControl = false),
            good().copy(publicMarkerIgnoredForThisField = false))) {
            assertEquals(Decision.UNKNOWN_CONTROL, Stage12W010PublicDescription.decide(e))
            rejected("123 segundos", e)
        }
    }

    @Test fun oneMatchingExtremeOrTimeOrConfigurationChangeNeverApproves() {
        for (e in listOf(good().copy(controlsExactlyEqual = false), good().copy(boundedMonotonicWindow = false),
            good().copy(sameConfigurationAndLocales = false))) {
            assertEquals(Decision.UNKNOWN_TIME_OR_CONFIGURATION, Stage12W010PublicDescription.decide(e))
            rejected("123 segundos", e)
        }
        val mismatch = good().copy(candidateExactlyEqualsBoth = false)
        assertEquals(Decision.REJECT_DIFFERENT_VALUE, Stage12W010PublicDescription.decide(mismatch))
        rejected("456 segundos", mismatch)
    }

    @Test fun monotonicBracketIncludesItsWholeConstructionAndRefusesOneSecondOrBackwardTime() {
        assertTrue(Stage12W010PublicDescription.boundedWindow(0, 1, 2, 999))
        assertTrue(Stage12W010PublicDescription.boundedWindow(10, 10, 10, 10))
        assertFalse(Stage12W010PublicDescription.boundedWindow(0, 1, 2, 1000))
        assertFalse(Stage12W010PublicDescription.boundedWindow(-1, 1, 2, 3))
        assertFalse(Stage12W010PublicDescription.boundedWindow(1, 0, 2, 3))
        assertFalse(Stage12W010PublicDescription.boundedWindow(0, 2, 1, 3))
        assertFalse(Stage12W010PublicDescription.boundedWindow(0, 1, 3, 2))
    }

    @Test fun ordinaryPublicTextStillUsesTheStrictPathAndDiagnosticsContainOnlySafeFacts() {
        assertFalse(Stage12W010PublicDescription.requireText("MIGUARDIA", fixture, null))
        val e = good().copy(candidateExactlyEqualsBoth = false)
        val safe = e.safe(Stage12W010PublicDescription.decide(e))
        assertTrue(safe.contains("decision=REJECT_DIFFERENT_VALUE"))
        assertFalse(safe.contains("123 segundos")); assertFalse(safe.contains(fixture.institution))
        assertFalse(safe.contains("base=")); assertFalse(safe.contains("elapsed="))
    }
}
