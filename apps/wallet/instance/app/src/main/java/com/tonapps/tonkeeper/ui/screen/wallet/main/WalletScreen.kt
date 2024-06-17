package com.tonapps.tonkeeper.ui.screen.wallet.main

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.component.MainRecyclerView
import com.tonapps.tonkeeper.ui.component.wallet.WalletHeaderView
import com.tonapps.tonkeeper.ui.screen.main.MainScreen
import com.tonapps.tonkeeper.ui.screen.wallet.picker.PickerScreen
import com.tonapps.tonkeeper.ui.screen.settings.main.SettingsScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.WalletAdapter
import com.tonapps.tonkeeperx.R
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.drawable.BarDrawable
import uikit.extensions.collectFlow
import uikit.navigation.Navigation.Companion.navigation

class WalletScreen: MainScreen.Child(R.layout.fragment_wallet) {

    private val walletViewModel: WalletViewModel by viewModel()

    private val adapter: WalletAdapter by inject()

    private lateinit var headerView: WalletHeaderView
    private lateinit var listView: MainRecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(walletViewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.onWalletClick = { navigation?.add(PickerScreen.newInstance()) }
        headerView.onSettingsClick = { navigation?.add(SettingsScreen.newInstance()) }
        headerView.doWalletSwipe = { right ->
            if (right) {
                walletViewModel.prevWallet()
            } else {
                walletViewModel.nextWallet()
            }
        }

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter

        collectFlow(walletViewModel.uiLabelFlow, headerView::setWallet)
    }

    override fun getRecyclerView(): RecyclerView? {
        if (this::listView.isInitialized) {
            return listView
        }
        return null
    }

    override fun getHeaderDividerOwner(): BarDrawable.BarDrawableOwner? {
        if (this::headerView.isInitialized) {
            return headerView
        }
        return null
    }

    companion object {
        fun newInstance() = WalletScreen()
    }
}