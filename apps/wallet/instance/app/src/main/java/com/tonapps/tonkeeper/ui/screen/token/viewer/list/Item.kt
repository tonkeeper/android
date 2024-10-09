package com.tonapps.tonkeeper.ui.screen.token.viewer.list

import android.net.Uri
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.wallet.api.entity.ChartEntity
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.settings.ChartPeriod

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_BALANCE = 0
        const val TYPE_ACTIONS = 1
        const val TYPE_PRICE = 2
        const val TYPE_CHART = 3
        const val TYPE_W5_BANNER = 4
    }

    data class Balance(
        val balance: CharSequence,
        val fiat: CharSequence,
        val iconUri: Uri,
        val hiddenBalance: Boolean,
    ): Item(TYPE_BALANCE)

    data class Actions(
        val wallet: WalletEntity,
        val swapUri: Uri,
        val token: TokenEntity,
    ): Item(TYPE_ACTIONS) {

        val walletAddress: String
            get() = wallet.address

        val tokenAddress: String
            get() = token.address

        val walletType: Wallet.Type
            get() = wallet.type

        val send: Boolean
            get() = !wallet.isWatchOnly && token.isTransferable

        val swap: Boolean
            get() = token.verified && !wallet.isWatchOnly
    }

    data class Price(
        val fiatPrice: CharSequence,
        val rateDiff24h: CharSequence,
        val delta: CharSequence
    ): Item(TYPE_PRICE)

    data class Chart(
        val data: List<ChartEntity>,
        val square: Boolean,
        val period: ChartPeriod,
    ): Item(TYPE_CHART)

    data class W5Banner(
        val wallet: WalletEntity,
        val addButton: Boolean
    ): Item(TYPE_W5_BANNER)
}