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
    currencies: List<WalletCurrency>
): BaseWalletVM(app) {

    private val currenciesFlow = flow {
        val list = currencies.ifEmpty {
            WalletCurrency.FIAT.mapNotNull { WalletCurrency.of(it) }
        }
        emit(WalletCurrency.sort(list))
    }.flowOn(Dispatchers.IO)

    private val _queryFlow = MutableStateFlow("")

    val uiItemsFlow = combine(currenciesFlow, _queryFlow) { currencies, query ->
        if (query.isEmpty()) {
            currencies
        } else {
            currencies.filter { it.title.contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true) }
        }
    }.map { currencies ->
        val uiItems = mutableListOf<Item>()
        for ((index, currency) in currencies.withIndex()) {
            val position = ListCell.getPosition(currencies.size, index)
            uiItems.add(Item(position, currency))
        }
        uiItems.toList()
    }.flowOn(Dispatchers.IO)

    fun query(value: CharSequence?) {
        _queryFlow.value = value?.toString()?.trim() ?: ""
    }

}