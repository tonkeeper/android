package com.tonapps.tonkeeper.usecase.emulation

import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.token.TokenRepository
import io.tonapi.models.MessageConsequences

data class Emulated(
    val consequences: MessageConsequences?,
    val withBattery: Boolean = false,
    val total: Total,
    val extra: Extra,
    val currency: WalletCurrency,
    val failed: Boolean = false
) {

    companion object {
        val defaultExtra = Extra(false, Coins.ONE, Coins.ONE)
    }

    val nftCount: Int
        get() = total.nftCount

    val totalFormat: CharSequence
        get() = CurrencyFormatter.format(currency.code, total.totalFiat)

    data class Total(
        val totalFiat: Coins,
        val nftCount: Int,
        val isDangerous: Boolean,
    )

    data class Extra(
        val isRefund: Boolean,
        val value: Coins,
        val fiat: Coins,
    )

    suspend fun loadTokens(testnet: Boolean, tokenRepository: TokenRepository): List<TokenEntity> {
        val jettonsAddress = consequences?.risk?.jettons?.map {
            it.jetton.address.toRawAddress()
        } ?: emptyList()

        return tokenRepository.getTokens(testnet, jettonsAddress)
    }

}