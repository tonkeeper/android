package com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.base.picker.QueryReceiver
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.OnRampPickerScreen
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.OnRampPickerViewModel
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.list.Adapter
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.hideKeyboardWhenScroll

class OnRampCurrencyPickerScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_onramp_currency_picker, wallet), QueryReceiver {

    override val viewModel: OnRampPickerViewModel
        get() = OnRampPickerScreen.parentViewModel(requireParentFragment())

    private val adapter = Adapter(
        onCurrencyClick = {
            if (it.currency.chain is WalletCurrency.Chain.TON) {
                viewModel.setToken(it.id, it.network)
            } else {
                viewModel.setCurrency(it.currency)
            }
       },
        onMoreClick = { openMore(it.id) }
    )

    private lateinit var listView: RecyclerView
    private lateinit var loaderView: View
    private lateinit var emptyView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItemsFlow, ::setUiItems)
    }

    private fun setUiItems(items: List<Item>) {
        if (items.isEmpty()) {
            loaderView.visibility = View.GONE
            listView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            emptyView.visibility = View.GONE
            listView.visibility = View.VISIBLE
            adapter.submitList(items) {
                loaderView.visibility = View.GONE
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView = view.findViewById(R.id.list)
        listView.adapter = adapter
        listView.applyNavBottomPadding(requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium))

        loaderView = view.findViewById(R.id.loader)

        if (adapter.itemCount == 0) {
            loaderView.visibility = View.VISIBLE
        }

        emptyView = view.findViewById(R.id.empty)
    }

    private fun openMore(id: String) {
        when (id) {
            OnRampPickerViewModel.ALL_CURRENCIES_ID -> viewModel.openFiatPicker()
            OnRampPickerViewModel.ALL_CRYPTO_ID -> viewModel.openCryptoPicker()
            OnRampPickerViewModel.ALL_TON_ASSETS_ID -> viewModel.openTONAssetsPicker()
        }
    }

    override fun onQuery(query: String) {
        viewModel.setSearchQuery(query)
    }

    companion object {

        fun newInstance(wallet: WalletEntity) = OnRampCurrencyPickerScreen(wallet)
    }


}