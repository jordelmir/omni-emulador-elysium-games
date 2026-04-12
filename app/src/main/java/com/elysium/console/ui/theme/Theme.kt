package com.elysium.console.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════
// Design Tokens — Elysium Console
// ═══════════════════════════════════════════════════════════════

// Primary Palette
val NeonGreen = Color(0xFF00FF8C)
val NeonGreenDim = Color(0xFF00CC70)
val NeonGreenGlow = Color(0x4000FF8C)
val NeonCyan = Color(0xFF00E5FF)
val NeonPurple = Color(0xFFBB86FC)
val NeonAmber = Color(0xFFFFAB00)
val NeonRed = Color(0xFFFF5252)

// v2 Spec Tokens
val GridLine = Color(0x1A00FF8C)
val AlertAmber = Color(0xFFFFB800)

// Surface Palette
val DeepBlack = Color(0xFF080808)
val SurfaceDark = Color(0xFF111318)
val SurfaceCard = Color(0xFF1A1D24)
val SurfaceElevated = Color(0xFF22262F)
val SurfaceBorder = Color(0xFF2A2E38)

// Text Palette
val TextPrimary = Color(0xFFE8EAED)
val TextSecondary = Color(0xFF9AA0A6)
val TextTertiary = Color(0xFF5F6368)

// ═══════════════════════════════════════════════════════════════
// Typography — JetBrains Mono via Google Fonts
// ═══════════════════════════════════════════════════════════════

private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = emptyList()
)

private val jetBrainsMonoFont = GoogleFont("JetBrains Mono")

val JetBrainsMonoFamily = FontFamily(
    Font(googleFont = jetBrainsMonoFont, fontProvider = googleFontProvider, weight = FontWeight.Light),
    Font(googleFont = jetBrainsMonoFont, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = jetBrainsMonoFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = jetBrainsMonoFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = jetBrainsMonoFont, fontProvider = googleFontProvider, weight = FontWeight.Bold)
)

val ElysiumTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
        color = TextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = TextSecondary
    ),
    bodySmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = TextTertiary
    ),
    labelLarge = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
        color = NeonGreen
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = TextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.5.sp,
        color = TextTertiary
    )
)

// ═══════════════════════════════════════════════════════════════
// Color Scheme
// ═══════════════════════════════════════════════════════════════

private val ElysiumColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = DeepBlack,
    primaryContainer = NeonGreenDim,
    onPrimaryContainer = DeepBlack,
    secondary = NeonCyan,
    onSecondary = DeepBlack,
    tertiary = NeonPurple,
    onTertiary = DeepBlack,
    background = DeepBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder,
    error = NeonRed,
    onError = DeepBlack
)

// ═══════════════════════════════════════════════════════════════
// Shapes
// ═══════════════════════════════════════════════════════════════

val ElysiumShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// ═══════════════════════════════════════════════════════════════
// Theme Composable
// ═══════════════════════════════════════════════════════════════

@Composable
fun ElysiumConsoleTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ElysiumColorScheme,
        typography = ElysiumTypography,
        shapes = ElysiumShapes,
        content = content
    )
}
