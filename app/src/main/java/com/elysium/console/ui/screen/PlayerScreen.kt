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
                    
                    // Physical Bluetooth Controller Support
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                    
                    setOnKeyListener { _, keyCode, event ->
                        val pressed = event.action == android.view.KeyEvent.ACTION_DOWN
                        val retroId = when (keyCode) {
                            android.view.KeyEvent.KEYCODE_BUTTON_B -> 0 // Libretro B (South)
                            android.view.KeyEvent.KEYCODE_BUTTON_X -> 1 // Libretro Y (West)
                            android.view.KeyEvent.KEYCODE_BUTTON_SELECT -> 2
                            android.view.KeyEvent.KEYCODE_BUTTON_START -> 3
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> 4
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> 5
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> 6
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> 7
                            android.view.KeyEvent.KEYCODE_BUTTON_A -> 8 // Libretro A (East)
                            android.view.KeyEvent.KEYCODE_BUTTON_Y -> 9 // Libretro X (North)
                            android.view.KeyEvent.KEYCODE_BUTTON_L1 -> 10
                            android.view.KeyEvent.KEYCODE_BUTTON_R1 -> 11
                            android.view.KeyEvent.KEYCODE_BUTTON_L2 -> 12
                            android.view.KeyEvent.KEYCODE_BUTTON_R2 -> 13
                            android.view.KeyEvent.KEYCODE_BUTTON_THUMBL -> 14
                            android.view.KeyEvent.KEYCODE_BUTTON_THUMBR -> 15
                            else -> -1
                        }
                        
                        if (retroId != -1) {
                            com.elysium.console.bridge.ElysiumBridge.nativeSetButton(retroId, pressed)
                            true // Event consumed
                        } else {
                            false
                        }
                    }

                    // Analog Stick Support
                    setOnGenericMotionListener { _, event ->
                        if (event.isFromSource(android.view.InputDevice.SOURCE_JOYSTICK)) {
                            val x = event.getAxisValue(android.view.MotionEvent.AXIS_X)
                            val y = event.getAxisValue(android.view.MotionEvent.AXIS_Y)
                            
                            val threshold = 0.5f
                            // Map Left Analog to D-Pad for classic games
                            com.elysium.console.bridge.ElysiumBridge.nativeSetButton(6, x < -threshold) // LEFT
                            com.elysium.console.bridge.ElysiumBridge.nativeSetButton(7, x > threshold)  // RIGHT
                            com.elysium.console.bridge.ElysiumBridge.nativeSetButton(4, y < -threshold) // UP
                            com.elysium.console.bridge.ElysiumBridge.nativeSetButton(5, y > threshold)  // DOWN
                            true
                        } else {
                            false
                        }
                    }
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
