package com.tonapps.tonkeeper.ui.screen.browser.connected

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.browser.connected.list.Adapter
import com.tonapps.tonkeeper.ui.screen.browser.connected.list.Item
import com.tonapps.tonkeeper.ui.screen.browser.main.BrowserMainViewModel
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.getViewModel
import uikit.dialog.alert.AlertDialog
import uikit.extensions.collectFlow
import uikit.extensions.isMaxScrollReached
import uikit.utils.RecyclerVerticalScrollListener

class BrowserConnectedScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_browser_connected, wallet) {

    override val viewModel: BrowserConnectedViewModel by walletViewModel()

    private val mainViewModel: BrowserMainViewModel by lazy {
        requireParentFragment().getViewModel()
    }

    private val adapter = Adapter { app ->
        deleteAppConfirm(app)
    }

    private val scrollListener = object : RecyclerVerticalScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, verticalScrollOffset: Int) {
            mainViewModel.setTopScrolled(verticalScrollOffset > 0)
            mainViewModel.setBottomScrolled(!recyclerView.isMaxScrollReached)
        }
    }

    private lateinit var listView: RecyclerView
    private lateinit var placeholderView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView = view.findViewById(R.id.list)
        listView.adapter = adapter
        listView.layoutManager = object : GridLayoutManager(context, SPAN_COUNT) {

            override fun supportsPredictiveItemAnimations(): Boolean = false
        }

        placeholderView = view.findViewById(R.id.placeholder)

        collectFlow(viewModel.uiItemsFlow, ::setList)
    }

    private fun setList(items: List<Item>) {
        if (items.isEmpty()) {
            listView.visibility = View.GONE
            placeholderView.visibility = View.VISIBLE
        } else {
            listView.visibility = View.VISIBLE
            placeholderView.visibility = View.GONE
            adapter.submitList(items)
        }
    }

    private fun deleteAppConfirm(app: AppEntity) {
        val message = getString(Localization.remove_dapp_confirm, app.name)
        AlertDialog.Builder(requireContext())
            .setMessage(message)
            .setNegativeButton(Localization.confirm) {
                viewModel.deleteConnect(app)
            }
            .setPositiveButton(Localization.cancel) {

            }.show()
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

        fun newInstance(wallet: WalletEntity) = BrowserConnectedScreen(wallet)
    }
}