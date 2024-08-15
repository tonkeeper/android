package com.tonapps.tonkeeper.ui.screen.notifications.manage

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.ui.screen.notifications.manage.list.Adapter
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.widget.HeaderView
import uikit.widget.SimpleRecyclerView

class NotificationsManageScreen: BaseFragment(R.layout.fragment_notifications_manage), BaseFragment.SwipeBack {

    private val notificationsManageViewModel: NotificationsManageViewModel by viewModel()
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(notificationsManageViewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<HeaderView>(R.id.header)
        headerView.doOnCloseClick = { finish() }

        val listView = view.findViewById<SimpleRecyclerView>(R.id.list)
        listView.adapter = adapter
    }

    companion object {
        fun newInstance() = NotificationsManageScreen()
    }
}