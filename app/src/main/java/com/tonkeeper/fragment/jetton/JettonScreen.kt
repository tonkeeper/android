package com.tonkeeper.fragment.jetton

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeperx.R
import com.tonkeeper.core.history.list.HistoryAdapter
import com.tonkeeper.extensions.launch
import com.tonkeeper.fragment.jetton.list.JettonAdapter
import com.tonkeeper.fragment.jetton.list.JettonItemDecoration
import uikit.base.BaseFragment
import uikit.extensions.toggleVisibilityAnimation
import uikit.extensions.verticalScrolled
import uikit.list.LinearLayoutManager
import uikit.list.ListPaginationListener
import uikit.mvi.AsyncState
import uikit.mvi.UiScreen
import uikit.widget.HeaderView

class JettonScreen : UiScreen<JettonScreenState, JettonScreenEffect, JettonScreenFeature>(R.layout.fragment_jetton), BaseFragment.SwipeBack {

    companion object {
        private const val JETTON_ADDRESS_KEY = "JETTON_ADDRESS_KEY"
        private const val JETTON_NAME_KEY = "JETTON_NAME_KEY"

        fun newInstance(jettonAddress: String, jettonName: String): JettonScreen {
            val screen = JettonScreen()
            screen.arguments = Bundle().apply {
                putString(JETTON_ADDRESS_KEY, jettonAddress)
                putString(JETTON_NAME_KEY, jettonName)
            }
            return screen
        }
    }

    private val jettonAdapter = JettonAdapter()
    private val historyAdapter = HistoryAdapter()

    private val jettonAddress: String by lazy {
        arguments?.getString(JETTON_ADDRESS_KEY)!!
    }

    private val jettonName: String by lazy {
        arguments?.getString(JETTON_NAME_KEY) ?: ""
    }

    private val scrollListener = object : ListPaginationListener() {
        override fun onLoadMore() {
            val latLt = historyAdapter.getLastLt() ?: return
            feature.loadMore(jettonAddress, latLt)
        }
    }

    override val feature: JettonScreenFeature by viewModels()
    override var doOnDragging: ((Boolean) -> Unit)? = null
    override var doOnDraggingProgress: ((Float) -> Unit)? = null

    private lateinit var headerView: HeaderView
    private lateinit var shimmerView: View
    private lateinit var listView: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.title = jettonName
        headerView.doOnCloseClick = { finish() }

        shimmerView = view.findViewById(R.id.shimmer)

        listView = view.findViewById(R.id.list)
        listView.layoutManager = LinearLayoutManager(view.context)
        listView.adapter = ConcatAdapter(jettonAdapter, historyAdapter)
        listView.addItemDecoration(JettonItemDecoration(view.context))
        listView.verticalScrolled.launch(this) {
            headerView.divider = it
        }
    }

    override fun onEndShowingAnimation() {
        super.onEndShowingAnimation()
        feature.load(jettonAddress)
    }

    override fun newUiState(state: JettonScreenState) {
        if (state.asyncState == AsyncState.Default) {
            jettonAdapter.submitList(state.getTopItems())
            historyAdapter.submitList(state.historyItems) {
                toggleVisibilityAnimation(shimmerView, listView)
            }
        }

        listView.clearOnScrollListeners()
        if (!state.loadedAll) {
            listView.addOnScrollListener(scrollListener)
        }
    }

}