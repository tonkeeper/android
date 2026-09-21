package com.tonapps.onboading.screens.selector

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.cell.TextCell
import ui.components.moon.container.MoonScaffold

// A seed phrase that is valid both as a standard TON mnemonic and a BIP39 mnemonic (the ~1/256 case).
// The user picks which wallet to import from this ambiguous seed.
enum class WalletKind(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
) {
    Ton(UIKitIcon.ic_ton_transparent, Localization.wallet_kind_ton),
    Multichain(UIKitIcon.ic_multichain, Localization.wallet_kind_multichain),
}

// A selectable wallet kind together with its formatted balance subtitle. The balance is null while it
// is still being fetched, in which case no subtitle is shown.
data class WalletKindItem(
    val kind: WalletKind,
    val balance: String? = null,
)

@Composable
fun WalletKindSelectorScreen(
    items: List<WalletKindItem>,
    selected: WalletKind,
    onSelect: (WalletKind) -> Unit,
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
                    title = stringResource(Localization.choose_wallet),
                    description = stringResource(Localization.choose_wallet_description)
                )

                Spacer(Modifier.height(24.dp))

                items.forEachIndexed { index, item ->
                    WalletKindCell(
                        item = item,
                        selected = item.kind == selected,
                        position = MoonBundlePosition.default(items.size, index),
                        onClick = { onSelect(item.kind) },
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
private fun WalletKindCell(
    item: WalletKindItem,
    selected: Boolean,
    position: MoonBundlePosition,
    onClick: () -> Unit,
) {
    MoonBundleCell(position = position) {
        TextCell(
            title = {
                MoonItemTitle(text = stringResource(item.kind.titleRes))
            },
            subtitle = item.balance?.let { balance ->
                { MoonItemSubtitle(text = balance) }
            },
            image = {
                MoonItemImage(
                    painter = painterResource(item.kind.iconRes),
                    size = 44.dp,
                )
            },
            content = {
                RadioButton(
                    selected = selected,
                    onClick = onClick,
                )
            },
            onClick = onClick,
            minHeight = 76.dp,
        )
    }
}
