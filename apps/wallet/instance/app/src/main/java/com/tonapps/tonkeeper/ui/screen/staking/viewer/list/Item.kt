package com.tonapps.tonkeeper.ui.screen.staking.viewer.list

import com.tonapps.icu.Coins
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.wallet.data.staking.StakingPool

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_BALANCE = 0
        const val TYPE_ACTIONS = 1
        const val TYPE_DETAILS = 3
        const val TYPE_LINKS = 4
    }

    data class Balance(
        val poolImplementation: StakingPool.Implementation,
        val balance: Coins,
        val balanceFormat: CharSequence,
        val fiat: Coins,
        val fiatFormat: CharSequence,
    ): Item(TYPE_BALANCE)

    data class Actions(
        val poolAddress: String
    ): Item(TYPE_ACTIONS)

    data class Details(
        val apyFormat: String,
        val minDepositFormat: CharSequence,
    ): Item(TYPE_DETAILS)

    data class Links(
        val links: List<String>
    ): Item(TYPE_LINKS)
}