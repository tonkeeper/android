package com.tonapps.core.loyalty

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.tonapps.core.deeplink.DeepLinkBuilder
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.api.entity.BannerEntity
import com.tonapps.wallet.localization.Localization

const val LOYALTY_HOST = "loyalty.keeperwallet.com"

const val LOYALTY_URL = "https://$LOYALTY_HOST/"

val LOYALTY_DEEPLINK: String = DeepLinkBuilder.dApp(LOYALTY_URL)

const val LOYALTY_BANNER_ID = "loyalty"

@Composable
fun rememberLoyaltyBanner(): BannerEntity {
    val title = stringResource(Localization.loyalty_banner_title)
    val buttonTitle = stringResource(Localization.loyalty_banner_join)
    return remember(title, buttonTitle) {
        BannerEntity(
            id = LOYALTY_BANNER_ID,
            title = title,
            description = "",
            image = "res:/${UIKitIcon.bg_loyalty_banner}",
            textColor = null,
            backgroundColor = null,
            button = BannerEntity.Button(
                type = BannerEntity.Button.Type.DEEPLINK,
                payload = LOYALTY_DEEPLINK,
                title = buttonTitle,
            ),
        )
    }
}
