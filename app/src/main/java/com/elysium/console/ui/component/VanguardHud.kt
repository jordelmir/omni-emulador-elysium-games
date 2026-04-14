package com.elysium.console.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.console.domain.model.TelemetryData
import com.elysium.console.ui.theme.NeonGreen
import com.elysium.console.ui.theme.SurfaceDark

/**
 * VANGUARD HUD: A futuristic OSD for real-time hardware telemetry.
 * Designed with a 'Digital Scanner' aesthetic for the Ultra Edition.
 */
@Composable
fun VanguardHud(
    telemetry: TelemetryData,
    shaderProfile: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hud_scan")
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_line"
    )

    Box(
        modifier = modifier
            .width(220.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, NeonGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .drawBehind {
                // Subtle scanning line effect
                val y = size.height * scanY
                drawLine(
                    color = NeonGreen.copy(alpha = 0.3f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "VANGUARD OSD",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonGreen,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(NeonGreen, RoundedCornerShape(3.dp))
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Metrics Grid
            HudMetric(label = "EMU FREQ", value = "${"%.1f".format(telemetry.fps)} FPS", subValue = "STABLE")
            HudMetric(label = "FRAME LAT", value = "${"%.2f".format(telemetry.frameTimeMs)} ms", subValue = "SYNC")
            HudMetric(label = "VISUAL", value = shaderProfile, subValue = "ACTIVE")
            
            // Resource Bars
            ResourceBar(label = "CPU LOAD", progress = (telemetry.cpuUsage / 100f).coerceIn(0f, 1f))
            ResourceBar(label = "MEM SYNC", progress = (telemetry.ramUsageMb / 16384f).coerceIn(0f, 1f)) // Assuming 16GB
        }
    }
}

@Composable
private fun HudMetric(label: String, value: String, subValue: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, fontSize = 8.sp, color = NeonGreen.copy(alpha = 0.6f), letterSpacing = 1.sp)
            Text(value, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Text(
            subValue,
            fontSize = 8.sp,
            color = NeonGreen,
            modifier = Modifier
                .border(0.5.dp, NeonGreen.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun ResourceBar(label: String, progress: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 8.sp, color = NeonGreen.copy(alpha = 0.6f), letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(NeonGreen)
            )
        }
    }
}
