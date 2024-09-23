package com.tonapps.tonkeeper.ui.screen.staking.viewer

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Adapter
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow

class StakeViewerScreen(wallet: WalletEntity): BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.SwipeBack {

    override val viewModel: StakeViewerViewModel by walletViewModel { parametersOf(args.address) }

    private val args: StakeViewerArgs by lazy { StakeViewerArgs(requireArguments()) }

    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.uiItemsFlow.catch {
            navigation?.toast(Localization.unknown_error)
            finish()
        }.onEach(adapter::submitList).launchIn(lifecycleScope)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(args.name)
        setAdapter(adapter)
        collectFlow(viewModel.poolNameFlow, ::setTitle)
    }

    companion object {

        fun newInstance(wallet: WalletEntity, address: String, name: String): StakeViewerScreen {
            val fragment = StakeViewerScreen(wallet)
            fragment.setArgs(StakeViewerArgs(address, name))
            return fragment
        }
    }
}