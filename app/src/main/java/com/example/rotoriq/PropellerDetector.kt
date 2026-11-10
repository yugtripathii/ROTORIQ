package com.example.rotoriq

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Classifies one propeller photograph as TYPE_A, TYPE_B or UNKNOWN.
 *
 * ## How it works
 *
 * The two Pluto propeller types are physically distinguished by a moulded
 * letter near the blade root. Blade silhouette was tested and rejected: on
 * the supplied photographs the apparent blade skew tracked the propeller's
 * position in the frame (lens perspective) rather than its type, giving
 * contradictory answers for the same propeller photographed twice.
 *
 * So classification is template matching on the moulded letter:
 *   1. OpenCvProcessor segments the propeller and measures focus and framing.
 *   2. Quality gates reject photographs that cannot support a decision.
 *   3. A region of interest around the hub is searched for each template
 *      across a range of rotations and scales, since the user cannot be
 *      expected to hold the phone at a fixed angle.
 *   4. The better score wins only if it clears MIN_TEMPLATE_SCORE and beats
 *      the other by MIN_TEMPLATE_MARGIN. Otherwise UNKNOWN.
 *
 * ## Calibration state
 *
 * The templates are NOT shipped. They must be cropped from real photographs
 * of each letter and placed in assets (see ReferenceConfig.TEMPLATE_*).
 * Until both exist, [classify] returns [Classification.Unknown] for every
 * image, by design, and the app reports UNABLE TO VERIFY. No image is ever
 * guessed at, and no PASS can arise from an uncalibrated classifier.
 */
object PropellerDetector {

    /** Three-way classifier output, as specified. */
    sealed interface Classification {
        data class Known(val type: PropellerType, val confidence: Float) : Classification
        data class Unknown(val reason: String) : Classification
    }

    /** Rotations searched, in degrees. The letter can appear at any angle. */
    private val SEARCH_ANGLES: List<Double> = (0 until 360 step 15).map { it.toDouble() }

    /** Template scales searched, relative to the detected propeller radius. */
    private val SEARCH_SCALES: List<Double> = listOf(0.16, 0.22, 0.30, 0.40)

    /** Cached templates, loaded once from assets. */
    @Volatile
    private var templates: Map<PropellerType, Mat>? = null

    /**
     * Full per-image pipeline: load, preprocess, classify, and wrap the answer
     * in a [PropellerFinding] for DecisionEngine. Never throws; any failure
     * becomes an unknown finding with a human-readable note.
     *
     * Call off the main thread.
     */
    fun analyse(context: Context, position: MotorPosition, uri: Uri): PropellerFinding {
        if (!OpenCvProcessor.isReady) {
            return unknown(position, "Image processing unavailable on this device.")
        }

        val bitmap = OpenCvProcessor.loadBitmap(context, uri)
            ?: return unknown(position, "Photo could not be opened. Take it again.")

        return try {
            val analysis = OpenCvProcessor.process(bitmap)
                ?: return unknown(position, "Photo could not be processed. Take it again.")

            analysis.use {
                when (val result = classify(context, it)) {
                    is Classification.Known ->
                        PropellerFinding(position, result.type, result.confidence)

                    is Classification.Unknown ->
                        unknown(position, result.reason)
                }
            }
        } catch (t: Throwable) {
            unknown(position, "Unexpected error while reading this photo.")
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Quality gates, then letter matching. Public so it can be unit tested
     * against a prepared [OpenCvProcessor.Analysis].
     */
    fun classify(context: Context, analysis: OpenCvProcessor.Analysis): Classification {
        // --- Quality gates: refuse to guess from an unusable photograph -----
        if (analysis.sharpness < ReferenceConfig.MIN_SHARPNESS) {
            return Classification.Unknown(
                "Photo is out of focus. Hold steady and tap the blade to focus."
            )
        }
        val hub = analysis.hub
        val bounds = analysis.bounds
        if (hub == null || bounds == null || analysis.contour == null) {
            return Classification.Unknown(
                "No propeller found. Centre the propeller against a plain background."
            )
        }
        if (analysis.propAreaFraction < ReferenceConfig.MIN_PROP_AREA_FRACTION) {
            return Classification.Unknown(
                "Propeller is too small in frame. Move the camera closer."
            )
        }
        if (analysis.propAreaFraction > ReferenceConfig.MAX_PROP_AREA_FRACTION) {
            return Classification.Unknown(
                "Propeller fills the frame. Move the camera back so both blades fit."
            )
        }

        // --- Letter matching -------------------------------------------------
        val loaded = loadTemplates(context)
        val templateA = loaded[PropellerType.A]
        val templateB = loaded[PropellerType.B]
        if (templateA == null || templateB == null) {
            return Classification.Unknown(
                "Classifier is not calibrated: reference letter templates are missing."
            )
        }

        val roi = hubRegion(analysis, hub) ?: return Classification.Unknown(
            "Could not isolate the propeller hub. Photograph it straight on."
        )

        return try {
            val scoreA = bestScore(roi, templateA, analysis.radius)
            val scoreB = bestScore(roi, templateB, analysis.radius)
            val winner = if (scoreA >= scoreB) PropellerType.A else PropellerType.B
            val best = max(scoreA, scoreB)
            val margin = kotlin.math.abs(scoreA - scoreB)

            when {
                best < ReferenceConfig.MIN_TEMPLATE_SCORE -> Classification.Unknown(
                    "Type marking not readable. Light the propeller from the side and retake."
                )

                margin < ReferenceConfig.MIN_TEMPLATE_MARGIN -> Classification.Unknown(
                    "Type marking is ambiguous between A and B. Retake with sharper lighting."
                )

                else -> Classification.Known(winner, best.toFloat().coerceIn(0f, 1f))
            }
        } catch (t: Throwable) {
            Classification.Unknown("Classification failed on this photo.")
        } finally {
            roi.release()
        }
    }

    /** Square region around the hub, where the moulded letter sits. */
    private fun hubRegion(analysis: OpenCvProcessor.Analysis, hub: Point): Mat? {
        val gray = analysis.gray
        val half = (analysis.radius * 0.85).roundToInt().coerceAtLeast(24)
        val left = (hub.x - half).roundToInt().coerceIn(0, gray.width() - 1)
        val top = (hub.y - half).roundToInt().coerceIn(0, gray.height() - 1)
        val width = min(half * 2, gray.width() - left)
        val height = min(half * 2, gray.height() - top)
        if (width < 24 || height < 24) return null

        return try {
            Mat(gray, Rect(left, top, width, height)).clone()
        } catch (t: Throwable) {
            null
        }
    }

    /** Best normalised-correlation score for one template over rotation and scale. */
    private fun bestScore(roi: Mat, template: Mat, propRadius: Double): Double {
        var best = -1.0
        for (scale in SEARCH_SCALES) {
            val targetHeight = (propRadius * scale).roundToInt()
            if (targetHeight < 8) continue
            val aspect = template.width().toDouble() / template.height().toDouble()
            val targetWidth = (targetHeight * aspect).roundToInt()
            if (targetWidth < 8) continue
            if (targetWidth >= roi.width() || targetHeight >= roi.height()) continue

            val scaled = Mat()
            Imgproc.resize(
                template, scaled,
                Size(targetWidth.toDouble(), targetHeight.toDouble()),
                0.0, 0.0, Imgproc.INTER_AREA
            )

            try {
                for (angle in SEARCH_ANGLES) {
                    val rotated = rotate(scaled, angle)
                    try {
                        if (rotated.width() >= roi.width() || rotated.height() >= roi.height()) {
                            continue
                        }
                        val result = Mat()
                        try {
                            Imgproc.matchTemplate(roi, rotated, result, Imgproc.TM_CCOEFF_NORMED)
                            val score = Core.minMaxLoc(result).maxVal
                            if (score > best) best = score
                        } finally {
                            result.release()
                        }
                    } finally {
                        rotated.release()
                    }
                }
            } finally {
                scaled.release()
            }
        }
        return best
    }

    private fun rotate(src: Mat, degrees: Double): Mat {
        if (degrees == 0.0) return src.clone()
        val centre = Point(src.width() / 2.0, src.height() / 2.0)
        val matrix = Imgproc.getRotationMatrix2D(centre, degrees, 1.0)
        val out = Mat()
        return try {
            Imgproc.warpAffine(
                src, out, matrix, src.size(),
                Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE, org.opencv.core.Scalar(0.0)
            )
            out
        } finally {
            matrix.release()
        }
    }

    /**
     * Loads the calibration templates from assets. A missing file is the normal,
     * expected state before calibration, not an error.
     */
    private fun loadTemplates(context: Context): Map<PropellerType, Mat> {
        templates?.let { return it }

        val loaded = synchronized(this) {
            templates ?: buildMap {
                readTemplate(context, ReferenceConfig.TEMPLATE_A)?.let { put(PropellerType.A, it) }
                readTemplate(context, ReferenceConfig.TEMPLATE_B)?.let { put(PropellerType.B, it) }
            }.also { templates = it }
        }
        return loaded
    }

    private fun readTemplate(context: Context, fileName: String): Mat? = try {
        context.assets.open("${ReferenceConfig.TEMPLATE_ASSET_DIR}/$fileName").use { stream ->
            val bitmap: Bitmap? = BitmapFactory.decodeStream(stream)
            if (bitmap == null) {
                null
            } else {
                val rgba = Mat()
                Utils.bitmapToMat(bitmap, rgba)
                val gray = Mat()
                Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
                rgba.release()
                bitmap.recycle()
                gray.takeIf { !it.empty() }
            }
        }
    } catch (t: Throwable) {
        null   // asset absent: classifier stays uncalibrated and reports UNKNOWN
    }

    private fun unknown(position: MotorPosition, reason: String) =
        PropellerFinding(position, detected = null, confidence = 0f, qualityNote = reason)
}


