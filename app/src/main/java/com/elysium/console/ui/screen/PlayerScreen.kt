package com.elysium.console.ui.screen

import android.opengl.GLSurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.elysium.console.ui.theme.DeepBlack
import com.elysium.console.ui.theme.NeonGreen
import com.elysium.console.ui.theme.NeonGreenGlow
import com.elysium.console.ui.theme.SurfaceDark
import com.elysium.console.ui.theme.TextSecondary
import com.elysium.console.viewmodel.EmulationViewModel
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Emulation player screen with GLSurfaceView for OpenGL ES rendering,
 * floating HUD overlay with FPS counter, and transport controls.
 */
@Composable
fun PlayerScreen(
    romPath: String,
    viewModel: EmulationViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val telemetry by viewModel.telemetry.collectAsState()
    val activePlatform by viewModel.activePlatform.collectAsState()
    var showHud by remember { mutableStateOf(true) }
    var isPaused by remember { mutableStateOf(false) }

    // Initialize emulation on composition
    DisposableEffect(romPath) {
        viewModel.startEmulation(romPath)
        onDispose {
            viewModel.stopEmulation()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepBlack)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showHud = !showHud
            }
    ) {
        // GLSurfaceView wrapped in AndroidView for Compose interop
        AndroidView(
            factory = { context ->
                GLSurfaceView(context).apply {
                    setEGLContextClientVersion(3)
                    setRenderer(object : GLSurfaceView.Renderer {
                        private var viewWidth = 0
                        private var viewHeight = 0

                        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                            android.opengl.GLES30.glClearColor(0.031f, 0.031f, 0.031f, 1.0f)
                        }

                        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                            android.opengl.GLES30.glViewport(0, 0, width, height)
                            viewWidth = width
                            viewHeight = height
                        }

                        override fun onDrawFrame(gl: GL10?) {
                            android.opengl.GLES30.glClear(android.opengl.GLES30.GL_COLOR_BUFFER_BIT)
                            com.elysium.console.bridge.ElysiumBridge.nativeRenderFrame(viewWidth, viewHeight)
                        }
                    })
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Vanguard Virtual Gamepad (Always visible for gameplay)
        com.elysium.console.ui.component.VanguardGamepad(
            platform = activePlatform ?: com.elysium.console.domain.model.Platform.ARCADE,
            onButtonEvent = { id, pressed ->
                // Native Input Sync
                com.elysium.console.bridge.ElysiumBridge.nativeSetButton(id, pressed)
                
                // Synchronized Haptics
                if (pressed) {
                    try {
                        val vibrator = context.getSystemService(android.os.Vibrator::class.java)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(10, 80))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(10)
                        }
                    } catch (e: Exception) { /* Silent fail */ }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // HUD Overlay — tap to toggle visibility
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top HUD bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    DeepBlack.copy(alpha = 0.8f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NeonGreen
                        )
                    }

                    // VANGUARD HUD (OSD)
                    com.elysium.console.ui.component.VanguardHud(
                        telemetry = telemetry,
                        shaderProfile = viewModel.getActiveShaderName(),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Bottom transport controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    DeepBlack.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .drawBehind {
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        NeonGreenGlow,
                                        Color.Transparent
                                    )
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Save State
                    TransportButton(
                        icon = Icons.Default.Save,
                        label = "SAVE",
                        onClick = { /* Save state */ }
                    )

                    // Play/Pause
                    FilledIconButton(
                        onClick = { isPaused = !isPaused },
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = NeonGreen.copy(alpha = 0.15f),
                            contentColor = NeonGreen
                        ),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Reset
                    TransportButton(
                        icon = Icons.Default.RestartAlt,
                        label = "RESET",
                        onClick = { /* Reset core */ }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = SurfaceDark.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextSecondary,
                letterSpacing = 1.5.sp,
                fontSize = 8.sp
            )
        )
    }
}
