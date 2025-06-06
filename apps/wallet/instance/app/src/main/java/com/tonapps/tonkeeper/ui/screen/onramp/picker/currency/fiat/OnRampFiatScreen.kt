package com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.fiat

import com.tonapps.tonkeeper.ui.base.picker.QueryReceiver
import com.tonapps.tonkeeper.ui.base.picker.currency.CurrencyPickerScreen
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.OnRampPickerScreen
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.OnRampPickerViewModel
import com.tonapps.wallet.data.core.currency.WalletCurrency

class OnRampFiatScreen: CurrencyPickerScreen(), QueryReceiver {

    private val mainViewModel: OnRampPickerViewModel
        get() = OnRampPickerScreen.parentViewModel(requireParentFragment())

    override val currencies: List<WalletCurrency>
        get() {
            val array = arguments?.getParcelableArrayList<WalletCurrency>(ARG_CURRENCIES)
            return array?.toList() ?: emptyList()
        }

    override fun onSelected(currency: WalletCurrency) {
        mainViewModel.setCurrency(currency)
    }

    override fun onQuery(query: String) {
        viewModel.query(query)
    }

    companion object {

        private const val ARG_CURRENCIES = "currencies"

        fun newInstance(currencies: List<WalletCurrency>): OnRampFiatScreen {
            val screen = OnRampFiatScreen()
            screen.putParcelableListArg(ARG_CURRENCIES, currencies)
            return screen
        }
    }


}