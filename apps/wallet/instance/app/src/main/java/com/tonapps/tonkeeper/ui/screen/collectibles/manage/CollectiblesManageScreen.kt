package com.tonapps.tonkeeper.ui.screen.collectibles.manage

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.collectibles.manage.list.Adapter
import com.tonapps.tonkeeper.ui.screen.collectibles.manage.list.Item
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import uikit.base.BaseFragment
import uikit.extensions.collectFlow

class CollectiblesManageScreen(wallet: WalletEntity): BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.SwipeBack {

    private val spamDialog: CollectionSpamDialog by lazy { CollectionSpamDialog(requireContext()) }

    override val viewModel: CollectiblesManageViewModel by walletViewModel()

    private val adapter = Adapter(
        onClick = {
            if (it.spam) {
                showSpamDialog(it)
            } else {
                viewModel.toggle(it)
            }
        },
        showAllClick = { viewModel.showAll() }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(getString(Localization.collectibles))
        setAdapter(adapter)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    private fun showSpamDialog(item: Item.Collection) {
        spamDialog.show(item) {
            viewModel.notSpam(item)
        }
    }

    companion object {

        fun newInstance(wallet: WalletEntity) = CollectiblesManageScreen(wallet)
    }
}