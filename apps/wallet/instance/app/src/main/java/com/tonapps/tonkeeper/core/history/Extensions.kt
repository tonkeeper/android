package com.tonapps.tonkeeper.core.history

import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.icu.Coins
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.api.extensions.toTokenEntity
import com.tonapps.wallet.data.rates.RatesRepository
import io.tonapi.models.Action
import io.tonapi.models.JettonSwapAction
import io.tonapi.models.JettonTransferAction

suspend fun Action.getTonAmountRaw(network: TonNetwork, ratesRepository: RatesRepository): Coins {
    val tonAmount = tonTransfer?.let { Coins.of(it.amount) }
    val jettonAmountInTON = jettonTransfer?.let {
        val amountCoins = it.amountCoins
        val jettonAddress = it.jetton.address
        val rates = ratesRepository.getRates(network, WalletCurrency.TON, jettonAddress)
        rates.convert(jettonAddress, amountCoins)
    }
    return tonAmount ?: jettonAmountInTON ?: Coins.ZERO
}

val JettonSwapAction.tokenIn: TokenEntity
    get() {
        val jetton = jettonMasterIn?.toTokenEntity()
        return jetton ?: TokenEntity.TON
    }

val JettonTransferAction.amountCoins: Coins
    get() = Coins.ofNano(amount, jetton.decimals)

val JettonSwapAction.amountCoinsIn: Coins
    get() {
        val tonAmount = tonIn ?: return Coins.ofNano(amountIn, tokenIn.decimals)
        return Coins.of(tonAmount, tokenIn.decimals)
    }

val JettonSwapAction.tokenOut: TokenEntity
    get() {
        val jetton = jettonMasterOut?.toTokenEntity()
        return jetton ?: TokenEntity.TON
    }

val JettonSwapAction.amountCoinsOut: Coins
    get() {
        val tonAmount = tonOut ?: return Coins.ofNano(amountOut, tokenOut.decimals)
        return Coins.of(tonAmount, tokenOut.decimals)
    }
