package com.matanjerbi.cinematheque.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// A dark-room palette: near-black screen, marquee-gold accent, velvet-seat red.
private val Ink = Color(0xFF121014)
private val InkRaised = Color(0xFF1C1A20)
private val InkHigh = Color(0xFF26232B)
private val Marquee = Color(0xFFE3B04B)
private val MarqueeDim = Color(0xFF5A4720)
private val Velvet = Color(0xFFB0413E)
private val Paper = Color(0xFFEDE6DA)
private val PaperDim = Color(0xFFA39C92)

private val CinemaColors = darkColorScheme(
    primary = Marquee,
    onPrimary = Ink,
    primaryContainer = MarqueeDim,
    onPrimaryContainer = Paper,
    secondary = Velvet,
    onSecondary = Paper,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = InkHigh,
    onSurfaceVariant = PaperDim,
    surfaceContainer = InkRaised,
    surfaceContainerHigh = InkHigh,
    outline = Color(0xFF4A4650),
)

private val CinemaType = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

@Composable
fun CinemathequeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CinemaColors, typography = CinemaType, content = content)
}
