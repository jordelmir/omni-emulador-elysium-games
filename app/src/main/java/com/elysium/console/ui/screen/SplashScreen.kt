package com.elysium.console.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.9f) }
    var statusText by remember { mutableStateOf("INITIALIZING BOOT LOADER...") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val downloader = remember { com.elysium.console.data.downloader.CoreDownloader(context) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(1000, easing = LinearEasing))
        scale.animateTo(1.0f, tween(1500, easing = LinearEasing))
        
        statusText = "CALIBRATING VANGUARD CORES..."
        delay(400)
        
        val requiredCores = listOf("snes9x", "genesis_plus_gx", "mgba", "nestopia")
        for (core in requiredCores) {
            try {
                val installed = downloader.isCoreInstalled(core)
                if (!installed) {
                    downloader.downloadCore(core).collect { state ->
                        when (state) {
                            is com.elysium.console.data.downloader.CoreDownloader.DownloadState.Connecting -> {
                                statusText = "CONNECTING TO VANGUARD SERVERS..."
                            }
                            is com.elysium.console.data.downloader.CoreDownloader.DownloadState.Downloading -> {
                                statusText = "DOWNLOADING CORE [$core]... ${state.progress.toInt()}%"
                            }
                            is com.elysium.console.data.downloader.CoreDownloader.DownloadState.Extracting -> {
                                statusText = "EXTRACTING CORE ENGINE ($core)..."
                            }
                            is com.elysium.console.data.downloader.CoreDownloader.DownloadState.Error -> {
                                statusText = "CORE MISMATCH: SKIPPING $core"
                                delay(1000)
                            }
                            is com.elysium.console.data.downloader.CoreDownloader.DownloadState.Success -> {
                                statusText = "CORE $core OPTIMIZED."
                                delay(200)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                statusText = "BYPASSING CORE INITIALIZATION..."
                delay(500)
            }
        }
        
        statusText = "VANGUARD SYSTEM READY"
        delay(400)
        onAnimationFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack),
        contentAlignment = Alignment.Center
    ) {
        // Ambient background glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonGreen.copy(alpha = 0.05f), Color.Transparent),
                            radius = size.width
                        )
                    )
                }
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Logo
            Image(
                painter = painterResource(id = com.elysium.console.R.drawable.omni_elysium_logo),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(160.dp)
                    .scale(scale.value)
                    .alpha(alpha.value)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Technical HUD
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(alpha.value)) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = NeonGreen,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Scanning bar
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    val progress = remember { Animatable(0f) }
                    LaunchedEffect(Unit) {
                        progress.animateTo(1f, tween(2500, easing = LinearEasing))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.value)
                            .fillMaxHeight()
                            .background(NeonGreen)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "ULTRA EDITION",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color.White.copy(alpha = 0.4f),
                    letterSpacing = 4.sp
                ),
                modifier = Modifier.alpha(alpha.value)
            )
        }
    }
}
