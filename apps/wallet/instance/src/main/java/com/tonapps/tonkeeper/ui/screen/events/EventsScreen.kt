package com.tonapps.tonkeeper.ui.screen.events

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.main.MainViewModel
import com.tonapps.tonkeeper.ui.component.WalletHeaderView
import com.tonapps.tonkeeper.ui.screen.events.list.Adapter
import com.tonapps.tonkeeper.ui.screen.main.MainScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundTransparentColor
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.isMaxScrollReached
import uikit.utils.RecyclerVerticalScrollListener
import uikit.widget.HeaderView

class EventsScreen: MainScreen.Child(R.layout.fragment_main_list) {

    private val eventsViewModel: EventsViewModel by viewModel()

    private val adapter = Adapter()

    private lateinit var headerView: HeaderView
    private lateinit var listView: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.title = getString(Localization.history)
        headerView.setColor(requireContext().backgroundTransparentColor)

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter

        collectFlow(eventsViewModel.uiItemsFlow, adapter::submitList)
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