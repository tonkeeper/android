package com.tonapps.tonkeeper.ui.screen.collectibles.main

import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.extensions.isLightTheme
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.UiListState
import com.tonapps.tonkeeper.ui.screen.collectibles.main.list.Adapter
import com.tonapps.tonkeeper.ui.screen.collectibles.manage.CollectiblesManageScreen
import com.tonapps.tonkeeper.ui.screen.main.MainScreen
import com.tonapps.tonkeeper.ui.screen.qr.QRScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundPageColor
import com.tonapps.uikit.color.backgroundTransparentColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import uikit.drawable.BarDrawable
import uikit.extensions.collectFlow
import uikit.widget.EmptyLayout
import uikit.widget.HeaderView

class CollectiblesScreen(wallet: WalletEntity): MainScreen.Child(R.layout.fragment_main_list, wallet) {

    override val fragmentName: String = "CollectiblesScreen"

    override val viewModel: CollectiblesViewModel by walletViewModel()

    private val adapter = Adapter()

    private lateinit var headerView: HeaderView
    private lateinit var refreshView: SwipeRefreshLayout
    private lateinit var listView: RecyclerView
    private lateinit var emptyView: EmptyLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.title = getString(Localization.collectibles)
        headerView.setTitleGravity(Gravity.START)
        headerView.hideCloseIcon()
        if (requireContext().isLightTheme) {
            headerView.setColor(requireContext().backgroundPageColor)
        } else {
            headerView.setColor(requireContext().backgroundTransparentColor)
        }

        refreshView = view.findViewById(R.id.refresh)
        refreshView.setOnRefreshListener { viewModel.refresh() }

        listView = view.findViewById(R.id.list)
        listView.updatePadding(top = 0)
        listView.layoutManager = object : GridLayoutManager(context, 3) {
            override fun supportsPredictiveItemAnimations(): Boolean = false
        }
        listView.adapter = adapter

        emptyView = view.findViewById(R.id.empty)
        emptyView.doOnButtonClick = { openQRCode() }

        collectFlow(viewModel.uiListStateFlow) { state ->
            when (state) {
                is UiListState.Loading -> {
                    removeActionIcons()
                    adapter.applySkeleton()
                    headerView.setSubtitle(Localization.updating)
                }
                is UiListState.Empty -> {
                    removeActionIcons()
                    refreshView.isRefreshing = false
                    setEmptyState()
                    headerView.setSubtitle(null)
                    headerView.setAction(0)
                }
                is UiListState.Items -> {
                    applyActionIcons()
                    setListState()
                    adapter.submitList(state.items) {
                        if (!state.cache) {
                            headerView.setSubtitle(null)
                            refreshView.isRefreshing = false
                        }
                    }
                }
            }
        }
    }

    private fun applyActionIcons() {
        headerView.setAction(UIKitIcon.ic_sliders_16)
        headerView.doOnActionClick = {
            navigation?.add(CollectiblesManageScreen.newInstance(wallet))
        }
        headerView.setRightButton(Localization.spam) {
            navigation?.add(CollectiblesManageScreen.newInstance(wallet, true))
        }
    }

    private fun removeActionIcons() {
        headerView.setAction(0)
        headerView.doOnActionClick = null
        headerView.setRightContent(null)
    }

    private fun openQRCode() {
        navigation?.add(QRScreen.newInstance(screenContext.wallet))
    }

    private fun setEmptyState() {
        if (emptyView.visibility == View.VISIBLE) {
            return
        }
        emptyView.visibility = View.VISIBLE
        listView.visibility = View.GONE
    }

    private fun setListState() {
        if (listView.visibility == View.VISIBLE) {
            return
        }
        emptyView.visibility = View.GONE
        listView.visibility = View.VISIBLE
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

        fun newInstance(wallet: WalletEntity) = CollectiblesScreen(wallet)
    }
}