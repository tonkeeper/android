package com.tonkeeper.fragment.wallet.main

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.dialog.fiat.FiatDialog
import com.tonkeeper.dialog.IntroWalletDialog
import com.tonkeeper.fragment.camera.CameraFragment
import com.tonkeeper.fragment.receive.ReceiveScreen
import com.tonkeeper.fragment.send.SendScreen
import com.tonkeeper.fragment.wallet.main.pager.WalletScreenAdapter
import com.tonkeeper.fragment.wallet.main.popup.NewWalletPopup
import com.tonkeeper.fragment.wallet.main.popup.WalletPickerPopup
import com.tonkeeper.fragment.wallet.main.view.WalletHeaderView
import kotlinx.coroutines.launch
import uikit.extensions.withAnimation
import uikit.mvi.AsyncState
import uikit.mvi.UiScreen
import uikit.navigation.Navigation.Companion.nav
import uikit.widget.HeaderView
import uikit.widget.TabLayoutEx

class WalletScreen: UiScreen<WalletScreenState, WalletScreenEffect, WalletScreenFeature>(R.layout.fragment_wallet) {

    companion object {
        fun newInstance() = WalletScreen()
    }

    override val feature: WalletScreenFeature by viewModels()

    private val fiatDialog: FiatDialog by lazy {
        FiatDialog(requireContext(), lifecycleScope)
    }

    private lateinit var bodyView: View
    private lateinit var headerView: WalletHeaderView
    private lateinit var amountView: AppCompatTextView
    private lateinit var addressView: AppCompatTextView
    private lateinit var buyButton: View
    private lateinit var sendButton: View
    private lateinit var receiveButton: View
    private lateinit var tabsContainerView: View
    private lateinit var tabsView: TabLayoutEx
    private lateinit var pagerView: ViewPager2

    private lateinit var shimmerView: View
    private lateinit var shimmerHeaderView: WalletHeaderView

    private val newWalletPopup: NewWalletPopup by lazy {
        val popup = NewWalletPopup(requireContext())
        popup.doOnCreateWalletClick = {
            IntroWalletDialog(requireContext()).show()
        }
        popup
    }

    private val walletPickerPopup: WalletPickerPopup by lazy {
        WalletPickerPopup(lifecycleScope, requireContext())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nav()?.setFragmentResultListener(FiatDialog.FIAT_DIALOG_REQUEST) { _, _ ->
            fiatDialog.show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bodyView = view.findViewById(R.id.body)

        headerView = view.findViewById(R.id.header)
        headerView.doOnActionClick = {
            nav()?.add(CameraFragment.newInstance())
        }
        headerView.doOnTitleClick = { selectWallet(headerView.titleView) }

        amountView = view.findViewById(R.id.amount)
        addressView = view.findViewById(R.id.address)

        sendButton = view.findViewById(R.id.send)
        sendButton.setOnClickListener {
            nav()?.add(SendScreen.newInstance())
        }
        receiveButton = view.findViewById(R.id.receive)
        receiveButton.setOnClickListener {
            nav()?.add(ReceiveScreen.newInstance())
        }

        buyButton = view.findViewById(R.id.buy)
        buyButton.setOnClickListener {
            fiatDialog.show()
        }

        tabsContainerView = view.findViewById(R.id.tabs_container)
        tabsView = view.findViewById(R.id.tabs)

        pagerView = view.findViewById(R.id.pager)

        shimmerView = view.findViewById(R.id.shimmer)
        shimmerHeaderView = view.findViewById(R.id.shimmer_header)
        shimmerHeaderView.doOnActionClick = {
            nav()?.add(CameraFragment.newInstance())
        }
        shimmerHeaderView.doOnTitleClick = { selectWallet(shimmerHeaderView.titleView) }
    }

    private fun selectWallet(view: View) {
        lifecycleScope.launch {
            val wallets = App.walletManager.getWallets()
            val selectedWallet = App.walletManager.getActiveWallet()
            if (1 >= wallets.size) {
                newWalletPopup.show(view)
                return@launch
            }
            walletPickerPopup.show(wallets, selectedWallet, view)
        }
    }

    override fun newUiState(state: WalletScreenState) {
        setAsyncState(state.asyncState)

        if (state.title.isNullOrEmpty()) {
            headerView.title = getString(R.string.wallet)
            shimmerHeaderView.title = getString(R.string.wallet)
        } else {
            headerView.title = state.title
            shimmerHeaderView.title = state.title
        }

        amountView.text = state.displayBalance
        addressView.text = state.shortAddress
        pagerView.adapter = WalletScreenAdapter(state.pages)
        if (state.pages.size > 1) {
            tabsContainerView.visibility = View.VISIBLE
            TabLayoutMediator(tabsView, pagerView) { tab, position ->
                tab.setText(state.pages[position].titleRes)
            }.attach()
            tabsView.requestLayout()
        } else {
            tabsContainerView.visibility = View.GONE
        }

        if (state.shortAddress.isEmpty()) {
            shimmerView.visibility = View.VISIBLE
            bodyView.visibility = View.GONE
        } else {
            shimmerView.visibility = View.GONE
            bodyView.visibility = View.VISIBLE
        }
    }

    private fun setAsyncState(asyncState: AsyncState) {
        headerView.updating = asyncState == AsyncState.Loading
        shimmerHeaderView.updating = asyncState == AsyncState.Loading
    }
}