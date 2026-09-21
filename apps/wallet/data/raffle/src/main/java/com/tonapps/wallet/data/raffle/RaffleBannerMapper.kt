package com.tonapps.wallet.data.raffle

import com.tonapps.wallet.api.entity.BannerEntity
import com.tonapps.wallet.data.raffle.entities.RaffleEntity

/**
 * Adapts the raffle's standalone banner card to the wallet-list banners
 * carousel, so it reuses the existing dismiss/hide machinery.
 */
fun RaffleEntity.bannerEntity(): BannerEntity? {
    val banner = banner ?: return null
    return BannerEntity(
        id = "raffle_$id",
        title = banner.title,
        description = banner.description.orEmpty(),
        image = banner.imageUrl,
        textColor = null,
        backgroundColor = null,
        button = BannerEntity.Button(
            type = when (banner.button.action) {
                RaffleEntity.Cta.Action.Deeplink -> BannerEntity.Button.Type.DEEPLINK
                RaffleEntity.Cta.Action.Link -> BannerEntity.Button.Type.LINK
            },
            payload = banner.button.payload,
            title = banner.button.title,
        ),
    )
}
