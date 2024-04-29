package com.tonapps.tonkeeper.ui.screen.browser.connected

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.browser.connected.list.Adapter
import com.tonapps.tonkeeper.ui.screen.browser.main.BrowserMainViewModel
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.isMaxScrollReached
import uikit.utils.RecyclerVerticalScrollListener

class BrowserConnectedScreen: BaseFragment(R.layout.fragment_browser_connected) {

    private val connectedViewModel: BrowserConnectedViewModel by viewModel()
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView = view.findViewById(R.id.list)
        listView.adapter = adapter
        listView.layoutManager = object : GridLayoutManager(context, SPAN_COUNT) {

            override fun supportsPredictiveItemAnimations(): Boolean = false
        }

        collectFlow(connectedViewModel.uiItemsFlow, adapter::submitList)
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

        fun newInstance() = BrowserConnectedScreen()
    }
}