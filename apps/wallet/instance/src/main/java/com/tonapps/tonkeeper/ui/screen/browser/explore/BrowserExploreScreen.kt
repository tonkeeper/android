package com.tonapps.tonkeeper.ui.screen.browser.explore

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.browser.explore.list.Adapter
import com.tonapps.tonkeeper.ui.screen.browser.explore.list.Item
import com.tonapps.tonkeeper.ui.screen.browser.main.BrowserMainViewModel
import com.tonapps.tonkeeper.ui.screen.main.MainScreen
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.isMaxScrollReached
import uikit.utils.RecyclerVerticalScrollListener
import uikit.widget.HeaderView

class BrowserExploreScreen : BaseFragment(R.layout.fragment_browser_explore) {

    private val exploreViewModel: BrowserExploreViewModel by viewModel()
    private val mainViewModel: BrowserMainViewModel by lazy {
        requireParentFragment().getViewModel()
    }

    private val adapter = Adapter()
    private val scrollListener = object : RecyclerVerticalScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, verticalScrollOffset: Int) {
            mainViewModel.setTopScrolled(verticalScrollOffset > 0)
            mainViewModel.setBottomScrolled(!recyclerView.isMaxScrollReached)
        }
    }

    private lateinit var listView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(exploreViewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView = view.findViewById(R.id.list)
        listView.adapter = adapter
        listView.layoutManager = object : GridLayoutManager(context, SPAN_COUNT) {

            init {
                spanSizeLookup = object : SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return when (adapter.getItemViewType(position)) {
                            Item.TYPE_TITLE, Item.TYPE_BANNERS -> SPAN_COUNT
                            else -> 1
                        }
                    }
                }
            }

            override fun supportsPredictiveItemAnimations(): Boolean = false
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

    companion object {

        private const val SPAN_COUNT = 4

        fun newInstance() = BrowserExploreScreen()
    }
}