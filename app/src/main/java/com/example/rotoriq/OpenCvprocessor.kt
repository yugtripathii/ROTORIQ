package com.example.rotoriq

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.Closeable
import kotlin.math.max

/**
 * Image loading and OpenCV preprocessing. Knows nothing about propeller types
 * or the installation rule: it turns a photo into a segmented propeller plus
 * quality metrics, and PropellerDetector decides what that means.
 */
object OpenCvProcessor {

    @Volatile
    private var initialised = false

    /** True once the native OpenCV library has loaded successfully. */
    val isReady: Boolean get() = initialised

    /**
     * Loads the native library. Safe to call more than once.
     * Call from MainActivity.onCreate and check the result; if it fails the
     * app must still run and report UNABLE TO VERIFY rather than crashing.
     */
    @Synchronized
    fun initialise(): Boolean {
        if (initialised) return true
        initialised = try {
            OpenCVLoader.initLocal()
        } catch (t: Throwable) {
            false
        }
        return initialised
    }

    /**
     * Output of [process]. Holds native memory, so always use it inside `use { }`.
     *
     * @param sharpness        variance of the Laplacian; low means blurry.
     * @param propAreaFraction propeller area as a fraction of the whole frame.
     * @param hub              estimated rotation centre, in [gray] pixel coordinates.
     * @param bounds           bounding box of the propeller contour.
     */
    class Analysis(
        val gray: Mat,
        val mask: Mat,
        val contour: MatOfPoint?,
        val hub: Point?,
        val bounds: Rect?,
        val sharpness: Double,
        val propAreaFraction: Double
    ) : Closeable {

        /** Rough propeller radius in pixels, from the bounding box. */
        val radius: Double
            get() = bounds?.let { max(it.width, it.height) / 2.0 } ?: 0.0

        override fun close() {
            gray.release()
            mask.release()
            contour?.release()
        }
    }

    /**
     * Decodes [uri] honouring EXIF rotation, downscaled so the longest edge is
     * at most [maxDim]. Returns null if the image cannot be read at all.
     */
    fun loadBitmap(context: Context, uri: Uri, maxDim: Int = 1600): Bitmap? = try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null || bytes.isEmpty()) {
            null
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

            val longest = max(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longest / (sample * 2) >= maxDim) sample *= 2

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?.let { applyExifRotation(it, bytes) }
        }
    } catch (t: Throwable) {
        null
    }

    private fun applyExifRotation(bitmap: Bitmap, bytes: ByteArray): Bitmap = try {
        val orientation = bytes.inputStream().use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) {
            bitmap
        } else {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { if (it != bitmap) bitmap.recycle() }
        }
    } catch (t: Throwable) {
        bitmap
    }

    /**
     * Segments the propeller and measures image quality.
     *
     * Pipeline: greyscale, Laplacian variance for focus, blur, Otsu threshold
     * (the propeller is darker than any sensible background), morphological
     * clean-up, then the contour that best combines large area with closeness
     * to the frame centre, since the user is asked to centre the propeller.
     *
     * Returns null only if OpenCV is unavailable or the bitmap is unusable.
     */
    fun process(bitmap: Bitmap): Analysis? {
        if (!initialised) return null

        val rgba = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            if (rgba.empty()) return null

            val gray = Mat()
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)

            val sharpness = laplacianVariance(gray)

            val blurred = Mat()
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            val mask = Mat()
            Imgproc.threshold(
                blurred, mask, 0.0, 255.0,
                Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU
            )
            blurred.release()

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()

            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                mask.clone(), contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
            )
            hierarchy.release()

            val frameArea = (gray.width() * gray.height()).toDouble()
            val centre = Point(gray.width() / 2.0, gray.height() / 2.0)

            val best = contours.maxByOrNull { contour ->
                val area = Imgproc.contourArea(contour)
                if (area < frameArea * 0.005) {
                    Double.NEGATIVE_INFINITY
                } else {
                    val moments = Imgproc.moments(contour)
                    val cx = if (moments.m00 != 0.0) moments.m10 / moments.m00 else 0.0
                    val cy = if (moments.m00 != 0.0) moments.m01 / moments.m00 else 0.0
                    val distance = Math.hypot(cx - centre.x, cy - centre.y)
                    area / (1.0 + distance)
                }
            }?.takeIf { Imgproc.contourArea(it) > frameArea * 0.005 }

            contours.filter { it !== best }.forEach { it.release() }

            val area = best?.let { Imgproc.contourArea(it) } ?: 0.0
            val hub = best?.let {
                val moments = Imgproc.moments(it)
                if (moments.m00 == 0.0) null
                else Point(moments.m10 / moments.m00, moments.m01 / moments.m00)
            }

            Analysis(
                gray = gray,
                mask = mask,
                contour = best,
                hub = hub,
                bounds = best?.let { Imgproc.boundingRect(it) },
                sharpness = sharpness,
                propAreaFraction = if (frameArea > 0) area / frameArea else 0.0
            )
        } catch (t: Throwable) {
            null
        } finally {
            rgba.release()
        }
    }

    /** Variance of the Laplacian: the standard cheap focus measure. */
    private fun laplacianVariance(gray: Mat): Double {
        val laplacian = Mat()
        return try {
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
            val mean = MatOfDouble()
            val stdDev = MatOfDouble()
            Core.meanStdDev(laplacian, mean, stdDev)
            val sd = stdDev.toArray().firstOrNull() ?: 0.0
            mean.release()
            stdDev.release()
            sd * sd
        } catch (t: Throwable) {
            0.0
        } finally {
            laplacian.release()
        }
    }
}




