package com.frynetworks.pow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Palette carried over from the existing FryMiner control panel: crimson on near-black.
val Crimson = Color(0xFFDC143C)
val CrimsonDark = Color(0xFF8B0000)
val Salmon = Color(0xFFFF6B6B)
val Ink = Color(0xFF0A0A0A)
val Panel = Color(0xFF0F0F0F)
val Field = Color(0xFF1A1A1A)
val Edge = Color(0xFF2A2A2A)
val Success = Color(0xFF4CAF50)
val Warning = Color(0xFFFFA500)
val Danger = Color(0xFFF44336)
val Muted = Color(0xFF888888)
val LogGreen = Color(0xFF00FF00)

private val FryColors = darkColorScheme(
    primary = Crimson,
    onPrimary = Color.White,
    primaryContainer = CrimsonDark,
    onPrimaryContainer = Color(0xFFFFDADA),
    secondary = Salmon,
    onSecondary = Color.Black,
    background = Ink,
    onBackground = Color(0xFFEDEDED),
    surface = Panel,
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Field,
    onSurfaceVariant = Color(0xFFCCCCCC),
    outline = Edge,
    error = Danger,
    onError = Color.White,
)

// 12sp floor: anything smaller is unreadable at ten feet on a 720p TV.
private val FryTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp),
    labelSmall = TextStyle(fontSize = 12.sp),
)

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = LogGreen)

@Composable
fun FryPoWTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = FryColors,
        typography = FryTypography,
        content = content,
    )
}
