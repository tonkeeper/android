package com.tonapps.trading.screens.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.moon.MoonTextShimmer
import ui.theme.Dimens
import ui.theme.Shapes
import ui.theme.UIKit

private const val TitlePlaceholder = "Top Movers"
private const val SeeAllPlaceholder = "Show all"
private const val SymbolPlaceholder = "TICKER"
private const val ChangePlaceholder = "+0.00%"

@Composable
internal fun ShelvesShimmer(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = Dimens.heightBar),
    ) {
        ShelfShimmerGroup(showSeeAll = true)
        ShelfShimmerGroup(showSeeAll = false)
        ShelfShimmerGroup(showSeeAll = false)
    }
}

@Composable
private fun ShelfShimmerGroup(showSeeAll: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoonTextShimmer(
                text = TitlePlaceholder,
                style = UIKit.typography.label1,
                cornerRadius = 12.dp,
            )
            if (showSeeAll) {
                MoonTextShimmer(
                    text = SeeAllPlaceholder,
                    style = UIKit.typography.label1,
                    cornerRadius = 12.dp,
                )
            }
        }

        Column(
            modifier = Modifier
                .background(shape = Shapes.medium, color = UIKit.colorScheme.background.content)
                .padding(8.dp),
        ) {
            repeat(2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                ) {
                    repeat(4) {
                        Box(modifier = Modifier.weight(1f)) {
                            AssetShimmerItem()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetShimmerItem() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .requiredSize(56.dp)
                .background(UIKit.colorScheme.background.contentTint, CircleShape),
        )
        Spacer(modifier = Modifier.height(8.dp))
        MoonTextShimmer(
            text = SymbolPlaceholder,
            style = UIKit.typography.body3,
            cornerRadius = 8.dp,
            backgroundFill = UIKit.colorScheme.background.contentTint,
            highlightColor = UIKit.colorScheme.background.highlighted,
        )
        Spacer(modifier = Modifier.height(1.dp))
        MoonTextShimmer(
            text = ChangePlaceholder,
            style = UIKit.typography.body3,
            cornerRadius = 8.dp,
            backgroundFill = UIKit.colorScheme.background.contentTint,
            highlightColor = UIKit.colorScheme.background.highlighted,
        )
    }
}
