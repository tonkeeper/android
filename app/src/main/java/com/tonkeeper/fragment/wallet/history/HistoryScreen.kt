package com.tonkeeper.fragment.wallet.history

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.R
import com.tonkeeper.core.history.list.HistoryAdapter
import com.tonkeeper.core.history.list.HistoryItemDecoration
import com.tonkeeper.dialog.fiat.FiatDialog
import com.tonkeeper.fragment.main.MainTabScreen
import com.tonkeeper.fragment.receive.ReceiveScreen
import uikit.decoration.ListCellDecoration
import uikit.list.LinearLayoutManager
import uikit.mvi.AsyncState
import uikit.mvi.UiScreen
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView

class HistoryScreen: MainTabScreen<HistoryScreenState, HistoryScreenEffect, HistoryScreenFeature>(R.layout.fragment_history) {

    companion object {

        const val DeepLink = "tonkeeper://activity"

        fun newInstance() = HistoryScreen()
    }

    override val feature: HistoryScreenFeature by viewModels()

    private val fiatDialog: FiatDialog by lazy {
        FiatDialog(requireContext(), lifecycleScope)
    }

    private val adapter = HistoryAdapter()

    private lateinit var bodyView: View
    private lateinit var headerView: HeaderView
    private lateinit var listView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var buyView: View
    private lateinit var receiveView: View

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

        buyView = view.findViewById(R.id.buy)
        buyView.setOnClickListener {
            fiatDialog.show()
        }

        receiveView = view.findViewById(R.id.receive)
        receiveView.setOnClickListener {
            navigation?.add(ReceiveScreen.newInstance())
        }
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

    override fun newUiEffect(effect: HistoryScreenEffect) {
        super.newUiEffect(effect)
        if (effect == HistoryScreenEffect.UpScroll) {
            onUpScroll()
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

    override fun onUpScroll() {
        listView.smoothScrollToPosition(0)
    }
}