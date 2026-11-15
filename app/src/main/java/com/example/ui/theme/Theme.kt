package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = ForestEmerald,
    onPrimary = ForestOnPrimaryLight,
    primaryContainer = SageContainerLight,
    onPrimaryContainer = SageOnContainerLight,
    secondary = TerracottaLight,
    onSecondary = ForestOnPrimaryLight,
    secondaryContainer = TerracottaContainerLight,
    onSecondaryContainer = TerracottaOnContainerLight,
    tertiary = EucalyptusLight,
    onTertiary = ForestOnPrimaryLight,
    background = GardenMistLight,
    onBackground = GardenInkLight,
    surface = GardenCardLight,
    onSurface = GardenInkLight,
    surfaceVariant = GardenSurfaceVariantLight,
    onSurfaceVariant = GardenMutedLight,
    outline = GardenOutlineLight,
    error = WiltCoralLight,
    onError = ForestOnPrimaryLight
)

private val DarkColorScheme = darkColorScheme(
    primary = ForestEmeraldDark,
    onPrimary = ForestOnPrimaryDark,
    primaryContainer = SageContainerDark,
    onPrimaryContainer = SageOnContainerDark,
    secondary = TerracottaDark,
    onSecondary = ForestOnPrimaryDark,
    secondaryContainer = TerracottaContainerDark,
    onSecondaryContainer = TerracottaOnContainerDark,
    tertiary = EucalyptusDark,
    onTertiary = ForestOnPrimaryDark,
    background = GardenNightDark,
    onBackground = GardenInkDark,
    surface = GardenCardDark,
    onSurface = GardenInkDark,
    surfaceVariant = GardenSurfaceVariantDark,
    onSurfaceVariant = GardenMutedDark,
    outline = GardenOutlineDark,
    error = WiltCoralDark,
    onError = ForestOnPrimaryDark
)

private val FocusGardenShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun FocusGardenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FocusGardenTypography,
        shapes = FocusGardenShapes,
        content = content
    )
}
