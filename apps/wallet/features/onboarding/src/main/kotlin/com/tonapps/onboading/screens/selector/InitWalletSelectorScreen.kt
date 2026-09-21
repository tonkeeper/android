package com.tonapps.onboading.screens.selector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.num.FiatRate
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.core.components.AccountCell
import com.tonapps.core.components.walletTypeLabel
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import ui.shortAddress
import ui.components.moon.MoonCircleIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonWarningCell
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.container.MoonScaffold
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.theme.UIKit

// Screen-specific decoration on top of the shared "W5" label, per the version picker mock.
private const val BEST_VERSION_SUFFIX = " · BEST"

@Composable
fun WalletSelectorScreen(
    accounts: List<WalletVersionItem>,
    selectedAddress: String?,
    onSelect: (AccountWithDetails) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MoonScaffold(modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                MoonTextContentCell(
                    title = stringResource(Localization.choose_wallet_version),
                    description = stringResource(Localization.choose_wallet_version_description)
                )

                Spacer(Modifier.height(24.dp))

                accounts.forEachIndexed { index, item ->
                    WalletVersionCell(
                        item = item,
                        selected = item.account.data.displayAddress == selectedAddress,
                        position = MoonBundlePosition.default(accounts.size, index),
                        onClick = { onSelect(item.account) },
                    )
                }

                var isInfoVisible by remember { mutableStateOf(false) }

                MoonWarningCell(
                    text = stringResource(Localization.learn_wallet_versions),
                    onClick = { isInfoVisible = true }
                )

                if (isInfoVisible) {
                    WalletSelectorInfoDialog(
                        onClose = { isInfoVisible = false }
                    )
                }
            }

            MoonBottomButtonCell(
                modifier = Modifier.align(Alignment.BottomCenter),
                text = stringResource(Localization.continue_action)
            ) {
                onContinue()
            }
        }
    }
}

@Composable
private fun WalletVersionCell(
    item: WalletVersionItem,
    selected: Boolean,
    position: MoonBundlePosition,
    onClick: () -> Unit,
) {
    val account = item.account

    AccountCell(
        asset = account.asset,
        image = { WalletVersionIcon(addressType = account.address.type) },
        title = {
            MoonItemTitle(text = account.data.displayAddress.shortAddress)
        },
        subtitle = {
            MoonItemSubtitle(text = item.fiat?.let { versionSubtitle(it) } ?: account.formattedBalance)
        },
        tags = {
            val label = account.address.walletTypeLabel()
            if (label != null) {
                MoonLabel(
                    text = when (account.address.type) {
                        Address.Type.TonV5R1 -> label + BEST_VERSION_SUFFIX
                        else -> label
                    },
                    colors = when (account.address.type) {
                        Address.Type.TonV5R1 -> MoonLabelDefault.success()
                        Address.Type.TonV4R2 -> MoonLabelDefault.grey()
                        else -> MoonLabelDefault.grey()
                    }
                )
            }
        },
        position = position,
        onClick = onClick,
        content = {
            RadioButton(
                selected = selected,
                onClick = onClick,
            )
        },
    )
}

@Composable
private fun versionSubtitle(fiat: WalletVersionFiat): String {
    val balance = Formatter.formatFiat(
        value = fiat.balance,
        rate = FiatRate(BigDecimal.ONE, fiat.currency),
    )
    if (fiat.nftCount <= 0) {
        return balance
    }
    val nfts = pluralStringResource(Plurals.nft_count, fiat.nftCount, fiat.nftCount)
    return stringResource(Localization.fiat_nft_subtitle, balance, nfts)
}

@Composable
private fun WalletVersionIcon(addressType: Address.Type) {
    val accent = addressType == Address.Type.TonV5R1
    MoonCircleIcon(
        painter = painterResource(UIKitIcon.ic_ton_28),
        size = 44.dp,
        iconSize = 28.dp,
        color = if (accent) {
            UIKit.colorScheme.accent.blue.copy(alpha = 0.12f)
        } else {
            UIKit.colorScheme.background.contentTint
        },
        tint = if (accent) {
            UIKit.colorScheme.accent.blue
        } else {
            UIKit.colorScheme.icon.secondary
        },
    )
}


@Composable
private fun WalletSelectorInfoDialog(
    onClose: () -> Unit
) {
    val navigator = rememberDialogNavigator { onClose() }

    MoonModalDialog(
        navigator = navigator,
    ) {
        MoonTopAppBarSimple(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { navigator.close() },
            backgroundColor = Color.Transparent,
        )

        MoonTextContentCell(
            title = stringResource(Localization.about_wallet_versions_title),
            description = stringResource(Localization.about_wallet_versions_description)
        )

        Spacer(Modifier.height(24.dp))

        MoonBundleCell {
            Column(
                modifier = Modifier.padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WalletVersionInfoItem(
                    text = stringResource(Localization.wallet_version_w5_description)
                )
                WalletVersionInfoItem(
                    text = stringResource(Localization.wallet_version_v4r2_description)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        MoonBottomButtonCell(text = stringResource(Localization.ok)) {
            onClose()
        }
    }
}

@Composable
private fun WalletVersionInfoItem(
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp),
            text = "•",
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.primary,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = text,
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.primary,
        )
    }
}
