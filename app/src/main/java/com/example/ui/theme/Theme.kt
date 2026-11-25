package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val TradePulseColorScheme = darkColorScheme(
    primary = BullEmerald,
    onPrimary = ObsidianBg,
    primaryContainer = BullEmeraldDim,
    onPrimaryContainer = BullEmerald,
    secondary = AiCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = AiCyanDim,
    onSecondaryContainer = AiCyan,
    tertiary = RiskAmber,
    onTertiary = ObsidianBg,
    tertiaryContainer = RiskAmberDim,
    onTertiaryContainer = RiskAmber,
    error = BearCoral,
    onError = ObsidianBg,
    errorContainer = BearCoralDim,
    onErrorContainer = BearCoral,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = TerminalSurface,
    onSurface = TextPrimary,
    surfaceVariant = TerminalSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = TerminalSurface,
    surfaceContainer = TerminalSurfaceElevated,
    surfaceContainerHigh = TerminalSurfaceHighest,
    outline = TerminalBorder,
    outlineVariant = TerminalBorder
)

val TradePulseShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun TradePulseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TradePulseColorScheme,
        typography = TradePulseTypography,
        shapes = TradePulseShapes,
        content = content
    )
}
