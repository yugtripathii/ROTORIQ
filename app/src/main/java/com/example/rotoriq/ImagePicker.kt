package com.example.rotoriq

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.io.File

/**
 * Android Photo Picker integration. No permission is required for this API.
 *
 * The URI the picker hands back carries only a temporary read grant, which
 * does not survive process death, so the chosen image is copied into the app
 * cache straight away and the cached URI is what the rest of the app uses.
 */
@Composable
fun rememberImagePicker(
    fileNameHint: String,
    onPicked: (Uri) -> Unit,
    onCancelled: () -> Unit = {},
    onError: (String) -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    val currentHint by rememberUpdatedState(fileNameHint)
    val currentPicked by rememberUpdatedState(onPicked)
    val currentCancelled by rememberUpdatedState(onCancelled)
    val currentError by rememberUpdatedState(onError)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            currentCancelled()
        } else {
            val cached = copyToCache(context, uri, currentHint)
            if (cached == null) {
                currentError("That image could not be read. Try another one.")
            } else {
                currentPicked(cached)
            }
        }
    }

    return remember(launcher) {
        {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }
}

/**
 * Copies [source] into the cache directory so the app keeps access to it.
 * Returns null if the image cannot be read.
 */
fun copyToCache(context: Context, source: Uri, fileNameHint: String): Uri? = try {
    val target = File(
        context.cacheDir,
        "rotoriq_${fileNameHint}_${System.currentTimeMillis()}.jpg"
    )
    context.contentResolver.openInputStream(source)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    }
    if (target.length() > 0L) Uri.fromFile(target) else null
} catch (t: Throwable) {
    null
}


