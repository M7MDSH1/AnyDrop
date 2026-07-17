package com.anydrop.app.ui.tape_deck.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.AmberDim
import com.anydrop.app.ui.theme.LightGray
import com.anydrop.app.ui.theme.MediumGray

@Composable
fun ReelAnimation(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    isSpinning: Boolean = false,
    reelColor: Color = Amber,
    trackColor: Color = LightGray
) {
    val infiniteTransition = rememberInfiniteTransition(label = "reel")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isSpinning) 2000 else 30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier.size(size)) {
        val cx = size.toPx() / 2
        val cy = size.toPx() / 2
        val outerR = size.toPx() / 2 - 4.dp.toPx()
        val innerR = outerR * 0.6f
        val hubR = outerR * 0.15f

        drawCircle(color = MediumGray, radius = outerR + 2.dp.toPx())
        drawCircle(color = reelColor, radius = outerR)
        drawCircle(color = trackColor, radius = innerR)

        val spokeCount = 6
        for (i in 0 until spokeCount) {
            val angle = Math.toRadians((rotation + i * (360f / spokeCount)).toDouble())
            val startX = cx + hubR * Math.cos(angle).toFloat()
            val startY = cy + hubR * Math.sin(angle).toFloat()
            val endX = cx + innerR * Math.cos(angle).toFloat()
            val endY = cy + innerR * Math.sin(angle).toFloat()

            drawLine(
                color = reelColor.copy(alpha = 0.6f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        drawCircle(color = AmberDim, radius = hubR)

        drawCircle(
            color = Amber,
            radius = outerR,
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}
