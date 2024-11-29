package com.tonapps.tonkeeper.ui.screen.browser.more

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.browser.base.BrowserBaseScreen
import com.tonapps.tonkeeper.ui.screen.browser.more.list.Adapter
import com.tonapps.wallet.data.account.entities.WalletEntity
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow

class BrowserMoreScreen(wallet: WalletEntity): BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.SwipeBack {

    private val baseFragment: BrowserBaseScreen? by lazy {
        BrowserBaseScreen.from(this)
    }

    private val id: String by lazy {
        requireArguments().getString(ARG_ID)!!
    }

    override val viewModel: BrowserMoreViewModel by walletViewModel {
        parametersOf(id)
    }

    private val adapter = Adapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
        collectFlow(viewModel.titleFlow, ::setTitle)
        setAdapter(adapter)
    }

    override fun finishInternal() {
        baseFragment?.removeFragment(this)
    }

    companion object {

        private const val ARG_ID = "id"

        fun newInstance(wallet: WalletEntity, id: String): BrowserMoreScreen {
            val fragment = BrowserMoreScreen(wallet)
            fragment.putStringArg(ARG_ID, id)
            return fragment
        }
    }

}