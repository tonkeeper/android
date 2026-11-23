package com.tonapps.perps.screens.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.tonapps.core.components.BannersViewer
import com.tonapps.core.loyalty.rememberLoyaltyBanner
import ui.preview.ThemedPreview

@Composable
internal fun PerpsLoyaltyBanner(
    onClick: (String) -> Unit,
    onHide: () -> Unit,
) {
    val banner = rememberLoyaltyBanner()
    val banners = remember(banner) { listOf(banner) }
    BannersViewer(
        banners = banners,
        analytics = null,
        onHide = { onHide() },
        onClick = { button -> onClick(button.payload) },
    )
}

@Preview
@Composable
private fun PerpsLoyaltyBannerPreview() {
    ThemedPreview {
        PerpsLoyaltyBanner(onClick = {}, onHide = {})
    }
}
