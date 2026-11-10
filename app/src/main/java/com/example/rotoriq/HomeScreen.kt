package com.example.rotoriq

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared RotorIQ palette, used by every screen. */
internal val Navy = Color(0xFF101D32)
internal val Blue = Color(0xFF35B9E8)
internal val CardColor = Color(0xFF1B2D45)
internal val PassGreen = Color(0xFF2ECC71)
internal val FailRed = Color(0xFFE74C3C)
internal val WarnAmber = Color(0xFFF1C40F)

/**
 * Landing screen. Pure UI: owns no state and performs no work.
 * MainActivity supplies the state and handles every callback.
 */
@Composable
fun HomeScreen(
    status: String,
    capturedCount: Int,
    isVerifying: Boolean,
    canResume: Boolean,
    processingAvailable: Boolean,
    detail: String? = null,
    onStartInspection: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Navy)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(35.dp))

        Text("✦ ROTORIQ", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Blue)
        Text(
            "SMART PROPELLER INSPECTION",
            fontSize = 12.sp,
            color = Color.LightGray,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(35.dp))

        Text("DRONE SAFETY SYSTEM", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Verify Before You Fly.",
            color = Color.White,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Inspect your Pluto drone's propeller installation.",
            color = Color.LightGray,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(30.dp))

        DronePositionDiagram(diagramSize = 170.dp)

        Spacer(Modifier.height(26.dp))

        ActionButton(
            title = if (canResume) "▶  START OVER" else "▶  START INSPECTION",
            enabled = !isVerifying,
            onClick = onStartInspection
        )

        if (canResume) {
            Spacer(Modifier.height(14.dp))
            ActionButton(
                title = "↩  RESUME INSPECTION",
                enabled = !isVerifying,
                onClick = onResume
            )
        }

        Spacer(Modifier.height(26.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "INSPECTION STATUS",
                    color = Blue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))

                if (isVerifying) {
                    CircularProgressIndicator(color = Blue)
                    Spacer(Modifier.height(12.dp))
                }

                Text(status, color = Color.White, fontSize = 18.sp)

                if (capturedCount > 0 && !isVerifying) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$capturedCount of ${MotorPosition.entries.size} propellers captured",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }

                detail?.let { line ->
                    Spacer(Modifier.height(8.dp))
                    Text(line, color = Color.Gray, fontSize = 12.sp)
                }

                if (!processingAvailable) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Image processing is unavailable on this device, so results " +
                                "will always be UNABLE TO VERIFY.",
                        color = WarnAmber,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Text("SAFETY FIRST • MOTORS MUST BE STOPPED", color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable
internal fun ActionButton(
    title: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Blue,
            contentColor = Navy,
            disabledContainerColor = CardColor,
            disabledContentColor = Color.Gray
        )
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}


