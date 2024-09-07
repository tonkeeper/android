package com.tonapps.tonkeeper.ui.screen.staking.viewer.list

import android.net.Uri
import androidx.annotation.StringRes
import com.tonapps.icu.Coins
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.staking.StakingPool

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_BALANCE = 0
        const val TYPE_ACTIONS = 1
        const val TYPE_DETAILS = 3
        const val TYPE_LINKS = 4
        const val TYPE_TOKEN = 5
        const val TYPE_SPACE = 6
        const val TYPE_DESCRIPTION = 7
    }

    data class Balance(
        val poolImplementation: StakingPool.Implementation,
        val balance: Coins,
        val balanceFormat: CharSequence,
        val fiat: Coins,
        val fiatFormat: CharSequence,
        val hiddenBalance: Boolean,
    ): Item(TYPE_BALANCE)

    data class Actions(
        val poolAddress: String,
        val wallet: WalletEntity,
    ): Item(TYPE_ACTIONS)

    data class Details(
        val apyFormat: String,
        val minDepositFormat: CharSequence,
        val maxApy: Boolean,
    ): Item(TYPE_DETAILS)

    data class Links(
        val links: List<String>
    ): Item(TYPE_LINKS)

    data class Token(
        val wallet: WalletEntity,
        val iconUri: Uri,
        val address: String,
        val symbol: String,
        val name: String,
        val balance: Coins,
        val balanceFormat: CharSequence,
        val fiat: Coins,
        val fiatFormat: CharSequence,
        val rate: CharSequence,
        val rateDiff24h: String,
        val verified: Boolean,
        val testnet: Boolean,
        val hiddenBalance: Boolean,
        val blacklist: Boolean
    ): Item(TYPE_TOKEN)

    data object Space: Item(TYPE_SPACE)

    data class Description(
        @StringRes val resId: Int
    ): Item(TYPE_DESCRIPTION)
}