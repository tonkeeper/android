package com.tonapps.core.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonButtonCell
import ui.components.moon.cell.MoonButtonCellDefaults
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview
import ui.theme.UIKit

data class InsufficientFundsWallet(
    val emoji: CharSequence,
    val name: String,
)

data class InsufficientFundsAction(
    val text: String,
    val primary: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun InsufficientFundsDialog(
    title: String,
    messages: List<String>,
    actions: List<InsufficientFundsAction>,
    onClose: () -> Unit,
    @DrawableRes iconRes: Int = UIKitIcon.ic_exclamationmark_circle_84,
    tintIcon: Boolean = true,
    wallet: InsufficientFundsWallet? = null,
    note: String? = null,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)

    MoonModalDialog(navigator = navigator) {
        InsufficientFundsContent(
            title = title,
            messages = messages,
            actions = actions,
            iconRes = iconRes,
            tintIcon = tintIcon,
            wallet = wallet,
            note = note,
            onClose = { navigator.close() },
        )
    }
}

@Composable
private fun InsufficientFundsContent(
    title: String,
    messages: List<String>,
    actions: List<InsufficientFundsAction>,
    @DrawableRes iconRes: Int,
    tintIcon: Boolean,
    wallet: InsufficientFundsWallet?,
    note: String?,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MoonTopAppBarSimple(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = onClose,
            backgroundColor = Color.Transparent,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))

            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(84.dp),
                colorFilter = if (tintIcon) {
                    ColorFilter.tint(UIKit.colorScheme.icon.secondary)
                } else {
                    null
                },
            )

            Spacer(Modifier.height(24.dp))

            InsufficientFundsTitle(title = title, wallet = wallet)

            if (messages.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                for (message in messages) {
                    Text(
                        text = message,
                        style = UIKit.typography.body1,
                        color = UIKit.colorScheme.text.secondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (note != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = note,
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.text.tertiary,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))

            for (action in actions) {
                MoonButtonCell(
                    text = action.text,
                    colors = if (action.primary) {
                        MoonButtonCellDefaults.ButtonColorsPrimary
                    } else {
                        MoonButtonCellDefaults.ButtonColorsSecondary
                    },
                    onClick = {
                        onClose()
                        action.onClick()
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InsufficientFundsTitle(
    title: String,
    wallet: InsufficientFundsWallet?,
) {
    val style = UIKit.typography.h2
    val color = UIKit.colorScheme.text.primary
    if (wallet == null) {
        Text(
            text = title,
            style = style,
            color = color,
            textAlign = TextAlign.Center,
        )
        return
    }

    val emojiId = "wallet_emoji"
    val emojiSize = 20.dp
    val emojiSizeSp = with(LocalDensity.current) { emojiSize.toSp() }
    Text(
        text = buildAnnotatedString {
            append(title)
            append(" ")
            appendInlineContent(emojiId)
            append(" ")
            append(wallet.name)
        },
        inlineContent = mapOf(
            emojiId to InlineTextContent(
                Placeholder(
                    width = emojiSizeSp,
                    height = emojiSizeSp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                Emoji(emoji = wallet.emoji, size = emojiSize)
            },
        ),
        style = style,
        color = color,
        textAlign = TextAlign.Center,
    )
}

@Preview
@Composable
private fun InsufficientFundsSendPreview() {
    ThemedPreview {
        InsufficientFundsContent(
            title = stringResource(Localization.insufficient_balance_title),
            messages = listOf(
                stringResource(
                    Localization.insufficient_balance_fees,
                    "1.25 TON",
                    "0.12 TON",
                ),
            ),
            actions = listOf(
                InsufficientFundsAction(
                    text = stringResource(Localization.recharge_battery),
                    primary = true,
                    onClick = {},
                ),
                InsufficientFundsAction(
                    text = stringResource(Localization.buy_ton, "TON"),
                    onClick = {},
                ),
            ),
            iconRes = UIKitIcon.ic_exclamationmark_circle_84,
            tintIcon = true,
            wallet = null,
            note = null,
            onClose = {},
        )
    }
}

@Preview
@Composable
private fun InsufficientFundsMigrationPreview() {
    ThemedPreview {
        InsufficientFundsContent(
            title = stringResource(Localization.migration_insufficient_ton_title),
            messages = listOf(
                stringResource(Localization.migration_insufficient_required, "0.05 TON"),
                stringResource(Localization.migration_insufficient_balance, "0.01 TON"),
            ),
            actions = listOf(
                InsufficientFundsAction(
                    text = stringResource(Localization.continue_action),
                    primary = true,
                    onClick = {},
                ),
                InsufficientFundsAction(
                    text = stringResource(Localization.migration_deposit_ton),
                    onClick = {},
                ),
            ),
            iconRes = UIKitIcon.ic_exclamationmark_circle_84,
            tintIcon = true,
            wallet = InsufficientFundsWallet(emoji = "💎", name = "Wallet"),
            note = stringResource(
                Localization.migration_insufficient_partial_note,
                "TRON",
                "TON",
                "GRAM",
            ),
            onClose = {},
        )
    }
}

@Preview
@Composable
private fun InsufficientFundsBalancePreview() {
    ThemedPreview {
        InsufficientFundsContent(
            title = stringResource(Localization.insufficient_balance_title),
            messages = listOf(
                stringResource(
                    Localization.insufficient_balance_default,
                    "12 USDT",
                    "0.4 USDT",
                ),
            ),
            actions = listOf(
                InsufficientFundsAction(
                    text = stringResource(Localization.buy_ton, "USDT"),
                    primary = true,
                    onClick = {},
                ),
            ),
            iconRes = UIKitIcon.ic_empty_battery_accent_flash_128,
            tintIcon = false,
            wallet = null,
            note = null,
            onClose = {},
        )
    }
}

@Preview
@Composable
private fun InsufficientFundsFeePreview() {
    ThemedPreview {
        InsufficientFundsContent(
            title = stringResource(Localization.confirmation_insufficient_balance, "TON"),
            messages = listOf(
                stringResource(
                    Localization.confirmation_insufficient_balance_description,
                    "TON",
                    "0.012 TON",
                    "TON",
                ),
            ),
            actions = listOf(
                InsufficientFundsAction(
                    text = stringResource(Localization.recharge_battery),
                    primary = true,
                    onClick = {},
                ),
                InsufficientFundsAction(
                    text = stringResource(Localization.deposit_asset, "TON"),
                    onClick = {},
                ),
            ),
            iconRes = UIKitIcon.ic_empty_battery_accent_flash_128,
            tintIcon = false,
            wallet = null,
            note = null,
            onClose = {},
        )
    }
}
