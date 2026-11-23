package com.tonapps.onboading.screens.selector

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import kotlinx.parcelize.Parcelize
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import ui.components.moon.MoonCircleIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.cell.TextCell
import ui.components.moon.container.MoonScaffold
import ui.theme.UIKit

// A seed phrase that holds more than one kind of wallet: valid both as a standard TON mnemonic and
// a BIP39 mnemonic (the ~1/256 case), or a BIP39 one with an active Telegram wallet.
// The user picks which wallet to import from it.
enum class WalletKind(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    // A bare glyph drawn on the cell's icon circle, unlike the full-size TON and Multichain images.
    val iconOnCircle: Boolean = false,
) {
    Ton(UIKitIcon.ic_ton_transparent, Localization.wallet_kind_ton),
    Multichain(UIKitIcon.ic_multichain, Localization.wallet_kind_multichain),
    Telegram(UIKitIcon.ic_telegram_28, Localization.wallet_kind_telegram, iconOnCircle = true),
}

// A selectable wallet kind together with its formatted balance subtitle. The balance is null while it
// is still being fetched, in which case no subtitle is shown.
@Parcelize
data class WalletKindItem(
    val kind: WalletKind,
    val balance: String? = null,
) : Parcelable

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
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = stringResource(Localization.choose_wallet),
                    description = pluralStringResource(Plurals.choose_wallet_description, items.size, items.size)
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
                if (item.kind.iconOnCircle) {
                    MoonCircleIcon(
                        painter = painterResource(item.kind.iconRes),
                        size = 44.dp,
                        iconSize = 28.dp,
                        tint = UIKit.colorScheme.icon.primary,
                    )
                } else {
                    MoonItemImage(
                        painter = painterResource(item.kind.iconRes),
                        size = 44.dp,
                    )
                }
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
