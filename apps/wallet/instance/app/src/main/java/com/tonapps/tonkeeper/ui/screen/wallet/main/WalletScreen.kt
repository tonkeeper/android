package com.tonapps.tonkeeper.ui.screen.wallet.main

import android.os.Bundle
import android.view.View
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerMigrationStartFrom
import com.tonapps.bus.screenrender.ScreenRenderId
import com.tonapps.bus.screenrender.ScreenRenderTracker
import com.tonapps.bus.screenrender.ScreenRenderTrigger
import com.tonapps.core.screenrender.ScreenRenderFragmentLifecycle
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.component.MainRecyclerView
import com.tonapps.tonkeeper.ui.component.wallet.WalletHeaderView
import com.tonapps.portfolio.screens.list.WalletsListFragment
import com.tonapps.tonkeeper.ui.screen.main.MainScreen
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeper.ui.screen.settings.main.SettingsScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item.Status
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.WalletAdapter
import com.tonapps.tonkeeper.ui.screen.watchonly.WatchInfoScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxEventsScreen
import com.tonapps.wallet.features.ledger.pairing.LedgerPairingFragment
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import uikit.drawable.BarDrawable
import uikit.extensions.collectFlow
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.BalloonTooltip

class WalletScreen(wallet: WalletEntity): MainScreen.Child(R.layout.fragment_wallet, wallet) {

    override val fragmentName: String = "WalletScreen"

    override val viewModel: WalletViewModel by walletViewModel()

    private val rootViewModel: RootViewModel by activityViewModel()

    private val adapter = WalletAdapter()

    private val screenRender = ScreenRenderFragmentLifecycle(this, ScreenRenderId.Portfolio)

    private lateinit var headerView: WalletHeaderView
    private lateinit var refreshLayout: SwipeRefreshLayout
    private lateinit var listView: MainRecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        screenRender.onViewCreated()
        headerView = view.findViewById(R.id.header)
        headerView.onWalletClick = { navigation?.add(WalletsListFragment()) }
        headerView.onScanClick = {
            if (wallet.isWatchOnly) {
                navigation?.add(WatchInfoScreen.newInstance(wallet))
            } else {
                rootViewModel.openCamera(wallet)
            }
        }
        headerView.onHistoryClick = {
            ScreenRenderTracker.shared.begin(ScreenRenderId.History, ScreenRenderTrigger.InApp, isFirstOpen = true)
            navigation?.add(TxEventsScreen.newInstance(wallet))
        }

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
            when (viewModel.consumeHeaderTooltip(wallet)) {
                WalletHeaderTooltip.LedgerMigration -> showHeaderTooltip(
                    message = getString(Localization.ledger_mc_migrate_tooltip),
                    onDismissed = ::onLedgerMigrationTooltipDismissed,
                ) {
                    viewModel.onLedgerMigrationTooltipSeen()
                    navigation?.add(LedgerPairingFragment.newMigrationInstance(wallet.id, LedgerMigrationStartFrom.Tooltip))
                }
                WalletHeaderTooltip.AddMultichain -> showHeaderTooltip(getString(Localization.tooltip_add_multichain_wallet)) {
                    navigation?.add(WalletsListFragment())
                }
                null -> Unit
            }
        }
    }

    private fun onLedgerMigrationTooltipDismissed(reason: BalloonTooltip.DismissReason) {
        when (reason) {
            BalloonTooltip.DismissReason.User -> viewModel.onLedgerMigrationTooltipDismissedByUser()
            BalloonTooltip.DismissReason.Timeout -> viewModel.onLedgerMigrationTooltipSeen()
            BalloonTooltip.DismissReason.Other -> Unit
        }
    }

    private fun showHeaderTooltip(
        message: String,
        onDismissed: ((BalloonTooltip.DismissReason) -> Unit)? = null,
        onClick: () -> Unit,
    ) {
        headerView.walletAnchorView.doOnLayout { anchor ->
            var tooltip: BalloonTooltip? = null
            var navigated = false
            tooltip = BalloonTooltip.show(
                anchorView = anchor,
                badgeText = getString(Localization.badge_new),
                messageText = message,
                placement = BalloonTooltip.Placement.BOTTOM,
                onDismissed = onDismissed,
                onClickListener = {
                    tooltip?.dismiss()
                    if (!navigated) {
                        navigated = true
                        onClick()
                    }
                },
            )
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        screenRender.onHiddenChanged(hidden)
        if (!hidden) {
            viewModel.onScreenShown()
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