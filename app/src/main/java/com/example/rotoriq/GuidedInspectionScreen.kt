package com.example.rotoriq

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Screen 1 of the guided flow: explains the diagonal rule and shows the drone
 * diagram with the two diagonals colour-coded, then starts the inspection.
 */
@Composable
fun GuidedInspectionScreen(
    onStart: () -> Unit,
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
        Spacer(Modifier.height(24.dp))

        Text(
            "Guided Propeller Inspection",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Photograph all four propellers individually. RotorIQ will compare " +
                    "diagonally opposite propellers to verify their installation.",
            color = Color.LightGray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(26.dp))

        DronePositionDiagram(diagramSize = 240.dp)

        Spacer(Modifier.height(22.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "THE RULE",
                    color = Blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))
                DiagonalLegend(
                    color = DiagonalOne,
                    title = "Diagonal 1 — front left + rear right",
                    detail = "Both must be the same type."
                )
                Spacer(Modifier.height(12.dp))
                DiagonalLegend(
                    color = DiagonalTwo,
                    title = "Diagonal 2 — front right + rear left",
                    detail = "Both must be the same type, and different from diagonal 1."
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Either diagonal may hold type A or type B — only the pairing matters.",
                    color = Color.Gray,
                    fontSize = 12.sp
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
                Text(
                    "BEFORE YOU START",
                    color = Blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                listOf(
                    "Motors must be stopped and the battery disconnected.",
                    "Work in bright light, ideally with light coming from one side.",
                    "Keep the drone's nose pointing away from you throughout."
                ).forEach { line ->
                    Text("•  $line", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        Spacer(Modifier.height(26.dp))

        ActionButton(title = "▶  START INSPECTION", onClick = onStart)

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) {
            Text("Back", color = Color.LightGray, fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DiagonalLegend(color: Color, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.size(12.dp))
        Column {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Color.LightGray, fontSize = 12.sp)
        }
    }
}

/** Row used by the review screen: "FL vs RR — MATCH". */
@Composable
fun DiagonalComparisonRow(
    first: MotorPosition,
    second: MotorPosition,
    findings: List<PropellerFinding>,
    modifier: Modifier = Modifier
) {
    val a = findings.firstOrNull { it.position == first }
    val b = findings.firstOrNull { it.position == second }
    val bothKnown = a?.detected != null && b?.detected != null

    val (text, color) = when {
        !bothKnown -> "UNKNOWN" to WarnAmber
        a?.detected == b?.detected -> "MATCH" to PassGreen
        else -> "MISMATCH" to FailRed
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${first.shortLabel} vs ${second.shortLabel}",
            color = first.diagonalColor(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

