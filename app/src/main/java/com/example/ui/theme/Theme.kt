package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NothingColorScheme = darkColorScheme(
    primary = NothingCrimsonRed,
    onPrimary = NothingPureWhite,
    primaryContainer = NothingCrimsonDim,
    onPrimaryContainer = NothingPureWhite,
    secondary = NothingPureWhite,
    onSecondary = NothingPureBlack,
    secondaryContainer = NothingInteractiveSurface,
    onSecondaryContainer = NothingPureWhite,
    tertiary = NothingSignalAmber,
    onTertiary = NothingPureBlack,
    background = NothingPureBlack,
    onBackground = NothingPureWhite,
    surface = NothingCarbonSurface,
    onSurface = NothingPureWhite,
    surfaceVariant = NothingElevatedSurface,
    onSurfaceVariant = NothingTechnicalGray,
    outline = NothingBorderHairline,
    outlineVariant = NothingMutedGray,
    error = NothingCrimsonRed,
    onError = NothingPureWhite
)

val NothingShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun GlyphFlowTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NothingColorScheme,
        typography = Typography,
        shapes = NothingShapes,
        content = content
    )
}
