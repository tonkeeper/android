package com.tonkeeper.fragment.wallet.history

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.R
import com.tonkeeper.core.history.list.HistoryAdapter
import com.tonkeeper.dialog.fiat.FiatDialog
import com.tonkeeper.extensions.launch
import com.tonkeeper.extensions.receive
import com.tonkeeper.fragment.main.MainTabScreen
import com.tonkeeper.fragment.receive.ReceiveScreen
import uikit.extensions.toggleVisibilityAnimation
import uikit.extensions.verticalScrolled
import uikit.list.LinearLayoutManager
import uikit.mvi.AsyncState
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.EmptyLayout
import uikit.widget.HeaderView

class HistoryScreen: MainTabScreen<HistoryScreenState, HistoryScreenEffect, HistoryScreenFeature>(R.layout.fragment_history) {

    companion object {

        const val DeepLink = "tonkeeper://activity"

        fun newInstance() = HistoryScreen()
    }

    override val feature: HistoryScreenFeature by viewModels()

    private val adapter = HistoryAdapter()

    private lateinit var headerView: HeaderView
    private lateinit var listView: RecyclerView
    private lateinit var emptyView: EmptyLayout
    private lateinit var shimmerView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        headerView = view.findViewById(R.id.header)

        listView = view.findViewById(R.id.list)
        listView.layoutManager = LinearLayoutManager(view.context)
        listView.adapter = adapter
        listView.verticalScrolled.launch(this) {
            headerView.divider = it
        }

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
            toggleVisibilityAnimation(shimmerView, emptyView)
        } else if (state.items.isNotEmpty()) {
            adapter.submitList(state.items) {
                toggleVisibilityAnimation(shimmerView, listView)
            }
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
        } else {
            headerView.setDefault()
        }
    }

    override fun onUpScroll() {
        listView.smoothScrollToPosition(0)
        headerView.divider = false
    }
}