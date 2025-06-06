package com.tonapps.tonkeeper.ui.screen.staking.stake

import android.os.Bundle
import android.view.View
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseHolderWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeper.ui.screen.staking.stake.amount.StakeAmountFragment
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.data.staking.entities.PoolEntity
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.hideKeyboard

class StakingScreen(wallet: WalletEntity): BaseHolderWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.BottomSheet {

    override val fragmentName: String = "StakingScreen"

    private val poolAddress: String by lazy { arguments?.getString(POOL_ADDRESS_KEY) ?:"" }

    override val viewModel: StakingViewModel by walletViewModel { parametersOf(poolAddress) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.simpleTrackEvent("staking_open", viewModel.installId)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setFragment(StakeAmountFragment.newInstance())
    }

    override fun onDragging() {
        super.onDragging()
        requireContext().hideKeyboard()
    }

    companion object {

        private const val POOL_ADDRESS_KEY = "pool_address"

        fun newInstance(wallet: WalletEntity, poolAddress: String? = null): BaseFragment {
            if (poolAddress?.equalsAddress(PoolEntity.ethena.address) == true) {
                return DAppScreen.newInstance(wallet, AppEntity.ethena, "staking")
            }
            val fragment = StakingScreen(wallet)
            fragment.putStringArg(POOL_ADDRESS_KEY, poolAddress)
            return fragment
        }
    }
}