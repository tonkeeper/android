package com.tonapps.tonkeeper.ui.screen.events.spam

import android.os.Bundle
import android.util.Log
import android.view.View
import com.tonapps.tonkeeper.core.history.list.HistoryAdapter
import com.tonapps.tonkeeper.core.history.list.HistoryItemDecoration
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.uikit.list.ListPaginationListener
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import uikit.base.BaseFragment
import uikit.extensions.collectFlow

class SpamEventsScreen(
    wallet: WalletEntity
): BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.SwipeBack {

    override val fragmentName: String = "SpamEventsScreen"

    override val viewModel: SpamEventsViewModel by walletViewModel()

    private val paginationListener = object : ListPaginationListener() {
        override fun onLoadMore() {
            viewModel.loadMore()
        }
    }

    private val legacyAdapter = HistoryAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(getString(Localization.spam))
        setAdapter(legacyAdapter)
        addItemDecoration(HistoryItemDecoration())
        addScrollListener(paginationListener)

        collectFlow(viewModel.uiStateFlow) {
            setUiItems(it.uiItems)
        }
    }

    private fun setUiItems(uiItems: List<HistoryItem>) {
        val oldItemCount = legacyAdapter.itemCount
        legacyAdapter.submitList(uiItems) {
            if (2 >= oldItemCount && oldItemCount != 0) {
                scrollToTop()
            }
        }
    }

    companion object {

        fun newInstance(wallet: WalletEntity) = SpamEventsScreen(wallet)
    }
}