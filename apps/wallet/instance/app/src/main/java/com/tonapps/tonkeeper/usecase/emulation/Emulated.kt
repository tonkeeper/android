package com.tonapps.tonkeeper.usecase.emulation

import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.entity.TransferType
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.data.token.TokenRepository
import io.tonapi.models.JettonQuantity
import io.tonapi.models.MessageConsequences

data class Emulated(
    val consequences: MessageConsequences?,
    val type: TransferType,
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

    val withBattery: Boolean
        get() = type == TransferType.Battery || type == TransferType.Gasless

    val totalTon: Coins
        get() = consequences?.let {
            Coins.of(it.risk.ton)
        } ?: Coins.ZERO

    val totalFees: Coins
        get() = consequences?.let {
            Coins.of(it.trace.transaction.totalFees)
        } ?: Coins.ZERO

    val jettons: List<JettonQuantity>
        get() = consequences?.risk?.jettons ?: emptyList()

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
        val jettonsAddress = jettons.map {
            it.jetton.address.toRawAddress()
        }

        return tokenRepository.getTokens(testnet, jettonsAddress)
    }

}