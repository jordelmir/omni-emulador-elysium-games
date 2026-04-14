package com.elysium.console.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.console.R
import com.elysium.console.ui.theme.DeepBlack
import com.elysium.console.ui.theme.NeonGreen
import kotlinx.coroutines.delay

/**
 * Premium Splash Screen for Omni Elysium.
 */
@Composable
fun SplashScreen(onNavigateToDashboard: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "splash_glow")
    
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1500, easing = LinearEasing),
        label = "fade_in"
    )

    val scale by animateFloatAsState(
        targetValue = 1.1f,
        animationSpec = tween(2000, easing = FastOutSlowInEasing),
        label = "scale_up"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LaunchedEffect(Unit) {
        delay(2500) // Cinematic delay
        onNavigateToDashboard()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.omni_elysium_logo),
                contentDescription = "Omni Logo",
                modifier = Modifier
                    .size(200.dp)
                    .scale(scale * pulse)
                    .alpha(alpha)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "OMNI ELYSIUM",
                style = MaterialTheme.typography.headlineMedium,
                color = NeonGreen,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp,
                modifier = Modifier.alpha(alpha)
            )
            
            Text(
                text = "UNIVERSAL ORCHESTRATOR",
                style = MaterialTheme.typography.labelSmall,
                color = NeonGreen.copy(alpha = 0.6f),
                letterSpacing = 4.sp,
                modifier = Modifier.alpha(alpha).padding(top = 8.dp)
            )
        }
    }
}
