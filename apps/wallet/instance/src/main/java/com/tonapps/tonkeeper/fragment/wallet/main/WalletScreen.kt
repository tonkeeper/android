package com.tonapps.tonkeeper.fragment.wallet.main

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.dialog.fiat.FiatDialog
import com.tonapps.tonkeeper.extensions.launch
import com.tonapps.tonkeeper.fragment.main.MainTabScreen
import com.tonapps.tonkeeper.fragment.main.MainViewModel
import com.tonapps.tonkeeper.fragment.settings.main.SettingsScreen
import com.tonapps.tonkeeper.fragment.wallet.main.list.WalletAdapter
import com.tonapps.tonkeeper.fragment.wallet.main.view.WalletHeaderView
import com.tonapps.tonkeeper.ui.screen.picker.WalletPickerFragment
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.extensions.bottomScrolled
import uikit.extensions.collectFlow
import uikit.extensions.toggleVisibilityAnimation
import uikit.extensions.topScrolled
import uikit.extensions.verticalScrolled
import uikit.navigation.Navigation.Companion.navigation

class WalletScreen: MainTabScreen<WalletScreenState, WalletScreenEffect, WalletScreenFeature>(R.layout.fragment_wallet) {

    companion object {
        fun newInstance() = WalletScreen()
    }

    private val mainViewModel: MainViewModel by lazy {
        requireParentFragment().getViewModel()
    }

    override val feature: WalletScreenFeature by viewModels()

    private val adapter = WalletAdapter()

    private lateinit var headerView: WalletHeaderView
    private lateinit var listView: RecyclerView
    private lateinit var shimmerView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigation?.setFragmentResultListener(FiatDialog.FIAT_DIALOG_REQUEST) { _ ->
            FiatDialog.open(requireContext())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        headerView = view.findViewById(R.id.header)
        headerView.onSettingsClick = { navigation?.add(SettingsScreen.newInstance()) }
        headerView.onWalletClick = { navigation?.add(WalletPickerFragment.newInstance()) }

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter
        collectFlow(listView.topScrolled, headerView::setDivider)
        collectFlow(listView.bottomScrolled, mainViewModel::setBottomScrolled)

        shimmerView = view.findViewById(R.id.shimmer)
    }

    override fun newUiState(state: WalletScreenState) {
        headerView.setWallet(state.title, state.emoji, state.color)
        if (state.items.isNotEmpty()) {
            adapter.submitList(state.items) {
                toggleVisibilityAnimation(shimmerView, listView)
            }
        }
    }

    override fun onUpScroll() {
        listView.smoothScrollToPosition(0)
    }
}