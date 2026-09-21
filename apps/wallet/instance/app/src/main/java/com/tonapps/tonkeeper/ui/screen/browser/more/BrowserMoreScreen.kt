package com.tonapps.tonkeeper.ui.screen.browser.more

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.browser.base.BrowserBaseScreen
import com.tonapps.tonkeeper.ui.screen.browser.more.list.Adapter
import com.tonapps.blockchain.model.legacy.WalletEntity
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow

class BrowserMoreScreen(wallet: WalletEntity): BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.SwipeBack {

    override val fragmentName: String = "BrowserMoreScreen"

    private val baseFragment: BrowserBaseScreen? by lazy {
        BrowserBaseScreen.from(this)
    }

    private val id: String by lazy {
        requireArguments().getString(ARG_ID)!!
    }

    private val chain: String? by lazy {
        requireArguments().getString(ARG_CHAIN)
    }

    override val viewModel: BrowserMoreViewModel by walletViewModel {
        parametersOf(id, chain)
    }

    private val environment: Environment by inject()

    private val adapter: Adapter by lazy {
        Adapter(
            selectedChain = viewModel.selectedChain,
            onChainSelected = viewModel::onChainSelected,
            environment = environment,
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
        collectFlow(viewModel.titleFlow, ::setTitle)
        setAdapter(adapter)
    }

    override fun finishInternal() {
        baseFragment?.removeFragment(this) ?: finish()
    }

    companion object {

        private const val ARG_ID = "id"
        private const val ARG_CHAIN = "chain"

        fun newInstance(wallet: WalletEntity, id: String, chain: String? = null): BrowserMoreScreen {
            val fragment = BrowserMoreScreen(wallet)
            fragment.putStringArg(ARG_ID, id)
            chain?.let { fragment.putStringArg(ARG_CHAIN, it) }
            return fragment
        }
    }

}