package `in`.ragv.onlinegallery.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme

/**
 * Common styling defaults for TV UI components
 */
@OptIn(ExperimentalTvMaterial3Api::class)
object TvComponentDefaults {

    /**
     * Standard overlay button colors (for video controls, navigation buttons, etc.)
     */
    @Composable
    fun overlayButtonColors(
        containerColor: Color = OverlayColors.surfaceLight,
        focusedContainerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        contentColor: Color = OverlayColors.surface,
        focusedContentColor: Color = OverlayColors.surface
    ) = ClickableSurfaceDefaults.colors(
        containerColor = containerColor,
        focusedContainerColor = focusedContainerColor,
        contentColor = contentColor,
        focusedContentColor = focusedContentColor
    )

    /**
     * Larger overlay button colors (for primary actions like play/pause)
     */
    @Composable
    fun overlayButtonColorsPrimary(
        containerColor: Color = OverlayColors.surfaceMedium,
        focusedContainerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        contentColor: Color = OverlayColors.surface,
        focusedContentColor: Color = OverlayColors.surface
    ) = ClickableSurfaceDefaults.colors(
        containerColor = containerColor,
        focusedContainerColor = focusedContainerColor,
        contentColor = contentColor,
        focusedContentColor = focusedContentColor
    )

    /**
     * Standard button scale (1.2x on focus)
     */
    @Composable
    fun buttonScale(
        focusedScale: Float = 1.2f
    ) = ClickableSurfaceDefaults.scale(focusedScale = focusedScale)

    /**
     * Smaller button scale (1.1x on focus)
     */
    @Composable
    fun buttonScaleSmall(
        focusedScale: Float = 1.1f
    ) = ClickableSurfaceDefaults.scale(focusedScale = focusedScale)

    /**
     * Standard button border with thick focus indicator
     */
    @Composable
    fun buttonBorder(
        borderWidth: androidx.compose.ui.unit.Dp = ComponentSizes.BorderThick
    ) = ClickableSurfaceDefaults.border(
        focusedBorder = Border(
            border = BorderStroke(borderWidth, MaterialTheme.colorScheme.primary),
            shape = CircleShape
        )
    )

    /**
     * Thin button border
     */
    @Composable
    fun buttonBorderThin(
        borderWidth: androidx.compose.ui.unit.Dp = ComponentSizes.BorderThin
    ) = ClickableSurfaceDefaults.border(
        focusedBorder = Border(
            border = BorderStroke(borderWidth, MaterialTheme.colorScheme.primary),
            shape = CircleShape
        )
    )

    /**
     * Circular button shape
     */
    @Composable
    fun circleShape() = ClickableSurfaceDefaults.shape(shape = CircleShape)
}
