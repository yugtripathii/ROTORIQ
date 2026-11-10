package com.example.rotoriq

/**
 * Turns PropellerDetector findings into a VerificationResult.
 *
 * Decision order:
 *  1. Reference or input malformed (missing / duplicate slots)   -> UNABLE_TO_VERIFY
 *  2. Reliable findings contradict EVERY valid configuration     -> INCORRECT
 *  3. Any propeller not reliably classified                      -> UNABLE_TO_VERIFY
 *  4. All four reliable and the installation is valid            -> PASS
 *
 * A confirmed fault (2) is reported even when another propeller is unreadable,
 * because the drone is already known to be wrong. PASS is only ever reached
 * with complete, reliable evidence.
 */
class DecisionEngine(
    private val minConfidence: Float = ReferenceConfig.MIN_CONFIDENCE
) {

    fun evaluate(findings: List<PropellerFinding>): VerificationResult =
        try {
            decide(findings)
        } catch (e: Exception) {
            VerificationResult.unableToVerify(
                "Verification error: ${e.message ?: "unknown"}", findings
            )
        }

    private fun decide(findings: List<PropellerFinding>): VerificationResult {
        if (!ReferenceConfig.isConsistent() || minConfidence !in 0f..1f) {
            return VerificationResult.unableToVerify(
                "Reference configuration is missing or invalid.", findings
            )
        }

        val byPosition = findings.groupBy { it.position }
        if (byPosition.values.any { it.size > 1 }) {
            return VerificationResult.unableToVerify(
                "More than one result was reported for the same motor.", findings
            )
        }
        val missing = MotorPosition.entries.filter { it !in byPosition }
        if (missing.isNotEmpty()) {
            return VerificationResult.unableToVerify(
                "No photograph for: ${missing.joinToString { it.label }}.", findings
            )
        }

        // Only reliable classifications count as evidence.
        val reliable: Map<MotorPosition, PropellerType> = findings
            .filter { it.isReliable(minConfidence) }
            .associate { it.position to it.detected!! }

        val stillPossible = ReferenceConfig.VALID_CONFIGURATIONS.filter { configuration ->
            reliable.all { (position, type) -> configuration.getValue(position) == type }
        }

        if (stillPossible.isEmpty()) {
            return VerificationResult(Verdict.INCORRECT, describeFault(reliable), findings)
        }

        val unreadable = findings.filterNot { it.isReliable(minConfidence) }
        if (unreadable.isNotEmpty()) {
            return VerificationResult.unableToVerify(describeUnreadable(unreadable), findings)
        }

        val summary = ReferenceConfig.DIAGONAL_PAIRS.joinToString("; ") { (a, b) ->
            "${a.shortLabel}+${b.shortLabel} = type ${reliable.getValue(a)}"
        }
        return VerificationResult(
            Verdict.PASS,
            "Propellers installed correctly ($summary).",
            findings
        )
    }

    /** Explains why reliable findings rule out both valid configurations. */
    private fun describeFault(reliable: Map<MotorPosition, PropellerType>): String {
        val reasons = mutableListOf<String>()
        val diagonalTypes = mutableListOf<PropellerType>()

        for ((a, b) in ReferenceConfig.DIAGONAL_PAIRS) {
            val first = reliable[a]
            val second = reliable[b]
            when {
                first != null && second != null && first != second ->
                    reasons += "${a.label} (type $first) and ${b.label} (type $second) " +
                            "are diagonally opposite and must match"

                first != null -> diagonalTypes += first
                second != null -> diagonalTypes += second
            }
        }
        if (reasons.isEmpty() && diagonalTypes.size == 2 && diagonalTypes[0] == diagonalTypes[1]) {
            reasons += "both diagonals use type ${diagonalTypes[0]}; " +
                    "one diagonal must use type ${diagonalTypes[0].other}"
        }
        if (reasons.isEmpty()) {
            reasons += "the detected types do not match any valid installation"
        }

        return "Do not fly. ${reasons.joinToString("; ")}."
    }

    /** Names the photographs that need retaking, with the detector's own reason. */
    private fun describeUnreadable(unreadable: List<PropellerFinding>): String {
        val details = unreadable.joinToString("  ") { finding ->
            val note = finding.qualityNote ?: "not readable"
            "${finding.position.label}: $note"
        }
        return "Could not read ${unreadable.size} of ${MotorPosition.entries.size} " +
                "propellers. $details"
    }
}



