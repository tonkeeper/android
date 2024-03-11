package com.tonapps.tonkeeper.fragment.wallet.history

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.core.history.list.HistoryAdapter
import com.tonapps.tonkeeper.dialog.fiat.FiatDialog
import com.tonapps.tonkeeper.fragment.main.MainTabScreen
import com.tonapps.tonkeeper.fragment.main.MainViewModel
import com.tonapps.uikit.color.backgroundTransparentColor
import com.tonapps.uikit.list.ListPaginationListener
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.extensions.collectFlow
import uikit.extensions.isMaxScrollReached
import uikit.extensions.toggleVisibilityAnimation
import uikit.extensions.topScrolled
import uikit.mvi.AsyncState
import uikit.navigation.Navigation.Companion.navigation
import uikit.utils.RecyclerVerticalScrollListener
import uikit.widget.EmptyLayout
import uikit.widget.HeaderView

class HistoryScreen: MainTabScreen<HistoryScreenState, HistoryScreenEffect, HistoryScreenFeature>(R.layout.fragment_history) {

    companion object {

        const val DeepLink = "tonkeeper://activity"

        fun newInstance() = HistoryScreen()
    }

    private val adapter = HistoryAdapter()

    private val offsetScrollListener = object : ListPaginationListener() {
        override fun onLoadMore() {
            val latLt = adapter.getLastLt() ?: return
            feature.loadMore(latLt)
        }
    }

    override val feature: HistoryScreenFeature by viewModel()
    private val mainViewModel: MainViewModel by lazy {
        requireParentFragment().getViewModel()
    }

    private lateinit var headerView: HeaderView
    private lateinit var listView: RecyclerView
    private lateinit var emptyView: EmptyLayout

    private val scrollListener = object : RecyclerVerticalScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, verticalScrollOffset: Int) {
            headerView.setDivider(verticalScrollOffset > 0)
            mainViewModel.setBottomScrolled(!recyclerView.isMaxScrollReached)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.setColor(requireContext().backgroundTransparentColor)

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter
        listView.addOnScrollListener(scrollListener)

        emptyView = view.findViewById(R.id.empty)
        emptyView.doOnButtonClick = { first ->
            if (first) {
                FiatDialog.open(requireContext())
            } else {
                // navigation?.receive()
            }
        }
    }

    override fun newUiState(state: HistoryScreenState) {
        setAsyncState(state.asyncState)
        if (state.items.isEmpty() && state.asyncState == AsyncState.Default) {
            emptyView.visibility = View.VISIBLE
            listView.visibility = View.GONE
        } else if (state.items.isEmpty() && state.asyncState == AsyncState.Loading) {
            emptyView.visibility = View.GONE
            listView.visibility = View.VISIBLE
            adapter.submitList(state.items)
        } else if (state.items.isNotEmpty()) {
            emptyView.visibility = View.GONE
            listView.visibility = View.VISIBLE
            adapter.submitList(state.items)
        }

        listView.removeOnScrollListener(offsetScrollListener)
        if (!state.loadedAll) {
            listView.addOnScrollListener(offsetScrollListener)
        }
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

    override fun onResume() {
        super.onResume()
        attachScrollHandler()
    }

    override fun onPause() {
        super.onPause()
        detachScrollHandler()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            detachScrollHandler()
        } else {
            attachScrollHandler()
        }
    }

    private fun attachScrollHandler() {
        scrollListener.attach(listView)
    }

    private fun detachScrollHandler() {
        scrollListener.detach()
    }
}