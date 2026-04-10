package ui.components.moon.cell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.theme.UIKit

object MoonButtonCellDefaults {

    val ButtonColorsPrimary: ButtonColors
        @Composable
        get() = ButtonColors(
            containerColor = UIKit.colorScheme.buttonPrimary.primaryBackground,
            contentColor = UIKit.colorScheme.buttonPrimary.primaryForeground,
            disabledContainerColor = UIKit.colorScheme.buttonPrimary.primaryBackgroundDisable,
            disabledContentColor = UIKit.colorScheme.buttonPrimary.primaryForeground.copy(alpha = 0.49f),
        )

    val ButtonColorsSecondary: ButtonColors
        @Composable
        get() = ButtonColors(
            containerColor = UIKit.colorScheme.buttonSecondary.primaryBackground,
            contentColor = UIKit.colorScheme.buttonSecondary.primaryForeground,
            disabledContainerColor = UIKit.colorScheme.buttonSecondary.primaryBackgroundDisable.copy(alpha = 0.49f),
            disabledContentColor = UIKit.colorScheme.buttonSecondary.primaryForeground.copy(alpha = 0.49f),
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

}

@Composable
fun MoonButtonCell(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = MoonButtonCellDefaults.ButtonColorsPrimary,
    contentPadding: PaddingValues = remember { PaddingValues(16.dp) },
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier
            .fillMaxWidth()
            .background(UIKit.colorScheme.background.transparent)
            .padding(contentPadding)
            .height(56.dp),
        onClick = onClick,
        elevation = null,
        enabled = enabled,
        colors = colors,
        shape = UIKit.shapes.large,
    ) {
        Text(
            text = text,
            maxLines = 1,
            style = UIKit.typography.label1,
        )
    }
}