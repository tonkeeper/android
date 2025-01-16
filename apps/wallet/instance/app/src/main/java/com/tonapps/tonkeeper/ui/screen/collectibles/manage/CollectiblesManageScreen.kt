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

    override val fragmentName: String = "CollectiblesManageScreen"

    private var spamArg: Boolean = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        spamArg = arguments?.getBoolean(ARG_SPAM) ?: false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(getString(Localization.collectibles))
        setAdapter(adapter)
        collectFlow(viewModel.uiItemsFlow, ::setUiItems)
    }

    private fun setUiItems(uiItems: List<Item>) {
        adapter.submitList(uiItems) {
            if (spamArg) {
                scrollToSpam()
                spamArg = false
            }
        }
    }

    private fun scrollToSpam() {
        val index = adapter.currentList.indexOf(viewModel.spamItem)
        if (index != -1) {
            listView.scrollToPosition(index)
        }
    }

    private fun showSpamDialog(item: Item.Collection) {
        spamDialog.show(item) {
            viewModel.notSpam(item)
        }
    }

    companion object {

        private const val ARG_SPAM = "spam"

        fun newInstance(
            wallet: WalletEntity,
            spam: Boolean = false
        ): CollectiblesManageScreen {
            val screen = CollectiblesManageScreen(wallet)
            screen.putBooleanArg(ARG_SPAM, spam)
            return screen
        }
    }
}