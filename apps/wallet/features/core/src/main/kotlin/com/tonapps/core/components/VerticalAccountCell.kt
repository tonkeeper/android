package com.tonapps.core.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.tonapps.wallet.data.multichain.account.AccountEntity
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.cell.TextCell
import ui.shortAddress

@Composable
fun VerticalAssetCell(
    account: AccountEntity,
    onClick: (() -> Unit)? = null,
) {
    TextCell(
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MoonItemTitle(text = account.chain.toAsset().name)
            }
        },
        image = {
            MoonItemImage(painter = account.chain.painterResource(), size = 28.dp)
        },
        content = {
            MoonItemSubtitle(account.displayAddress.shortAddress)
        },
        onClick = onClick,
        minHeight = 52.dp,
    )
}
