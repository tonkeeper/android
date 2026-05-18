package com.tonapps.deposit.screens.qr

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.lib.blockchain.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonButtonCell
import ui.components.moon.cell.MoonButtonCellDefaults
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.painterResource
import ui.preview.ThemedPreview

@Preview(showSystemUi = true)
@Composable
private fun QrEnableTronDialogPreview() {
    ThemedPreview(true) {
        QrEnableTronDialog(isBatteryEnabled = true, onEnable = {}, onClose = {})
    }
}

@Composable
fun QrEnableTronDialog(
    isBatteryEnabled: Boolean,
    onEnable: () -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)
    MoonModalDialog(navigator = navigator) {
        MoonTopAppBarSimple(
            title = stringResource(Localization.deposit),
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { navigator.close() },
            backgroundColor = Color.Transparent,
        )
        MoonSurface {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MoonCutBadgedBox(
                    badge = {
                        MoonItemImage(
                            painterResource(UIKitIcon.ic_tron),
                            size = 40.dp,
                        )
                    },
                    direction = BadgeDirection.EndBottom,
                ) {
                    MoonItemImage(
                        painter = painterResource(R.drawable.ic_usdt_with_bg),
                        size = 96.dp
                    )
                }

                Spacer(Modifier.height(16.dp))

                MoonTextContentCell(
                    title = "USD₮ TRC20",
                    description = if (isBatteryEnabled) {
                        stringResource(Localization.tron_toggle_text)
                    } else {
                        stringResource(Localization.tron_toggle_trc_text)
                    },
                )

                Spacer(Modifier.height(32.dp))

                MoonButtonCell(
                    stringResource(Localization.enable_usdt_tron),
                    contentPadding = remember { PaddingValues(horizontal = 16.dp) },
                ) {
                    onEnable()
                    navigator.close()
                }

                MoonButtonCell(
                    text = stringResource(Localization.later),
                    colors = MoonButtonCellDefaults.ButtonColorsSecondary,
                ) {
                    navigator.close()
                }
            }
        }
    }
}