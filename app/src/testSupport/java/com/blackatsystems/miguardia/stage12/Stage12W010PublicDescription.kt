package com.blackatsystems.miguardia.stage12

/** Narrow acceptance rule for an observed public getter, never a class/visibility exemption. */
internal object Stage12W010PublicDescription {
    fun boundedWindow(beforeStart: Long, beforeEnd: Long, afterStart: Long, afterEnd: Long): Boolean =
        beforeStart >= 0L && beforeStart <= beforeEnd && beforeEnd <= afterStart && afterStart <= afterEnd &&
            afterEnd - beforeStart in 0L..999L

    enum class Decision { VERIFIED_PUBLIC_CONTROL, REJECT_PRIVATE_LITERAL, REJECT_SCOPE_OR_STATE,
        REJECT_DIFFERENT_VALUE, UNKNOWN_CONTROL, UNKNOWN_TIME_OR_CONFIGURATION }
    data class Evidence(
        val field: Stage12W010Observation.Field,
        val exactCountdownClass: Boolean,
        val hiddenNeutralState: Boolean,
        val forbiddenLiteral: Boolean,
        val cleanControlKnown: Boolean,
        val oldDetectorRejectsCleanControl: Boolean,
        val publicMarkerIgnoredForThisField: Boolean,
        val boundedMonotonicWindow: Boolean,
        val sameConfigurationAndLocales: Boolean,
        val controlsExactlyEqual: Boolean,
        val candidateExactlyEqualsBoth: Boolean,
    ) {
        fun safe(decision: Decision): String = "W010_PUBLIC_DESCRIPTION_CONTROL;decision=${decision.name}" +
            ";field=${field.name};exact_countdown_class=$exactCountdownClass;hidden_neutral_state=$hiddenNeutralState" +
            ";forbidden_literal=$forbiddenLiteral;control_known=$cleanControlKnown" +
            ";old_detector_rejects_clean_control=$oldDetectorRejectsCleanControl" +
            ";marker_ignored_for_field=$publicMarkerIgnoredForThisField;bounded_window=$boundedMonotonicWindow" +
            ";same_configuration_locales=$sameConfigurationAndLocales;controls_equal=$controlsExactlyEqual" +
            ";candidate_equals_both=$candidateExactlyEqualsBoth"
    }

    fun decide(e: Evidence): Decision = when {
        e.forbiddenLiteral -> Decision.REJECT_PRIVATE_LITERAL
        e.field !in setOf(Stage12W010Observation.Field.VIEW_DESCRIPTION, Stage12W010Observation.Field.NODE_DESCRIPTION) ||
            !e.exactCountdownClass || !e.hiddenNeutralState -> Decision.REJECT_SCOPE_OR_STATE
        !e.cleanControlKnown || !e.oldDetectorRejectsCleanControl || !e.publicMarkerIgnoredForThisField -> Decision.UNKNOWN_CONTROL
        !e.boundedMonotonicWindow || !e.sameConfigurationAndLocales || !e.controlsExactlyEqual -> Decision.UNKNOWN_TIME_OR_CONFIGURATION
        !e.candidateExactlyEqualsBoth -> Decision.REJECT_DIFFERENT_VALUE
        else -> Decision.VERIFIED_PUBLIC_CONTROL
    }

    /** The literal detector stays strict. This additional acceptance needs a per-reading attestation. */
    fun requireText(text: String, fixture: Stage12W010Fixture, evidence: Evidence?): Boolean {
        try { Stage12W010Oracle.requireNoSecretText(listOf(text), fixture); return false }
        catch (original: IllegalStateException) {
            if (original.message != "W010_APPLIED_SECRET_TEXT") throw original
            // Recheck the literal independently: even a malformed attestation cannot waive it.
            if (fixture.forbiddenText.any { text.contains(it, ignoreCase = true) } ||
                evidence == null || decide(evidence) != Decision.VERIFIED_PUBLIC_CONTROL) throw original
            return true
        }
    }
}
