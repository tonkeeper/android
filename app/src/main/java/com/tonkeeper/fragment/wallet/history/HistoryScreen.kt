package com.tonkeeper.fragment.wallet.history

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.R
import com.tonkeeper.fragment.wallet.history.list.HistoryAdapter
import com.tonkeeper.fragment.wallet.history.list.HistoryItemDecoration
import uikit.mvi.AsyncState
import uikit.mvi.UiScreen
import uikit.widget.HeaderView
import uikit.widget.LoaderView

class HistoryScreen: UiScreen<HistoryScreenState, HistoryScreenEffect, HistoryScreenFeature>(R.layout.fragment_history) {

    companion object {
        fun newInstance() = HistoryScreen()
    }

    override val feature: HistoryScreenFeature by viewModels()

    private lateinit var headerView: HeaderView
    private lateinit var listView: RecyclerView
    private lateinit var emptyView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)

        listView = view.findViewById(R.id.list)
        listView.addItemDecoration(HistoryItemDecoration(view.context))

        emptyView = view.findViewById(R.id.empty)
    }

    override fun newUiState(state: HistoryScreenState) {
        setAsyncState(state.asyncState)
        if (state.items.isEmpty() && state.asyncState == AsyncState.Default) {
            listView.visibility = View.GONE

            emptyView.visibility = View.VISIBLE
        } else {
            listView.visibility = View.VISIBLE
            listView.adapter = HistoryAdapter(state.items)
            emptyView.visibility = View.GONE
        }
    }

    private fun setAsyncState(asyncState: AsyncState) {
        if (asyncState == AsyncState.Loading) {
            headerView.setUpdating(R.string.updating)
        } else {
            headerView.setDefault()
        }
    }
}