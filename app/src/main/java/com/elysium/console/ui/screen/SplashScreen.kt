package com.elysium.console.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.console.ui.theme.NeonGreen
import com.elysium.console.ui.theme.DeepBlack
import kotlinx.coroutines.delay

/**
 * Cinematic startup screen for Omni Elysium Vanguard.
 * Features a neon-pulse logo animation and system initialization sequence.
 */
@Composable
fun SplashScreen(onAnimationFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.8f) }

    LaunchedEffect(Unit) {
        startAnimation = true
        alpha.animateTo(1f, tween(1000, easing = LinearEasing))
        scale.animateTo(1.1f, tween(1500, easing = LinearEasing))
        delay(500)
        onAnimationFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Placeholder for Logo / Symbol
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale.value)
                    .alpha(alpha.value)
                    .background(NeonGreen.copy(alpha = 0.1f), shape = androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ω",
                    style = MaterialTheme.typography.displayLarge.copy(
                        color = NeonGreen,
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "OMNI ELYSIUM",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = Color.White,
                    letterSpacing = 8.sp,
                    fontWeight = FontWeight.Light
                ),
                modifier = Modifier.alpha(alpha.value)
            )

            Text(
                text = "VANGUARD EDITION",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = NeonGreen,
                    letterSpacing = 2.sp
                ),
                modifier = Modifier.alpha(alpha.value)
            )
        }
    }
}
