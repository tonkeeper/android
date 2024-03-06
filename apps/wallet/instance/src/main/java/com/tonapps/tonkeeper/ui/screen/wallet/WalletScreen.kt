package com.tonapps.tonkeeper.ui.screen.wallet

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.fragment.main.MainRecyclerView
import com.tonapps.tonkeeper.fragment.main.MainViewModel
import com.tonapps.tonkeeper.ui.component.WalletHeaderView
import com.tonapps.tonkeeper.ui.screen.picker.PickerScreen
import com.tonapps.tonkeeper.ui.screen.settings.SettingsScreen
import com.tonapps.tonkeeper.ui.screen.wallet.list.Adapter
import com.tonapps.tonkeeper.ui.screen.wallet.list.Item
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.bottomScrolled
import uikit.extensions.collectFlow
import uikit.extensions.topScrolled
import uikit.navigation.Navigation.Companion.navigation

class WalletScreen: BaseFragment(R.layout.fragment_wallet) {

    private val walletViewModel: WalletViewModel by viewModel()
    private val mainViewModel: MainViewModel by lazy {
        requireParentFragment().getViewModel()
    }

    private val adapter = Adapter()

    private lateinit var headerView: WalletHeaderView
    private lateinit var listView: MainRecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.onWalletClick = { navigation?.add(PickerScreen.newInstance()) }
        headerView.onSettingsClick = { navigation?.add(SettingsScreen.newInstance()) }

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter

        collectFlow(listView.topScrolled, headerView::setDivider)
        collectFlow(listView.bottomScrolled, mainViewModel::setBottomScrolled)
        collectFlow(walletViewModel.uiLabelFlow, headerView::setWallet)
        collectFlow(walletViewModel.uiItemsFlow, ::submitList)
    }

    private fun submitList(list: List<Item>) {
        adapter.submitList(list)
    }

    companion object {
        fun newInstance() = WalletScreen()
    }
}