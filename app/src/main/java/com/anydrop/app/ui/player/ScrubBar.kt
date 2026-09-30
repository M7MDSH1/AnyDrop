package com.anydrop.app.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Player timeline. Playback progress is read in the draw phase, so the bar glides
 * every frame without recomposing anything. Under the finger the track thickens,
 * the thumb grows and a bubble above it shows the target time and how far the
 * jump is. A tap anywhere on the bar seeks there; a drag scrubs.
 *
 * [onScrub] fires for every move so the caller can preview the frame;
 * [onScrubEnd] fires once, with the final position, when the finger lifts.
 */
@Composable
fun ScrubBar(
    position: () -> Long,
    buffered: () -> Long,
    duration: Long,
    accent: Color,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.22f),
    bufferColor: Color = Color.White.copy(alpha = 0.42f)
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var anchorMs by remember { mutableLongStateOf(0L) }
    val lift by animateFloatAsState(
        targetValue = if (scrubbing) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "scrubLift"
    )

    // The pointer handler outlives recompositions; read the latest values through these.
    val currentPosition by rememberUpdatedState(position)
    val currentDuration by rememberUpdatedState(duration)
    val currentOnStart by rememberUpdatedState(onScrubStart)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnEnd by rememberUpdatedState(onScrubEnd)

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(40.dp)) {
        val widthPx = constraints.maxWidth.toFloat()
        // Room at both ends so the grown thumb never clips.
        val edgePx = with(density) { 10.dp.toPx() }
        val spanPx = (widthPx - 2 * edgePx).coerceAtLeast(1f)
        fun fractionAt(x: Float) = ((x - edgePx) / spanPx).coerceIn(0f, 1f)
        fun targetMs() = (scrubFraction * currentDuration).toLong()

        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (currentDuration <= 0L) return@awaitEachGesture
                        // Consuming every event keeps the player's swipe-to-next and
                        // double-tap layers from reacting to a scrub.
                        down.consume()
                        anchorMs = currentPosition()
                        scrubFraction = fractionAt(down.position.x)
                        scrubbing = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnStart()
                        currentOnScrub(targetMs())
                        try {
                            drag(down.id) { change ->
                                change.consume()
                                scrubFraction = fractionAt(change.position.x)
                                currentOnScrub(targetMs())
                            }
                        } finally {
                            scrubbing = false
                            currentOnEnd(targetMs())
                        }
                    }
                }
        ) {
            val dur = currentDuration.coerceAtLeast(1L).toFloat()
            val played = if (scrubbing) scrubFraction else (position() / dur).coerceIn(0f, 1f)
            val loaded = (buffered() / dur).coerceIn(0f, 1f)
            val cy = size.height / 2f
            val start = edgePx
            val end = size.width - edgePx
            val span = end - start
            val stroke = lerp(3.dp, 7.dp, lift).toPx()
            val thumbX = start + span * played

            drawLine(trackColor, Offset(start, cy), Offset(end, cy), stroke, StrokeCap.Round)
            if (loaded > played) {
                drawLine(bufferColor, Offset(start, cy), Offset(start + span * loaded, cy), stroke, StrokeCap.Round)
            }
            drawLine(accent, Offset(start, cy), Offset(thumbX, cy), stroke, StrokeCap.Round)
            if (lift > 0.01f) {
                drawCircle(accent.copy(alpha = 0.22f * lift), radius = lerp(6.dp, 20.dp, lift).toPx(), center = Offset(thumbX, cy))
            }
            drawCircle(accent, radius = lerp(6.dp, 9.dp, lift).toPx(), center = Offset(thumbX, cy))
        }

        if (scrubbing) {
            val target = targetMs()
            ScrubBubble(
                time = target.toTimeString(),
                delta = formatDelta(target - anchorMs),
                accent = accent,
                modifier = Modifier.layout { measurable, _ ->
                    // Measured free of the bar's 40dp height, which would clip its second line.
                    val bubble = measurable.measure(Constraints())
                    val thumbX = edgePx + spanPx * scrubFraction
                    val maxX = (widthPx.roundToInt() - bubble.width).coerceAtLeast(0)
                    val x = (thumbX - bubble.width / 2f).roundToInt().coerceIn(0, maxX)
                    // Floats above the bar, centred on the thumb, clamped to the bar's ends.
                    layout(0, 0) { bubble.place(x, -bubble.height - 2.dp.roundToPx()) }
                }
            )
        }
    }
}

@Composable
private fun ScrubBubble(time: String, delta: String?, accent: Color, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            time,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            style = TabularDigits
        )
        if (delta != null) {
            Text(delta, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, style = TabularDigits)
        }
    }
}

/**
 * A time readout that only recomposes when the shown text changes (once a
 * second), however often [text]'s inputs move.
 */
@Composable
fun TimeLabel(
    text: () -> String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val latest by rememberUpdatedState(text)
    val shown by remember { derivedStateOf { latest() } }
    Text(shown, color = color, fontSize = 12.sp, style = TabularDigits, modifier = modifier)
}

// Equal-width digits, so running times do not jitter sideways.
private val TabularDigits = TextStyle(fontFeatureSettings = "tnum")

internal fun Long.toTimeString(): String {
    val totalSeconds = coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

/** "+0:15" / "−1:05"; null under a second, where a jump is not worth showing. */
private fun formatDelta(ms: Long): String? {
    if (abs(ms) < 1_000L) return null
    val totalSeconds = abs(ms) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val body = if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    return (if (ms > 0) "+" else "−") + body
}
