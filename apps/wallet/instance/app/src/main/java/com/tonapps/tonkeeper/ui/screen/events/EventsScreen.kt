package com.tonapps.tonkeeper.ui.screen.events

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tonapps.tonkeeper.core.history.list.HistoryAdapter
import com.tonapps.tonkeeper.core.history.list.HistoryItemDecoration
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.screen.events.filters.FiltersAdapter
import com.tonapps.tonkeeper.ui.screen.main.MainScreen
import com.tonapps.tonkeeper.ui.screen.purchase.PurchaseScreen
import com.tonapps.tonkeeper.ui.screen.qr.QRScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundTransparentColor
import com.tonapps.uikit.list.LinearLayoutManager
import com.tonapps.uikit.list.ListPaginationListener
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.withContext
import uikit.drawable.BarDrawable
import uikit.drawable.HeaderDrawable
import uikit.extensions.collectFlow
import uikit.extensions.dp
import uikit.widget.EmptyLayout
import uikit.widget.HeaderView

class EventsScreen(wallet: WalletEntity) : MainScreen.Child(R.layout.fragment_main_events_list, wallet) {

    override val viewModel: EventsViewModel by walletViewModel()

    private val legacyAdapter = HistoryAdapter()
    private val filtersAdapter = FiltersAdapter {
        viewModel.clickFilter(it)
    }

    private val paginationListener = object : ListPaginationListener() {
        override fun onLoadMore() {
            viewModel.loadMore()
        }
    }

    private lateinit var headerView: HeaderView
    private lateinit var headerDrawable: HeaderDrawable
    private lateinit var refreshView: SwipeRefreshLayout
    private lateinit var listView: RecyclerView
    private lateinit var filtersView: RecyclerView
    private lateinit var emptyView: EmptyLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.title = getString(Localization.history)
        headerView.setSubtitle(Localization.updating)
        headerView.setColor(requireContext().backgroundTransparentColor)

        refreshView = view.findViewById(R.id.refresh)
        refreshView.setOnRefreshListener { viewModel.initialLoad() }

        headerDrawable = HeaderDrawable(requireContext())
        headerDrawable.setColor(requireContext().backgroundTransparentColor)

        filtersView = view.findViewById(R.id.filters)
        filtersView.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        filtersView.adapter = filtersAdapter
        filtersView.background = headerDrawable
        filtersView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                outRect.right = 8.dp
            }
        })

        listView = view.findViewById(R.id.list)
        listView.adapter = legacyAdapter
        listView.addOnScrollListener(paginationListener)
        listView.addItemDecoration(HistoryItemDecoration())

        emptyView = view.findViewById(R.id.empty)
        emptyView.doOnButtonClick = { first ->
            if (first) {
                navigation?.add(PurchaseScreen.newInstance(screenContext.wallet))
            } else {
                openQRCode()
            }
        }

        // collectFlow(viewModel.uiStateFlow, ::applyState)
        collectFlow(viewModel.uiFilterItemsFlow, filtersAdapter::submitList)
        collectFlow(viewModel.uiStateFlow, ::applyState)
    }


    private fun applyState(state: EventsUiState) {
        if (state.uiItems.isEmpty() && !state.loading) {
            setEmptyState()
        } else {
            if (state.loading) {
                setLoading(true)
            }
            setListState(state.uiItems) {
                if (!state.loading) {
                    setLoading(false)
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        if (refreshView.isRefreshing) {
            refreshView.isRefreshing = loading
        }
        if (loading) {
            headerView.setSubtitle(Localization.updating)
        } else {
            headerView.setSubtitle(null)
        }
    }

    override fun scrollUp() {
        super.scrollUp()
        listView.scrollToPosition(0)
        viewModel.initialLoad()
    }

    private fun openQRCode() {
        navigation?.add(QRScreen.newInstance(screenContext.wallet, TokenEntity.TON))
    }

    private fun setEmptyState() {
        setLoading(false)
        if (emptyView.visibility != View.VISIBLE) {
            headerView.setSubtitle(null)
            emptyView.visibility = View.VISIBLE
            refreshView.visibility = View.GONE
        }
    }

    private fun setListState(uiItems: List<HistoryItem>, commitCallback: Runnable) {
        legacyAdapter.submitList(uiItems, commitCallback)
        if (refreshView.visibility != View.VISIBLE) {
            emptyView.visibility = View.GONE
            refreshView.visibility = View.VISIBLE
        }
    }

    override fun getRecyclerView(): RecyclerView? {
        if (this::listView.isInitialized) {
            return listView
        }
        return null
    }

    override fun getTopBarDrawable(): BarDrawable? {
        if (this::headerDrawable.isInitialized) {
            return headerDrawable
        }
        return null
    }

    companion object {
        fun newInstance(wallet: WalletEntity) = EventsScreen(wallet)
    }
}