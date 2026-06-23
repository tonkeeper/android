package com.tonapps.tonkeeper.ui.screen.collectibles.manage

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.collectibles.manage.list.Adapter
import com.tonapps.tonkeeper.ui.screen.collectibles.manage.list.Item
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.localization.Localization
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow

class CollectiblesManageScreen(wallet: WalletEntity): BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.SwipeBack {

    override val fragmentName: String = "CollectiblesManageScreen"

    private val spamArg: Boolean
        get() = arguments?.getBoolean(ARG_SPAM) ?: false

    private val spamDialog: CollectionSpamDialog by lazy { CollectionSpamDialog(requireContext()) }

    private val vm: CollectiblesManageViewModel by walletViewModel {
        parametersOf(CollectiblesManageArgs(spamOnly = spamArg))
    }

    override val viewModel: BaseWalletVM?
        get() = null

    private val adapter = Adapter(
        onClick = {
            if (it.spam) {
                showSpamDialog(it)
            } else {
                vm.toggle(it)
            }
        },
        showAllClick = { vm.showAll() }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(getString(if (spamArg) Localization.spam else Localization.collectibles))
        setAdapter(adapter)
        collectFlow(vm.uiItemsFlow, ::setUiItems)
    }

    private fun setUiItems(uiItems: List<Item>) {
        adapter.submitList(uiItems)
    }

    private fun showSpamDialog(item: Item.Collection) {
        spamDialog.show(item) {
            vm.notSpam(item)
            navigation?.toast(Localization.tx_marked_as_not_spam)
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