package com.tonapps.tonkeeper.fragment.chart

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.core.history.list.HistoryAdapter
import com.tonapps.tonkeeper.core.history.list.HistoryItemDecoration
import com.tonapps.tonkeeper.fragment.chart.list.ChartAdapter
import com.tonapps.tonkeeper.fragment.chart.list.ChartItemDecoration
import com.tonapps.uikit.list.ListPaginationListener
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.isMaxScrollReached
import uikit.extensions.toggleVisibilityAnimation
import uikit.extensions.topScrolled
import uikit.mvi.AsyncState
import uikit.mvi.UiScreen
import uikit.utils.RecyclerVerticalScrollListener
import uikit.widget.HeaderView
import uikit.widget.SimpleRecyclerView

class ChartScreen: UiScreen<ChartScreenState, ChartScreenEffect, ChartScreenFeature>(R.layout.fragment_chart), BaseFragment.SwipeBack {

    companion object {
        fun newInstance() = ChartScreen()
    }

    override val feature: ChartScreenFeature by viewModel()

    private val historyAdapter = HistoryAdapter()
    private val chartAdapter = ChartAdapter {
        feature.loadChart(it)
    }

    private val paginationScrollListener = object : ListPaginationListener() {
        override fun onLoadMore() {
            feature.loadMore()
        }
    }

    private val verticalScrollListener = object : RecyclerVerticalScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, verticalScrollOffset: Int) {
            headerView.setDivider(verticalScrollOffset > 0)
        }
    }

    private lateinit var headerView: HeaderView
    private lateinit var shimmerView: View
    private lateinit var listView: SimpleRecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        shimmerView = view.findViewById(R.id.shimmer)

        listView = view.findViewById(R.id.list)
        listView.adapter = ConcatAdapter(chartAdapter, historyAdapter)
        listView.addItemDecoration(ChartItemDecoration(view.context))
        listView.addItemDecoration(HistoryItemDecoration)
        listView.applyNavBottomPadding(requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium))
        collectFlow(listView.topScrolled, headerView::setDivider)
    }

    override fun newUiState(state: ChartScreenState) {
        if (state.asyncState == AsyncState.Default) {
            chartAdapter.submitList(state.getTopItems())
            historyAdapter.submitList(state.historyItems) {
                toggleVisibilityAnimation(shimmerView, listView)
            }
        }

        listView.clearOnScrollListeners()
        verticalScrollListener.attach(listView)
        if (!state.loadedAll) {
            listView.addOnScrollListener(paginationScrollListener)
        }
    }

    override fun onEndShowingAnimation() {
        super.onEndShowingAnimation()
        feature.load()
    }
}