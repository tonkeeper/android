package com.tonapps.tonkeeper.ui.screen.settings.currency

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.settings.currency.list.Adapter
import com.tonapps.tonkeeper.ui.screen.settings.currency.list.Item
import com.tonapps.wallet.api.entity.CurrencyEntity
import com.tonapps.tonkeeper.worker.WidgetUpdaterWorker
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.smartScrollTo

class CurrencyScreen: BaseListWalletScreen<ScreenContext.None>(ScreenContext.None), BaseFragment.SwipeBack {

    override val fragmentName: String = "CurrencyScreen"

    override val viewModel: CurrencyViewModel by viewModel()

    private val adapter = Adapter(::selectCurrency)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(getString(Localization.primary_currency))
        setAdapter(adapter)

        collectFlow(viewModel.uiItemsFlow, ::setUiItems)
    }

    private fun setUiItems(items: List<Item>) {
        adapter.submitList(items) {
            scrollToSelectedIfNeeded(items)
        }
    }

    private fun scrollToSelectedIfNeeded(items: List<Item>) {
        if (items.size <= 1) {
            return
        }
        val index = adapter.selectedIndex
        if (index < 0) {
            return
        }
        listView.smartScrollTo(index)
    }

    private fun selectCurrency(currency: CurrencyEntity) {
        viewModel.selectCurrency(currency)
        WidgetUpdaterWorker.update(requireContext())
        finish()
    }

    companion object {
        fun newInstance() = CurrencyScreen()
    }
}