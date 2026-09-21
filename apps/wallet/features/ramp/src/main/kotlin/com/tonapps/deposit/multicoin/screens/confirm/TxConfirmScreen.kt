package com.tonapps.deposit.multicoin.screens.confirm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.core.components.Emoji
import com.tonapps.core.components.assetImageUrl
import com.tonapps.core.components.tokenChainImageUrl
import com.tonapps.core.components.tokenDisplayName
import com.tonapps.core.helper.rememberClipboardManager
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonExpandable
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLargeItemSubtitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.TextCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.shortAddress
import ui.theme.UIKit

@Composable
fun TxConfirmScreen(
    feature: TxConfirmFeature,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
    onSendSuccess: () -> Unit,
    onConfirm: () -> Unit = {},
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    onTopUp: (assetId: String) -> Unit,
) {
    ConfirmScreenScaffold(
        feature = feature,
        onClose = onClose,
        onBack = onBack,
        onSendSuccess = onSendSuccess,
        onConfirm = onConfirm,
        onTopUp = onTopUp,
        onOpenBattery = onOpenBattery,
    ) { tx ->
        TxConfirmBody(
            feature = feature,
            tx = tx,
            onOpenBattery = onOpenBattery,
        )
    }
}

@Composable
private fun TxConfirmBody(
    feature: TxConfirmFeature,
    tx: PendingTransaction,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
) {
    val clipboardManager = rememberClipboardManager()
    val asset = tx.account.asset

    ConfirmHeader(tx)

    Spacer(Modifier.height(32.dp))

    MoonBundleCell {
        Column {
            MoonPropertyBigCell(
                title = {
                    MoonItemTitle(
                        text = stringResource(Localization.wallet),
                        color = UIKit.colorScheme.text.secondary,
                    )
                },
                content = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Emoji(emoji = tx.wallet.emoji)
                        MoonItemTitle(text = tx.wallet.name)
                    }
                },
                onClick = { clipboardManager.copy(tx.wallet.name) },
            )

            val recipientDomain = (tx.request.type as? ConfirmType.Transfer)?.domain
            if (!recipientDomain.isNullOrBlank()) {
                MoonItemDivider()
                MoonPropertyBigCell(
                    title = {
                        MoonItemTitle(
                            text = stringResource(Localization.recipient),
                            color = UIKit.colorScheme.text.secondary,
                        )
                    },
                    content = {
                        MoonItemTitle(text = recipientDomain, maxLines = 2)
                    },
                    onClick = { clipboardManager.copy(recipientDomain) },
                )
            }

            val recipientAddress = tx.recipientAddress()
            if (!recipientAddress.isNullOrBlank()) {
                MoonItemDivider()
                MoonPropertyBigCell(
                    title = {
                        MoonItemTitle(
                            text = stringResource(Localization.recipient_address),
                            color = UIKit.colorScheme.text.secondary,
                        )
                    },
                    content = {
                        MoonItemTitle(text = recipientAddress.shortAddress, maxLines = 2)
                    },
                    onClick = { clipboardManager.copy(recipientAddress) },
                )
            }

            val appName = tx.appName()
            if (!appName.isNullOrBlank()) {
                MoonItemDivider()
                MoonPropertyBigCell(
                    title = {
                        MoonItemTitle(
                            text = "App",
                            color = UIKit.colorScheme.text.secondary,
                        )
                    },
                    content = {
                        MoonItemTitle(
                            text = appName,
                            color = UIKit.colorScheme.accent.blue,
                        )
                    },
                    onClick = { clipboardManager.copy(appName) },
                )
            }

            MoonItemDivider()
            MoonPropertyBigCell(
                title = stringResource(Localization.network),
                value = asset.value.coin.name,
                valueDescription = asset.value.tokenDisplayName(),
            )

            val amount = tx.amount()
            if (amount != null) {
                MoonItemDivider()
                MoonPropertyBigCell(
                    title = stringResource(Localization.amount),
                    value = amount.first,
                    valueDescription = amount.second,
                    onClick = { clipboardManager.copy(amount.first) },
                )
            }

            if (tx.fee.options.isNotEmpty()) {
                TxFeeCell(
                    feature = feature,
                    tx = tx,
                    onOpenBattery = onOpenBattery,
                )
            } else {
                val fee = tx.fee()
                if (fee != null) {
                    FeeCell(fee)
                }
            }

            val comment = tx.comment()
            if (!comment.isNullOrBlank()) {
                MoonItemDivider()
                TextCell(
                    modifier = Modifier.padding(vertical = 8.dp),
                    title = {
                        MoonItemTitle(
                            text = stringResource(Localization.comment),
                            color = UIKit.colorScheme.text.secondary,
                        )
                    },
                    subtitle = {
                        MoonItemTitle(text = comment, maxLines = 10)
                    },
                    minHeight = 82.dp,
                    onClick = { clipboardManager.copy(comment) },
                )
            }

            val content = tx.signingContent()
            if (!content.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                var isVisible by remember { mutableStateOf(false) }
                val jsonRoots = remember(content) { parseExpandableJson(content) } // TODO to type

                TextCell(
                    modifier = Modifier.padding(vertical = 8.dp),
                    title = {
                        Row(
                            modifier = Modifier.clickable { isVisible = !isVisible },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MoonItemTitle(
                                modifier = Modifier.weight(1f),
                                text = "Signing content",
                                color = UIKit.colorScheme.text.secondary,
                            )

                            ExpandedIcon(isVisible)
                        }
                    },
                    subtitle = {
                        MoonExpandable(isVisible) {
                            if (jsonRoots != null) {
                                ExpandableJsonList(roots = jsonRoots)
                            } else {
                                MoonItemTitle(text = content, maxLines = 100)
                            }
                        }
                    },
                    minHeight = 56.dp,
                    onClick = null,
                )
            }
        }
    }
}

@Composable
private fun ConfirmHeader(tx: PendingTransaction) {
    val asset = tx.account.asset
    val assetSymbol = asset.symbol
    val assetImageUrl = asset.assetImageUrl()
    val chainImageUrl = asset.tokenChainImageUrl()

    if (tx.signing !is Signing.Msg) {
        MoonCutBadgedBox(
            badge = if (chainImageUrl != null) {
                { MoonItemImage(image = chainImageUrl, size = 32.dp) }
            } else {
                null
            },
            direction = BadgeDirection.EndBottom,
        ) {
            MoonItemImage(
                image = assetImageUrl,
                size = 96.dp,
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    MoonLargeItemSubtitle(text = stringResource(Localization.confirm_action))
    Spacer(Modifier.height(4.dp))
    Text(
        text = when (val signing = tx.signing) {
            is Signing.Msg -> stringResource(Localization.sign_message)
            is Signing.Tx -> when (signing.value) {
                is Transaction.Call -> stringResource(Localization.call_contract)
                is Transaction.Staking.Claim -> stringResource(Localization.claim_reward)
                is Transaction.Staking.Compound -> stringResource(Localization.compound_stake)
                is Transaction.Staking.Stake -> stringResource(Localization.stake_asset, assetSymbol)
                is Transaction.Staking.Unstake -> stringResource(Localization.unstake_asset, assetSymbol)
                is Transaction.Staking.Restake -> stringResource(Localization.restake_asset, assetSymbol)
                is Transaction.Swap -> stringResource(Localization.swap_asset, assetSymbol)
                is Transaction.Transfer -> stringResource(
                    Localization.jetton_transfer,
                    assetSymbol
                )
            }
        },
        color = UIKit.colorScheme.text.primary,
        style = UIKit.typography.h3,
        textAlign = TextAlign.Center,
    )
}
