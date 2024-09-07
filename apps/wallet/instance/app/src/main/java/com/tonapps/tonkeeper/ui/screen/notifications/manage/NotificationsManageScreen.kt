package com.tonapps.tonkeeper.ui.screen.notifications.manage

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.notifications.manage.list.Adapter
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletEntity
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.widget.HeaderView
import uikit.widget.SimpleRecyclerView

class NotificationsManageScreen(wallet: WalletEntity): BaseWalletScreen<ScreenContext.Wallet>(R.layout.fragment_notifications_manage, ScreenContext.Wallet(wallet)), BaseFragment.SwipeBack {

    override val viewModel: NotificationsManageViewModel by viewModel {
        parametersOf(screenContext.wallet)
    }

    private val adapter = Adapter { url, enabled ->
        viewModel.enabledPush(url, enabled)
    }

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