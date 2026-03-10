package com.tonapps.tonkeeper.ui.base.picker.currency

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.os.AndroidCurrency
import com.tonapps.tonkeeper.ui.base.picker.BasePickerScreen
import com.tonapps.tonkeeper.ui.base.picker.currency.list.Adapter
import com.tonapps.wallet.data.core.currency.WalletCurrency
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.extensions.collectFlow

abstract class CurrencyPickerScreen: BasePickerScreen() {

    open val currencies = emptyList<WalletCurrency>()

    open val extras: List<String> = emptyList()

    override val viewModel: CurrencyPickerViewModel by viewModel {
        parametersOf(currencies, extras)
    }

    override val adapter = Adapter { item ->
        onSelected(item.currency)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItemsFlow) { items ->
            adapter.submitList(items) {
                setEmptyVisibility(items.isEmpty())
            }
        }
    }

    abstract fun onSelected(currency: WalletCurrency)

    /*override fun onQuery(query: CharSequence?) {
        viewModel.query(query)
    }*/

}