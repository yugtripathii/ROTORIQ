package com.example.rotoriq

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

/** Walks up the ContextWrapper chain to the hosting activity. */
fun Context.findActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Live camera for one motor position. Handles the CAMERA permission itself and
 * reports failures upward instead of crashing; all capture work is delegated
 * to [CameraController].
 */
@Composable
fun CameraScreen(
    position: MotorPosition,
    onCaptured: (android.net.Uri) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionRequested = true
        if (!granted) {
            errorMessage = "Camera permission denied. You can still upload a photo instead."
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val controller = remember(context) { CameraController(context) }
    DisposableEffect(controller) {
        onDispose { controller.shutdown() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasPermission && activity != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                update = { view ->
                    // bind() is idempotent enough for this flow: it unbinds first.
                    scope.launch {
                        controller.bind(activity, view).onFailure {
                            errorMessage = "Camera could not start on this device."
                        }
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color(0xCC000000))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                position.label.uppercase(),
                color = Blue,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Hold the camera directly above the propeller. Both blades, the hub " +
                        "and the mounting area must be visible.",
                color = Color.LightGray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        errorMessage?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    message,
                    color = WarnAmber,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                if (!hasPermission && permissionRequested) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Enable Camera in Settings → Apps → RotorIQ → Permissions.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xCC000000))
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("CANCEL", color = Color.LightGray, fontSize = 14.sp)
            }

            Button(
                onClick = {
                    if (!isCapturing) {
                        isCapturing = true
                        scope.launch {
                            controller.capture(position.name.lowercase())
                                .onSuccess { onCaptured(it) }
                                .onFailure { errorMessage = "Capture failed. Try again." }
                            isCapturing = false
                        }
                    }
                },
                enabled = hasPermission && !isCapturing,
                shape = CircleShape,
                modifier = Modifier.size(76.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Blue,
                    contentColor = Navy,
                    disabledContainerColor = Color.DarkGray
                )
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(color = Navy, strokeWidth = 2.dp)
                } else {
                    Text("📷", fontSize = 22.sp)
                }
            }

            Spacer(Modifier.size(64.dp))
        }
    }
}


