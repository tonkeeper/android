package com.tonkeeper.fragment.wallet.history

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.R
import com.tonkeeper.core.history.list.HistoryAdapter
import com.tonkeeper.core.history.list.HistoryItemDecoration
import uikit.list.LinearLayoutManager
import uikit.mvi.AsyncState
import uikit.mvi.UiScreen
import uikit.widget.HeaderView

class HistoryScreen: UiScreen<HistoryScreenState, HistoryScreenEffect, HistoryScreenFeature>(R.layout.fragment_history) {

    companion object {
        fun newInstance() = HistoryScreen()
    }

    override val feature: HistoryScreenFeature by viewModels()

    private val adapter = HistoryAdapter()

    private lateinit var bodyView: View
    private lateinit var headerView: HeaderView
    private lateinit var listView: RecyclerView
    private lateinit var emptyView: View

    private lateinit var shimmerHeaderView: HeaderView
    private lateinit var shimmerView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bodyView = view.findViewById(R.id.body)

        headerView = view.findViewById(R.id.header)

        listView = view.findViewById(R.id.list)
        listView.layoutManager = LinearLayoutManager(view.context)
        listView.adapter = adapter
        listView.addItemDecoration(HistoryItemDecoration(view.context))

        emptyView = view.findViewById(R.id.empty)

        shimmerHeaderView = view.findViewById(R.id.shimmer_header)
        shimmerView = view.findViewById(R.id.shimmer)
    }

    override fun newUiState(state: HistoryScreenState) {
        setAsyncState(state.asyncState)
        if (state.items.isEmpty() && state.asyncState == AsyncState.Default) {
            listView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            adapter.submitList(state.items)
            listView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }

        if (state.items.isEmpty() && state.asyncState == AsyncState.Loading) {
            bodyView.visibility = View.GONE
            shimmerView.visibility = View.VISIBLE
        } else {
            bodyView.visibility = View.VISIBLE
            shimmerView.visibility = View.GONE
        }
    }

    private fun setAsyncState(asyncState: AsyncState) {
        if (asyncState == AsyncState.Loading) {
            headerView.setUpdating(R.string.updating)
            shimmerHeaderView.setUpdating(R.string.updating)
        } else {
            headerView.setDefault()
            shimmerHeaderView.setDefault()
        }
    }
}