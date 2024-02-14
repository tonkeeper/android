package com.tonapps.tonkeeper.fragment.wallet.collectibles

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.extensions.launch
import com.tonapps.tonkeeper.extensions.receive
import com.tonapps.tonkeeper.fragment.main.MainTabScreen
import com.tonapps.tonkeeper.fragment.wallet.collectibles.list.CollectiblesAdapter
import uikit.extensions.collectFlow
import uikit.extensions.toggleVisibilityAnimation
import uikit.extensions.topScrolled
import uikit.extensions.verticalScrolled
import uikit.mvi.AsyncState
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.EmptyLayout
import uikit.widget.HeaderView

class CollectiblesScreen: MainTabScreen<CollectiblesScreenState, CollectiblesScreenEffect, CollectiblesScreenFeature>(R.layout.fragment_collectibles) {

    companion object {
        private const val spanCount = 3

        fun newInstance() = CollectiblesScreen()
    }

    override val feature: CollectiblesScreenFeature by viewModels()

    private val adapter = CollectiblesAdapter()

    private lateinit var headerView: HeaderView
    private lateinit var listView: RecyclerView
    private lateinit var emptyView: EmptyLayout
    private lateinit var shimmerView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        listView = view.findViewById(R.id.list)
        listView.layoutManager = object : GridLayoutManager(context, spanCount) {
            override fun supportsPredictiveItemAnimations(): Boolean = false
        }
        listView.adapter = adapter
        collectFlow(listView.topScrolled, headerView::setDivider)

        emptyView = view.findViewById(R.id.empty)
        emptyView.doOnButtonClick = { first ->
            if (first) {
                navigation?.openURL("https://getgems.io/")
            } else {
                navigation?.receive()
            }
        }

        shimmerView = view.findViewById(R.id.shimmer)
    }

    override fun onUpScroll() {
        listView.smoothScrollToPosition(0)
        headerView.setDivider(false)
    }

    override fun newUiState(state: CollectiblesScreenState) {
        setAsyncState(state.asyncState)

        if (state.items.isEmpty() && state.asyncState == AsyncState.Default) {
            toggleVisibilityAnimation(shimmerView, emptyView)
        } else if (state.items.isNotEmpty()) {
            adapter.submitList(state.items) {
                toggleVisibilityAnimation(shimmerView, listView)
            }
        }
    }

    private fun setAsyncState(asyncState: AsyncState) {
        if (asyncState == AsyncState.Loading) {
            headerView.setUpdating(Localization.updating)
        } else {
            headerView.setDefault()
        }
    }
}