package ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
class ButtonSize(
    val height: Dp,
    val textStyle: TextStyle,
    val shape: RoundedCornerShape,
)

val ButtonSizeSmall: ButtonSize
    @Composable
    get() = ButtonSize(
        height = 36.dp,
        textStyle = UIKit.typography.label2,
        shape = RoundedCornerShape(18.dp)
    )

val ButtonSizeMedium: ButtonSize
    @Composable
    get() = ButtonSize(
        height = 48.dp,
        textStyle = UIKit.typography.label1,
        shape = RoundedCornerShape(24.dp)
    )

val ButtonSizeLarge: ButtonSize
    @Composable
    get() = ButtonSize(
        height = 56.dp,
        textStyle = UIKit.typography.label1,
        shape = Shapes.medium
    )

val ButtonColorsPrimary: ButtonColors
    @Composable
    get() = ButtonColors(
        containerColor = UIKit.colorScheme.buttonPrimary.primaryBackground,
        contentColor = UIKit.colorScheme.buttonPrimary.primaryForeground,
        disabledContainerColor = UIKit.colorScheme.buttonPrimary.primaryBackgroundDisable,
        disabledContentColor = UIKit.colorScheme.buttonPrimary.primaryForeground,
    )

val ButtonColorsSecondary: ButtonColors
    @Composable
    get() = ButtonColors(
        containerColor = UIKit.colorScheme.buttonSecondary.primaryBackground,
        contentColor = UIKit.colorScheme.buttonSecondary.primaryForeground,
        disabledContainerColor = UIKit.colorScheme.buttonSecondary.primaryBackgroundDisable,
        disabledContentColor = UIKit.colorScheme.buttonSecondary.primaryForeground,
    )

val ButtonColorsTertiary: ButtonColors
    @Composable
    get() = ButtonColors(
        containerColor = UIKit.colorScheme.buttonTertiary.primaryBackground,
        contentColor = UIKit.colorScheme.buttonTertiary.primaryForeground,
        disabledContainerColor = UIKit.colorScheme.buttonTertiary.primaryBackgroundDisable,
        disabledContentColor = UIKit.colorScheme.buttonTertiary.primaryForeground,
    )

val ButtonColorsGreen: ButtonColors
    @Composable
    get() = ButtonColors(
        containerColor = UIKit.colorScheme.buttonGreen.primaryBackground,
        contentColor = UIKit.colorScheme.buttonGreen.primaryForeground,
        disabledContainerColor = UIKit.colorScheme.buttonGreen.primaryBackgroundDisable,
        disabledContentColor = UIKit.colorScheme.buttonGreen.primaryForeground,
    )

val ButtonColorsOrange: ButtonColors
    @Composable
    get() = ButtonColors(
        containerColor = UIKit.colorScheme.buttonOrange.primaryBackground,
        contentColor = UIKit.colorScheme.buttonOrange.primaryForeground,
        disabledContainerColor = UIKit.colorScheme.buttonOrange.primaryBackgroundDisable,
        disabledContentColor = UIKit.colorScheme.buttonOrange.primaryForeground,
    )
