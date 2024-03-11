package com.tonapps.tonkeeper.ui.screen.wallet

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.fragment.main.MainRecyclerView
import com.tonapps.tonkeeper.fragment.main.MainViewModel
import com.tonapps.tonkeeper.ui.component.WalletHeaderView
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

class WalletScreen: BaseFragment(R.layout.fragment_wallet) {

    private val walletViewModel: WalletViewModel by viewModel()
    private val mainViewModel: MainViewModel by lazy {
        requireParentFragment().getViewModel()
    }

    private val adapter = Adapter()

    private lateinit var headerView: WalletHeaderView
    private lateinit var listView: MainRecyclerView

    private val scrollListener = object : RecyclerVerticalScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, verticalScrollOffset: Int) {
            headerView.setDivider(verticalScrollOffset > 0)
            mainViewModel.setBottomScrolled(!recyclerView.isMaxScrollReached)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.onWalletClick = { navigation?.add(PickerScreen.newInstance()) }
        headerView.onSettingsClick = { navigation?.add(SettingsScreen.newInstance()) }

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter
        listView.addOnScrollListener(scrollListener)

        collectFlow(walletViewModel.uiLabelFlow, headerView::setWallet)
        collectFlow(walletViewModel.uiItemsFlow, ::submitList)
    }

    private fun submitList(list: List<Item>) {
        adapter.submitList(list)
    }

    override fun onResume() {
        super.onResume()
        attachScrollHandler()
    }

    override fun onPause() {
        super.onPause()
        detachScrollHandler()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            detachScrollHandler()
        } else {
            attachScrollHandler()
        }
    }

    private fun attachScrollHandler() {
        scrollListener.attach(listView)
    }

    private fun detachScrollHandler() {
        scrollListener.detach()
    }

    companion object {
        fun newInstance() = WalletScreen()
    }
}