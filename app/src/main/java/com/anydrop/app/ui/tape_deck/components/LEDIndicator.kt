package com.anydrop.app.ui.tape_deck.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anydrop.app.ui.theme.AmberDim
import com.anydrop.app.ui.theme.LedGreen
import com.anydrop.app.ui.theme.White

@Composable
fun LEDIndicator(
    modifier: Modifier = Modifier,
    label: String = "",
    isOn: Boolean = false,
    onColor: Color = LedGreen,
    offColor: Color = AmberDim,
    blink: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "led_blink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )

    val ledColor by animateColorAsState(
        targetValue = if (isOn) onColor else offColor,
        label = "led_color"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (blink && isOn) ledColor.copy(alpha = blinkAlpha)
                    else ledColor
                )
        )
        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (isOn) White else White.copy(alpha = 0.4f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
