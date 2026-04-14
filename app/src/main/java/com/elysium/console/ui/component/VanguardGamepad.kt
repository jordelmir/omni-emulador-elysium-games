package com.elysium.console.ui.component

import android.view.MotionEvent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.console.bridge.RetroButton
import com.elysium.console.ui.theme.NeonGreen
import com.elysium.console.ui.theme.SurfaceDark

/**
 * VANGUARD GAMEPAD: A premium virtual controller system.
 * Designed for the Ultra Edition with multi-touch support and haptic synchronization.
 */
@Composable
fun VanguardGamepad(
    platform: com.elysium.console.domain.model.Platform,
    onButtonEvent: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Left Side: D-Pad
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            VanguardDPad(onButtonEvent = onButtonEvent)
        }

        // Center: Select/Start
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SystemButton(label = "SELECT", id = RetroButton.SELECT, onButtonEvent = onButtonEvent)
            SystemButton(label = "START", id = RetroButton.START, onButtonEvent = onButtonEvent)
        }

        // Right Side: Action Buttons
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            ActionButtons(platform = platform, onButtonEvent = onButtonEvent)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun VanguardButton(
    id: Int,
    label: String,
    onButtonEvent: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NeonGreen
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 1.2f else 1f, tween(100), label = "btn_scale")
    val alpha by animateFloatAsState(if (isPressed) 0.8f else 0.4f, tween(100), label = "btn_alpha")

    Box(
        modifier = modifier
            .scale(scale)
            .alpha(alpha)
            .size(56.dp)
            .background(color.copy(alpha = 0.1f), CircleShape)
            .border(2.dp, color, CircleShape)
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onButtonEvent(id, true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onButtonEvent(id, false)
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp
        )
    }
}

@Composable
private fun ActionButtons(
    platform: com.elysium.console.domain.model.Platform,
    onButtonEvent: (Int, Boolean) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val showXY = platform != com.elysium.console.domain.model.Platform.NES && 
                    platform != com.elysium.console.domain.model.Platform.GBA &&
                    platform != com.elysium.console.domain.model.Platform.ARCADE

        // SNES/PlayStation Diamond Layout
        if (showXY) {
            VanguardButton(id = RetroButton.Y, label = "Y", onButtonEvent = onButtonEvent, modifier = Modifier.align(Alignment.CenterStart))
            VanguardButton(id = RetroButton.X, label = "X", onButtonEvent = onButtonEvent, modifier = Modifier.align(Alignment.TopCenter))
        }
        
        VanguardButton(id = RetroButton.A, label = "A", onButtonEvent = onButtonEvent, modifier = Modifier.align(Alignment.CenterEnd))
        VanguardButton(id = RetroButton.B, label = "B", onButtonEvent = onButtonEvent, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun VanguardDPad(onButtonEvent: (Int, Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(140.dp)
            .background(SurfaceDark.copy(alpha = 0.2f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Vertical
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DPadPart(id = RetroButton.UP, label = "▲", onButtonEvent = onButtonEvent)
            Spacer(modifier = Modifier.height(40.dp))
            DPadPart(id = RetroButton.DOWN, label = "▼", onButtonEvent = onButtonEvent)
        }
        // Horizontal
        Row(verticalAlignment = Alignment.CenterVertically) {
            DPadPart(id = RetroButton.LEFT, label = "◀", onButtonEvent = onButtonEvent)
            Spacer(modifier = Modifier.width(40.dp))
            DPadPart(id = RetroButton.RIGHT, label = "▶", onButtonEvent = onButtonEvent)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DPadPart(id: Int, label: String, onButtonEvent: (Int, Boolean) -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .alpha(if (isPressed) 1f else 0.5f)
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onButtonEvent(id, true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onButtonEvent(id, false)
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = NeonGreen, fontSize = 24.sp)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SystemButton(label: String, id: Int, onButtonEvent: (Int, Boolean) -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .width(64.dp)
            .height(28.dp)
            .alpha(if (isPressed) 1f else 0.4f)
            .background(SurfaceDark.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .border(1.dp, NeonGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onButtonEvent(id, true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onButtonEvent(id, false)
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = NeonGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}
