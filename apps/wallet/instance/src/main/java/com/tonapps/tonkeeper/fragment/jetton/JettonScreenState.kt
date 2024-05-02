package com.tonapps.tonkeeper.fragment.jetton

import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.api.parsedBalance
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.fragment.jetton.list.JettonItem
import io.tonapi.models.JettonBalance
import com.tonapps.wallet.data.account.WalletType
import uikit.mvi.AsyncState
import uikit.mvi.UiState

data class JettonScreenState(
    val walletAddress: String = "",
    val walletType: WalletType = WalletType.Default,
    val asyncState: AsyncState = AsyncState.Loading,
    val jetton: JettonBalance? = null,
    val currencyBalance: CharSequence = "",
    val rateFormat: CharSequence = "",
    val rate24h: String = "",
    val historyItems: List<HistoryItem> = emptyList(),
    val loadedAll: Boolean = false,
): UiState() {

    val balance: CharSequence
        get() {
            val jetton = jetton ?: return ""
            return CurrencyFormatter.format(jetton.jetton.symbol, jetton.parsedBalance, jetton.jetton.decimals)
        }

    fun getTopItems(): List<JettonItem> {
        val jetton = jetton ?: return emptyList()
        val items = mutableListOf<JettonItem>()
        items.add(JettonItem.Header(
            balance = balance,
            currencyBalance = currencyBalance,
            iconUrl = jetton.jetton.image,
            rate = rateFormat,
            diff24h = rate24h
        ))
        items.add(JettonItem.Actions(walletAddress, jetton, walletType))
        return items
    }
}