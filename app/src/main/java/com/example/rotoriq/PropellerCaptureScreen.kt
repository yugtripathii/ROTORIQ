package com.example.rotoriq

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Instruction text required for every capture step. */
private const val CAPTURE_INSTRUCTIONS =
    "Position your camera directly above the propeller. Ensure both blades, " +
            "the central hub and the mounting area are visible."

/**
 * Screens 2 and 3 of the guided flow. Identical UI for each of the four
 * slots; the capture order groups the diagonals so the user photographs
 * FL and RR, then FR and RL.
 *
 * Holds no inspection state: MainActivity owns it and passes everything in.
 */
@Composable
fun PropellerCaptureScreen(
    position: MotorPosition,
    diagonal: Int,
    stepNumber: Int,
    totalSteps: Int,
    imageUri: Uri?,
    captured: Set<MotorPosition>,
    onCapture: () -> Unit,
    onUpload: () -> Unit,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Navy)
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(18.dp))

        Text(
            "DIAGONAL $diagonal  •  STEP $stepNumber OF $totalSteps",
            color = position.diagonalColor(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            position.label.uppercase(),
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { stepNumber.toFloat() / totalSteps.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = position.diagonalColor(),
            trackColor = CardColor
        )

        Spacer(Modifier.height(18.dp))

        DronePositionDiagram(
            diagramSize = 190.dp,
            highlight = position,
            captured = captured
        )

        Spacer(Modifier.height(18.dp))

        if (imageUri == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "HOW TO PHOTOGRAPH",
                        color = Blue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(CAPTURE_INSTRUCTIONS, color = Color.LightGray, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Light the propeller from the side so the moulded type letter " +
                                "near the blade root catches a shadow.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            ActionButton(title = "📷  CAPTURE IMAGE", onClick = onCapture)
            Spacer(Modifier.height(12.dp))
            ActionButton(title = "🖼  UPLOAD IMAGE", onClick = onUpload)
        } else {
            CapturedImagePreview(imageUri)

            Spacer(Modifier.height(18.dp))
            ActionButton(title = "✓  CONFIRM", onClick = onConfirm)
            Spacer(Modifier.height(12.dp))
            ActionButton(title = "🔁  RETAKE", onClick = onRetake)
            Spacer(Modifier.height(12.dp))
            ActionButton(title = "🖼  CHOOSE FROM GALLERY", onClick = onUpload)
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = onBack) {
                Text("← Back", color = Color.LightGray, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** Decodes and shows the captured photo, off the main thread. */
@Composable
fun CapturedImagePreview(uri: Uri, modifier: Modifier = Modifier) {
    val bitmap by rememberPreviewBitmap(uri)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(16.dp))
            .background(CardColor),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        when {
            image != null -> Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Captured propeller photograph",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Blue)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Loading photo…",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun rememberPreviewBitmap(uri: Uri): State<Bitmap?> {
    val context = LocalContext.current
    return produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            OpenCvProcessor.loadBitmap(context, uri, maxDim = 900)
        }
    }
}

