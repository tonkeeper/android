package com.tonapps.tonkeeper.ui.screen.settings.apps

import android.os.Bundle
import android.util.Log
import android.view.View
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.settings.apps.list.Adapter
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.localization.Localization
import uikit.base.BaseFragment
import uikit.dialog.alert.AlertDialog
import uikit.extensions.collectFlow

class AppsScreen(wallet: WalletEntity): BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.SwipeBack {

    override val viewModel: AppsViewModel by walletViewModel()

    private val adapter = Adapter({ app -> disconnectApp(app) }, { disconnectAll() })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(getString(Localization.apps))
        setAdapter(adapter)
    }

    private fun disconnectApp(app: AppEntity) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setMessage(getString(Localization.disconnect_dapp_confirm, app.name))
        builder.setNegativeButton(Localization.disconnect) { viewModel.disconnectApp(app) }
        builder.setPositiveButton(Localization.cancel)
        builder.show()
    }

    private fun disconnectAll() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setMessage(getString(Localization.disconnect_all_apps_confirm))
        builder.setNegativeButton(Localization.disconnect) { viewModel.disconnectAll() }
        builder.setPositiveButton(Localization.cancel)
        builder.show()
    }

    companion object {
        fun newInstance(wallet: WalletEntity) = AppsScreen(wallet)
    }
}