package com.tonapps.tonkeeper.fragment.wallet.history

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.core.history.list.HistoryAdapter
import com.tonapps.tonkeeper.dialog.fiat.FiatDialog
import com.tonapps.tonkeeper.extensions.launch
import com.tonapps.tonkeeper.extensions.receive
import com.tonapps.tonkeeper.fragment.main.MainTabScreen
import uikit.extensions.collectFlow
import uikit.extensions.toggleVisibilityAnimation
import uikit.extensions.topScrolled
import uikit.extensions.verticalScrolled
import uikit.mvi.AsyncState
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.EmptyLayout
import uikit.widget.HeaderView

class HistoryScreen: MainTabScreen<HistoryScreenState, HistoryScreenEffect, HistoryScreenFeature>(R.layout.fragment_history) {

    companion object {

        const val DeepLink = "tonkeeper://activity"

        fun newInstance() = HistoryScreen()
    }

    private val adapter = HistoryAdapter()

    private val scrollListener = object : com.tonapps.uikit.list.ListPaginationListener() {
        override fun onLoadMore() {
            val latLt = adapter.getLastLt() ?: return
            feature.loadMore(latLt)
        }
    }

    override val feature: HistoryScreenFeature by viewModels()

    private lateinit var headerView: HeaderView
    private lateinit var listView: RecyclerView
    private lateinit var emptyView: EmptyLayout
    private lateinit var shimmerView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        headerView = view.findViewById(R.id.header)

        listView = view.findViewById(R.id.list)
        listView.layoutManager = com.tonapps.uikit.list.LinearLayoutManager(view.context)
        listView.adapter = adapter
        collectFlow(listView.topScrolled, headerView::setDivider)

        emptyView = view.findViewById(R.id.empty)
        emptyView.doOnButtonClick = { first ->
            if (first) {
                FiatDialog.open(requireContext())
            } else {
                navigation?.receive()
            }
        }

        shimmerView = view.findViewById(R.id.shimmer)
    }

    override fun newUiState(state: HistoryScreenState) {
        setAsyncState(state.asyncState)
        if (state.items.isEmpty() && state.asyncState == AsyncState.Default) {
            setEmptyState()
        } else if (state.items.isNotEmpty()) {
            adapter.submitList(state.items) {
                emptyView.visibility = View.GONE
                toggleVisibilityAnimation(shimmerView, listView)
            }
        }

        listView.clearOnScrollListeners()
        if (!state.loadedAll) {
            listView.addOnScrollListener(scrollListener)
        }
    }

    private fun setEmptyState() {
        toggleVisibilityAnimation(shimmerView, emptyView)
    }

    override fun newUiEffect(effect: HistoryScreenEffect) {
        super.newUiEffect(effect)
        if (effect == HistoryScreenEffect.UpScroll) {
            upScroll()
        }
    }

    private fun setAsyncState(asyncState: AsyncState) {
        if (asyncState == AsyncState.Loading) {
            headerView.setUpdating(Localization.updating)
        } else {
            headerView.setDefault()
        }
    }

    override fun onUpScroll() {
        listView.smoothScrollToPosition(0)
        headerView.setDivider(false)
    }
}