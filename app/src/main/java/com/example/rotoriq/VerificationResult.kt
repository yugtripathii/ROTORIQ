package com.example.rotoriq

/**
 * Shared result model for the RotorIQ verification pipeline.
 *
 * Produced by DecisionEngine, displayed by ResultScreen. The installation
 * rule itself lives in ReferenceConfig; nothing here decides anything.
 */

/** Overall outcome of one inspection. */
enum class Verdict {
    /** All four propellers reliably classified and the installation is valid. */
    PASS,

    /** Reliable classifications prove the installation is wrong. */
    INCORRECT,

    /** The images or classifications were not reliable enough to decide. */
    UNABLE_TO_VERIFY
}

/** Motor slot on the drone frame, viewed from above with the nose pointing up. */
enum class MotorPosition(val label: String, val shortLabel: String) {
    FRONT_LEFT("Front left", "FL"),
    FRONT_RIGHT("Front right", "FR"),
    REAR_LEFT("Rear left", "RL"),
    REAR_RIGHT("Rear right", "RR")
}

/**
 * Pluto propeller classification.
 *
 * These are the two blade chiralities. The Pluto propellers carry a moulded
 * letter near the blade root, and A/B here refer to that marking. Nothing in
 * this app assumes which diagonal carries which type.
 */
enum class PropellerType {
    A,
    B;

    val other: PropellerType
        get() = if (this == A) B else A
}

/**
 * What PropellerDetector found for a single motor slot.
 *
 * @param detected    classified type, or null when the classifier returned UNKNOWN.
 * @param confidence  classifier confidence in [0, 1]; 0 when unknown.
 * @param qualityNote why the image could not be classified, shown to the user
 *                    so they know which photo to retake.
 */
data class PropellerFinding(
    val position: MotorPosition,
    val detected: PropellerType?,
    val confidence: Float,
    val qualityNote: String? = null
) {
    init {
        require(confidence in 0f..1f) { "confidence must be in [0, 1], was $confidence" }
    }

    /** True when a type was detected with at least [minConfidence]. */
    fun isReliable(minConfidence: Float): Boolean =
        detected != null && confidence >= minConfidence
}

/**
 * Final outcome of one inspection.
 *
 * Safety rule enforced here: a PASS can only be constructed when the findings
 * cover all four slots, every one is reliably classified, and together they
 * form a valid installation per ReferenceConfig. Any code path that tries to
 * build a PASS without that evidence throws rather than showing an unsafe result.
 */
data class VerificationResult(
    val verdict: Verdict,
    val message: String,
    val findings: List<PropellerFinding> = emptyList()
) {
    init {
        if (verdict == Verdict.PASS) {
            require(
                findings.size == MotorPosition.entries.size &&
                        findings.map { it.position }.toSet() == MotorPosition.entries.toSet()
            ) { "PASS requires exactly one finding per motor slot" }
            require(findings.all { it.isReliable(ReferenceConfig.MIN_CONFIDENCE) }) {
                "PASS requires every propeller to be reliably classified"
            }
            require(ReferenceConfig.isValidInstallation(findings.associate { it.position to it.detected!! })) {
                "PASS requires a valid diagonal installation"
            }
        }
    }

    /** Slots whose findings contradict every valid configuration; used to highlight faults. */
    val problemPositions: Set<MotorPosition>
        get() = when (verdict) {
            Verdict.INCORRECT -> ReferenceConfig.DIAGONAL_PAIRS
                .flatMap { (a, b) -> listOf(a, b) }
                .filter { position ->
                    val pair = ReferenceConfig.partnerOf(position)
                    val mine = findings.firstOrNull { it.position == position }?.detected
                    val theirs = findings.firstOrNull { it.position == pair }?.detected
                    mine != null && theirs != null && mine != theirs
                }
                .toSet()
                .ifEmpty { findings.mapNotNull { it.detected?.let { _ -> it.position } }.toSet() }

            Verdict.UNABLE_TO_VERIFY ->
                findings.filterNot { it.isReliable(ReferenceConfig.MIN_CONFIDENCE) }
                    .map { it.position }.toSet() +
                        MotorPosition.entries.filter { p -> findings.none { it.position == p } }

            Verdict.PASS -> emptySet()
        }

    companion object {
        /** Every failure path: bad image, OpenCV error, detection failure, missing config. */
        fun unableToVerify(
            reason: String,
            findings: List<PropellerFinding> = emptyList()
        ): VerificationResult = VerificationResult(Verdict.UNABLE_TO_VERIFY, reason, findings)
    }
}
