package uikit.compose.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uikit.compose.AppTheme
import uikit.compose.Dimens
import uikit.compose.UIKit

@Composable
private fun UIKitBaseButton(
    onClick: () -> Unit,
    text: String,
    textStyle: TextStyle,
    buttonColors: ButtonColors,
    height: Dp = Dimens.itemHeight,
    shape: Shape = RoundedCornerShape(Dimens.cornerMedium),
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(Modifier.height(height)),
        shape = shape,
        colors = buttonColors,
        elevation = elevation,
        contentPadding = contentPadding,
    ) {
        Text(
            text = text,
            style = textStyle
        )
    }
}

@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = UIKit.typography.label1
) {
    UIKitBaseButton(
        onClick = onClick,
        text = text,
        textStyle = textStyle,
        buttonColors = ButtonDefaults.buttonColors(
            containerColor = UIKit.colors.buttonPrimaryBackground,
            contentColor = UIKit.colors.buttonPrimaryForeground,
            disabledContainerColor = UIKit.colors.buttonPrimaryBackgroundDisabled,
            disabledContentColor = UIKit.colors.buttonPrimaryForeground.copy(alpha = 0.48f)
        ),
        modifier = modifier,
        enabled = enabled
    )
}


@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = UIKit.typography.label1
) {
    UIKitBaseButton(
        onClick = onClick,
        text = text,
        textStyle = textStyle,
        buttonColors = ButtonDefaults.buttonColors(
            containerColor = UIKit.colors.buttonSecondaryBackground,
            contentColor = UIKit.colors.buttonSecondaryForeground,
            disabledContainerColor = UIKit.colors.buttonSecondaryBackgroundDisabled,
            disabledContentColor = UIKit.colors.buttonSecondaryForeground.copy(alpha = 0.48f)
        ),
        modifier = modifier,
        enabled = enabled
    )
}

@Composable
fun GreenButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = UIKit.typography.label1
) {
    val contentColor = UIKit.colors.buttonSecondaryForeground
    val disabledContentColor = contentColor.copy(alpha = 0.48f)

    UIKitBaseButton(
        onClick = onClick,
        text = text,
        textStyle = textStyle,
        buttonColors = ButtonDefaults.buttonColors(
            containerColor = UIKit.colors.buttonGreenBackground,
            contentColor = contentColor,
            disabledContainerColor = UIKit.colors.buttonGreenBackgroundDisabled,
            disabledContentColor = disabledContentColor
        ),
        modifier = modifier,
        enabled = enabled
    )
}

@Composable
fun OrangeButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = UIKit.typography.label1
) {
    UIKitBaseButton(
        onClick = onClick,
        text = text,
        textStyle = textStyle,
        buttonColors = ButtonDefaults.buttonColors(
            containerColor = UIKit.colors.buttonOrangeBackground,
            contentColor = UIKit.colors.buttonSecondaryForeground, // Из XML стиля
            disabledContainerColor = UIKit.colors.buttonOrangeBackgroundDisabled,
            disabledContentColor = UIKit.colors.buttonSecondaryForeground.copy(alpha = 0.48f) // Предполагаем alpha
        ),
        modifier = modifier,
        enabled = enabled
    )
}

@Composable
fun TertiaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = UIKit.typography.label2
) {
    UIKitBaseButton(
        onClick = onClick,
        text = text,
        textStyle = textStyle,
        buttonColors = ButtonDefaults.buttonColors(
            containerColor = UIKit.colors.buttonTertiaryBackground,
            contentColor = UIKit.colors.buttonTertiaryForeground,
            disabledContainerColor = UIKit.colors.buttonTertiaryBackgroundDisabled,
            disabledContentColor = UIKit.colors.buttonTertiaryForeground.copy(alpha = 0.48f)
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = Dimens.offsetMedium),
        elevation = null
    )
}

@Composable
fun SmallPrimaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = UIKit.typography.label2
) {
    UIKitBaseButton(
        onClick = onClick,
        text = text,
        textStyle = textStyle,
        buttonColors = ButtonDefaults.buttonColors(
            containerColor = UIKit.colors.buttonPrimaryBackground,
            contentColor = UIKit.colors.buttonPrimaryForeground,
            disabledContainerColor = UIKit.colors.buttonPrimaryBackgroundDisabled,
            disabledContentColor = UIKit.colors.buttonPrimaryForeground.copy(alpha = 0.48f)
        ),
        height = Dimens.tertiaryHeight,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = Dimens.gap, vertical = Dimens.offsetExtraSmall)
    )
}

@Composable
fun SmallSecondaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = UIKit.typography.label2
) {
    UIKitBaseButton(
        onClick = onClick,
        text = text,
        textStyle = textStyle,
        buttonColors = ButtonDefaults.buttonColors(
            containerColor = UIKit.colors.buttonSecondaryBackground,
            contentColor = UIKit.colors.buttonSecondaryForeground,
            disabledContainerColor = UIKit.colors.buttonSecondaryBackgroundDisabled,
            disabledContentColor = UIKit.colors.buttonSecondaryForeground.copy(alpha = 0.48f)
        ),
        height = Dimens.tertiaryHeight,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = Dimens.gap, vertical = Dimens.offsetExtraSmall)
    )
}

@Composable
fun SmallTertiaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = UIKit.typography.label2
) {
    UIKitBaseButton(
        onClick = onClick,
        text = text,
        textStyle = textStyle,
        buttonColors = ButtonDefaults.buttonColors(
            containerColor = UIKit.colors.buttonTertiaryBackground,
            contentColor = UIKit.colors.buttonTertiaryForeground,
            disabledContainerColor = UIKit.colors.buttonTertiaryBackgroundDisabled,
            disabledContentColor = UIKit.colors.buttonTertiaryForeground.copy(alpha = 0.48f)
        ),
        height = Dimens.tertiaryHeight,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = Dimens.gap, vertical = Dimens.offsetExtraSmall)
    )
}

@Preview(name = "Primary Enabled", showBackground = true, group = "Buttons")
@Composable
private fun PrimaryButtonEnabledPreview() {
    UIKit(theme = AppTheme.BLUE) { PrimaryButton(onClick = {}, text = "Primary") }
}

@Preview(name = "Secondary Enabled", showBackground = true, group = "Buttons")
@Composable
private fun SecondaryButtonEnabledPreview() {
    UIKit(theme = AppTheme.BLUE) { SecondaryButton(onClick = {}, text = "Secondary") }
}

@Preview(name = "Green Enabled", showBackground = true, group = "Buttons")
@Composable
private fun GreenButtonEnabledPreview() {
    UIKit(theme = AppTheme.BLUE) { GreenButton(onClick = {}, text = "Green") }
}

@Preview(name = "Orange Enabled", showBackground = true, group = "Buttons")
@Composable
private fun OrangeButtonEnabledPreview() {
    UIKit(theme = AppTheme.BLUE) { OrangeButton(onClick = {}, text = "Orange") }
}

@Preview(name = "Tertiary Enabled", showBackground = true, group = "Buttons")
@Composable
private fun TertiaryButtonEnabledPreview() {
    UIKit(theme = AppTheme.BLUE) { TertiaryButton(onClick = {}, text = "Tertiary") }
}

@Preview(name = "Small Primary Enabled", showBackground = true, group = "Buttons")
@Composable
private fun SmallPrimaryButtonEnabledPreview() {
    UIKit(theme = AppTheme.BLUE) { SmallPrimaryButton(onClick = {}, text = "Small Primary") }
}

@Preview(name = "Small Secondary Enabled", showBackground = true, group = "Buttons")
@Composable
private fun SmallSecondaryButtonEnabledPreview() {
    UIKit(theme = AppTheme.BLUE) { SmallSecondaryButton(onClick = {}, text = "Small Secondary") }
}

@Preview(name = "Small Tertiary Enabled", showBackground = true, group = "Buttons")
@Composable
private fun SmallTertiaryButtonEnabledPreview() {
    UIKit(theme = AppTheme.BLUE) { SmallTertiaryButton(onClick = {}, text = "Small Tertiary") }
}




