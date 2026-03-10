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

    override val extras: List<String>
        get() = arguments?.getStringArrayList(ARG_EXTRAS) ?: emptyList()

    override fun onSelected(currency: WalletCurrency) {
        mainViewModel.setCurrency(currency)
    }

    override fun onQuery(query: String) {
        viewModel.query(query)
    }

    companion object {

        private const val ARG_CURRENCIES = "currencies"
        private const val ARG_EXTRAS = "extras"

        fun newInstance(currencies: List<WalletCurrency>, extras: List<String>): OnRampFiatScreen {
            if (extras.isNotEmpty() && extras.size != currencies.size) {
                throw IllegalArgumentException("Extras size must match currencies size")
            }
            val screen = OnRampFiatScreen()
            screen.putParcelableListArg(ARG_CURRENCIES, currencies)
            screen.putStringList(ARG_EXTRAS, extras)
            return screen
        }
    }


}