package com.example.rotoriq

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Thin wrapper over CameraX. Owns the provider, the preview and the capture
 * use case, and nothing else: it has no idea what a propeller is.
 *
 * One instance per camera screen. Call [shutdown] when the screen goes away.
 */
class CameraController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    /**
     * Binds preview and capture to [lifecycleOwner].
     * Returns a failure rather than throwing if the camera cannot start,
     * so the UI can show a message instead of crashing.
     */
    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ): Result<Unit> = try {
        val provider = awaitProvider()
        cameraProvider = provider

        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        imageCapture = capture

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            capture
        )
        Result.success(Unit)
    } catch (t: Throwable) {
        Result.failure(t)
    }

    /** Takes one photo into the app cache and returns its URI. */
    suspend fun capture(fileNameHint: String): Result<Uri> {
        val capture = imageCapture
            ?: return Result.failure(IllegalStateException("Camera is not ready"))

        val target = File(
            context.cacheDir,
            "rotoriq_${fileNameHint}_${System.currentTimeMillis()}.jpg"
        )
        val options = ImageCapture.OutputFileOptions.Builder(target).build()

        return suspendCancellableCoroutine { continuation ->
            try {
                capture.takePicture(
                    options,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            val uri = output.savedUri ?: Uri.fromFile(target)
                            if (continuation.isActive) continuation.resume(Result.success(uri))
                        }

                        override fun onError(exception: ImageCaptureException) {
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(exception))
                            }
                        }
                    }
                )
            } catch (t: Throwable) {
                if (continuation.isActive) continuation.resume(Result.failure(t))
            }
        }
    }

    fun shutdown() {
        runCatching { cameraProvider?.unbindAll() }
        cameraProvider = null
        imageCapture = null
    }

    private suspend fun awaitProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        if (continuation.isActive) continuation.resume(future.get())
                    } catch (t: Throwable) {
                        if (continuation.isActive) continuation.cancel(t)
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        }
}


