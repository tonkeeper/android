package com.tonapps.tonkeeper.ui.screen.notifications

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.notifications.list.Adapter
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletEntity
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.widget.HeaderView
import uikit.widget.SimpleRecyclerView

class NotificationsManageScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_notifications_manage, wallet), BaseFragment.SwipeBack {

    override val fragmentName: String = "NotificationsManageScreen"

    override val viewModel: NotificationsManageViewModel by walletViewModel()

    private val adapter = Adapter({ wallet, enabled ->
        viewModel.toggleWalletPush(wallet, enabled)
    }, { url, enabled ->
        viewModel.toggleDAppPush(url, enabled)
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<HeaderView>(R.id.header)
        headerView.doOnCloseClick = { finish() }

        val listView = view.findViewById<SimpleRecyclerView>(R.id.list)
        listView.adapter = adapter
    }

    companion object {

        fun newInstance(wallet: WalletEntity) = NotificationsManageScreen(wallet)
    }
}