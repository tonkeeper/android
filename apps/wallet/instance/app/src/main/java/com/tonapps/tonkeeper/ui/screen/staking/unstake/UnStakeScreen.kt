package com.tonapps.tonkeeper.ui.screen.staking.unstake

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseHolderWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen
import com.tonapps.tonkeeper.ui.screen.staking.unstake.amount.UnStakeAmountFragment
import com.tonapps.tonkeeper.ui.screen.staking.unstake.confirm.UnStakeConfirmFragment
import com.tonapps.tonkeeper.ui.screen.staking.viewer.StakeViewerScreen
import com.tonapps.wallet.data.account.entities.WalletEntity
import kotlinx.coroutines.delay
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.hideKeyboard

class UnStakeScreen(wallet: WalletEntity): BaseHolderWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.BottomSheet {

    private val poolAddress: String by lazy { arguments?.getString(POOL_ADDRESS_KEY) ?:"" }

    override val viewModel: UnStakeViewModel by walletViewModel { parametersOf(poolAddress) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectFlow(viewModel.eventFlow, ::onEvent)
    }

    private suspend fun onEvent(event: UnStakeEvent) {
        when(event) {
            is UnStakeEvent.RouteToAmount -> setFragment(UnStakeAmountFragment.newInstance())
            is UnStakeEvent.OpenConfirm -> setFragment(UnStakeConfirmFragment.newInstance())
            is UnStakeEvent.Finish -> {
                navigation?.openURL("tonkeeper://activity")
                navigation?.removeByClass(UnStakeScreen::class.java)
                navigation?.removeByClass(StakeViewerScreen::class.java)
                delay(2000)
                finish()
            }
        }
    }

    override fun onDragging() {
        super.onDragging()
        requireContext().hideKeyboard()
    }

    companion object {

        private const val POOL_ADDRESS_KEY = "pool_address"

        fun newInstance(wallet: WalletEntity, poolAddress: String): UnStakeScreen {
            val fragment = UnStakeScreen(wallet)
            fragment.putStringArg(POOL_ADDRESS_KEY, poolAddress)
            return fragment
        }

    }

}