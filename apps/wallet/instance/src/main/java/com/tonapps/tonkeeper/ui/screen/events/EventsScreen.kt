package com.tonapps.tonkeeper.ui.screen.events

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.core.history.list.HistoryAdapter
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.ui.screen.main.MainViewModel
import com.tonapps.tonkeeper.ui.component.WalletHeaderView
import com.tonapps.tonkeeper.ui.screen.events.list.Adapter
import com.tonapps.tonkeeper.ui.screen.main.MainScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundTransparentColor
import com.tonapps.uikit.list.ListCell
import com.tonapps.uikit.list.ListPaginationListener
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.isMaxScrollReached
import uikit.utils.RecyclerVerticalScrollListener
import uikit.widget.HeaderView

class EventsScreen: MainScreen.Child(R.layout.fragment_main_list) {

    private val eventsViewModel: EventsViewModel by viewModel()

    private val legacyAdapter = HistoryAdapter()
    private val paginationListener = object : ListPaginationListener() {
        override fun onLoadMore() {
            eventsViewModel.loadMore()
        }
    }

    private lateinit var headerView: HeaderView
    private lateinit var listView: RecyclerView
    private lateinit var filtersView: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.title = getString(Localization.history)
        headerView.setColor(requireContext().backgroundTransparentColor)

        filtersView = view.findViewById(R.id.filters)

        listView = view.findViewById(R.id.list)
        listView.adapter = legacyAdapter
        listView.addOnScrollListener(paginationListener)
        listView.addItemDecoration(object : RecyclerView.ItemDecoration() {

            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                super.getItemOffsets(outRect, view, parent, state)
                val position = parent.getChildAdapterPosition(view)
                val item = legacyAdapter.getItem(position)
                if (item is HistoryItem.Event && (item.position == ListCell.Position.LAST || item.position == ListCell.Position.SINGLE)) {
                    outRect.bottom = 6.dp
                } else if (item is HistoryItem.App) {
                    outRect.bottom = 6.dp
                }
            }
        })

        collectFlow(eventsViewModel.uiItemsFlow, legacyAdapter::submitList)
        collectFlow(eventsViewModel.isUpdatingFlow) { updating ->
            if (updating) {
                headerView.setSubtitle(Localization.updating)
            } else {
                headerView.setSubtitle(null)
            }
        }
    }

    override fun getRecyclerView() = listView

    override fun getHeaderDividerOwner() = headerView

    companion object {
        fun newInstance() = EventsScreen()
    }
}