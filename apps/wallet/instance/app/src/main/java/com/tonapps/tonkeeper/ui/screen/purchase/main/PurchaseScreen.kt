package com.tonapps.tonkeeper.ui.screen.purchase.main

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.extensions.countryEmoji
import com.tonapps.tonkeeper.ui.screen.country.CountryPickerScreen
import com.tonapps.tonkeeper.ui.screen.purchase.main.list.Adapter
import com.tonapps.tonkeeper.ui.screen.purchase.web.PurchaseWebScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.collectFlow
import uikit.navigation.Navigation.Companion.navigation
import kotlin.coroutines.resume

class PurchaseScreen: BaseFragment(R.layout.fragment_purchase), BaseFragment.BottomSheet {

    private val purchaseViewModel: PurchaseViewModel by viewModel()
    private val adapter: Adapter by lazy { Adapter(::open) }
    private val confirmDialog: PurchaseConfirmDialog by lazy {
        PurchaseConfirmDialog(requireContext())
    }

    private lateinit var countryView: AppCompatTextView
    private lateinit var tabBuyView: AppCompatTextView
    private lateinit var tabSellView: AppCompatTextView
    private lateinit var listView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(purchaseViewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        countryView = view.findViewById(R.id.country)
        countryView.setOnClickListener {
            navigation?.add(CountryPickerScreen.newInstance(COUNTRY_REQUEST_KEY))
        }

        tabBuyView = view.findViewById(R.id.tab_buy)
        tabBuyView.setOnClickListener { purchaseViewModel.setTab(PurchaseViewModel.Tab.BUY) }

        tabSellView = view.findViewById(R.id.tab_sell)
        tabSellView.setOnClickListener { purchaseViewModel.setTab(PurchaseViewModel.Tab.SELL) }

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter
        listView.applyNavBottomPadding()

        view.findViewById<View>(R.id.close).setOnClickListener { finish() }

        collectFlow(purchaseViewModel.countryFlow) { country ->
            countryView.text = country.countryEmoji
        }

        collectFlow(purchaseViewModel.tabFlow, ::applyTab)
    }

    private fun applyTab(tab: PurchaseViewModel.Tab) {
        if (tab == PurchaseViewModel.Tab.BUY) {
            tabBuyView.setBackgroundResource(uikit.R.drawable.bg_button_tertiary)
            tabSellView.background = null
        } else if (tab == PurchaseViewModel.Tab.SELL) {
            tabBuyView.background = null
            tabSellView.setBackgroundResource(uikit.R.drawable.bg_button_tertiary)
        }
    }

    private fun open(method: PurchaseMethodEntity) {
        combine(
            purchaseViewModel.open(method),
            purchaseViewModel.walletFlow
        ) { showConfirmDialog, wallet ->
            if (showConfirmDialog && !confirmDialog(method)) {
                purchaseViewModel.disableConfirmDialog(wallet, method)
            }
            navigation?.add(PurchaseWebScreen.newInstance(method))
            finish()
        }.launchIn(lifecycleScope)
    }

    private suspend fun confirmDialog(
        method: PurchaseMethodEntity
    ): Boolean = suspendCancellableCoroutine { continuation ->
        confirmDialog.show(method) { showAgain ->
            continuation.resume(showAgain)
        }
    }

    companion object {
        private const val COUNTRY_REQUEST_KEY = "buy_country_request"
        fun newInstance() = PurchaseScreen()
    }
}