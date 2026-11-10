package com.example.rotoriq

/**
 * Reference data for the Pluto drone: the installation rule, the image-quality
 * gates, the reference-dataset location, and every matching threshold.
 *
 * Installation rule (both orientations valid; A/B is NOT tied to a diagonal):
 *   Front left  == Rear right    (diagonal 1)
 *   Front right == Rear left     (diagonal 2)
 *   diagonal 1 type != diagonal 2 type
 */
object ReferenceConfig {

    // ---- Installation rule -------------------------------------------------

    /** Pairs of slots that must carry the same propeller type. */
    val DIAGONAL_PAIRS: List<Pair<MotorPosition, MotorPosition>> = listOf(
        MotorPosition.FRONT_LEFT to MotorPosition.REAR_RIGHT,
        MotorPosition.FRONT_RIGHT to MotorPosition.REAR_LEFT
    )

    /** The slot diagonally opposite [position]. */
    fun partnerOf(position: MotorPosition): MotorPosition =
        DIAGONAL_PAIRS.first { it.first == position || it.second == position }
            .let { if (it.first == position) it.second else it.first }

    /** Single source of truth for a correct installation. False if any slot is missing. */
    fun isValidInstallation(types: Map<MotorPosition, PropellerType>): Boolean {
        if (!types.keys.containsAll(MotorPosition.entries)) return false
        val perDiagonal = DIAGONAL_PAIRS.map { (first, second) ->
            val a = types.getValue(first)
            val b = types.getValue(second)
            if (a != b) return false                 // diagonal internally inconsistent
            a
        }
        return perDiagonal.toSet().size == DIAGONAL_PAIRS.size   // diagonals must differ
    }

    /**
     * Every valid configuration, derived from [isValidInstallation] by testing all
     * 16 possible A/B assignments. Nothing is hardcoded; this yields exactly two.
     */
    val VALID_CONFIGURATIONS: List<Map<MotorPosition, PropellerType>> by lazy {
        allAssignments().filter { isValidInstallation(it) }
    }

    /** Sanity check of the reference itself; DecisionEngine refuses to run if this fails. */
    fun isConsistent(): Boolean =
        MIN_CONFIDENCE in 0f..1f &&
                DIAGONAL_PAIRS.flatMap { listOf(it.first, it.second) }.toSet() ==
                MotorPosition.entries.toSet() &&
                VALID_CONFIGURATIONS.size == 2

    private fun allAssignments(): List<Map<MotorPosition, PropellerType>> =
        MotorPosition.entries.fold(listOf(emptyMap())) { acc, position ->
            acc.flatMap { partial -> PropellerType.entries.map { partial + (position to it) } }
        }

    // ---- Image-quality gates ----------------------------------------------
    // TUNABLE. A photo failing any of these is reported UNKNOWN, never guessed.

    /** Laplacian-variance floor. Below this the photo is too blurry to use. */
    const val MIN_SHARPNESS: Double = 90.0

    /** Propeller must occupy at least this fraction of the frame (camera too far). */
    const val MIN_PROP_AREA_FRACTION: Double = 0.030

    /** ...and at most this much (camera too close, blades cropped). */
    const val MAX_PROP_AREA_FRACTION: Double = 0.820

    // ---- Reference dataset -------------------------------------------------

    /**
     * Root asset folder holding the processed reference images. Sub-folders are
     * located by name rather than by fixed path, so "type_a", "Type_A" and
     * "Processed/Type_A" all resolve. Asset paths are case-sensitive, which is
     * why the lookup normalises case and underscores.
     */
    const val DATASET_ASSET_DIR: String = "rotoriq_dataset"

    const val DATASET_DIR_TYPE_A: String = "type_a"
    const val DATASET_DIR_TYPE_B: String = "type_b"

    /**
     * Never used as positive reference material. Present only so the loader can
     * recognise and deliberately skip it.
     */
    const val DATASET_DIR_UNKNOWN: String = "unknown"

    /** Reference images are decoded at this longest edge before segmentation. */
    const val REFERENCE_DECODE_MAX_DIM: Int = 640

    /** Hard cap on references held per class, to bound memory and match time. */
    const val MAX_REFERENCES_PER_TYPE: Int = 80

    /** Both classes need at least this many usable references, or matching is refused. */
    const val MIN_REFERENCES_PER_TYPE: Int = 3

    // ---- Legacy single-letter templates (fallback) -------------------------
    // Used only when the dataset above is absent, preserving the earlier behaviour.

    const val TEMPLATE_ASSET_DIR: String = "propeller_reference"
    const val TEMPLATE_A: String = "type_a.png"
    const val TEMPLATE_B: String = "type_b.png"
    const val MIN_TEMPLATE_SCORE: Double = 0.62
    const val MIN_TEMPLATE_MARGIN: Double = 0.08

    // ---- Matching --------------------------------------------------------
    // ALL UNCALIBRATED. These numbers were chosen to be strict, not to make the
    // classifier produce answers. They have not been validated against labelled
    // photographs taken on an Android device, and the thresholds that worked in
    // offline Python experiments do not transfer: different decoder, different
    // resize path, different camera pipeline. Measure, then set these from data.

    /** Edge length of the canonical patch both query and references are reduced to. */
    const val NORMALISED_PATCH: Int = 128

    /** Crop a square of (radius x this x 2) around the hub before normalising. */
    const val CROP_MARGIN: Double = 1.25

    /**
     * When true, query and reference images are both cropped to the detected
     * propeller and rotated to a common axis before comparison. This suppresses
     * background, which otherwise dominates whole-photograph correlation.
     * Set false only to reproduce naive whole-image matching.
     */
    const val CROP_TO_PROPELLER: Boolean = true

    /** A class score is the mean of its best K reference scores, not a single max. */
    const val MATCH_TOP_K: Int = 5

    /**
     * Winning class score floor, as normalised cross-correlation in [-1, 1].
     * This is the same number DecisionEngine uses as its reliability gate, so a
     * classification that clears it here is consistent downstream.
     */
    const val MIN_CONFIDENCE: Float = 0.80f

    /** The winning class must beat the other by at least this much. */
    const val MIN_CLASS_SEPARATION: Double = 0.10

    /**
     * At least this many references of the winning class must individually clear
     * MIN_CONFIDENCE, so one freak match cannot decide the answer.
     */
    const val MIN_SUPPORTING_REFERENCES: Int = 3
}

