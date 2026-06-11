package com.tonapps.deposit.screens.send

import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.bus.generated.Events
import com.tonapps.deposit.screens.send.state.SendDestination
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.token.entities.AccountTokenEntity

data class SendParams(
    val walletId: String,
    val destination: SendDestination,
    val selectedToken: AccountTokenEntity,
    val tokenAmount: Coins,
    val isMaxAmount: Boolean,
    val comment: String?,
    val encryptedComment: Boolean,
    val currency: WalletCurrency,
    val nftAddress: String,
    val tokens: List<AccountTokenEntity>,
    val tronAvailable: Boolean,
    val exchangeData: Exchange? = null,
    val analyticsFrom: Events.SendNative.SendNativeFrom = Events.SendNative.SendNativeFrom.WalletScreen,
) {
    data class Exchange(
        val currency: WalletCurrency,
        val exchangeAddress: String,
        val estimatedDurationSeconds: Int? = null,
        val withdrawalFee: String? = null,
    )
}
