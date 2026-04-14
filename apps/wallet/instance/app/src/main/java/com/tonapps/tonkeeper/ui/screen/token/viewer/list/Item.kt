package com.tonapps.tonkeeper.ui.screen.token.viewer.list

import android.net.Uri
import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.icu.Coins
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.entity.ChartEntity
import com.tonapps.wallet.api.entity.EthenaEntity
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.data.settings.ChartPeriod

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_BALANCE = 0
        const val TYPE_ACTIONS = 1
        const val TYPE_CHART = 2
        const val TYPE_W5_BANNER = 3
        const val TYPE_BATTERY_BANNER = 4
        const val TYPE_ABOUT_ETHENA = 6
        const val TYPE_SPACE = 7
        const val TYPE_ETHENA_BALANCE = 8
        const val TYPE_ETHENA_METHOD = 9
        const val TYPE_TRON_BANNER = 10
    }

    data class Balance(
        val balance: CharSequence,
        val fiat: CharSequence,
        val iconUri: Uri,
        val showNetwork: Boolean,
        val blockchain: Blockchain,
        val hiddenBalance: Boolean,
        val wallet: WalletEntity,
        val availableTransfers: Int?,
    ): Item(TYPE_BALANCE) {
        val networkIconRes: Int
            get() = when (blockchain) {
                Blockchain.TRON -> R.drawable.ic_tron
                else -> R.drawable.ic_ton
            }
    }

    data class Actions(
        val wallet: WalletEntity,
        val swapUri: Uri,
        val tronSwapUrl: String?,
        val swapDisabled: Boolean,
        val tronTransfersDisabled: Boolean,
        val token: TokenEntity,
    ): Item(TYPE_ACTIONS) {

        val walletAddress: String
            get() = wallet.address

        val tokenAddress: String
            get() = token.address

        val walletType: WalletType
            get() = wallet.type

        val currency: WalletCurrency
            get() = token.asCurrency

        val send: Boolean
            get() = !wallet.isWatchOnly && token.isTransferable

        val swap: Boolean
            get() = if (token.isUsdtTrc20) {
                !swapDisabled && wallet.hasPrivateKey && tronSwapUrl != null
            } else {
                !swapDisabled && token.verified && !wallet.isWatchOnly
            }

        val maxColumnCount: Int
            get() = if (swap) {
                3
            } else {
                2
            }
    }

    data class Chart(
        val data: List<ChartEntity>,
        val square: Boolean,
        val period: ChartPeriod,
        val fiatPrice: CharSequence,
        val rateNow: Coins,
        val rateDiff24h: CharSequence,
        val delta: CharSequence,
        val currency: WalletCurrency
    ): Item(TYPE_CHART)

    data class W5Banner(
        val wallet: WalletEntity,
        val addButton: Boolean
    ): Item(TYPE_W5_BANNER)

    data class BatteryBanner(
        val wallet: WalletEntity,
        val token: TokenEntity
    ): Item(TYPE_BATTERY_BANNER)

    data class AboutEthena(
        val description: String,
        val url: String,
    ): Item(TYPE_ABOUT_ETHENA)

    data object Space: Item(TYPE_SPACE)

    data class EthenaBalance(
        val position: ListCell.Position,
        val wallet: WalletEntity,
        val staked: Boolean,
        val methodType: EthenaEntity.Method.Type? = null,
        val balance: Coins,
        val balanceFormat: CharSequence,
        val fiatFormat: CharSequence,
        val showApy: Boolean = true,
        val title: CharSequence? = null,
        val apyText: CharSequence? = null,
        val fiatRate: CharSequence? = null,
        val rateDiff24h: String? = null,
        val verified: Boolean = false,
        val hiddenBalance: Boolean,
    ): Item(TYPE_ETHENA_BALANCE) {
        val iconRes: Int?
            get() {
                return when (methodType) {
                    EthenaEntity.Method.Type.STONFI -> R.drawable.ethena
                    EthenaEntity.Method.Type.AFFLUENT -> R.drawable.affluent
                    null -> null
                }
            }
    }

    data class EthenaMethod(
        val position: ListCell.Position,
        val wallet: WalletEntity,
        val methodType: EthenaEntity.Method.Type? = null,
        val url: String,
        val name: String,
        val apy: CharSequence,
    ): Item(TYPE_ETHENA_METHOD) {
        val iconRes: Int?
            get() {
                return when (methodType) {
                    EthenaEntity.Method.Type.STONFI -> R.drawable.stonfi
                    EthenaEntity.Method.Type.AFFLUENT -> R.drawable.affluent
                    null -> null
                }
            }
    }

    data class TronBanner(
        val wallet: WalletEntity,
        val trxAmountFormat: CharSequence,
        val trxBalanceFormat: CharSequence,
        val onlyTrx: Boolean
    ): Item(TYPE_TRON_BANNER)
}