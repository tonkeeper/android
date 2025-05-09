package uikit.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import uikit.R

@Composable
fun UIKit(
    typography: AppTypography = DefaultAppTypography,
    theme: AppTheme = AppTheme.BLUE,
    content: @Composable () -> Unit
) {

    val colors = rememberAppColors(theme = theme)

    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTypography provides typography
    ) {
        Box(
            modifier = Modifier.background(colors.backgroundPage)
        ) {
            content()
        }
    }
}

@Composable
internal fun rememberAppColors(theme: AppTheme): AppColors {
    val textPrimary = colorResource(id = theme.textPrimaryColorRes)
    val textSecondary = colorResource(id = theme.textSecondaryColorRes)
    val textTertiary = colorResource(id = theme.textTertiaryColorRes)
    val textAccent = colorResource(id = theme.textAccentColorRes)
    val textPrimaryAlternate = colorResource(id = theme.textPrimaryAlternateColorRes)

    val backgroundPage = colorResource(id = theme.backgroundPageColorRes)
    val backgroundTransparent = colorResource(id = theme.backgroundTransparentColorRes)
    val backgroundContent = colorResource(id = theme.backgroundContentColorRes)
    val backgroundContentTint = colorResource(id = theme.backgroundContentTintColorRes)
    val backgroundContentAttention = colorResource(id = theme.backgroundContentAttentionColorRes)
    val backgroundHighlighted = colorResource(id = theme.backgroundHighlightedColorRes)
    val backgroundOverlayStrong = colorResource(id = theme.backgroundOverlayStrongColorRes)
    val backgroundOverlayLight = colorResource(id = theme.backgroundOverlayLightColorRes)
    val backgroundOverlayExtraLight = colorResource(id = theme.backgroundOverlayExtraLightColorRes)

    val iconPrimary = colorResource(id = theme.iconPrimaryColorRes)
    val iconSecondary = colorResource(id = theme.iconSecondaryColorRes)
    val iconTertiary = colorResource(id = theme.iconTertiaryColorRes)
    val iconPrimaryAlternate = colorResource(id = theme.iconPrimaryAlternateColorRes)

    val buttonPrimaryBackground = colorResource(id = theme.buttonPrimaryBackgroundColorRes)
    val buttonPrimaryBackgroundDisabled = colorResource(id = theme.buttonPrimaryBackgroundDisabledColorRes)
    val buttonPrimaryBackgroundHighlighted = colorResource(id = theme.buttonPrimaryBackgroundHighlightedColorRes)
    val buttonPrimaryForeground = colorResource(id = theme.buttonPrimaryForegroundColorRes)

    val buttonSecondaryBackground = colorResource(id = theme.buttonSecondaryBackgroundColorRes)
    val buttonSecondaryBackgroundDisabled = colorResource(id = theme.buttonSecondaryBackgroundDisabledColorRes)
    val buttonSecondaryBackgroundHighlighted = colorResource(id = theme.buttonSecondaryBackgroundHighlightedColorRes)
    val buttonSecondaryForeground = colorResource(id = theme.buttonSecondaryForegroundColorRes)

    val buttonTertiaryBackground = colorResource(id = theme.buttonTertiaryBackgroundColorRes)
    val buttonTertiaryBackgroundDisabled = colorResource(id = theme.buttonTertiaryBackgroundDisabledColorRes)
    val buttonTertiaryBackgroundHighlighted = colorResource(id = theme.buttonTertiaryBackgroundHighlightedColorRes)
    val buttonTertiaryForeground = colorResource(id = theme.buttonTertiaryForegroundColorRes)

    val buttonGreenBackground = colorResource(id = theme.buttonGreenBackgroundColorRes)
    val buttonGreenBackgroundDisabled = colorResource(id = theme.buttonGreenBackgroundDisabledColorRes)
    val buttonGreenBackgroundHighlighted = colorResource(id = theme.buttonGreenBackgroundHighlightedColorRes)

    val buttonOrangeBackground = colorResource(id = theme.buttonOrangeBackgroundColorRes)
    val buttonOrangeBackgroundDisabled = colorResource(id = theme.buttonOrangeBackgroundDisabledColorRes)
    val buttonOrangeBackgroundHighlighted = colorResource(id = theme.buttonOrangeBackgroundHighlightedColorRes)

    val fieldBackground = colorResource(id = theme.fieldBackgroundColorRes)
    val fieldActiveBorder = colorResource(id = theme.fieldActiveBorderColorRes)
    val fieldErrorBorder = colorResource(id = theme.fieldErrorBorderColorRes)
    val fieldErrorBackground = colorResource(id = theme.fieldErrorBackgroundColorRes)

    val accentBlue = colorResource(id = theme.accentBlueColorRes)
    val accentGreen = colorResource(id = theme.accentGreenColorRes)
    val accentRed = colorResource(id = theme.accentRedColorRes)
    val accentOrange = colorResource(id = theme.accentOrangeColorRes)
    val accentPurple = colorResource(id = theme.accentPurpleColorRes)

    val tabBarActiveIcon = colorResource(id = theme.tabBarActiveIconColorRes)
    val tabBarInactiveIcon = colorResource(id = theme.tabBarInactiveIconColorRes)

    val separatorCommon = colorResource(id = theme.separatorCommonColorRes)
    val separatorAlternate = colorResource(id = theme.separatorAlternateColorRes)

    return remember(
        theme,
        textPrimary,
        textSecondary,
        textTertiary,
        textAccent,
        textPrimaryAlternate,
        backgroundPage,
        backgroundTransparent,
        backgroundContent,
        backgroundContentTint,
        backgroundContentAttention,
        backgroundHighlighted,
        backgroundOverlayStrong,
        backgroundOverlayLight,
        backgroundOverlayExtraLight,
        iconPrimary,
        iconSecondary,
        iconTertiary,
        iconPrimaryAlternate,
        buttonPrimaryBackground,
        buttonPrimaryBackgroundDisabled,
        buttonPrimaryBackgroundHighlighted,
        buttonPrimaryForeground,
        buttonSecondaryBackground,
        buttonSecondaryBackgroundDisabled,
        buttonSecondaryBackgroundHighlighted,
        buttonSecondaryForeground,
        buttonTertiaryBackground,
        buttonTertiaryBackgroundDisabled,
        buttonTertiaryBackgroundHighlighted,
        buttonTertiaryForeground,
        buttonGreenBackground,
        buttonGreenBackgroundDisabled,
        buttonGreenBackgroundHighlighted,
        buttonOrangeBackground,
        buttonOrangeBackgroundDisabled,
        buttonOrangeBackgroundHighlighted,
        fieldBackground,
        fieldActiveBorder,
        fieldErrorBorder,
        fieldErrorBackground,
        accentBlue,
        accentGreen,
        accentRed,
        accentOrange,
        accentPurple,
        tabBarActiveIcon,
        tabBarInactiveIcon,
        separatorCommon,
        separatorAlternate
    ) {
        AppColors(
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            textTertiary = textTertiary,
            textAccent = textAccent,
            textPrimaryAlternate = textPrimaryAlternate,
            backgroundPage = backgroundPage,
            backgroundTransparent = backgroundTransparent,
            backgroundContent = backgroundContent,
            backgroundContentTint = backgroundContentTint,
            backgroundContentAttention = backgroundContentAttention,
            backgroundHighlighted = backgroundHighlighted,
            backgroundOverlayStrong = backgroundOverlayStrong,
            backgroundOverlayLight = backgroundOverlayLight,
            backgroundOverlayExtraLight = backgroundOverlayExtraLight,
            iconPrimary = iconPrimary,
            iconSecondary = iconSecondary,
            iconTertiary = iconTertiary,
            iconPrimaryAlternate = iconPrimaryAlternate,
            buttonPrimaryBackground = buttonPrimaryBackground,
            buttonPrimaryBackgroundDisabled = buttonPrimaryBackgroundDisabled,
            buttonPrimaryBackgroundHighlighted = buttonPrimaryBackgroundHighlighted,
            buttonPrimaryForeground = buttonPrimaryForeground,
            buttonSecondaryBackground = buttonSecondaryBackground,
            buttonSecondaryBackgroundDisabled = buttonSecondaryBackgroundDisabled,
            buttonSecondaryBackgroundHighlighted = buttonSecondaryBackgroundHighlighted,
            buttonSecondaryForeground = buttonSecondaryForeground,
            buttonTertiaryBackground = buttonTertiaryBackground,
            buttonTertiaryBackgroundDisabled = buttonTertiaryBackgroundDisabled,
            buttonTertiaryBackgroundHighlighted = buttonTertiaryBackgroundHighlighted,
            buttonTertiaryForeground = buttonTertiaryForeground,
            buttonGreenBackground = buttonGreenBackground,
            buttonGreenBackgroundDisabled = buttonGreenBackgroundDisabled,
            buttonGreenBackgroundHighlighted = buttonGreenBackgroundHighlighted,
            buttonOrangeBackground = buttonOrangeBackground,
            buttonOrangeBackgroundDisabled = buttonOrangeBackgroundDisabled,
            buttonOrangeBackgroundHighlighted = buttonOrangeBackgroundHighlighted,
            fieldBackground = fieldBackground,
            fieldActiveBorder = fieldActiveBorder,
            fieldErrorBorder = fieldErrorBorder,
            fieldErrorBackground = fieldErrorBackground,
            accentBlue = accentBlue,
            accentGreen = accentGreen,
            accentRed = accentRed,
            accentOrange = accentOrange,
            accentPurple = accentPurple,
            tabBarActiveIcon = tabBarActiveIcon,
            tabBarInactiveIcon = tabBarInactiveIcon,
            separatorCommon = separatorCommon,
            separatorAlternate = separatorAlternate,
            isDark = theme.isDark
        )
    }
}

object Dimens {
    val actionSize: Dp @Composable get() = dimensionResource(R.dimen.actionSize)
    val itemHeight: Dp @Composable get() = dimensionResource(R.dimen.itemHeight)
    val barHeight: Dp @Composable get() = dimensionResource(R.dimen.barHeight)
    val shadowSize: Dp @Composable get() = dimensionResource(R.dimen.shadowSize)
    val tertiaryHeight: Dp @Composable get() = dimensionResource(R.dimen.tertiaryHeight)
    val iconSize: Dp @Composable get() = dimensionResource(R.dimen.iconSize)

    val gap: Dp @Composable get() = dimensionResource(R.dimen.gap)

    val cornerExtraExtraSmall: Dp @Composable get() = dimensionResource(R.dimen.cornerExtraExtraSmall)
    val cornerExtraSmall: Dp @Composable get() = dimensionResource(R.dimen.cornerExtraSmall)
    val cornerSmall: Dp @Composable get() = dimensionResource(R.dimen.cornerSmall)
    val cornerMedium: Dp @Composable get() = dimensionResource(R.dimen.cornerMedium)
    val cornerLarge: Dp @Composable get() = dimensionResource(R.dimen.cornerLarge)

    val offsetExtraExtraSmall: Dp @Composable get() = dimensionResource(R.dimen.offsetExtraExtraSmall)
    val offsetExtraSmall: Dp @Composable get() = dimensionResource(R.dimen.offsetExtraSmall)
    val offsetMedium: Dp @Composable get() = dimensionResource(R.dimen.offsetMedium)
    val offsetLarge: Dp @Composable get() = dimensionResource(R.dimen.offsetLarge)

    val bulletSize: Dp @Composable get() = dimensionResource(R.dimen.bulletSize)
    val bulletOffset: Dp @Composable get() = dimensionResource(R.dimen.bulletOffset)
}

object UIKit {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current

    val typography: AppTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTypography.current
}
