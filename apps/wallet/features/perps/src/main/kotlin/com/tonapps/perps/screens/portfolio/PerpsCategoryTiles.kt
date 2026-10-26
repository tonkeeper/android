package com.tonapps.perps.screens.portfolio

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonapps.core.navigation.PortfolioSearchAssetType
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.theme.Shapes
import ui.theme.UIKit

private enum class PerpsCategoryTile(
    val category: PortfolioSearchAssetType?,
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
) {
    Tokens(PortfolioSearchAssetType.TOKENS, UIKitIcon.ic_tokens_28, Localization.perps_filter_tokens),
    Perps(null, UIKitIcon.ic_perps_28, Localization.perps_service_perps),
    Stocks(PortfolioSearchAssetType.STOCKS, UIKitIcon.ic_stocks_28, Localization.perps_filter_stocks),
    Etfs(PortfolioSearchAssetType.ETFS, UIKitIcon.ic_etfs_28, Localization.perps_filter_etfs),
}

@Composable
fun PerpsCategoryTiles(
    onOpenPerps: () -> Unit,
    onOpenCategory: (PortfolioSearchAssetType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
    ) {
        PerpsCategoryTile.entries.forEach { tile ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(Shapes.medium)
                    .clickable {
                        val category = tile.category
                        if (category != null) {
                            onOpenCategory(category)
                        } else {
                            onOpenPerps()
                        }
                    }
                    .padding(horizontal = 2.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(UIKit.colorScheme.background.content, Shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(tile.icon),
                        contentDescription = null,
                        tint = UIKit.colorScheme.accent.blue,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(tile.title),
                    style = UIKit.typography.body3,
                    color = UIKit.colorScheme.text.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
