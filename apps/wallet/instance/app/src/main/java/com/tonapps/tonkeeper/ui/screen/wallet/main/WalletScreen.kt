package com.tonapps.tonkeeper.ui.screen.wallet.main

import android.os.Bundle
import android.view.View
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.component.MainRecyclerView
import com.tonapps.tonkeeper.ui.component.wallet.WalletHeaderView
import com.tonapps.portfolio.screens.list.WalletsListFragment
import com.tonapps.tonkeeper.ui.screen.camera.CameraScreen
import com.tonapps.tonkeeper.ui.screen.main.MainScreen
import com.tonapps.tonkeeper.ui.screen.settings.main.SettingsScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item.Status
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.WalletAdapter
import com.tonapps.tonkeeper.ui.screen.watchonly.WatchInfoScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxEventsScreen
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import uikit.drawable.BarDrawable
import uikit.extensions.collectFlow
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.BalloonTooltip

class WalletScreen(wallet: WalletEntity): MainScreen.Child(R.layout.fragment_wallet, wallet) {

    override val fragmentName: String = "WalletScreen"

    override val viewModel: WalletViewModel by walletViewModel()

    private val adapter = WalletAdapter()

    private lateinit var headerView: WalletHeaderView
    private lateinit var refreshLayout: SwipeRefreshLayout
    private lateinit var listView: MainRecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.onWalletClick = { navigation?.add(WalletsListFragment()) }
        headerView.onScanClick = {
            if (wallet.isWatchOnly) {
                navigation?.add(WatchInfoScreen.newInstance(wallet))
            } else {
                navigation?.add(CameraScreen.newInstance())
            }
        }
        headerView.onHistoryClick = { navigation?.add(TxEventsScreen.newInstance(wallet)) }

        headerView.onSettingsClick = { navigation?.add(SettingsScreen.newInstance("wallet")) }
        headerView.doWalletSwipe = { right ->
            if (right) {
                viewModel.prevWallet()
            } else {
                viewModel.nextWallet()
            }
        }

        refreshLayout = view.findViewById(R.id.refresh)
        refreshLayout.setOnRefreshListener { viewModel.refresh() }

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter

        collectFlow(viewModel.uiLabelFlow.filterNotNull(), headerView::setWallet)
        collectFlow(viewModel.hasBackupFlow, headerView::setDot)
        collectFlow(viewModel.statusFlow) { status ->
            if (refreshLayout.isRefreshing && status != Status.Updating) {
                refreshLayout.isRefreshing = false
            }
        }
        lifecycleScope.launch {
            if (viewModel.consumeAddMcTooltip()) {
                headerView.walletAnchorView.doOnLayout { anchor ->
                    var tooltip: BalloonTooltip? = null
                    var navigated = false
                    tooltip = BalloonTooltip.show(
                        anchorView = anchor,
                        badgeText = getString(Localization.badge_new),
                        messageText = getString(Localization.tooltip_add_multichain_wallet),
                        placement = BalloonTooltip.Placement.BOTTOM,
                        onClickListener = {
                            tooltip?.dismiss()
                            if (!navigated) {
                                navigated = true
                                navigation?.add(WalletsListFragment())
                            }
                        },
                    )
                }
            }
        }
    }

    override fun getRecyclerView(): RecyclerView? {
        if (this::listView.isInitialized) {
            return listView
        }
        return null
    }

    override fun getTopBarDrawable(): BarDrawable? {
        if (this::headerView.isInitialized) {
            return headerView.background as? BarDrawable
        }
        return null
    }

    companion object {
        fun newInstance(wallet: WalletEntity) = WalletScreen(wallet)
    }
}