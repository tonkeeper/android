package com.tonapps.tonkeeper.ui.base.picker.currency

import android.app.Application
import com.tonapps.tonkeeper.os.AndroidCurrency
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.base.picker.currency.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.core.currency.WalletCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class CurrencyPickerViewModel(
    app: Application,
    currencies: List<WalletCurrency>,
    extra: List<String>,
): BaseWalletVM(app) {

    private data class WithExtra(
        val currency: WalletCurrency,
        val extra: String
    ) {

        fun containsQuery(query: String) = currency.containsQuery(query)
    }

    private val currenciesFlow = flow {
        val list = currencies.ifEmpty {
            WalletCurrency.FIAT.mapNotNull { WalletCurrency.of(it) }
        }
        emit(WalletCurrency.sort(list))
    }.flowOn(Dispatchers.IO)

    private val _queryFlow = MutableStateFlow("")

    val uiItemsFlow = combine(currenciesFlow.map { currencies ->
        if (extra.isEmpty()) {
            currencies.map { WithExtra(it, "") }
        } else {
            currencies.mapIndexed { index, currency ->
                WithExtra(currency, extra.getOrNull(index) ?: "")
            }
        }

    }, _queryFlow) { currencies, query ->
        if (query.isEmpty()) {
            currencies
        } else {
            currencies.filter { it.containsQuery(query) }
        }
    }.map { currencies ->
        val uiItems = mutableListOf<Item>()
        for ((index, currency) in currencies.withIndex()) {
            val position = ListCell.getPosition(currencies.size, index)
            uiItems.add(Item(position, currency.currency, currency.extra))
        }
        uiItems.toList()
    }.flowOn(Dispatchers.IO)

    fun query(value: CharSequence?) {
        _queryFlow.value = value?.toString()?.trim() ?: ""
    }

}