package ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

object UIKit {
    val colorScheme: AppColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColorScheme.current

    val typography: UIKitTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalTypography.current

    val shapes: Shapes
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.shapes
}

private val UIKitShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@NonRestartableComposable
fun MoonTheme(
    colorScheme: AppColorScheme,
    content: @Composable () -> Unit
) {
    val typography = rememberAppTypography()

    MaterialTheme(
        colorScheme = colorScheme.toMaterialColorScheme(),
        typography = typography.toMaterialTypography(),
        shapes = UIKitShapes
    ) {
        CompositionLocalProvider(
            LocalAppColorScheme provides colorScheme,
            LocalTypography provides typography,
            LocalRippleConfiguration provides colorScheme.rippleConfiguration,
            LocalIndication provides ripple()
        ) {
            content()
        }
    }
}

@ReadOnlyComposable
private fun AppColorScheme.toMaterialColorScheme(): ColorScheme {
    return ColorScheme(
        primary = buttonPrimary.primaryBackground,
        onPrimary = buttonPrimary.primaryForeground,
        primaryContainer = buttonPrimary.primaryBackgroundHighlighted,
        onPrimaryContainer = buttonPrimary.primaryForeground,
        inversePrimary = buttonPrimary.primaryBackgroundDisable,
        secondary = buttonSecondary.primaryBackground,
        onSecondary = buttonSecondary.primaryForeground,
        secondaryContainer = buttonSecondary.primaryBackgroundHighlighted,
        onSecondaryContainer = buttonSecondary.primaryForeground,
        tertiary = buttonTertiary.primaryBackground,
        onTertiary = buttonTertiary.primaryForeground,
        tertiaryContainer = buttonTertiary.primaryBackgroundHighlighted,
        onTertiaryContainer = buttonTertiary.primaryForeground,
        background = background.page,
        onBackground = text.primary,
        surface = background.content,
        onSurface = text.primary,
        surfaceVariant = background.contentTint,
        onSurfaceVariant = text.secondary,
        surfaceTint = accent.blue,
        inverseSurface = text.primary,
        inverseOnSurface = text.primaryAlternate,
        error = accent.red,
        onError = buttonPrimary.primaryForeground,
        errorContainer = field.errorBackground,
        onErrorContainer = accent.red,
        outline = separator.common,
        outlineVariant = separator.alternate,
        scrim = background.overlayStrong,
        surfaceBright = background.content,
        surfaceContainer = background.content,
        surfaceContainerHigh = background.contentTint,
        surfaceContainerHighest = background.contentAttention,
        surfaceContainerLow = background.page,
        surfaceContainerLowest = background.page,
        surfaceDim = background.page,
    )
}

@ReadOnlyComposable
private fun UIKitTypography.toMaterialTypography(): Typography {
    return Typography(
        displayLarge = num1,
        displayMedium = num2,
        displaySmall = h1,
        headlineLarge = h1,
        headlineMedium = h2,
        headlineSmall = h3,
        titleLarge = h3,
        titleMedium = label1,
        titleSmall = label2,
        bodyLarge = body1,
        bodyMedium = body2,
        bodySmall = body3,
        labelLarge = label1,
        labelMedium = label3,
        labelSmall = body4CAPS,
    )
}
