package com.tonapps.tonkeeper.ui.screen.onramp.main

import com.tonapps.wallet.api.entity.OnRampMerchantEntity
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity

data class OnRampMerchant(
    val provider: PurchaseMethodEntity,
    val entity: OnRampMerchantEntity,
) {

    val id: String
        get() = provider.id
}