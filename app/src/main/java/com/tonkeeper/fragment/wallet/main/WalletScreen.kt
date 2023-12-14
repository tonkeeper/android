package com.tonkeeper.fragment.wallet.main

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.dialog.fiat.FiatDialog
import com.tonkeeper.dialog.IntroWalletDialog
import com.tonkeeper.extensions.copyToClipboard
import com.tonkeeper.fragment.camera.CameraFragment
import com.tonkeeper.fragment.main.MainTabScreen
import com.tonkeeper.fragment.receive.ReceiveScreen
import com.tonkeeper.fragment.send.SendScreen
import com.tonkeeper.fragment.wallet.main.pager.WalletScreenAdapter
import com.tonkeeper.fragment.wallet.main.pager.WalletScreenHolder
import com.tonkeeper.fragment.wallet.main.pager.WalletScreenItem
import com.tonkeeper.fragment.wallet.main.popup.NewWalletPopup
import com.tonkeeper.fragment.wallet.main.popup.WalletPickerPopup
import com.tonkeeper.fragment.wallet.main.view.WalletHeaderView
import kotlinx.coroutines.launch
import uikit.extensions.findViewHolderForAdapterPosition
import uikit.mvi.AsyncState
import uikit.mvi.UiScreen
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.TabLayoutEx

class WalletScreen: MainTabScreen<WalletScreenState, WalletScreenEffect, WalletScreenFeature>(R.layout.fragment_wallet) {

    companion object {
        fun newInstance() = WalletScreen()
    }

    override val feature: WalletScreenFeature by viewModels()

    private val fiatDialog: FiatDialog by lazy {
        FiatDialog(requireContext(), lifecycleScope)
    }

    private val adapter = WalletScreenAdapter()

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
        navigation?.setFragmentResultListener(FiatDialog.FIAT_DIALOG_REQUEST) { _, _ ->
            fiatDialog.show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bodyView = view.findViewById(R.id.body)

        headerView = view.findViewById(R.id.header)
        headerView.doOnActionClick = { openCamera() }
        headerView.doOnTitleClick = { selectWallet(headerView.titleView) }

        amountView = view.findViewById(R.id.amount)
        addressView = view.findViewById(R.id.address)
        addressView.setOnClickListener { feature.copyAddress() }

        sendButton = view.findViewById(R.id.send)
        sendButton.setOnClickListener {
            navigation?.add(SendScreen.newInstance())
        }
        receiveButton = view.findViewById(R.id.receive)
        receiveButton.setOnClickListener {
            navigation?.add(ReceiveScreen.newInstance())
        }

        buyButton = view.findViewById(R.id.buy)
        buyButton.setOnClickListener {
            fiatDialog.show()
        }

        tabsContainerView = view.findViewById(R.id.tabs_container)
        tabsView = view.findViewById(R.id.tabs)

        pagerView = view.findViewById(R.id.pager)
        pagerView.adapter = adapter

        shimmerView = view.findViewById(R.id.shimmer)
        shimmerHeaderView = view.findViewById(R.id.shimmer_header)
        shimmerHeaderView.doOnActionClick = { openCamera() }
        shimmerHeaderView.doOnTitleClick = { selectWallet(shimmerHeaderView.titleView) }
    }

    private fun openCamera() {
        navigation?.add(CameraFragment.newInstance())
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

    private fun setPages(pages: List<WalletScreenItem>) {
        if (adapter.itemCount != pages.size) {
            adapter.submitList(pages)
            return
        }

        for ((index, page) in pages.withIndex()) {
            val holder = pagerView.findViewHolderForAdapterPosition(index) as? WalletScreenHolder ?: continue
            holder.bind(page)
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

        setPages(state.pages)

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

    override fun newUiEffect(effect: WalletScreenEffect) {
        super.newUiEffect(effect)
        if (effect is WalletScreenEffect.CopyAddress) {
            copyAddress(effect.address)
        }
    }

    private fun copyAddress(address: String) {
        navigation?.toast(getString(R.string.copied))
        context?.copyToClipboard(address)
    }

    private fun setAsyncState(asyncState: AsyncState) {
        headerView.updating = asyncState == AsyncState.Loading
        shimmerHeaderView.updating = asyncState == AsyncState.Loading
    }

    override fun onUpScroll() {
        val index = pagerView.currentItem
        val holder = pagerView.findViewHolderForAdapterPosition(index) as? WalletScreenHolder ?: return
        holder.scrollUp()
    }
}