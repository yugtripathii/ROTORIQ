package com.example.rotoriq

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Screen 4: the full verification summary — every slot's detected type, both
 * diagonal comparisons, and the final verdict.
 *
 * Pure UI. It renders what DecisionEngine produced and decides nothing itself.
 */
@Composable
fun ResultScreen(
    result: VerificationResult,
    onRetakeAll: () -> Unit,
    onRetakePosition: (MotorPosition) -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (result.verdict) {
        Verdict.PASS -> PassGreen
        Verdict.INCORRECT -> FailRed
        Verdict.UNABLE_TO_VERIFY -> WarnAmber
    }
    val headline = when (result.verdict) {
        Verdict.PASS -> "PASS"
        Verdict.INCORRECT -> "INCORRECT INSTALLATION"
        Verdict.UNABLE_TO_VERIFY -> "UNABLE TO VERIFY"
    }
    val subtitle = when (result.verdict) {
        Verdict.PASS -> "Both diagonals check out. Safe to proceed with your other preflight checks."
        Verdict.INCORRECT -> "Do not fly. Swap the flagged propellers and inspect again."
        Verdict.UNABLE_TO_VERIFY -> "No conclusion was reached. This is not a pass."
    }
    val problems = result.problemPositions

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Navy)
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(26.dp))
        Text("INSPECTION RESULT", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    headline,
                    color = accent,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    subtitle,
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        DronePositionDiagram(
            diagramSize = 210.dp,
            problems = problems,
            captured = MotorPosition.entries.toSet(),
            labels = result.findings.associate { finding ->
                finding.position to (finding.detected?.let { "${finding.position.shortLabel} · $it" }
                    ?: "${finding.position.shortLabel} · ?")
            }
        )

        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "DIAGONAL COMPARISON",
                    color = Blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                ReferenceConfig.DIAGONAL_PAIRS.forEach { (first, second) ->
                    DiagonalComparisonRow(first, second, result.findings)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "The two diagonals must also differ from each other.",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("DETAILS", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(result.message, color = Color.White, fontSize = 14.sp)
            }
        }

        if (result.findings.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "PER PROPELLER",
                        color = Blue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    // Fixed slot order, so the layout matches the drone every time.
                    MotorPosition.entries.forEach { position ->
                        Spacer(Modifier.height(10.dp))
                        FindingRow(
                            position = position,
                            finding = result.findings.firstOrNull { it.position == position },
                            isProblem = position in problems,
                            onRetake = { onRetakePosition(position) }
                        )
                        HorizontalDivider(color = Navy)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        ActionButton(title = "🔁  INSPECT AGAIN", onClick = onRetakeAll)
        Spacer(Modifier.height(12.dp))
        ActionButton(title = "🏠  BACK TO HOME", onClick = onHome)

        Spacer(Modifier.height(22.dp))
        Text("SAFETY FIRST • MOTORS MUST BE STOPPED", color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable
private fun FindingRow(
    position: MotorPosition,
    finding: PropellerFinding?,
    isProblem: Boolean,
    onRetake: () -> Unit
) {
    val reliable = finding?.isReliable(ReferenceConfig.MIN_CONFIDENCE) == true
    val valueText = when {
        finding == null -> "NO PHOTO"
        finding.detected == null -> "UNKNOWN"
        else -> "TYPE ${finding.detected}"
    }
    val valueColor = when {
        isProblem -> FailRed
        reliable -> Color.White
        else -> WarnAmber
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                position.label,
                color = position.diagonalColor(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(valueText, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (finding != null && finding.detected != null) {
                    Text(
                        "confidence ${(finding.confidence * 100).roundToInt()}%",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        finding?.qualityNote?.let { note ->
            Text(note, color = WarnAmber, fontSize = 11.sp)
        }
        if (!reliable) {
            Text(
                "Tap to retake this photograph",
                color = Blue,
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable(onClick = onRetake)
                    .padding(top = 2.dp, bottom = 8.dp)
            )
        }
    }
}



