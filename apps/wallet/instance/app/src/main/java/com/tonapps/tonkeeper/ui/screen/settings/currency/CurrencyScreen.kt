package com.tonapps.tonkeeper.ui.screen.settings.currency

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.screen.settings.currency.list.Adapter
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.base.BaseListFragment
import uikit.extensions.collectFlow

class CurrencyScreen: BaseListWalletScreen(), BaseFragment.SwipeBack {

    override val viewModel: CurrencyViewModel by viewModel()

    private val adapter = Adapter(::selectCurrency)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(getString(Localization.primary_currency))
        setAdapter(adapter)

        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    private fun selectCurrency(currency: String) {
        viewModel.selectCurrency(currency)
        finish()
    }

    companion object {
        fun newInstance() = CurrencyScreen()
    }
}