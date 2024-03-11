package com.tonapps.tonkeeper.fragment.wallet.collectibles

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.fragment.main.MainTabScreen
import com.tonapps.tonkeeper.fragment.main.MainViewModel
import com.tonapps.tonkeeper.fragment.wallet.collectibles.list.Adapter
import com.tonapps.uikit.color.backgroundTransparentColor
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.extensions.isMaxScrollReached
import uikit.extensions.toggleVisibilityAnimation
import uikit.mvi.AsyncState
import uikit.navigation.Navigation.Companion.navigation
import uikit.utils.RecyclerVerticalScrollListener
import uikit.widget.EmptyLayout
import uikit.widget.HeaderView

class CollectiblesScreen: MainTabScreen<CollectiblesScreenState, CollectiblesScreenEffect, CollectiblesScreenFeature>(R.layout.fragment_collectibles) {

    companion object {
        private const val spanCount = 3

        fun newInstance() = CollectiblesScreen()
    }

    override val feature: CollectiblesScreenFeature by viewModel()

    private val mainViewModel: MainViewModel by lazy {
        requireParentFragment().getViewModel()
    }

    private val adapter = Adapter()

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
        listView.layoutManager = object : GridLayoutManager(context, spanCount) {
            override fun supportsPredictiveItemAnimations(): Boolean = false
        }
        listView.adapter = adapter
        listView.addOnScrollListener(scrollListener)

        emptyView = view.findViewById(R.id.empty)
        emptyView.doOnButtonClick = { first ->
            if (first) {
                navigation?.openURL("https://getgems.io/")
            } else {
                // navigation?.receive()
            }
        }
    }

    override fun onUpScroll() {
        listView.smoothScrollToPosition(0)
        headerView.setDivider(false)
    }

    override fun newUiState(state: CollectiblesScreenState) {
        setAsyncState(state.asyncState)

        adapter.submitList(state.items)
    }

    private fun setAsyncState(asyncState: AsyncState) {
        if (asyncState == AsyncState.Loading) {
            headerView.setUpdating(Localization.updating)
        } else {
            headerView.setDefault()
        }
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