package com.elysium.console.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.console.domain.model.TelemetryData
import com.elysium.console.ui.theme.NeonAmber
import com.elysium.console.ui.theme.NeonCyan
import com.elysium.console.ui.theme.NeonGreen
import com.elysium.console.ui.theme.NeonGreenGlow
import com.elysium.console.ui.theme.NeonRed
import com.elysium.console.ui.theme.SurfaceDark
import com.elysium.console.ui.theme.TextSecondary

/**
 * Bottom telemetry bar displaying real-time CPU%, RAM, and FPS metrics
 * with animated circular progress indicators and neon color coding.
 */
@Composable
fun TelemetryBar(
    telemetry: TelemetryData,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // Top glow border
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            NeonGreen.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .background(
                Brush.verticalGradient(
                    listOf(
                        SurfaceDark.copy(alpha = 0.95f),
                        SurfaceDark
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // FPS Gauge
            TelemetryGauge(
                label = "FPS",
                value = "%.0f".format(telemetry.fps),
                progress = (telemetry.fps / 60.0).toFloat().coerceIn(0f, 1f),
                color = when {
                    telemetry.fps >= 55 -> NeonGreen
                    telemetry.fps >= 30 -> NeonAmber
                    else -> NeonRed
                }
            )

            TelemetrySeparator()

            // CPU Gauge
            TelemetryGauge(
                label = "CPU",
                value = "%.0f%%".format(telemetry.cpuUsage),
                progress = (telemetry.cpuUsage / 100f).coerceIn(0f, 1f),
                color = when {
                    telemetry.cpuUsage < 60f -> NeonCyan
                    telemetry.cpuUsage < 85f -> NeonAmber
                    else -> NeonRed
                }
            )

            TelemetrySeparator()

            // RAM Gauge
            TelemetryGauge(
                label = "RAM",
                value = "%.0f".format(telemetry.ramUsageMb),
                unit = "MB",
                progress = (telemetry.ramUsageMb / 1024f).coerceIn(0f, 1f),
                color = NeonCyan
            )

            TelemetrySeparator()

            // Thermal State
            TelemetryGauge(
                label = "TEMP",
                value = telemetry.thermalLabel,
                progress = (telemetry.thermalState / 3f).coerceIn(0f, 1f),
                color = when (telemetry.thermalState) {
                    0 -> NeonGreen
                    1 -> NeonAmber
                    2 -> NeonRed.copy(alpha = 0.8f)
                    else -> NeonRed
                }
            )
        }
    }
}

/**
 * Individual telemetry gauge with animated circular progress ring.
 */
@Composable
private fun TelemetryGauge(
    label: String,
    value: String,
    progress: Float,
    color: Color,
    unit: String = "",
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500),
        label = "gauge_$label"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Circular gauge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp)
        ) {
            Canvas(modifier = Modifier.size(44.dp)) {
                val strokeWidth = 3.dp.toPx()

                // Background ring
                drawArc(
                    color = color.copy(alpha = 0.15f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Progress ring
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Value text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        lineHeight = 10.sp
                    )
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = color.copy(alpha = 0.6f),
                            fontSize = 6.sp,
                            lineHeight = 7.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextSecondary,
                fontSize = 8.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

/**
 * Vertical separator between telemetry gauges.
 */
@Composable
private fun TelemetrySeparator() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        NeonGreenGlow,
                        Color.Transparent
                    )
                )
            )
    )
}
