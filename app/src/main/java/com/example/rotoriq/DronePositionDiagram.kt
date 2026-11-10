package com.example.rotoriq

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colour of the first diagonal (FL + RR). */
internal val DiagonalOne = Color(0xFF35B9E8)

/** Colour of the second diagonal (FR + RL). */
internal val DiagonalTwo = Color(0xFFB07CFF)

/** Which diagonal a slot belongs to: 1 for FL/RR, 2 for FR/RL. */
internal fun MotorPosition.diagonalIndex(): Int =
    if (this == MotorPosition.FRONT_LEFT || this == MotorPosition.REAR_RIGHT) 1 else 2

internal fun MotorPosition.diagonalColor(): Color =
    if (diagonalIndex() == 1) DiagonalOne else DiagonalTwo

/**
 * Top view of the drone with its four motors, nose upward.
 *
 * The two diagonals are drawn in different colours so the pairing rule is
 * obvious at a glance. [highlight] rings the slot currently being
 * photographed, [captured] fills slots already done, and [problems] marks
 * slots the verification flagged.
 */
@Composable
fun DronePositionDiagram(
    modifier: Modifier = Modifier,
    diagramSize: Dp = 230.dp,
    highlight: MotorPosition? = null,
    captured: Set<MotorPosition> = emptySet(),
    problems: Set<MotorPosition> = emptySet(),
    labels: Map<MotorPosition, String> = emptyMap()
) {
    Box(modifier = modifier.size(diagramSize), contentAlignment = Alignment.Center) {

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val rotorRadius = w * 0.115f
            val inset = w * 0.17f

            val centres = mapOf(
                MotorPosition.FRONT_LEFT to Offset(inset, inset),
                MotorPosition.FRONT_RIGHT to Offset(w - inset, inset),
                MotorPosition.REAR_LEFT to Offset(inset, h - inset),
                MotorPosition.REAR_RIGHT to Offset(w - inset, h - inset)
            )
            val bodyCentre = Offset(w / 2f, h / 2f)

            // Diagonal guides first, so they sit behind everything else.
            drawLine(
                color = DiagonalOne.copy(alpha = 0.45f),
                start = centres.getValue(MotorPosition.FRONT_LEFT),
                end = centres.getValue(MotorPosition.REAR_RIGHT),
                strokeWidth = w * 0.012f
            )
            drawLine(
                color = DiagonalTwo.copy(alpha = 0.45f),
                start = centres.getValue(MotorPosition.FRONT_RIGHT),
                end = centres.getValue(MotorPosition.REAR_LEFT),
                strokeWidth = w * 0.012f
            )

            centres.values.forEach { centre ->
                drawLine(
                    color = Color(0xFF3A4D68),
                    start = bodyCentre,
                    end = centre,
                    strokeWidth = w * 0.030f
                )
            }

            drawRoundRect(
                color = Color(0xFF24374F),
                topLeft = Offset(w * 0.37f, h * 0.27f),
                size = Size(w * 0.26f, h * 0.46f),
                cornerRadius = CornerRadius(w * 0.09f, w * 0.09f)
            )
            // Nose marker, so "front" is unambiguous.
            drawCircle(
                color = Color(0xFF6E8299),
                radius = w * 0.022f,
                center = Offset(w / 2f, h * 0.33f)
            )

            centres.forEach { (position, centre) ->
                val base = position.diagonalColor()
                val isCaptured = position in captured
                val isProblem = position in problems
                val isActive = position == highlight

                drawCircle(
                    color = if (isCaptured || isActive) base.copy(alpha = 0.30f)
                    else Color(0xFF1B2D45),
                    radius = rotorRadius,
                    center = centre
                )
                drawCircle(
                    color = if (isProblem) FailRed else base,
                    radius = rotorRadius,
                    center = centre,
                    style = Stroke(width = if (isActive || isProblem) w * 0.020f else w * 0.010f)
                )
                if (isActive) {
                    drawCircle(
                        color = base.copy(alpha = 0.55f),
                        radius = rotorRadius * 1.45f,
                        center = centre,
                        style = Stroke(width = w * 0.008f)
                    )
                }
                drawBlades(centre, rotorRadius * 0.82f, if (isProblem) FailRed else base)
            }
        }

        // Slot labels laid over the canvas, one per corner.
        val corner = diagramSize * 0.33f
        MotorPosition.entries.forEach { position ->
            val xSign = when (position) {
                MotorPosition.FRONT_LEFT, MotorPosition.REAR_LEFT -> -1f
                else -> 1f
            }
            val ySign = when (position) {
                MotorPosition.FRONT_LEFT, MotorPosition.FRONT_RIGHT -> -1f
                else -> 1f
            }
            Text(
                text = labels[position] ?: position.shortLabel,
                color = if (position in problems) FailRed else position.diagonalColor(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = corner * xSign, y = corner * ySign)
            )
        }
    }
}

/** Two short blades, so a rotor reads as a propeller rather than a plain circle. */
private fun DrawScope.drawBlades(centre: Offset, radius: Float, color: Color) {
    drawLine(
        color = color.copy(alpha = 0.85f),
        start = Offset(centre.x - radius, centre.y - radius * 0.28f),
        end = Offset(centre.x + radius, centre.y + radius * 0.28f),
        strokeWidth = radius * 0.34f
    )
}



