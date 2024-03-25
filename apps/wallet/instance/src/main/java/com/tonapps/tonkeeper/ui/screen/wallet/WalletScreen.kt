package com.tonapps.tonkeeper.ui.screen.wallet

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.component.MainRecyclerView
import com.tonapps.tonkeeper.ui.screen.main.MainViewModel
import com.tonapps.tonkeeper.ui.component.WalletHeaderView
import com.tonapps.tonkeeper.ui.screen.main.MainScreen
import com.tonapps.tonkeeper.ui.screen.picker.PickerScreen
import com.tonapps.tonkeeper.ui.screen.settings.main.SettingsScreen
import com.tonapps.tonkeeper.ui.screen.wallet.list.Adapter
import com.tonapps.tonkeeper.ui.screen.wallet.list.Item
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.isMaxScrollReached
import uikit.navigation.Navigation.Companion.navigation
import uikit.utils.RecyclerVerticalScrollListener

class WalletScreen: MainScreen.Child(R.layout.fragment_wallet) {

    private val walletViewModel: WalletViewModel by viewModel()

    private val adapter = Adapter {
        walletViewModel.toggleBalance()
    }

    private lateinit var headerView: WalletHeaderView
    private lateinit var listView: MainRecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.onWalletClick = { navigation?.add(PickerScreen.newInstance()) }
        headerView.onSettingsClick = { navigation?.add(SettingsScreen.newInstance()) }

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter

        collectFlow(walletViewModel.uiLabelFlow, headerView::setWallet)
        collectFlow(walletViewModel.uiItemsFlow, adapter::submitList)
    }

    override fun getRecyclerView() = listView

    override fun getHeaderDividerOwner() = headerView

    companion object {
        fun newInstance() = WalletScreen()
    }
}