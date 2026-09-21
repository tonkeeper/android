package com.tonapps.deposit.multicoin.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.core.components.painterResource
import com.tonapps.core.helper.rememberClipboardManager
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.account.AccountEntity
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.TextCell
import ui.preview.ThemedPreview
import ui.shortAddress
import ui.theme.UIKit

@Preview
@Composable
fun QrAccountCellPreview() {
    val asset = Chain.Ton.Mainnet.toAsset()
    ThemedPreview {
//        QrAccountCell(
//            account = AccountEntity(
//                assetId = "EQBynBO23ywHy_CgarY9NK9FTz0yDsG82PtcbSTQgGoXwiuS",
//                publicKey = "",
//                displayAddress = "",
//                asset = AssetEntity(
//                    id = asset.id,
//                    symbol = asset.symbol,
//                    decimals = asset.decimals.value,
//                    imageUrl = "https://www.citypng.com/photo/26167/ethereum-eth-round-logo-icon-png",
//                    name = asset.coin.name,
//                ),
//                walletId = ""
//            )
//        )
    }
}



@Composable
fun QrAccountCell(
    account: AccountEntity,
    position: MoonBundlePosition = MoonBundlePosition.Default,
    onOpenQr: () -> Unit,
) {
    val clipboard = rememberClipboardManager()
    val chainImageUrl = account.chain.painterResource()

    MoonBundleCell(position = position) {
        TextCell(
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoonItemTitle(text = account.chain.name)
                    }
                }
            },
            subtitle = {
                MoonItemSubtitle(text = account.displayAddress.shortAddress)
            },
            image = {
                MoonItemImage(painter = chainImageUrl, size = 44.dp)
            },
            content = {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onOpenQr) {
                        MoonItemIcon(
                            painter = ui.painterResource(UIKitIcon.ic_qr_code_16),
                            color = UIKit.colorScheme.icon.primary,
                            size = 20.dp,
                        )
                    }

                    IconButton(onClick = { clipboard.copy(account.displayAddress) }) {
                        MoonItemIcon(
                            painter = ui.painterResource(UIKitIcon.ic_copy_16),
                            color = UIKit.colorScheme.icon.primary,
                            size = 20.dp,
                        )
                    }
                }
            },
            onClick = onOpenQr,
            minHeight = 76.dp,
        )
    }
}
