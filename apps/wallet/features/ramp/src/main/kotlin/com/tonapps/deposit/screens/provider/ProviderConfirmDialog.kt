package com.tonapps.deposit.screens.provider

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonAsyncImage
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonButtonCell
import ui.components.moon.cell.MoonInfoCell
import ui.components.moon.cell.MoonTextCheckboxCell
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview
import ui.theme.Dimens
import ui.theme.UIKit

@Preview
@Composable
private fun ProviderConfirmDialogPreview() {
    ThemedPreview {
        ProviderConfirmDialog(
            title = "Moonpay",
            icon = "",
            description = null,
            onConfirm = {},
            onClose = {},
        )
    }
}

@Composable
fun ProviderConfirmDialog(
    title: String,
    icon: Any,
    description: CharSequence? = null,
    onConfirm: (doNotShowAgain: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)
    MoonModalDialog(navigator = navigator) {
        MoonTopAppBarSimple(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { navigator.close() },
            backgroundColor = Color.Transparent,
        )
        ProviderConfirmDialogContent(
            title = title,
            icon = icon,
            description = description,
            onConfirm = onConfirm,
        )
    }
}

@Composable
private fun ProviderConfirmDialogContent(
    title: String,
    icon: Any,
    description: CharSequence?,
    onConfirm: (doNotShowAgain: Boolean) -> Unit,
) {
    var isChecked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DAppIcon(icon = icon)
        Spacer(modifier = Modifier.height(Dimens.offsetMedium))

        MoonTextContentCell(
            title = title,
            description = description,
        )

        Spacer(modifier = Modifier.height(Dimens.offsetLarge))

        MoonInfoCell(
            text = stringResource(Localization.fiat_open_description),
            painter = painterResource(UIKitIcon.ic_exclamationmark_circle_16),
        )

        MoonButtonCell(
            onClick = { onConfirm(isChecked) },
            text = stringResource(Localization.open),
        )

        MoonTextCheckboxCell(
            text = stringResource(Localization.do_not_show_again),
            isChecked = isChecked,
            onCheckedChanged = { isChecked = it },
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// TODO to design system
@Composable
private fun DAppIcon(icon: Any?) {
    Box(modifier = Modifier.padding(Dimens.offsetMedium)) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(UIKit.colorScheme.background.content),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                if (icon is Painter) {
                    Image(
                        painter = icon,
                        contentDescription = null,
                    )
                } else {
                    MoonAsyncImage(
                        image = icon,
                        size = 72.dp,
                    )
                }
            } else {
                Image(
                    painter = painterResource(UIKitIcon.ic_globe_56),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(UIKit.colorScheme.icon.secondary),
                )
            }
        }
    }
}
