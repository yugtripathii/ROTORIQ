package com.example.rotoriq

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/** Which screen the guided inspection is on. */
enum class InspectionStep { HOME, INTRO, CAPTURE, RESULT }

/**
 * All state for one guided inspection run.
 *
 * Immutable: every change produces a copy, so Compose recomposes predictably.
 * Images are held as URI strings so the whole thing survives rotation through
 * [Saver]. MainActivity owns the single instance.
 */
@Immutable
data class InspectionState(
    val step: InspectionStep = InspectionStep.HOME,
    val sequenceIndex: Int = 0,
    val images: Map<MotorPosition, String> = emptyMap()
) {
    /** Capture order: first diagonal (FL, RR), then second (FR, RL). */
    val currentPosition: MotorPosition
        get() = CAPTURE_ORDER[sequenceIndex.coerceIn(0, CAPTURE_ORDER.lastIndex)]

    /** 1 while capturing the first diagonal, 2 for the second. */
    val currentDiagonal: Int
        get() = if (sequenceIndex < 2) 1 else 2

    val currentUri: Uri?
        get() = uriFor(currentPosition)

    val capturedCount: Int
        get() = images.size

    val allCaptured: Boolean
        get() = images.keys.containsAll(MotorPosition.entries)

    val capturedPositions: Set<MotorPosition>
        get() = images.keys

    fun uriFor(position: MotorPosition): Uri? =
        images[position]?.takeIf { it.isNotEmpty() }?.let(Uri::parse)

    fun withImage(position: MotorPosition, uri: Uri): InspectionState =
        copy(images = images + (position to uri.toString()))

    fun withoutImage(position: MotorPosition): InspectionState =
        copy(images = images - position)

    /** Advance to the next slot, or to the result step once all four are present. */
    fun advance(): InspectionState =
        if (sequenceIndex < CAPTURE_ORDER.lastIndex) copy(sequenceIndex = sequenceIndex + 1)
        else this

    fun back(): InspectionState =
        if (sequenceIndex > 0) copy(sequenceIndex = sequenceIndex - 1)
        else copy(step = InspectionStep.INTRO)

    fun goTo(position: MotorPosition): InspectionState =
        copy(sequenceIndex = CAPTURE_ORDER.indexOf(position).coerceAtLeast(0))

    companion object {
        val CAPTURE_ORDER: List<MotorPosition> = listOf(
            MotorPosition.FRONT_LEFT,
            MotorPosition.REAR_RIGHT,
            MotorPosition.FRONT_RIGHT,
            MotorPosition.REAR_LEFT
        )

        /**
         * Survives rotation. The verification result is deliberately NOT saved:
         * it is cheaper and safer to re-run the pipeline than to restore a stale verdict.
         */
        val Saver: Saver<InspectionState, Any> = listSaver(
            save = { state ->
                buildList {
                    add(state.step.name)
                    add(state.sequenceIndex.toString())
                    MotorPosition.entries.forEach { add(state.images[it].orEmpty()) }
                }
            },
            restore = { values: List<String> ->
                val restoredStep = runCatching { InspectionStep.valueOf(values[0]) }
                    .getOrDefault(InspectionStep.HOME)
                InspectionState(
                    // A result cannot be restored, so never land back on RESULT.
                    step = if (restoredStep == InspectionStep.RESULT) InspectionStep.CAPTURE
                    else restoredStep,
                    sequenceIndex = values[1].toIntOrNull() ?: 0,
                    images = MotorPosition.entries
                        .mapIndexedNotNull { index, position ->
                            values.getOrNull(index + 2)
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { position to it }
                        }
                        .toMap()
                )
            }
        )
    }
}


