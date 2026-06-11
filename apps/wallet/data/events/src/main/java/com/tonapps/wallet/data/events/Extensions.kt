package com.tonapps.wallet.data.events

import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.icu.Coins
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.data.rates.RatesRepository
import io.tonapi.models.AccountAddress
import io.tonapi.models.AccountEvent
import io.tonapi.models.Action
import io.tonapi.models.JettonTransferAction

val JettonTransferAction.amountCoins: Coins
    get() = Coins.ofNano(amount, jetton.decimals)

suspend fun Action.getTonAmountRaw(network: TonNetwork, ratesRepository: RatesRepository): Coins {
    val tonAmount = tonTransfer?.let { Coins.of(it.amount) }
    val jettonAmountInTON = jettonTransfer?.let {
        val amountCoins = it.amountCoins
        val jettonAddress = it.jetton.address
        val rates = ratesRepository.getRates(TonNetwork.MAINNET, WalletCurrency.TON, jettonAddress)
        rates.convert(jettonAddress, amountCoins)
    }
    return tonAmount ?: jettonAmountInTON ?: Coins.ZERO
}

val Action.isTransfer: Boolean
    get() {
        return type == Action.Type.TonTransfer || type == Action.Type.JettonTransfer || type == Action.Type.NftItemTransfer || type == Action.Type.Purchase
    }

fun AccountEvent.isOutTransfer(accountId: String): Boolean {
    return actions.any { it.isOutTransfer(accountId) }
}

fun Action.isOutTransfer(accountId: String): Boolean {
    if (!isTransfer) {
        return false
    }
    val sender = tonTransfer?.sender ?: jettonTransfer?.sender ?: nftItemTransfer?.sender ?: return false
    return sender.address.equalsAddress(accountId)
}

val Action.recipient: AccountAddress?
    get() = nftItemTransfer?.recipient ?: tonTransfer?.recipient ?: jettonTransfer?.recipient ?: jettonSwap?.userWallet ?: jettonMint?.recipient ?: depositStake?.staker ?: withdrawStake?.staker ?: withdrawStakeRequest?.staker ?: depositTokenStake?.staker ?: withdrawTokenStakeRequest?.staker

val Action.sender: AccountAddress?
    get() = nftItemTransfer?.sender ?:tonTransfer?.sender ?: jettonTransfer?.sender ?: jettonSwap?.userWallet ?: jettonBurn?.sender ?: depositStake?.staker ?: withdrawStake?.staker ?: withdrawStakeRequest?.staker ?: depositTokenStake?.staker ?: withdrawTokenStakeRequest?.staker
