package com.anydrop.app.ui.player.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

enum class GestureZone { LEFT, RIGHT, CENTER }

@Composable
fun GestureOverlay(
    modifier: Modifier = Modifier,
    onDoubleTapLeft: () -> Unit = {},
    onDoubleTapRight: () -> Unit = {},
    onBrightnessChange: (Float) -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
    onSingleTap: () -> Unit = {},
    content: @Composable () -> Unit
) {
    var zone by remember { mutableStateOf(GestureZone.CENTER) }

    Box(modifier = modifier.fillMaxSize()) {
        content()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            if (offset.x < size.width / 2) onDoubleTapLeft()
                            else onDoubleTapRight()
                        },
                        onTap = { onSingleTap() }
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            zone = when {
                                offset.x < size.width / 3f -> GestureZone.LEFT
                                offset.x > 2f * size.width / 3f -> GestureZone.RIGHT
                                else -> GestureZone.CENTER
                            }
                        },
                        onVerticalDrag = { _, dragAmount ->
                            val delta = -dragAmount / size.height
                            when (zone) {
                                GestureZone.LEFT -> onBrightnessChange(delta)
                                GestureZone.RIGHT -> onVolumeChange(delta)
                                GestureZone.CENTER -> {}
                            }
                        }
                    )
                }
        )
    }
}
