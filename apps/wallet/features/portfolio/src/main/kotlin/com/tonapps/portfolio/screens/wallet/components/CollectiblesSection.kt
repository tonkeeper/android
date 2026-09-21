package com.tonapps.portfolio.screens.wallet.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonapps.portfolio.screens.wallet.WalletCollectiblesState
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.localization.RStr
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import ui.components.moon.MoonAsyncImage
import ui.components.moon.cell.MoonBundleTitleCell
import ui.painterResource
import ui.theme.Shapes
import ui.theme.UIKit

private val SectionHorizontalPadding = 16.dp
private val ItemSpacing = 8.dp
private val NextItemPeek = 16.dp
private const val VisibleCardsCount = 3
private const val SeeAllKey = "see_all"

@Composable
fun CollectiblesSection(
    state: WalletCollectiblesState,
    onHeaderClick: () -> Unit,
    onSeeAllClick: () -> Unit,
    onNftClick: (NftEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        CollectiblesHeader(onClick = onHeaderClick)
        if (state.allHidden) {
            AllCollectiblesHiddenCell(onClick = onSeeAllClick)
        } else {
            CollectiblesRow(
                state = state,
                onSeeAllClick = onSeeAllClick,
                onNftClick = onNftClick,
            )
        }
    }
}

@Composable
private fun CollectiblesHeader(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MoonBundleTitleCell(
        title = stringResource(RStr.collectibles),
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
private fun CollectiblesRow(
    state: WalletCollectiblesState,
    onSeeAllClick: () -> Unit,
    onNftClick: (NftEntity) -> Unit,
) {
    val density = LocalDensity.current
    val viewportWidth = LocalWindowInfo.current.containerSize.width
    val nftCardWidth = remember(viewportWidth) {
        with(density) {
            val availablePx = viewportWidth -
                    SectionHorizontalPadding.roundToPx() -
                    (ItemSpacing.roundToPx() * (VisibleCardsCount - 1)) -
                    NextItemPeek.roundToPx()
            (availablePx / VisibleCardsCount).toDp()
        }
    }

    val listState = rememberLazyListState()

    if (state.showSeeAll) {
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }
                .filter { !it }
                .collectLatest {
                    val layoutInfo = listState.layoutInfo
                    val seeAllItem = layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == SeeAllKey }
                        ?: return@collectLatest
                    val viewportEnd = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
                    val overflow = seeAllItem.offset + seeAllItem.size - viewportEnd
                    if (overflow in 1 until seeAllItem.size) {
                        val visible = seeAllItem.size - overflow
                        if (visible >= seeAllItem.size / 2) {
                            listState.animateScrollBy(overflow.toFloat())
                        } else {
                            listState.animateScrollBy(-visible.toFloat())
                        }
                    }
                }
        }
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(
            horizontal = SectionHorizontalPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(
            items = state.previewItems,
            key = { _, nft -> nft.id },
        ) { _, nft ->
            NftPreviewCard(
                nft = nft,
                cardWidth = nftCardWidth,
                onClick = { onNftClick(nft) },
            )
        }
        if (state.showSeeAll) {
            item(key = SeeAllKey) {
                SeeAllButton(
                    cardWidth = nftCardWidth,
                    onClick = onSeeAllClick,
                )
            }
        }
    }
}

@Composable
private fun NftPreviewCard(
    nft: NftEntity,
    cardWidth: Dp,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(cardWidth)
            .clip(Shapes.medium)
            .background(UIKit.colorScheme.background.content)
            .clickable(onClick = onClick),
    ) {
        Box {
            MoonAsyncImage(
                image = nft.mediumUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop,
            )
            if (nft.inSale) {
                Image(
                    painter = painterResource(UIKitIcon.ic_sale_badge_16),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(26.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = nft.name,
                style = UIKit.typography.label2,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = nft.collectionName,
                style = UIKit.typography.body3,
                color = UIKit.colorScheme.text.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SeeAllButton(
    cardWidth: Dp,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(cardWidth)
            .height(cardWidth)
            .clip(Shapes.medium)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(UIKit.colorScheme.buttonSecondary.primaryBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UIKitIcon.ic_chevron_right_16),
                contentDescription = null,
                tint = UIKit.colorScheme.icon.primary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(RStr.see_all),
            style = UIKit.typography.label3,
            color = UIKit.colorScheme.text.secondary,
            maxLines = 1,
        )
    }
}