package `in`.ragv.onlinegallery.ui.theme

import androidx.compose.ui.graphics.Color

// Primary brand colors
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Semantic overlay colors
object OverlayColors {
    val surface = Color.White
    val onSurface = Color.Black

    // Surface overlays with different opacity levels
    val surfaceLight = Color.White.copy(alpha = 0.2f)
    val surfaceMedium = Color.White.copy(alpha = 0.3f)
    val surfaceHeavy = Color.White.copy(alpha = 0.9f)

    // Scrim overlays
    val scrimLight = Color.Black.copy(alpha = 0.3f)
    val scrimMedium = Color.Black.copy(alpha = 0.5f)
    val scrimHeavy = Color.Black.copy(alpha = 0.7f)
    val scrimExtraHeavy = Color.Black.copy(alpha = 0.8f)

    // Progress indicators
    val progressTrack = Color.White.copy(alpha = 0.3f)
}