package `in`.ragv.onlinegallery.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Consistent spacing and sizing system for the app
 */
object Spacing {
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 24.dp
    val ExtraLarge = 32.dp
    val ExtraExtraLarge = 48.dp
}

/**
 * Component-specific sizes
 */
object ComponentSizes {
    // Button sizes
    val ButtonSmall = 48.dp
    val ButtonMedium = 80.dp
    val ButtonLarge = 96.dp

    // Icon sizes
    val IconSmall = 32.dp
    val IconMedium = 48.dp
    val IconLarge = 56.dp
    val IconExtraLarge = 64.dp

    // Border widths
    val BorderThin = 4.dp
    val BorderThick = 6.dp
}

/**
 * Grid layout configurations
 */
object GridConfig {
    // Album grid
    const val AlbumColumns = 3
    val AlbumHorizontalSpacing = 24.dp
    val AlbumVerticalSpacing = 24.dp

    // Media grid
    const val MediaColumns = 4
    val MediaHorizontalSpacing = 16.dp
    val MediaVerticalSpacing = 16.dp
}
