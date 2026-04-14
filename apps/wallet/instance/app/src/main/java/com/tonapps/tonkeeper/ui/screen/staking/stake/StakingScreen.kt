package com.tonapps.tonkeeper.ui.screen.staking.stake

import android.os.Bundle
import android.view.View
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseHolderWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.staking.stake.amount.StakeAmountFragment
import com.tonapps.blockchain.model.legacy.WalletEntity
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.hideKeyboard

class StakingScreen(wallet: WalletEntity) :
    BaseHolderWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)),
    BaseFragment.BottomSheet {

    override val fragmentName: String = "StakingScreen"

    private val poolAddress: String by lazy { arguments?.getString(POOL_ADDRESS_KEY) ?: "" }

    private val from: String by lazy { arguments?.getString(FROM_KEY) ?: "" }

    override val viewModel: StakingViewModel by walletViewModel { parametersOf(poolAddress) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics?.simpleTrackEvent(
            "staking_open", hashMapOf(
                "from" to from
            )
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setFragment(StakeAmountFragment.newInstance(from = from))
    }

    override fun onDragging() {
        super.onDragging()
        requireContext().hideKeyboard()
    }

    companion object {

        private const val POOL_ADDRESS_KEY = "pool_address"
        private const val FROM_KEY = "from"

        fun newInstance(
            wallet: WalletEntity,
            from: String,
            poolAddress: String? = null,
        ): BaseFragment {
            val fragment = StakingScreen(wallet)
            fragment.putStringArg(POOL_ADDRESS_KEY, poolAddress)
            fragment.putStringArg(FROM_KEY, from)
            return fragment
        }
    }
}