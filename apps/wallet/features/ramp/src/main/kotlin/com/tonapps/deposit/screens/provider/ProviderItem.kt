package com.tonapps.deposit.screens.provider

import androidx.compose.runtime.Stable
import com.tonapps.icu.Coins
import io.exchangeapi.models.ExchangeMerchantInfoButton

@Stable
data class ProviderItem(
    val id: String,
    val title: String,
    val imageUrl: String,
    val isBest: Boolean,
    val minAmount: Coins = Coins.ZERO,
    val maxAmount: Coins? = null,
    val buttons: List<ExchangeMerchantInfoButton> = emptyList(),
) {
    companion object {
        val EMPTY = ProviderItem(id = "", title = "", imageUrl = "", isBest = false)
    }
}

@Stable
data class ProviderRate(
    val rateFormatted: String,
)

@Stable
data class ProviderQuote(
    val amount: Coins,
    val receiveCoins: Coins,
    val currencyCode: String,
    val widgetUrl: String,
    val receiveAmount: String? = null,
    val merchantTransactionId: String? = null,
)

@Stable
data class ProviderWithQuote(
    val info: ProviderItem,
    val rate: ProviderRate? = null,
    val quote: ProviderQuote? = null,
)
