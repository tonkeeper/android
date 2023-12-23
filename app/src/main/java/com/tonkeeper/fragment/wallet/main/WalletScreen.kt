package com.tonkeeper.fragment.wallet.main

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.dialog.IntroWalletDialog
import com.tonkeeper.dialog.fiat.FiatDialog
import com.tonkeeper.extensions.launch
import com.tonkeeper.extensions.openCamera
import com.tonkeeper.fragment.main.MainTabScreen
import com.tonkeeper.fragment.wallet.main.list.WalletAdapter
import com.tonkeeper.fragment.wallet.main.popup.NewWalletPopup
import com.tonkeeper.fragment.wallet.main.popup.WalletPickerPopup
import com.tonkeeper.fragment.wallet.main.view.WalletHeaderView
import kotlinx.coroutines.launch
import uikit.extensions.toggleVisibilityAnimation
import uikit.extensions.verticalScrolled
import uikit.list.LinearLayoutManager
import uikit.mvi.AsyncState
import uikit.navigation.Navigation.Companion.navigation

class WalletScreen: MainTabScreen<WalletScreenState, WalletScreenEffect, WalletScreenFeature>(R.layout.fragment_wallet) {

    companion object {
        fun newInstance() = WalletScreen()
    }

    override val feature: WalletScreenFeature by viewModels()

    private val adapter = WalletAdapter()

    private lateinit var headerView: WalletHeaderView
    private lateinit var listView: RecyclerView
    private lateinit var shimmerView: View

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
            FiatDialog.open(requireContext())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        headerView = view.findViewById(R.id.header)
        headerView.doOnTitleClick = { selectWallet(headerView.titleView) }
        headerView.doOnActionClick = { navigation?.openCamera() }

        listView = view.findViewById(R.id.list)
        listView.layoutManager = LinearLayoutManager(view.context)
        listView.adapter = adapter
        listView.verticalScrolled.launch(this) {
            headerView.divider = it
        }

        shimmerView = view.findViewById(R.id.shimmer)
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
        } else {
            headerView.title = state.title
        }

        if (state.items.isNotEmpty()) {
            adapter.submitList(state.items) {
                toggleVisibilityAnimation(shimmerView, listView)
            }
        }
    }

    private fun setAsyncState(asyncState: AsyncState) {
        headerView.updating = asyncState == AsyncState.Loading
    }

    override fun onUpScroll() {
        listView.smoothScrollToPosition(0)
        headerView.divider = false
    }
}