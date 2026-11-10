package com.example.rotoriq

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry point and coordinator.
 *
 * Responsibilities, and nothing beyond them:
 *  - initialise OpenCV once, tolerating failure
 *  - own the inspection state
 *  - route between Home, Intro, Capture, Camera and Result
 *  - run the verification pipeline off the main thread
 *
 * All image processing lives in OpenCvProcessor and PropellerDetector; the
 * installation rule lives in ReferenceConfig and DecisionEngine; every screen
 * is its own composable. None of that is duplicated here.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Native library load can fail on an unsupported ABI. The app must still
        // start: verification then reports UNABLE TO VERIFY rather than crashing.
        val openCvReady = OpenCvProcessor.initialise()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Blue,
                    onPrimary = Navy,
                    background = Navy,
                    onBackground = Color.White,
                    surface = CardColor,
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Navy
                ) {
                    RotorIqApp(openCvReady = openCvReady)
                }
            }
        }
    }
}

@Composable
private fun RotorIqApp(openCvReady: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Survives rotation. The verdict deliberately does not: re-running the
    // pipeline is cheaper and safer than restoring a stale PASS.
    var state by rememberSaveable(stateSaver = InspectionState.Saver) {
        mutableStateOf(InspectionState())
    }

    var showCamera by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<VerificationResult?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val totalSteps = InspectionState.CAPTURE_ORDER.size

    val pickImage = rememberImagePicker(
        fileNameHint = state.currentPosition.name.lowercase(),
        onPicked = { uri ->
            state = state.withImage(state.currentPosition, uri)
            notice = null
        },
        onCancelled = { notice = "Image selection cancelled." },
        onError = { message -> notice = message }
    )

    fun startVerification() {
        if (isVerifying) return
        isVerifying = true
        notice = null
        scope.launch {
            val outcome = runVerification(context, state)
            result = outcome
            state = state.copy(step = InspectionStep.RESULT)
            isVerifying = false
        }
    }

    // ---- Routing ----------------------------------------------------------

    when {
        isVerifying -> VerifyingScreen()

        showCamera -> {
            BackHandler { showCamera = false }
            CameraScreen(
                position = state.currentPosition,
                onCaptured = { uri ->
                    state = state.withImage(state.currentPosition, uri)
                    showCamera = false
                    notice = null
                },
                onCancel = { showCamera = false }
            )
        }

        else -> when (state.step) {
            InspectionStep.HOME -> HomeScreen(
                status = notice ?: when {
                    !openCvReady -> "Image processing unavailable"
                    state.allCaptured -> "Four photographs ready to verify"
                    state.capturedCount > 0 -> "Inspection in progress"
                    else -> "Awaiting inspection"
                },
                capturedCount = state.capturedCount,
                isVerifying = false,
                canResume = state.capturedCount > 0,
                processingAvailable = openCvReady,
                onStartInspection = {
                    result = null
                    notice = null
                    state = InspectionState(step = InspectionStep.INTRO)
                },
                onResume = {
                    notice = null
                    state = state.copy(step = InspectionStep.CAPTURE)
                }
            )

            InspectionStep.INTRO -> {
                BackHandler { state = state.copy(step = InspectionStep.HOME) }
                GuidedInspectionScreen(
                    onStart = { state = state.copy(step = InspectionStep.CAPTURE, sequenceIndex = 0) },
                    onBack = { state = state.copy(step = InspectionStep.HOME) }
                )
            }

            InspectionStep.CAPTURE -> {
                BackHandler { state = state.back() }
                Column(Modifier.fillMaxSize()) {
                    notice?.let { message ->
                        Text(
                            text = message,
                            color = WarnAmber,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 6.dp)
                        )
                    }
                    PropellerCaptureScreen(
                        position = state.currentPosition,
                        diagonal = state.currentDiagonal,
                        stepNumber = state.sequenceIndex + 1,
                        totalSteps = totalSteps,
                        imageUri = state.currentUri,
                        captured = state.capturedPositions,
                        onCapture = {
                            notice = null
                            showCamera = true
                        },
                        onUpload = {
                            notice = null
                            pickImage()
                        },
                        onRetake = {
                            state = state.withoutImage(state.currentPosition)
                            notice = null
                        },
                        onConfirm = {
                            if (state.sequenceIndex == totalSteps - 1) {
                                if (state.allCaptured) {
                                    startVerification()
                                } else {
                                    val missing = MotorPosition.entries
                                        .firstOrNull { it !in state.capturedPositions }
                                    if (missing != null) {
                                        notice = "${missing.label} is still missing."
                                        state = state.goTo(missing)
                                    }
                                }
                            } else {
                                state = state.advance()
                            }
                        },
                        onBack = { state = state.back() }
                    )
                }
            }

            InspectionStep.RESULT -> {
                val outcome = result
                if (outcome == null) {
                    // Only reachable if the process was recreated at this step.
                    // Never write state during composition: do it as an effect.
                    LaunchedEffect(Unit) {
                        state = state.copy(step = InspectionStep.CAPTURE)
                    }
                } else {
                    BackHandler { state = state.copy(step = InspectionStep.HOME) }
                    ResultScreen(
                        result = outcome,
                        onRetakeAll = {
                            result = null
                            state = InspectionState(step = InspectionStep.CAPTURE)
                        },
                        onRetakePosition = { position ->
                            result = null
                            state = state
                                .withoutImage(position)
                                .goTo(position)
                                .copy(step = InspectionStep.CAPTURE)
                        },
                        onHome = { state = state.copy(step = InspectionStep.HOME) }
                    )
                }
            }
        }
    }
}

/**
 * Runs the full pipeline off the main thread:
 * image -> OpenCvProcessor -> PropellerDetector -> DecisionEngine -> verdict.
 *
 * Every failure path produces UNABLE TO VERIFY. No branch here can create a PASS;
 * only DecisionEngine can, and VerificationResult rejects an unjustified one.
 */
private suspend fun runVerification(
    context: Context,
    state: InspectionState
): VerificationResult = withContext(Dispatchers.Default) {
    try {
        if (!OpenCvProcessor.isReady) {
            return@withContext VerificationResult.unableToVerify(
                "Image processing is unavailable on this device, so the propellers " +
                        "cannot be checked."
            )
        }

        val missing = MotorPosition.entries.filter { state.uriFor(it) == null }
        if (missing.isNotEmpty()) {
            return@withContext VerificationResult.unableToVerify(
                "Missing photographs for: ${missing.joinToString { it.label }}."
            )
        }

        val findings = MotorPosition.entries.map { position ->
            val uri = state.uriFor(position)
            if (uri == null) {
                PropellerFinding(position, null, 0f, "No photograph was taken.")
            } else {
                PropellerDetector.analyse(context, position, uri)
            }
        }

        DecisionEngine().evaluate(findings)
    } catch (t: Throwable) {
        VerificationResult.unableToVerify(
            "Verification could not be completed: ${t.message ?: "unexpected error"}."
        )
    }
}

@Composable
private fun VerifyingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Blue)
            Spacer(Modifier.height(20.dp))
            Text(
                "Analysing propellers…",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Reading all four photographs",
                color = Color.LightGray,
                fontSize = 13.sp
            )
        }
    }
}
